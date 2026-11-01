package net.anubis.client.module.impl.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D.Float;
import java.awt.image.BufferedImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class PeepArt {
   static final int STREAK = 0;
   static final int DROP = 1;
   static final int PETAL = 2;
   static final int HEART_LEFT = 3;
   static final int HEART_RIGHT = 4;
   static final int FEATHER = 5;
   static final int WISP = 6;
   static final int BEAM = 7;
   static final int TWINKLE = 8;
   static final int ARC = 9;
   static final int MIST = 10;
   static final int BOLT = 11;
   static final int BOLT_FORK = 12;
   static final int HEART = 13;
   static final int CONE = 14;
   static final int PETAL_CURL = 15;
   static final int GLINT = 16;
   static final int COUNT = 17;
   static final float ARC_RADIUS = 0.421875F;
   static final float CONE_FOOT = 0.05F;
   static final float CONE_END = 0.9F;
   static final float FEATHER_ASPECT = 0.28846154F;

   private PeepArt() {
   }

   static PeepArt.Art[] drawAll() {
      PeepArt.Art[] out = new PeepArt.Art[17];
      out[0] = streak();
      out[1] = drop();
      out[2] = petal(false);
      out[15] = petal(true);
      PeepArt.Art[] heart = heart();
      out[13] = heart[0];
      out[3] = heart[1];
      out[4] = heart[2];
      out[5] = feather();
      out[6] = wisp();
      out[7] = beam();
      out[8] = twinkle();
      out[9] = arc();
      out[10] = mist();
      out[11] = bolt(7, false);
      out[12] = bolt(23, true);
      out[14] = cone();
      out[16] = glint();
      return out;
   }

   static int[] rim(int[] tattoo, int w, int h) {
      float[] core = new float[w * h];

      for (int i = 0; i < core.length; i++) {
         core[i] = (tattoo[i] >>> 24) / 255.0F;
      }

      float[] wide = PeepSky.blur(core, w, h, 3);
      float[] deep = PeepSky.blur(core, w, h, 7);
      float[] out = new float[core.length];

      for (int i = 0; i < out.length; i++) {
         float ring = MathHelper.clamp(wide[i] * 2.2F - smooth(0.55F, 0.95F, core[i]) * 1.2F, 0.0F, 1.0F) * (1.0F - smooth(0.6F, 0.8F, deep[i]));
         float band = core[i] * smooth(0.4F, 0.6F, deep[i]) * (1.0F - smooth(0.85F, 0.97F, deep[i]));
         float tip = core[i] * (1.0F - smooth(0.45F, 0.6F, deep[i]));
         out[i] = Math.max(ring, Math.max(band, tip));
      }

      return white(out);
   }

   static int[] gold(int[] tattoo, int w, int h) {
      float[] core = new float[w * h];

      for (int i = 0; i < core.length; i++) {
         core[i] = smooth(0.3F, 0.65F, (tattoo[i] >>> 24) / 255.0F);
      }

      float[] deep = PeepSky.blur(core, w, h, 7);
      float[] fill = PeepSky.blur(core, w, h, 12);
      float[] soft = PeepSky.blur(core, w, h, 2);
      int[] out = new int[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int i = y * w + x;
            float line = core[i] * (1.0F - smooth(0.88F, 0.98F, fill[i]));
            line = Math.max(line, Math.min(1.0F, soft[i] * 1.8F) * (1.0F - smooth(0.25F, 0.45F, deep[i])));
            if (!(line <= 0.002F)) {
               float bevel = soft[Math.min(h - 1, y + 2) * w + Math.min(w - 1, x + 2)] - soft[Math.max(0, y - 2) * w + Math.max(0, x - 2)];
               float lit = MathHelper.clamp(0.5F + 1.1F * bevel, 0.0F, 1.0F);
               int r = Math.round(MathHelper.lerp(lit, 0.85F, 1.0F) * 255.0F);
               int g = Math.round(MathHelper.lerp(lit, 0.8F, 1.0F) * 255.0F);
               int b = Math.round(MathHelper.lerp(lit, 0.45F, 1.0F) * 255.0F);
               out[i] = Math.round(MathHelper.clamp(line, 0.0F, 1.0F) * 255.0F) << 24 | r << 16 | g << 8 | b;
            }
         }
      }

      return out;
   }

   static int[] veil(int[] tattoo, int w, int h) {
      float[] core = new float[w * h];

      for (int i = 0; i < core.length; i++) {
         core[i] = (tattoo[i] >>> 24) / 255.0F;
      }

      float[] soft = PeepSky.blur(core, w, h, 3);
      float[] deep = PeepSky.blur(core, w, h, 7);
      float[] mist = PeepSky.blur(core, w, h, 11);
      float[] out = new float[core.length];

      for (int i = 0; i < out.length; i++) {
         float hollow = 1.0F - 0.85F * smooth(0.6F, 0.9F, deep[i]);
         float body = Math.min(1.0F, soft[i] * 1.15F) * hollow;
         out[i] = Math.max(body, Math.min(1.0F, mist[i] * 2.2F) * 0.45F * (0.4F + 0.6F * hollow));
      }

      return white(out);
   }

   private static PeepArt.Art streak() {
      int w = 256;
      int h = 32;
      float[] a = new float[w * h];
      float head = 0.93F;

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float t = (x + 0.5F) / w;
            float dy = y + 0.5F - h * 0.5F;
            float along = t < head ? (float)Math.pow(t / head, 2.6) : (float)Math.cos((t - head) / (1.0F - head) * (float) (Math.PI / 2));
            float sigma = 0.7F + 2.6F * (float)Math.pow(t, 1.6);
            float body = along * gauss(dy, sigma);
            float bulb = 0.9F * gauss((float)Math.hypot(x + 0.5F - w * 0.9F, dy), 3.4F);
            a[y * w + x] = Math.min(1.0F, body + bulb);
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 2)));
   }

   private static PeepArt.Art drop() {
      int w = 12;
      int h = 96;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float t = (y + 0.5F) / h;
         float along = (float)Math.pow(Math.sin(Math.PI * t), 0.7) * (0.3F + 0.7F * t);

         for (int x = 0; x < w; x++) {
            a[y * w + x] = along * gauss(x + 0.5F - w * 0.5F, 1.15F);
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 1)));
   }

   private static PeepArt.Art petal(boolean curled) {
      int w = 60;
      int h = 68;
      int pad = 6;
      Float shape = new Float();
      if (curled) {
         shape.moveTo(0.52, 0.99);
         shape.curveTo(0.2, 0.84, 0.03, 0.52, 0.12, 0.22);
         shape.curveTo(0.17, 0.06, 0.31, 0.02, 0.41, 0.13);
         shape.curveTo(0.56, 0.25, 0.76, 0.21, 0.93, 0.07);
         shape.curveTo(1.0, 0.42, 0.85, 0.8, 0.52, 0.99);
      } else {
         shape.moveTo(0.46, 0.99);
         shape.curveTo(0.3, 0.9, 0.06, 0.62, 0.05, 0.38);
         shape.curveTo(0.04, 0.14, 0.24, 0.02, 0.45, 0.04);
         shape.curveTo(0.55, 0.05, 0.6, 0.075, 0.64, 0.07);
         shape.curveTo(0.8, 0.05, 0.97, 0.18, 0.95, 0.4);
         shape.curveTo(0.93, 0.62, 0.7, 0.88, 0.54, 0.99);
      }

      shape.closePath();
      float[] hard = fill(shape, w, h, pad);
      float[] mask = PeepSky.blur(hard, w, h, 1);
      float[] inner = PeepSky.blur(hard, w, h, 4);
      int[] out = new int[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int i = y * w + x;
            float m = mask[i];
            if (!(m <= 0.002F)) {
               float nx = (x + 0.5F - pad) / (w - pad * 2.0F);
               float ny = (y + 0.5F - pad) / (h - pad * 2.0F);
               float r = (float)Math.hypot((nx - 0.5F) * 1.2F, ny - 0.42F) / 0.6F;
               int col = mix(16763356, 16088488, smooth(0.08F, 0.75F, r));
               col = mix(col, 13582964, smooth(0.7F, 1.15F, r) * 0.65F);
               float base = smooth(0.64F, 1.0F, ny);
               col = mix(col, 11020378, base * 0.5F);
               float angle = (float)Math.atan2(nx - 0.5F, 1.0F - ny);
               float vein = (float)Math.pow(Math.abs(Math.cos(angle * 11.0F)), 24.0) * (1.0F - base) * smooth(0.1F, 0.5F, 1.0F - ny);
               col = mix(col, 16768232, vein * 0.2F);
               float rim = (1.0F - smooth(0.55F, 0.9F, inner[i])) * (1.0F - smooth(0.4F, 0.9F, ny));
               col = mix(col, 16758996, rim * 0.55F);
               if (curled) {
                  float lip = (1.0F - smooth(0.5F, 0.85F, inner[i])) * (1.0F - smooth(0.16F, 0.4F, ny));
                  float under = smooth(0.14F, 0.26F, ny) * (1.0F - smooth(0.26F, 0.46F, ny)) * smooth(0.3F, 0.8F, inner[i]);
                  col = mix(mix(col, 16767462, lip * 0.7F), 11546719, under * 0.35F);
               }

               out[i] = ColorHelper.withAlpha(MathHelper.clamp(m * 0.95F, 0.0F, 1.0F), col);
            }
         }
      }

      return new PeepArt.Art(w, h, out);
   }

   private static PeepArt.Art[] heart() {
      int w = 128;
      int h = 120;
      int pad = 16;
      Float shape = new Float();
      shape.moveTo(0.5, 0.95);
      shape.curveTo(0.13, 0.68, 0.0, 0.44, 0.08, 0.24);
      shape.curveTo(0.17, 0.04, 0.42, 0.03, 0.5, 0.24);
      shape.curveTo(0.58, 0.03, 0.83, 0.04, 0.92, 0.24);
      shape.curveTo(1.0, 0.44, 0.87, 0.68, 0.5, 0.95);
      shape.closePath();
      float[] mask = fill(shape, w, h, pad);
      float[][] part = new float[][]{mask, new float[w * h], new float[w * h]};

      for (int y = 0; y < h; y++) {
         float split = w * 0.5F + crack((y + 0.5F - pad) / (h - pad * 2.0F)) * (w - pad * 2.0F);

         for (int x = 0; x < w; x++) {
            part[x + 0.5F < split ? 1 : 2][y * w + x] = mask[y * w + x];
         }
      }

      PeepArt.Art[] out = new PeepArt.Art[part.length];

      for (int piece = 0; piece < part.length; piece++) {
         float[] core = part[piece];
         float[] inner = PeepSky.blur(core, w, h, 6);
         float[] glow = PeepSky.blur(core, w, h, 7);
         int[] px = new int[w * h];

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int i = y * w + x;
               float nx = (x + 0.5F - pad) / (w - pad * 2.0F);
               float ny = (y + 0.5F - pad) / (h - pad * 2.0F);
               float m = core[i];
               float edge = MathHelper.clamp((m - inner[i]) * 2.6F, 0.0F, 1.0F) * m;
               float shine = gauss((float)Math.hypot(nx - 0.3F, ny - 0.27F), 0.09F) * m;
               int body = mix(12720235, 16732080, 0.25F + shine * 0.5F);
               float bodyAlpha = Math.max(m * (0.16F + 0.1F * (1.0F - ny)), edge);
               float haloAlpha = (1.0F - m) * Math.min(1.0F, glow[i] * 1.5F) * 0.65F;
               float alpha = bodyAlpha + haloAlpha;
               if (!(alpha <= 0.002F)) {
                  int line = mix(16751320, 16770806, smooth(0.75F, 1.0F, edge) * 0.45F);
                  int col = mix(16732080, mix(body, line, edge / Math.max(bodyAlpha, 1.0E-4F)), bodyAlpha / alpha);
                  px[i] = ColorHelper.withAlpha(MathHelper.clamp(alpha, 0.0F, 1.0F), col);
               }
            }
         }

         out[piece] = new PeepArt.Art(w, h, px);
      }

      return out;
   }

   static float crack(float ny) {
      float[] ys = new float[]{0.2F, 0.34F, 0.47F, 0.6F, 0.73F, 0.86F, 1.0F};
      float[] xs = new float[]{0.0F, -0.05F, 0.045F, -0.04F, 0.04F, -0.025F, 0.0F};
      if (ny <= ys[0]) {
         return 0.0F;
      }

      for (int i = 1; i < ys.length; i++) {
         if (ny <= ys[i]) {
            return MathHelper.lerp((ny - ys[i - 1]) / (ys[i] - ys[i - 1]), xs[i - 1], xs[i]);
         }
      }

      return 0.0F;
   }

   private static PeepArt.Art feather() {
      int w = Math.round(60.0F);
      int h = 208;
      BufferedImage image = new BufferedImage(w, h, 2);
      Graphics2D g = smooth(image);
      java.awt.geom.QuadCurve2D.Float shaft = new java.awt.geom.QuadCurve2D.Float(w * 0.42F, h * 0.97F, w * 0.56F, h * 0.5F, w * 0.44F, h * 0.035F);
      int barbs = 120;

      for (int i = 0; i < barbs; i++) {
         float t = 0.06F + 0.92F * i / (barbs - 1.0F);
         float px = quad(shaft.x1, shaft.ctrlx, shaft.x2, t);
         float py = quad(shaft.y1, shaft.ctrly, shaft.y2, t);
         float tx = quadSlope(shaft.x1, shaft.ctrlx, shaft.x2, t);
         float ty = quadSlope(shaft.y1, shaft.ctrly, shaft.y2, t);
         float len = (float)Math.hypot(tx, ty);
         tx /= len;
         ty /= len;
         float profile = (float)Math.pow(Math.sin(Math.PI * Math.min(1.0F, (t - 0.02F) / 0.97F)), 0.75);
         boolean fluff = t < 0.3F;

         for (int side = -1; side <= 1; side += 2) {
            float gap = PeepSky.hash01(i * 7 + side * 3 + 11);
            if ((fluff || !(gap < 0.15F)) && (side != 1 || i < 50 || i > 53) && (side != -1 || i < 82 || i > 85)) {
               float reach = w * (fluff ? 0.36F : 0.4F) * profile * (0.9F + 0.1F * gap) * (side < 0 ? 0.7F : 1.05F);
               float angle = (fluff ? 0.55F : 0.95F) * side;
               float dx = tx * (float)Math.cos(angle) - ty * (float)Math.sin(angle);
               float dy = tx * (float)Math.sin(angle) + ty * (float)Math.cos(angle);
               float bend = fluff ? 0.35F : 0.12F;
               g.setColor(new Color(1.0F, 1.0F, 1.0F, fluff ? 0.32F : 0.72F));
               g.setStroke(new BasicStroke(fluff ? 0.9F : 1.05F, 1, 1));
               g.draw(
                  new java.awt.geom.QuadCurve2D.Float(
                     px,
                     py,
                     px + dx * reach * 0.55F,
                     py + dy * reach * 0.55F,
                     px + dx * reach - side * ty * reach * bend * 0.0F + tx * reach * bend,
                     py + dy * reach + ty * reach * bend
                  )
               );
            }
         }
      }

      g.dispose();
      BufferedImage quill = new BufferedImage(w, h, 2);
      g = smooth(quill);
      g.setColor(new Color(1.0F, 1.0F, 1.0F, 1.0F));
      g.setStroke(new BasicStroke(1.7F, 1, 1));
      g.draw(shaft);
      g.dispose();
      float[] barb = alpha(image);
      float[] stem = alpha(quill);
      float[] a = new float[barb.length];

      for (int i = 0; i < a.length; i++) {
         a[i] = stem[i] + barb[i] * (1.0F - stem[i]);
      }

      float[] soft = PeepSky.blur(a, w, h, 1);

      for (int i = 0; i < a.length; i++) {
         a[i] = Math.min(1.0F, a[i] * 0.8F + soft[i] * 0.45F);
      }

      fadeBorder(a, w, h, 2);
      float[] bright = PeepSky.blur(stem, w, h, 1);
      int[] out = new int[w * h];

      for (int i = 0; i < out.length; i++) {
         out[i] = ColorHelper.withAlpha(MathHelper.clamp(a[i], 0.0F, 1.0F), mix(16764134, 16777215, Math.min(1.0F, bright[i] * 1.6F)));
      }

      return new PeepArt.Art(w, h, out);
   }

   private static PeepArt.Art wisp() {
      int w = 96;
      int h = 256;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float t = (y + 0.5F) / h;
         float along = smooth(0.0F, 0.2F, t) * (float)Math.pow(1.0F - t, 1.5);
         float bend = (float)Math.pow(t, 1.2);
         float centre = 0.5F + 0.13F * (float)Math.sin(t * 6.0F + 0.4F) * bend;
         float second = 0.5F + 0.13F * (float)Math.sin(t * 6.0F + 2.3F) * bend;
         float sigma = w * (0.12F * (float)Math.pow(1.0F - t, 0.8) + 0.018F);

         for (int x = 0; x < w; x++) {
            float nx = (x + 0.5F) / w;
            float veil = gauss((nx - centre) * w, sigma);
            float strand = 0.5F * gauss((nx - second) * w, sigma * 0.6F) * smooth(0.22F, 0.5F, t);
            float core = 0.55F * gauss((nx - centre) * w, sigma * 0.35F) * (1.0F - t);
            a[y * w + x] = Math.min(1.0F, along * (0.7F * veil + strand + core));
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(PeepSky.blur(a, w, h, 3), w, h, 3)));
   }

   private static PeepArt.Art beam() {
      int w = 128;
      int h = 512;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float t = 1.0F - (y + 0.5F) / h;
         float along = smooth(0.0F, 0.09F, t) * (float)Math.pow(1.0F - t, 1.25);
         float sigma = 1.6F + 27.0F * t;
         float core = 0.7F + 3.2F * t;

         for (int x = 0; x < w; x++) {
            float dx = x + 0.5F - w * 0.5F;
            a[y * w + x] = Math.min(1.0F, along * (0.7F * gauss(dx, sigma) + 0.45F * gauss(dx, core) * (1.0F - t)));
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 2)));
   }

   private static PeepArt.Art cone() {
      int w = 256;
      int h = 512;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float t = 1.0F - (y + 0.5F) / h;
         float along = smooth(0.0F, 0.035F, t) * (1.0F - smooth(0.3F, 1.0F, t)) * (0.6F + 0.4F * (1.0F - t));
         float half = (0.05F + 0.84999996F * t) * w * 0.5F;

         for (int x = 0; x < w; x++) {
            float across = (x + 0.5F - w * 0.5F) / half;
            float r = Math.abs(across);
            if (!(r >= 1.2F)) {
               float body = 1.0F - smooth(0.5F, 1.05F, r);
               float axis = 0.35F * gauss(r, 0.2F);
               float shafts = 0.88F + 0.12F * valueNoise((across + 1.2F) * 3.5F, t * 2.2F, 64, 1);
               a[y * w + x] = Math.min(1.0F, along * (0.7F * body + axis) * shafts);
            }
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 2)));
   }

   private static PeepArt.Art glint() {
      int w = 32;
      int h = 32;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float dx = x + 0.5F - w * 0.5F;
            float dy = y + 0.5F - h * 0.5F;
            float d = (float)Math.hypot(dx, dy);
            float rays = glintRay(dx, dy) + glintRay(dy, dx);
            float core = gauss(d, 1.6F) + 0.4F * gauss(d, 4.0F);
            a[y * w + x] = Math.min(1.0F, rays + core);
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 1)));
   }

   private static float glintRay(float along, float across) {
      float reach = Math.max(0.0F, 1.0F - Math.abs(along) / 15.0F);
      return gauss(across, 0.7F + 0.9F * reach) * reach * reach * 0.9F;
   }

   private static PeepArt.Art twinkle() {
      int w = 64;
      int h = 64;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float dx = x + 0.5F - w * 0.5F;
            float dy = y + 0.5F - h * 0.5F;
            float ray = ray(dx, dy, 29.0F) + ray(dy, dx, 29.0F);
            float diag = 0.35F * (ray((dx + dy) * 0.7071F, (dx - dy) * 0.7071F, 14.0F) + ray((dx - dy) * 0.7071F, (dx + dy) * 0.7071F, 14.0F));
            float core = gauss((float)Math.hypot(dx, dy), 2.6F) + 0.35F * gauss((float)Math.hypot(dx, dy), 7.0F);
            a[y * w + x] = Math.min(1.0F, ray + diag + core);
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 2)));
   }

   private static PeepArt.Art arc() {
      int w = 256;
      int h = 256;
      float radius = 0.421875F * w;
      float sweep = 2.6F;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float dx = x + 0.5F - w * 0.5F;
            float dy = y + 0.5F - h * 0.5F;
            float r = (float)Math.hypot(dx, dy);
            float behind = (float)(-Math.atan2(dy, dx));
            if (behind < 0.0F) {
               behind += (float) (Math.PI * 2);
            }

            float head = behind < 0.06F ? 1.0F : (behind > sweep ? 0.0F : (float)Math.pow(1.0F - behind / sweep, 1.6));
            if (behind > 6.2031856F) {
               head = (float)Math.cos(((float) (Math.PI * 2) - behind) / 0.08F * (float) (Math.PI / 2));
            }

            float line = gauss(r - radius, 1.3F) + 0.3F * gauss(r - radius, 4.5F);
            a[y * w + x] = Math.min(1.0F, line * head);
         }
      }

      return new PeepArt.Art(w, h, white(fadeBorder(a, w, h, 2)));
   }

   private static PeepArt.Art mist() {
      int w = 512;
      int h = 96;
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float t = (y + 0.5F) / h;
         float profile = (float)Math.pow(Math.sin(Math.PI * t), 1.4) * (0.55F + 0.45F * t);

         for (int x = 0; x < w; x++) {
            float n = 0.0F;
            float amp = 0.55F;
            float total = 0.0F;

            for (int octave = 0; octave < 4; octave++) {
               int cells = 4 << octave;
               n += amp * valueNoise((float)x / w * cells, t * (2.5F + octave * 2.5F) + octave * 7.3F, cells, octave);
               total += amp;
               amp *= 0.5F;
            }

            n /= total;
            a[y * w + x] = profile * smooth(0.28F, 0.78F, n);
         }
      }

      float[] out = PeepSky.blur(a, w, h, 1);

      for (int y = 0; y < h; y++) {
         float edge = smooth(0.0F, 3.0F, y) * smooth(0.0F, 3.0F, h - 1 - y);

         for (int x = 0; x < w; x++) {
            out[y * w + x] = out[y * w + x] * edge;
         }
      }

      return new PeepArt.Art(w, h, white(out));
   }

   private static PeepArt.Art bolt(int seed, boolean fork) {
      int w = 128;
      int h = 256;
      BufferedImage image = new BufferedImage(w, h, 2);
      Graphics2D g = smooth(image);
      g.setColor(Color.WHITE);
      float[] xs = new float[33];
      float[] ys = new float[33];
      jag(xs, ys, w * 0.5F, 12.0F, w * 0.5F + (PeepSky.hash01(seed) - 0.5F) * 30.0F, h - 12.0F, seed);
      g.setStroke(new BasicStroke(2.6F, 1, 1));
      g.draw(polyline(xs, ys));
      float[] bx = new float[17];
      float[] by = new float[17];
      int branches = fork ? 2 : 1;

      for (int b = 0; b < branches; b++) {
         int from = 9 + b * 8;
         float side = (b % 2 == 0 ? 1.0F : -1.0F) * (PeepSky.hash01(seed + b) < 0.5F ? 1.0F : -1.0F);
         jag(bx, by, xs[from], ys[from], xs[from] + side * (26.0F + 14.0F * b), ys[from] + 58.0F - 10.0F * b, seed * 3 + 1 + b);
         g.setStroke(new BasicStroke(1.5F - 0.3F * b, 1, 1));
         g.draw(polyline(bx, by));
      }

      g.dispose();
      float[] core = alpha(image);

      for (int y = 0; y < h; y++) {
         float out = smooth(12.0F, 44.0F, y);

         for (int x = 0; x < w; x++) {
            core[y * w + x] = core[y * w + x] * out;
         }
      }

      float[] glow = PeepSky.blur(core, w, h, 6);
      int[] out = new int[w * h];

      for (int i = 0; i < out.length; i++) {
         float halo = Math.min(1.0F, glow[i] * 3.0F);
         out[i] = ColorHelper.withAlpha(MathHelper.clamp(Math.max(core[i], halo * 0.7F), 0.0F, 1.0F), mix(16732080, 16774395, core[i]));
      }

      return new PeepArt.Art(w, h, out);
   }

   private static void jag(float[] xs, float[] ys, float x0, float y0, float x1, float y1, int seed) {
      int n = xs.length - 1;
      xs[0] = x0;
      ys[0] = y0;
      xs[n] = x1;
      ys[n] = y1;
      float spread = (float)Math.hypot(x1 - x0, y1 - y0) * 0.32F;

      for (int step = n; step > 1; step /= 2) {
         for (int i = 0; i + step <= n; i += step) {
            int mid = i + step / 2;
            xs[mid] = (xs[i] + xs[i + step]) * 0.5F + (PeepSky.hash01(seed * 131 + mid * 17 + step) - 0.5F) * spread;
            float y = (ys[i] + ys[i + step]) * 0.5F + (PeepSky.hash01(seed * 151 + mid * 13 + step) - 0.5F) * spread * 0.25F;
            float a = ys[i] + 0.3F * (ys[i + step] - ys[i]);
            float b = ys[i] + 0.7F * (ys[i + step] - ys[i]);
            ys[mid] = MathHelper.clamp(y, Math.min(a, b), Math.max(a, b));
         }

         spread *= 0.62F;
      }
   }

   private static Float polyline(float[] xs, float[] ys) {
      Float path = new Float();
      path.moveTo(xs[0], ys[0]);

      for (int i = 1; i < xs.length; i++) {
         path.lineTo(xs[i], ys[i]);
      }

      return path;
   }

   private static float ray(float along, float across, float length) {
      float reach = Math.max(0.0F, 1.0F - Math.abs(along) / length);
      return gauss(across, 0.85F + 0.6F * reach) * reach * reach;
   }

   private static float valueNoise(float x, float y, int period, int octave) {
      int x0 = (int)Math.floor(x);
      int y0 = (int)Math.floor(y);
      float fx = x - x0;
      float fy = y - y0;
      fx = fx * fx * (3.0F - 2.0F * fx);
      fy = fy * fy * (3.0F - 2.0F * fy);
      float a = lattice(x0, y0, period, octave);
      float b = lattice(x0 + 1, y0, period, octave);
      float c = lattice(x0, y0 + 1, period, octave);
      float d = lattice(x0 + 1, y0 + 1, period, octave);
      return MathHelper.lerp(fy, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
   }

   private static float lattice(int x, int y, int period, int octave) {
      return PeepSky.hash01(Math.floorMod(x, period) * 7919 + y * 104729 + octave * 15485863);
   }

   private static float[] fill(Float unitShape, int w, int h, int pad) {
      BufferedImage image = new BufferedImage(w, h, 2);
      Graphics2D g = smooth(image);
      g.translate(pad, pad);
      g.scale(w - pad * 2.0, h - pad * 2.0);
      g.setColor(Color.WHITE);
      g.fill(unitShape);
      g.dispose();
      return alpha(image);
   }

   private static Graphics2D smooth(BufferedImage image) {
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      return g;
   }

   private static float[] alpha(BufferedImage image) {
      int w = image.getWidth();
      int h = image.getHeight();
      int[] px = image.getRGB(0, 0, w, h, null, 0, w);
      float[] a = new float[px.length];

      for (int i = 0; i < a.length; i++) {
         a[i] = (px[i] >>> 24) / 255.0F;
      }

      return a;
   }

   private static float[] fadeBorder(float[] a, int w, int h, int border) {
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int d = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y));
            if (d < border) {
               a[y * w + x] = a[y * w + x] * ((float)d / border);
            }
         }
      }

      return a;
   }

   private static int[] white(float[] a) {
      int[] out = new int[a.length];

      for (int i = 0; i < a.length; i++) {
         out[i] = Math.round(MathHelper.clamp(a[i], 0.0F, 1.0F) * 255.0F) << 24 | 16777215;
      }

      return out;
   }

   private static float quad(float a, float b, float c, float t) {
      float s = 1.0F - t;
      return s * s * a + 2.0F * s * t * b + t * t * c;
   }

   private static float quadSlope(float a, float b, float c, float t) {
      return 2.0F * (1.0F - t) * (b - a) + 2.0F * t * (c - b);
   }

   private static float gauss(float d, float sigma) {
      return (float)Math.exp(-d * d / (2.0F * sigma * sigma));
   }

   private static float smooth(float from, float to, float x) {
      float t = MathHelper.clamp((x - from) / (to - from), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static int mix(int from, int to, float t) {
      return ColorHelper.lerp(MathHelper.clamp(t, 0.0F, 1.0F), from, to) & 16777215;
   }

   @Environment(EnvType.CLIENT)
   record Art(int width, int height, int[] argb) {
   }
}
