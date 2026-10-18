package net.anubis.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import net.anubis.client.gui.EgyptDraw;
import net.anubis.client.gui.ThemeImages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class DuskEmblem {
   private static final String NAME = "ANUBIS CLIENT";
   private static final float NAME_TRACK = 6.0F;
   private static final float[] ANKH = new float[]{0.46F, 0.32285714F, 0.5371429F, 0.4457143F};
   private static final float[] LEFT_EYE = new float[]{0.40857142F, 0.43714285F, 0.4485714F, 0.47714287F};
   private static final float[] RIGHT_EYE = new float[]{0.55142856F, 0.4457143F, 0.5885714F, 0.48F};
   private static final int HALO_BLUR = 5;
   private static final int GLOW_BLUR = 3;
   private static final float CYCLE = 8.0F;
   private static final float FIRST_PASS = 1.1F;
   private static final float HEAD_PASS = 0.9F;
   private static final float NAME_START = 0.55F;
   private static final float NAME_PASS = 0.8F;
   private static final float BUTTON_START = 1.2F;
   private static final float BUTTON_STAGGER = 0.25F;
   private static final float BUTTON_PASS = 0.8F;
   private static final float EYES_AT = 4.2F;
   private static final float EYES_FOR = 0.7F;
   private static final float ANKH_BREATH = 3.5F;
   static final int LIGHT = -3633;

   private DuskEmblem() {
   }

   static void draw(MenuContext c, float cx, float cy, float size, float nameY) {
      float t = c.time();
      float cycle = cycle(t);
      head(c, cx, cy, size, t, cycle);
      name(c, cx, nameY, cycle);
   }

   private static float cycle(float t) {
      return t < 1.1F ? -1.0F : (t - 1.1F) % 8.0F;
   }

   static float buttonPass(MenuContext c, int n) {
      float cycle = cycle(c.time());
      if (cycle < 0.0F) {
         return -1.0F;
      }

      float p = (cycle - 1.2F - n * 0.25F) / 0.8F;
      return !(p < 0.0F) && !(p > 1.0F) ? p : -1.0F;
   }

   static float breath(float t) {
      return 0.5F - 0.5F * (float)Math.cos(t * Math.PI * 2.0 / 3.5);
   }

   private static void head(MenuContext c, float cx, float cy, float size, float t, float cycle) {
      ThemeImages.Texture head = c.head();
      if (head != null) {
         ImDrawList dl = c.bg();
         float in = c.intro(0.0F);
         size *= 0.96F + 0.04F * in;
         float x0 = cx - size * 0.5F;
         float y0 = cy - size * 0.5F;
         float px = size / head.width();
         ThemeImages.Texture halo = c.headBlur(5);
         float pad = 10.0F * px;
         c.image(dl, halo, x0 - pad, y0 - pad, x0 + size + pad, y0 + size + pad, -16777216, 0.7F * in);
         c.image(dl, head, x0, y0, x0 + size, y0 + size, -1, in);
         lightUp(c, ANKH, x0, y0, size, px, in * (0.2F + 0.5F * breath(t)));
         float eyes = cycle < 0.0F ? 0.0F : flash((cycle - 4.2F) / 0.7F);
         lightUp(c, LEFT_EYE, x0, y0, size, px, in * eyes);
         lightUp(c, RIGHT_EYE, x0, y0, size, px, in * eyes);
         if (cycle >= 0.0F && cycle < 0.9F) {
            sweep(c, x0, y0, size, smooth(cycle / 0.9F), in);
         }
      }
   }

   private static void lightUp(MenuContext c, float[] box, float x0, float y0, float size, float px, float amount) {
      if (!(amount <= 0.01F)) {
         ImDrawList dl = c.bg();
         float bx0 = x0 + box[0] * size;
         float by0 = y0 + box[1] * size;
         float bx1 = x0 + box[2] * size;
         float by1 = y0 + box[3] * size;
         ThemeImages.Texture glow = c.headBlur(3);
         float pad = 6.0F * px;
         dl.pushClipRect(bx0 - pad, by0 - pad, bx1 + pad, by1 + pad, true);
         c.image(dl, glow, x0 - pad, y0 - pad, x0 + size + pad, y0 + size + pad, -11654, 0.8F * amount);
         dl.popClipRect();
         dl.pushClipRect(bx0, by0, bx1, by1, true);
         c.image(dl, c.headMask(), x0, y0, x0 + size, y0 + size, -3633, 0.6F * amount);
         dl.popClipRect();
      }
   }

   private static void sweep(MenuContext c, float x0, float y0, float size, float progress, float alpha) {
      ThemeImages.Texture mask = c.headMask();
      if (mask != null) {
         ImDrawList dl = c.bg();
         float half = size * 0.14F;
         float lean = size * 0.35F;
         float top = x0 - half + progress * (size + half * 2.0F + lean);
         float y1 = y0 + size;
         float[] xs = new float[]{top - half, top, top + half, top - half - lean, top - lean, top + half - lean};
         int edge = EgyptDraw.col(-3633, 0.0F);
         int centre = EgyptDraw.col(-3633, 0.9F * alpha);
         dl.pushClipRect(x0, y0, x0 + size, y1, true);
         dl.pushTextureID(mask.id());
         dl.primReserve(12, 6);
         int first = dl.getVtxCurrentIdx();

         for (int i = 0; i < 6; i++) {
            float y = i < 3 ? y0 : y1;
            dl.primWriteVtx(xs[i], y, (xs[i] - x0) / size, (y - y0) / size, i % 3 == 1 ? centre : edge);
         }

         writeQuad(dl, first, first + 1, first + 4, first + 3);
         writeQuad(dl, first + 1, first + 2, first + 5, first + 4);
         dl.popTextureID();
         dl.popClipRect();
      }
   }

   private static void name(MenuContext c, float cx, float y, float cycle) {
      ImDrawList dl = c.bg();
      ImFont font = c.nameFont();
      float s = c.scale();
      float track = 6.0F * s;
      float width = c.spacedWidth(font, "ANUBIS CLIENT", track);
      float left = cx - width * 0.5F;
      float light = cycle < 0.0F ? -1.0F : (cycle - 0.55F) / 0.8F;
      float x = left;

      for (int i = 0; i < "ANUBIS CLIENT".length(); i++) {
         String glyph = String.valueOf("ANUBIS CLIENT".charAt(i));
         float w = c.textWidth(font, glyph);
         float a = c.intro(0.3F + i * 0.035F);
         float ly = y + (1.0F - a) * 6.0F * s;
         float at = (x + w * 0.5F - left) / width;
         float k = !(light < 0.0F) && !(light > 1.0F) ? smooth(Math.max(0.0F, 1.0F - Math.abs(light * 1.4F - 0.2F - at) / 0.16F)) : 0.0F;
         c.text(dl, font, x + s, ly + 1.5F * s, -16777216, 0.7F * a, glyph);
         c.text(dl, font, x, ly, EgyptDraw.mix(-871878, -3633, k), a, glyph);
         x += w + track;
      }
   }

   private static float flash(float x) {
      if (!(x <= 0.0F) && !(x >= 1.0F)) {
         return x < 0.15F ? smooth(x / 0.15F) : smooth(1.0F - (x - 0.15F) / 0.85F);
      } else {
         return 0.0F;
      }
   }

   static float smooth(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }

   private static void writeQuad(ImDrawList dl, int a, int b, int c, int d) {
      dl.primWriteIdx(a);
      dl.primWriteIdx(b);
      dl.primWriteIdx(c);
      dl.primWriteIdx(a);
      dl.primWriteIdx(c);
      dl.primWriteIdx(d);
   }
}
