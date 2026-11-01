package net.anubis.client.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

@Environment(EnvType.CLIENT)
public final class LightLeakScanner {
   private static final int MIN_INSIDE = 2;
   private static final int DARK_GAP = 3;
   private static final long BUDGET_NANOS = 1500000L;
   private static final int NOISY_CHUNK = 512;
   private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();

   public void clear() {
      this.queue.clear();
   }

   public void queueLoaded(ClientWorld level, BlockPos around, int radius) {
      int px = around.getX() >> 4;
      int pz = around.getZ() >> 4;
      LongArrayList found = new LongArrayList();

      for (int ring = 0; ring <= radius; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && LevelUtil.isLoaded(level, px + dx, pz + dz)) {
                  found.add(ChunkPos.toLong(px + dx, pz + dz));
               }
            }
         }
      }

      for (int i = 0; i < found.size(); i++) {
         this.queue.add(found.getLong(i));
      }
   }

   public void onPacketApplied(Packet<?> packet) {
      if (packet instanceof ChunkDataS2CPacket chunk) {
         this.queue.add(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      } else if (packet instanceof LightUpdateS2CPacket light) {
         this.queue.add(ChunkPos.toLong(light.getChunkX(), light.getChunkZ()));
      }
   }

   public void tick(ClientWorld level) {
      if (!this.queue.isEmpty()) {
         boolean optimized = PerformanceModule.optimizationsEnabled();
         long started = System.nanoTime();
         long deadline = optimized ? PerformanceBudget.acquire(1500000L) : started + 1500000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.removeFirstLong();
            scan(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
         }

         if (optimized) {
            PerformanceBudget.record(started);
         }
      }
   }

   private static void scan(ClientWorld level, int chunkX, int chunkZ) {
      WorldChunk chunk = level.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
      if (chunk != null) {
         ChunkSection[] sections = chunk.getSectionArray();
         ChunkLightingView light = level.getLightingProvider().get(LightType.BLOCK);
         LongArrayList found = new LongArrayList();

         for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (!section.isEmpty()) {
               int sectionY = chunk.sectionIndexToCoord(i);
               ChunkNibbleArray data = light.getLightSection(ChunkSectionPos.from(chunkX, sectionY, chunkZ));
               if (data != null && !data.isUninitialized() && anyLight(data)) {
                  scanSection(level, section, data, chunkX << 4, sectionY << 4, chunkZ << 4, found);
                  if (found.size() > 512) {
                     return;
                  }
               }
            }
         }

         for (int i = 0; i < found.size(); i++) {
            BlockEntityLeakTracker.hint(BlockPos.fromLong(found.getLong(i)), Blocks.AIR, BlockEntityLeakTracker.Source.LIGHT);
         }
      }
   }

   private static void scanSection(ClientWorld level, ChunkSection section, ChunkNibbleArray data, int baseX, int baseY, int baseZ, LongArrayList found) {
      for (int y = 0; y < 16; y++) {
         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               BlockState state = section.getBlockState(x, y, z);
               int inside = data.get(x, y, z);
               if (state.getOpacity() >= 15) {
                  if (inside >= 2 && inside > state.getLuminance()) {
                     found.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                  }
               } else if (state.isAir() && inside <= 12 && brightestInside(data, x, y, z) - inside >= 3) {
                  BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                  if (!nextToGeode(level, pos)) {
                     found.add(pos.asLong());
                  }
               }
            }
         }
      }
   }

   private static int brightestInside(ChunkNibbleArray data, int x, int y, int z) {
      int best = 0;
      if (x > 0) {
         best = Math.max(best, data.get(x - 1, y, z));
      }

      if (x < 15) {
         best = Math.max(best, data.get(x + 1, y, z));
      }

      if (y > 0) {
         best = Math.max(best, data.get(x, y - 1, z));
      }

      if (y < 15) {
         best = Math.max(best, data.get(x, y + 1, z));
      }

      if (z > 0) {
         best = Math.max(best, data.get(x, y, z - 1));
      }

      if (z < 15) {
         best = Math.max(best, data.get(x, y, z + 1));
      }

      return best;
   }

   private static boolean anyLight(ChunkNibbleArray data) {
      byte[] raw = data.asByteArray();

      for (byte value : raw) {
         if (value != 0) {
            return true;
         }
      }

      return false;
   }

   private static boolean nextToGeode(ClientWorld level, BlockPos pos) {
      for (BlockPos next : new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) {
         BlockState state = level.getBlockState(next);
         if (state.isOf(Blocks.BUDDING_AMETHYST) || state.isOf(Blocks.AMETHYST_BLOCK)) {
            return true;
         }
      }

      return false;
   }
}
