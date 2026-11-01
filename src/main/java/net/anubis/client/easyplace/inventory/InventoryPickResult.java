package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public record InventoryPickResult(InventoryPickResult.Status status, Item item, Hand hand, int slot) {
   public static InventoryPickResult ready(Item item, Hand hand, int slot) {
      return new InventoryPickResult(InventoryPickResult.Status.READY, item, hand, slot);
   }

   public static InventoryPickResult waiting(Item item, int slot) {
      return new InventoryPickResult(InventoryPickResult.Status.WAITING, item, null, slot);
   }

   public static InventoryPickResult missing(Item item) {
      return new InventoryPickResult(InventoryPickResult.Status.MISSING, item, null, -1);
   }

   @Environment(EnvType.CLIENT)
   public enum Status {
      READY,
      WAITING,
      MISSING;
   }
}
