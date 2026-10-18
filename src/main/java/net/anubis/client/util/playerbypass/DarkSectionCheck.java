package net.anubis.client.util.playerbypass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.AbstractBlock.AbstractBlockState;

@Environment(EnvType.CLIENT)
final class DarkSectionCheck {
   private static final int[][] SIDES = new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

   private DarkSectionCheck() {
   }

   static boolean airlessAround(ClientWorld level, int chunkX, int sectionY, int chunkZ) {
      if (!airless(level, chunkX, sectionY, chunkZ)) {
         return false;
      }

      for (int[] side : SIDES) {
         if (!airless(level, chunkX + side[0], sectionY + side[1], chunkZ + side[2])) {
            return false;
         }
      }

      return true;
   }

   private static boolean airless(ClientWorld level, int chunkX, int sectionY, int chunkZ) {
      if (sectionY >= level.getBottomSectionCoord() && sectionY <= level.getTopSectionCoord()) {
         WorldChunk chunk = level.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
         if (chunk == null) {
            return false;
         }

         ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
         if (section.isEmpty()) {
            return false;
         }

         if (!section.getBlockStateContainer().hasAny(AbstractBlockState::isAir)) {
            return true;
         }

         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  if (section.getBlockState(x, y, z).isAir()) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return true;
      }
   }
}
