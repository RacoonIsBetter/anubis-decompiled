package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class SkyMips {
   private static final int MAX_LEVELS = 6;

   private SkyMips() {
   }

   static int size(int full, int level) {
      return Math.max(1, full >> level);
   }

   static int[][] chain(int[] argb, int width, int height) {
      int count = 1;

      while (count < 6 && Math.min(width, height) >> count >= 1) {
         count++;
      }

      int[][] levels = new int[count][];
      levels[0] = argb;

      for (int level = 1; level < count; level++) {
         int sw = size(width, level - 1);
         int sh = size(height, level - 1);
         int dw = size(width, level);
         int dh = size(height, level);
         int[] src = levels[level - 1];
         int[] dst = new int[dw * dh];

         for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
               float a = 0.0F;
               float r = 0.0F;
               float g = 0.0F;
               float b = 0.0F;
               float pr = 0.0F;
               float pg = 0.0F;
               float pb = 0.0F;

               for (int j = 0; j < 2; j++) {
                  int sy = Math.min(sh - 1, y * 2 + j);

                  for (int i = 0; i < 2; i++) {
                     int c = src[sy * sw + Math.min(sw - 1, x * 2 + i)];
                     float ca = (c >>> 24) / 255.0F;
                     a += ca;
                     r += ca * (c >> 16 & 0xFF);
                     g += ca * (c >> 8 & 0xFF);
                     b += ca * (c & 0xFF);
                     pr += c >> 16 & 0xFF;
                     pg += c >> 8 & 0xFF;
                     pb += c & 0xFF;
                  }
               }

               int alpha = Math.min(255, Math.round(a * 0.25F * 255.0F));
               if (a <= 1.0E-4F) {
                  a = 4.0F;
                  r = pr;
                  g = pg;
                  b = pb;
               }

               dst[y * dw + x] = alpha << 24
                  | Math.min(255, Math.round(r / a)) << 16
                  | Math.min(255, Math.round(g / a)) << 8
                  | Math.min(255, Math.round(b / a));
            }
         }

         levels[level] = dst;
      }

      return levels;
   }
}
