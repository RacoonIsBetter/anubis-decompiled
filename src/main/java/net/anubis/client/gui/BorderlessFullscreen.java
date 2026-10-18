package net.anubis.client.gui;

import com.sun.jna.Platform;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class BorderlessFullscreen {
   private BorderlessFullscreen() {
   }

   public static boolean wanted() {
      return Platform.isWindows() && NoInvLeakModule.wantsBorderless();
   }

   public static void tick() {
      Window window = MinecraftClient.getInstance().getWindow();
      if (window.isFullscreen()) {
         BorderlessFullscreen.Switchable switchable = (BorderlessFullscreen.Switchable)(Object)window;
         if (wanted() != switchable.anubis$isBorderless()) {
            switchable.anubis$reapplyMode();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Switchable {
      boolean anubis$isBorderless();

      void anubis$reapplyMode();
   }
}
