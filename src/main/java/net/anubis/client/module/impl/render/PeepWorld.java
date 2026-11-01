package net.anubis.client.module.impl.render;

import net.anubis.client.gui.ThemeTint;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class PeepWorld {
   private static final float PINK_R = 1.0F;
   private static final float PINK_G = 0.30980393F;
   private static final float PINK_B = 0.6901961F;
   private static final float PINK_LUMA = luma(1.0F, 0.30980393F, 0.6901961F);
   private static final long HORIZON_STALE_NANOS = 250000000L;
   private static volatile int horizon;
   private static volatile float horizonShare;
   private static volatile long horizonAt;

   private PeepWorld() {
   }

   static void horizon(int rgb, float share) {
      horizon = rgb;
      horizonShare = share;
      horizonAt = System.nanoTime();
   }

   public static void fog(Vector4f color) {
      float share = horizonShare;
      if (share > 0.0F && System.nanoTime() - horizonAt > 250000000L) {
         share = 0.0F;
      }

      fog(color, horizon, share);
   }

   private static void fog(Vector4f color, int horizon, float share) {
      float level = ThemeTint.world();
      if (level > 0.0F) {
         float scale = luma(color.x, color.y, color.z) / PINK_LUMA;
         color.x = color.x + (Math.min(1.0F, 1.0F * scale) - color.x) * level;
         color.y = color.y + (Math.min(1.0F, 0.30980393F * scale) - color.y) * level;
         color.z = color.z + (Math.min(1.0F, 0.6901961F * scale) - color.z) * level;
      }

      if (share > 0.0F) {
         color.x = color.x + ((horizon >> 16 & 0xFF) / 255.0F - color.x) * share;
         color.y = color.y + ((horizon >> 8 & 0xFF) / 255.0F - color.y) * share;
         color.z = color.z + ((horizon & 0xFF) / 255.0F - color.z) * share;
      }
   }

   static int fog(int rgb) {
      return fog(rgb, 0, 0.0F);
   }

   static int fog(int rgb, int horizon, float share) {
      Vector4f color = new Vector4f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
      fog(color, horizon, share);
      return channel(color.x) << 16 | channel(color.y) << 8 | channel(color.z);
   }

   private static float luma(float r, float g, float b) {
      return 0.2126F * r + 0.7152F * g + 0.0722F * b;
   }

   private static int channel(float value) {
      return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
   }
}
