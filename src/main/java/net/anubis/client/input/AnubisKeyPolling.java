package net.anubis.client.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

@Environment(EnvType.CLIENT)
public final class AnubisKeyPolling {
   private AnubisKeyPolling() {
   }

   public static boolean isKeyDown(Window window, int key) {
      return InputUtil.isKeyPressed(window, key);
   }
}
