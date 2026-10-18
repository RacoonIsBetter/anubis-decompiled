package net.anubis.client.util.playerbypass;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlayerBypassLightTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Long2ObjectOpenHashMap<ChunkLightSnapshot> SNAPSHOTS = new Long2ObjectOpenHashMap();
   private static final LongOpenHashSet ALERTED = new LongOpenHashSet();
   private static final LongOpenHashSet SUSPECT = new LongOpenHashSet();
   static final int SUSPECT_TURNED_DARK = 1;
   static final int SUSPECT_AIRLESS = 2;
   private static final Long2IntOpenHashMap SENT_NOT_DARK = new Long2IntOpenHashMap();
   private static final int MAX_REMEMBERED_CHUNKS = 250000;
   private static String sentPlace;
   private static final Long2ObjectOpenHashMap<ChunkLightSnapshot> HELD = new Long2ObjectOpenHashMap();
   private static final ChunkEntryGuard ENTRY_GUARD = new ChunkEntryGuard();
   private static final ProtectedChunkStore PROTECTED_CHUNKS = ProtectedChunkStore.get();
   private static ClientWorld lastLevel;
   private static PlayerBypassLightTracker.SpottedListener spottedListener;
   private static long visibilityRevision;

   private PlayerBypassLightTracker() {
   }

   public static void onTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      syncLevel(mc);
      ClientPlayerEntity player = mc.player;
      if (lastLevel != null && player != null) {
         ENTRY_GUARD.update(
            player.getChunkPos().toLong(), PlayerBypassLightTracker::isLoaded, PlayerBypassLightTracker::isFlaggedOrHeld, PROTECTED_CHUNKS
         );
      }

      PROTECTED_CHUNKS.tickAutosave();
   }

   public static void onPacketApplied(Packet<?> packet) {
      int chunkX;
      int chunkZ;
      LightData data;
      if (packet instanceof ChunkDataS2CPacket chunk) {
         chunkX = chunk.getChunkX();
         chunkZ = chunk.getChunkZ();
         data = chunk.getLightData();
      } else {
         if (!(packet instanceof LightUpdateS2CPacket light)) {
            return;
         }

         chunkX = light.getChunkX();
         chunkZ = light.getChunkZ();
         data = light.getData();
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      syncLevel(mc);
      if (level != null && data != null) {
         if (mc.player != null && mc.player.getY() < 0.0) {
            hold(chunkX, chunkZ, data, level.countVerticalSections(), level.getBottomSectionCoord());
         } else {
            ingest(chunkX, chunkZ, data, level.countVerticalSections(), level.getBottomSectionCoord());
         }

         rememberNotDark(chunkX, chunkZ, data, level.countVerticalSections(), level.getBottomSectionCoord());
         visibilityRevision++;
      }
   }

   private static void rememberNotDark(int chunkX, int chunkZ, LightData data, int sectionCount, int bottomSectionY) {
      int bits = LightDataClassifier.sentNotDark(data, sectionCount, bottomSectionY);
      if (bits != 0) {
         if (SENT_NOT_DARK.size() >= 250000) {
            SENT_NOT_DARK.clear();
         }

         long key = ChunkPos.toLong(chunkX, chunkZ);
         SENT_NOT_DARK.put(key, SENT_NOT_DARK.get(key) | bits);
      }
   }

   private static void syncLevel(MinecraftClient mc) {
      ClientWorld level = mc.world;
      if (level != lastLevel) {
         visibilityRevision++;
         SNAPSHOTS.clear();
         ALERTED.clear();
         SUSPECT.clear();
         HELD.clear();
         ENTRY_GUARD.reset();
         lastLevel = level;
         PROTECTED_CHUNKS.flush();
         if (level == null) {
            PROTECTED_CHUNKS.select(null, null);
         } else {
            String world = worldKey(mc);
            String dimension = level.getRegistryKey().getValue().toString();
            String place = world + "\n" + dimension;
            if (!place.equals(sentPlace)) {
               SENT_NOT_DARK.clear();
               sentPlace = place;
            }

            PROTECTED_CHUNKS.select(world, dimension);
         }
      }
   }

   private static String worldKey(MinecraftClient mc) {
      IntegratedServer local = mc.getServer();
      if (local != null) {
         Path folder = local.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
         return ProtectedChunkStore.singleplayerKey(folder == null ? "world" : folder.toString());
      } else {
         ServerInfo server = mc.getCurrentServerEntry();
         return ProtectedChunkStore.serverKey(server == null ? null : server.address);
      }
   }

   private static void ingest(int chunkX, int chunkZ, LightData data, int sectionCount, int bottomSectionY) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      if (HELD.containsKey(key)) {
         hold(chunkX, chunkZ, data, sectionCount, bottomSectionY);
      }

      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)SNAPSHOTS.get(key);
      boolean wasFlagged = snapshot != null && isFlagged(key, snapshot);
      if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
         snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
         SNAPSHOTS.put(key, snapshot);
      }

      try {
         LightDataClassifier.apply(data, snapshot);
      } catch (Throwable t) {
         LOGGER.error("Player Bypass light ingest failed for chunk {}, {}", new Object[]{chunkX, chunkZ, t});
         return;
      }

      boolean flagged = snapshot.hasBaseSignal();
      if (flagged && !PROTECTED_CHUNKS.flagAllowed(key, wasFlagged)) {
         SNAPSHOTS.remove(key);
      } else {
         int suspect = flagged ? suspicion(key, chunkX, chunkZ, snapshot) : 0;
         if (flagged && suspect != 0) {
            SUSPECT.add(key);
         } else if (flagged) {
            SUSPECT.remove(key);
            if (spottedListener != null && ALERTED.add(key)) {
               spottedListener.onChunkSpotted(chunkX, chunkZ);
            }
         } else if (!ALERTED.contains(key)) {
            SNAPSHOTS.remove(key);
            SUSPECT.remove(key);
         }
      }
   }

   private static int suspicion(long key, int chunkX, int chunkZ, ChunkLightSnapshot snapshot) {
      int sentNotDark = SENT_NOT_DARK.get(key);
      int why = 0;

      for (int i = 1; i < snapshot.sectionCount + 1; i++) {
         if (ChunkLightSnapshot.candidate(i, snapshot.bottomSectionY) && snapshot.sky[i] == LightSectionStatus.ZEROED_PRESENT) {
            if (i < 32 && (sentNotDark >>> i & 1) != 0) {
               why |= 1;
            } else {
               if (lastLevel == null || !DarkSectionCheck.airlessAround(lastLevel, chunkX, snapshot.bottomSectionY + i - 1, chunkZ)) {
                  return 0;
               }

               why |= 2;
            }
         }
      }

      return why;
   }

   private static void hold(int chunkX, int chunkZ, LightData data, int sectionCount, int bottomSectionY) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)HELD.get(key);
      if (snapshot == null || snapshot.sectionCount != sectionCount || snapshot.bottomSectionY != bottomSectionY) {
         snapshot = new ChunkLightSnapshot(sectionCount, bottomSectionY);
      }

      try {
         LightDataClassifier.apply(data, snapshot);
      } catch (Throwable t) {
         LOGGER.error("Player Bypass light hold failed for chunk {}, {}", new Object[]{chunkX, chunkZ, t});
         return;
      }

      if (snapshot.hasBaseSignal() && PROTECTED_CHUNKS.flagAllowed(key, false)) {
         HELD.put(key, snapshot);
      } else {
         HELD.remove(key);
      }
   }

   public static void setSpottedListener(PlayerBypassLightTracker.SpottedListener listener) {
      spottedListener = listener;
   }

   public static Long2ObjectMap<ChunkLightSnapshot> snapshots() {
      return SNAPSHOTS;
   }

   public static long visibilityRevision() {
      return visibilityRevision;
   }

   public static boolean isSuspect(long chunkKey) {
      return SUSPECT.contains(chunkKey);
   }

   public static boolean isFlagged(long chunkKey, ChunkLightSnapshot snapshot) {
      return ALERTED.contains(chunkKey) || snapshot.hasBaseSignal();
   }

   public static boolean isFlagged(long chunkKey) {
      ChunkLightSnapshot snapshot = (ChunkLightSnapshot)SNAPSHOTS.get(chunkKey);
      return snapshot != null && isFlagged(chunkKey, snapshot);
   }

   private static boolean isFlaggedOrHeld(long chunkKey) {
      return isFlagged(chunkKey) || HELD.containsKey(chunkKey);
   }

   private static boolean isLoaded(long chunkKey) {
      return lastLevel != null && lastLevel.getChunkManager().isChunkLoaded(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface SpottedListener {
      void onChunkSpotted(int var1, int var2);
   }
}
