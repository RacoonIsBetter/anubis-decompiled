package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

@Environment(EnvType.CLIENT)
public final class InventoryManager {
   private final MinecraftClient mc;
   private final ServerRangeInventorySource serverRange;

   public InventoryManager(MinecraftClient mc) {
      this.mc = mc;
      this.serverRange = new ServerRangeInventorySource(mc);
   }

   public InventoryPickResult prepare(InventoryPickSourceMode source, Item item, long tick, boolean driving) {
      return switch (source) {
         case SERVER_RANGE -> this.serverRange.prepare(item, tick, driving);
         case HOTBAR_ONLY -> this.hotbarOnly(item);
      };
   }

   private InventoryPickResult hotbarOnly(Item item) {
      if (this.mc.player == null) {
         return InventoryPickResult.missing(item);
      }

      for (int slot = 0; slot < 9; slot++) {
         if (this.mc.player.getInventory().getStack(slot).getItem() == item) {
            return InventoryPickResult.ready(item, null, slot);
         }
      }

      return InventoryPickResult.missing(item);
   }

   public void reset() {
      this.serverRange.reset();
   }
}
