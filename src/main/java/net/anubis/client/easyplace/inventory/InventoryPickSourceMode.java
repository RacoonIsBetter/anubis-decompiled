package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum InventoryPickSourceMode {
   SERVER_RANGE("Server Range"),
   HOTBAR_ONLY("Hotbar Only");

   private final String title;

   InventoryPickSourceMode(String title) {
      this.title = title;
   }

   public String title() {
      return this.title;
   }

   public static String[] titles() {
      InventoryPickSourceMode[] modes = values();
      String[] titles = new String[modes.length];

      for (int i = 0; i < modes.length; i++) {
         titles[i] = modes[i].title;
      }

      return titles;
   }

   public static InventoryPickSourceMode of(String title) {
      for (InventoryPickSourceMode mode : values()) {
         if (mode.title.equals(title)) {
            return mode;
         }
      }

      return HOTBAR_ONLY;
   }

   @Override
   public String toString() {
      return this.title;
   }
}
