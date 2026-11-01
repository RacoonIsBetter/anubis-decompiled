package net.anubis.client.security;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.gui.FabricStatusTree;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricBasicButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class TamperStop {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");

   private TamperStop() {
   }

   public static void stopGame(String reason, Path jar) {
      LOGGER.error("[Anubis] Build check: the game was stopped because this Anubis jar was modified ({}). Get Anubis only from {}", reason, "discord.gg/anubis");
      if (!showWindow(jar)) {
         showFabricWindow(jar);
      }

      System.exit(1);
   }

   private static boolean showWindow(Path jar) {
      String java = ProcessHandle.current().info().command().orElse(null);
      if (java != null && jar != null) {
         try {
            Process window = new ProcessBuilder(java, "-cp", jar.toString(), TamperWindow.class.getName(), jar.toString())
               .redirectOutput(Redirect.DISCARD)
               .redirectError(Redirect.DISCARD)
               .start();
            return window.waitFor() == 0;
         } catch (Exception e) {
            LOGGER.warn("[Anubis] Build check: the warning window could not open ({})", e.toString());
            return false;
         }
      } else {
         return false;
      }
   }

   private static void showFabricWindow(Path jar) {
      try {
         FabricStatusTree tree = new FabricStatusTree("Anubis", TamperWindow.plainText(jar == null ? "" : jar.toString()));
         tree.addButton("Copy official link", FabricBasicButtonType.CLICK_MANY).withClipboard("https://discord.gg/anubis");
         tree.addButton("Close", FabricBasicButtonType.CLICK_ONCE).makeClose();
         FabricGuiEntry.open(tree);
      } catch (Throwable e) {
         LOGGER.warn("[Anubis] Build check: Fabric's error window could not open ({})", e.toString());
      }
   }
}
