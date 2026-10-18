package net.anubis.client.gui;

import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ImageDownsampler {
   private ImageDownsampler() {
   }

   public static int[] dimensions(int width, int height, int maxSide) {
      if (width > 0 && height > 0 && maxSide > 0) {
         double scale = Math.min(1.0, (double)maxSide / Math.max(width, height));
         return new int[]{Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale))};
      } else {
         throw new IllegalArgumentException("Image dimensions");
      }
   }

   public static void rgba(ByteBuffer source, int width, int height, ByteBuffer target, int outWidth, int outHeight) {
      if (outWidth > 0 && outHeight > 0 && outWidth <= width && outHeight <= height) {
         double stepX = (double)width / outWidth;
         double stepY = (double)height / outHeight;

         for (int y = 0; y < outHeight; y++) {
            double top = y * stepY;
            double bottom = (y + 1) * stepY;

            for (int x = 0; x < outWidth; x++) {
               double left = x * stepX;
               double right = (x + 1) * stepX;
               double a = 0.0;
               double r = 0.0;
               double g = 0.0;
               double b = 0.0;

               for (int sy = (int)top; sy < Math.min(height, (int)Math.ceil(bottom)); sy++) {
                  double wy = Math.min(bottom, sy + 1.0) - Math.max(top, sy);

                  for (int sx = (int)left; sx < Math.min(width, (int)Math.ceil(right)); sx++) {
                     double weight = wy * (Math.min(right, sx + 1.0) - Math.max(left, sx));
                     int i = (sy * width + sx) * 4;
                     double alpha = (source.get(i + 3) & 0xFF) * weight;
                     a += alpha;
                     r += (source.get(i) & 0xFF) * alpha;
                     g += (source.get(i + 1) & 0xFF) * alpha;
                     b += (source.get(i + 2) & 0xFF) * alpha;
                  }
               }

               target.put((byte)(a == 0.0 ? 0L : Math.round(r / a)));
               target.put((byte)(a == 0.0 ? 0L : Math.round(g / a)));
               target.put((byte)(a == 0.0 ? 0L : Math.round(b / a)));
               target.put((byte)Math.round(a / (stepX * stepY)));
            }
         }

         target.flip();
      } else {
         throw new IllegalArgumentException("Only image reduction is supported");
      }
   }
}
