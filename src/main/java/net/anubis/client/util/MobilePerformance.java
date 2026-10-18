package net.anubis.client.util;

import imgui.AnubisAndroidNative;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MobilePerformance {
   private static final boolean ANDROID = AnubisAndroidNative.isAndroidRuntime();

   private MobilePerformance() {
   }

   public static boolean isAndroid() {
      return ANDROID;
   }

   public static boolean active() {
      return ANDROID && PerformanceModule.mobileEffectsEnabled();
   }

   public static int segments(int original, int minimum) {
      return reduce(original, minimum);
   }

   public static int particles(int original, int minimum) {
      return reduce(original, minimum);
   }

   public static int trailSamples(int original, int minimum) {
      return reduce(original, minimum);
   }

   public static int uiSegments(int original, float radiusPixels) {
      if (active() && original > 12 && Float.isFinite(radiusPixels) && !(radiusPixels <= 0.0F)) {
         double angle = Math.acos(Math.max(-1.0, 1.0 - 0.35 / radiusPixels));
         if (angle <= 0.0) {
            return original;
         }

         int needed = (int)Math.ceil(Math.PI / angle);
         return needed >= original ? original : Math.min(original, Math.max(12, (needed + 3) / 4 * 4));
      } else {
         return original;
      }
   }

   private static int reduce(int original, int minimum) {
      return active() && original > 0 ? Math.min(original, Math.max(minimum, original / 2 + original % 2)) : original;
   }
}
