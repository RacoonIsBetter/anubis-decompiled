package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.ChunkAreas;
import net.anubis.client.util.ChunkOverlay;
import net.anubis.client.util.GeodeGlowScan;
import net.anubis.client.util.LevelUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BuddingAmethystBlock;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class SusChunkFinderModule extends ClientModule {
   private static final long SCAN_BUDGET_NANOS = 3000000L;
   private static final int MIN_SCANNED_NEIGHBOURS = 3;
   private static final int NO_PACKET_SENSITIVITY = 1;
   private static final int VISIBLE_REFRESH_TICKS = 20;
   private static final int PACKET_MIN_Y = -16;
   private static final int PACKET_MAX_Y = 30;
   private static final String METHOD_CLASSIC = "Classic";
   private static final String METHOD_ANBS = "ANBS+ Scan";
   private static final int GLOW_CELLS_PER_POINT = 12;
   private static final int GEODE_TOP_Y = 47;
   private static final int MIN_EXPOSED_BUDDING = 10;
   private static final int MIN_EXPOSED_LINING = 160;
   private static final int GEODE_SETTLE_TICKS = 40;
   private static final int GEODE_CELL_SHIFT = 2;
   private static final int TORCH_LIGHT = 14;
   private static final Direction[] SIDES = Direction.values();
   private final ModeSetting method = this.addSetting("method", new ModeSetting("Method", "Classic", "Classic", "ANBS+ Scan"));
   private final NumberSetting spreadRadius = this.addSetting("spread-radius", new NumberSetting("Simulation Distance", 5.0F, 2.0F, 16.0F).step(1.0F));
   private final NumberSetting sensitivity = this.addSetting("packet-sensitivity", new NumberSetting("Sensitivity", 6.0F, 1.0F, 20.0F).step(1.0F));
   private final ColorSetting chunkColor = this.addSetting("chunk-color", new ColorSetting("Chunk Color", 1627325444));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting detectMined = this.addSetting("detect-mined-clusters", new BooleanSetting("Detect Mined Clusters", false));
   private final ColorSetting minedColor = this.addSetting("mined-color", new ColorSetting("Mined Color", 1621511679));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final ChunkOverlay chunkOverlay = new ChunkOverlay();
   private final ChunkAreas minedAreas = new ChunkAreas();
   private final ChunkOverlay minedOverlay = new ChunkOverlay();
   private final GeodeGlowScan glow = new GeodeGlowScan();
   private final Long2IntOpenHashMap heat = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap vetoes = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap chunkScore = new Long2IntOpenHashMap();
   private final Long2BooleanOpenHashMap chunkUngrown = new Long2BooleanOpenHashMap();
   private final Long2BooleanOpenHashMap chunkHasPacket = new Long2BooleanOpenHashMap();
   private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
   private boolean visibleDirty = true;
   private int visibleAge;
   private ClientWorld lastLevel;
   private int appliedRadius;
   private final Long2ObjectOpenHashMap<SusChunkFinderModule.GeodeSlice> geodeSlices = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap geodeWaiting = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap geodeScanTicks = new Long2IntOpenHashMap();
   private List<SusChunkFinderModule.GeodeCluster> geodeClusters = List.of();
   private boolean geodeClustersDirty;
   private LongOpenHashSet minedChunks = new LongOpenHashSet();
   private int minedAge;
   private int ticks;

   public SusChunkFinderModule() {
      super("sus-chunk-finder", "Sus Chunk Finder", "Finds chunks with signs of player activity from fully grown amethyst.", ModuleCategory.BASE_RAID);
      this.method.onChange(this::rescan);
      this.sensitivity.onChange(() -> this.visibleDirty = true);
      this.detectMined.onChange(this::restartGeodes);
      this.minedColor.visibleWhen(this.detectMined::get);
   }

   private void rescan() {
      this.clearAll();
      this.queueLoadedChunks();
   }

   private void restartGeodes() {
      this.clearGeodes();
      if (this.detectMined.get()) {
         this.queueLoadedChunks();
      }
   }

   @Override
   protected void onEnable() {
      this.clearAll();
      this.lastLevel = MinecraftClient.getInstance().world;
      this.queueLoadedChunks();
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      this.heat.clear();
      this.vetoes.clear();
      this.chunkScore.clear();
      this.chunkUngrown.clear();
      this.chunkHasPacket.clear();
      this.scannedChunks.clear();
      this.queue.clear();
      this.queued.clear();
      this.visible = new Long2IntOpenHashMap();
      this.visibleDirty = true;
      this.appliedRadius = this.spreadRadius.getInt();
      this.clearGeodes();
   }

   private void clearGeodes() {
      this.geodeSlices.clear();
      this.geodeWaiting.clear();
      this.geodeScanTicks.clear();
      this.geodeClusters = List.of();
      this.geodeClustersDirty = false;
      this.minedChunks = new LongOpenHashSet();
      this.minedAge = 0;
   }

   private void queueLoadedChunks() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         int radius = (Integer)mc.options.getViewDistance().getValue() + 1;
         ChunkPos center = mc.player.getChunkPos();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (LevelUtil.isLoaded(mc.world, x, z)) {
                  this.enqueue(ChunkPos.toLong(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         this.enqueue(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      } else if (event.packet instanceof LightUpdateS2CPacket light && this.method.is("ANBS+ Scan")) {
         this.enqueue(ChunkPos.toLong(light.getChunkX(), light.getChunkZ()));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = level;
         this.queueLoadedChunks();
      }

      if (level != null) {
         this.ticks++;
         if (this.spreadRadius.getInt() != this.appliedRadius) {
            this.clearAll();
            this.queueLoadedChunks();
         }

         long deadline = System.nanoTime() + 3000000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               this.updateChunk(key, level.getChunk(cx, cz), level);
               this.scannedChunks.add(key);
               this.visibleDirty = true;
            }
         }

         if (this.visibleDirty || ++this.visibleAge >= 20) {
            this.visible = this.computeVisible(level);
            this.visibleDirty = false;
            this.visibleAge = 0;
         }

         if (this.detectMined.get() && ++this.minedAge >= 20) {
            this.minedChunks = this.computeMined(level);
            this.minedAge = 0;
         }
      }
   }

   private void updateChunk(long key, WorldChunk chunk, ClientWorld level) {
      int r = this.spreadRadius.getInt();
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);
      int oldHeat = this.chunkScore.getOrDefault(key, 0);
      boolean oldUngrown = this.chunkUngrown.getOrDefault(key, false);
      if (oldHeat > 0) {
         forArea(cx, cz, r, area -> subtract(this.heat, area, oldHeat));
      }

      if (oldUngrown) {
         forArea(cx, cz, r, area -> subtract(this.vetoes, area, 1));
      }

      this.chunkHasPacket.put(key, hasBlocksInRange(chunk, -16, 30));
      SusChunkFinderModule.ChunkScore score = this.scoreChunk(chunk, level);
      this.chunkScore.put(key, score.selfHeat());
      this.chunkUngrown.put(key, score.hasUngrown());
      if (score.hasUngrown()) {
         forArea(cx, cz, r, area -> this.vetoes.addTo(area, 1));
      }

      if (score.selfHeat() > 0) {
         forArea(cx, cz, r, area -> this.heat.addTo(area, score.selfHeat()));
      }

      if (this.detectMined.get()) {
         this.collectGeode(key, chunk, level);
      }
   }

   private static void forArea(int cx, int cz, int r, LongConsumer action) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            action.accept(ChunkPos.toLong(cx + dx, cz + dz));
         }
      }
   }

   private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
      int left = map.get(key) - amount;
      if (left > 0) {
         map.put(key, left);
      } else {
         map.remove(key);
      }
   }

   private static boolean hasBlocksInRange(WorldChunk chunk, int minY, int maxY) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty()) {
            int baseY = minSectionY + i << 4;
            if (baseY + 15 >= minY && baseY <= maxY) {
               for (int y = 0; y < 16; y++) {
                  int worldY = baseY + y;
                  if (worldY >= minY && worldY <= maxY) {
                     for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                           if (!section.getBlockState(x, y, z).isAir()) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private SusChunkFinderModule.ChunkScore scoreChunk(WorldChunk chunk, ClientWorld level) {
      if (this.method.is("ANBS+ Scan")) {
         return new SusChunkFinderModule.ChunkScore(this.glow.scan(level, chunk, null) / 12, false);
      }

      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();
      ChunkPos pos = chunk.getPos();
      boolean paletteOnly = isDonutFolia();
      int grown = 0;
      int ungrown = 0;
      boolean sawBud = false;
      boolean sawCluster = false;
      Mutable cursor = new Mutable();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null && !section.isEmpty()) {
            if (paletteOnly) {
               if (!sawBud && section.hasAny(SusChunkFinderModule::isBud)) {
                  sawBud = true;
               }

               if (!sawCluster && section.hasAny(statex -> statex.isOf(Blocks.AMETHYST_CLUSTER))) {
                  sawCluster = true;
               }
            } else if (section.hasAny(SusChunkFinderModule::isAmethystGrowth)) {
               int baseY = minSectionY + i << 4;

               for (int x = 0; x < 16; x++) {
                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isOf(Blocks.AMETHYST_CLUSTER)) {
                           grown++;
                        } else if (isBud(state) && state.contains(Properties.FACING)) {
                           Direction facing = (Direction)state.get(Properties.FACING);
                           cursor.set(pos.getStartX() + x, baseY + y, pos.getStartZ() + z).move(facing.getOpposite());
                           if (level.getBlockState(cursor).isOf(Blocks.BUDDING_AMETHYST)) {
                              ungrown++;
                           } else {
                              grown++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (paletteOnly) {
         if (sawBud) {
            ungrown++;
         } else if (sawCluster) {
            grown++;
         }
      }

      return new SusChunkFinderModule.ChunkScore(grown, ungrown > 0);
   }

   private void collectGeode(long key, WorldChunk chunk, ClientWorld level) {
      this.geodeScanTicks.put(key, this.ticks);
      if (this.geodeSlices.remove(key) != null) {
         this.geodeClustersDirty = true;
      }

      this.geodeWaiting.remove(key);
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            long near = ChunkPos.toLong(cx + dx, cz + dz);
            if ((dx != 0 || dz != 0) && (this.geodeWaiting.get(near) & waitBit(-dx, -dz)) != 0) {
               this.enqueue(near);
            }
         }
      }

      if (LevelUtil.isOverworld(level)) {
         ChunkSection[] sections = chunk.getSectionArray();
         int minSectionY = chunk.getBottomSectionCoord();
         int minX = cx << 4;
         int minZ = cz << 4;
         LongArrayList lining = new LongArrayList();
         LongArrayList budding = new LongArrayList();
         Mutable cursor = new Mutable();
         int waiting = 0;

         for (int i = 0; i < sections.length; i++) {
            int baseY = minSectionY + i << 4;
            if (baseY > 47) {
               break;
            }

            ChunkSection section = sections[i];
            if (section != null && !section.isEmpty() && section.hasAny(SusChunkFinderModule::isLining)) {
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isLining(state)) {
                           int wx = minX + x;
                           int wy = baseY + y;
                           int wz = minZ + z;
                           boolean exposed = false;
                           int missing = 0;

                           for (Direction side : SIDES) {
                              int nx = wx + side.getOffsetX();
                              int ny = wy + side.getOffsetY();
                              int nz = wz + side.getOffsetZ();
                              if (!level.isOutOfHeightLimit(ny)) {
                                 int ncx = nx >> 4;
                                 int ncz = nz >> 4;
                                 boolean inside = ncx == cx && ncz == cz;
                                 if (!inside && !LevelUtil.isLoaded(level, ncx, ncz)) {
                                    missing |= waitBit(ncx - cx, ncz - cz);
                                 } else {
                                    cursor.set(nx, ny, nz);
                                    if (isOpen(inside ? chunk.getBlockState(cursor) : level.getBlockState(cursor))) {
                                       exposed = true;
                                       break;
                                    }
                                 }
                              }
                           }

                           if (!exposed) {
                              waiting |= missing;
                           } else {
                              long packed = BlockPos.asLong(wx, wy, wz);
                              lining.add(packed);
                              if (state.isOf(Blocks.BUDDING_AMETHYST)) {
                                 budding.add(packed);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (waiting != 0) {
            this.geodeWaiting.put(key, waiting);
         }

         if (!lining.isEmpty()) {
            this.geodeSlices.put(key, new SusChunkFinderModule.GeodeSlice(lining.toLongArray(), budding.toLongArray()));
            this.geodeClustersDirty = true;
         }
      }
   }

   private static int waitBit(int dx, int dz) {
      return 1 << (dx + 1) * 3 + dz + 1;
   }

   private LongOpenHashSet computeMined(ClientWorld level) {
      if (this.geodeSlices.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)))) {
         this.geodeClustersDirty = true;
      }

      this.geodeWaiting.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)));
      this.geodeScanTicks.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)));
      if (this.geodeClustersDirty) {
         this.geodeClusters = this.clusterGeodes();
         this.geodeClustersDirty = false;
      }

      LongOpenHashSet mined = new LongOpenHashSet();

      for (SusChunkFinderModule.GeodeCluster cluster : this.geodeClusters) {
         boolean large = cluster.budding() > 0 ? cluster.budding() >= 10 : cluster.lining().length >= 160;
         if (large && this.settled(level, cluster) && unlit(level, cluster.lining())) {
            mined.add(cluster.chunk());
         }
      }

      return mined;
   }

   private List<SusChunkFinderModule.GeodeCluster> clusterGeodes() {
      Long2ObjectOpenHashMap<LongArrayList> cells = new Long2ObjectOpenHashMap();
      LongOpenHashSet budding = new LongOpenHashSet();
      ObjectIterator clusters = this.geodeSlices.values().iterator();

      while (clusters.hasNext()) {
         SusChunkFinderModule.GeodeSlice slice = (SusChunkFinderModule.GeodeSlice)clusters.next();

         for (long packed : slice.lining()) {
            long cell = BlockPos.asLong(
               BlockPos.unpackLongX(packed) >> 2, BlockPos.unpackLongY(packed) >> 2, BlockPos.unpackLongZ(packed) >> 2
            );
            LongArrayList members = (LongArrayList)cells.get(cell);
            if (members == null) {
               cells.put(cell, members = new LongArrayList());
            }

            members.add(packed);
         }

         for (long packed : slice.budding()) {
            budding.add(packed);
         }
      }

      List<SusChunkFinderModule.GeodeCluster> clustersx = new ArrayList<>();
      LongOpenHashSet seen = new LongOpenHashSet();
      LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
      LongIterator var25 = cells.keySet().iterator();

      while (var25.hasNext()) {
         long start = (Long)var25.next();
         if (seen.add(start)) {
            LongArrayList lining = new LongArrayList();
            frontier.enqueue(start);

            while (!frontier.isEmpty()) {
               long cell = frontier.dequeueLong();
               lining.addAll((LongList)cells.get(cell));
               int x = BlockPos.unpackLongX(cell);
               int y = BlockPos.unpackLongY(cell);
               int z = BlockPos.unpackLongZ(cell);

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dy = -1; dy <= 1; dy++) {
                     for (int dz = -1; dz <= 1; dz++) {
                        long next = BlockPos.asLong(x + dx, y + dy, z + dz);
                        if (cells.containsKey(next) && seen.add(next)) {
                           frontier.enqueue(next);
                        }
                     }
                  }
               }
            }

            clustersx.add(toCluster(lining.toLongArray(), budding));
         }
      }

      return clustersx;
   }

   private static SusChunkFinderModule.GeodeCluster toCluster(long[] lining, LongOpenHashSet budding) {
      int count = 0;
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      long sumX = 0L;
      long sumZ = 0L;

      for (long packed : lining) {
         if (budding.contains(packed)) {
            count++;
         }

         int x = BlockPos.unpackLongX(packed);
         int z = BlockPos.unpackLongZ(packed);
         minX = Math.min(minX, x);
         minZ = Math.min(minZ, z);
         maxX = Math.max(maxX, x);
         maxZ = Math.max(maxZ, z);
         sumX += x;
         sumZ += z;
      }

      int centerX = (int)Math.floorDiv(sumX, lining.length);
      int centerZ = (int)Math.floorDiv(sumZ, lining.length);
      return new SusChunkFinderModule.GeodeCluster(lining, count, minX, minZ, maxX, maxZ, ChunkPos.toLong(centerX >> 4, centerZ >> 4));
   }

   private boolean settled(ClientWorld level, SusChunkFinderModule.GeodeCluster cluster) {
      for (int cx = cluster.minX() - 1 >> 4; cx <= cluster.maxX() + 1 >> 4; cx++) {
         for (int cz = cluster.minZ() - 1 >> 4; cz <= cluster.maxZ() + 1 >> 4; cz++) {
            long key = ChunkPos.toLong(cx, cz);
            if (!LevelUtil.isLoaded(level, cx, cz) || !this.geodeScanTicks.containsKey(key)) {
               return false;
            }

            if (this.ticks - this.geodeScanTicks.get(key) < 40) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean unlit(ClientWorld level, long[] lining) {
      Mutable cursor = new Mutable();
      Long2IntOpenHashMap peaks = new Long2IntOpenHashMap();

      for (long packed : lining) {
         int x = BlockPos.unpackLongX(packed);
         int y = BlockPos.unpackLongY(packed);
         int z = BlockPos.unpackLongZ(packed);

         for (Direction side : SIDES) {
            cursor.set(x + side.getOffsetX(), y + side.getOffsetY(), z + side.getOffsetZ());
            if (isAmethystGrowth(level.getBlockState(cursor))) {
               return false;
            }

            int light = level.getLightLevel(LightType.BLOCK, cursor);
            if (light > 0 && sourceLight(level, cursor, light, peaks) != 14) {
               return false;
            }
         }
      }

      return true;
   }

   private static int sourceLight(ClientWorld level, BlockPos start, int light, Long2IntOpenHashMap peaks) {
      LongArrayList path = new LongArrayList();
      Mutable at = start.mutableCopy();
      Mutable probe = new Mutable();

      int peak;
      while (true) {
         long key = at.asLong();
         if (peaks.containsKey(key)) {
            peak = peaks.get(key);
            break;
         }

         path.add(key);
         Direction uphill = null;
         int best = light;

         for (Direction side : SIDES) {
            int value = level.getLightLevel(LightType.BLOCK, probe.set(at, side));
            if (value > best) {
               best = value;
               uphill = side;
            }
         }

         if (uphill == null) {
            peak = light;
            break;
         }

         at.move(uphill);
         light = best;
      }

      for (int i = 0; i < path.size(); i++) {
         peaks.put(path.getLong(i), peak);
      }

      return peak;
   }

   private static boolean isLining(BlockState state) {
      return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
   }

   private static boolean isOpen(BlockState state) {
      return BuddingAmethystBlock.canGrowIn(state) || isAmethystGrowth(state);
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isAmethystGrowth(BlockState state) {
      return state.isOf(Blocks.AMETHYST_CLUSTER) || isBud(state);
   }

   private static boolean isDonutFolia() {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection == null) {
         return false;
      }

      String brand = connection.getBrand();
      return brand != null && brand.contains("DonutFolia");
   }

   private Long2IntOpenHashMap computeVisible(ClientWorld level) {
      Long2IntOpenHashMap eligible = new Long2IntOpenHashMap();
      ObjectIterator withPacket = this.heat.long2IntEntrySet().iterator();

      while (withPacket.hasNext()) {
         Entry entry = (Entry)withPacket.next();
         long key = entry.getLongKey();
         if (!this.vetoes.containsKey(key)) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz) && this.scannedNeighbours(cx, cz) >= 3) {
               eligible.put(key, entry.getIntValue());
            }
         }
      }

      int withPacketx = this.sensitivity.getInt();
      int reach = this.appliedRadius;
      Long2IntOpenHashMap kept = new Long2IntOpenHashMap();
      ObjectIterator var6 = eligible.long2IntEntrySet().iterator();

      while (var6.hasNext()) {
         Entry entry = (Entry)var6.next();
         long key = entry.getLongKey();
         int value = entry.getIntValue();
         int threshold = this.chunkHasPacket.getOrDefault(key, true) ? withPacketx : 1;
         if (value >= threshold && isPeak(eligible, key, value, reach)) {
            kept.put(key, value);
         }
      }

      return kept;
   }

   private static boolean isPeak(Long2IntOpenHashMap field, long key, int value, int reach) {
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if ((dx != 0 || dz != 0) && field.get(ChunkPos.toLong(cx + dx, cz + dz)) > value) {
               return false;
            }
         }
      }

      return true;
   }

   private int scannedNeighbours(int cx, int cz) {
      int count = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && this.scannedChunks.contains(ChunkPos.toLong(cx + dx, cz + dz))) {
               count++;
            }
         }
      }

      return count;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (MinecraftClient.getInstance().world != null) {
         this.chunkOverlay.draw(this.visible.keySet(), this.chunkColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.visible.keySet(), 63.0, ColorHelper.fullAlpha(this.chunkColor.argb()));
         }

         this.minedOverlay.draw(this.minedChunks, this.minedColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.minedAreas.drawTracers(this.minedChunks, 63.0, ColorHelper.fullAlpha(this.minedColor.argb()));
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record ChunkScore(int selfHeat, boolean hasUngrown) {
   }

   @Environment(EnvType.CLIENT)
   private record GeodeCluster(long[] lining, int budding, int minX, int minZ, int maxX, int maxZ, long chunk) {
   }

   @Environment(EnvType.CLIENT)
   private record GeodeSlice(long[] lining, long[] budding) {
   }
}
