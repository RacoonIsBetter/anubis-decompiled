package net.anubis.client.easyplace.placement;

import net.anubis.client.easyplace.inventory.InventoryPickSourceMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record PlacementConfig(
   boolean allBlocks,
   boolean rotationSpoof,
   double maxYawChange,
   InventoryPickSourceMode inventoryPickSource,
   boolean schematicOnly,
   int tickDelay,
   boolean schematicAura,
   double auraRange,
   boolean hopperForceFacing,
   boolean baritoneWalk,
   boolean debug,
   boolean fovFilter,
   double fovDegrees
) {
}
