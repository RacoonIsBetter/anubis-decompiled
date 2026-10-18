package net.anubis.client.gui.mainmenu;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MenuAction {
   SINGLEPLAYER("Singleplayer"),
   MULTIPLAYER("Multiplayer"),
   REALMS("Realms"),
   OPTIONS("Options"),
   QUIT("Quit"),
   LANGUAGE("Language"),
   ACCESSIBILITY("Accessibility"),
   ANUBIS("Anubis Client"),
   MODS("Mods Folder"),
   ACCOUNTS("Accounts"),
   CREDITS("Credits");

   public static final List<MenuAction> MAIN = List.of(SINGLEPLAYER, MULTIPLAYER, REALMS, OPTIONS, QUIT);
   private final String label;

   MenuAction(String label) {
      this.label = label;
   }

   public String label() {
      return this.label;
   }
}
