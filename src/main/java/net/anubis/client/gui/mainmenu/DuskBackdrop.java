package net.anubis.client.gui.mainmenu;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class DuskBackdrop {
   public static final Identifier TEXTURE = Identifier.of("anubis", "textures/gui/dusk_backdrop.png");
   private static final float ASPECT = 1.7777778F;

   private DuskBackdrop() {
   }

   public static void draw(DrawContext graphics, int width, int height) {
      if (width > 0 && height > 0) {
         float screen = (float)width / height;
         float u = screen < 1.7777778F ? (1.0F - screen / 1.7777778F) * 0.5F : 0.0F;
         float v = screen > 1.7777778F ? (1.0F - 1.7777778F / screen) * 0.5F : 0.0F;
         graphics.drawTexturedQuad(TEXTURE, 0, 0, width, height, u, 1.0F - u, v, 1.0F - v);
      }
   }
}
