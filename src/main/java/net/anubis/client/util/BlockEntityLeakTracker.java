package net.anubis.client.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class BlockEntityLeakTracker {
   private static final double ON_EDGE = 0.07;
   private static final Map<BlockPos, BlockEntityLeakTracker.Entry> FOUND = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<BlockPos> ADDED = new ConcurrentLinkedQueue<>();
   private static final AtomicInteger GENERATION = new AtomicInteger();
   private static final AtomicIntegerArray COUNTS = new AtomicIntegerArray(BlockEntityLeakTracker.Source.values().length);
   private static volatile boolean recordAdditions;

   private BlockEntityLeakTracker() {
   }

   public static Map<BlockPos, BlockEntityLeakTracker.Entry> view() {
      return FOUND;
   }

   public static boolean contains(BlockPos pos) {
      return FOUND.containsKey(pos);
   }

   public static boolean containsReal(BlockPos pos) {
      BlockEntityLeakTracker.Entry entry = FOUND.get(pos);
      return entry != null && entry.real();
   }

   public static int count(BlockEntityLeakTracker.Source source) {
      return COUNTS.get(source.ordinal());
   }

   public static void clear() {
      FOUND.clear();
      ADDED.clear();

      for (int i = 0; i < COUNTS.length(); i++) {
         COUNTS.set(i, 0);
      }

      GENERATION.incrementAndGet();
   }

   public static int generation() {
      return GENERATION.get();
   }

   public static void recordAdditions(boolean on) {
      recordAdditions = on;
      if (!on) {
         ADDED.clear();
      }
   }

   public static BlockPos pollAdded() {
      return ADDED.poll();
   }

   public static void ingest(Packet<?> packet) {
      if (packet instanceof ChunkDataS2CPacket chunkPacket) {
         ChunkData data = chunkPacket.getChunkData();

         try {
            data.getBlockEntities(chunkPacket.getChunkX(), chunkPacket.getChunkZ()).accept((pos, type, tag) -> {
               if (type != null) {
                  put(pos, type, tag, BlockEntityLeakTracker.Source.CHUNK);
               }
            });
         } catch (Throwable var9) {
         }
      } else if (packet instanceof BlockEntityUpdateS2CPacket bePacket) {
         put(bePacket.getPos(), bePacket.getBlockEntityType(), bePacket.getNbt(), BlockEntityLeakTracker.Source.DATA);
      } else if (packet instanceof BlockEventS2CPacket event) {
         if (event.getBlock() instanceof BlockEntityProvider) {
            hint(event.getPos(), event.getBlock(), BlockEntityLeakTracker.Source.EVENT);
         }
      } else if (packet instanceof WorldEventS2CPacket event) {
         Block block = eventBlock(event.getEventId());
         if (block != null && !event.isGlobal()) {
            hint(event.getPos(), block, BlockEntityLeakTracker.Source.EVENT);
         }
      } else if (packet instanceof PlaySoundS2CPacket sound) {
         Block block = soundBlock(sound.getSound());
         if (block != null) {
            hintAt(sound.getX(), sound.getY(), sound.getZ(), block);
         }
      } else if (packet instanceof BlockUpdateS2CPacket update) {
         dropRevealedHint(update.getPos(), update.getState());
      } else if (packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
         clear();
      }
   }

   public static void hint(BlockPos pos, Block block, BlockEntityLeakTracker.Source source) {
      BlockPos key = pos.toImmutable();
      BlockEntityLeakTracker.Entry old = FOUND.get(key);
      if (old == null || !old.real() && old.block() == Blocks.AIR && block != Blocks.AIR) {
         FOUND.put(key, new BlockEntityLeakTracker.Entry(block, null, source));
         COUNTS.incrementAndGet(source.ordinal());
         if (recordAdditions) {
            ADDED.add(key);
         }
      }
   }

   private static void put(BlockPos pos, BlockEntityType<?> type, NbtCompound tag, BlockEntityLeakTracker.Source source) {
      if (!PerformanceModule.optimizationsEnabled() || !containsReal(pos)) {
         BlockPos key = pos.toImmutable();
         NbtCompound data = tag != null && !tag.isEmpty() ? tag : null;
         BlockEntityLeakTracker.Entry old = FOUND.get(key);
         if (old == null || !old.real()) {
            FOUND.put(key, new BlockEntityLeakTracker.Entry(blockFromType(type), data, source));
            COUNTS.incrementAndGet(source.ordinal());
            if (recordAdditions) {
               ADDED.add(key);
            }
         } else if (data != null && !data.equals(old.tag())) {
            FOUND.replace(key, old, new BlockEntityLeakTracker.Entry(old.block(), data, old.source()));
         }
      }
   }

   private static void dropRevealedHint(BlockPos pos, BlockState state) {
      BlockEntityLeakTracker.Entry old = FOUND.get(pos);
      if (old != null && !old.real()) {
         if (!state.isAir() && !state.isOf(Blocks.STONE) && !state.isOf(Blocks.DEEPSLATE) && !(state.getBlock() instanceof BlockEntityProvider)) {
            if (FOUND.remove(pos, old)) {
               GENERATION.incrementAndGet();
            }
         }
      }
   }

   private static void hintAt(double x, double y, double z, Block block) {
      int bx = (int)Math.floor(x);
      int by = (int)Math.floor(y);
      int bz = (int)Math.floor(z);
      double fx = x - bx;
      double fy = y - by;
      double fz = z - bz;
      if (!(Math.abs(fy - 0.5) > 0.07)) {
         boolean seamX = fx < 0.07;
         boolean seamZ = fz < 0.07;
         if ((seamX || !(Math.abs(fx - 0.5) > 0.07)) && (seamZ || !(Math.abs(fz - 0.5) > 0.07)) && (!seamX || !seamZ)) {
            hint(new BlockPos(bx, by, bz), block, BlockEntityLeakTracker.Source.SOUND);
            if (seamX) {
               hint(new BlockPos(bx - 1, by, bz), block, BlockEntityLeakTracker.Source.SOUND);
            }

            if (seamZ) {
               hint(new BlockPos(bx, by, bz - 1), block, BlockEntityLeakTracker.Source.SOUND);
            }
         }
      }
   }

   private static Block eventBlock(int type) {
      return switch (type) {
         case 1000, 1001, 1002, 2000 -> Blocks.DISPENSER;
         case 1035 -> Blocks.BREWING_STAND;
         case 1049, 1050, 2010 -> Blocks.CRAFTER;
         default -> null;
      };
   }

   private static Block soundBlock(RegistryEntry<SoundEvent> sound) {
      Identifier id = sound.value().id();
      if (!id.getNamespace().equals("minecraft")) {
         return null;
      }

      String path = id.getPath();
      if (!path.startsWith("block.")) {
         return null;
      }

      String name = path.substring("block.".length());
      int dot = name.indexOf(46);
      if (dot < 0) {
         return null;
      }

      String kind = name.substring(0, dot);

      return switch (kind) {
         case "chest" -> Blocks.CHEST;
         case "ender_chest" -> Blocks.ENDER_CHEST;
         case "barrel" -> Blocks.BARREL;
         case "shulker_box" -> Blocks.SHULKER_BOX;
         case "beacon" -> Blocks.BEACON;
         case "vault" -> Blocks.VAULT;
         case "trial_spawner" -> Blocks.TRIAL_SPAWNER;
         case "chiseled_bookshelf" -> Blocks.CHISELED_BOOKSHELF;
         case "copper_chest" -> (Block)Registries.BLOCK.get(Identifier.ofVanilla("copper_chest"));
         default -> null;
      };
   }

   public static Block blockFromType(BlockEntityType<?> type) {
      Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(type);
      if (id == null) {
         return Blocks.AIR;
      }

      String path = id.getPath();

      Block mapped = switch (path) {
         case "mob_spawner" -> Blocks.SPAWNER;
         case "piston" -> Blocks.MOVING_PISTON;
         case "sign" -> Blocks.OAK_SIGN;
         case "hanging_sign" -> Blocks.OAK_HANGING_SIGN;
         case "skull" -> Blocks.SKELETON_SKULL;
         case "banner" -> Blocks.WHITE_BANNER;
         case "bed" -> Blocks.RED_BED;
         case "brushable_block" -> Blocks.SUSPICIOUS_SAND;
         default -> null;
      };
      return mapped != null ? mapped : Registries.BLOCK.getEntry(id).map(RegistryEntry::value).orElse(Blocks.AIR);
   }

   @Environment(EnvType.CLIENT)
   public record Entry(Block block, NbtCompound tag, BlockEntityLeakTracker.Source source) {
      public boolean real() {
         return this.source == BlockEntityLeakTracker.Source.CHUNK || this.source == BlockEntityLeakTracker.Source.DATA;
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Source {
      CHUNK,
      DATA,
      EVENT,
      SOUND,
      LIGHT;
   }
}
