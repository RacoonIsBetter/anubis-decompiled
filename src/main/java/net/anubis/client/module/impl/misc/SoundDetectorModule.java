package net.anubis.client.module.impl.misc;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.basefinder.HeardSound;
import net.anubis.client.basefinder.SoundHistoryHud;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Marks player-made sounds (containers, workstations, doors, mining, building, player actions and,
 * opt-in, running machinery) that no visible player was close enough to make: someone working where
 * you can't see them. Alerts in chat with an optional chime, keeps a history panel, and snoozes a
 * spot after it alerts so a farm or a busy base doesn't spam. Base Finder reads the same sounds.
 */
@Environment(EnvType.CLIENT)
public final class SoundDetectorModule extends ClientModule implements HudOverlayProvider {
   private static final long NOTIFY_COOLDOWN_MS = 1000L;
   private static final int UNDERGROUND_DEPTH = 4;
   /** Alerts closer than this to a snoozed spot stay quiet (markers still update). */
   private static final double SNOOZE_RADIUS = 12.0;

   private final Map<HeardSound.Kind, BooleanSetting> kinds = new EnumMap<>(HeardSound.Kind.class);
   private final BooleanSetting undergroundOnly = this.addSetting("underground-only", new BooleanSetting("Underground Only", false));
   private final NumberSetting memory = this.addSetting("memory", new NumberSetting("Memory (s)", 60.0F, 5.0F, 600.0F).step(5.0F));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final BooleanSetting chime = this.addSetting("chime", new BooleanSetting("Chime", true));
   private final NumberSetting snooze = this.addSetting("snooze", new NumberSetting("Snooze Same Spot (s)", 30.0F, 0.0F, 600.0F).step(5.0F));
   private final BooleanSetting history = this.addSetting("history", new BooleanSetting("History Panel", true));
   private final NumberSetting historyRows = this.addSetting("history-rows", new NumberSetting("History Rows", 6.0F, 1.0F, 15.0F).step(1.0F));
   private final NumberSetting historyX = this.addSetting("history-x", new NumberSetting("History X", 8.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting historyY = this.addSetting("history-y", new NumberSetting("History Y", 220.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting historyScale = this.addSetting("history-scale", new NumberSetting("History Scale", 1.0F, 0.6F, 2.0F).step(0.05F));
   private final SoundHistoryHud hud = new SoundHistoryHud();
   /** Where alerts went off recently, with when: the snooze covers their surroundings. */
   private final Long2LongOpenHashMap alerted = new Long2LongOpenHashMap();
   private final Long2ObjectOpenHashMap<SoundDetectorModule.Marker> markers = new Long2ObjectOpenHashMap<>();
   private long lastNotifyAt;

   public SoundDetectorModule() {
      super(
         "sound-detector",
         "Sound Detector",
         "Marks chests, workstations, doors, mining and building heard where no visible player is: someone working out of sight.",
         ModuleCategory.BASE_RAID
      );

      for (HeardSound.Kind kind : HeardSound.Kind.values()) {
         // Machinery keeps running once built, so it is opt-in: a farm next to you would never stop.
         this.kinds.put(kind, this.addSetting(kind.name().toLowerCase(), new BooleanSetting(kind.label, kind != HeardSound.Kind.MACHINERY)));
      }

      this.usesNotifications();
      this.historyRows.visibleWhen(this.history::get);
      this.historyX.visibleWhen(this.history::get);
      this.historyY.visibleWhen(this.history::get);
      this.historyScale.visibleWhen(this.history::get);
   }

   @Override
   protected void onDisable() {
      this.markers.clear();
      this.alerted.clear();
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return;
      }

      HeardSound heard = HeardSound.classify(event.packet, level);
      BooleanSetting wanted = heard == null ? null : this.kinds.get(heard.kind());
      if (wanted == null || !wanted.get() || this.undergroundOnly.get() && !underground(level, heard.blockPos())) {
         return;
      }

      long now = System.currentTimeMillis();
      long key = heard.blockPos().asLong();
      SoundDetectorModule.Marker was = this.markers.get(key);
      this.markers.put(key, new SoundDetectorModule.Marker(heard, was == null ? 1 : was.count() + 1, now));
      if (was == null && now - this.lastNotifyAt >= NOTIFY_COOLDOWN_MS && !this.snoozed(heard.blockPos(), now)) {
         this.lastNotifyAt = now;
         this.alerted.put(key, now);
         BlockPos pos = heard.blockPos();
         ModuleChat.send(this, ModuleChat.body().text("Heard ").warn(heard.what()).text(" at ").coords(pos.getX(), pos.getY(), pos.getZ()).text(" with nobody in sight"));
         if (this.chime.get() && this.notificationsOn()) {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.6F));
         }
      }
   }

   /** A spot that alerted within the snooze time, or anything near it (a farm, a busy base), stays quiet. */
   private boolean snoozed(BlockPos pos, long now) {
      long window = (long)(this.snooze.get() * 1000.0F);
      if (window <= 0L) {
         return false;
      }

      double radius = SNOOZE_RADIUS * SNOOZE_RADIUS;
      var it = this.alerted.long2LongEntrySet().fastIterator();

      while (it.hasNext()) {
         var entry = it.next();
         if (now - entry.getLongValue() > window) {
            it.remove();
         } else if (BlockPos.fromLong(entry.getLongKey()).getSquaredDistance(pos) < radius) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      long memory = (long)(this.memory.get() * 1000.0F);
      long now = System.currentTimeMillis();
      this.markers.values().removeIf(m -> now - m.lastAt() > memory);
   }

   private static boolean underground(ClientWorld level, BlockPos pos) {
      WorldChunk chunk = level.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
      return chunk != null && pos.getY() <= chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15) - UNDERGROUND_DEPTH;
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.history.get();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      MinecraftClient mc = MinecraftClient.getInstance();
      long now = System.currentTimeMillis();
      List<SoundDetectorModule.Marker> newest = new ArrayList<>(this.markers.values());
      newest.sort(Comparator.comparingLong(SoundDetectorModule.Marker::lastAt).reversed());
      List<SoundHistoryHud.Row> rows = new ArrayList<>();

      for (SoundDetectorModule.Marker marker : newest.subList(0, Math.min(newest.size(), this.historyRows.getInt()))) {
         BlockPos pos = marker.heard().blockPos();
         int distance = mc.player == null ? 0 : (int)Math.round(Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pos)));
         rows.add(new SoundHistoryHud.Row(marker.heard().what(), pos, marker.count(), ago(now - marker.lastAt()), distance, marker.heard().kind().color));
      }

      String status = this.markers.isEmpty() ? "listening" : this.markers.size() + (this.markers.size() == 1 ? " spot" : " spots");
      this.hud.render(dl, rows, this.historyX.get(), this.historyY.get(), this.historyScale.get(), status);
   }

   private static String ago(long millis) {
      long seconds = millis / 1000L;
      return seconds < 60L ? seconds + "s ago" : seconds / 60L + "m ago";
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.markers.isEmpty()) {
         return;
      }

      FoglessLines lines = FoglessLines.forFrame(event.partialTick);
      float memory = this.memory.get() * 1000.0F;
      long now = System.currentTimeMillis();

      for (SoundDetectorModule.Marker marker : this.markers.values()) {
         float life = Math.max(0.15F, 1.0F - (now - marker.lastAt()) / memory);
         int color = marker.heard().kind().color;
         Vec3d c = Vec3d.ofCenter(marker.heard().blockPos());
         Box box = new Box(c.x - 0.4, c.y - 0.4, c.z - 0.4, c.x + 0.4, c.y + 0.4, c.z + 0.4);
         GizmoDrawing.box(box, DrawStyle.filled(ColorHelper.withAlpha(Math.round(70 * life), color))).ignoreOcclusion();
         int edge = ColorHelper.withAlpha(Math.round(230 * life), color);
         Vec3d[] bottom = {new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.minX, box.minY, box.maxZ)};

         for (int i = 0; i < 4; i++) {
            Vec3d a = bottom[i];
            Vec3d b = bottom[(i + 1) % 4];
            lines.line(a, b, edge, 1.5F);
            lines.line(a.add(0.0, 0.8, 0.0), b.add(0.0, 0.8, 0.0), edge, 1.5F);
            lines.line(a, a.add(0.0, 0.8, 0.0), edge, 1.5F);
         }

         if (this.tracers.get()) {
            TracerEngine.draw(c, ColorHelper.withAlpha(Math.round(255 * life), color));
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Marker(HeardSound heard, int count, long lastAt) {
   }
}
