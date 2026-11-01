package net.anubis.launcher;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Desktop.Action;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

public final class InstallNotice {
   private static final String TITLE = "Anubis Client";
   private static final String MESSAGE = "Anubis Client is a Fabric mod for Minecraft 1.21.11.\nPut this file in your mods folder and start the game with Fabric Loader.";

   private InstallNotice() {
   }

   public static void main(String[] args) {
      if (GraphicsEnvironment.isHeadless()) {
         System.out.println("Anubis Client is a Fabric mod for Minecraft 1.21.11.\nPut this file in your mods folder and start the game with Fabric Loader.");
      } else {
         try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         } catch (Exception var3) {
         }

         String[] options = new String[]{"Open Mods Folder", "Close"};
         int choice = JOptionPane.showOptionDialog(
            null,
            "Anubis Client is a Fabric mod for Minecraft 1.21.11.\nPut this file in your mods folder and start the game with Fabric Loader.",
            "Anubis Client",
            -1,
            1,
            null,
            options,
            options[1]
         );
         if (choice == 0) {
            openModsFolder();
         }

         System.exit(0);
      }
   }

   private static void openModsFolder() {
      File mods = modsFolder();
      if (!mods.isDirectory()) {
         mods.mkdirs();
      }

      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN) && mods.isDirectory()) {
         try {
            Desktop.getDesktop().open(mods);
            return;
         } catch (IOException var2) {
         }
      }

      JOptionPane.showMessageDialog(null, "Your mods folder is:\n" + mods.getAbsolutePath(), "Anubis Client", 1);
   }

   private static File modsFolder() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      String home = System.getProperty("user.home", ".");
      if (os.contains("win")) {
         String appData = System.getenv("APPDATA");
         return new File(new File(appData != null ? appData : home, ".minecraft"), "mods");
      } else {
         return os.contains("mac") ? new File(home, "Library/Application Support/minecraft/mods") : new File(home, ".minecraft/mods");
      }
   }
}
