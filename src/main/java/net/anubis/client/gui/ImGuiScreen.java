package net.anubis.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class ImGuiScreen extends Screen {
   private final ClickGuiRenderer clickGui = new ClickGuiRenderer();

   public ImGuiScreen() {
      super(Text.literal("ANUBIS"));
   }

   @Override
   protected void init() {
      super.init();
      ImGuiManager.getInstance().activate(this.clickGui::render);
   }

   @Override
   public void removed() {
      super.removed();
      this.clickGui.handleEscape();
      ImGuiManager.getInstance().deactivate();
      ModuleConfigStore.getInstance().saveIfChanged();
      ThemeStore.get().save();
      HudTheme.get().save();
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (ThemeStore.get().menuBlur.get()) {
         super.renderBackground(context, mouseX, mouseY, deltaTicks);
      }
   }

   @Override
   public void onFilesDropped(List<Path> paths) {
      this.clickGui.onFilesDropped(paths);
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      ImGuiManager.getInstance().requestCursor(context);
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      return input.key() == 256 && this.clickGui.handleEscape() ? true : super.keyPressed(input);
   }

   public boolean capturesKeys() {
      return this.clickGui.capturesKeys();
   }

   @Override
   public boolean shouldPause() {
      return false;
   }
}
