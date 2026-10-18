package net.anubis.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

@Environment(EnvType.CLIENT)
public final class ImGuiScreen extends Screen {
   private final ClickGuiRenderer clickGui = new ClickGuiRenderer();

   public ImGuiScreen() {
      super(Text.literal("ANUBIS"));
   }

   protected void init() {
      super.init();
      ImGuiManager.getInstance().activate(this.clickGui::render);
   }

   public void removed() {
      super.removed();
      this.clickGui.handleEscape();
      ImGuiManager.getInstance().deactivate();
      ModuleConfigStore.getInstance().save();
      ThemeStore.get().save();
      HudTheme.get().save();
   }

   public void renderBackground(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (ThemeStore.get().menuBlur.get()) {
         super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   public void onFilesDropped(List<Path> paths) {
      this.clickGui.onFilesDropped(paths);
   }

   public void render(DrawContext graphics, int mouseX, int mouseY, float tickDelta) {
      ImGuiManager.getInstance().requestCursor(graphics);
   }

   public boolean keyPressed(KeyInput event) {
      return event.key() == 256 && this.clickGui.handleEscape() ? true : super.keyPressed(event);
   }

   public boolean capturesKeys() {
      return this.clickGui.capturesKeys();
   }

   public boolean shouldPause() {
      return false;
   }
}
