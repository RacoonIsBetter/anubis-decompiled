package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.ChunkAreas;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.playerbypass.ChunkLightSnapshot;
import net.anubis.client.util.playerbypass.PlayerBypassLightTracker;
import net.anubis.client.util.playerbypass.VisibleChunkCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.math.ColorHelper;

@Environment(EnvType.CLIENT)
public final class PlayerBypassModule extends ClientModule {
   private static final int RENDER_DISTANCE_CHUNKS = 16;
   private static final int ZONE_RADIUS = 5;
   private static final double MARKER_Y = 64.0;
   private static final float LINE_WIDTH = 1.5F;
   private static final float CENTER_FILL = 0.45F;
   private static final float RIM_FILL = 0.25F;
   private static final float EDGE_SHADE = 0.8F;
   private static final int BASE_COLOR = -2516480;
   private static final int SUSPECT_COLOR = -5766;
   private final NumberSetting opacity = this.addSetting("opacity", new NumberSetting("Opacity", 140.0F, 10.0F, 255.0F).step(1.0F));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final ChunkAreas suspectAreas = new ChunkAreas();
   private final Long2FloatOpenHashMap baseWeight = new Long2FloatOpenHashMap();
   private final Long2FloatOpenHashMap suspectWeight = new Long2FloatOpenHashMap();
   private final LongOpenHashSet zone = new LongOpenHashSet();
   private final VisibleChunkCache visibleChunks = new VisibleChunkCache(16);
   private boolean zoneDirty = true;
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final BooleanSetting showCoordinates = this.addSetting("show-coordinates", new BooleanSetting("Show Coordinates", true));
   private final BooleanSetting disconnect = this.addSetting("disconnect-on-spotted", new BooleanSetting("Auto Disconnect", false));

   public PlayerBypassModule() {
      super("player-bypass", "Player Bypass", "Flags chunks with signs of underground player bases from light data.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.sound.visibleWhen(notifications::get);
      this.showCoordinates.visibleWhen(() -> notifications.get() || this.disconnect.get());
   }

   @Override
   protected void onEnable() {
      this.visibleChunks.invalidate();
      PlayerBypassLightTracker.setSpottedListener(this::onChunkSpotted);
   }

   @Override
   protected void onDisable() {
      this.visibleChunks.invalidate();
      PlayerBypassLightTracker.setSpottedListener(null);
   }

   private void onChunkSpotted(int chunkX, int chunkZ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      int x = (chunkX << 4) + 8;
      int z = (chunkZ << 4) + 8;
      boolean showPos = this.showCoordinates.get();
      if (this.notificationsOn()) {
         ChallengeToast.addOrUpdate(
            new ItemStack(Items.PLAYER_HEAD),
            "Player Chunk Detected",
            showPos ? Text.literal("at " + x + ", " + z) : Text.empty(),
            this.sound.get()
         );
      }

      if (this.disconnect.get()) {
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         if (connection != null) {
            ModuleChat.send(this, ModuleChat.body().text("Auto Disconnect ").warn("triggered"));
            connection.getConnection().disconnect(Text.literal("[Player Bypass] Base detected" + (showPos ? " at " + x + ", " + z : "")));
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         Long2ObjectMap<ChunkLightSnapshot> snapshots = PlayerBypassLightTracker.snapshots();
         if (!snapshots.isEmpty()) {
            int centerX = mc.player.getBlockPos().getX() >> 4;
            int centerZ = mc.player.getBlockPos().getZ() >> 4;
            LongOpenHashSet bases;
            LongOpenHashSet suspects;
            boolean changed;
            if (PerformanceModule.optimizationsEnabled()) {
               changed = this.visibleChunks
                  .update(
                     PlayerBypassLightTracker.visibilityRevision(),
                     centerX,
                     centerZ,
                     snapshots,
                     PlayerBypassLightTracker::isFlagged,
                     PlayerBypassLightTracker::isSuspect
                  );
               bases = this.visibleChunks.bases();
               suspects = this.visibleChunks.suspects();
            } else {
               this.visibleChunks.invalidate();
               changed = true;
               bases = new LongOpenHashSet();
               suspects = new LongOpenHashSet();
               ObjectIterator base = snapshots.long2ObjectEntrySet().iterator();

               while (base.hasNext()) {
                  Entry<ChunkLightSnapshot> entry = (Entry<ChunkLightSnapshot>)base.next();
                  long key = entry.getLongKey();
                  if (Math.abs(ChunkPos.getPackedX(key) - centerX) <= 16
                     && Math.abs(ChunkPos.getPackedZ(key) - centerZ) <= 16
                     && PlayerBypassLightTracker.isFlagged(key, (ChunkLightSnapshot)entry.getValue())) {
                     (PlayerBypassLightTracker.isSuspect(key) ? suspects : bases).add(key);
                  }
               }
            }

            if (!bases.isEmpty() || !suspects.isEmpty()) {
               int base = -2516480;
               int suspect = -5766;
               if (changed) {
                  spread(bases, this.baseWeight);
                  spread(suspects, this.suspectWeight);
                  this.zoneDirty = true;
               }

               FoglessLines lines = FoglessLines.forFrame(event.partialTick);
               this.drawZones(lines, base, suspect);
               if (this.tracers.get()) {
                  this.chunkAreas.drawTracers(bases, 64.0, ColorHelper.fullAlpha(base));
                  this.suspectAreas.drawTracers(suspects, 64.0, ColorHelper.fullAlpha(suspect));
               }
            }
         }
      }
   }

   private static void spread(LongOpenHashSet sources, Long2FloatOpenHashMap into) {
      into.clear();
      LongIterator var2 = sources.iterator();

      while (var2.hasNext()) {
         long key = (Long)var2.next();
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);

         for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
               float weight = falloff(Math.max(Math.abs(dx), Math.abs(dz)));
               long area = ChunkPos.toLong(cx + dx, cz + dz);
               if (weight > into.get(area)) {
                  into.put(area, weight);
               }
            }
         }
      }
   }

   private static float falloff(int ring) {
      float t = ring / 6.0F;
      return 1.0F - t * t * (3.0F - 2.0F * t);
   }

   private void drawZones(FoglessLines lines, int base, int suspect) {
      if (this.zoneDirty) {
         this.zone.clear();
         this.zone.addAll(this.baseWeight.keySet());
         this.zone.addAll(this.suspectWeight.keySet());
         this.zoneDirty = false;
      }

      float maxAlpha = Math.max(10, Math.min(255, this.opacity.getInt()));
      LongIterator var5 = this.zone.iterator();

      while (var5.hasNext()) {
         long key = (Long)var5.next();
         float b = this.baseWeight.get(key);
         float s = this.suspectWeight.get(key);
         float mix = b * b / (b * b + s * s);
         int color = ColorHelper.lerp(mix, suspect, base);
         float strength = 0.25F + 0.75F * Math.max(b, s);
         int fill = ColorHelper.withAlpha(Math.round(maxAlpha * 0.45F * strength * ColorHelper.getAlphaFloat(color)), color);
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         double x0 = cx << 4;
         double z0 = cz << 4;
         double x1 = x0 + 16.0;
         double z1 = z0 + 16.0;
         GizmoDrawing.box(new Box(x0, 64.0, z0, x1, 64.0, z1), DrawStyle.filled(fill)).ignoreOcclusion();
         int edge = ColorHelper.withAlpha(Math.round(maxAlpha * ColorHelper.getAlphaFloat(color)), ColorHelper.scaleRgb(color, 0.8F));
         if (!this.zone.contains(ChunkPos.toLong(cx, cz - 1))) {
            edge(lines, x0, z0, x1, z0, edge);
         }

         if (!this.zone.contains(ChunkPos.toLong(cx, cz + 1))) {
            edge(lines, x0, z1, x1, z1, edge);
         }

         if (!this.zone.contains(ChunkPos.toLong(cx - 1, cz))) {
            edge(lines, x0, z0, x0, z1, edge);
         }

         if (!this.zone.contains(ChunkPos.toLong(cx + 1, cz))) {
            edge(lines, x1, z0, x1, z1, edge);
         }
      }
   }

   private static void edge(FoglessLines lines, double ax, double az, double bx, double bz, int color) {
      lines.line(new Vec3d(ax, 64.0, az), new Vec3d(bx, 64.0, bz), color, 1.5F);
   }
}
