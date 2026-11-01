package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;

@Environment(EnvType.CLIENT)
public interface InventoryPickSource {
   InventoryPickResult prepare(Item var1, long var2);

   void reset();
}
