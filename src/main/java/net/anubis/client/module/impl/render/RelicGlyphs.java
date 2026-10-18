package net.anubis.client.module.impl.render;

import java.util.Arrays;
import net.anubis.client.util.glyph.GlyphShapes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class RelicGlyphs {
   static final float[] ANKH = GlyphShapes.ANKH;
   static final float[] EYE = join(GlyphShapes.EYE, GlyphShapes.EYE_PUPIL);
   static final float[] SCARAB = GlyphShapes.SCARAB;
   static final float[] DJED = djed();
   static final float[] FEATHER = feather();
   static final float[] WAS = was();
   static final float[] DOLLAR = dollar(16);
   static final float[] EURO = euro(26);
   static final float[] BITCOIN = bitcoin(12, 44);

   private RelicGlyphs() {
   }

   static float[] mobile(float[] glyph) {
      if (glyph == DOLLAR) {
         return RelicGlyphs.Mobile.DOLLAR;
      } else if (glyph == EURO) {
         return RelicGlyphs.Mobile.EURO;
      } else {
         return glyph == BITCOIN ? RelicGlyphs.Mobile.BITCOIN : glyph;
      }
   }

   private static float[] djed() {
      RelicGlyphs.Sketch s = new RelicGlyphs.Sketch();
      s.quad(-0.36F, -1.0F, 0.36F, -1.0F, 0.32F, -0.88F, -0.32F, -0.88F);
      s.quad(-0.2F, -0.88F, 0.2F, -0.88F, 0.15F, 0.2F, -0.15F, 0.2F);

      for (int k = 0; k < 4; k++) {
         float low = 0.2F + k * 0.2F;
         float high = low + 0.12F;
         float reach = k == 3 ? 0.48F : 0.44F;
         s.quad(-reach, low, reach, low, reach, high, -reach, high);
         if (k < 3) {
            s.quad(-0.15F, high, 0.15F, high, 0.15F, high + 0.08F, -0.15F, high + 0.08F);
         }
      }

      s.quad(-0.36F, 0.92F, 0.36F, 0.92F, 0.26F, 1.0F, -0.26F, 1.0F);
      return s.build();
   }

   private static float[] feather() {
      float[] spine = new float[]{
         0.0F,
         -0.72F,
         0.0F,
         -0.54F,
         0.01F,
         -0.3F,
         0.02F,
         0.0F,
         0.03F,
         0.26F,
         0.06F,
         0.48F,
         0.11F,
         0.64F,
         0.19F,
         0.77F,
         0.3F,
         0.86F,
         0.42F,
         0.9F,
         0.53F,
         0.87F,
         0.6F,
         0.8F
      };
      float[] edge = new float[]{0.05F, 0.21F, 0.28F, 0.3F, 0.29F, 0.26F, 0.22F, 0.17F, 0.13F, 0.09F, 0.05F, 0.02F};
      int n = edge.length;
      float[] rachis = new float[n];
      float[] outer = new float[n];
      float[] inner = new float[n];

      for (int i = 0; i < n; i++) {
         rachis[i] = Math.min(0.026F, edge[i] * 0.45F);
         outer[i] = -edge[i];
         inner[i] = -rachis[i];
      }

      RelicGlyphs.Sketch s = new RelicGlyphs.Sketch();
      s.quad(-0.04F, -1.0F, 0.04F, -1.0F, 0.04F, -0.7F, -0.04F, -0.7F);
      s.band(spine, rachis, edge);
      s.band(spine, outer, inner);
      return s.build();
   }

   private static float[] was() {
      RelicGlyphs.Sketch s = new RelicGlyphs.Sketch();
      s.quad(-0.08F, -0.7F, 0.08F, -0.7F, 0.08F, 0.5F, -0.08F, 0.5F);
      s.quad(-0.3F, -1.0F, -0.16F, -1.0F, 0.03F, -0.7F, -0.08F, -0.7F);
      s.quad(0.16F, -1.0F, 0.3F, -1.0F, 0.08F, -0.7F, -0.03F, -0.7F);
      s.quad(-0.08F, 0.5F, 0.08F, 0.5F, 0.26F, 0.78F, 0.06F, 0.86F);
      s.stroke(0.15F, 0.12F, 0.8F, 0.36F, 0.84F, 0.62F, 0.7F);
      s.stroke(0.1F, 0.08F, 0.82F, -0.02F, 1.0F);
      return s.build();
   }

   private static float[] dollar(int steps) {
      float r = 0.34F;
      float[] upper = arc(0.0F, r, r, 28.0F, 270.0F, steps);
      float[] lower = arc(0.0F, -r, r, 90.0F, -152.0F, steps);
      RelicGlyphs.Sketch s = new RelicGlyphs.Sketch();
      s.stroke(0.17F, join(upper, Arrays.copyOfRange(lower, 2, lower.length)));
      s.quad(-0.055F, -0.98F, 0.055F, -0.98F, 0.055F, 0.98F, -0.055F, 0.98F);
      return s.build();
   }

   private static float[] euro(int steps) {
      RelicGlyphs.Sketch s = new RelicGlyphs.Sketch();
      s.stroke(0.17F, arc(0.1F, 0.0F, 0.62F, 48.0F, 312.0F, steps));
      s.quad(-0.82F, 0.06F, 0.16F, 0.06F, 0.16F, 0.2F, -0.82F, 0.2F);
      s.quad(-0.82F, -0.22F, 0.1F, -0.22F, 0.1F, -0.08F, -0.82F, -0.08F);
      return s.build();
   }

   private static float[] bitcoin(int letterSteps, int rimSteps) {
      RelicGlyphs.Sketch b = new RelicGlyphs.Sketch();
      b.quad(-0.3F, -0.56F, -0.17F, -0.56F, -0.17F, 0.56F, -0.3F, 0.56F);
      b.stroke(0.13F, join(new float[]{-0.24F, 0.5F}, arc(0.06F, 0.27F, 0.23F, 90.0F, -90.0F, letterSteps), new float[]{-0.24F, 0.04F}));
      b.stroke(0.13F, join(new float[]{-0.24F, 0.04F}, arc(0.1F, -0.24F, 0.28F, 90.0F, -90.0F, letterSteps), new float[]{-0.24F, -0.52F}));
      b.quad(-0.21F, 0.5F, -0.13F, 0.5F, -0.13F, 0.74F, -0.21F, 0.74F);
      b.quad(-0.03F, 0.5F, 0.05F, 0.5F, 0.05F, 0.74F, -0.03F, 0.74F);
      b.quad(-0.21F, -0.74F, -0.13F, -0.74F, -0.13F, -0.5F, -0.21F, -0.5F);
      b.quad(-0.03F, -0.74F, 0.05F, -0.74F, 0.05F, -0.5F, -0.03F, -0.5F);
      float[] mark = b.build();
      float c = (float)Math.cos(Math.toRadians(-14.0));
      float sn = (float)Math.sin(Math.toRadians(-14.0));

      for (int i = 0; i < mark.length; i += 2) {
         float x = mark[i] + 0.04F;
         float y = mark[i + 1];
         mark[i] = x * c - y * sn;
         mark[i + 1] = x * sn + y * c;
      }

      RelicGlyphs.Sketch rim = new RelicGlyphs.Sketch();
      rim.stroke(0.1F, arc(0.0F, 0.0F, 0.92F, 0.0F, 360.0F, rimSteps));
      return join(rim.build(), mark);
   }

   private static float[] arc(float cx, float cy, float radius, float from, float to, int steps) {
      float[] points = new float[(steps + 1) * 2];

      for (int i = 0; i <= steps; i++) {
         double angle = Math.toRadians(from + (to - from) * i / steps);
         points[i * 2] = cx + radius * (float)Math.cos(angle);
         points[i * 2 + 1] = cy + radius * (float)Math.sin(angle);
      }

      return points;
   }

   private static float[] join(float[]... parts) {
      int length = 0;

      for (float[] part : parts) {
         length += part.length;
      }

      float[] out = new float[length];
      int at = 0;

      for (float[] part : parts) {
         System.arraycopy(part, 0, out, at, part.length);
         at += part.length;
      }

      return out;
   }

   @Environment(EnvType.CLIENT)
   private static final class Mobile {
      static final float[] DOLLAR = RelicGlyphs.dollar(10);
      static final float[] EURO = RelicGlyphs.euro(16);
      static final float[] BITCOIN = RelicGlyphs.bitcoin(8, 24);
   }

   @Environment(EnvType.CLIENT)
   private static final class Sketch {
      private float[] data = new float[64];
      private int size;

      void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
         if (this.size + 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.size++] = ax;
         this.data[this.size++] = ay;
         this.data[this.size++] = bx;
         this.data[this.size++] = by;
         this.data[this.size++] = cx;
         this.data[this.size++] = cy;
         this.data[this.size++] = dx;
         this.data[this.size++] = dy;
      }

      void stroke(float width, float... points) {
         float[] low = new float[points.length / 2];
         float[] high = new float[points.length / 2];
         Arrays.fill(low, -width * 0.5F);
         Arrays.fill(high, width * 0.5F);
         this.band(points, low, high);
      }

      void band(float[] points, float[] low, float[] high) {
         int n = points.length / 2;
         float[] nx = new float[n];
         float[] ny = new float[n];

         for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            float dx = points[b * 2] - points[a * 2];
            float dy = points[b * 2 + 1] - points[a * 2 + 1];
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            nx[i] = -dy / length;
            ny[i] = dx / length;
         }

         int i = 0;

         for (int j = 1; j < n; j++) {
            float ax = points[i * 2];
            float ay = points[i * 2 + 1];
            float bx = points[j * 2];
            float by = points[j * 2 + 1];
            this.quad(
               ax + nx[i] * low[i],
               ay + ny[i] * low[i],
               bx + nx[j] * low[j],
               by + ny[j] * low[j],
               bx + nx[j] * high[j],
               by + ny[j] * high[j],
               ax + nx[i] * high[i],
               ay + ny[i] * high[i]
            );
            i++;
         }
      }

      float[] build() {
         return Arrays.copyOf(this.data, this.size);
      }
   }
}
