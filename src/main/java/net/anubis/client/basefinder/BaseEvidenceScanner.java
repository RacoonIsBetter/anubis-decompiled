package net.anubis.client.basefinder;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Thickness;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Scores one loaded chunk for signs of player activity. Not thread-safe; one instance per module.
 */
@Environment(EnvType.CLIENT)
public final class BaseEvidenceScanner {
   /** Blocks must be at least this far below the column's surface to count as buried. */
   private static final int BURIED_DEPTH = 6;
   /** Tunnels and shafts must be at least this far below the surface. */
   private static final int TUNNEL_DEPTH = 10;
   private static final int GRID_MIN_Y = -64;
   private static final int GRID_MAX_Y = 72;
   private static final int SHAFT_MIN_HEIGHT = 10;
   private static final int LONG_DRIPSTONE = 8;
   /** Side of the square floor a dug-out room must have, all at one floor and one ceiling height. */
   private static final int ROOM_SIZE = 5;
   /** A surface block only counts as part of a build when it sits in a straight line of this many. */
   private static final int SURFACE_LINE = 3;
   /** Below this many lined-up surface blocks, a chunk's surface is treated as scattered junk. */
   private static final int SURFACE_BUILD_MIN = 6;
   private static final byte AIR = 0;
   private static final byte SOLID = 1;
   private static final byte OTHER = 2;

   private static final byte NONE = 0;
   private static final byte STORAGE_ANY = 1;
   private static final byte STORAGE_BURIED = 2;
   private static final byte CHEST = 3;
   private static final byte UTILITY_ANY = 4;
   private static final byte UTILITY_BURIED = 5;
   private static final byte REDSTONE = 6;
   private static final byte LIGHT = 7;
   private static final byte BUILDING = 8;
   private static final byte OBSIDIAN = 9;
   private static final byte DEEPSLATE = 10;
   private static final byte AMETHYST_CLUSTER = 11;
   private static final byte AMETHYST_BUD = 12;
   private static final byte KELP_TOP = 13;
   private static final byte DRIPSTONE = 14;
   private static final byte MARK_STRONGHOLD = 15;
   private static final byte MARK_ANCIENT_CITY = 16;
   private static final byte MARK_TRIAL = 17;
   private static final byte MARK_RUINED_PORTAL = 18;
   private static final byte MARK_MONUMENT = 19;
   private static final byte MARK_SPAWNER = 20;
   private static final byte MARK_MOSSY_COBBLE = 21;
   /** Player-only building blocks (concrete, stained glass, quartz, metal blocks): buried or lined up on the surface. */
   private static final byte CRAFTED_BUILDING = 22;
   private static final byte SURFACE_BUILD = 23;
   private static final byte STORAGE_SURFACE = 24;
   private static final byte UTILITY_SURFACE = 25;
   private static final byte SIGN = 26;
   private static final byte PORTAL = 27;
   private static final byte FARMLAND = 28;
   private static final byte CATEGORY_COUNT = 29;

   private final Map<Block, Byte> categories = new IdentityHashMap<>();
   private final int[] surface = new int[256];
   private boolean masked;
   private final int[] found = new int[CATEGORY_COUNT];
   private final long[] firstAt = new long[CATEGORY_COUNT];
   private byte[] grid = new byte[0];
   private final BlockPos.Mutable cursor = new BlockPos.Mutable();

   public ChunkReport scan(ClientWorld level, WorldChunk chunk, Options options) {
      int baseX = chunk.getPos().getStartX();
      int baseZ = chunk.getPos().getStartZ();
      int gridMin = Math.max(GRID_MIN_Y, level.getBottomY());
      int gridMax = Math.min(GRID_MAX_Y, level.getTopYInclusive());
      int gridHeight = Math.max(0, gridMax - gridMin + 1);
      boolean wantGrid = options.tunnels() && gridHeight > 0;
      if (wantGrid && this.grid.length < gridHeight * 256) {
         this.grid = new byte[gridHeight * 256];
      }

      if (wantGrid) {
         Arrays.fill(this.grid, 0, gridHeight * 256, AIR);
      }

      this.masked = false;
      Arrays.fill(this.found, 0);
      Arrays.fill(this.firstAt, 0L);
      int oceanColumns = 0;

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
            this.surface[z << 4 | x] = top;
            if (chunk.getBlockState(this.cursor.set(baseX + x, top, baseZ + z)).isOf(Blocks.WATER)) {
               oceanColumns++;
            }
         }
      }

      int amethystClusters = 0;
      int amethystTotal = 0;
      int kelpColumns = 0;
      int kelpGrown = 0;
      long amethystAt = 0L;
      long kelpAt = 0L;
      ChunkSection[] sections = chunk.getSectionArray();
      int bottomSection = chunk.getBottomSectionCoord();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         int baseY = bottomSection + i << 4;
         if (section == null || section.isEmpty()) {
            // Some plugins send the hidden underground as air instead; a whole empty section below Y=0 never generates.
            if (baseY < 0) {
               this.masked = true;
            }

            continue;
         }

         // Anti-xray / anti-freecam plugins (DonutSMP's, ChunkVeil, Phantom) send the underground as
         // nothing but deepslate until you can see it. Real deepslate layers always have tuff, ores or gravel.
         if (baseY < 0 && !section.hasAny(state -> !state.isAir() && !state.isOf(Blocks.DEEPSLATE))) {
            this.masked = true;
         }

         boolean inGrid = wantGrid && baseY + 15 >= gridMin && baseY <= gridMax;
         if (!inGrid && !section.hasAny(this::interesting)) {
            continue;
         }

         for (int y = 0; y < 16; y++) {
            int worldY = baseY + y;
            boolean gridRow = inGrid && worldY >= gridMin && worldY <= gridMax;
            int rowOffset = gridRow ? (worldY - gridMin) * 256 : 0;

            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (gridRow) {
                     this.grid[rowOffset + (z << 4 | x)] = state.isAir() ? AIR : state.isOpaqueFullCube() ? SOLID : OTHER;
                  }

                  if (state.isAir()) {
                     continue;
                  }

                  byte category = this.category(state.getBlock());
                  if (category == NONE) {
                     continue;
                  }

                  boolean buried = worldY <= this.surface[z << 4 | x] - BURIED_DEPTH;
                  long pos = BlockPos.asLong(baseX + x, worldY, baseZ + z);
                  switch (category) {
                     case DEEPSLATE -> {
                        if (state.contains(Properties.AXIS) && state.get(Properties.AXIS) != Direction.Axis.Y) {
                           this.note(DEEPSLATE, pos);
                        }
                     }
                     case STORAGE_ANY, UTILITY_ANY -> {
                        if (buried) {
                           this.note(category, pos);
                        } else {
                           this.note(category == STORAGE_ANY ? STORAGE_SURFACE : UTILITY_SURFACE, pos);
                        }
                     }
                     case CRAFTED_BUILDING -> {
                        if (buried) {
                           this.note(BUILDING, pos);
                        } else if (this.inLine(level, state.getBlock(), baseX + x, worldY, baseZ + z)) {
                           this.note(SURFACE_BUILD, pos);
                        }
                     }
                     case MARK_STRONGHOLD, MARK_ANCIENT_CITY, MARK_TRIAL, MARK_RUINED_PORTAL, MARK_MONUMENT, MARK_SPAWNER,
                        MARK_MOSSY_COBBLE -> this.note(category, pos);
                     case SIGN, PORTAL -> this.note(category, pos);
                     case STORAGE_BURIED, CHEST, UTILITY_BURIED, REDSTONE, LIGHT, BUILDING, OBSIDIAN, FARMLAND -> {
                        if (buried) {
                           this.note(category, pos);
                        }
                     }
                     case AMETHYST_CLUSTER, AMETHYST_BUD -> {
                        amethystTotal++;
                        if (category == AMETHYST_CLUSTER) {
                           amethystClusters++;
                        }

                        if (amethystAt == 0L) {
                           amethystAt = pos;
                        }
                     }
                     case KELP_TOP -> {
                        kelpColumns++;
                        BlockState above = chunk.getBlockState(this.cursor.set(baseX + x, worldY + 1, baseZ + z));
                        BlockState twoAbove = chunk.getBlockState(this.cursor.set(baseX + x, worldY + 2, baseZ + z));
                        if (above.isOf(Blocks.WATER) && !twoAbove.isOf(Blocks.WATER) && !twoAbove.isOf(Blocks.KELP_PLANT)) {
                           kelpGrown++;
                           if (kelpAt == 0L) {
                              kelpAt = pos;
                           }
                        }
                     }
                     case DRIPSTONE -> {
                        if (state.get(Properties.THICKNESS) == Thickness.TIP && state.get(Properties.VERTICAL_DIRECTION) == Direction.DOWN
                           && this.dripstoneLength(chunk, baseX + x, worldY, baseZ + z) >= LONG_DRIPSTONE) {
                           this.note(DRIPSTONE, pos);
                        }
                     }
                     default -> {
                     }
                  }
               }
            }
         }
      }

      ChunkReport report = new ChunkReport(chunk.getPos().toLong());
      if (options.blocks()) {
         this.scoreBlocks(report, oceanColumns > 128);
      }

      if (options.growth()) {
         if (amethystTotal >= 6) {
            float ratio = (float)amethystClusters / amethystTotal;
            if (ratio >= 0.6F) {
               report.set(Signal.AMETHYST, (ratio - 0.5F) / 0.5F, Math.round(ratio * 100.0F), amethystAt);
            }
         }

         if (kelpColumns >= 5) {
            float ratio = (float)kelpGrown / kelpColumns;
            if (ratio >= 0.5F) {
               report.set(Signal.KELP, (ratio - 0.4F) / 0.6F, Math.round(ratio * 100.0F), kelpAt);
            }
         }

         this.give(report, Signal.DRIPSTONE, DRIPSTONE);
      }

      if (wantGrid) {
         this.scanTunnels(report, baseX, baseZ, gridMin, gridHeight, options.tunnelLength());
         if (!this.structure()) {
            this.scanRooms(report, baseX, baseZ, gridMin, gridHeight);
         }
      }

      return report.isEmpty() ? null : report;
   }

   /** Strongholds, ancient cities, trial chambers, dungeons and monuments have flat rooms and decorations of their own. */
   private boolean structure() {
      return this.found[MARK_STRONGHOLD] > 0 || this.found[MARK_ANCIENT_CITY] > 0 || this.found[MARK_TRIAL] > 0 || this.found[MARK_MONUMENT] > 0
         || this.found[MARK_SPAWNER] > 0 && this.found[MARK_MOSSY_COBBLE] > 0;
   }

   /** Whether the last scanned chunk had underground sections the server replaced with plain deepslate. */
   public boolean masked() {
      return this.masked;
   }

   private void scoreBlocks(ChunkReport report, boolean ocean) {
      boolean stronghold = this.found[MARK_STRONGHOLD] > 0;
      boolean ancientCity = this.found[MARK_ANCIENT_CITY] > 0;
      boolean trial = this.found[MARK_TRIAL] > 0;
      boolean dungeon = this.found[MARK_SPAWNER] > 0 && this.found[MARK_MOSSY_COBBLE] > 0;
      boolean structure = stronghold || ancientCity || trial;
      boolean surfaceBuild = this.found[SURFACE_BUILD] >= SURFACE_BUILD_MIN;
      this.give(report, Signal.ROTATED_DEEPSLATE, DEEPSLATE);
      this.give(report, Signal.STORAGE, STORAGE_ANY);
      if (surfaceBuild) {
         this.give(report, Signal.SURFACE_BUILD, SURFACE_BUILD);
      }

      // Storage and utility blocks lying around on the surface with no build next to them are
      // usually junk (dropped loot, griefed spawn, highway litter): they count once, not per block.
      this.giveSurface(report, Signal.STORAGE, STORAGE_SURFACE, surfaceBuild);
      this.giveSurface(report, Signal.UTILITY, UTILITY_SURFACE, surfaceBuild);
      if (!structure) {
         this.give(report, Signal.STORAGE, STORAGE_BURIED);
         if (!dungeon && !ocean) {
            this.give(report, Signal.STORAGE, CHEST);
         }

         this.give(report, Signal.UTILITY, UTILITY_BURIED);
         this.give(report, Signal.LIGHTING, LIGHT);
         if (!ocean) {
            this.give(report, Signal.BUILDING, BUILDING);
         }
      }

      this.give(report, Signal.UTILITY, UTILITY_ANY);
      if (!this.structure()) {
         this.give(report, Signal.SIGNS, SIGN);
      }

      // Ruined portals are never lit: a lit portal was lit by a player.
      if (this.found[PORTAL] > 0) {
         report.add(Signal.NETHER_PORTAL, 1, this.firstAt[PORTAL]);
      }

      this.give(report, Signal.FARM, FARMLAND);
      if (!ancientCity && !trial) {
         this.give(report, Signal.REDSTONE, REDSTONE);
      }

      if (this.found[MARK_MONUMENT] > 0) {
         report.clear(Signal.LIGHTING);
      }

      if (this.found[MARK_RUINED_PORTAL] == 0) {
         this.give(report, Signal.OBSIDIAN, OBSIDIAN);
      }
   }

   private void give(ChunkReport report, Signal signal, byte category) {
      if (this.found[category] > 0) {
         report.add(signal, this.found[category], this.firstAt[category]);
      }
   }

   private void giveSurface(ChunkReport report, Signal signal, byte category, boolean surfaceBuild) {
      if (this.found[category] > 0) {
         report.add(signal, surfaceBuild ? this.found[category] : 1, this.firstAt[category]);
      }
   }

   /** True when {@code block} continues on both sides along one axis: part of a wall, floor or pillar, not a lone block. */
   private boolean inLine(ClientWorld level, Block block, int x, int y, int z) {
      for (Direction.Axis axis : Direction.Axis.values()) {
         int run = 1;

         for (int sign = -1; sign <= 1; sign += 2) {
            int dx = axis == Direction.Axis.X ? sign : 0;
            int dy = axis == Direction.Axis.Y ? sign : 0;
            int dz = axis == Direction.Axis.Z ? sign : 0;

            for (int step = 1; step < SURFACE_LINE && level.getBlockState(this.cursor.set(x + dx * step, y + dy * step, z + dz * step)).isOf(block); step++) {
               run++;
            }
         }

         if (run >= SURFACE_LINE) {
            return true;
         }
      }

      return false;
   }

   private void note(byte category, long pos) {
      if (this.found[category]++ == 0) {
         this.firstAt[category] = pos;
      }
   }

   /**
    * Straight 1-wide, 2-tall corridors with solid floor, ceiling and walls, and 1x1 vertical
    * shafts walled on all four sides. Caves almost never make these; players do constantly.
    */
   private void scanTunnels(ChunkReport report, int baseX, int baseZ, int gridMin, int height, int minLength) {
      for (int y = 1; y + 2 < height; y++) {
         for (int z = 1; z < 15; z++) {
            int run = 0;

            for (int x = 0; x < 16; x++) {
               if (this.corridor(x, y, z, true, gridMin)) {
                  if (++run == minLength) {
                     report.add(Signal.TUNNEL, 1, BlockPos.asLong(baseX + x - minLength / 2, gridMin + y, baseZ + z));
                  }
               } else {
                  run = 0;
               }
            }
         }

         for (int x = 1; x < 15; x++) {
            int run = 0;

            for (int z = 0; z < 16; z++) {
               if (this.corridor(x, y, z, false, gridMin)) {
                  if (++run == minLength) {
                     report.add(Signal.TUNNEL, 1, BlockPos.asLong(baseX + x, gridMin + y, baseZ + z - minLength / 2));
                  }
               } else {
                  run = 0;
               }
            }
         }
      }

      for (int z = 1; z < 15; z++) {
         for (int x = 1; x < 15; x++) {
            int run = 0;

            for (int y = 1; y < height - 1; y++) {
               boolean deep = gridMin + y <= this.surface[z << 4 | x] - TUNNEL_DEPTH;
               if (deep && this.at(x, y, z) == AIR && this.walled(x, y, z)) {
                  if (++run == SHAFT_MIN_HEIGHT) {
                     report.add(Signal.SHAFT, 1, BlockPos.asLong(baseX + x, gridMin + y - SHAFT_MIN_HEIGHT / 2, baseZ + z));
                  }
               } else {
                  run = 0;
               }
            }
         }
      }
   }

   /**
    * Deep rooms with a flat solid floor and a flat solid ceiling over at least a 5x5 area. Caves are
    * never that even; players clearing out a base are.
    */
   private void scanRooms(ChunkReport report, int baseX, int baseZ, int gridMin, int height) {
      boolean[] used = new boolean[256];

      for (int y = 1; y + 4 < height; y++) {
         Arrays.fill(used, false);

         for (int z0 = 0; z0 + ROOM_SIZE <= 16; z0++) {
            for (int x0 = 0; x0 + ROOM_SIZE <= 16; x0++) {
               if (this.room(x0, y, z0, gridMin, used)) {
                  for (int dz = 0; dz < ROOM_SIZE; dz++) {
                     for (int dx = 0; dx < ROOM_SIZE; dx++) {
                        used[z0 + dz << 4 | x0 + dx] = true;
                     }
                  }

                  report.add(Signal.ROOM, 1, BlockPos.asLong(baseX + x0 + ROOM_SIZE / 2, gridMin + y, baseZ + z0 + ROOM_SIZE / 2));
               }
            }
         }
      }
   }

   private boolean room(int x0, int y, int z0, int gridMin, boolean[] used) {
      int ceiling = -1;

      for (int dz = 0; dz < ROOM_SIZE; dz++) {
         for (int dx = 0; dx < ROOM_SIZE; dx++) {
            int x = x0 + dx;
            int z = z0 + dz;
            if (used[z << 4 | x] || gridMin + y + 1 > this.surface[z << 4 | x] - TUNNEL_DEPTH) {
               return false;
            }

            if (this.at(x, y - 1, z) != SOLID || this.at(x, y, z) != AIR || this.at(x, y + 1, z) != AIR) {
               return false;
            }

            int top = y + 2;
            while (top < y + 4 && this.at(x, top, z) == AIR) {
               top++;
            }

            if (this.at(x, top, z) != SOLID || ceiling >= 0 && top != ceiling) {
               return false;
            }

            ceiling = top;
         }
      }

      return true;
   }

   private boolean corridor(int x, int y, int z, boolean alongX, int gridMin) {
      if (gridMin + y + 1 > this.surface[z << 4 | x] - TUNNEL_DEPTH) {
         return false;
      }

      if (this.at(x, y, z) != AIR || this.at(x, y + 1, z) != AIR || this.at(x, y - 1, z) != SOLID || this.at(x, y + 2, z) != SOLID) {
         return false;
      }

      return alongX
         ? this.at(x, y, z - 1) == SOLID && this.at(x, y, z + 1) == SOLID && this.at(x, y + 1, z - 1) == SOLID && this.at(x, y + 1, z + 1) == SOLID
         : this.at(x - 1, y, z) == SOLID && this.at(x + 1, y, z) == SOLID && this.at(x - 1, y + 1, z) == SOLID && this.at(x + 1, y + 1, z) == SOLID;
   }

   private boolean walled(int x, int y, int z) {
      return this.at(x - 1, y, z) == SOLID && this.at(x + 1, y, z) == SOLID && this.at(x, y, z - 1) == SOLID && this.at(x, y, z + 1) == SOLID;
   }

   private byte at(int x, int y, int z) {
      return this.grid[y * 256 + (z << 4 | x)];
   }

   private int dripstoneLength(WorldChunk chunk, int x, int tipY, int z) {
      int length = 0;
      int y = tipY;

      while (length < 32 && chunk.getBlockState(this.cursor.set(x, y, z)).isOf(Blocks.POINTED_DRIPSTONE)) {
         length++;
         y++;
      }

      return length;
   }

   private boolean interesting(BlockState state) {
      return this.category(state.getBlock()) != NONE;
   }

   private byte category(Block block) {
      Byte cached = this.categories.get(block);
      if (cached == null) {
         cached = classify(block);
         this.categories.put(block, cached);
      }

      return cached;
   }

   private static byte classify(Block block) {
      BlockState state = block.getDefaultState();
      if (block == Blocks.DEEPSLATE) {
         return DEEPSLATE;
      } else if (state.isIn(BlockTags.ALL_SIGNS) || state.isIn(BlockTags.BANNERS) || block instanceof AbstractSkullBlock) {
         return SIGN;
      } else if (block == Blocks.NETHER_PORTAL) {
         return PORTAL;
      } else if (block == Blocks.FARMLAND || state.isIn(BlockTags.CROPS)) {
         return FARMLAND;
      } else if (state.isIn(BlockTags.SHULKER_BOXES) || block == Blocks.ENDER_CHEST) {
         return STORAGE_ANY;
      } else if (block == Blocks.BARREL) {
         return STORAGE_BURIED;
      } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
         return CHEST;
      } else if (block == Blocks.BEACON || block == Blocks.ENCHANTING_TABLE || block == Blocks.RESPAWN_ANCHOR || block == Blocks.CRAFTER
         || state.isIn(BlockTags.ANVIL)) {
         return UTILITY_ANY;
      } else if (block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
         || block == Blocks.BREWING_STAND || block == Blocks.SMITHING_TABLE || block == Blocks.GRINDSTONE || block == Blocks.STONECUTTER
         || block == Blocks.LOOM || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE || state.isIn(BlockTags.BEDS)) {
         return UTILITY_BURIED;
      } else if (block == Blocks.REDSTONE_WIRE || block == Blocks.REPEATER || block == Blocks.COMPARATOR || block == Blocks.OBSERVER
         || block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.HOPPER || block == Blocks.DROPPER || block == Blocks.DISPENSER
         || block == Blocks.REDSTONE_LAMP || block == Blocks.DAYLIGHT_DETECTOR || block == Blocks.TARGET || block == Blocks.NOTE_BLOCK
         || block == Blocks.LEVER || block == Blocks.REDSTONE_BLOCK) {
         return REDSTONE;
      } else if (block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH
         || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN || block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT
         || block == Blocks.SEA_LANTERN || block == Blocks.REDSTONE_TORCH || block == Blocks.REDSTONE_WALL_TORCH || block == Blocks.JACK_O_LANTERN
         || block == Blocks.END_ROD || block == Blocks.OCHRE_FROGLIGHT || block == Blocks.VERDANT_FROGLIGHT || block == Blocks.PEARLESCENT_FROGLIGHT) {
         return LIGHT;
      } else if (block == Blocks.OBSIDIAN) {
         return OBSIDIAN;
      } else if (block == Blocks.AMETHYST_CLUSTER) {
         return AMETHYST_CLUSTER;
      } else if (block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD) {
         return AMETHYST_BUD;
      } else if (block == Blocks.KELP) {
         return KELP_TOP;
      } else if (block == Blocks.POINTED_DRIPSTONE) {
         return DRIPSTONE;
      } else if (block == Blocks.END_PORTAL_FRAME || block == Blocks.MOSSY_STONE_BRICKS || block == Blocks.CRACKED_STONE_BRICKS
         || block == Blocks.INFESTED_STONE_BRICKS) {
         return MARK_STRONGHOLD;
      } else if (block == Blocks.REINFORCED_DEEPSLATE || block == Blocks.SCULK_SHRIEKER) {
         return MARK_ANCIENT_CITY;
      } else if (block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT) {
         return MARK_TRIAL;
      } else if (block == Blocks.CRYING_OBSIDIAN || block == Blocks.NETHERRACK) {
         return MARK_RUINED_PORTAL;
      } else if (block == Blocks.PRISMARINE_BRICKS || block == Blocks.DARK_PRISMARINE) {
         return MARK_MONUMENT;
      } else if (block == Blocks.SPAWNER) {
         return MARK_SPAWNER;
      } else if (block == Blocks.MOSSY_COBBLESTONE) {
         return MARK_MOSSY_COBBLE;
      }

      String path = Registries.BLOCK.getId(block).getPath();
      if (path.endsWith("_concrete") || path.contains("stained_glass") || path.startsWith("quartz_") || path.startsWith("smooth_quartz")
         || block == Blocks.IRON_BLOCK || block == Blocks.DIAMOND_BLOCK || block == Blocks.EMERALD_BLOCK || block == Blocks.NETHERITE_BLOCK
         || block == Blocks.LAPIS_BLOCK) {
         return CRAFTED_BUILDING;
      }

      if (path.endsWith("_concrete") || path.endsWith("glass") || path.endsWith("glass_pane") || path.endsWith("_wool") || path.endsWith("_carpet")
         || path.equals("bricks") || path.equals("quartz_block") || path.equals("smooth_stone") || path.startsWith("polished_blackstone")) {
         return BUILDING;
      }

      return NONE;
   }

   @Environment(EnvType.CLIENT)
   public record Options(boolean blocks, boolean tunnels, boolean growth, int tunnelLength) {
   }
}
