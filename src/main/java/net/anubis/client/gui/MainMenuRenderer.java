package net.anubis.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.anubis.client.addon.MenuPanels;
import net.anubis.client.gui.mainmenu.CommunityPopup;
import net.anubis.client.gui.mainmenu.CreditsPopup;
import net.anubis.client.gui.mainmenu.DuskMenu;
import net.anubis.client.gui.mainmenu.MenuContext;
import net.anubis.client.gui.mainmenu.OfficialBuildBanner;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class MainMenuRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final float NOWHERE = -10000.0F;
   private final MenuContext context;
   private final CommunityPopup popup = new CommunityPopup();
   private final MenuPanels.Panel accounts = MenuPanels.newAccountsPanel();
   private final CreditsPopup credits = new CreditsPopup();
   private long openedAt = System.nanoTime();
   private boolean failed;

   MainMenuRenderer(Screen screen) {
      this.context = new MenuContext(screen, this.accounts == null ? null : this.accounts::open, this.credits::open);
   }

   boolean keyPressed(int key) {
      return this.popup.keyPressed(key) || this.credits.keyPressed(key) || this.accounts != null && this.accounts.keyPressed(key);
   }

   void render() {
      MinecraftClient mc = MinecraftClient.getInstance();
      long now = System.nanoTime();
      if (mc.getOverlay() != null) {
         this.openedAt = now;
      }

      ImGuiIO io = ImGui.getIO();
      boolean live = mc.getOverlay() == null;
      float t = (float)(now - this.openedAt) / 1.0E9F;
      float mx = io.getMousePosX();
      float my = io.getMousePosY();
      boolean click = live && ImGui.isMouseClicked(0);
      float width = io.getDisplaySizeX();
      float height = io.getDisplaySizeY();
      float scale = ImGuiManager.getInstance().uiScale();
      float dt = Math.max(0.001F, io.getDeltaTime());
      this.popup.update(t, live);
      boolean menuPointer = !this.popup.isOpen() && (this.accounts == null || !this.accounts.isOpen()) && !this.credits.isOpen();
      this.context
         .begin(
            ImGui.getBackgroundDrawList(),
            ImGui.getForegroundDrawList(),
            width,
            height,
            scale,
            t,
            dt,
            menuPointer ? mx : -10000.0F,
            menuPointer ? my : -10000.0F,
            menuPointer && click
         );

      try {
         DuskMenu.render(this.context);
      } catch (RuntimeException e) {
         if (!this.failed) {
            LOGGER.error("[Anubis] Main menu failed to render", e);
         }

         this.failed = true;
         OfficialBuildBanner.draw(this.context);
         return;
      }

      if (this.context.pendingTooltip() != null) {
         drawTooltip(this.context);
      }

      if (this.accounts != null) {
         this.accounts.render(width, height, scale, dt);
      }

      this.credits.render(ImGui.getForegroundDrawList(), width, height, scale, dt, mx, my, click);
      this.popup.render(ImGui.getForegroundDrawList(), width, height, scale, dt, mx, my, click);
      OfficialBuildBanner.draw(this.context);
   }

   private static void drawTooltip(MenuContext c) {
      ImDrawList dl = c.fg();
      ImFont font = c.tagFont();
      String text = c.pendingTooltip();
      float size = c.textHeight(font);
      float w = c.textWidth(font, text) + c.px(18.0F);
      float h = size + c.px(9.0F);
      float x = c.tooltipX() - w * 0.5F;
      float y = c.tooltipY();
      dl.addRectFilled(x, y, x + w, y + h, MenuContext.col(-16053492, 0.96F), c.px(3.0F));
      dl.addRect(x, y, x + w, y + h, MenuContext.col(-1, 0.25F), c.px(3.0F), 0, Math.max(1.0F, c.scale()));
      c.text(dl, font, x + c.px(9.0F), y + c.px(4.5F), -921103, 1.0F, text);
   }
}
