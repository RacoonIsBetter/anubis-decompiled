package net.anubis.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightingView;

@Environment(EnvType.CLIENT)
public final class AmethystLight {
   public static final int SMALL_BUD = 1;
   public static final int MEDIUM_BUD = 2;
   public static final int LARGE_BUD = 4;
   public static final int CLUSTER = 5;
   private static final Direction[] SIDES = Direction.values();

   private AmethystLight() {
   }

   public static void scan(ClientWorld level, WorldChunk chunk, AmethystLight.Sink out) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();
      ChunkPos pos = chunk.getPos();
      Mutable cursor = new Mutable();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty() && section.getBlockStateContainer().hasAny(AmethystLight::isLining)) {
            int baseY = (minSectionY + i) * 16;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (section.getBlockState(x, y, z).getLuminance() <= 0) {
                        cursor.set(pos.getStartX() + x, baseY + y, pos.getStartZ() + z);
                        int light = level.getLightLevel(LightType.BLOCK, cursor);
                        if (isGrowthLight(light) && touchesLining(level, cursor) && isLocalMaximum(level, cursor, light)) {
                           out.accept(cursor.asLong(), light);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void scanSources(ClientWorld level, WorldChunk chunk, AmethystLight.Sink out) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();
      ChunkPos pos = chunk.getPos();
      AmethystLight.LightCache light = new AmethystLight.LightCache(level, pos.x, pos.z, minSectionY, sections.length);
      Mutable cursor = new Mutable();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty() && !light.quiet(i) && section.getBlockStateContainer().hasAny(AmethystLight::isLining)) {
            int baseY = (minSectionY + i) * 16;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     BlockState state = section.getBlockState(x, y, z);
                     if (state.getLuminance() <= 0) {
                        int wx = pos.getStartX() + x;
                        int wy = baseY + y;
                        int wz = pos.getStartZ() + z;
                        int value = light.get(wx, wy, wz);
                        int brightest = light.brightestAround(wx, wy, wz);
                        int source;
                        if (value == 0) {
                           if (brightest != 4 && brightest != 3 || !isOpen(state)) {
                              continue;
                           }

                           cursor.set(wx, wy, wz);
                           if (!touchesLining(level, cursor) || !litOpening(level, light, cursor, brightest)) {
                              continue;
                           }

                           source = brightest == 4 ? 5 : 4;
                        } else {
                           if (!isGrowthLight(value) || brightest >= value) {
                              continue;
                           }

                           cursor.set(wx, wy, wz);
                           if (!touchesLining(level, cursor) || value == 4 && besideDarkOpening(level, light, cursor)) {
                              continue;
                           }

                           source = value;
                        }

                        out.accept(cursor.asLong(), source);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean litOpening(ClientWorld level, AmethystLight.LightCache light, BlockPos pos, int brightest) {
      Mutable next = new Mutable();

      for (Direction direction : SIDES) {
         next.set(pos, direction);
         if (light.get(next.getX(), next.getY(), next.getZ()) == brightest && isOpen(level.getBlockState(next))) {
            return true;
         }
      }

      return false;
   }

   private static boolean besideDarkOpening(ClientWorld level, AmethystLight.LightCache light, BlockPos pos) {
      Mutable next = new Mutable();

      for (Direction direction : SIDES) {
         next.set(pos, direction);
         if (light.get(next.getX(), next.getY(), next.getZ()) == 0) {
            BlockState state = level.getBlockState(next);
            if (isOpen(state) && !state.isOf(Blocks.VOID_AIR)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean isOpen(BlockState state) {
      return state.isAir() || state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.WATER);
   }

   public static boolean isGrowthLight(int light) {
      return light == 1 || light == 2 || light == 4 || light == 5;
   }

   public static boolean touchesLining(ClientWorld level, BlockPos pos) {
      for (Direction direction : SIDES) {
         if (isLining(level.getBlockState(pos.offset(direction)))) {
            return true;
         }
      }

      return false;
   }

   public static boolean isLining(BlockState state) {
      return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
   }

   private static boolean isLocalMaximum(ClientWorld level, BlockPos pos, int light) {
      for (Direction direction : SIDES) {
         if (level.getLightLevel(LightType.BLOCK, pos.offset(direction)) >= light) {
            return false;
         }
      }

      return true;
   }

   @Environment(EnvType.CLIENT)
   private static final class LightCache {
      private final ChunkLightingView listener;
      private final int chunkX;
      private final int chunkZ;
      private final int minSectionY;
      private final int sectionCount;
      private final ChunkNibbleArray[] layers;
      private final boolean[] fetched;

      private LightCache(ClientWorld level, int chunkX, int chunkZ, int minSectionY, int sectionCount) {
         this.listener = level.getLightingProvider().get(LightType.BLOCK);
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
         this.minSectionY = minSectionY - 1;
         this.sectionCount = sectionCount + 2;
         this.layers = new ChunkNibbleArray[9 * this.sectionCount];
         this.fetched = new boolean[this.layers.length];
      }

      private ChunkNibbleArray layer(int dx, int dz, int section) {
         int slot = ((dx + 1) * 3 + dz + 1) * this.sectionCount + section;
         if (!this.fetched[slot]) {
            this.fetched[slot] = true;
            ChunkNibbleArray data = this.listener.getLightSection(ChunkSectionPos.from(this.chunkX + dx, this.minSectionY + section, this.chunkZ + dz));
            this.layers[slot] = data != null && !data.isUninitialized() ? data : null;
         }

         return this.layers[slot];
      }

      int get(int x, int y, int z) {
         int dx = (x >> 4) - this.chunkX;
         int dz = (z >> 4) - this.chunkZ;
         int section = (y >> 4) - this.minSectionY;
         if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && section >= 0 && section < this.sectionCount) {
            ChunkNibbleArray data = this.layer(dx, dz, section);
            return data == null ? 0 : data.get(x & 15, y & 15, z & 15);
         } else {
            return 0;
         }
      }

      int brightestAround(int x, int y, int z) {
         int best = this.get(x + 1, y, z);
         best = Math.max(best, this.get(x - 1, y, z));
         best = Math.max(best, this.get(x, y + 1, z));
         best = Math.max(best, this.get(x, y - 1, z));
         best = Math.max(best, this.get(x, y, z + 1));
         return Math.max(best, this.get(x, y, z - 1));
      }

      boolean quiet(int sectionIndex) {
         int section = sectionIndex + 1;
         return this.layer(0, 0, section) == null
            && this.layer(0, 0, section - 1) == null
            && this.layer(0, 0, section + 1) == null
            && this.layer(-1, 0, section) == null
            && this.layer(1, 0, section) == null
            && this.layer(0, -1, section) == null
            && this.layer(0, 1, section) == null;
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Sink {
      void accept(long var1, int var3);
   }
}
