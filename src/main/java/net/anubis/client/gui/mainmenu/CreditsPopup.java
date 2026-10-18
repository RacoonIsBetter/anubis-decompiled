package net.anubis.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.List;
import net.anubis.client.gui.EgyptDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CreditsPopup {
   private static final List<String> CREDITS = List.of("jeff", "autism", "Meteor Client");
   private static final float ROW_H = 34.0F;
   private static final float DIVIDER_HALF = 56.0F;
   private static final float DIAMOND = 3.0F;
   private final DuskWidgets ui = new DuskWidgets();
   private final MenuFrame frame = new MenuFrame();
   private DuskWidgets.Dialog dialog;
   private long openedAt;

   public void open() {
      this.openedAt = System.nanoTime();
      this.dialog = new DuskWidgets.Dialog("Credits", "").content(CREDITS.size() * 34.0F, this::drawContent).cancel("Close");
   }

   public boolean isOpen() {
      return this.dialog != null && !this.dialog.isClosed();
   }

   public boolean keyPressed(int key) {
      if (!this.isOpen()) {
         return false;
      }

      if (key == 256) {
         this.dialog.dismiss();
      }

      return true;
   }

   public void render(ImDrawList dl, float width, float height, float scale, float dt, float mouseX, float mouseY, boolean clicked) {
      if (this.isOpen()) {
         float time = (float)(System.nanoTime() - this.openedAt) / 1.0E9F;
         this.frame.begin(dl, dl, width, height, scale, time, dt, mouseX, mouseY, clicked);
         this.ui.begin(this.frame);
         this.ui.dialog(this.dialog);
      }
   }

   private void drawContent(DuskWidgets ui, DuskWidgets.Dialog dialog, float x, float y, float w, float h) {
      MenuFrame f = ui.frame();
      ImDrawList dl = f.bg();
      float s = f.scale();
      float a = ui.alpha();
      ImFont font = f.menuFont();
      float rowH = 34.0F * s;
      float cx = x + w * 0.5F;

      for (int i = 0; i < CREDITS.size(); i++) {
         String name = CREDITS.get(i);
         float top = y + i * rowH;
         f.text(dl, font, cx - f.textWidth(font, name) * 0.5F, top + (rowH - f.textHeight(font)) * 0.5F, -11654, a, name);
         if (i < CREDITS.size() - 1) {
            divider(dl, cx, top + rowH, s, a);
         }
      }
   }

   private static void divider(ImDrawList dl, float cx, float cy, float s, float a) {
      float half = 56.0F * s;
      float d = 3.0F * s;
      float gap = d * 2.0F;
      float th = Math.max(1.0F, s);
      int line = EgyptDraw.col(-871878, 0.4F * a);
      dl.addLine(cx - half, cy, cx - gap, cy, line, th);
      dl.addLine(cx + gap, cy, cx + half, cy, line, th);
      dl.addQuadFilled(cx, cy - d, cx + d, cy, cx, cy + d, cx - d, cy, EgyptDraw.col(-871878, 0.75F * a));
   }
}
