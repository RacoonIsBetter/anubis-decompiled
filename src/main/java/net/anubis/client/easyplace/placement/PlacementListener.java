package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface PlacementListener {
   void feedback(String var1, String var2, int var3);
}
