package net.anubis.client.basefinder;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.List;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

/** Sound Detector's history panel: the most recent sounds heard out of sight, newest first. Colours are ImGui ABGR. */
@Environment(EnvType.CLIENT)
public final class SoundHistoryHud {
   private static final int TEXT = 0xFFF5F5F5;
   private static final int MUTED = 0xFFA3A3A3;
   private static final int BORDER = 0x1AFFFFFF;
   private static final int SHADOW = 0x40000000;

   public void render(ImDrawList dl, List<SoundHistoryHud.Row> rows, float x, float y, float scale, String status) {
      ImGuiManager fonts = ImGuiManager.getInstance();
      ImFont body = fonts.hudTextFont();
      ImFont small = fonts.hudLabelFont();
      int bodyPx = px(body, scale);
      int smallPx = px(small, scale);
      float pad = 9.0F * scale;
      float w = 260.0F * scale;
      float headerH = bodyPx + 10.0F * scale;
      float rowH = smallPx * 2 + 7.0F * scale;
      float h = pad * 2.0F + headerH + (rows.isEmpty() ? smallPx + 4.0F * scale : rows.size() * rowH);
      float r = Math.max(4.0F, GuiPalette.rounding() * scale);
      dl.addRectFilled(x - 1.0F, y + 1.0F, x + w + 1.0F, y + h + 3.0F * scale, SHADOW, r + 1.0F);
      dl.addRectFilled(x, y, x + w, y + h, GuiPalette.cardBg(), r);
      dl.addRect(x, y, x + w, y + h, BORDER, r, 0, 1.0F);
      float ty = y + pad;
      dl.addCircleFilled(x + pad + 3.0F * scale, ty + bodyPx * 0.5F, 3.0F * scale, GuiPalette.accent(), 12);
      dl.addText(body, bodyPx, x + pad + 11.0F * scale, ty, TEXT, "Sound Detector");
      dl.addText(small, smallPx, x + w - pad - width(small, smallPx, status), ty + (bodyPx - smallPx) * 0.5F, MUTED, status);
      ty += headerH;
      dl.addRectFilled(x + pad, ty - 5.0F * scale, x + w - pad, ty - 5.0F * scale + 1.0F, BORDER);
      if (rows.isEmpty()) {
         dl.addText(small, smallPx, x + pad, ty, MUTED, "Nothing heard out of sight yet");
         return;
      }

      for (SoundHistoryHud.Row row : rows) {
         int color = GuiPalette.abgr(row.argb());
         dl.addCircleFilled(x + pad + 3.0F * scale, ty + smallPx * 0.55F, 3.0F * scale, color, 10);
         String title = row.what() + (row.count() > 1 ? "  x" + row.count() : "");
         dl.addText(small, smallPx, x + pad + 11.0F * scale, ty, TEXT, fit(small, smallPx, title, w - pad * 2.0F - 11.0F * scale));
         String where = row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ() + "  ·  " + row.ago() + "  ·  " + row.distance() + "m";
         dl.addText(small, smallPx, x + pad + 11.0F * scale, ty + smallPx + 2.0F * scale, MUTED, where);
         ty += rowH;
      }
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

   /** One panel line; {@code argb} is the kind's colour. */
   @Environment(EnvType.CLIENT)
   public record Row(String what, BlockPos pos, int count, String ago, int distance, int argb) {
   }
}
