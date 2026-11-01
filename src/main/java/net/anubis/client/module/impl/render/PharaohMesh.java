package net.anubis.client.module.impl.render;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack.Entry;

@Environment(EnvType.CLIENT)
final class PharaohMesh {
   static final int GOLD = 0;
   static final int INLAY = 1;
   static final int GEM = 2;
   static final int BRONZE = 3;
   static final int MATERIALS = 5;
   static final int STYLE_NEMES = 0;
   static final int STYLE_KHEPRESH = 1;
   static final int STYLE_SUN_DISC = 2;
   private static final float PX = 0.0625F;
   private static final float TWIST = 0.12F;
   private static final float[] CORNER_U = new float[]{0.0F, 1.0F, 1.0F, 0.0F};
   private static final float[] CORNER_V = new float[]{0.0F, 0.0F, 1.0F, 1.0F};
   private static final float BAND_TOP = -6.6F;
   private static final float BAND_BOTTOM = -5.3F;
   private final float[] pos;
   private final float[] normal;
   private final float[] sweep;
   private final byte[] material;
   private final float[] tone;
   private final int quads;

   PharaohMesh(PharaohMesh.Builder b) {
      this.quads = b.quads;
      this.pos = Arrays.copyOf(b.pos, this.quads * 12);
      this.normal = Arrays.copyOf(b.normal, this.quads * 3);
      this.sweep = Arrays.copyOf(b.sweep, this.quads * 8);
      this.material = Arrays.copyOf(b.material, this.quads);
      this.tone = Arrays.copyOf(b.tone, this.quads);
   }

   boolean isEmpty() {
      return this.quads == 0;
   }

   void emit(Entry pose, VertexConsumer out, int light, PharaohMesh.Look look) {
      int[] palette = look.palette();
      float gain = look.sweepGain();
      boolean glint = gain > 0.004F;
      float sc = look.sweepCos();
      float ss = look.sweepSin();

      for (int q = 0; q < this.quads; q++) {
         int mat = this.material[q];
         int argb = palette[mat];
         float base = mat == 2 ? this.tone[q] * look.gemPulse() : this.tone[q];
         boolean metal = glint && (mat == 0 || mat == 3);
         int lit = mat == 2 && look.glow() ? 15728880 : light;
         int n = q * 3;
         float nx = this.normal[n];
         float ny = this.normal[n + 1];
         float nz = this.normal[n + 2];

         for (int v = 0; v < 4; v++) {
            int p = q * 12 + v * 3;
            float bright = base;
            if (metal) {
               int s = q * 8 + v * 2;
               float c = this.sweep[s] * sc + this.sweep[s + 1] * ss;
               if (c > 0.0F) {
                  c *= c;
                  c *= c;
                  c *= c;
                  bright += gain * c;
               }
            }

            out.vertex(pose, this.pos[p], this.pos[p + 1], this.pos[p + 2])
               .color(tint(argb, bright))
               .texture(CORNER_U[v], CORNER_V[v])
               .overlay(OverlayTexture.DEFAULT_UV)
               .light(lit)
               .normal(pose, nx, ny, nz);
         }
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

   static PharaohMesh head(int style, int stripes, boolean helmet) {
      PharaohMesh.Builder b = new PharaohMesh.Builder();
      float e = helmet ? 5.35F : 4.8F;
      float top = -4.0F - e;
      brow(b, e);
      switch (style) {
         case 1:
            khepresh(b, e, top);
            break;
         case 2:
            sunDisc(b, e, top);
            break;
         default:
            nemes(b, e, top, stripes);
      }

      uraeus(b, -(e + 0.45F));
      return new PharaohMesh(b);
   }

   static PharaohMesh body(int style, int stripes, boolean chest, boolean collar) {
      PharaohMesh.Builder b = new PharaohMesh.Builder();
      float lift = lift(chest);
      float front = -2.0F - lift;
      float back = 2.0F + lift;
      if (collar) {
         collar(b, front - 0.12F);
      }

      if (style == 0) {
         for (int s = -1; s <= 1; s += 2) {
            float z0 = front - 0.9F;
            float z1 = front - 0.35F;
            b.stripedSlab(-0.3F, s * 2.0F, s * 6.2F, z0, z1, 5.0F, s * 1.6F, s * 4.4F, z0, z1, stripes + 1, 1, 0);
            b.slab(5.0F, s * 1.75F, s * 4.25F, z0 + 0.05F, z1 - 0.05F, 5.7F, s * 2.1F, s * 3.9F, z0 + 0.1F, z1 - 0.1F, 3);
         }

         b.stripedSlab(-0.4F, -3.4F, 3.4F, back, back + 0.6F, 4.4F, -1.3F, 1.3F, back, back + 0.6F, stripes, 1, 0);
         b.box(-1.6F, 4.4F, back - 0.1F, 1.6F, 5.2F, back + 0.8F, 0);
         b.slab(5.2F, -1.1F, 1.1F, back, back + 0.55F, 6.4F, -0.4F, 0.4F, back + 0.1F, back + 0.45F, 3);
      }

      return new PharaohMesh(b);
   }

   private static float lift(boolean chest) {
      return chest ? 1.15F : 0.2F;
   }

   private static void brow(PharaohMesh.Builder b, float e) {
      float o = e + 0.45F;
      float i = e - 0.3F;
      b.box(-o, -6.6F, -o, o, -5.3F, -i, 0);
      b.box(-o, -6.6F, i, o, -5.3F, o, 0);
      b.box(-o, -6.6F, -i, -i, -5.3F, i, 0);
      b.box(i, -6.6F, -i, o, -5.3F, i, 0);

      for (int s = -1; s <= 1; s += 2) {
         for (int k = 0; k < 2; k++) {
            float x = s * (1.7F + 1.6F * k);
            b.box(x - 0.45F, -6.2999997F, -o - 0.12F, x + 0.45F, -5.6000004F, -o + 0.05F, 1);
         }
      }
   }

   private static void uraeus(PharaohMesh.Builder b, float zf) {
      b.slab(-5.3F, -0.5F, 0.5F, zf - 0.5F, zf, -7.8F, -0.4F, 0.4F, zf - 0.95F, zf - 0.35F, 0);
      b.slab(-7.6F, -0.8F, 0.8F, zf - 1.15F, zf - 0.55F, -9.6F, -1.15F, 1.15F, zf - 1.2F, zf - 0.6F, 0);
      b.slab(-9.6F, -1.15F, 1.15F, zf - 1.2F, zf - 0.6F, -10.1F, -0.5F, 0.5F, zf - 1.2F, zf - 0.65F, 0);
      b.box(-0.42F, -10.7F, zf - 1.75F, 0.42F, -10.0F, zf - 0.75F, 0);
      b.box(-0.4F, -9.1F, zf - 1.32F, 0.4F, -8.2F, zf - 1.1F, 2);
   }

   private static void nemes(PharaohMesh.Builder b, float e, float top, int stripes) {
      int hoodBands = Math.max(2, (stripes + 1) / 2);
      float hoodTop = top - 1.2F;

      for (int k = 0; k < hoodBands; k++) {
         float t0 = (float)k / hoodBands;
         float t1 = (float)(k + 1) / hoodBands;
         float y0 = lerp(-6.6F, hoodTop, t0);
         float y1 = lerp(-6.6F, hoodTop, t1);
         float x0 = hood(e, t0);
         float x1 = hood(e, t1);
         b.slab(y0, -x0, x0, -x0, x0, y1, -x1, x1, -x1, x1, (k & 1) == 0 ? 1 : 0);
      }

      float crown = hood(e, 1.0F);
      b.slab(hoodTop, -crown, crown, -crown, crown, hoodTop - 0.55F, -(crown - 0.9F), crown - 0.9F, -(crown - 0.9F), crown - 0.9F, 0);
      b.stripedSlab(-5.3F, -e, e, e - 0.5F, e + 0.3F, 0.8F, -(e + 1.1F), e + 1.1F, e - 0.5F, e + 0.55F, stripes, 1, 0);
      float[] upper = new float[12];
      float[] lower = new float[12];

      for (int s = -1; s <= 1; s += 2) {
         ring(upper, -5.3F, s * (e - 0.2F), s * (e + 0.45F), -2.6F, e + 0.2F);
         ring(lower, 0.4F, s * (e + 1.6F), s * (e + 2.3F), -3.4F, e + 0.9F);
         b.striped(upper, lower, stripes, 1, 0);
      }
   }

   private static float hood(float e, float t) {
      return e + 0.35F * (float)Math.sin(Math.PI * t * 0.8) - 0.9F * t * t * t;
   }

   private static void khepresh(PharaohMesh.Builder b, float e, float top) {
      int levels = 6;
      float crownTop = top - 5.2F;

      for (int j = 0; j < levels; j++) {
         float t0 = (float)j / levels;
         float t1 = (float)(j + 1) / levels;
         float y0 = lerp(-6.6F, crownTop, t0);
         float y1 = lerp(-6.6F, crownTop, t1);
         float x0 = crownWidth(e, t0);
         float x1 = crownWidth(e, t1);
         float f0 = crownFront(e, t0);
         float f1 = crownFront(e, t1);
         float k0 = crownBack(e, t0);
         float k1 = crownBack(e, t1);
         b.slab(y0, -x0, x0, f0, k0, y1, -x1, x1, f1, k1, 1);
         boolean wide = (j & 1) == 0;
         b.studs(-x0, y0, f0, x0, y0, f0, x1, y1, f1, -x1, y1, f1, wide, j < 2, 0.0F, (y0 + y1) * 0.5F, 0.0F);
         b.studs(x0, y0, k0, -x0, y0, k0, -x1, y1, k1, x1, y1, k1, wide, false, 0.0F, (y0 + y1) * 0.5F, 0.0F);
         b.studs(-x0, y0, k0, -x0, y0, f0, -x1, y1, f1, -x1, y1, k1, wide, false, 0.0F, (y0 + y1) * 0.5F, 0.0F);
         b.studs(x0, y0, f0, x0, y0, k0, x1, y1, k1, x1, y1, f1, wide, false, 0.0F, (y0 + y1) * 0.5F, 0.0F);
      }

      float xt = crownWidth(e, 1.0F);
      float ft = crownFront(e, 1.0F);
      float kt = crownBack(e, 1.0F);
      b.slab(crownTop, -xt, xt, ft, kt, crownTop - 0.6F, -(xt - 0.9F), xt - 0.9F, ft + 0.9F, kt - 0.9F, 1);

      for (int s = -1; s <= 1; s += 2) {
         b.slab(-5.7000003F, s * 0.5F, s * 1.5F, e + 0.35F, e + 0.6F, 3.2F, s * 1.0F, s * 2.1F, e + 0.9F, e + 1.15F, 0);
         b.slab(3.2F, s * 1.05F, s * 2.05F, e + 0.9F, e + 1.15F, 3.8F, s * 1.2F, s * 1.9F, e + 0.95F, e + 1.1F, 3);
      }
   }

   private static float crownWidth(float e, float t) {
      return e + 0.3F + 0.9F * (float)Math.sin(Math.PI * t) - 1.3F * t * t * t * t;
   }

   private static float crownFront(float e, float t) {
      return -(e + 0.3F) - 0.35F * (float)Math.sin(Math.PI * t) + 1.8F * t * t * t;
   }

   private static float crownBack(float e, float t) {
      return e + 0.3F + 0.6F * (float)Math.sin(Math.PI * t) + 2.4F * t * t;
   }

   private static void sunDisc(PharaohMesh.Builder b, float e, float top) {
      float m = e - 1.0F;
      b.slab(top + 0.3F, -m, m, -m, m, top - 1.8F, -(m + 0.4F), m + 0.4F, -(m + 0.4F), m + 0.4F, 0);
      float band = m + 0.35F;
      b.box(-band, top - 1.25F, -band, band, top - 0.7F, band, 1);
      float rim = m + 0.75F;
      b.box(-rim, top - 2.3F, -rim, rim, top - 1.8F, rim, 3);
      int samples = 11;
      float[] path = new float[samples * 3];
      float[] half = new float[samples];

      for (int s = -1; s <= 1; s += 2) {
         for (int i = 0; i < samples; i++) {
            float t = (float)i / (samples - 1);
            path[i * 3] = s * (1.6F + 5.0F * (float)Math.sin(2.9845130209103035 * t));
            path[i * 3 + 1] = top - 2.3F - 11.2F * t;
            path[i * 3 + 2] = 0.6F + 0.5F * (float)Math.sin(Math.PI * t);
            half[i] = 0.85F - 0.65F * t;
         }

         b.tube(path, half, samples, 0);
      }

      b.box(-0.55F, top - 4.2F, 0.45F, 0.55F, top - 2.3F, 1.35F, 3);
      float cy = top - 8.3F;
      float cz = 0.9F;
      b.disc(0.0F, cy, cz, 4.4F, 0.45F, 24, 2, 0);
      b.annulus(0.0F, cy, cz - 0.52F, 3.4F, 3.95F, 24, 0, cz);
   }

   private static void collar(PharaohMesh.Builder b, float z) {
      float cy = -1.2F;
      int rows = 4;
      int segments = 12;
      float width = 0.68F;

      for (int row = 0; row < rows; row++) {
         float r0 = 1.9F + row * width;
         float r1 = r0 + width;
         double from = Math.asin(Math.min(1.0, 1.0 / r0));
         double span = Math.PI - 2.0 * from;
         int mat = (row & 1) == 0 ? 0 : 1;

         for (int i = 0; i < segments; i++) {
            double a0 = from + span * i / segments;
            double a1 = from + span * (i + 1) / segments;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            b.quad(c0 * r0, cy + s0 * r0, z, c0 * r1, cy + s0 * r1, z, c1 * r1, cy + s1 * r1, z, c1 * r0, cy + s1 * r0, z, 0.0F, cy, z + 1.0F, mat);
         }
      }

      float edge = 1.9F + rows * width + 0.42F;
      double from = Math.asin(Math.min(1.0, 1.0 / edge));
      int drops = 9;

      for (int i = 0; i < drops; i++) {
         double a = from + (Math.PI - 2.0 * from) * (i + 0.5) / drops;
         float c = (float)Math.cos(a);
         float s = (float)Math.sin(a);
         float x = c * edge;
         float y = cy + s * edge;
         float tx = -s * 0.3F;
         float ty = c * 0.3F;
         float rx = c * 0.42F;
         float ry = s * 0.42F;
         b.quad(
            x - rx, y - ry, z - 0.02F, x + tx, y + ty, z - 0.02F, x + rx, y + ry, z - 0.02F, x - tx, y - ty, z - 0.02F, x, y, z + 1.0F, (i & 1) == 0 ? 2 : 0
         );
      }
   }

   private static void ring(float[] out, float y, float xa, float xb, float za, float zb) {
      set(out, 0, xa, y, za);
      set(out, 1, xb, y, za);
      set(out, 2, xb, y, zb);
      set(out, 3, xa, y, zb);
   }

   private static void set(float[] out, int corner, float x, float y, float z) {
      out[corner * 3] = x;
      out[corner * 3 + 1] = y;
      out[corner * 3 + 2] = z;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   @Environment(EnvType.CLIENT)
   static final class Builder {
      private float[] pos = new float[3072];
      private float[] normal = new float[768];
      private float[] sweep = new float[2048];
      private byte[] material = new byte[256];
      private float[] tone = new float[256];
      private int quads;
      private float shade = 1.0F;
      private final float[] corners = new float[24];

      void shade(float value) {
         this.shade = value;
      }

      private void ensure() {
         if (this.quads >= this.material.length) {
            int capacity = this.material.length * 2;
            this.pos = Arrays.copyOf(this.pos, capacity * 12);
            this.normal = Arrays.copyOf(this.normal, capacity * 3);
            this.sweep = Arrays.copyOf(this.sweep, capacity * 8);
            this.material = Arrays.copyOf(this.material, capacity);
            this.tone = Arrays.copyOf(this.tone, capacity);
         }
      }

      void quad(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         float ox,
         float oy,
         float oz,
         int mat
      ) {
         float ux = cx - ax;
         float uy = cy - ay;
         float uz = cz - az;
         float vx = dx - bx;
         float vy = dy - by;
         float vz = dz - bz;
         float nx = uy * vz - uz * vy;
         float ny = uz * vx - ux * vz;
         float nz = ux * vy - uy * vx;
         float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (!(length < 1.0E-6F)) {
            nx /= length;
            ny /= length;
            nz /= length;
            float mx = (ax + bx + cx + dx) * 0.25F - ox;
            float my = (ay + by + cy + dy) * 0.25F - oy;
            float mz = (az + bz + cz + dz) * 0.25F - oz;
            if (nx * mx + ny * my + nz * mz < 0.0F) {
               nx = -nx;
               ny = -ny;
               nz = -nz;
            }

            this.ensure();
            int p = this.quads * 12;
            this.vertex(p, ax, ay, az);
            this.vertex(p + 3, bx, by, bz);
            this.vertex(p + 6, cx, cy, cz);
            this.vertex(p + 9, dx, dy, dz);
            this.normal[this.quads * 3] = nx;
            this.normal[this.quads * 3 + 1] = ny;
            this.normal[this.quads * 3 + 2] = nz;
            this.material[this.quads] = (byte)mat;
            int hash = this.quads * -1640531535;
            hash ^= hash >>> 15;
            this.tone[this.quads] = this.shade * (0.955F + 0.065F * ((hash & 0xFF) / 255.0F));
            this.quads++;
         }
      }

      private void vertex(int p, float x, float y, float z) {
         this.pos[p] = x * 0.0625F;
         this.pos[p + 1] = y * 0.0625F;
         this.pos[p + 2] = z * 0.0625F;
         double angle = Math.atan2(x, -z) + y * 0.12F;
         int s = this.quads * 8 + (p - this.quads * 12) / 3 * 2;
         this.sweep[s] = (float)Math.cos(angle);
         this.sweep[s + 1] = (float)Math.sin(angle);
      }

      void prism(float[] c, int mat) {
         float ox = 0.0F;
         float oy = 0.0F;
         float oz = 0.0F;

         for (int i = 0; i < 8; i++) {
            ox += c[i * 3];
            oy += c[i * 3 + 1];
            oz += c[i * 3 + 2];
         }

         ox *= 0.125F;
         oy *= 0.125F;
         oz *= 0.125F;
         this.face(c, 0, 1, 2, 3, ox, oy, oz, mat);
         this.face(c, 4, 5, 6, 7, ox, oy, oz, mat);
         this.face(c, 0, 1, 5, 4, ox, oy, oz, mat);
         this.face(c, 1, 2, 6, 5, ox, oy, oz, mat);
         this.face(c, 2, 3, 7, 6, ox, oy, oz, mat);
         this.face(c, 3, 0, 4, 7, ox, oy, oz, mat);
      }

      private void face(float[] c, int a, int b, int d, int e, float ox, float oy, float oz, int mat) {
         this.quad(
            c[a * 3],
            c[a * 3 + 1],
            c[a * 3 + 2],
            c[b * 3],
            c[b * 3 + 1],
            c[b * 3 + 2],
            c[d * 3],
            c[d * 3 + 1],
            c[d * 3 + 2],
            c[e * 3],
            c[e * 3 + 1],
            c[e * 3 + 2],
            ox,
            oy,
            oz,
            mat
         );
      }

      void slab(float y0, float xa0, float xb0, float za0, float zb0, float y1, float xa1, float xb1, float za1, float zb1, int mat) {
         float[] c = this.corners;
         PharaohMesh.set(c, 0, xa0, y0, za0);
         PharaohMesh.set(c, 1, xb0, y0, za0);
         PharaohMesh.set(c, 2, xb0, y0, zb0);
         PharaohMesh.set(c, 3, xa0, y0, zb0);
         PharaohMesh.set(c, 4, xa1, y1, za1);
         PharaohMesh.set(c, 5, xb1, y1, za1);
         PharaohMesh.set(c, 6, xb1, y1, zb1);
         PharaohMesh.set(c, 7, xa1, y1, zb1);
         this.prism(c, mat);
      }

      void box(float x0, float y0, float z0, float x1, float y1, float z1, int mat) {
         this.slab(y0, x0, x1, z0, z1, y1, x0, x1, z0, z1, mat);
      }

      void stripedSlab(
         float y0, float xa0, float xb0, float za0, float zb0, float y1, float xa1, float xb1, float za1, float zb1, int bands, int first, int second
      ) {
         float[] from = new float[12];
         float[] to = new float[12];
         PharaohMesh.ring(from, y0, xa0, xb0, za0, zb0);
         PharaohMesh.ring(to, y1, xa1, xb1, za1, zb1);
         this.striped(from, to, bands, first, second);
      }

      void striped(float[] from, float[] to, int bands, int first, int second) {
         float[] c = this.corners;

         for (int k = 0; k < bands; k++) {
            float t0 = (float)k / bands;
            float t1 = (float)(k + 1) / bands;

            for (int i = 0; i < 12; i++) {
               c[i] = PharaohMesh.lerp(from[i], to[i], t0);
               c[12 + i] = PharaohMesh.lerp(from[i], to[i], t1);
            }

            this.prism(c, (k & 1) == 0 ? first : second);
         }
      }

      void studs(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         boolean wide,
         boolean clearCentre,
         float ox,
         float oy,
         float oz
      ) {
         int count = wide ? 3 : 2;

         for (int i = 0; i < count; i++) {
            float u = wide ? 0.2F + 0.3F * i : 0.35F + 0.3F * i;
            if (!clearCentre || !wide || i != 1) {
               this.stud(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u, 0.5F, ox, oy, oz);
            }
         }
      }

      private void stud(
         float ax,
         float ay,
         float az,
         float bx,
         float by,
         float bz,
         float cx,
         float cy,
         float cz,
         float dx,
         float dy,
         float dz,
         float u,
         float v,
         float ox,
         float oy,
         float oz
      ) {
         float px = PharaohMesh.lerp(PharaohMesh.lerp(ax, bx, u), PharaohMesh.lerp(dx, cx, u), v);
         float py = PharaohMesh.lerp(PharaohMesh.lerp(ay, by, u), PharaohMesh.lerp(dy, cy, u), v);
         float pz = PharaohMesh.lerp(PharaohMesh.lerp(az, bz, u), PharaohMesh.lerp(dz, cz, u), v);
         float ux = bx - ax;
         float uy = by - ay;
         float uz = bz - az;
         float ul = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
         float vx = dx - ax;
         float vy = dy - ay;
         float vz = dz - az;
         float vl = (float)Math.sqrt(vx * vx + vy * vy + vz * vz);
         if (!(ul < 1.0E-5F) && !(vl < 1.0E-5F)) {
            ux /= ul;
            uy /= ul;
            uz /= ul;
            vx /= vl;
            vy /= vl;
            vz /= vl;
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(nl < 1.0E-5F)) {
               nx /= nl;
               ny /= nl;
               nz /= nl;
               if (nx * (px - ox) + ny * (py - oy) + nz * (pz - oz) < 0.0F) {
                  nx = -nx;
                  ny = -ny;
                  nz = -nz;
               }

               float h = 0.32F;
               float lift = 0.07F;
               px += nx * lift;
               py += ny * lift;
               pz += nz * lift;
               this.quad(
                  px - ux * h - vx * h,
                  py - uy * h - vy * h,
                  pz - uz * h - vz * h,
                  px + ux * h - vx * h,
                  py + uy * h - vy * h,
                  pz + uz * h - vz * h,
                  px + ux * h + vx * h,
                  py + uy * h + vy * h,
                  pz + uz * h + vz * h,
                  px - ux * h + vx * h,
                  py - uy * h + vy * h,
                  pz - uz * h + vz * h,
                  px - nx,
                  py - ny,
                  pz - nz,
                  0
               );
            }
         }
      }

      void tube(float[] path, float[] half, int samples, int mat) {
         float[] c = this.corners;

         for (int i = 0; i + 1 < samples; i++) {
            section(c, 0, path, half, samples, i);
            section(c, 4, path, half, samples, i + 1);
            this.prism(c, i + 2 == samples ? 3 : mat);
         }
      }

      private static void section(float[] out, int corner, float[] path, float[] half, int samples, int i) {
         int prev = Math.max(0, i - 1);
         int next = Math.min(samples - 1, i + 1);
         float tx = path[next * 3] - path[prev * 3];
         float ty = path[next * 3 + 1] - path[prev * 3 + 1];
         float length = (float)Math.sqrt(tx * tx + ty * ty);
         float nx = length > 1.0E-5F ? -ty / length : 1.0F;
         float ny = length > 1.0E-5F ? tx / length : 0.0F;
         float h = half[i];
         float x = path[i * 3];
         float y = path[i * 3 + 1];
         float z = path[i * 3 + 2];
         PharaohMesh.set(out, corner, x + nx * h, y + ny * h, z - h);
         PharaohMesh.set(out, corner + 1, x + nx * h, y + ny * h, z + h);
         PharaohMesh.set(out, corner + 2, x - nx * h, y - ny * h, z + h);
         PharaohMesh.set(out, corner + 3, x - nx * h, y - ny * h, z - h);
      }

      void disc(float cx, float cy, float cz, float radius, float depth, int segments, int faceMat, int rimMat) {
         float zf = cz - depth;
         float zb = cz + depth;

         for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2) * i / segments;
            double a1 = (Math.PI * 2) * (i + 1) / segments;
            float x0 = cx + radius * (float)Math.cos(a0);
            float y0 = cy + radius * (float)Math.sin(a0);
            float x1 = cx + radius * (float)Math.cos(a1);
            float y1 = cy + radius * (float)Math.sin(a1);
            this.quad(cx, cy, zf, x0, y0, zf, x1, y1, zf, x1, y1, zf, cx, cy, cz, faceMat);
            this.quad(cx, cy, zb, x0, y0, zb, x1, y1, zb, x1, y1, zb, cx, cy, cz, faceMat);
            this.quad(x0, y0, zf, x1, y1, zf, x1, y1, zb, x0, y0, zb, cx, cy, cz, rimMat);
         }
      }

      void annulus(float cx, float cy, float z, float inner, float outer, int segments, int mat, float behind) {
         for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2) * i / segments;
            double a1 = (Math.PI * 2) * (i + 1) / segments;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            this.quad(
               cx + c0 * inner,
               cy + s0 * inner,
               z,
               cx + c0 * outer,
               cy + s0 * outer,
               z,
               cx + c1 * outer,
               cy + s1 * outer,
               z,
               cx + c1 * inner,
               cy + s1 * inner,
               z,
               cx,
               cy,
               behind,
               mat
            );
         }
      }
   }

   @Environment(EnvType.CLIENT)
   record Look(int[] palette, boolean glow, float sweepCos, float sweepSin, float sweepGain, float gemPulse) {
   }
}
