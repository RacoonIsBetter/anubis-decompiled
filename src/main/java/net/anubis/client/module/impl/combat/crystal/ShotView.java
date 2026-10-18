package net.anubis.client.module.impl.combat.crystal;

import imgui.ImDrawList;
import java.util.Locale;
import net.anubis.client.gui.WorldLabel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class ShotView {
   private static final long SLIDE_MS = 120L;
   private static final long HOLD_MS = 150L;
   private static final long FADE_MS = 350L;
   private static final float FILL_ALPHA = 0.22F;
   private static final float LINE_WIDTH = 1.5F;
   private static final double LABEL_RISE = 0.25;
   private Box from;
   private Box to;
   private long movedAt;
   private long shownAt;
   private int color;
   private float damage;
   private float selfDamage;

   public void show(Box box, int color, float damage, float selfDamage) {
      long now = System.currentTimeMillis();
      if (!this.showing(now)) {
         this.from = box;
         this.movedAt = now;
      } else if (!box.equals(this.to)) {
         this.from = this.box(now);
         this.movedAt = now;
      }

      this.to = box;
      this.shownAt = now;
      this.color = color;
      this.damage = damage;
      this.selfDamage = selfDamage;
   }

   public void clear() {
      this.to = null;
   }

   public boolean showing() {
      return this.showing(System.currentTimeMillis());
   }

   public void draw() {
      long now = System.currentTimeMillis();
      if (this.showing(now)) {
         float alpha = this.alpha(now);
         GizmoDrawing.box(this.box(now), DrawStyle.filledAndStroked(fade(this.color, alpha), 1.5F, fade(this.color, alpha * 0.22F))).ignoreOcclusion();
      }
   }

   public void label(ImDrawList dl, float screenW, float screenH) {
      long now = System.currentTimeMillis();
      if (this.showing(now)) {
         Box box = this.box(now);
         Vec3d top = new Vec3d(box.getCenter().x, box.maxY + 0.25, box.getCenter().z);
         float[] screen = WorldLabel.project(MinecraftClient.getInstance(), top, screenW, screenH);
         if (screen != null) {
            WorldLabel.drawText(
               dl,
               screen[0],
               screen[1],
               String.format(Locale.ROOT, "%.1f", this.damage),
               String.format(Locale.ROOT, "self %.1f", this.selfDamage),
               this.alpha(now)
            );
         }
      }
   }

   private boolean showing(long now) {
      return this.to != null && this.alpha(now) > 0.0F;
   }

   private float alpha(long now) {
      long idle = now - this.shownAt - 150L;
      return idle <= 0L ? 1.0F : Math.max(0.0F, 1.0F - (float)idle / 350.0F);
   }

   private Box box(long now) {
      float t = Math.min(1.0F, (float)(now - this.movedAt) / 120.0F);
      float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
      return new Box(
         this.from.getMinPos().lerp(this.to.getMinPos(), eased), this.from.getMaxPos().lerp(this.to.getMaxPos(), eased)
      );
   }

   private static int fade(int argb, float alpha) {
      return Math.round((argb >>> 24) * alpha) << 24 | argb & 16777215;
   }
}
