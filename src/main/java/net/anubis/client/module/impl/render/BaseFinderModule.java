package net.anubis.client.module.impl.render;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.anubis.client.basefinder.BaseEvidenceScanner;
import net.anubis.client.basefinder.BaseFinderEntry;
import net.anubis.client.basefinder.BaseFinderHud;
import net.anubis.client.basefinder.ChunkReport;
import net.anubis.client.basefinder.Signal;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.LevelUtil;
import net.anubis.client.util.PerformanceBudget;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * One module for base hunting: scores every loaded chunk for player activity (placed blocks,
 * rotated deepslate, hand-mined tunnels and shafts, grown amethyst and kelp, players hiding
 * underground), ranks them by probability, and explains each find.
 */
@Environment(EnvType.CLIENT)
public final class BaseFinderModule extends ClientModule implements HudOverlayProvider {
   private static final int RESCAN_DELAY_TICKS = 40;
   private static final int RANK_EVERY_TICKS = 10;
   private static final int UNDERGROUND_DEPTH = 8;
   private static final int MAX_REPORTS = 8192;
   private static final long NOTIFY_COOLDOWN_MS = 1500L;

   private final NumberSetting threshold = this.addSetting("min-probability", new NumberSetting("Min Probability %", 40.0F, 10.0F, 95.0F).step(5.0F));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 512.0F, 64.0F, 2048.0F).step(16.0F));
   private final BooleanSetting scanBlocks = this.addSetting("placed-blocks", new BooleanSetting("Placed Blocks", true));
   private final BooleanSetting scanTunnels = this.addSetting("tunnels", new BooleanSetting("Tunnels & Shafts", true));
   private final NumberSetting tunnelLength = this.addSetting("tunnel-length", new NumberSetting("Tunnel Length", 8.0F, 5.0F, 15.0F).step(1.0F));
   private final BooleanSetting scanGrowth = this.addSetting("growth", new BooleanSetting("Growth (Amethyst, Kelp)", true));
   private final BooleanSetting scanPlayers = this.addSetting("live-players", new BooleanSetting("Live Players", true));
   private final NumberSetting playerMemory = this.addSetting("player-memory", new NumberSetting("Player Memory (s)", 120.0F, 10.0F, 600.0F).step(10.0F));
   private final ModeSetting highlight = this.addSetting("highlight", new ModeSetting("Highlight", "Evidence Depth", "Evidence Depth", "Fixed Height", "Off"));
   private final NumberSetting highlightY = this.addSetting("highlight-height", new NumberSetting("Height", 64.0F, -64.0F, 320.0F).step(1.0F));
   private final ColorSetting lowColor = this.addSetting("low-color", new ColorSetting("Low Color", 0xFFFFC14D));
   private final ColorSetting highColor = this.addSetting("high-color", new ColorSetting("High Color", 0xFFFF4D6A));
   private final BooleanSetting dots = this.addSetting("reason-dots", new BooleanSetting("Reason Dots", true));
   private final BooleanSetting panel = this.addSetting("panel", new BooleanSetting("Panel", true));
   private final NumberSetting panelRows = this.addSetting("panel-rows", new NumberSetting("Panel Rows", 5.0F, 1.0F, 12.0F).step(1.0F));
   private final NumberSetting panelX = this.addSetting("panel-x", new NumberSetting("Panel X", 8.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting panelY = this.addSetting("panel-y", new NumberSetting("Panel Y", 8.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting panelScale = this.addSetting("panel-scale", new NumberSetting("Scale", 1.0F, 0.6F, 2.0F).step(0.05F));
   private final BooleanSetting tracer = this.addSetting("tracer", new BooleanSetting("Tracer To Best", false));
   private final NumberSetting notifyAbove = this.addSetting("notify-above", new NumberSetting("Notify Above %", 60.0F, 10.0F, 100.0F).step(5.0F));

   private final BaseEvidenceScanner scanner = new BaseEvidenceScanner();
   private final BaseFinderHud hud = new BaseFinderHud();
   private final Long2ObjectOpenHashMap<ChunkReport> reports = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Sighting> sightings = new Long2ObjectOpenHashMap<>();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private final Long2IntOpenHashMap dirtyAt = new Long2IntOpenHashMap();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private volatile List<BaseFinderEntry> ranked = List.of();
   private ClientWorld lastLevel;
   private boolean scanning;
   private int ticks;
   private boolean rankDirty = true;
   private long lastNotifyAt;
   private int pendingNotices;
   private BaseFinderEntry pendingBest;

   public BaseFinderModule() {
      super("base-finder", "Base Finder", "Ranks loaded chunks by how likely they hide a base, and shows why.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.notifyAbove.visibleWhen(notifications::get);
      this.tunnelLength.visibleWhen(this.scanTunnels::get);
      this.playerMemory.visibleWhen(this.scanPlayers::get);
      this.highlightY.visibleWhen(() -> this.highlight.is("Fixed Height"));
      this.panelRows.visibleWhen(this.panel::get);
      this.panelX.visibleWhen(this.panel::get);
      this.panelY.visibleWhen(this.panel::get);
   }

   @Override
   protected void onEnable() {
      this.startLevel(MinecraftClient.getInstance().world);
   }

   @Override
   protected void onDisable() {
      this.startLevel(null);
   }

   private void startLevel(ClientWorld level) {
      this.reports.clear();
      this.sightings.clear();
      this.queue.clear();
      this.queued.clear();
      this.dirtyAt.clear();
      this.announced.clear();
      this.ranked = List.of();
      this.rankDirty = true;
      this.pendingNotices = 0;
      this.pendingBest = null;
      this.lastLevel = level;
      this.scanning = level != null && level.getRegistryKey() != World.END;
      if (this.scanning) {
         this.queueLoadedChunks(level);
      }
   }

   private void queueLoadedChunks(ClientWorld level) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int radius = mc.options.getViewDistance().getValue() + 1;
         ChunkPos center = mc.player.getChunkPos();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (LevelUtil.isLoaded(level, x, z)) {
                  this.enqueue(ChunkPos.toLong(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.scanning && this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         this.enqueue(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (this.scanning && event.pos != null) {
         long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
         if (!this.dirtyAt.containsKey(key)) {
            this.dirtyAt.put(key, this.ticks);
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.startLevel(level);
      }

      if (!this.scanning || mc.player == null) {
         return;
      }

      this.ticks++;
      this.flushDirty();
      this.scanQueued(level);
      if (this.scanPlayers.get()) {
         this.trackPlayers(mc, level);
      }

      if (this.rankDirty || this.ticks % RANK_EVERY_TICKS == 0) {
         this.rank(mc, level);
         this.rankDirty = false;
      }

      this.flushNotices();
   }

   private void flushDirty() {
      if (!this.dirtyAt.isEmpty()) {
         var it = this.dirtyAt.long2IntEntrySet().fastIterator();

         while (it.hasNext()) {
            var entry = it.next();
            if (this.ticks - entry.getIntValue() >= RESCAN_DELAY_TICKS) {
               this.enqueue(entry.getLongKey());
               it.remove();
            }
         }
      }
   }

   private void scanQueued(ClientWorld level) {
      boolean optimized = PerformanceModule.optimizationsEnabled();
      long deadline = optimized ? PerformanceBudget.acquire(PerformanceModule.scanBudgetNanos()) : System.nanoTime() + PerformanceModule.scanBudgetNanos();
      long started = System.nanoTime();
      BaseEvidenceScanner.Options options = new BaseEvidenceScanner.Options(
         this.scanBlocks.get(), this.scanTunnels.get(), this.scanGrowth.get(), this.tunnelLength.getInt()
      );

      try {
         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               WorldChunk chunk = level.getChunkManager().getWorldChunk(cx, cz);
               if (chunk != null) {
                  ChunkReport report = this.scanner.scan(level, chunk, options);
                  if (report == null) {
                     this.reports.remove(key);
                  } else {
                     this.reports.put(key, report);
                  }

                  this.rankDirty = true;
               }
            }
         }

         if (this.reports.size() > MAX_REPORTS) {
            this.reports.keySet().removeIf(k -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)));
         }
      } finally {
         if (optimized) {
            PerformanceBudget.record(started);
         }
      }
   }

   private void trackPlayers(MinecraftClient mc, ClientWorld level) {
      long now = System.currentTimeMillis();

      for (AbstractClientPlayerEntity other : level.getPlayers()) {
         if (other == mc.player || other.isSpectator()) {
            continue;
         }

         BlockPos pos = other.getBlockPos();
         int cx = pos.getX() >> 4;
         int cz = pos.getZ() >> 4;
         if (!LevelUtil.isLoaded(level, cx, cz)) {
            continue;
         }

         WorldChunk chunk = level.getChunkManager().getWorldChunk(cx, cz);
         if (chunk != null) {
            int surface = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15);
            if (pos.getY() <= surface - UNDERGROUND_DEPTH) {
               long key = ChunkPos.toLong(cx, cz);
               Sighting was = this.sightings.put(key, new Sighting(other.getGameProfile().name(), now, pos.asLong()));
               if (was == null) {
                  this.rankDirty = true;
               }
            }
         }
      }

      long memory = (long)(this.playerMemory.get() * 1000.0F);
      if (this.sightings.values().removeIf(s -> now - s.seenAt() > memory)) {
         this.rankDirty = true;
      }
   }

   private float playerStrength(Sighting sighting, long now) {
      if (sighting == null || !this.scanPlayers.get()) {
         return 0.0F;
      }

      float memory = this.playerMemory.get() * 1000.0F;
      return Math.max(0.0F, 1.0F - (now - sighting.seenAt()) / memory * 0.5F);
   }

   private void rank(MinecraftClient mc, ClientWorld level) {
      boolean[] groups = new boolean[Signal.Group.values().length];
      groups[Signal.Group.BLOCKS.ordinal()] = this.scanBlocks.get();
      groups[Signal.Group.TUNNELS.ordinal()] = this.scanTunnels.get();
      groups[Signal.Group.GROWTH.ordinal()] = this.scanGrowth.get();
      groups[Signal.Group.PLAYERS.ordinal()] = this.scanPlayers.get();
      float min = this.threshold.get() / 100.0F;
      double maxDistance = this.range.get();
      Vec3d eye = mc.player.getEyePos();
      float yaw = mc.player.getYaw();
      long now = System.currentTimeMillis();
      LongOpenHashSet keys = new LongOpenHashSet(this.reports.keySet());
      keys.addAll(this.sightings.keySet());
      List<BaseFinderEntry> out = new ArrayList<>();
      var it = keys.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         double centerX = (cx << 4) + 8.0;
         double centerZ = (cz << 4) + 8.0;
         double distance = Math.hypot(centerX - eye.x, centerZ - eye.z);
         if (distance > maxDistance) {
            continue;
         }

         ChunkReport report = this.reports.get(key);
         Sighting sighting = this.sightings.get(key);
         if (report == null) {
            report = new ChunkReport(key);
         }

         float playerStrength = this.playerStrength(sighting, now);
         float p = report.probability(groups, playerStrength);
         if (p < min) {
            continue;
         }

         List<ChunkReport.Reason> reasons = report.reasons(groups, playerStrength, sighting == null ? null : sighting.name());
         long anchor = playerStrength > 0.0F && (report.anchor == 0L || Signal.PLAYER.weight >= reasons.get(0).signal().weight)
            ? sighting.pos()
            : report.anchor;
         BlockPos at = anchor == 0L ? BlockPos.ofFloored(centerX, eye.y, centerZ) : BlockPos.fromLong(anchor);
         double targetYaw = Math.toDegrees(Math.atan2(-(centerX - eye.x), centerZ - eye.z));
         float bearing = MathHelper.wrapDegrees((float)targetYaw - yaw);
         float t = MathHelper.clamp((p - min) / Math.max(0.05F, 1.0F - min), 0.0F, 1.0F);
         int argb = ColorHelper.lerp(t, this.lowColor.argb(), this.highColor.argb()) | 0xFF000000;
         out.add(new BaseFinderEntry(key, (int)centerX, (int)centerZ, p, List.copyOf(reasons), Vec3d.ofCenter(at), distance, bearing, argb));
         if (p * 100.0F >= this.notifyAbove.get() && this.announced.add(key)) {
            this.pendingNotices++;
            if (this.pendingBest == null || p > this.pendingBest.probability()) {
               this.pendingBest = out.get(out.size() - 1);
            }
         }
      }

      out.sort(Comparator.comparingDouble(BaseFinderEntry::probability).reversed().thenComparingDouble(BaseFinderEntry::distance));
      this.ranked = List.copyOf(out);
   }

   private void flushNotices() {
      if (this.pendingNotices == 0) {
         return;
      }

      if (!this.notificationsOn()) {
         this.pendingNotices = 0;
         this.pendingBest = null;
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.lastNotifyAt >= NOTIFY_COOLDOWN_MS && this.pendingBest != null) {
         BaseFinderEntry best = this.pendingBest;
         ModuleChat.Body body = ModuleChat.body()
            .good(Math.round(best.probability() * 100.0F) + "%")
            .text(" chunk at ")
            .coords(best.blockX(), (int)best.anchor().y, best.blockZ())
            .text(": " + best.topReason());
         if (this.pendingNotices > 1) {
            body.text(" (+" + (this.pendingNotices - 1) + " more)");
         }

         ModuleChat.send(this, body);
         this.pendingNotices = 0;
         this.pendingBest = null;
         this.lastNotifyAt = now;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      List<BaseFinderEntry> entries = this.ranked;
      if (mc.world == null || entries.isEmpty()) {
         return;
      }

      if (!this.highlight.is("Off")) {
         FoglessLines lines = FoglessLines.forFrame(event.partialTick);
         boolean fixed = this.highlight.is("Fixed Height");

         for (BaseFinderEntry entry : entries) {
            double y = fixed ? this.highlightY.get() : Math.floor(entry.anchor().y) + 0.02;
            double x0 = (ChunkPos.getPackedX(entry.chunk()) << 4) + 0.5;
            double z0 = (ChunkPos.getPackedZ(entry.chunk()) << 4) + 0.5;
            double x1 = x0 + 15.0;
            double z1 = z0 + 15.0;
            int fillAlpha = Math.round(24.0F + 56.0F * entry.probability());
            Vec3d[] outline = new Vec3d[]{new Vec3d(x0, y, z0), new Vec3d(x1, y, z0), new Vec3d(x1, y, z1), new Vec3d(x0, y, z1)};
            GizmoDrawing.collect(new Tile(outline, ColorHelper.withAlpha(fillAlpha, entry.argb()))).ignoreOcclusion();
            int edge = ColorHelper.withAlpha(200, entry.argb());

            for (int i = 0; i < 4; i++) {
               lines.line(outline[i], outline[(i + 1) % 4], edge, 1.5F);
            }
         }
      }

      if (this.tracer.get()) {
         BaseFinderEntry best = entries.get(0);
         TracerEngine.draw(best.anchor(), best.argb());
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.scanning && (this.panel.get() || this.dots.get() && !this.ranked.isEmpty());
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      BaseFinderHud.Style style = new BaseFinderHud.Style(
         this.panel.get(), this.dots.get(), this.panelX.get(), this.panelY.get(), this.panelRows.getInt(), this.panelScale.get()
      );
      this.hud.render(dl, screenW, screenH, this.ranked, style);
   }

   @Environment(EnvType.CLIENT)
   private record Sighting(String name, long seenAt, long pos) {
   }

   @Environment(EnvType.CLIENT)
   private record Tile(Vec3d[] outline, int color) implements Gizmo {
      public void draw(GizmoDrawer primitives, float alpha) {
         primitives.addPolygon(this.outline, ColorHelper.withAlpha(Math.round(ColorHelper.getAlpha(this.color) * alpha), this.color));
      }
   }
}
