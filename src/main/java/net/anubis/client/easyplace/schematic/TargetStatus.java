package net.anubis.client.easyplace.schematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum TargetStatus {
   CORRECT,
   MISSING,
   ADJUST,
   WRONG,
   NO_ITEM,
   UNSUPPORTED;
}
