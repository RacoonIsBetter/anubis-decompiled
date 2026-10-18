package net.anubis.client.gui;

import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

@Environment(EnvType.CLIENT)
public final class HudEditorScreen extends Screen {
   public HudEditorScreen() {
      super(Text.literal("ANUBIS HUD Layout"));
   }

   public void removed() {
      super.removed();
      HudLayoutStore.getInstance().save();
   }

   public void close() {
      HudLayoutStore.getInstance().save();
      MinecraftClient.getInstance().setScreen(null);
   }

   public void renderBackground(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick) {
   }

   public void render(DrawContext graphics, int mouseX, int mouseY, float tickDelta) {
      ImGuiManager.getInstance().requestCursor(graphics);
   }

   public boolean shouldPause() {
      return false;
   }
}
