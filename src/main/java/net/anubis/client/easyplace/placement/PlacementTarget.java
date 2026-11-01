package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public record PlacementTarget(
   BlockPos targetPos,
   BlockPos neighborPos,
   Direction placeSide,
   Direction clickedFace,
   int hotbarSlot,
   float yaw,
   float pitch,
   int score,
   Vec3d hitCursor,
   boolean sneak
) {
}
