package net.anubis.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;

@Environment(EnvType.CLIENT)
public final class ScreenText {
   private static final float IN = 0.4F;
   private static final float OUT = 0.8F;
   private final int rgb;
   private final float stay;
   private Text title;
   private Text subtitle;
   private long start;

   public ScreenText(int rgb, float staySeconds) {
      this.rgb = rgb & 16777215;
      this.stay = staySeconds;
   }

   public void show(String line, String below) {
      this.title = Text.literal(line);
      this.subtitle = Text.literal(below);
      this.start = 0L;
   }

   public void render(DrawContext g) {
      if (this.title != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         long now = System.nanoTime();
         if (this.start == 0L) {
            if (mc.currentScreen != null || mc.world == null) {
               return;
            }

            this.start = now;
         }

         float t = (float)(now - this.start) / 1.0E9F;
         if (t >= 0.4F + this.stay + 0.8F) {
            this.title = null;
         } else {
            float fade = t < 0.4F ? t / 0.4F : Math.min(1.0F, (0.4F + this.stay + 0.8F - t) / 0.8F);
            int alpha = Math.round(fade * 255.0F);
            if (alpha > 8) {
               TextRenderer font = mc.textRenderer;
               int color = ColorHelper.withAlpha(alpha, this.rgb);
               g.getMatrices().pushMatrix();
               g.getMatrices().translate(g.getScaledWindowWidth() / 2.0F, g.getScaledWindowHeight() / 2.0F - 30.0F);
               g.getMatrices().scale(3.0F, 3.0F);
               g.drawText(font, this.title, -font.getWidth(this.title) / 2, -9 / 2, color, true);
               g.getMatrices().popMatrix();
               g.getMatrices().pushMatrix();
               g.getMatrices().translate(g.getScaledWindowWidth() / 2.0F, g.getScaledWindowHeight() / 2.0F);
               g.getMatrices().scale(1.5F, 1.5F);
               g.drawText(font, this.subtitle, -font.getWidth(this.subtitle) / 2, -9 / 2, color, true);
               g.getMatrices().popMatrix();
            }
         }
      }
   }
}
