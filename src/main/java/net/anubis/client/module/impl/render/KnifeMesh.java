package net.anubis.client.module.impl.render;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack.Entry;

@Environment(EnvType.CLIENT)
final class KnifeMesh {
   private static final float SHINE_WIDTH = 2.6F;
   private static final float SHINE_BOOST = 1.5F;
   private static final float WIPE_FRONT = 1.5F;
   private static final float GLOW_ALPHA = 0.85F;
   private static final float GLINT_UV = 0.09F;
   private final float[] pos;
   private final float[] normal;
   private final byte[] material;
   private final float[] tone;
   private final int quads;
   private final float shineFrom;
   private final float shineTo;

   private KnifeMesh(float[] pos, float[] normal, byte[] material, float[] tone, int quads, float shineFrom, float shineTo) {
      this.pos = pos;
      this.normal = normal;
      this.material = material;
      this.tone = tone;
      this.quads = quads;
      this.shineFrom = shineFrom;
      this.shineTo = shineTo;
   }

   void emit(Entry pose, VertexConsumer out, int[] palette, int light, KnifeMesh.Shine shine) {
      boolean shining = shine.gain() > 0.004F;

      for (int q = 0; q < this.quads; q++) {
         int mat = this.material[q];
         int argb = palette[mat];
         boolean lit = shining && (shine.tail() > 0.0F ? KnifeMaterial.blade(mat) : KnifeMaterial.metallic(mat));
         float nx = this.normal[q * 3];
         float ny = this.normal[q * 3 + 1];
         float nz = this.normal[q * 3 + 2];

         for (int v = 0; v < 4; v++) {
            int p = q * 12 + v * 3;
            float x = this.pos[p];
            float y = this.pos[p + 1];
            float z = this.pos[p + 2];
            float bright = this.tone[q];
            if (lit) {
               bright += shine.gain() * 1.5F * this.profile(x, shine);
            }

            out.vertex(pose, x, y, z)
               .color(tint(argb, bright))
               .texture((x + z) * 0.09F, y * 0.09F)
               .overlay(OverlayTexture.DEFAULT_UV)
               .light(light)
               .normal(pose, nx, ny, nz);
         }
      }
   }

   void emitGlow(Entry pose, VertexConsumer out, int rgb, KnifeMesh.Shine shine) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      float[] alpha = new float[4];

      for (int q = 0; q < this.quads; q++) {
         if (KnifeMaterial.blade(this.material[q])) {
            float most = 0.0F;

            for (int v = 0; v < 4; v++) {
               alpha[v] = Math.min(1.0F, shine.gain() * 0.85F * this.profile(this.pos[q * 12 + v * 3], shine));
               most = Math.max(most, alpha[v]);
            }

            if (!(most < 0.01F)) {
               float nx = this.normal[q * 3];
               float ny = this.normal[q * 3 + 1];
               float nz = this.normal[q * 3 + 2];

               for (int v = 0; v < 4; v++) {
                  int p = q * 12 + v * 3;
                  out.vertex(pose, this.pos[p], this.pos[p + 1], this.pos[p + 2])
                     .color(r, g, b, Math.round(alpha[v] * 255.0F))
                     .texture(0.5F, 0.5F)
                     .overlay(OverlayTexture.DEFAULT_UV)
                     .light(15728880)
                     .normal(pose, nx, ny, nz);
               }
            }
         }
      }
   }

   private float profile(float x, KnifeMesh.Shine shine) {
      float d = x - (this.shineFrom + (this.shineTo - this.shineFrom) * shine.at());
      if (shine.tail() <= 0.0F) {
         float k = d / 2.6F;
         return (float)Math.exp(-k * k);
      } else if (d > 0.0F) {
         float k = d / 1.5F;
         return (float)Math.exp(-k * k);
      } else {
         return (float)Math.exp(d / shine.tail());
      }
   }

   private static int tint(int argb, float k) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      if (k <= 1.0F) {
         r = (int)(r * k);
         g = (int)(g * k);
         b = (int)(b * k);
      } else {
         float w = Math.min(1.0F, k - 1.0F);
         r += (int)((255 - r) * w);
         g += (int)((255 - g) * w);
         b += (int)((255 - b) * w);
      }

      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   @Environment(EnvType.CLIENT)
   record Shine(float at, float gain, float tail) {
      static final KnifeMesh.Shine NONE = new KnifeMesh.Shine(0.0F, 0.0F, 0.0F);
   }

   @Environment(EnvType.CLIENT)
   static final class Voxels {
      private static final int SX = 128;
      private static final int SY = 64;
      private static final int SZ = 16;
      private static final int OX = 64;
      private static final int OY = 32;
      private static final int OZ = 8;
      private static final int[][] FACES = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
      private final byte[] cells = new byte[131072];
      private final float[] shade = new float[131072];

      void set(int x, int y, int z, int material) {
         this.set(x, y, z, material, 1.0F);
      }

      void set(int x, int y, int z, int material, float tone) {
         int i = index(x, y, z);
         this.cells[i] = (byte)(material + 1);
         this.shade[i] = tone;
      }

      void clear(int x, int y, int z) {
         this.cells[index(x, y, z)] = 0;
      }

      boolean filled(int x, int y, int z) {
         int ix = x + 64;
         int iy = y + 32;
         int iz = z + 8;
         return ix >= 0 && iy >= 0 && iz >= 0 && ix < 128 && iy < 64 && iz < 16 ? this.cells[(ix * 64 + iy) * 16 + iz] != 0 : false;
      }

      KnifeMesh mesh(float shineFrom, float shineTo) {
         int capacity = 256;
         float[] pos = new float[capacity * 12];
         float[] normal = new float[capacity * 3];
         byte[] material = new byte[capacity];
         float[] tone = new float[capacity];
         int quads = 0;

         for (int ix = 0; ix < 128; ix++) {
            for (int iy = 0; iy < 64; iy++) {
               for (int iz = 0; iz < 16; iz++) {
                  int cell = (ix * 64 + iy) * 16 + iz;
                  if (this.cells[cell] != 0) {
                     int x = ix - 64;
                     int y = iy - 32;
                     int z = iz - 8;

                     for (int[] f : FACES) {
                        if (!this.filled(x + f[0], y + f[1], z + f[2])) {
                           if (quads == capacity) {
                              capacity *= 2;
                              pos = Arrays.copyOf(pos, capacity * 12);
                              normal = Arrays.copyOf(normal, capacity * 3);
                              material = Arrays.copyOf(material, capacity);
                              tone = Arrays.copyOf(tone, capacity);
                           }

                           face(pos, quads * 12, x, y, z, f);
                           normal[quads * 3] = f[0];
                           normal[quads * 3 + 1] = f[1];
                           normal[quads * 3 + 2] = f[2];
                           material[quads] = (byte)(this.cells[cell] - 1);
                           tone[quads] = this.shade[cell];
                           quads++;
                        }
                     }
                  }
               }
            }
         }

         return new KnifeMesh(
            Arrays.copyOf(pos, quads * 12),
            Arrays.copyOf(normal, quads * 3),
            Arrays.copyOf(material, quads),
            Arrays.copyOf(tone, quads),
            quads,
            shineFrom,
            shineTo
         );
      }

      private static void face(float[] out, int at, int x, int y, int z, int[] f) {
         float x0 = x;
         float x1 = x + 1;
         float y0 = y;
         float y1 = y + 1;
         float z0 = z;
         float z1 = z + 1;
         float[] c;
         if (f[0] == 1) {
            c = new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1};
         } else if (f[0] == -1) {
            c = new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0};
         } else if (f[1] == 1) {
            c = new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0};
         } else if (f[1] == -1) {
            c = new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
         } else if (f[2] == 1) {
            c = new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1};
         } else {
            c = new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0};
         }

         System.arraycopy(c, 0, out, at, 12);
      }

      private static int index(int x, int y, int z) {
         return ((x + 64) * 64 + y + 32) * 16 + z + 8;
      }
   }
}
