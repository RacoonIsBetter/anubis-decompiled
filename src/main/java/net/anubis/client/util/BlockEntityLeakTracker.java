package net.anubis.client.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;

@Environment(EnvType.CLIENT)
public final class BlockEntityLeakTracker {
   private static final Map<BlockPos, BlockEntityLeakTracker.Entry> FOUND = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<BlockPos> ADDED = new ConcurrentLinkedQueue<>();
   private static final AtomicInteger GENERATION = new AtomicInteger();
   private static volatile boolean recordAdditions;

   private BlockEntityLeakTracker() {
   }

   public static Map<BlockPos, BlockEntityLeakTracker.Entry> view() {
      return FOUND;
   }

   public static boolean contains(BlockPos pos) {
      return FOUND.containsKey(pos);
   }

   public static void clear() {
      FOUND.clear();
      ADDED.clear();
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
                  put(pos, type);
               }
            });
         } catch (Throwable var5) {
         }
      } else if (packet instanceof BlockEntityUpdateS2CPacket bePacket) {
         put(bePacket.getPos(), bePacket.getBlockEntityType());
      } else if (packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
         clear();
      }
   }

   private static void put(BlockPos pos, BlockEntityType<?> type) {
      if (!PerformanceModule.optimizationsEnabled() || !FOUND.containsKey(pos)) {
         BlockPos key = pos.toImmutable();
         if (FOUND.putIfAbsent(key, new BlockEntityLeakTracker.Entry(blockFromType(type))) == null && recordAdditions) {
            ADDED.add(key);
         }
      }
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
      return mapped != null ? mapped : Registries.BLOCK.getEntry(id).<Block>map(RegistryEntry::value).orElse(Blocks.AIR);
   }

   @Environment(EnvType.CLIENT)
   public record Entry(Block block) {
   }
}
