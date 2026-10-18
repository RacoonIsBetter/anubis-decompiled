package net.anubis.client.gui;

import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

@Environment(EnvType.CLIENT)
public final class AnubisTitleScreen extends TitleScreen {
   private final MainMenuRenderer menu = new MainMenuRenderer(this);

   public static boolean enabled() {
      return ThemeStore.get().customMainMenu.get();
   }

   protected void init() {
      ImGuiManager.getInstance().activate(this.menu::render);
   }

   public void tick() {
   }

   public boolean keyPressed(KeyInput event) {
      return this.menu.keyPressed(event.key()) || super.keyPressed(event);
   }

   public boolean mouseClicked(Click event, boolean doubleClick) {
      return false;
   }

   public void removed() {
      super.removed();
      ImGuiManager.getInstance().deactivate();
   }

   public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
   }

   public void render(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
      ImGuiManager.getInstance().requestCursor(graphics);
      graphics.drawDeferredElements();
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean shouldPause() {
      return false;
   }
}
