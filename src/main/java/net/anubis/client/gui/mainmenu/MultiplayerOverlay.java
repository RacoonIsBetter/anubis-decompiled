package net.anubis.client.gui.mainmenu;

import com.example.client.mixin.AccessorMenusJoinMultiplayerScreen;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.anubis.client.account.Account;
import net.anubis.client.account.Accounts;
import net.anubis.client.gui.EgyptDraw;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.proxy.Proxies;
import net.anubis.client.proxy.Proxy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;

@Environment(EnvType.CLIENT)
public final class MultiplayerOverlay {
   private static final int HEADER_HEIGHT = 33;
   private static final float NOWHERE = -10000.0F;
   private static final float BUTTON_H = 62.0F;
   private static final float ICON = 34.0F;
   private static final float MARGIN = 16.0F;
   private final MultiplayerScreen screen;
   private final ConnectionsPanel panel;
   private final DuskWidgets ui = new DuskWidgets();
   private final MenuFrame frame = new MenuFrame();
   private long shownAt;
   private boolean overButton;

   public MultiplayerOverlay(MultiplayerScreen screen) {
      this.screen = screen;
      MinecraftClient mc = MinecraftClient.getInstance();
      this.panel = new ConnectionsPanel(() -> mc.send(((AccessorMenusJoinMultiplayerScreen)screen)::anubis$refreshServerList));
   }

   public void show() {
      this.shownAt = System.nanoTime();
      ImGuiManager.getInstance().activate(this::render);
   }

   public void hide() {
      ImGuiManager.getInstance().deactivate();
   }

   public boolean takesPointer() {
      return this.overButton || this.panel.isOpen();
   }

   public boolean keyPressed(int key) {
      return this.panel.keyPressed(key);
   }

   private void render() {
      ImGuiIO io = ImGui.getIO();
      float width = io.getDisplaySizeX();
      float height = io.getDisplaySizeY();
      float scale = ImGuiManager.getInstance().uiScale();
      float dt = Math.max(0.001F, io.getDeltaTime());
      boolean free = !this.panel.isOpen();
      this.frame
         .begin(
            ImGui.getBackgroundDrawList(),
            ImGui.getForegroundDrawList(),
            width,
            height,
            scale,
            (float)(System.nanoTime() - this.shownAt) / 1.0E9F,
            dt,
            free ? io.getMousePosX() : -10000.0F,
            free ? io.getMousePosY() : -10000.0F,
            free && ImGui.isMouseClicked(0)
         );
      this.ui.begin(this.frame);

      try {
         this.header();
      } finally {
         this.ui.end();
      }

      this.panel.render(width, height, scale, dt);
   }

   private void header() {
      Window window = MinecraftClient.getInstance().getWindow();
      double gui = window.getScaleFactor();
      float s = this.frame.scale();
      float a = this.frame.intro(0.0F);
      float h = 62.0F * s;
      float margin = 16.0F * s;
      float header = (float)(33.0 * gui);
      float y = Math.round((header - h) * 0.5F);
      ImFont font = this.frame.menuFont();
      float w = Math.max(this.frame.textWidth(font, "Accounts"), this.frame.textWidth(font, "Proxies")) + 82.0F * s;
      Proxy proxy = Proxies.get().enabled();
      float x = margin;
      boolean over = this.button(ConnectionsPanel.Tab.ACCOUNTS, "Accounts", null, x, y, w, h, s, a);
      over |= this.button(ConnectionsPanel.Tab.PROXIES, "Proxies", proxy, x + w + 8.0F * s, y, w, h, s, a);
      this.overButton = over;
      float titleRight = (float)((window.getScaledWidth() + MinecraftClient.getInstance().textRenderer.getWidth(this.screen.getTitle())) / 2.0 * gui);
      float right = this.frame.width() - margin;
      float room = right - titleRight - 3.0F * margin;
      if (!(room < 40.0F * s)) {
         ImFont nameFont = this.frame.menuFont();
         ImFont routeFont = this.frame.listFont();
         float lineGap = 4.0F * s;
         float total = this.frame.textHeight(nameFont) + lineGap + this.frame.textHeight(routeFont);
         float ty = Math.round((header - total) * 0.5F);
         String name = DuskWidgets.fit(nameFont, this.frame.displayName(), room);
         String route = DuskWidgets.fit(routeFont, proxy != null ? proxy.displayAddress() : "No proxy", room);
         ImDrawList dl = this.frame.bg();
         this.frame.text(dl, nameFont, right - this.frame.textWidth(nameFont, name), ty, -923950, a, name);
         this.frame
            .text(
               dl,
               routeFont,
               right - this.frame.textWidth(routeFont, route),
               ty + this.frame.textHeight(nameFont) + lineGap,
               proxy != null ? -1326998 : -7372954,
               a,
               route
            );
      }
   }

   private boolean button(ConnectionsPanel.Tab tab, String label, Proxy proxy, float x, float y, float w, float h, float s, float a) {
      ImDrawList dl = this.frame.bg();
      boolean over = this.ui.hovered(x, y, w, h);
      if (this.ui.pressed(x, y, w, h)) {
         this.panel.open(tab);
      }

      float k = this.ui.hover(tab, over);
      dl.addRectFilled(x, y, x + w, y + h, EgyptDraw.col(-15461610, 0.88F * a));
      if (k > 0.01F) {
         dl.addRectFilled(x, y, x + w, y + h, EgyptDraw.col(-923950, 0.06F * k * a));
      }

      dl.addRect(x, y, x + w, y + h, EgyptDraw.col(EgyptDraw.mix(-923950, -871878, k), (0.18F + 0.4F * k) * a), 0.0F, 0, Math.max(1.0F, s));
      float icon = 34.0F * s;
      float ix = x + 16.0F * s;
      float iy = y + (h - icon) * 0.5F;
      if (tab == ConnectionsPanel.Tab.ACCOUNTS) {
         Account active = Accounts.get().active();
         if (active != null) {
            PlayerFaces.draw(dl, active, ix, iy, icon, s, a);
         } else {
            PlayerFaces.draw(dl, MinecraftClient.getInstance().getSession().getUuidOrNull(), false, ix, iy, icon, s, a);
         }
      } else {
         ConnectionsPanel.proxyDot(dl, ix + icon * 0.5F, iy + icon * 0.5F, proxy != null, 2.1F * s, a);
      }

      ImFont font = this.frame.menuFont();
      this.frame.text(dl, font, ix + icon + 12.0F * s, y + (h - this.frame.textHeight(font)) * 0.5F, EgyptDraw.mix(-923950, -11654, k), a, label);
      return over;
   }
}
