package net.anubis.client.util.netherite;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.registry.RegistryKeys;

@Environment(EnvType.CLIENT)
public final class NetheriteChunkTracker {
   static final int MAXIMUM_CANDIDATES = 512;
   private static final int ANCIENT_STATE_ID = Block.getRawIdFromState(Blocks.ANCIENT_DEBRIS.getDefaultState());
   private static final RegistryKey<World> DONUT_NETHER = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("worlds", "smp_nether"));
   private static final Set<NetheriteChunkTracker.Candidate> CANDIDATES = new LinkedHashSet<>();
   private static WeakReference<ClientWorld> lastLevel = new WeakReference<>(null);
   private static RegistryKey<World> lastDimension;
   private static NetheriteChunkTracker.CandidateListener listener;
   private static NetheriteChunkTracker.RevealListener revealListener;

   private NetheriteChunkTracker() {
   }

   public static void onPacketApplied(Packet<?> packet) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && isNether(level)) {
         if (packet instanceof ChunkDataS2CPacket chunk) {
            trackLevel(level);

            for (int sectionY : HiddenDebrisScanner.findHiddenSections(chunk.getChunkData(), level.countVerticalSections(), level.getBottomSectionCoord(), ANCIENT_STATE_ID)) {
               add(new NetheriteChunkTracker.Candidate(chunk.getChunkX(), chunk.getChunkZ(), sectionY));
            }
         } else if (packet instanceof BlockUpdateS2CPacket update) {
            onBlockUpdate(update.getPos(), update.getState());
         } else if (packet instanceof ChunkDeltaUpdateS2CPacket sectionUpdate) {
            sectionUpdate.visitUpdates(NetheriteChunkTracker::onBlockUpdate);
         }
      }
   }

   private static void onBlockUpdate(BlockPos pos, BlockState state) {
      if (Block.getRawIdFromState(state) == ANCIENT_STATE_ID) {
         if (revealListener != null) {
            revealListener.onRevealed(pos.toImmutable());
         }

         CANDIDATES.remove(new NetheriteChunkTracker.Candidate(pos.getX() >> 4, pos.getZ() >> 4, pos.getY() >> 4));
      }
   }

   private static void add(NetheriteChunkTracker.Candidate candidate) {
      if (!CANDIDATES.contains(candidate)) {
         if (CANDIDATES.size() >= 512) {
            Iterator<NetheriteChunkTracker.Candidate> oldest = CANDIDATES.iterator();
            oldest.next();
            oldest.remove();
         }

         CANDIDATES.add(candidate);
         if (listener != null) {
            listener.onCandidate(candidate);
         }
      }
   }

   private static void trackLevel(ClientWorld level) {
      RegistryKey<World> dimension = level.getRegistryKey();
      if (level != lastLevel.get() || !dimension.equals(lastDimension)) {
         CANDIDATES.clear();
         lastLevel = new WeakReference<>(level);
         lastDimension = dimension;
      }
   }

   public static boolean isNether(ClientWorld level) {
      RegistryKey<World> dimension = level.getRegistryKey();
      return dimension == World.NETHER || DONUT_NETHER.equals(dimension) || level.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER);
   }

   public static List<NetheriteChunkTracker.Candidate> snapshot() {
      return MinecraftClient.getInstance().world != lastLevel.get() ? List.of() : List.copyOf(CANDIDATES);
   }

   public static void setListener(NetheriteChunkTracker.CandidateListener candidateListener) {
      listener = candidateListener;
   }

   public static void setRevealListener(NetheriteChunkTracker.RevealListener debrisListener) {
      revealListener = debrisListener;
   }

   @Environment(EnvType.CLIENT)
   public record Candidate(int chunkX, int chunkZ, int sectionY) {
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface CandidateListener {
      void onCandidate(NetheriteChunkTracker.Candidate var1);
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface RevealListener {
      void onRevealed(BlockPos var1);
   }
}
