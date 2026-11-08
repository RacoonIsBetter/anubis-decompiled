package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Finds out what an anti-xray / anti-ESP server still leaks. It logs every packet that comes from
 * inside the hidden underground (a section the server sends as nothing but deepslate) and every
 * packet about an entity the client was never sent (a hidden player), to
 * {@code config/anubis/leak-test.log}, with a chat summary every few seconds. Read-only: it never
 * sends anything to the server.
 */
@Environment(EnvType.CLIENT)
public final class LeakTestModule extends ClientModule {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

   private final NumberSetting summaryEvery = this.addSetting("summary", new NumberSetting("Summary Every (s)", 15.0F, 5.0F, 120.0F).step(5.0F));
   private final Map<String, Integer> counts = new TreeMap<>();
   /** Per unknown entity id: how many packets, the last known position (from position syncs), and when. */
   private final Int2ObjectOpenHashMap<LeakTestModule.Ghost> ghosts = new Int2ObjectOpenHashMap<>();
   private BufferedWriter out;
   private int ticks;
   private int total;

   public LeakTestModule() {
      super(
         "leak-test",
         "Leak Test",
         "Logs what the server still sends from inside hidden underground and about hidden entities, to find anti-xray leaks.",
         ModuleCategory.BASE_RAID
      );
   }

   @Override
   protected void onEnable() {
      this.counts.clear();
      this.ghosts.clear();
      this.total = 0;
      Path file = FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("leak-test.log");

      try {
         Files.createDirectories(file.getParent());
         this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         this.out.write("=== Leak test started " + java.time.LocalDateTime.now() + " ===\n");
         this.out.flush();
         ModuleChat.send(this, ModuleChat.body().text("Logging leaks to ").warn("config/anubis/leak-test.log"));
      } catch (IOException e) {
         this.out = null;
         ModuleChat.send(this, ModuleChat.body().warn("Could not open the leak log").text(": " + e.getMessage()));
      }
   }

   @Override
   protected void onDisable() {
      this.summary(true);

      if (this.out != null) {
         try {
            this.out.close();
         } catch (IOException ignored) {
         }

         this.out = null;
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         this.inspect(event.packet, level);
      }
   }

   private void inspect(Packet<?> packet, ClientWorld level) {
      if (packet instanceof PlaySoundS2CPacket sound) {
         Vec3d pos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
         if (hidden(level, BlockPos.ofFloored(pos))) {
            this.hit("sound in hidden layer: " + sound.getSound().value().id().getPath() + " (" + sound.getCategory().getName() + ")", pos);
         }
      } else if (packet instanceof PlaySoundFromEntityS2CPacket sound) {
         if (level.getEntityById(sound.getEntityId()) == null) {
            this.hit("sound from unknown entity: " + sound.getSound().value().id().getPath() + " (" + sound.getCategory().getName() + ")", null);
         }
      } else if (packet instanceof WorldEventS2CPacket event) {
         if (hidden(level, event.getPos())) {
            this.hit("world event in hidden layer: id " + event.getEventId(), Vec3d.ofCenter(event.getPos()));
         }
      } else if (packet instanceof BlockBreakingProgressS2CPacket progress) {
         boolean unknown = level.getEntityById(progress.getEntityId()) == null;
         if (unknown || hidden(level, progress.getPos())) {
            this.hit("mining progress" + (unknown ? " by unknown entity" : "") + (hidden(level, progress.getPos()) ? " in hidden layer" : ""), Vec3d.ofCenter(progress.getPos()));
         }
      } else if (packet instanceof BlockUpdateS2CPacket update) {
         if (hidden(level, update.getPos()) && !update.getState().isOf(Blocks.DEEPSLATE)) {
            this.hit("block update in hidden layer: " + name(update.getState().getBlock()), Vec3d.ofCenter(update.getPos()));
         }
      } else if (packet instanceof ParticleS2CPacket particle) {
         Vec3d pos = new Vec3d(particle.getX(), particle.getY(), particle.getZ());
         if (hidden(level, BlockPos.ofFloored(pos))) {
            this.hit("particles in hidden layer: " + Registries.PARTICLE_TYPE.getId(particle.getParameters().getType()), pos);
         }
      } else if (packet instanceof EntitySpawnS2CPacket spawn) {
         Vec3d pos = new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ());
         if (hidden(level, BlockPos.ofFloored(pos))) {
            this.hit("entity spawned in hidden layer: " + Registries.ENTITY_TYPE.getId(spawn.getEntityType()).getPath(), pos);
         }
      } else if (packet instanceof EntityS2CPacket move) {
         this.unknown(level, move.getEntity(level), -1, "movement", null);
      } else if (packet instanceof EntityPositionSyncS2CPacket sync) {
         this.unknown(level, level.getEntityById(sync.id()), sync.id(), "position sync", sync.values().position());
      } else if (packet instanceof EntitySetHeadYawS2CPacket head) {
         this.unknown(level, head.getEntity(level), -1, "head turn", null);
      } else if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
         this.unknown(level, level.getEntityById(velocity.getEntityId()), velocity.getEntityId(), "velocity", null);
      } else if (packet instanceof EntityAnimationS2CPacket animation) {
         this.unknown(level, level.getEntityById(animation.getEntityId()), animation.getEntityId(), "arm swing / animation", null);
      } else if (packet instanceof EntityStatusS2CPacket status) {
         this.unknown(level, status.getEntity(level), -1, "status", null);
      } else if (packet instanceof EntityEquipmentUpdateS2CPacket equipment) {
         this.unknown(level, level.getEntityById(equipment.getEntityId()), equipment.getEntityId(), "equipment", null);
      } else if (packet instanceof EntityTrackerUpdateS2CPacket data) {
         this.unknown(level, level.getEntityById(data.id()), data.id(), "entity data", null);
      }
   }

   /**
    * Packets about entities the client doesn't have. A few always arrive just after a mob unloads;
    * a hidden player keeps producing them under one id, and position syncs give away where it is.
    */
   private void unknown(ClientWorld level, Entity entity, int id, String what, Vec3d pos) {
      if (entity != null) {
         return;
      }

      if (id < 0) {
         this.hit(what + " of an unknown entity", null);
         return;
      }

      LeakTestModule.Ghost ghost = this.ghosts.computeIfAbsent(id, k -> new LeakTestModule.Ghost());
      ghost.packets++;
      ghost.lastAt = System.currentTimeMillis();
      if (pos != null) {
         ghost.pos = pos;
      }

      this.hit(what + " of unknown entity #" + id, pos);
   }

   /** Inside the hidden underground: below Y=0 in a section sent as nothing but deepslate (or air). */
   private static boolean hidden(ClientWorld level, BlockPos pos) {
      if (pos.getY() >= 0) {
         return false;
      }

      WorldChunk chunk = level.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
      if (chunk == null) {
         return false;
      }

      ChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
      return section == null || section.isEmpty() || !section.hasAny(state -> !state.isAir() && !state.isOf(Blocks.DEEPSLATE));
   }

   private void hit(String what, Vec3d pos) {
      this.total++;
      String key = what.replaceAll(" \\(.*\\)$", "");
      this.counts.merge(key.replaceAll(" #\\d+", ""), 1, Integer::sum);
      if (this.out != null) {
         try {
            String where = pos == null ? "" : String.format(" @ %.1f %.1f %.1f", pos.x, pos.y, pos.z);
            this.out.write(LocalTime.now().format(TIME) + "  " + what + where + "\n");
         } catch (IOException ignored) {
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (++this.ticks % (int)(this.summaryEvery.get() * 20.0F) == 0) {
         this.summary(false);
      }
   }

   private void summary(boolean last) {
      if (this.out != null) {
         try {
            this.out.flush();
         } catch (IOException ignored) {
         }
      }

      if (this.counts.isEmpty()) {
         if (last) {
            ModuleChat.send(this, ModuleChat.body().text("No leaks seen."));
         }

         return;
      }

      StringBuilder ghostText = new StringBuilder();
      long now = System.currentTimeMillis();

      for (Int2ObjectMap.Entry<LeakTestModule.Ghost> entry : this.ghosts.int2ObjectEntrySet()) {
         LeakTestModule.Ghost ghost = entry.getValue();
         if (ghost.packets >= GHOST_PACKETS && now - ghost.lastAt < 30000L) {
            ghostText.append(ghostText.isEmpty() ? "" : ", ").append("#").append(entry.getIntKey()).append(" x").append(ghost.packets);
            if (ghost.pos != null) {
               ghostText.append(String.format(" at %.0f %.0f %.0f", ghost.pos.x, ghost.pos.y, ghost.pos.z));
            }
         }
      }

      this.ghosts.int2ObjectEntrySet().removeIf(e -> now - e.getValue().lastAt > 60000L);
      if (!ghostText.isEmpty()) {
         ModuleChat.send(this, ModuleChat.body().warn("Hidden entities").text(" (steady packets, never spawned): " + ghostText));
      }

      StringBuilder text = new StringBuilder();
      this.counts.entrySet().stream()
         .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
         .limit(6)
         .forEach(e -> text.append(text.isEmpty() ? "" : ", ").append(e.getKey()).append(" x").append(e.getValue()));
      ModuleChat.send(this, ModuleChat.body().warn(this.total + " leaks").text(": " + text));
      this.counts.clear();
   }

   /** Packets for one id before it counts as a hidden entity rather than unload noise. */
   private static final int GHOST_PACKETS = 5;

   @Environment(EnvType.CLIENT)
   private static final class Ghost {
      int packets;
      long lastAt;
      Vec3d pos;
   }

   private static String name(Block block) {
      return Registries.BLOCK.getId(block).getPath();
   }
}
