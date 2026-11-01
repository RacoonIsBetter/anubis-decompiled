package net.anubis.client.util;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

@Environment(EnvType.CLIENT)
public final class HiddenAmethyst {
   private static final int TOP_SECTION = 2;
   private static final int RING_LIGHT = 4;
   private static final int CLUSTER_LIGHT = 5;
   private static final int BRIGHT_NEIGHBOUR = 2;
   private static final int UNKNOWN_BLOCKER = 15;
   private static final Direction[] SIDES = Direction.values();
   private static final BlockState VOID = Blocks.VOID_AIR.getDefaultState();

   private HiddenAmethyst() {
   }

   public static HiddenAmethyst.Reading read(ClientWorld level, WorldChunk chunk, ServerBlockLight light, Boolean hiddenSolids) {
      int minSection = chunk.getBottomSectionCoord();
      int topSection = Math.min(2, chunk.getTopSectionCoord());
      if (topSection < minSection) {
         return HiddenAmethyst.Reading.NONE;
      }

      boolean[] own = geodeSections(chunk, minSection, topSection);
      boolean found = false;

      for (boolean section : own) {
         found |= section;
      }

      return !found ? HiddenAmethyst.Reading.NONE : new HiddenAmethyst.Scan(level, chunk, light, hiddenSolids).run(own, minSection, topSection);
   }

   private static boolean[] geodeSections(WorldChunk chunk, int minSection, int topSection) {
      boolean[] out = new boolean[topSection - minSection + 1];
      if (chunk == null) {
         return out;
      }

      ChunkSection[] sections = chunk.getSectionArray();

      for (int sectionY = minSection; sectionY <= topSection; sectionY++) {
         ChunkSection section = sections[chunk.sectionCoordToIndex(sectionY)];
         out[sectionY - minSection] = section != null && !section.isEmpty() && section.hasAny(HiddenAmethyst::isAmethyst);
      }

      return out;
   }

   private static boolean isLining(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.AMETHYST_BLOCK || block == Blocks.BUDDING_AMETHYST;
   }

   private static boolean isCrystal(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD
         || block == Blocks.MEDIUM_AMETHYST_BUD
         || block == Blocks.LARGE_AMETHYST_BUD
         || block == Blocks.AMETHYST_CLUSTER;
   }

   private static boolean isAmethyst(BlockState state) {
      return isLining(state) || isCrystal(state);
   }

   private static boolean isOpen(BlockState state) {
      return state.isAir() && state != VOID || state.isOf(Blocks.WATER);
   }

   private static boolean isGrowthLight(int light) {
      return light == 1 || light == 2 || light == 4 || light == 5;
   }

   @Environment(EnvType.CLIENT)
   public record Reading(int buds, int clusters, int darkVotes, int glowVotes) {
      public static final HiddenAmethyst.Reading NONE = new HiddenAmethyst.Reading(0, 0, 0, 0);
   }

   @Environment(EnvType.CLIENT)
   private static final class Scan {
      private final WorldChunk[] chunks = new WorldChunk[9];
      private final ServerBlockLight.Column[] columns = new ServerBlockLight.Column[9];
      private final int chunkX;
      private final int chunkZ;
      private final ClientWorld level;
      private final boolean solidsPass;
      private final boolean solidRing;
      private final Long2ByteOpenHashMap darkClusters = new Long2ByteOpenHashMap();

      private Scan(ClientWorld level, WorldChunk chunk, ServerBlockLight light, Boolean hiddenSolids) {
         this.level = level;
         this.chunkX = chunk.getPos().x;
         this.chunkZ = chunk.getPos().z;
         this.solidsPass = Boolean.TRUE.equals(hiddenSolids);
         this.solidRing = !Boolean.FALSE.equals(hiddenSolids);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               int slot = (dx + 1) * 3 + dz + 1;
               this.chunks[slot] = dx == 0 && dz == 0 ? chunk : level.getChunkManager().getWorldChunk(this.chunkX + dx, this.chunkZ + dz, false);
               this.columns[slot] = light.column(level, this.chunkX + dx, this.chunkZ + dz);
            }
         }

         this.darkClusters.defaultReturnValue((byte)-1);
      }

      private HiddenAmethyst.Reading run(boolean[] own, int minSection, int topSection) {
         boolean[][] sides = new boolean[][]{
            HiddenAmethyst.geodeSections(this.chunks[this.slot(1, 0)], minSection, topSection),
            HiddenAmethyst.geodeSections(this.chunks[this.slot(-1, 0)], minSection, topSection),
            HiddenAmethyst.geodeSections(this.chunks[this.slot(0, 1)], minSection, topSection),
            HiddenAmethyst.geodeSections(this.chunks[this.slot(0, -1)], minSection, topSection)
         };
         int buds = 0;
         int clusters = 0;
         int darkVotes = 0;
         int glowVotes = 0;
         int minX = this.chunkX << 4;
         int minZ = this.chunkZ << 4;

         for (int sectionY = minSection; sectionY <= topSection; sectionY++) {
            int index = sectionY - minSection;
            boolean near = own[index] || index > 0 && own[index - 1] || index + 1 < own.length && own[index + 1];

            for (boolean[] side : sides) {
               near |= side[index];
            }

            if (near) {
               int baseY = sectionY << 4;

               for (int y = baseY; y < baseY + 16; y++) {
                  for (int z = minZ; z < minZ + 16; z++) {
                     for (int x = minX; x < minX + 16; x++) {
                        BlockState state = this.state(x, y, z);
                        if (!HiddenAmethyst.isCrystal(state)) {
                           int value = this.light(x, y, z);
                           if (value == 0) {
                              if (this.brightestStored(x, y, z) >= 2 && this.darkCandidate(x, y, z, state)) {
                                 if (this.darkNextToGlow(x, y, z, state)) {
                                    darkVotes++;
                                 }

                                 if (this.darkCluster(x, y, z)) {
                                    clusters++;
                                 }
                              }
                           } else if (value >= 0
                              && this.passesLight(x, y, z, state, value)
                              && this.touchesLining(x, y, z)
                              && value >= this.brightestAround(x, y, z)
                              && (value != 4 || !this.besideDarkCluster(x, y, z) && (!this.solidRing || !this.besideDarkSolid(x, y, z)))
                              && HiddenAmethyst.isGrowthLight(value)
                              && this.fadesEvenly(x, y, z, value)) {
                              if (value == 5) {
                                 glowVotes++;
                                 clusters++;
                              } else {
                                 buds++;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return new HiddenAmethyst.Reading(buds, clusters, darkVotes, glowVotes);
      }

      private int slot(int dx, int dz) {
         return (dx + 1) * 3 + dz + 1;
      }

      private int slotOf(int x, int z) {
         int dx = (x >> 4) - this.chunkX;
         int dz = (z >> 4) - this.chunkZ;
         return dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 ? this.slot(dx, dz) : -1;
      }

      private BlockState state(int x, int y, int z) {
         if (this.level.isOutOfHeightLimit(y)) {
            return HiddenAmethyst.VOID;
         }

         int slot = this.slotOf(x, z);
         if (slot < 0) {
            return this.level.getBlockState(new BlockPos(x, y, z));
         }

         WorldChunk chunk = this.chunks[slot];
         if (chunk == null) {
            return HiddenAmethyst.VOID;
         }

         ChunkSection section = chunk.getSectionArray()[chunk.getSectionIndex(y)];
         return section == null ? HiddenAmethyst.VOID : section.getBlockState(x & 15, y & 15, z & 15);
      }

      private int light(int x, int y, int z) {
         int slot = this.slotOf(x, z);
         ServerBlockLight.Column column = slot < 0 ? null : this.columns[slot];
         return column == null ? -1 : column.get(x, y, z);
      }

      private boolean touchesLining(int x, int y, int z) {
         for (Direction side : HiddenAmethyst.SIDES) {
            if (HiddenAmethyst.isLining(this.state(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ()))) {
               return true;
            }
         }

         return false;
      }

      private boolean hiddenSolid(int x, int y, int z, BlockState state) {
         return state != HiddenAmethyst.VOID
            && !HiddenAmethyst.isOpen(state)
            && !HiddenAmethyst.isAmethyst(state)
            && state.getLuminance() == 0
            && this.touchesLining(x, y, z);
      }

      private boolean solidPasses(int x, int y, int z, BlockState state) {
         return this.solidsPass && this.hiddenSolid(x, y, z, state);
      }

      private boolean passesLight(int x, int y, int z, BlockState state, int value) {
         return HiddenAmethyst.isOpen(state) || value > 0 && this.solidPasses(x, y, z, state);
      }

      private boolean passesLight(int x, int y, int z) {
         BlockState state = this.state(x, y, z);
         return HiddenAmethyst.isOpen(state) || this.solidPasses(x, y, z, state) && this.light(x, y, z) > 0;
      }

      private boolean darkCandidate(int x, int y, int z, BlockState state) {
         return (HiddenAmethyst.isOpen(state) || this.solidPasses(x, y, z, state)) && this.touchesLining(x, y, z);
      }

      private boolean darkNextToGlow(int x, int y, int z, BlockState state) {
         if (HiddenAmethyst.isOpen(state) && this.light(x, y, z) == 0) {
            for (Direction side : HiddenAmethyst.SIDES) {
               int nx = x + side.getOffsetX();
               int ny = y + side.getOffsetY();
               int nz = z + side.getOffsetZ();
               if (this.passesLight(nx, ny, nz) && this.light(nx, ny, nz) >= 2) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }

      private boolean countsAsNeighbour(int x, int y, int z) {
         return this.passesLight(x, y, z) && !this.darkNextToGlow(x, y, z, this.state(x, y, z));
      }

      private boolean darkCluster(int x, int y, int z) {
         long key = BlockPos.asLong(x, y, z);
         byte known = this.darkClusters.get(key);
         if (known >= 0) {
            return known == 1;
         }

         boolean cluster = false;
         BlockState state = this.state(x, y, z);
         if (this.light(x, y, z) == 0 && this.darkCandidate(x, y, z, state)) {
            int strong = 0;
            boolean weak = false;

            for (Direction side : HiddenAmethyst.SIDES) {
               int nx = x + side.getOffsetX();
               int ny = y + side.getOffsetY();
               int nz = z + side.getOffsetZ();
               if (this.countsAsNeighbour(nx, ny, nz)) {
                  int around = this.light(nx, ny, nz);
                  if (around != -1) {
                     if (around >= 4) {
                        strong++;
                     } else {
                        weak = true;
                     }
                  }
               }
            }

            cluster = !weak && strong >= (HiddenAmethyst.isOpen(state) ? 1 : 2);
         }

         this.darkClusters.put(key, (byte)(cluster ? 1 : 0));
         return cluster;
      }

      private boolean besideDarkCluster(int x, int y, int z) {
         for (Direction side : HiddenAmethyst.SIDES) {
            if (this.darkCluster(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ())) {
               return true;
            }
         }

         return false;
      }

      private boolean besideDarkSolid(int x, int y, int z) {
         for (Direction side : HiddenAmethyst.SIDES) {
            int nx = x + side.getOffsetX();
            int ny = y + side.getOffsetY();
            int nz = z + side.getOffsetZ();
            if (this.light(nx, ny, nz) == 0 && this.hiddenSolid(nx, ny, nz, this.state(nx, ny, nz))) {
               return true;
            }
         }

         return false;
      }

      private int seenLight(int x, int y, int z) {
         int value = this.light(x, y, z);
         if (value != -1) {
            return value;
         }

         BlockState state = this.state(x, y, z);
         boolean blocks = state != HiddenAmethyst.VOID && !HiddenAmethyst.isOpen(state) && !this.solidPasses(x, y, z, state) && state.getLuminance() == 0;
         return blocks ? 0 : 15;
      }

      private int brightestStored(int x, int y, int z) {
         int best = -1;

         for (Direction side : HiddenAmethyst.SIDES) {
            best = Math.max(best, this.light(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ()));
         }

         return best;
      }

      private int brightestAround(int x, int y, int z) {
         int best = 0;

         for (Direction side : HiddenAmethyst.SIDES) {
            best = Math.max(best, this.seenLight(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ()));
         }

         return best;
      }

      private boolean fadesEvenly(int x, int y, int z, int value) {
         for (Direction side : HiddenAmethyst.SIDES) {
            int nx = x + side.getOffsetX();
            int ny = y + side.getOffsetY();
            int nz = z + side.getOffsetZ();
            if (this.countsAsNeighbour(nx, ny, nz)) {
               int around = this.light(nx, ny, nz);
               if (around != -1 && around < value - 1) {
                  return false;
               }
            }
         }

         return true;
      }
   }
}
