package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum PlacementState {
   IDLE,
   FIND_TARGET,
   CALCULATE_ROTATION,
   WAIT_ROTATION,
   EXECUTE_PLACE,
   WAIT_ACK;
}
