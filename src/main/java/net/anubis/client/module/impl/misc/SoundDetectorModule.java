package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.EnumMap;
import java.util.Map;
import net.anubis.client.basefinder.HeardSound;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Marks player-made sounds (containers, workstations, doors, mining, building) that no visible
 * player was close enough to make: someone working where you can't see them. Base Finder reads the
 * same sounds as hidden-player evidence.
 */
@Environment(EnvType.CLIENT)
public final class SoundDetectorModule extends ClientModule {
   private static final long NOTIFY_COOLDOWN_MS = 1000L;
   private static final int UNDERGROUND_DEPTH = 4;

   private final Map<HeardSound.Kind, BooleanSetting> kinds = new EnumMap<>(HeardSound.Kind.class);
   private final BooleanSetting undergroundOnly = this.addSetting("underground-only", new BooleanSetting("Underground Only", false));
   private final NumberSetting memory = this.addSetting("memory", new NumberSetting("Memory (s)", 60.0F, 5.0F, 600.0F).step(5.0F));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
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
         this.kinds.put(kind, this.addSetting(kind.name().toLowerCase(), new BooleanSetting(kind.label, true)));
      }

      this.usesNotifications();
   }

   @Override
   protected void onDisable() {
      this.markers.clear();
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
      if (was == null && now - this.lastNotifyAt >= NOTIFY_COOLDOWN_MS) {
         this.lastNotifyAt = now;
         BlockPos pos = heard.blockPos();
         ModuleChat.send(this, ModuleChat.body().text("Heard ").warn(heard.what()).text(" at ").coords(pos.getX(), pos.getY(), pos.getZ()).text(" with nobody in sight"));
      }
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
