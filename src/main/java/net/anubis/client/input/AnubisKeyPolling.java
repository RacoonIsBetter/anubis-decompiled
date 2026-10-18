package net.anubis.client.input;

import imgui.glfw.AnubisKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class AnubisKeyPolling {
   private AnubisKeyPolling() {
   }

   public static boolean isKeyDown(Window window, int key) {
      return AnubisKeys.isAndroid() ? AnubisKeys.getKey(window.getHandle(), key) == 1 : InputUtil.isKeyPressed(window, key);
   }
}
