package net.anubis.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class LightSnapshot {
   private static final int MAX_HITS = 250000;
   private final ArrayDeque<ChunkPos> chunks;
   private final Map<BlockPos, Integer> hits = new HashMap<>();
   private final int minLight;

   public LightSnapshot(ChunkPos center, int radius, int minLight) {
      this.minLight = minLight;
      List<ChunkPos> ordered = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            ordered.add(new ChunkPos(center.x + x, center.z + z));
         }
      }

      ordered.sort(
         Comparator.comparingInt(
            p -> (p.x - center.x) * (p.x - center.x)
               + (p.z - center.z) * (p.z - center.z)
         )
      );
      this.chunks = new ArrayDeque<>(ordered);
   }

   public Map<BlockPos, Integer> hits() {
      return this.hits;
   }

   public boolean collect(ClientWorld level) {
      long start = System.nanoTime();
      int processed = 0;

      while (!this.chunks.isEmpty() && processed++ < 16 && System.nanoTime() - start < 2000000L) {
         ChunkPos pos = this.chunks.removeFirst();
         if (level.getChunkManager().isChunkLoaded(pos.x, pos.z)) {
            LightingProvider engine = level.getChunkManager().getLightingProvider();
            ChunkLightingView blockLight = engine.get(LightType.BLOCK);
            ChunkLightingView skyLight = engine.get(LightType.SKY);
            Mutable cursor = new Mutable();

            for (int sy = level.getBottomY() >> 4; sy < 0; sy++) {
               ChunkNibbleArray block = blockLight.getLightSection(ChunkSectionPos.from(pos.x, sy, pos.z));
               if (block != null && !block.isUninitialized()) {
                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           int light = block.get(x, y, z);
                           if (light >= this.minLight) {
                              cursor.set((pos.x << 4) + x, (sy << 4) + y, (pos.z << 4) + z);
                              if (light > skyLight.getLightLevel(cursor)) {
                                 this.hits.put(cursor.toImmutable(), light);
                              }

                              if (this.hits.size() >= 250000) {
                                 this.chunks.clear();
                                 return true;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return this.chunks.isEmpty();
   }
}
