package net.anubis.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.impl.client.RegionMapModule;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.util.donut.DonutGoliaths;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class RegionMapRenderer {
   private static final float PAD = 10.0F;
   private static final float INFO_GAP = 8.0F;
   private static final float INFO_LINE_H = 22.0F;
   private static final float LEGEND_GAP = 4.0F;
   private static final float LEGEND_ROW_H = 16.0F;
   private static final float LEGEND_SWATCH = 9.0F;
   private static final float LABEL_GAP = 8.0F;
   private static final int MIN_ID_PX = 6;
   private static final String MINUS = "−";
   private static final String COORDINATE_LABEL = "Coordinate";
   private static final String REGION_LABEL = "Region";
   private static final String NO_REGION = "—";
   private static final int[] REGION_COLORS = new int[]{-10236257, -10246656, -1012536, -4559313, -1397425, -12401931, -16545540};
   private static final int[] LEGEND_ORDER = new int[]{0, 1, 2, 4, 3, 5, 6};
   private static final float PIN_SIZE = 6.0F;
   private static final float MAP_ROUNDING = 4.0F;
   private static final int GOLIATH_DIVIDER = 771751936;
   private static final int REGION_DIVIDER = 1191182336;
   private static final List<int[]> GOLIATH_LINES = new ArrayList<>();
   private static final List<int[]> REGION_LINES = new ArrayList<>();

   private RegionMapRenderer() {
   }

   private static void dividerRuns(int line, boolean vertical) {
      int grid = 36;
      int start = 0;
      int kind = dividerKind(line, 0, vertical);

      for (int along = 1; along <= grid; along++) {
         int next = along < grid ? dividerKind(line, along, vertical) : -1;
         if (next != kind) {
            if (kind == 2) {
               REGION_LINES.add(new int[]{vertical ? 1 : 0, line, start, along});
            } else if (kind == 1) {
               GOLIATH_LINES.add(new int[]{vertical ? 1 : 0, line, start, along});
            }

            kind = next;
            start = along;
         }
      }
   }

   private static int dividerKind(int line, int along, boolean vertical) {
      int a = vertical ? DonutGoliaths.square(line - 1, along) : DonutGoliaths.square(along, line - 1);
      int b = vertical ? DonutGoliaths.square(line, along) : DonutGoliaths.square(along, line);
      if (a < 0 == b < 0 && a / 1000 == b / 1000) {
         return a != b ? 1 : 0;
      } else {
         return 2;
      }
   }

   public static RegionMapRenderer.Size measureSize(RegionMapModule module, float scale) {
      float gridSize = gridSize(module, scale);
      float pad = 10.0F * scale;
      float contentW = Math.max(gridSize, measureInfoWidth(module, scale));
      return new RegionMapRenderer.Size(Math.round(contentW + pad * 2.0F), Math.round(gridSize + pad * 2.0F + 8.0F * scale + measureInfoHeight(module) * scale));
   }

   private static float gridSize(RegionMapModule module, float scale) {
      return 36.0F * module.squareSize() * scale;
   }

   private static float measureInfoWidth(RegionMapModule module, float scale) {
      ImGuiManager m = ImGuiManager.getInstance();
      int textPx = HudChrome.px(17.0F, scale);
      int labelPx = HudChrome.px(14.0F, scale);
      float labelCol = labelColumn(module, m.hudLabelFont(), labelPx) + 8.0F * scale;
      float values = module.showCoordinates() ? HudChrome.tabularWidth(m.hudTextFont(), textPx, "−000000, −000000") : 0.0F;
      float legend = 0.0F;

      for (String name : DonutGoliaths.REGIONS) {
         values = Math.max(values, HudChrome.width(m.hudTextFont(), textPx, "00  ·  " + name));
         legend = Math.max(legend, 15.0F * scale + HudChrome.width(m.hudLabelFont(), labelPx, name));
      }

      return (float)Math.ceil(Math.max(labelCol + values, module.showLegend() ? legend : 0.0F));
   }

   public static void render(ImDrawList dl, RegionMapModule module, HudLayoutStore.HudElementState layout) {
      render(dl, module, layout, 1.0F);
   }

   public static void render(ImDrawList dl, RegionMapModule module, HudLayoutStore.HudElementState layout, float fade) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && dl != null && module != null && layout != null) {
         float scale = HudRenderer.scaleOf(layout);
         float square = module.squareSize() * scale;
         float pad = 10.0F * scale;
         float gridSize = gridSize(module, scale);
         float x = Math.round(layout.x);
         float y = Math.round(layout.y);
         float r = HudRenderer.hudRounding() * scale;
         float alpha = module.opacity() * fade;
         HudChrome.card(dl, HudRenderer.hudSurface(layout), x, y, layout.width, layout.height, r, alpha, module.shadow(), scale);
         float gridX = Math.round(x + (layout.width - gridSize) * 0.5F);
         float gridY = y + pad;
         float mapR = Math.min(4.0F * scale, square);

         for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
            int color = HudChrome.scaleAlpha(REGION_COLORS[region], alpha);

            for (int[] c : DonutGoliaths.CELLS[region]) {
               tile(dl, gridX, gridY, square, c, color, mapR);
            }
         }

         drawDividers(dl, gridX, gridY, square, alpha, scale);
         if (module.showRegionIds()) {
            for (int[][] goliaths : DonutGoliaths.CELLS) {
               for (int index = 0; index < goliaths.length; index++) {
                  int[] c = goliaths[index];
                  drawCenteredTileText(
                     dl,
                     index + 1,
                     gridX + c[0] * square,
                     gridY + c[1] * square,
                     (c[2] - c[0]) * square,
                     (c[3] - c[1]) * square,
                     module.textScale(),
                     alpha,
                     scale
                  );
               }
            }
         }

         renderPlayerPin(dl, module, mc, player, gridX, gridY, gridSize, alpha, scale);
         renderInfoPanel(dl, module, mc, player, x + pad, gridY + gridSize + 8.0F * scale, alpha, scale);
      }
   }

   static int regionColor(int region) {
      return REGION_COLORS[region];
   }

   static void tile(ImDrawList dl, float gridX, float gridY, float square, int[] c, int color, float mapRounding) {
      int grid = 36;
      int corners = 0;
      if (c[0] == 0 && c[1] == 0) {
         corners |= 16;
      }

      if (c[2] == grid && c[1] == 0) {
         corners |= 32;
      }

      if (c[0] == 0 && c[3] == grid) {
         corners |= 64;
      }

      if (c[2] == grid && c[3] == grid) {
         corners |= 128;
      }

      float x1 = gridX + c[0] * square;
      float y1 = gridY + c[1] * square;
      float x2 = gridX + c[2] * square;
      float y2 = gridY + c[3] * square;
      if (corners == 0) {
         dl.addRectFilled(x1, y1, x2, y2, color);
      } else {
         dl.addRectFilled(x1, y1, x2, y2, color, mapRounding, corners);
      }
   }

   static void drawDividers(ImDrawList dl, float gridX, float gridY, float square, float alpha, float scale) {
      float width = Math.max(1.0F, scale);
      drawRuns(dl, GOLIATH_LINES, gridX, gridY, square, width, HudChrome.scaleAlpha(771751936, alpha));
      drawRuns(dl, REGION_LINES, gridX, gridY, square, width, HudChrome.scaleAlpha(1191182336, alpha));
   }

   private static void drawRuns(ImDrawList dl, List<int[]> runs, float gridX, float gridY, float square, float width, int color) {
      float half = width * 0.5F;

      for (int[] run : runs) {
         if (run[0] == 1) {
            float lx = gridX + run[1] * square;
            dl.addRectFilled(lx - half, gridY + run[2] * square, lx + half, gridY + run[3] * square, color);
         } else {
            float ly = gridY + run[1] * square;
            dl.addRectFilled(gridX + run[2] * square, ly - half, gridX + run[3] * square, ly + half, color);
         }
      }
   }

   static void drawCenteredTileText(ImDrawList dl, int number, float tx, float ty, float w, float h, float textScale, float alpha, float scale) {
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      String label = String.valueOf(number);
      int fontPx = Math.max(6, HudChrome.px(14.0F * textScale * 0.85F, scale));
      float roomW = w - 2.0F * scale;
      float textW = HudChrome.width(font, fontPx, "00");
      if (textW > roomW) {
         fontPx = (int)Math.floor(fontPx * roomW / textW);
      }

      fontPx = Math.min(fontPx, (int)Math.floor(h - scale));
      if (fontPx >= 6) {
         float lx = tx + (w - HudChrome.width(font, fontPx, label)) * 0.5F;
         float ly = ty + (h - fontPx) * 0.5F;
         HudChrome.text(dl, font, fontPx, lx, ly, HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, alpha), label, true);
      }
   }

   private static void renderPlayerPin(
      ImDrawList dl, RegionMapModule module, MinecraftClient mc, ClientPlayerEntity player, float gridX, float gridY, float gridSize, float alpha, float scale
   ) {
      if (inOverworld(mc)) {
         double span = 450000.0;
         double fx = (player.getX() + 225000.0) / span;
         double fz = (player.getZ() + 225000.0) / span;
         if (!(fx < 0.0) && !(fx >= 1.0) && !(fz < 0.0) && !(fz >= 1.0)) {
            float centerX = gridX + (float)(fx * gridSize);
            float centerY = gridY + (float)(fz * gridSize);
            double angle = Math.toRadians(-player.getYaw() - 90.0);
            float arrowSize = 6.0F * module.playerScale() * scale;
            int color = HudChrome.scaleAlpha(toAbgr(module.playerIndicatorColor()), alpha);
            float tipX = centerX + (float)(Math.cos(angle) * arrowSize);
            float tipY = centerY - (float)(Math.sin(angle) * arrowSize);
            float leftX = centerX + (float)(Math.cos(angle + Math.toRadians(135.0)) * arrowSize);
            float leftY = centerY - (float)(Math.sin(angle + Math.toRadians(135.0)) * arrowSize);
            float rightX = centerX + (float)(Math.cos(angle - Math.toRadians(135.0)) * arrowSize);
            float rightY = centerY - (float)(Math.sin(angle - Math.toRadians(135.0)) * arrowSize);
            dl.addTriangleFilled(tipX, tipY, leftX, leftY, rightX, rightY, color);
            dl.addTriangle(tipX, tipY, leftX, leftY, rightX, rightY, HudChrome.scaleAlpha(-1728053248, alpha), 1.0F);
         }
      }
   }

   private static void renderInfoPanel(
      ImDrawList dl, RegionMapModule module, MinecraftClient mc, ClientPlayerEntity player, float x, float y, float alpha, float scale
   ) {
      ImGuiManager m = ImGuiManager.getInstance();
      ImFont textFont = m.hudTextFont();
      ImFont labelFont = m.hudLabelFont();
      int textPx = HudChrome.px(17.0F, scale);
      int labelPx = HudChrome.px(14.0F, scale);
      float labelOffset = HudChrome.baselineOffset(textFont, textPx, labelFont, labelPx);
      float valueX = x + labelColumn(module, labelFont, labelPx) + 8.0F * scale;
      float lineH = 22.0F * scale;
      float textInset = (float)Math.floor((lineH - textPx) * 0.5F);
      float lineTop = y + textInset;
      if (module.showCoordinates()) {
         HudChrome.text(dl, labelFont, labelPx, x, lineTop + labelOffset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, alpha), "Coordinate", false);
         HudChrome.tabular(
            dl,
            textFont,
            textPx,
            valueX,
            lineTop,
            HudChrome.scaleAlpha(HudChrome.TEXT_VALUE, alpha),
            number((long)Math.floor(player.getX())) + ", " + number((long)Math.floor(player.getZ())),
            false
         );
         lineTop += lineH;
      }

      DonutGoliaths.Goliath here = inOverworld(mc) ? DonutGoliaths.at(player.getX(), player.getZ()) : null;
      String region = here == null ? "—" : here.index() + 1 + "  ·  " + DonutGoliaths.REGIONS[here.region()];
      HudChrome.text(dl, labelFont, labelPx, x, lineTop + labelOffset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, alpha), "Region", false);
      HudChrome.text(dl, textFont, textPx, valueX, lineTop, HudChrome.scaleAlpha(HudChrome.TEXT_NAME, alpha), region, false);
      if (module.showLegend()) {
         float legendY = y + lineH * infoLines(module) + 4.0F * scale;
         float rowH = 16.0F * scale;
         float swatch = 9.0F * scale;

         for (int i = 0; i < LEGEND_ORDER.length; i++) {
            int regionIndex = LEGEND_ORDER[i];
            float rowY = legendY + i * rowH;
            float swatchY = rowY + (rowH - swatch) * 0.5F;
            dl.addRectFilled(x, swatchY, x + swatch, swatchY + swatch, HudChrome.scaleAlpha(REGION_COLORS[regionIndex], alpha), 2.0F * scale);
            HudChrome.text(
               dl,
               labelFont,
               labelPx,
               x + swatch + 6.0F * scale,
               rowY + (float)Math.floor((rowH - labelPx) * 0.5F),
               HudChrome.scaleAlpha(HudChrome.TEXT_NAME, alpha),
               DonutGoliaths.REGIONS[regionIndex],
               false
            );
         }
      }
   }

   private static boolean inOverworld(MinecraftClient mc) {
      return mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD;
   }

   private static int infoLines(RegionMapModule module) {
      return module.showCoordinates() ? 2 : 1;
   }

   private static float labelColumn(RegionMapModule module, ImFont labelFont, int labelPx) {
      float region = HudChrome.width(labelFont, labelPx, "Region");
      return module.showCoordinates() ? Math.max(HudChrome.width(labelFont, labelPx, "Coordinate"), region) : region;
   }

   private static float measureInfoHeight(RegionMapModule module) {
      float height = 22.0F * infoLines(module);
      if (module.showLegend()) {
         height += 4.0F + LEGEND_ORDER.length * 16.0F;
      }

      return height;
   }

   private static String number(long value) {
      return value < 0L ? "−" + -value : Long.toString(value);
   }

   private static int toAbgr(ColorSetting color) {
      return GuiPalette.abgr(color.argb());
   }

   static {
      for (int line = 1; line < 36; line++) {
         dividerRuns(line, true);
         dividerRuns(line, false);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Size(float width, float height) {
   }
}
