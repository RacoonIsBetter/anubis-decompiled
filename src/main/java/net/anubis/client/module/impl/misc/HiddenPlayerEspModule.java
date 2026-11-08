package net.anubis.client.module.impl.misc;

import com.example.client.mixin.AccessorEntityS2CPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.render.BaseFinderModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Anti-xray/anti-ESP plugins hide players (and mobs) underground by never spawning them on the
 * client, but servers still leak where they are: some keep sending their moves, and ChunkVeil-style
 * plugins send a snapshot of every hidden entity's position whenever your tracking refreshes (you
 * join, relog or get teleported). Every entity with a leaked position but no spawn is shown here;
 * one moving at player speed is marked as a hidden player, and Base Finder gets told about it.
 */
@Environment(EnvType.CLIENT)
public final class HiddenPlayerEspModule extends ClientModule {
   /** Players walk 4.3 and sprint 5.6 blocks/s; underground mobs mostly stay under 2.5. */
   private static final double PLAYER_SPEED = 3.5;
   /** Snapshot positions stay on screen this long; a moving ghost refreshes itself. */
   private static final long FORGET_MS = 60_000L;
   /** Many ghosts arriving within this window is a snapshot (you joined, relogged or were teleported). */
   private static final long SNAPSHOT_WINDOW_MS = 1_000L;
   private static final int SNAPSHOT_MIN = 3;
   private static final long SPEED_WINDOW_MS = 6_000L;
   private static final long ALERT_COOLDOWN_MS = 2_000L;

   private final NumberSetting minSpeed = this.addSetting("min-speed", new NumberSetting("Player Speed (b/s)", (float)PLAYER_SPEED, 1.0F, 8.0F).step(0.5F));
   private final BooleanSetting showUnclassified = this.addSetting("show-unclassified", new BooleanSetting("Show Unclassified", true));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting feedBaseFinder = this.addSetting("base-finder", new BooleanSetting("Tell Base Finder", true));
   private final Int2ObjectOpenHashMap<HiddenPlayerEspModule.Ghost> ghosts = new Int2ObjectOpenHashMap<>();
   private long lastAlertAt;
   private long snapshotStart;
   private int snapshotCount;

   public HiddenPlayerEspModule() {
      super(
         "hidden-player-esp",
         "Hidden Player ESP",
         "Shows players the server hides underground, from the position updates it still sends about them.",
         ModuleCategory.BASE_RAID
      );
      this.usesNotifications();
   }

   @Override
   protected void onDisable() {
      this.ghosts.clear();
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (event.packet instanceof EntityPositionSyncS2CPacket sync) {
         if (level.getEntityById(sync.id()) == null) {
            HiddenPlayerEspModule.Ghost ghost = this.ghosts.get(sync.id());
            if (ghost == null) {
               ghost = this.ghost(sync.id());
               this.countSnapshot(now);
            }

            ghost.seen(sync.values().position(), now);
         }
      } else if (event.packet instanceof EntityS2CPacket move && move.isPositionChanged()) {
         int id = ((AccessorEntityS2CPacket)move).anubis$getId();
         HiddenPlayerEspModule.Ghost ghost = this.ghosts.get(id);
         // Relative moves only help once a sync told us where the entity is.
         if (ghost != null && ghost.pos != null && level.getEntityById(id) == null) {
            ghost.seen(ghost.pos.add(move.getDeltaX() / 4096.0, move.getDeltaY() / 4096.0, move.getDeltaZ() / 4096.0), now);
         }
      } else if (event.packet instanceof EntitySpawnS2CPacket spawn) {
         // Once the server spawns it for us it is no longer hidden; normal ESP takes over.
         this.ghosts.remove(spawn.getEntityId());
      } else if (event.packet instanceof EntitiesDestroyS2CPacket destroy) {
         destroy.getEntityIds().forEach(this.ghosts::remove);
      }
   }

   /** New hidden positions arriving together: the server refreshed what you track and leaked everything hidden. */
   private void countSnapshot(long now) {
      if (now - this.snapshotStart > SNAPSHOT_WINDOW_MS) {
         this.snapshotStart = now;
         this.snapshotCount = 0;
      }

      if (++this.snapshotCount == SNAPSHOT_MIN) {
         ModuleChat.send(this, ModuleChat.body().text("The server just sent positions of ").warn("hidden entities").text(" (players or mobs) around you; relogging refreshes them."));
      }
   }

   private HiddenPlayerEspModule.Ghost ghost(int id) {
      return this.ghosts.computeIfAbsent(id, k -> new HiddenPlayerEspModule.Ghost());
   }

   @EventHandler
   public void onTick(TickEvent event) {
      long now = System.currentTimeMillis();
      this.ghosts.values().removeIf(g -> now - g.lastAt > FORGET_MS);

      for (Int2ObjectOpenHashMap.Entry<HiddenPlayerEspModule.Ghost> entry : this.ghosts.int2ObjectEntrySet()) {
         HiddenPlayerEspModule.Ghost ghost = entry.getValue();
         if (!ghost.player && this.isPlayer(ghost)) {
            ghost.player = true;
            this.announce(entry.getIntKey(), ghost, now);
         }
      }
   }

   private boolean isPlayer(HiddenPlayerEspModule.Ghost ghost) {
      return ghost.pos != null && ghost.topSpeed() >= this.minSpeed.get();
   }

   private void announce(int id, HiddenPlayerEspModule.Ghost ghost, long now) {
      BlockPos pos = BlockPos.ofFloored(ghost.pos);
      if (now - this.lastAlertAt >= ALERT_COOLDOWN_MS) {
         this.lastAlertAt = now;
         ModuleChat.send(
            this,
            ModuleChat.body().warn("Hidden player").text(String.format(" moving at %.1f b/s near ", ghost.topSpeed())).coords(pos.getX(), pos.getY(), pos.getZ())
         );
      }

      if (this.feedBaseFinder.get()) {
         BaseFinderModule finder = ModuleRegistry.get(BaseFinderModule.class);
         if (finder != null && finder.isEnabled()) {
            finder.reportHiddenPlayer(pos, String.format("hidden player moving at %.1f b/s", ghost.topSpeed()));
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.ghosts.isEmpty()) {
         return;
      }

      FoglessLines lines = FoglessLines.forFrame(event.partialTick);

      for (HiddenPlayerEspModule.Ghost ghost : this.ghosts.values()) {
         if (ghost.pos == null || !ghost.player && !this.showUnclassified.get()) {
            continue;
         }

         // Red: moving at player speed. Amber: a hidden player or mob whose position leaked (e.g. on relog).
         int color = ghost.player ? 0xFFFF3B3B : 0xFFFFB238;
         Vec3d p = ghost.pos;
         Box box = new Box(p.x - 0.3, p.y, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3);
         GizmoDrawing.box(box, DrawStyle.filled(ColorHelper.withAlpha(60, color))).ignoreOcclusion();
         Vec3d[] corners = {
            new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.minX, box.minY, box.maxZ)
         };

         for (int i = 0; i < 4; i++) {
            Vec3d a = corners[i];
            Vec3d b = corners[(i + 1) % 4];
            lines.line(a, b, color, 1.5F);
            lines.line(a.add(0.0, 1.8, 0.0), b.add(0.0, 1.8, 0.0), color, 1.5F);
            lines.line(a, a.add(0.0, 1.8, 0.0), color, 1.5F);
         }

         if (this.tracers.get() && ghost.player) {
            TracerEngine.draw(p.add(0.0, 0.9, 0.0), color);
         }
      }
   }

   /** One hidden entity: last position and recent samples to tell a player's speed from a mob's. */
   @Environment(EnvType.CLIENT)
   private static final class Ghost {
      final ArrayDeque<double[]> samples = new ArrayDeque<>();
      Vec3d pos;
      long lastAt;
      int packets;
      boolean player;

      void seen(Vec3d at, long now) {
         this.pos = at;
         this.lastAt = now;
         this.packets++;
         this.samples.addLast(new double[]{now, at.x, at.z});

         while (!this.samples.isEmpty() && now - this.samples.peekFirst()[0] > SPEED_WINDOW_MS) {
            this.samples.removeFirst();
         }
      }

      /** Horizontal blocks per second between the oldest and newest recent sample. */
      double topSpeed() {
         if (this.samples.size() < 2) {
            return 0.0;
         }

         double[] a = this.samples.peekFirst();
         double[] b = this.samples.peekLast();
         double seconds = (b[0] - a[0]) / 1000.0;
         return seconds < 0.5 ? 0.0 : Math.hypot(b[1] - a[1], b[2] - a[2]) / seconds;
      }
   }
}
