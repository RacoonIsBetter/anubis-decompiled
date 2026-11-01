package net.anubis.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ThemeTint {
   private static final int PINK = 16732080;
   private static final int DUSK = 2754594;
   private static final int WARM_ROSE = 16751309;
   private static final float SURFACE_SHARE = 0.45F;
   private static final float WORLD_SHARE = 0.3F;
   private static volatile float pink;

   private ThemeTint() {
   }

   public static void pink(float level) {
      pink = Math.max(0.0F, Math.min(1.0F, level));
   }

   public static float pink() {
      return pink;
   }

   public static int accent(int argb) {
      return mix(argb, 16732080, pink);
   }

   public static int surface(int argb) {
      return mix(argb, 2754594, pink * 0.45F);
   }

   public static float world() {
      float t = pink;
      return t * t * (3.0F - 2.0F * t) * 0.3F;
   }

   public static int world(int argb) {
      float t = world();
      if (t <= 0.0F) {
         return argb;
      }

      float warm = warmth(argb);
      int target = warm > 0.0F ? mix(16732080, 16751309, warm) & 16777215 : 16732080;
      return mix(argb, target, t * (1.0F - 0.4F * warm));
   }

   private static float warmth(int argb) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      if (max != 0 && !(max - min < 0.25F * max) && max != b) {
         float hue = max == r ? 60.0F * (g - b) / (max - min) : 60.0F * ((float)(b - r) / (max - min) + 2.0F);
         if (hue < 0.0F) {
            hue += 360.0F;
         }

         return Math.min(ramp(hue, 10.0F, 20.0F), 1.0F - ramp(hue, 60.0F, 70.0F));
      } else {
         return 0.0F;
      }
   }

   private static float ramp(float x, float from, float to) {
      return Math.max(0.0F, Math.min(1.0F, (x - from) / (to - from)));
   }

   private static int mix(int argb, int rgb, float t) {
      if (t <= 0.0F) {
         return argb;
      }

      int r = lerp(argb >> 16 & 0xFF, rgb >> 16 & 0xFF, t);
      int g = lerp(argb >> 8 & 0xFF, rgb >> 8 & 0xFF, t);
      int b = lerp(argb & 0xFF, rgb & 0xFF, t);
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int lerp(int a, int b, float t) {
      return Math.round(a + (b - a) * t);
   }
}
