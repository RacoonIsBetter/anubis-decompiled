package net.anubis.client.easyplace.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class PlacementUtil {
   private PlacementUtil() {
   }

   public static Vec3d faceCenterCursor(BlockPos targetPos, Direction placeSide) {
      return targetPos.toCenterPos().add(placeSide.getOffsetX() * 0.5, placeSide.getOffsetY() * 0.5, placeSide.getOffsetZ() * 0.5);
   }

   public static float[] calcRotation(Vec3d eyePos, Vec3d target) {
      double dx = target.x - eyePos.x;
      double dy = target.y - eyePos.y;
      double dz = target.z - eyePos.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float)Math.toDegrees(-Math.atan2(dy, dist));
      return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F)};
   }

   public static BlockHitResult rayTraceTowards(PlayerEntity player, float yaw, float pitch, double reach) {
      return rayTraceFrom(player, getEyePos(player), yaw, pitch, reach);
   }

   public static BlockHitResult rayTraceFrom(PlayerEntity player, Vec3d eyePos, float yaw, float pitch, double reach) {
      World level = MinecraftClient.getInstance().world;
      if (level == null) {
         return null;
      }

      double yawRad = Math.toRadians(yaw);
      double cosPitch = Math.cos(Math.toRadians(pitch));
      double sinPitch = Math.sin(Math.toRadians(pitch));
      Vec3d direction = new Vec3d(-Math.sin(yawRad) * cosPitch, -sinPitch, Math.cos(yawRad) * cosPitch);
      Vec3d end = eyePos.add(direction.multiply(reach));
      return level.raycast(new RaycastContext(eyePos, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
   }

   public static Vec3d getEyePos(PlayerEntity player) {
      return new Vec3d(player.getX(), player.getEyeY(), player.getZ());
   }

   public static Direction getClickFace(Direction placeSide) {
      return placeSide.getOpposite();
   }

   public static Direction getPlaceSide(BlockPos targetPos, Vec3d eyePos) {
      World level = MinecraftClient.getInstance().world;
      if (level == null) {
         return null;
      }

      Direction best = null;
      double bestScore = -Double.MAX_VALUE;

      for (Direction dir : Direction.values()) {
         BlockPos againstPos = targetPos.offset(dir);
         BlockState againstState = level.getBlockState(againstPos);
         if (!againstState.isAir() && !againstState.getCollisionShape(level, againstPos).isEmpty()) {
            Vec3d diff = eyePos.subtract(againstPos.toCenterPos());
            double score = diff.x * dir.getOffsetX() + diff.y * dir.getOffsetY() + diff.z * dir.getOffsetZ();
            if (score > bestScore) {
               bestScore = score;
               best = dir;
            }
         }
      }

      return best;
   }

   public static boolean faceMatchesAxis(BlockState expected, Direction clickedFace) {
      return !expected.contains(Properties.AXIS) || expected.get(Properties.AXIS) == clickedFace.getAxis();
   }

   public static boolean isFaceVisibleToEye(BlockPos pos, Direction clickedFace, Vec3d eyePos) {
      double buffer = 0.05;

      return switch (clickedFace) {
         case NORTH -> eyePos.z < pos.getZ() - 0.05;
         case SOUTH -> eyePos.z > pos.getZ() + 1.0 + 0.05;
         case WEST -> eyePos.x < pos.getX() - 0.05;
         case EAST -> eyePos.x > pos.getX() + 1.0 + 0.05;
         case UP -> eyePos.y > pos.getY() + 1.0 + 0.05;
         case DOWN -> eyePos.y < pos.getY() - 0.05;
      };
   }
}
