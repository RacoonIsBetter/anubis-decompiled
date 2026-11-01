package net.anubis.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.util.donut.DonutGoliaths;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class GoliathPickerScreen extends Screen {
   private static final float SQUARE = 7.0F;
   private static final float PAD = 14.0F;
   private static final float HEADER_H = 42.0F;
   private static final float INFO_H = 28.0F;
   private static final float BUTTON_W = 96.0F;
   private static final float BUTTON_H = 26.0F;
   private static final int HEADER_BAND = 251658239;
   private static final int WINDOW_FLAGS = 367;
   private final Set<DonutGoliaths.Goliath> picked;
   private final Consumer<Set<DonutGoliaths.Goliath>> onPick;
   private boolean confirmed;
   private boolean dragging;
   private boolean dragAdds;
   private DonutGoliaths.Goliath dragLast;

   public GoliathPickerScreen(Set<DonutGoliaths.Goliath> preselected, Consumer<Set<DonutGoliaths.Goliath>> onPick) {
      super(Text.literal("Goliath RTP"));
      this.picked = new LinkedHashSet<>(preselected);
      this.onPick = onPick;
   }

   @Override
   protected void init() {
      super.init();
      ImGuiManager.getInstance().activate(this::draw);
   }

   @Override
   public void removed() {
      super.removed();
      ImGuiManager.getInstance().deactivate();
      this.onPick.accept(this.confirmed ? Set.copyOf(this.picked) : null);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (ThemeStore.get().menuBlur.get()) {
         super.renderBackground(context, mouseX, mouseY, deltaTicks);
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      ImGuiManager.getInstance().requestCursor(context);
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (input.key() != 257 && input.key() != 335) {
         return super.keyPressed(input);
      }

      this.confirm();
      return true;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private void confirm() {
      if (!this.picked.isEmpty()) {
         this.confirmed = true;
         this.client.execute(this::close);
      }
   }

   private void draw() {
      float side = AnubisWidgets.s(252.0F);
      float pad = AnubisWidgets.s(14.0F);
      float headerH = AnubisWidgets.s(42.0F);
      float infoH = AnubisWidgets.s(28.0F);
      float rounding = AnubisWidgets.s(GuiPalette.rounding());
      ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() * 0.5F, ImGui.getIO().getDisplaySizeY() * 0.5F, 1, 0.5F, 0.5F);
      ImGui.pushStyleColor(2, GuiPalette.panelBg());
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, rounding);
      ImGui.pushStyleVar(4, 0.0F);
      if (ImGui.begin("##goliath_rtp", 367)) {
         float wx = ImGui.getWindowPosX();
         float wy = ImGui.getWindowPosY();
         float ww = side + pad * 2.0F;
         float wh = ImGui.getWindowHeight();
         ClickGuiRenderer.drawShadow(wx, wy, ww, wh, rounding, 4);
         ImDrawList dl = ImGui.getWindowDrawList();
         SurfaceBackground.draw(dl, ThemeStore.get().panels, wx, wy, ww, wh, rounding);
         boolean guard = HudChrome.needsTextGuard(ThemeStore.get().panels);
         drawHeader(dl, wx, wy, ww, headerH, rounding, guard);
         ImGui.dummy(ww, headerH);
         ImGui.setCursorPos(pad, headerH);
         DonutGoliaths.Goliath hover = this.drawMap(dl, side);
         ImFont small = ImGuiManager.getInstance().smallFont();
         String info = hover != null
            ? hover.label() + (hover.region() == 2 ? "  ·  no /rtp" : "")
            : (
               this.picked.isEmpty()
                  ? "Click or drag to pick goliaths"
                  : this.picked.size() + (this.picked.size() == 1 ? " goliath picked" : " goliaths picked")
            );
         float infoY = wy + headerH + side;
         AnubisWidgets.text(dl, small, wx + pad, infoY + (infoH - AnubisWidgets.lineHeight(small)) * 0.5F, -4542808, info, guard);
         float buttonW = AnubisWidgets.s(96.0F);
         float buttonH = AnubisWidgets.s(26.0F);
         float buttonsY = headerH + side + infoH;
         boolean pushed = ClickGuiRenderer.pushFont(small);
         ImGui.setCursorPos(pad, buttonsY);
         if (AnubisWidgets.chipButton("##goliath_clear", "Clear", buttonW, buttonH, false)) {
            this.picked.clear();
         }

         ImGui.setCursorPos(pad + side - buttonW, buttonsY);
         String go = this.picked.isEmpty() ? "Go" : "Go (" + this.picked.size() + ")";
         if (AnubisWidgets.chipButton("##goliath_go", go, buttonW, buttonH, !this.picked.isEmpty())) {
            this.confirm();
         }

         if (pushed) {
            ImGui.popFont();
         }

         ImGui.setCursorPos(0.0F, buttonsY + buttonH);
         ImGui.dummy(ww, pad);
      }

      ImGui.end();
      ImGui.popStyleVar(4);
      ImGui.popStyleColor();
   }

   private static void drawHeader(ImDrawList dl, float x, float y, float w, float h, float rounding, boolean guard) {
      float cap = Math.min(h, rounding + AnubisWidgets.s(2.0F));
      int top = AnubisWidgets.c(AnubisWidgets.withAlpha(251658239, 1.0F - cap / (2.0F * h)));
      int below = AnubisWidgets.c(AnubisWidgets.withAlpha(251658239, 1.0F - cap / h));
      int clear = AnubisWidgets.c(16777215);
      dl.addRectFilled(x, y, x + w, y + cap, top, rounding, 48);
      dl.addRectFilledMultiColor(x, y + cap, x + w, y + h, below, below, clear, clear);
      ImFont font = ImGuiManager.getInstance().categoryFont();
      float r = AnubisWidgets.s(7.0F);
      float cx = x + AnubisWidgets.s(14.0F) + r;
      float cy = y + h * 0.5F;
      AnubisWidgets.grid(dl, cx, cy, r, -4542808);
      AnubisWidgets.text(dl, font, cx + r + AnubisWidgets.s(9.0F), y + (h - AnubisWidgets.lineHeight(font)) * 0.5F, -461582, "Goliath RTP", guard);
   }

   private DonutGoliaths.Goliath drawMap(ImDrawList dl, float side) {
      float mapX = ImGui.getCursorScreenPosX();
      float mapY = ImGui.getCursorScreenPosY();
      ImGui.invisibleButton("##goliath_map", side, side);
      float square = side / 36.0F;
      DonutGoliaths.Goliath hover = ImGui.isItemHovered() ? goliathAt(ImGui.getMousePosX() - mapX, ImGui.getMousePosY() - mapY, square) : null;
      boolean pickable = hover != null && hover.region() != 2;
      if (ImGui.isItemClicked(0) && pickable) {
         this.dragAdds = !this.picked.contains(hover);
         this.toggle(hover);
         this.dragging = true;
         this.dragLast = hover;
      } else if (this.dragging && ImGui.isMouseDown(0)) {
         DonutGoliaths.Goliath under = goliathAt(ImGui.getMousePosX() - mapX, ImGui.getMousePosY() - mapY, square);
         if (under != null && under.region() != 2 && !under.equals(this.dragLast)) {
            this.toggle(under);
            this.dragLast = under;
         }
      }

      if (!ImGui.isMouseDown(0)) {
         this.dragging = false;
         this.dragLast = null;
      }

      float scale = ImGuiManager.getInstance().uiScale();
      float mapR = Math.min(AnubisWidgets.s(4.0F), square);
      boolean any = !this.picked.isEmpty();

      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         int color = RegionMapRenderer.regionColor(region);
         boolean europe = region == 2;
         int[][] goliaths = DonutGoliaths.CELLS[region];

         for (int index = 0; index < goliaths.length; index++) {
            DonutGoliaths.Goliath goliath = new DonutGoliaths.Goliath(region, index);
            boolean bright = !europe && (this.picked.contains(goliath) || goliath.equals(hover));
            float alpha = europe ? 0.3F : (bright ? 1.0F : (any ? 0.45F : 0.85F));
            RegionMapRenderer.tile(dl, mapX, mapY, square, goliaths[index], HudChrome.scaleAlpha(color, alpha), mapR);
         }
      }

      RegionMapRenderer.drawDividers(dl, mapX, mapY, square, 1.0F, scale);

      for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
         boolean europe = region == 2;
         int[][] goliaths = DonutGoliaths.CELLS[region];

         for (int index = 0; index < goliaths.length; index++) {
            int[] cell = goliaths[index];
            float x1 = mapX + cell[0] * square;
            float y1 = mapY + cell[1] * square;
            float x2 = mapX + cell[2] * square;
            float y2 = mapY + cell[3] * square;
            DonutGoliaths.Goliath goliath = new DonutGoliaths.Goliath(region, index);
            float inset = AnubisWidgets.s(0.75F);
            if (this.picked.contains(goliath)) {
               dl.addRect(x1 + inset, y1 + inset, x2 - inset, y2 - inset, -1, 0.0F, 0, AnubisWidgets.s(1.5F));
            } else if (!europe && goliath.equals(hover)) {
               dl.addRect(x1 + inset, y1 + inset, x2 - inset, y2 - inset, -1711276033, 0.0F, 0, AnubisWidgets.s(1.0F));
            }

            RegionMapRenderer.drawCenteredTileText(dl, index + 1, x1, y1, x2 - x1, y2 - y1, 1.0F, europe ? 0.5F : 1.0F, scale);
         }
      }

      if (this.client.player != null && this.client.world != null && this.client.world.getRegistryKey() != World.END) {
         double factor = this.client.world.getRegistryKey() == World.NETHER ? 8.0 : 1.0;
         double span = 450000.0;
         double fx = (this.client.player.getX() * factor + 225000.0) / span;
         double fz = (this.client.player.getZ() * factor + 225000.0) / span;
         if (fx >= 0.0 && fx < 1.0 && fz >= 0.0 && fz < 1.0) {
            float px = mapX + (float)(fx * side);
            float pz = mapY + (float)(fz * side);
            dl.addCircleFilled(px, pz, AnubisWidgets.s(3.0F), -13487361);
            dl.addCircle(px, pz, AnubisWidgets.s(3.0F), -1728053248, 16, AnubisWidgets.s(1.0F));
         }
      }

      return hover;
   }

   private void toggle(DonutGoliaths.Goliath goliath) {
      if (this.dragAdds) {
         this.picked.add(goliath);
      } else {
         this.picked.remove(goliath);
      }
   }

   private static DonutGoliaths.Goliath goliathAt(float u, float v, float square) {
      return !(u < 0.0F) && !(v < 0.0F) ? DonutGoliaths.at(u / square * 12500.0F - 225000.0F, v / square * 12500.0F - 225000.0F) : null;
   }
}
