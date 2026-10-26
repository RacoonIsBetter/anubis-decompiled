package net.anubis.client.basefinder;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.List;
import java.util.Locale;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/** Draws the Base Finder panel and the hoverable reason dots. All colours here are ImGui ABGR. */
@Environment(EnvType.CLIENT)
public final class BaseFinderHud {
   private static final int TEXT = 0xFFF5F5F5;
   private static final int MUTED = 0xFFA3A3A3;
   private static final int BORDER = 0x1AFFFFFF;
   private static final int SHADOW = 0x40000000;
   private static final float HOVER_RADIUS = 14.0F;

   public void render(ImDrawList dl, float screenW, float screenH, List<BaseFinderEntry> entries, Style style) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ImGuiManager fonts = ImGuiManager.getInstance();
      ImFont body = fonts.hudTextFont();
      ImFont small = fonts.hudLabelFont();
      if (style.dots()) {
         this.dots(dl, mc, screenW, screenH, entries, body, small, style.scale());
      }

      if (style.panel()) {
         this.panel(dl, entries, style, body, small);
      }
   }

   private void dots(ImDrawList dl, MinecraftClient mc, float screenW, float screenH, List<BaseFinderEntry> entries, ImFont body, ImFont small, float scale) {
      float cx = screenW * 0.5F;
      float cy = screenH * 0.5F;
      BaseFinderEntry hovered = null;
      float hoveredX = 0.0F;
      float hoveredY = 0.0F;
      float best = HOVER_RADIUS * scale;

      for (BaseFinderEntry entry : entries) {
         float[] p = WorldLabel.project(mc, entry.anchor(), screenW, screenH);
         if (p == null) {
            continue;
         }

         int color = GuiPalette.abgr(entry.argb());
         float r = 3.5F * scale;
         dl.addCircleFilled(p[0], p[1], r + 1.5F * scale, SHADOW, 16);
         dl.addCircleFilled(p[0], p[1], r, color, 16);
         float d = (float)Math.hypot(p[0] - cx, p[1] - cy);
         if (d < best) {
            best = d;
            hovered = entry;
            hoveredX = p[0];
            hoveredY = p[1];
         }
      }

      if (hovered != null) {
         int color = GuiPalette.abgr(hovered.argb());
         dl.addCircle(hoveredX, hoveredY, 7.0F * scale, color, 20, 1.5F * scale);
         this.tooltip(dl, hovered, hoveredX + 12.0F * scale, hoveredY - 10.0F * scale, screenW, screenH, body, small, scale);
      }
   }

   private void tooltip(ImDrawList dl, BaseFinderEntry entry, float x, float y, float screenW, float screenH, ImFont body, ImFont small, float scale) {
      int bodyPx = px(body, scale);
      int smallPx = px(small, scale);
      float pad = 8.0F * scale;
      float line = smallPx + 3.0F * scale;
      String title = percent(entry.probability()) + " base chance";
      String where = entry.blockX() + ", " + entry.blockZ() + "  ·  " + Math.round(entry.distance()) + "m";
      int shown = Math.min(entry.reasons().size(), 6);
      float w = Math.max(width(body, bodyPx, title), width(small, smallPx, where));

      for (int i = 0; i < shown; i++) {
         w = Math.max(w, width(small, smallPx, entry.reasons().get(i).text()) + 10.0F * scale);
      }

      w += pad * 2.0F;
      float h = pad * 2.0F + bodyPx + 4.0F * scale + line * (1 + shown);
      if (x + w > screenW - 4.0F) {
         x = x - w - 24.0F * scale;
      }

      y = Math.max(4.0F, Math.min(y, screenH - h - 4.0F));
      this.card(dl, x, y, w, h, scale);
      int color = GuiPalette.abgr(entry.argb());
      float ty = y + pad;
      dl.addText(body, bodyPx, x + pad, ty, color, title);
      ty += bodyPx + 4.0F * scale;
      dl.addText(small, smallPx, x + pad, ty, MUTED, where);
      ty += line;

      for (int i = 0; i < shown; i++) {
         dl.addCircleFilled(x + pad + 2.5F * scale, ty + smallPx * 0.55F, 2.0F * scale, color, 8);
         dl.addText(small, smallPx, x + pad + 10.0F * scale, ty, TEXT, entry.reasons().get(i).text());
         ty += line;
      }
   }

   private void panel(ImDrawList dl, List<BaseFinderEntry> entries, Style style, ImFont body, ImFont small) {
      float s = style.scale();
      int bodyPx = px(body, s);
      int smallPx = px(small, s);
      float pad = 9.0F * s;
      float x = style.x();
      float y = style.y();
      float w = 236.0F * s;
      float rowH = bodyPx + smallPx + 8.0F * s;
      int rows = Math.min(entries.size(), style.rows());
      float headerH = bodyPx + 10.0F * s;
      float h = pad * 2.0F + headerH + (rows == 0 ? smallPx + 4.0F * s : rows * rowH);
      this.card(dl, x, y, w, h, s);
      float ty = y + pad;
      int accent = GuiPalette.accent();
      dl.addCircleFilled(x + pad + 3.0F * s, ty + bodyPx * 0.5F, 3.0F * s, accent, 12);
      dl.addText(body, bodyPx, x + pad + 11.0F * s, ty, TEXT, "Base Finder");
      String count = entries.isEmpty() ? "scanning" : entries.size() + (entries.size() == 1 ? " chunk" : " chunks");
      dl.addText(small, smallPx, x + w - pad - width(small, smallPx, count), ty + (bodyPx - smallPx) * 0.5F, MUTED, count);
      ty += headerH;
      dl.addRectFilled(x + pad, ty - 5.0F * s, x + w - pad, ty - 5.0F * s + 1.0F, BORDER);
      if (rows == 0) {
         dl.addText(small, smallPx, x + pad, ty, MUTED, "No suspicious chunks yet");
         return;
      }

      for (int i = 0; i < rows; i++) {
         BaseFinderEntry entry = entries.get(i);
         int color = GuiPalette.abgr(entry.argb());
         float pillW = 40.0F * s;
         float pillH = bodyPx + 4.0F * s;
         float py = ty + (rowH - pillH) * 0.5F - 2.0F * s;
         dl.addRectFilled(x + pad, py, x + pad + pillW, py + pillH, withAlpha(color, 0x38), 5.0F * s);
         String pct = percent(entry.probability());
         dl.addText(body, bodyPx, x + pad + (pillW - width(body, bodyPx, pct)) * 0.5F, py + 2.0F * s, color, pct);
         float textX = x + pad + pillW + 8.0F * s;
         String coords = entry.blockX() + ", " + entry.blockZ();
         dl.addText(body, bodyPx, textX, ty, TEXT, coords);
         String dist = Math.round(entry.distance()) + "m";
         float distW = width(small, smallPx, dist);
         float arrowX = x + w - pad - 5.0F * s;
         dl.addText(small, smallPx, arrowX - 9.0F * s - distW, ty + (bodyPx - smallPx) * 0.5F, MUTED, dist);
         this.arrow(dl, arrowX, ty + bodyPx * 0.5F, 4.5F * s, entry.bearing(), MUTED);
         float maxReason = x + w - pad - textX;
         dl.addText(small, smallPx, textX, ty + bodyPx + 2.0F * s, MUTED, fit(small, smallPx, entry.topReason(), maxReason));
         ty += rowH;
      }
   }

   private void card(ImDrawList dl, float x, float y, float w, float h, float scale) {
      float r = Math.max(4.0F, GuiPalette.rounding() * scale);
      dl.addRectFilled(x - 1.0F, y + 1.0F, x + w + 1.0F, y + h + 3.0F * scale, SHADOW, r + 1.0F);
      dl.addRectFilled(x, y, x + w, y + h, GuiPalette.cardBg(), r);
      dl.addRect(x, y, x + w, y + h, BORDER, r, 0, 1.0F);
   }

   private void arrow(ImDrawList dl, float cx, float cy, float size, float bearingDegrees, int color) {
      double a = Math.toRadians(bearingDegrees);
      float sin = (float)Math.sin(a);
      float cos = (float)Math.cos(a);
      float tipX = cx + sin * size;
      float tipY = cy - cos * size;
      float leftX = cx + (-cos * 0.75F - sin * 0.8F) * size;
      float leftY = cy + (-sin * 0.75F + cos * 0.8F) * size;
      float rightX = cx + (cos * 0.75F - sin * 0.8F) * size;
      float rightY = cy + (sin * 0.75F + cos * 0.8F) * size;
      dl.addTriangleFilled(tipX, tipY, leftX, leftY, rightX, rightY, color);
   }

   private static String percent(float p) {
      return String.format(Locale.ROOT, "%d%%", Math.round(p * 100.0F));
   }

   private static String fit(ImFont font, int px, String text, float maxWidth) {
      if (width(font, px, text) <= maxWidth) {
         return text;
      }

      String cut = text;
      while (cut.length() > 1 && width(font, px, cut + "…") > maxWidth) {
         cut = cut.substring(0, cut.length() - 1);
      }

      return cut + "…";
   }

   private static float width(ImFont font, int px, String text) {
      return font != null && !text.isEmpty() ? font.calcTextSizeA(px, Float.MAX_VALUE, 0.0F, text).x : text.length() * px * 0.55F;
   }

   private static int px(ImFont font, float scale) {
      return Math.max(8, Math.round((font != null ? font.getFontSize() : 13.0F) * scale));
   }

   private static int withAlpha(int abgr, int alpha) {
      return abgr & 0x00FFFFFF | alpha << 24;
   }

   @Environment(EnvType.CLIENT)
   public record Style(boolean panel, boolean dots, float x, float y, int rows, float scale) {
   }
}
