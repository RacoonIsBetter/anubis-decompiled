package net.anubis.client.module.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.PerformanceBudget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

@Environment(EnvType.CLIENT)
public class HoleEspModule extends ClientModule {
   private static final String MODE_ALL = "All";
   private static final String MODE_HOLES_TUNNELS = "Holes + Tunnels";
   private static final String MODE_HOLES_STAIRS = "Holes + Stairs";
   private static final String MODE_TUNNELS_STAIRS = "Tunnels + Stairs";
   private static final String MODE_HOLES = "Holes";
   private static final String MODE_TUNNELS = "Tunnels";
   private static final String MODE_STAIRS = "Stairs";
   private static final int TYPE_HOLE = 0;
   private static final int TYPE_HOLE_3X1 = 1;
   private static final int TYPE_TUNNEL = 2;
   private static final int TYPE_STAIR = 3;
   private static final int MAX_HITS_PER_CHUNK = 1024;
   private static final int MAX_RENDER_BOXES = 8192;
   private static final int HOLE_GRID_W = 20;
   private static final int ELYTRA_REFRESH_TICKS = 5;
   private static final int NORMAL_REFRESH_TICKS = 20;
   private static final float ELYTRA_SCAN_RADIUS = 12.0F;
   private static final float ELYTRA_SCAN_BUDGET_MS = 8.0F;
   private static final int[][] HORIZONTAL_DIRS = new int[][]{{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
   private static final ThreadLocal<Mutable> PASSABLE_POS = ThreadLocal.withInitial(Mutable::new);
   private final ConcurrentHashMap<Long, List<HoleEspModule.Hit>> chunkCache = new ConcurrentHashMap<>();
   private final Queue<Long> scanQueue = new ConcurrentLinkedQueue<>();
   private final Set<Long> queued = ConcurrentHashMap.newKeySet();
   private long lastPlayerChunk = Long.MIN_VALUE;
   private int refreshTicks = 0;
   private ClientWorld sessionLevel;
   private boolean lastOptimized;
   private HoleEspModule.FastHoleScan activeScan;
   private boolean[] passableBuffer = new boolean[0];
   private final ModeSetting sMode = this.addSetting(
      "detection", new ModeSetting("Detection", "Holes", "Holes", "All", "Holes + Tunnels", "Holes + Stairs", "Tunnels + Stairs", "Tunnels", "Stairs")
   );
   private final NumberSetting sScanRadius = this.addSetting("scan-radius", new NumberSetting("Scan Radius", 8.0F, 1.0F, 24.0F).step(1.0F));
   private final NumberSetting sChunksPerTick = this.addSetting("chunks-tick", new NumberSetting("Chunks/Tick", 64.0F, 1.0F, 100.0F).step(1.0F));
   private final NumberSetting sScanBudgetMs = this.addSetting("scan-budget-ms", new NumberSetting("Scan Budget ms", 3.0F, 1.0F, 10.0F).step(1.0F));
   private final BooleanSetting sAirOnly = this.addSetting("only-air-blocks", new BooleanSetting("Only Air Blocks", true));
   private final NumberSetting sMinYOffset = this.addSetting("min-y-offset", new NumberSetting("Min Y Offset", 0.0F, 0.0F, 319.0F).step(1.0F));
   private final NumberSetting sMaxYOffset = this.addSetting("max-y-offset", new NumberSetting("Max Y Offset", 0.0F, 0.0F, 319.0F).step(1.0F));
   private final NumberSetting sMinHoleDepth = this.addSetting("min-hole-depth", new NumberSetting("Min Hole Depth", 4.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinTunnelLength = this.addSetting("min-tunnel-length", new NumberSetting("Min Tunnel Length", 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinTunnelHeight = this.addSetting("min-tunnel-height", new NumberSetting("Min Tunnel Height", 2.0F, 1.0F, 10.0F).step(1.0F));
   private final NumberSetting sMaxTunnelHeight = this.addSetting("max-tunnel-height", new NumberSetting("Max Tunnel Height", 3.0F, 2.0F, 10.0F).step(1.0F));
   private final NumberSetting sMinStairLength = this.addSetting("min-stair-length", new NumberSetting("Min Stair Length", 3.0F, 1.0F, 20.0F).step(1.0F));
   private final NumberSetting sMinStairHeight = this.addSetting("min-stair-height", new NumberSetting("Min Stair Height", 3.0F, 2.0F, 10.0F).step(1.0F));
   private final NumberSetting sMaxStairHeight = this.addSetting("max-stair-height", new NumberSetting("Max Stair Height", 5.0F, 2.0F, 10.0F).step(1.0F));
   private final BooleanSetting sFill = this.addSetting("fill", new BooleanSetting("Fill", true));
   private final BooleanSetting sOutline = this.addSetting("outline", new BooleanSetting("Outline", true));
   private final NumberSetting sLineWidth = this.addSetting("line-width", new NumberSetting("Line Width", 1.5F, 0.5F, 5.0F).step(0.5F));
   private final ColorSetting sHoleLine = this.addSetting("1x1-line", new ColorSetting("1x1 Line", 1291845631));
   private final ColorSetting sHoleFill = this.addSetting("1x1-fill", new ColorSetting("1x1 Fill", 1342177279));
   private final ColorSetting sHole3Line = this.addSetting("3x1-line", new ColorSetting("3x1 Line", 644245094));
   private final ColorSetting sHole3Fill = this.addSetting("3x1-fill", new ColorSetting("3x1 Fill", 644245094));
   private final ColorSetting sTunnelLine = this.addSetting("tunnel-line", new ColorSetting("Tunnel Line", 1593835775));
   private final ColorSetting sTunnelFill = this.addSetting("tunnel-fill", new ColorSetting("Tunnel Fill", 503316735));
   private final ColorSetting sStairLine = this.addSetting("stair-line", new ColorSetting("Stair Line", 1610547455));
   private final ColorSetting sStairFill = this.addSetting("stair-fill", new ColorSetting("Stair Fill", 520028415));

   public HoleEspModule() {
      super("hole-esp", "Hole ESP", "Highlights holes, tunnels, and staircases.", ModuleCategory.RENDER);
      this.sAirOnly.visibleWhen(() -> this.currentMode().tunnels() || this.currentMode().stairs());
      List.of(
            this.sMode,
            this.sScanRadius,
            this.sAirOnly,
            this.sMinYOffset,
            this.sMaxYOffset,
            this.sMinHoleDepth,
            this.sMinTunnelLength,
            this.sMinTunnelHeight,
            this.sMaxTunnelHeight,
            this.sMinStairLength,
            this.sMinStairHeight,
            this.sMaxStairHeight
         )
         .forEach(setting -> setting.onChange(this::resetScan));
   }

   @Override
   protected void onEnable() {
      this.resetScan();
   }

   private void resetScan() {
      this.lastPlayerChunk = Long.MIN_VALUE;
      this.refreshTicks = 0;
      this.chunkCache.clear();
      this.scanQueue.clear();
      this.queued.clear();
      this.activeScan = null;
   }

   @Override
   protected void onDisable() {
      this.resetScan();
      this.sessionLevel = null;
      this.passableBuffer = new boolean[0];
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      boolean optimized = PerformanceModule.optimizationsEnabled();
      if (this.lastOptimized != optimized || this.sessionLevel != mc.world) {
         this.resetScan();
      }

      this.lastOptimized = optimized;
      this.sessionLevel = mc.world;
      if (mc.world != null && mc.player != null) {
         int cx = mc.player.getBlockX() >> 4;
         int cz = mc.player.getBlockZ() >> 4;
         long curChunk = ChunkPos.toLong(cx, cz);
         int radius = this.scanRadius(mc);
         boolean fastTravel = isFastTravel(mc);
         boolean periodicRefresh = ++this.refreshTicks >= (fastTravel ? 5 : 20);
         if (curChunk != this.lastPlayerChunk || periodicRefresh) {
            this.refreshTicks = 0;
            this.lastPlayerChunk = curChunk;
            this.chunkCache.keySet().removeIf(keyx -> {
               int kx = chunkX(keyx);
               int kz = chunkZ(keyx);
               return Math.abs(kx - cx) > radius || Math.abs(kz - cz) > radius;
            });
            List<long[]> toQueue = new ArrayList<>();
            Vec3d velocity = mc.player.getVelocity();
            double vx = velocity.x;
            double vz = velocity.z;
            double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
            if (horizontalSpeed > 1.0E-4) {
               vx /= horizontalSpeed;
               vz /= horizontalSpeed;
            }

            for (int x = cx - radius; x <= cx + radius; x++) {
               for (int z = cz - radius; z <= cz + radius; z++) {
                  long key = ChunkPos.toLong(x, z);
                  if (!this.chunkCache.containsKey(key)
                     && !this.queued.contains(key)
                     && (this.activeScan == null || this.activeScan.key != key)
                     && mc.world.getChunkManager().isChunkLoaded(x, z)) {
                     int dx = x - cx;
                     int dz = z - cz;
                     long distScore = (dx * dx + dz * dz) * 1024L;
                     long aheadScore = fastTravel ? Math.round((dx * vx + dz * vz) * 1536.0) : 0L;
                     toQueue.add(new long[]{key, distScore - aheadScore});
                  }
               }
            }

            toQueue.sort(Comparator.comparingLong(a -> a[1]));

            for (long[] entry : toQueue) {
               this.scanQueue.add(entry[0]);
               this.queued.add(entry[0]);
            }
         }

         int chunksPerTick = Math.max(1, Math.round(value(this.sChunksPerTick, 4.0F)));
         float budgetMs = fastTravel && !optimized ? Math.max(value(this.sScanBudgetMs, 3.0F), 8.0F) : value(this.sScanBudgetMs, 3.0F);
         long requested = (long)(Math.max(1.0F, budgetMs) * 1000000.0F);
         long deadline = optimized ? PerformanceBudget.acquire(requested) : System.nanoTime() + requested;
         long started = System.nanoTime();
         if (started < deadline) {
            try {
               int i = 0;

               while (i < chunksPerTick && System.nanoTime() < deadline) {
                  if (this.activeScan != null) {
                     if (!this.activeScan.validFor(mc.world, cx, cz, radius)) {
                        int sx = this.activeScan.chunk.getPos().x;
                        int sz = this.activeScan.chunk.getPos().z;
                        long key = this.activeScan.key;
                        this.activeScan = null;
                        if (Math.abs((long)sx - cx) <= radius
                           && Math.abs((long)sz - cz) <= radius
                           && mc.world.getChunkManager().isChunkLoaded(sx, sz)
                           && this.queued.add(key)) {
                           this.scanQueue.add(key);
                        }
                     } else if (this.activeScan.step()) {
                        this.storeHits(this.activeScan.key, this.activeScan.hits);
                        this.activeScan = null;
                        i++;
                     }
                  } else {
                     Long key = this.scanQueue.poll();
                     if (key == null) {
                        break;
                     }

                     this.queued.remove(key);
                     int sx = chunkX(key);
                     int sz = chunkZ(key);
                     if (mc.world.getChunkManager().isChunkLoaded(sx, sz)) {
                        this.scanChunk(mc.world, sx, sz, key);
                     }

                     if (this.activeScan == null) {
                        i++;
                     }
                  }
               }
            } finally {
               if (optimized) {
                  PerformanceBudget.record(started);
               }
            }
         }
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         this.invalidateAround(chunk.getChunkX(), chunk.getChunkZ());
      }
   }

   private void invalidateAround(int cx, int cz) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int radius = this.scanRadius(mc) + 1;
         if (Math.abs((long)cx - (mc.player.getBlockX() >> 4)) <= radius && Math.abs((long)cz - (mc.player.getBlockZ() >> 4)) <= radius) {
            for (int x = cx - 1; x <= cx + 1; x++) {
               for (int z = cz - 1; z <= cz + 1; z++) {
                  long key = ChunkPos.toLong(x, z);
                  boolean known = this.chunkCache.containsKey(key);
                  if (this.activeScan != null && this.activeScan.key == key) {
                     this.activeScan = null;
                     known = true;
                  }

                  if (known && this.queued.add(key)) {
                     this.scanQueue.add(key);
                  }
               }
            }

            this.lastPlayerChunk = Long.MIN_VALUE;
         }
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (event.pos != null) {
         this.invalidateAround(event.pos.getX() >> 4, event.pos.getZ() >> 4);
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         boolean fill = this.sFill == null || this.sFill.value().get();
         boolean outline = this.sOutline == null || this.sOutline.value().get();
         if (fill || outline) {
            int px = mc.player.getBlockX() >> 4;
            int pz = mc.player.getBlockZ() >> 4;
            int radius = this.scanRadius(mc);
            float lineWidth = value(this.sLineWidth, 1.5F);
            int rendered = 0;

            for (Entry<Long, List<HoleEspModule.Hit>> entry : this.chunkCache.entrySet()) {
               long key = entry.getKey();
               if (Math.abs(chunkX(key) - px) <= radius && Math.abs(chunkZ(key) - pz) <= radius) {
                  for (HoleEspModule.Hit hit : entry.getValue()) {
                     if (++rendered > 8192) {
                        return;
                     }

                     DrawStyle style = !fill
                        ? DrawStyle.stroked(this.lineColor(hit.type()), lineWidth)
                        : (
                           !outline
                              ? DrawStyle.filled(this.fillColor(hit.type()))
                              : DrawStyle.filledAndStroked(this.lineColor(hit.type()), lineWidth, this.fillColor(hit.type()))
                        );
                     GizmoDrawing.box(hit.box(), style).ignoreOcclusion();
                  }
               }
            }
         }
      }
   }

   private void scanChunk(ClientWorld level, int chunkX, int chunkZ, long chunkKey) {
      WorldChunk chunk = level.getChunkManager().getWorldChunk(chunkX, chunkZ);
      if (chunk != null) {
         int baseX = chunkX << 4;
         int baseZ = chunkZ << 4;
         int minBuildY = level.getDimension().minY();
         int maxBuildY = minBuildY + chunk.getSectionArray().length * 16;
         int minY = Math.min(maxBuildY - 1, minBuildY + Math.round(value(this.sMinYOffset, 0.0F)));
         int maxY = Math.max(minY + 1, maxBuildY - Math.round(value(this.sMaxYOffset, 0.0F)));
         HoleEspModule.Mode mode = this.currentMode();
         boolean airOnly = this.sAirOnly != null && this.sAirOnly.value().get();
         if (mode.holes() && !mode.tunnels() && !mode.stairs()) {
            if (PerformanceModule.optimizationsEnabled()) {
               this.activeScan = new HoleEspModule.FastHoleScan(chunk, chunkKey, baseX, baseZ, minY, maxY);
            } else {
               this.storeHits(chunkKey, this.holeHits(level, baseX, baseZ, minY, maxY));
            }
         } else {
            List<HoleEspModule.Hit> hits = mode.holes() ? this.holeHits(level, baseX, baseZ, minY, maxY) : new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Mutable pos = new Mutable();
            ChunkSection[] sections = chunk.getSectionArray();

            for (int si = 0; si < sections.length; si++) {
               ChunkSection section = sections[si];
               if (section != null && !section.isEmpty()) {
                  int sectionBaseY = minBuildY + si * 16;

                  for (int ly = 0; ly < 16; ly++) {
                     int y = sectionBaseY + ly;
                     if (y > minY && y < maxY) {
                        for (int z = 0; z < 16; z++) {
                           for (int x = 0; x < 16; x++) {
                              int wx = baseX + x;
                              int wz = baseZ + z;
                              pos.set(wx, y, wz);
                              if (isPassable(level, pos, airOnly)) {
                                 if (mode.tunnels()) {
                                    this.checkTunnelStarts(level, pos, airOnly, hits, seen);
                                 }

                                 if (mode.stairs()) {
                                    this.checkStaircaseStarts(level, pos, airOnly, hits, seen);
                                 }

                                 if (hits.size() >= 1024) {
                                    this.chunkCache.put(chunkKey, List.copyOf(hits));
                                    return;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            this.storeHits(chunkKey, hits);
         }
      }
   }

   private void storeHits(long key, List<HoleEspModule.Hit> hits) {
      this.chunkCache.put(key, List.copyOf(hits));
   }

   private List<HoleEspModule.Hit> holeHits(ClientWorld level, int baseX, int baseZ, int minY, int maxY) {
      List<HoleEspModule.Hit> hits = new ArrayList<>();
      int gridMinY = minY - 1;
      int yCount = maxY - gridMinY + 1;
      if (yCount <= 1) {
         return hits;
      }

      boolean[] open = new boolean[yCount * 20 * 20];
      Mutable pos = new Mutable();

      for (int yi = 0; yi < yCount; yi++) {
         int y = gridMinY + yi;
         int yBase = yi * 20 * 20;

         for (int lz = -1; lz <= 18; lz++) {
            int zBase = yBase + (lz + 1) * 20;

            for (int lx = -1; lx <= 18; lx++) {
               pos.set(baseX + lx, y, baseZ + lz);
               open[zBase + lx + 1] = isOpen(level, pos);
            }
         }
      }

      int minDepth = Math.round(value(this.sMinHoleDepth, 4.0F));
      Set<String> seen = new HashSet<>();

      for (int column = 0; column < 256 && hits.size() < 1024; column++) {
         shaftsInColumn(open, column & 15, column >> 4, gridMinY, minY, maxY, minDepth, baseX, baseZ, hits, seen);
      }

      return hits;
   }

   private static void shaftsInColumn(
      boolean[] grid, int lx, int lz, int gridMinY, int minY, int maxY, int minDepth, int baseX, int baseZ, List<HoleEspModule.Hit> hits, Set<String> seen
   ) {
      int x = baseX + lx;
      int z = baseZ + lz;
      int single = 0;
      int alongX = 0;
      int alongZ = 0;

      for (int y = minY; y <= maxY; y++) {
         boolean inside = y < maxY;
         if (inside && walled1x1(grid, y, lx, lz, gridMinY)) {
            single++;
         } else {
            single = endShaft(single, y, minDepth, hits, seen, x, z, 1, 1, 0);
         }

         if (inside && walled3x1X(grid, y, lx, lz, gridMinY)) {
            alongX++;
         } else {
            alongX = endShaft(alongX, y, minDepth, hits, seen, x, z, 3, 1, 1);
         }

         if (inside && walled3x1Z(grid, y, lx, lz, gridMinY)) {
            alongZ++;
         } else {
            alongZ = endShaft(alongZ, y, minDepth, hits, seen, x, z, 1, 3, 1);
         }
      }
   }

   private static int endShaft(int run, int top, int minDepth, List<HoleEspModule.Hit> hits, Set<String> seen, int x, int z, int width, int length, int type) {
      if (run >= minDepth) {
         addHit(hits, seen, new Box(x, top - run, z, x + width, top, z + length), type);
      }

      return 0;
   }

   private static boolean walled1x1(boolean[] grid, int y, int lx, int lz, int gridMinY) {
      return grid(grid, y, lx, lz, gridMinY)
         && !grid(grid, y, lx - 1, lz, gridMinY)
         && !grid(grid, y, lx + 1, lz, gridMinY)
         && !grid(grid, y, lx, lz - 1, gridMinY)
         && !grid(grid, y, lx, lz + 1, gridMinY);
   }

   private static boolean walled3x1X(boolean[] grid, int y, int lx, int lz, int gridMinY) {
      return grid(grid, y, lx, lz, gridMinY)
         && grid(grid, y, lx + 1, lz, gridMinY)
         && grid(grid, y, lx + 2, lz, gridMinY)
         && !grid(grid, y, lx - 1, lz, gridMinY)
         && !grid(grid, y, lx + 3, lz, gridMinY)
         && !grid(grid, y, lx, lz - 1, gridMinY)
         && !grid(grid, y, lx, lz + 1, gridMinY)
         && !grid(grid, y, lx + 1, lz - 1, gridMinY)
         && !grid(grid, y, lx + 1, lz + 1, gridMinY)
         && !grid(grid, y, lx + 2, lz - 1, gridMinY)
         && !grid(grid, y, lx + 2, lz + 1, gridMinY);
   }

   private static boolean walled3x1Z(boolean[] grid, int y, int lx, int lz, int gridMinY) {
      return grid(grid, y, lx, lz, gridMinY)
         && grid(grid, y, lx, lz + 1, gridMinY)
         && grid(grid, y, lx, lz + 2, gridMinY)
         && !grid(grid, y, lx, lz - 1, gridMinY)
         && !grid(grid, y, lx, lz + 3, gridMinY)
         && !grid(grid, y, lx - 1, lz, gridMinY)
         && !grid(grid, y, lx + 1, lz, gridMinY)
         && !grid(grid, y, lx - 1, lz + 1, gridMinY)
         && !grid(grid, y, lx + 1, lz + 1, gridMinY)
         && !grid(grid, y, lx - 1, lz + 2, gridMinY)
         && !grid(grid, y, lx + 1, lz + 2, gridMinY);
   }

   private static boolean grid(boolean[] grid, int y, int lx, int lz, int gridMinY) {
      int yi = y - gridMinY;
      return grid[yi * 20 * 20 + (lz + 1) * 20 + lx + 1];
   }

   private void checkTunnelStarts(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      int minLength = Math.round(value(this.sMinTunnelLength, 3.0F));

      for (int[] dir : HORIZONTAL_DIRS) {
         Mutable previous = new Mutable(pos.getX() - dir[0], pos.getY(), pos.getZ() - dir[1]);
         if (!this.isTunnelSection(level, previous, dir, airOnly)) {
            Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());
            BlockPos start = null;
            BlockPos end = null;
            int steps = 0;
            int maxHeight = 0;
            if (this.isTunnelSection(level, current, dir, airOnly)) {
               start = current.toImmutable();
            }

            while (this.isTunnelSection(level, current, dir, airOnly)) {
               maxHeight = Math.max(maxHeight, this.getTunnelHeight(level, current, airOnly));
               end = current.toImmutable();
               current.move(dir[0], 0, dir[1]);
               steps++;
            }

            if (start != null && end != null && steps >= minLength) {
               addHit(
                  hits,
                  seen,
                  new Box(
                     Math.min(start.getX(), end.getX()),
                     start.getY(),
                     Math.min(start.getZ(), end.getZ()),
                     Math.max(start.getX(), end.getX()) + 1,
                     start.getY() + maxHeight,
                     Math.max(start.getZ(), end.getZ()) + 1
                  ),
                  2
               );
            }
         }
      }
   }

   private void checkStaircaseStarts(ClientWorld level, BlockPos pos, boolean airOnly, List<HoleEspModule.Hit> hits, Set<String> seen) {
      int minLength = Math.round(value(this.sMinStairLength, 3.0F));

      for (int[] dir : HORIZONTAL_DIRS) {
         Mutable previous = new Mutable(pos.getX() - dir[0], pos.getY() - 1, pos.getZ() - dir[1]);
         if (!this.isStaircaseSection(level, previous, dir, airOnly)) {
            Mutable current = new Mutable(pos.getX(), pos.getY(), pos.getZ());
            int steps = 0;
            List<Box> boxes = new ArrayList<>();

            while (this.isStaircaseSection(level, current, dir, airOnly)) {
               int height = this.getStaircaseHeight(level, current, airOnly);
               boxes.add(new Box(current.getX(), current.getY(), current.getZ(), current.getX() + 1, current.getY() + height, current.getZ() + 1));
               current.move(dir[0], 1, dir[1]);
               steps++;
            }

            if (steps >= minLength) {
               for (Box box : boxes) {
                  addHit(hits, seen, box, 3);
               }
            }
         }
      }
   }

   private boolean isTunnelSection(ClientWorld level, BlockPos pos, int[] dir, boolean airOnly) {
      int height = this.getTunnelHeight(level, pos, airOnly);
      if (height >= Math.round(value(this.sMinTunnelHeight, 2.0F)) && height <= Math.round(value(this.sMaxTunnelHeight, 3.0F))) {
         if (!isPassable(level, pos.getX(), pos.getY() - 1, pos.getZ(), airOnly) && !isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
            boolean alongX = dir[0] != 0;

            for (int i = 0; i < height; i++) {
               int y = pos.getY() + i;
               if (alongX) {
                  if (isPassable(level, pos.getX(), y, pos.getZ() - 1, airOnly) || isPassable(level, pos.getX(), y, pos.getZ() + 1, airOnly)) {
                     return false;
                  }
               } else if (isPassable(level, pos.getX() - 1, y, pos.getZ(), airOnly) || isPassable(level, pos.getX() + 1, y, pos.getZ(), airOnly)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isStaircaseSection(ClientWorld level, BlockPos pos, int[] dir, boolean airOnly) {
      int height = this.getStaircaseHeight(level, pos, airOnly);
      if (height >= Math.round(value(this.sMinStairHeight, 3.0F)) && height <= Math.round(value(this.sMaxStairHeight, 5.0F))) {
         if (!isPassable(level, pos.getX(), pos.getY() - 1, pos.getZ(), airOnly) && !isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
            boolean alongX = dir[0] != 0;

            for (int i = 0; i < height; i++) {
               int y = pos.getY() + i;
               if (alongX) {
                  if (isPassable(level, pos.getX(), y, pos.getZ() - 1, airOnly) || isPassable(level, pos.getX(), y, pos.getZ() + 1, airOnly)) {
                     return false;
                  }
               } else if (isPassable(level, pos.getX() - 1, y, pos.getZ(), airOnly) || isPassable(level, pos.getX() + 1, y, pos.getZ(), airOnly)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int getTunnelHeight(ClientWorld level, BlockPos pos, boolean airOnly) {
      int height = 0;
      int max = Math.round(value(this.sMaxTunnelHeight, 3.0F));

      while (height < max && isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
         height++;
      }

      return height;
   }

   private int getStaircaseHeight(ClientWorld level, BlockPos pos, boolean airOnly) {
      int height = 0;
      int max = Math.round(value(this.sMaxStairHeight, 5.0F));

      while (height < max && isPassable(level, pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) {
         height++;
      }

      return height;
   }

   private static boolean isOpen(ClientWorld level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
   }

   private static boolean isPassable(ClientWorld level, int x, int y, int z, boolean airOnly) {
      return isPassable(level, PASSABLE_POS.get().set(x, y, z), airOnly);
   }

   private static boolean isPassable(ClientWorld level, BlockPos pos, boolean airOnly) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
         return true;
      }

      if (airOnly) {
         return false;
      }

      VoxelShape shape = state.getCollisionShape(level, pos);
      return shape.isEmpty() || !VoxelShapes.fullCube().equals(shape);
   }

   private static void addHit(List<HoleEspModule.Hit> hits, Set<String> seen, Box box, int type) {
      String key = type + ":" + box.minX + "," + box.minY + "," + box.minZ + "," + box.maxX + "," + box.maxY + "," + box.maxZ;
      if (seen.add(key)) {
         for (HoleEspModule.Hit hit : hits) {
            if (hit.type() == type && hit.box().intersects(box)) {
               return;
            }
         }

         hits.add(new HoleEspModule.Hit(box, type));
      }
   }

   private HoleEspModule.Mode currentMode() {
      String mode = this.sMode == null ? "All" : this.sMode.value();

      return switch (mode) {
         case "Holes + Tunnels" -> new HoleEspModule.Mode(true, true, false);
         case "Holes + Stairs" -> new HoleEspModule.Mode(true, false, true);
         case "Tunnels + Stairs" -> new HoleEspModule.Mode(false, true, true);
         case "Holes" -> new HoleEspModule.Mode(true, false, false);
         case "Tunnels" -> new HoleEspModule.Mode(false, true, false);
         case "Stairs" -> new HoleEspModule.Mode(false, false, true);
         default -> new HoleEspModule.Mode(true, true, true);
      };
   }

   private int lineColor(int type) {
      return switch (type) {
         case 1 -> color(this.sHole3Line, 1610589440);
         case 2 -> color(this.sTunnelLine, 1593835775);
         case 3 -> color(this.sStairLine, 1610547455);
         default -> color(this.sHoleLine, 1610547200);
      };
   }

   private int fillColor(int type) {
      return switch (type) {
         case 1 -> color(this.sHole3Fill, 520070400);
         case 2 -> color(this.sTunnelFill, 503316735);
         case 3 -> color(this.sStairFill, 520028415);
         default -> color(this.sHoleFill, 520028160);
      };
   }

   private static int chunkX(long key) {
      return (int)key;
   }

   private static int chunkZ(long key) {
      return (int)(key >> 32);
   }

   private int scanRadius(MinecraftClient mc) {
      int renderDistance = mc.options.getViewDistance().getValue();
      float configuredValue = value(this.sScanRadius, 4.0F);
      if (isFastTravel(mc)) {
         configuredValue = Math.max(configuredValue, 12.0F);
      }

      int configured = Math.max(1, Math.round(configuredValue));
      return Math.min(renderDistance, configured);
   }

   private static boolean isFastTravel(MinecraftClient mc) {
      if (mc.player == null) {
         return false;
      }

      Vec3d velocity = mc.player.getVelocity();
      double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
      return mc.player.isGliding() || horizontalSpeedSq > 0.18;
   }

   private static float value(NumberSetting setting, float fallback) {
      return setting == null ? fallback : setting.value()[0];
   }

   private static int color(ColorSetting setting, int fallback) {
      return setting == null ? fallback : setting.argb();
   }

   @Environment(EnvType.CLIENT)
   private final class FastHoleScan {
      final WorldChunk chunk;
      final ClientWorld level;
      final WorldChunk[] neighbors = new WorldChunk[9];
      final long key;
      final int baseX;
      final int baseZ;
      final int minY;
      final int maxY;
      final int gridMinY;
      final int yCount;
      final int minDepth;
      final boolean[] passable;
      final List<HoleEspModule.Hit> hits = new ArrayList<>();
      final Set<String> seen = new HashSet<>();
      final Mutable pos = new Mutable();
      int row;
      int column;

      FastHoleScan(WorldChunk chunk, long key, int baseX, int baseZ, int minY, int maxY) {
         this.chunk = chunk;
         this.level = HoleEspModule.this.sessionLevel;
         this.key = key;
         this.baseX = baseX;
         this.baseZ = baseZ;
         this.minY = minY;
         this.maxY = maxY;
         this.gridMinY = minY - 1;
         this.yCount = maxY - this.gridMinY + 1;
         this.minDepth = Math.round(HoleEspModule.value(HoleEspModule.this.sMinHoleDepth, 4.0F));
         int size = Math.max(0, this.yCount) * 20 * 20;
         if (HoleEspModule.this.passableBuffer.length < size) {
            HoleEspModule.this.passableBuffer = new boolean[size];
         }

         this.passable = HoleEspModule.this.passableBuffer;

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               this.neighbors[(dx + 1) * 3 + dz + 1] = this.level.getChunkManager().getWorldChunk((baseX >> 4) + dx, (baseZ >> 4) + dz);
            }
         }
      }

      boolean validFor(ClientWorld currentLevel, int centerX, int centerZ, int radius) {
         if (currentLevel == this.level
            && Math.abs((long)this.chunk.getPos().x - centerX) <= radius
            && Math.abs((long)this.chunk.getPos().z - centerZ) <= radius) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (this.neighbors[(dx + 1) * 3 + dz + 1] != this.level.getChunkManager().getWorldChunk((this.baseX >> 4) + dx, (this.baseZ >> 4) + dz)) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      }

      boolean step() {
         if (this.yCount <= 1) {
            return true;
         }

         if (this.row < this.yCount) {
            int y = this.gridMinY + this.row;
            int yBase = this.row++ * 20 * 20;

            for (int lz = -1; lz <= 18; lz++) {
               int zBase = yBase + (lz + 1) * 20;

               for (int lx = -1; lx <= 18; lx++) {
                  this.pos.set(this.baseX + lx, y, this.baseZ + lz);
                  this.passable[zBase + lx + 1] = HoleEspModule.isOpen(this.level, this.pos);
               }
            }

            return false;
         } else {
            if (this.column >= 256) {
               return true;
            }

            int lx = this.column & 15;
            int lz = this.column++ >> 4;
            HoleEspModule.shaftsInColumn(
               this.passable, lx, lz, this.gridMinY, this.minY, this.maxY, this.minDepth, this.baseX, this.baseZ, this.hits, this.seen
            );
            return this.column >= 256 || this.hits.size() >= 1024;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Hit(Box box, int type) {
   }

   @Environment(EnvType.CLIENT)
   private record Mode(boolean holes, boolean tunnels, boolean stairs) {
   }
}
