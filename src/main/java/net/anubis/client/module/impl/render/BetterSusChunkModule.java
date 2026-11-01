package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.ChunkOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PropaguleBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome.Precipitation;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.ChunkLightProvider;

@Environment(EnvType.CLIENT)
public final class BetterSusChunkModule extends ClientModule {
   private static final String DONUT_BRAND = "DonutFolia";
   private static final int KELP = 0;
   private static final int CAVE_VINES = 1;
   private static final int WEEPING_VINES = 2;
   private static final int BAMBOO = 3;
   private static final int SUGAR_CANE = 4;
   private static final int CACTUS = 5;
   private static final int COCOA = 6;
   private static final int BERRY = 7;
   private static final int NETHER_WART = 8;
   private static final int CROP = 9;
   private static final int STEM = 10;
   private static final int AMETHYST = 11;
   private static final int VINE = 12;
   private static final int LEAVES = 13;
   private static final int SOIL = 14;
   private static final int PROPAGULE = 15;
   private static final int EYEBLOSSOM = 16;
   private static final int SNOW = 17;
   private static final int ICE = 18;
   private static final int EVIDENCE_KINDS = 19;
   private static final int GROWTH_LIGHT = 9;
   private static final int FULL_LIGHT = 15;
   private static final int MELT_LIGHT = 10;
   private static final int BAMBOO_LIMIT = 16;
   private static final int SHORT_COLUMN = 3;
   private static final int KELP_MAX_AGE = 25;
   private static final int DAY_TICKS = 24000;
   private static final int EYEBLOSSOM_OPENS = 12600;
   private static final int EYEBLOSSOM_CLOSES = 23401;
   private static final int EYEBLOSSOM_SETTLE = 4000;
   private static final int EYEBLOSSOM_MIN = 3;
   private static final int HOLE_DEPTH = 2;
   private static final int ENCLOSED_SIDES = 3;
   private static final int REFRESH_TICKS = 20;
   private static final int LOADED_MARGIN = 3;
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final int[][] SIDES = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int FILL_ALPHA = 100;
   private final NumberSetting simDistance = this.addSetting("sim-distance", new NumberSetting("Simulation Distance", 4.0F, 2.0F, 16.0F).step(1.0F));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk Color", -65536));
   private final NumberSetting sensitivity = this.addSetting("min-flag", new NumberSetting("Sensitivity", 40.0F, 1.0F, 500.0F).step(1.0F));
   private final BooleanSetting holeFilter = this.addSetting("hole-filter", new BooleanSetting("Hole Filter", false));
   private final NumberSetting holeDistance = this.addSetting("hole-distance", new NumberSetting("Hole Distance", 2.0F, 0.0F, 8.0F).step(1.0F));
   private final BooleanSetting customEvidence = this.addSetting("custom-evidence", new BooleanSetting("Custom Flags", false));
   private final BooleanSetting[] evidence = new BooleanSetting[]{
      this.evidence("count-kelp", "Kelp"),
      this.evidence("count-cave-vines", "Cave Vines"),
      this.evidence("count-weeping-vines", "Weeping Vines"),
      this.evidence("count-bamboo", "Bamboo"),
      this.evidence("count-sugar-cane", "Sugar Cane"),
      this.evidence("count-cactus", "Cactus"),
      this.evidence("count-cocoa", "Cocoa"),
      this.evidence("count-sweet-berries", "Sweet Berries"),
      this.evidence("count-nether-wart", "Nether Wart"),
      this.evidence("count-crops", "Crops"),
      this.evidence("count-stems", "Stems"),
      this.evidence("count-amethyst", "Amethyst"),
      this.evidence("count-vines", "Vines"),
      this.evidence("count-leaves", "Leaves"),
      this.evidence("count-grass-dirt", "Grass & Dirt"),
      this.evidence("count-propagules", "Mangrove Propagules"),
      this.evidence("count-eyeblossoms", "Eyeblossoms"),
      this.evidence("count-snow", "Snow"),
      this.evidence("count-ice", "Ice")
   };
   private final ChunkOverlay primaryOverlay = new ChunkOverlay();
   private final Object lock = new Object();
   private final Long2ObjectOpenHashMap<BetterSusChunkModule.Record> records = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap clearCoverage = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap support = new Long2IntOpenHashMap();
   private final LongOpenHashSet scanned = new LongOpenHashSet();
   private final LongArrayList distant = new LongArrayList();
   private final Set<Long> pending = ConcurrentHashMap.newKeySet();
   private final Set<Long> kelpChunks = ConcurrentHashMap.newKeySet();
   private final Set<Long> seenKelp = ConcurrentHashMap.newKeySet();
   private volatile LongSet primary = LongSets.EMPTY_SET;
   private volatile int generation;
   private volatile boolean dirty;
   private volatile boolean[] counted = new boolean[19];
   private ExecutorService executor;
   private RegistryKey<World> worldKey;
   private int tickCounter;

   public BetterSusChunkModule() {
      super(
         "better-sus-chunk",
         "Better Sus Chunk",
         "Marks chunks no player has been near: areas with overdue natural growth rule themselves out.",
         ModuleCategory.BASE_RAID
      );
      this.holeDistance.visibleWhen(this.holeFilter::get);
      this.simDistance.onChange(() -> this.dirty = true);
      this.holeFilter.onChange(() -> {
         if (this.holeFilter.get()) {
            this.dirty = true;
         }
      });
      this.customEvidence.onChange(this::refreshEvidence);

      for (BooleanSetting kind : this.evidence) {
         kind.visibleWhen(this.customEvidence::get);
         kind.onChange(this::refreshEvidence);
      }

      this.refreshEvidence();
   }

   private BooleanSetting evidence(String id, String name) {
      return this.addSetting(id, new BooleanSetting(name, true));
   }

   private void refreshEvidence() {
      boolean custom = this.customEvidence.get();
      boolean[] next = new boolean[19];

      for (int i = 0; i < next.length; i++) {
         next[i] = !custom || this.evidence[i].get();
      }

      this.counted = next;
      this.dirty = true;
   }

   @Override
   protected void onEnable() {
      this.resetState();
      this.executor = Executors.newSingleThreadExecutor(task -> {
         Thread thread = new Thread(task, "Anubis Better Sus Chunk");
         thread.setDaemon(true);
         return thread;
      });
      ClientWorld level = MinecraftClient.getInstance().world;
      this.worldKey = level == null ? null : level.getRegistryKey();
      this.dirty = false;
      this.rescanLoadedChunks();
   }

   @Override
   protected void onDisable() {
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }

      this.resetState();
      this.worldKey = null;
   }

   private void resetState() {
      synchronized (this.lock) {
         this.generation++;
         this.clearCoverage.clear();
         this.support.clear();
         this.records.clear();
         this.scanned.clear();
         this.pending.clear();
         this.kelpChunks.clear();
         this.seenKelp.clear();
      }

      this.primary = LongSets.EMPTY_SET;
      this.tickCounter = 0;
   }

   private void rescanLoadedChunks() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (this.isEnabled() && mc.player != null && level != null) {
         this.resetState();
         int radius = mc.options.getClampedViewDistance() + 3;
         ChunkPos center = mc.player.getChunkPos();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (level.isChunkLoaded(x, z)) {
                  this.scheduleScan(ChunkPos.toLong(x, z));
               }
            }
         }
      }
   }

   private void scheduleScan(long key) {
      ExecutorService worker = this.executor;
      if (worker != null && !worker.isShutdown()) {
         if (this.pending.add(key)) {
            int expected = this.generation;
            worker.submit(() -> this.runScan(key, expected));
         }
      }
   }

   private void runScan(long key, int expected) {
      this.pending.remove(key);
      if (expected == this.generation && this.isEnabled()) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (level.isChunkLoaded(cx, cz)) {
               WorldChunk chunk = level.getChunk(cx, cz);
               if (chunk != null) {
                  this.rescan(level, key, chunk, expected);
               }
            }
         }
      }
   }

   private void rescan(ClientWorld level, long key, WorldChunk chunk, int expected) {
      BetterSusChunkModule.Record old = this.removeRecord(key);
      BetterSusChunkModule.Source fresh = this.scanChunk(level, key, chunk);
      synchronized (this.lock) {
         if (expected == this.generation) {
            BetterSusChunkModule.Record merged = new BetterSusChunkModule.Record(
               fresh.clears() || old != null && old.clears(),
               this.simDistance.getInt(),
               fresh.hole() || old != null && old.hole(),
               Math.max(fresh.weight(), old == null ? 0 : old.weight())
            );
            this.scanned.add(key);
            this.records.put(key, merged);
            if (merged.clears()) {
               int cx = ChunkPos.getPackedX(key);
               int cz = ChunkPos.getPackedZ(key);
               int reach = 2 * merged.radius() + 2;

               for (int dx = -merged.radius(); dx <= merged.radius(); dx++) {
                  for (int dz = -merged.radius(); dz <= merged.radius(); dz++) {
                     this.clearCoverage.addTo(ChunkPos.toLong(cx + dx, cz + dz), 1);
                  }
               }

               for (int dx = -reach; dx <= reach; dx++) {
                  for (int dz = -reach; dz <= reach; dz++) {
                     this.support.addTo(ChunkPos.toLong(cx + dx, cz + dz), merged.weight());
                  }
               }
            }
         }
      }
   }

   private BetterSusChunkModule.Record removeRecord(long key) {
      synchronized (this.lock) {
         BetterSusChunkModule.Record old = (BetterSusChunkModule.Record)this.records.remove(key);
         this.scanned.remove(key);
         if (old != null && old.clears()) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            int reach = 2 * old.radius() + 2;

            for (int dx = -old.radius(); dx <= old.radius(); dx++) {
               for (int dz = -old.radius(); dz <= old.radius(); dz++) {
                  subtract(this.clearCoverage, ChunkPos.toLong(cx + dx, cz + dz), 1);
               }
            }

            for (int dx = -reach; dx <= reach; dx++) {
               for (int dz = -reach; dz <= reach; dz++) {
                  subtract(this.support, ChunkPos.toLong(cx + dx, cz + dz), old.weight());
               }
            }

            return old;
         } else {
            return old;
         }
      }
   }

   private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
      if (map.containsKey(key)) {
         int value = map.get(key);
         if (value <= amount) {
            map.remove(key);
         } else {
            map.put(key, value - amount);
         }
      }
   }

   private BetterSusChunkModule.Source scanChunk(ClientWorld level, long key, WorldChunk chunk) {
      boolean donut = donutServer();
      boolean[] counts = this.counted;
      int count = donut && counts[0] && this.kelpChunks.contains(key) ? 1 : 0;
      BetterSusChunkModule.BlockReader blocks = new BetterSusChunkModule.BlockReader(level);
      ChunkPos pos = chunk.getPos();
      int minX = pos.getStartX();
      int minZ = pos.getStartZ();
      Boolean bloomsOpen = eyeblossomsOpen(level);
      int lateBlooms = 0;
      int settledBlooms = 0;
      ChunkSection[] sections = chunk.getSectionArray();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty() && section.hasAny(statex -> kindOf(statex, counts) >= 0)) {
            int baseY = chunk.getBottomY() + (i << 4);

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     BlockState state = section.getBlockState(x, y, z);
                     int kind = kindOf(state, counts);
                     if (kind >= 0) {
                        if (kind == 15) {
                           if (state.get(PropaguleBlock.AGE) < 4) {
                              count++;
                           }
                        } else if (kind == 16) {
                           if (bloomsOpen != null) {
                              if (state.isOf(Blocks.OPEN_EYEBLOSSOM) == bloomsOpen) {
                                 settledBlooms++;
                              } else {
                                 lateBlooms++;
                              }
                           }
                        } else if (owed(blocks, minX + x, baseY + y, minZ + z, state, kind, donut)) {
                           count++;
                        }
                     }
                  }
               }
            }
         }
      }

      if (settledBlooms == 0 && lateBlooms >= 3) {
         count += lateBlooms;
      }

      if ((counts[17] || counts[18]) && chunk.hasHeightmap(Type.MOTION_BLOCKING)) {
         Heightmap heightmap = chunk.getHeightmap(Type.MOTION_BLOCKING);
         int seaLevel = level.getSeaLevel();

         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               int y = heightmap.get(x, z);
               if (surfaceOwed(level, blocks, minX + x, y, minZ + z, seaLevel, donut, counts)) {
                  count++;
               }
            }
         }
      }

      boolean hole = this.holeFilter.get() && hasHole(level, pos);
      return new BetterSusChunkModule.Source(count > 0, hole, count);
   }

   private static int kindOf(BlockState state, boolean[] counts) {
      int kind = classify(state);
      return kind >= 0 && counts[kind] ? kind : -1;
   }

   private static int classify(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.KELP) {
         return 0;
      } else if (block == Blocks.CAVE_VINES) {
         return 1;
      } else if (block == Blocks.WEEPING_VINES) {
         return 2;
      } else if (block == Blocks.BAMBOO) {
         return 3;
      } else if (block == Blocks.SUGAR_CANE) {
         return 4;
      } else if (block == Blocks.CACTUS) {
         return 5;
      } else if (block == Blocks.COCOA) {
         return 6;
      } else if (block == Blocks.SWEET_BERRY_BUSH) {
         return 7;
      } else if (block == Blocks.NETHER_WART) {
         return 8;
      } else if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
         return 10;
      } else if (block == Blocks.VINE) {
         return 12;
      } else if (block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_CLUSTER || isBud(state)) {
         return 11;
      } else if (block == Blocks.MANGROVE_PROPAGULE) {
         return state.get(PropaguleBlock.HANGING) ? 15 : -1;
      } else if (block == Blocks.OPEN_EYEBLOSSOM || block == Blocks.CLOSED_EYEBLOSSOM) {
         return 16;
      } else if (block instanceof CropBlock) {
         return 9;
      } else if (block instanceof LeavesBlock) {
         return 13;
      } else {
         return !isGrass(state) && !state.isOf(Blocks.DIRT) ? -1 : 14;
      }
   }

   private static Boolean eyeblossomsOpen(ClientWorld level) {
      if (level.getRegistryKey() == World.OVERWORLD && !level.getDimension().hasFixedTime()) {
         int time = Math.floorMod(level.getTimeOfDay(), 24000);
         boolean open = time >= 12600 && time < 23401;
         int since = open ? time - 12600 : Math.floorMod(time - 23401, 24000);
         return since >= 4000 ? open : null;
      } else {
         return null;
      }
   }

   private static boolean owed(BetterSusChunkModule.BlockReader blocks, int x, int y, int z, BlockState state, int kind, boolean donut) {
      if (kind == 14) {
         return soilOwed(blocks, x, y, z, state);
      }

      if (donut) {
         if (kind == 0) {
            return blocks.state(x, y + 1, z).isOf(Blocks.WATER) && column(blocks, x, y, z, Blocks.KELP_PLANT, 3) < 3;
         }

         if (kind == 11) {
            return isBud(state);
         }

         if (kind == 1) {
            return false;
         }
      }

      return growthOwed(blocks, x, y, z, state);
   }

   private static boolean growthOwed(BetterSusChunkModule.BlockReader blocks, int x, int y, int z, BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.KELP) {
         return state.get(Properties.AGE_25) < 25 && blocks.state(x, y + 1, z).isOf(Blocks.WATER);
      } else if (block == Blocks.CAVE_VINES || block == Blocks.WEEPING_VINES) {
         return state.get(Properties.AGE_25) < 25 && blocks.state(x, y - 1, z).isAir();
      } else if (block == Blocks.BAMBOO) {
         return state.get(Properties.STAGE) == 0
            && blocks.state(x, y + 1, z).isAir()
            && bambooBelow(blocks, x, y, z) + 1 < 16
            && blocks.light(x, y + 1, z) >= 9;
      } else if (block == Blocks.SUGAR_CANE) {
         return blocks.state(x, y + 1, z).isAir() && column(blocks, x, y, z, Blocks.SUGAR_CANE, 3) < 3;
      } else if (block == Blocks.CACTUS) {
         return blocks.state(x, y + 1, z).isAir() && (state.get(Properties.AGE_15) < 15 || column(blocks, x, y, z, Blocks.CACTUS, 3) < 3);
      } else if (block == Blocks.COCOA) {
         return state.get(Properties.AGE_2) < 2;
      } else if (block == Blocks.NETHER_WART) {
         return state.get(Properties.AGE_3) < 3;
      } else if (block == Blocks.SWEET_BERRY_BUSH) {
         return state.get(Properties.AGE_3) < 3 && blocks.light(x, y + 1, z) >= 9;
      } else if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
         return state.get(Properties.AGE_7) < 7 && blocks.light(x, y, z) >= 9;
      } else if (block instanceof CropBlock crop) {
         return !crop.isMature(state) && blocks.light(x, y, z) >= 9;
      } else if (block == Blocks.VINE) {
         return blocks.state(x, y - 1, z).isAir()
            && (state.get(Properties.NORTH) || state.get(Properties.EAST) || state.get(Properties.SOUTH) || state.get(Properties.WEST));
      } else {
         return !(block instanceof LeavesBlock)
            ? amethystOwed(blocks, x, y, z, state)
            : state.contains(Properties.DISTANCE_1_7)
               && state.contains(Properties.PERSISTENT)
               && state.get(Properties.DISTANCE_1_7) == 7
               && !state.get(Properties.PERSISTENT);
      }
   }

   private static boolean amethystOwed(BetterSusChunkModule.BlockReader blocks, int x, int y, int z, BlockState state) {
      if (state.isOf(Blocks.BUDDING_AMETHYST)) {
         for (Direction direction : DIRECTIONS) {
            BlockState near = blocks.state(x + direction.getOffsetX(), y + direction.getOffsetY(), z + direction.getOffsetZ());
            if (near.isAir()) {
               return true;
            }

            if (near.isOf(Blocks.WATER) && near.getFluidState().getLevel() == 8) {
               return true;
            }
         }

         return false;
      } else if (isBud(state) && state.contains(Properties.FACING)) {
         Direction facing = state.get(Properties.FACING);
         return blocks.state(x - facing.getOffsetX(), y - facing.getOffsetY(), z - facing.getOffsetZ()).isOf(Blocks.BUDDING_AMETHYST);
      } else {
         return false;
      }
   }

   private static int column(BetterSusChunkModule.BlockReader blocks, int x, int y, int z, Block target, int limit) {
      int length = 1;

      while (length < limit && blocks.state(x, y - length, z).isOf(target)) {
         length++;
      }

      return length;
   }

   private static int bambooBelow(BetterSusChunkModule.BlockReader blocks, int x, int y, int z) {
      int below = 0;

      while (below < 16 && blocks.state(x, y - below - 1, z).isOf(Blocks.BAMBOO)) {
         below++;
      }

      return below;
   }

   private static boolean soilOwed(BetterSusChunkModule.BlockReader blocks, int x, int y, int z, BlockState state) {
      BlockState upper = blocks.state(x, y + 1, z);
      if (isGrass(state)) {
         return smothers(upper);
      }

      if (state.isOf(Blocks.DIRT) && !smothers(upper) && !upper.getFluidState().isIn(FluidTags.WATER)) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               for (int ny = y - 1; ny <= y + 3; ny++) {
                  if ((dx != 0 || dz != 0 || ny != y)
                     && isGrass(blocks.state(x + dx, ny, z + dz))
                     && !smothers(blocks.state(x + dx, ny + 1, z + dz))
                     && blocks.light(x + dx, ny + 1, z + dz) >= 9) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean smothers(BlockState upper) {
      if (upper.isOf(Blocks.SNOW) && upper.get(SnowBlock.LAYERS) == 1) {
         return false;
      } else {
         return upper.getFluidState().getLevel() == 8
            ? true
            : ChunkLightProvider.getRealisticOpacity(Blocks.GRASS_BLOCK.getDefaultState(), upper, Direction.UP, upper.getOpacity()) >= 15;
      }
   }

   private static boolean surfaceOwed(
      ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z, int seaLevel, boolean donut, boolean[] counts
   ) {
      boolean snow = counts[17] && (!donut || !blocks.state(x, y - 1, z).isOf(Blocks.DEEPSLATE));
      return snow && snowOwed(level, blocks, x, y, z, seaLevel) || counts[18] && iceOwed(level, blocks, x, y, z, seaLevel);
   }

   private static boolean snowOwed(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z, int seaLevel) {
      if (!level.isOutOfHeightLimit(y) && blocks.state(x, y, z).isAir()) {
         if (!holdsSnow(level, blocks.state(x, y - 1, z), new BlockPos(x, y - 1, z))) {
            return false;
         }

         BlockPos pos = new BlockPos(x, y, z);
         return level.getBiome(pos).value().getPrecipitation(pos, seaLevel) != Precipitation.SNOW ? false : calmLight(level, blocks, x, y, z);
      } else {
         return false;
      }
   }

   private static boolean holdsSnow(ClientWorld level, BlockState below, BlockPos pos) {
      if (below.isIn(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
         return false;
      } else if (below.isIn(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) {
         return true;
      } else {
         return below.isOf(Blocks.SNOW) && below.get(SnowBlock.LAYERS) == 8 ? true : Block.isFaceFullSquare(below.getCollisionShape(level, pos), Direction.UP);
      }
   }

   private static boolean iceOwed(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z, int seaLevel) {
      int water = y - 1;
      if (level.isOutOfHeightLimit(water)) {
         return false;
      }

      BlockState state = blocks.state(x, water, z);
      if (state.getBlock() instanceof FluidBlock && state.getFluidState().getFluid() == Fluids.WATER) {
         if (!level.isChunkLoaded(x - 1 >> 4, z >> 4)
            || !level.isChunkLoaded(x + 1 >> 4, z >> 4)
            || !level.isChunkLoaded(x >> 4, z - 1 >> 4)
            || !level.isChunkLoaded(x >> 4, z + 1 >> 4)) {
            return false;
         } else if (isWater(blocks, x - 1, water, z)
            && isWater(blocks, x + 1, water, z)
            && isWater(blocks, x, water, z - 1)
            && isWater(blocks, x, water, z + 1)) {
            return false;
         } else {
            return level.getBiome(new BlockPos(x, y, z)).value().doesNotSnow(new BlockPos(x, water, z), seaLevel)
               ? false
               : calmLight(level, blocks, x, water, z);
         }
      } else {
         return false;
      }
   }

   private static boolean isWater(BetterSusChunkModule.BlockReader blocks, int x, int y, int z) {
      return blocks.state(x, y, z).getFluidState().isIn(FluidTags.WATER);
   }

   private static boolean calmLight(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z) {
      int radius = 5;

      for (int cx = x - radius >> 4; cx <= x + radius >> 4; cx++) {
         for (int cz = z - radius >> 4; cz <= z + radius >> 4; cz++) {
            if (!level.isChunkLoaded(cx, cz)) {
               return false;
            }
         }
      }

      for (int dx = -radius; dx <= radius; dx++) {
         int restY = radius - Math.abs(dx);

         for (int dy = -restY; dy <= restY; dy++) {
            int restZ = restY - Math.abs(dy);

            for (int dz = -restZ; dz <= restZ; dz++) {
               int emission = blocks.state(x + dx, y + dy, z + dz).getLuminance();
               if (emission != 0 && emission - Math.abs(dx) - Math.abs(dy) - Math.abs(dz) >= 10) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private static boolean hasHole(ClientWorld level, ChunkPos pos) {
      BetterSusChunkModule.BlockReader blocks = new BetterSusChunkModule.BlockReader(level);
      int bottom = level.getBottomY();
      int top = bottom + level.getHeight();
      int minX = pos.getStartX();
      int minZ = pos.getStartZ();

      for (int x = minX; x < minX + 16; x++) {
         for (int z = minZ; z < minZ + 16; z++) {
            int single = 0;
            int alongX = 0;
            int alongZ = 0;

            for (int y = bottom; y < top; y++) {
               if (singleShaft(level, blocks, x, y, z)) {
                  single++;
               } else {
                  if (shaftEnds(level, blocks, x, y, z, single)) {
                     return true;
                  }

                  single = 0;
               }

               if (wideShaft(level, blocks, x, y, z, 1, 0)) {
                  alongX++;
               } else {
                  if (shaftEnds(level, blocks, x, y, z, alongX)) {
                     return true;
                  }

                  alongX = 0;
               }

               if (wideShaft(level, blocks, x, y, z, 0, 1)) {
                  alongZ++;
               } else {
                  if (shaftEnds(level, blocks, x, y, z, alongZ)) {
                     return true;
                  }

                  alongZ = 0;
               }
            }
         }
      }

      return false;
   }

   private static boolean singleShaft(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z) {
      return open(level, blocks, x, y, z)
         && !open(level, blocks, x - 1, y, z)
         && !open(level, blocks, x + 1, y, z)
         && !open(level, blocks, x, y, z - 1)
         && !open(level, blocks, x, y, z + 1);
   }

   private static boolean wideShaft(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z, int stepX, int stepZ) {
      for (int i = 0; i < 3; i++) {
         if (!open(level, blocks, x + stepX * i, y, z + stepZ * i)) {
            return false;
         }
      }

      if (!open(level, blocks, x - stepX, y, z - stepZ) && !open(level, blocks, x + stepX * 3, y, z + stepZ * 3)) {
         for (int i = 0; i < 3; i++) {
            int wx = x + stepX * i;
            int wz = z + stepZ * i;
            if (open(level, blocks, wx - stepZ, y, wz - stepX) || open(level, blocks, wx + stepZ, y, wz + stepX)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean shaftEnds(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z, int run) {
      return run >= 2 && open(level, blocks, x, y, z) && !open(level, blocks, x, y - run - 1, z);
   }

   private static boolean open(ClientWorld level, BetterSusChunkModule.BlockReader blocks, int x, int y, int z) {
      return blocks.state(x, y, z).getCollisionShape(level, BlockPos.ORIGIN).isEmpty();
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isGrass(BlockState state) {
      return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.MYCELIUM);
   }

   private static boolean donutServer() {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection == null) {
         return false;
      }

      String brand = connection.getBrand();
      return brand != null && brand.contains("DonutFolia");
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         this.scheduleScan(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      } else if (event.packet instanceof GameJoinS2CPacket) {
         this.resetState();
         this.worldKey = null;
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && this.counted[0] && event.newState.isOf(Blocks.KELP) && donutServer()) {
         int age = event.newState.get(Properties.AGE_25);
         if (this.seenKelp.add(event.pos.asLong())) {
            if (age > 0 && age < 25) {
               if (level.getBlockState(event.pos.up()).isOf(Blocks.WATER)) {
                  long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
                  if (this.kelpChunks.add(key)) {
                     this.scheduleScan(key);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != null && mc.player != null) {
         RegistryKey<World> dimension = level.getRegistryKey();
         if (dimension != this.worldKey) {
            this.resetState();
            this.worldKey = dimension;
         }

         if (this.dirty) {
            this.dirty = false;
            this.rescanLoadedChunks();
         }

         this.pruneDistant(mc);
         if (++this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.rebuildDisplaySets(level);
         }
      }
   }

   private void pruneDistant(MinecraftClient mc) {
      int reach = mc.options.getClampedViewDistance() + 2 * this.simDistance.getInt() + 2;
      int px = mc.player.getBlockX() >> 4;
      int pz = mc.player.getBlockZ() >> 4;
      synchronized (this.lock) {
         this.distant.clear();
         LongIterator keys = this.records.keySet().iterator();

         while (keys.hasNext()) {
            long key = keys.nextLong();
            if (Math.abs(ChunkPos.getPackedX(key) - px) > reach || Math.abs(ChunkPos.getPackedZ(key) - pz) > reach) {
               this.distant.add(key);
            }
         }

         for (int i = 0; i < this.distant.size(); i++) {
            this.removeRecord(this.distant.getLong(i));
         }
      }

      this.kelpChunks.removeIf(keyx -> Math.abs(ChunkPos.getPackedX(keyx) - px) > reach || Math.abs(ChunkPos.getPackedZ(keyx) - pz) > reach);
      this.seenKelp
         .removeIf(packed -> Math.abs((BlockPos.unpackLongX(packed) >> 4) - px) > reach || Math.abs((BlockPos.unpackLongZ(packed) >> 4) - pz) > reach);
   }

   private void rebuildDisplaySets(ClientWorld level) {
      boolean holes = this.holeFilter.get();
      int threshold = this.sensitivity.getInt();
      int holeReach = this.holeDistance.getInt();
      int reach = 2 * this.simDistance.getInt() + 2;
      LongOpenHashSet nextPrimary = new LongOpenHashSet();
      synchronized (this.lock) {
         LongIterator keys = this.scanned.iterator();

         while (keys.hasNext()) {
            long key = keys.nextLong();
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (level.isChunkLoaded(cx, cz)
               && !this.clearCoverage.containsKey(key)
               && (!holes || !this.holeNearby(cx, cz, holeReach))
               && this.support.get(key) >= threshold
               && this.coveredSides(cx, cz, reach) >= 3) {
               nextPrimary.add(key);
            }
         }
      }

      this.primary = nextPrimary;
   }

   private boolean holeNearby(int cx, int cz, int distance) {
      for (int dx = -distance; dx <= distance; dx++) {
         for (int dz = -distance; dz <= distance; dz++) {
            BetterSusChunkModule.Record record = (BetterSusChunkModule.Record)this.records.get(ChunkPos.toLong(cx + dx, cz + dz));
            if (record != null && record.hole()) {
               return true;
            }
         }
      }

      return false;
   }

   private int coveredSides(int cx, int cz, int reach) {
      int sides = 0;

      for (int[] side : SIDES) {
         if (this.stripCleared(cx, cz, side[0], side[1], reach)) {
            sides++;
         }
      }

      return sides;
   }

   private boolean stripCleared(int cx, int cz, int dx, int dz, int reach) {
      for (int depth = 1; depth <= reach; depth++) {
         for (int lateral = -reach; lateral <= reach; lateral++) {
            long key = ChunkPos.toLong(cx + dx * depth + dz * lateral, cz + dz * depth + dx * lateral);
            if (this.clearCoverage.containsKey(key)) {
               return true;
            }
         }
      }

      return false;
   }

   public LongSet flaggedChunks() {
      return LongSets.unmodifiable(this.primary);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (MinecraftClient.getInstance().world != null) {
         this.primaryOverlay.drawTiles(this.primary, this.chunkColor.argbWithAlpha(100), this.chunkColor.argbWithAlpha(255), event.partialTick);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class BlockReader {
      private final ClientWorld level;
      private WorldChunk chunk;

      private BlockReader(ClientWorld level) {
         this.level = level;
      }

      private BlockState state(int x, int y, int z) {
         if (this.level.isOutOfHeightLimit(y)) {
            return Blocks.VOID_AIR.getDefaultState();
         }

         WorldChunk at = this.chunkAt(x >> 4, z >> 4);
         if (at == null) {
            return Blocks.VOID_AIR.getDefaultState();
         }

         ChunkSection section = at.getSectionArray()[at.getSectionIndex(y)];
         return section == null ? Blocks.VOID_AIR.getDefaultState() : section.getBlockState(x & 15, y & 15, z & 15);
      }

      private boolean emptySection(int x, int y, int z) {
         WorldChunk at = this.chunkAt(x >> 4, z >> 4);
         if (at == null) {
            return true;
         }

         ChunkSection section = at.getSectionArray()[at.getSectionIndex(y)];
         return section == null || section.isEmpty();
      }

      private WorldChunk chunkAt(int cx, int cz) {
         if (this.chunk != null && this.chunk.getPos().x == cx && this.chunk.getPos().z == cz) {
            return this.chunk;
         }

         WorldChunk loaded = this.level.getChunkManager().getWorldChunk(cx, cz);
         if (loaded != null) {
            this.chunk = loaded;
         }

         return loaded;
      }

      private int light(int x, int y, int z) {
         int value = Math.max(this.rawLight(LightType.BLOCK, x, y, z), this.rawLight(LightType.SKY, x, y, z));
         if (value < 15 && this.level.getDimension().hasSkyLight()) {
            return this.openSky(x, y, z) ? 15 : value;
         } else {
            return value;
         }
      }

      private int rawLight(LightType layer, int x, int y, int z) {
         ChunkNibbleArray data = this.level.getLightingProvider().get(layer).getLightSection(ChunkSectionPos.from(x >> 4, y >> 4, z >> 4));
         return data != null && !data.isArrayUninitialized() ? data.get(x & 15, y & 15, z & 15) : 0;
      }

      private boolean openSky(int x, int y, int z) {
         int top = this.level.getTopYInclusive();

         for (int ny = y + 1; ny <= top; ny++) {
            if (this.emptySection(x, ny, z)) {
               ny |= 15;
            } else if (this.state(x, ny, z).getOpacity() > 0) {
               return false;
            }
         }

         return true;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Record(boolean clears, int radius, boolean hole, int weight) {
   }

   @Environment(EnvType.CLIENT)
   private record Source(boolean clears, boolean hole, int weight) {
   }
}
