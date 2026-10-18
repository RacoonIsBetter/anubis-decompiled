package net.anubis.client.gui.mainmenu;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.anubis.client.gui.ImageDownsampler;
import net.anubis.client.gui.ThemeImages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class MenuTextures {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Map<String, ThemeImages.Texture> TEXTURES = new HashMap<>();
   private static boolean optimized;

   private static void changeProfile() {
      TEXTURES.values().forEach(ThemeImages.get()::retireTexture);
      TEXTURES.clear();
      optimized = ThemeImages.get().optimizedImages();
   }

   private MenuTextures() {
   }

   static ThemeImages.Texture original(String resource) {
      return cached(resource, resource, UnaryOperator.identity());
   }

   static ThemeImages.Texture mask(String resource) {
      return cached(resource + "#mask", resource, p -> {
         int[] out = new int[p.argb().length];

         for (int i = 0; i < out.length; i++) {
            out[i] = p.argb()[i] & 0xFF000000 | 16777215;
         }

         return new MenuTextures.Pixels(p.width(), p.height(), out);
      });
   }

   static ThemeImages.Texture blurred(String resource, int radius) {
      return cached(resource + "#blur" + radius, resource, p -> blur(p, radius));
   }

   private static ThemeImages.Texture cached(String key, String resource, UnaryOperator<MenuTextures.Pixels> variant) {
      boolean next = ThemeImages.get().optimizedImages();
      if (optimized != next) {
         changeProfile();
      }

      if (TEXTURES.containsKey(key)) {
         return TEXTURES.get(key);
      }

      ThemeImages.Texture texture = null;

      try (InputStream in = MenuTextures.class.getResourceAsStream(resource)) {
         if (in == null) {
            LOGGER.warn("[Anubis] Missing menu image {}", resource);
         } else {
            MenuTextures.Pixels p = decode(in.readAllBytes(), resource);
            if (p != null) {
               p = variant.apply(p);
               texture = upload(p.argb(), p.width(), p.height());
            }
         }
      } catch (Exception e) {
         LOGGER.warn("[Anubis] Failed to load menu image {}", key, e);
      }

      TEXTURES.put(key, texture);
      return texture;
   }

   private static MenuTextures.Pixels blur(MenuTextures.Pixels p, int radius) {
      int pad = radius * 2;
      int w = p.width() + pad * 2;
      int h = p.height() + pad * 2;
      float[] alpha = new float[w * h];

      for (int y = 0; y < p.height(); y++) {
         for (int x = 0; x < p.width(); x++) {
            alpha[(y + pad) * w + x + pad] = (p.argb()[y * p.width() + x] >>> 24) / 255.0F;
         }
      }

      for (int pass = 0; pass < 3; pass++) {
         alpha = box(alpha, w, h, radius, true);
         alpha = box(alpha, w, h, radius, false);
      }

      int[] out = new int[w * h];

      for (int i = 0; i < out.length; i++) {
         out[i] = Math.round(Math.min(1.0F, alpha[i]) * 255.0F) << 24 | 16777215;
      }

      return new MenuTextures.Pixels(w, h, out);
   }

   private static float[] box(float[] in, int w, int h, int radius, boolean across) {
      if (optimized) {
         return slidingBox(in, w, h, radius, across);
      }

      float[] out = new float[in.length];
      float norm = 1.0F / (radius * 2 + 1);

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float sum = 0.0F;

            for (int k = -radius; k <= radius; k++) {
               int sx = across ? x + k : x;
               int sy = across ? y : y + k;
               if (sx >= 0 && sy >= 0 && sx < w && sy < h) {
                  sum += in[sy * w + sx];
               }
            }

            out[y * w + x] = sum * norm;
         }
      }

      return out;
   }

   private static float[] slidingBox(float[] in, int w, int h, int radius, boolean across) {
      float[] out = new float[in.length];
      int length = across ? w : h;
      int rows = across ? h : w;
      float norm = 1.0F / (radius * 2 + 1);

      for (int row = 0; row < rows; row++) {
         double sum = 0.0;

         for (int k = 0; k <= Math.min(radius, length - 1); k++) {
            sum += in[across ? row * w + k : k * w + row];
         }

         for (int p = 0; p < length; p++) {
            out[across ? row * w + p : p * w + row] = (float)sum * norm;
            int left = p - radius;
            int right = p + radius + 1;
            if (left >= 0) {
               sum -= in[across ? row * w + left : left * w + row];
            }

            if (right < length) {
               sum += in[across ? row * w + right : right * w + row];
            }
         }
      }

      return out;
   }

   private static MenuTextures.Pixels decode(byte[] bytes, String name) {
      ByteBuffer file = null;
      ByteBuffer rgba = null;
      ByteBuffer reduced = null;

      try {
         MemoryStack stack = MemoryStack.stackPush();

         Object var25;
         label192: {
            MenuTextures.Pixels var27;
            try {
               file = MemoryUtil.memAlloc(bytes.length);
               file.put(bytes).flip();
               IntBuffer w = stack.mallocInt(1);
               IntBuffer h = stack.mallocInt(1);
               IntBuffer channels = stack.mallocInt(1);
               rgba = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
               if (rgba == null) {
                  LOGGER.warn("[Anubis] Can't decode {}: {}", name, STBImage.stbi_failure_reason());
                  var25 = null;
                  break label192;
               }

               int width = w.get(0);
               int height = h.get(0);
               if (optimized) {
                  int[] size = ImageDownsampler.dimensions(width, height, 1536);
                  if (width != size[0] || height != size[1]) {
                     reduced = MemoryUtil.memAlloc(size[0] * size[1] * 4);
                     ImageDownsampler.rgba(rgba, width, height, reduced, size[0], size[1]);
                     width = size[0];
                     height = size[1];
                  }
               }

               ByteBuffer pixels = reduced == null ? rgba : reduced;
               int[] argb = new int[width * height];

               for (int i = 0; i < argb.length; i++) {
                  int r = pixels.get(i * 4) & 255;
                  int g = pixels.get(i * 4 + 1) & 255;
                  int b = pixels.get(i * 4 + 2) & 255;
                  int a = pixels.get(i * 4 + 3) & 255;
                  argb[i] = a << 24 | r << 16 | g << 8 | b;
               }

               var27 = new MenuTextures.Pixels(width, height, argb);
            } catch (Throwable var23) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var22) {
                     var23.addSuppressed(var22);
                  }
               }

               throw var23;
            }

            if (stack != null) {
               stack.close();
            }

            return var27;
         }

         if (stack != null) {
            stack.close();
         }

         return (MenuTextures.Pixels)var25;
      } finally {
         if (reduced != null) {
            MemoryUtil.memFree(reduced);
         }

         if (rgba != null) {
            STBImage.stbi_image_free(rgba);
         }

         if (file != null) {
            MemoryUtil.memFree(file);
         }
      }
   }

   private static ThemeImages.Texture upload(int[] argb, int width, int height) {
      ByteBuffer buffer = MemoryUtil.memAlloc(argb.length * 4);

      try {
         for (int c : argb) {
            buffer.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c).put((byte)(c >>> 24));
         }

         buffer.flip();
         return ThemeImages.fromPixels(buffer, width, height, false);
      } finally {
         MemoryUtil.memFree(buffer);
      }
   }

   static {
      ThemeImages.get().onImageProfileChange(MenuTextures::changeProfile);
   }

   @Environment(EnvType.CLIENT)
   private record Pixels(int width, int height, int[] argb) {
   }
}
