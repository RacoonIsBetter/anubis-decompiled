package net.anubis.client.easyplace.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.anubis.client.easyplace.compat.BaritoneCompat;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.shape.VoxelShape;

@Environment(EnvType.CLIENT)
final class Walker {
   private static final int STILL_LIMIT = 60;
   private static final int GRACE = 40;
   private static final int TIME_LIMIT = 600;
   private static final double EYE = 1.62;
   private static final double HEIGHT = 1.8;
   private static final double JUMP = 1.2;
   private static final float SWIM_DEPTH = 0.4F;
   private static final double FULL = 0.999;
   private static final double STEP_UP = 0.55;
   private static final double MAX_DROP = 3.0;
   private static final double LEAP_ROOM = 2.6;
   private BlockPos goal;
   private long started;
   private long lastMove;
   private Vec3d lastPos;

   boolean active() {
      return this.goal != null;
   }

   BlockPos goal() {
      return this.goal;
   }

   void go(ClientPlayerEntity player, BlockPos feet, long tick) {
      this.goal = feet;
      this.started = tick;
      this.lastMove = tick;
      this.lastPos = player.getEntityPos();
      BaritoneCompat.goTo(feet);
   }

   Walker.Result tick(ClientPlayerEntity player, long tick) {
      if (this.goal == null) {
         return Walker.Result.FAILED;
      }

      Vec3d pos = player.getEntityPos();
      double dx = pos.x - (this.goal.getX() + 0.5);
      double dz = pos.z - (this.goal.getZ() + 0.5);
      if (dx * dx + dz * dz < 0.36 && Math.abs(pos.y - this.goal.getY()) < 0.6 && player.isOnGround()) {
         this.goal = null;
         return Walker.Result.ARRIVED;
      }

      if (pos.squaredDistanceTo(this.lastPos) > 0.0025) {
         this.lastPos = pos;
         this.lastMove = tick;
      }

      if ((tick - this.started <= 40L || tick - this.lastMove <= 60L) && tick - this.started <= 600L) {
         return Walker.Result.RUNNING;
      }

      this.stop();
      return Walker.Result.FAILED;
   }

   void stop() {
      if (this.goal != null) {
         BaritoneCompat.cancel();
      }

      this.goal = null;
   }

   static BlockPos standFor(ClientWorld level, ClientPlayerEntity player, BlockPos target, BlockState expected, Predicate<BlockPos> avoid) {
      return standFor(level, player, target, expected, avoid, player.getBlockInteractionRange() - 0.7);
   }

   static BlockPos standFor(ClientWorld level, ClientPlayerEntity player, BlockPos target, BlockState expected, Predicate<BlockPos> avoid, double reach) {
      Direction look = StateRules.lookFor(expected);
      Direction compass = StateRules.flatLookFor(expected);
      Vec3d from = player.getEntityPos();
      int playerFeet = player.getBlockPos().getY();
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;

      for (int dy = -1; dy <= 2; dy++) {
         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               BlockPos feet = target.add(dx, dy, dz);
               if (!feet.equals(target) && !feet.up().equals(target) && !avoid.test(feet) && standable(level, feet)) {
                  Vec3d eye = new Vec3d(feet.getX() + 0.5, floor(level, feet) + 1.62, feet.getZ() + 0.5);
                  Vec3d to = target.toCenterPos().subtract(eye);
                  if (!(to.length() > reach) && (look == null || dominant(to, look)) && (compass == null || facingFlat(to, compass))) {
                     double flat = Math.hypot(from.x - (feet.getX() + 0.5), from.z - (feet.getZ() + 0.5));
                     int up = feet.getY() - playerFeet;
                     double score = flat + (up > 0 ? up * 4.0 : -up * 0.3) + Math.abs(dy) * 0.2;
                     if (score < bestScore) {
                        bestScore = score;
                        best = feet;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   static BlockPos stepToward(ClientWorld level, ClientPlayerEntity player, BlockPos target, Predicate<BlockPos> avoid) {
      if (!player.isOnGround()) {
         return null;
      }

      BlockPos feet = player.getBlockPos();
      Vec3d aim = target.toCenterPos();
      double now = player.getEyePos().distanceTo(aim);
      BlockPos best = null;
      double bestDist = now - 0.3;

      for (BlockPos spot : steps(level, feet)) {
         if (!avoid.test(spot)) {
            double dist = new Vec3d(spot.getX() + 0.5, floor(level, spot) + 1.62, spot.getZ() + 0.5).distanceTo(aim);
            if (dist < bestDist) {
               bestDist = dist;
               best = spot;
            }
         }
      }

      return best;
   }

   static List<BlockPos> steps(ClientWorld level, BlockPos feet) {
      return steps(level, feet, true);
   }

   private static List<BlockPos> steps(ClientWorld level, BlockPos feet, boolean noPits) {
      List<BlockPos> out = new ArrayList<>();
      double from = floorOr(level, feet);

      for (Direction direction : Type.HORIZONTAL) {
         BlockPos side = feet.offset(direction);

         for (int dy = 1; dy >= -3; dy--) {
            BlockPos spot = side.up(dy);
            double to = floor(level, spot);
            if (!Double.isNaN(to)) {
               boolean intoWater = from - to > 0.6 && !level.getFluidState(spot).isEmpty();
               boolean pit = noPits && from - to > 0.01 && steps(level, spot, false).isEmpty();
               if (to - from <= 1.2 && from - to <= 3.0 && !intoWater && !pit && !nearLava(level, spot) && passage(level, feet, from, spot, to)) {
                  out.add(spot);
               }
               break;
            }
         }
      }

      return out;
   }

   static String explain(ClientWorld level, BlockPos feet) {
      StringBuilder out = new StringBuilder("from " + feet.toShortString() + " floor " + floor(level, feet) + ":");
      double from = floorOr(level, feet);

      for (Direction direction : Type.HORIZONTAL) {
         BlockPos side = feet.offset(direction);
         out.append(" ").append(direction).append("[");

         for (int dy = 1; dy >= -3; dy--) {
            BlockPos spot = side.up(dy);
            double to = floor(level, spot);
            out.append(dy)
               .append(":")
               .append(Double.isNaN(to) ? "-" : String.format("%.2f%s", to, to - from <= 1.2 && passage(level, feet, from, spot, to) ? "ok" : "x"))
               .append(" ");
         }

         out.append("]");
      }

      return out.toString();
   }

   static BlockPos fall(ClientWorld level, BlockPos feet, Direction way, double maxFall) {
      double from = floorOr(level, feet);
      BlockPos side = feet.offset(way);
      if (Double.isNaN(floor(level, side)) && roomAt(level, side)) {
         for (int depth = 1; depth <= Math.ceil(maxFall) + 1.0; depth++) {
            BlockPos spot = side.down(depth);
            double to = floor(level, spot);
            if (!level.getFluidState(spot).isEmpty()) {
               return null;
            }

            if (!Double.isNaN(to)) {
               if (!(from - to <= 3.0) && !(from - to > maxFall) && !nearLava(level, spot) && passage(level, feet, from, spot, to)) {
                  return spot;
               }

               return null;
            }

            if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) {
               return null;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static double floor(ClientWorld level, BlockPos feet) {
      if (!level.isChunkLoaded(feet)) {
         return Double.NaN;
      }

      if (level.getFluidState(feet).isIn(FluidTags.WATER) && level.getFluidState(feet).getHeight(level, feet) > 0.4F) {
         return Double.NaN;
      }

      double own = topUnderFoot(level, feet);
      double y;
      if (!Double.isNaN(own)) {
         if (own > 0.999) {
            return Double.NaN;
         }

         y = feet.getY() + own;
      } else {
         if (level.getFluidState(feet).isIn(FluidTags.LAVA) || level.getBlockState(feet).isOf(Blocks.BUBBLE_COLUMN)) {
            return Double.NaN;
         }

         BlockPos below = feet.down();
         double ground = topUnderFoot(level, below);
         if (Double.isNaN(ground) || ground <= 0.999) {
            return Double.NaN;
         }

         y = below.getY() + ground;
      }

      if (!level.getFluidState(feet.up()).isEmpty()) {
         return Double.NaN;
      } else {
         double x = feet.getX();
         double z = feet.getZ();
         if (!level.isSpaceEmpty(new Box(x + 0.2, y + 0.01, z + 0.2, x + 0.8, y + 1.8, z + 0.8))) {
            return Double.NaN;
         } else {
            return level.isSpaceEmpty(new Box(x + 0.2, y - 0.05, z + 0.2, x + 0.8, y - 0.001, z + 0.8)) ? Double.NaN : y;
         }
      }
   }

   static boolean roomAt(ClientWorld level, BlockPos feet) {
      if (level.isChunkLoaded(feet) && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.up()).isEmpty()) {
         double x = feet.getX();
         double y = feet.getY();
         double z = feet.getZ();
         return level.isSpaceEmpty(new Box(x + 0.2, y + 0.01, z + 0.2, x + 0.8, y + 1.8, z + 0.8));
      } else {
         return false;
      }
   }

   static boolean leap(ClientWorld level, BlockPos from, double fromY, Direction way) {
      return leap(level, from, fromY, way, 1);
   }

   static boolean leap(ClientWorld level, BlockPos from, double fromY, Direction way, int gap) {
      BlockPos spot = from.offset(way, gap + 1);
      double to = floor(level, spot);
      if (!Double.isNaN(to) && !(Math.abs(to - fromY) > 0.1)) {
         for (int i = 1; i <= gap; i++) {
            BlockPos cell = from.offset(way, i);
            if (!Double.isNaN(floor(level, cell)) || !level.getFluidState(cell).isEmpty()) {
               return false;
            }

            if (!safeFall(level, cell)) {
               return false;
            }

            for (Direction side : new Direction[]{way.rotateYClockwise(), way.rotateYCounterclockwise()}) {
               BlockPos beside = cell.offset(side);
               if (Double.isNaN(floor(level, beside)) && !safeFall(level, beside)) {
                  return false;
               }
            }
         }

         BlockPos beyond = spot.offset(way);
         if (Double.isNaN(floor(level, beyond)) && !safeFall(level, beyond)) {
            return false;
         }

         Box over = column(from, fromY, fromY).union(column(spot, fromY, fromY)).withMaxY(fromY + 2.6);
         return level.isSpaceEmpty(over);
      } else {
         return false;
      }
   }

   private static boolean safeFall(ClientWorld level, BlockPos cell) {
      for (int depth = 1; depth <= 3; depth++) {
         BlockPos below = cell.down(depth);
         if (level.getFluidState(below).isIn(FluidTags.LAVA) || level.getBlockState(below).isIn(BlockTags.FIRE)) {
            return false;
         }

         if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static double topUnderFoot(ClientWorld level, BlockPos pos) {
      VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
      if (shape.isEmpty()) {
         return Double.NaN;
      }

      double top = Double.NaN;

      for (Box box : shape.getBoundingBoxes()) {
         if (!(box.maxX <= 0.2) && !(box.minX >= 0.8) && !(box.maxZ <= 0.2) && !(box.minZ >= 0.8)) {
            top = Double.isNaN(top) ? box.maxY : Math.max(top, box.maxY);
         }
      }

      return top;
   }

   static boolean risky(ClientWorld level, BlockPos feet) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               BlockPos side = feet.add(dx, 0, dz);
               if (Double.isNaN(floor(level, side))) {
                  boolean beside = dx == 0 || dz == 0;

                  for (int depth = 0; depth <= 24; depth++) {
                     BlockPos below = side.down(depth);
                     if (level.getFluidState(below).isIn(FluidTags.LAVA) || level.getBlockState(below).isIn(BlockTags.FIRE)) {
                        if (depth <= 4) {
                           return true;
                        }
                        break;
                     }

                     boolean deep = depth > (beside ? 4 : 8);
                     if (level.getFluidState(below).isIn(FluidTags.WATER)) {
                        if (deep) {
                           return true;
                        }
                        break;
                     }

                     if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                        if (deep) {
                           return true;
                        }
                        break;
                     }

                     if (depth == 24) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   static boolean icy(ClientWorld level, BlockPos feet) {
      return level.getBlockState(feet.down()).isIn(BlockTags.ICE) || level.getBlockState(feet).isIn(BlockTags.ICE);
   }

   static int dropAt(ClientWorld level, BlockPos feet) {
      if (!level.isChunkLoaded(feet)) {
         return 0;
      }

      if (level.getFluidState(feet).isIn(FluidTags.LAVA)) {
         return 24;
      }

      if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
         return 0;
      }

      int depth = 0;

      while (depth < 24) {
         BlockPos below = feet.down(depth + 1);
         if (!level.getFluidState(below).isIn(FluidTags.LAVA) && !level.getBlockState(below).isIn(BlockTags.FIRE)) {
            if (level.getFluidState(below).isEmpty() && level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
               depth++;
               continue;
            }

            return depth;
         }

         return 24;
      }

      return 24;
   }

   static boolean nearLava(ClientWorld level, BlockPos feet) {
      for (BlockPos cell : new BlockPos[]{feet, feet.up(), feet.down(), feet.north(), feet.south(), feet.east(), feet.west()}) {
         if (level.getFluidState(cell).isIn(FluidTags.LAVA)) {
            return true;
         }
      }

      return false;
   }

   static double floorOr(ClientWorld level, BlockPos feet) {
      double y = floor(level, feet);
      return Double.isNaN(y) ? feet.getY() : y;
   }

   private static boolean passage(ClientWorld level, BlockPos feet, double from, BlockPos spot, double to) {
      double top = Math.max(from, to);
      Box here = column(feet, from, top);
      Box there = column(spot, to, top);
      Box across = new Box(
         Math.min(here.minX, there.minX),
         top + 0.55,
         Math.min(here.minZ, there.minZ),
         Math.max(here.maxX, there.maxX),
         top + 1.8,
         Math.max(here.maxZ, there.maxZ)
      );
      return level.isSpaceEmpty(here) && level.isSpaceEmpty(there) && level.isSpaceEmpty(across);
   }

   private static Box column(BlockPos pos, double bottom, double top) {
      return new Box(pos.getX() + 0.2, bottom + 0.01, pos.getZ() + 0.2, pos.getX() + 0.8, top + 1.8, pos.getZ() + 0.8);
   }

   static BlockPos stepAside(ClientWorld level, ClientPlayerEntity player, Predicate<BlockPos> avoid) {
      BlockPos at = player.getBlockPos();
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;

      for (int dy = -1; dy <= 1; dy++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               if (dx != 0 || dz != 0) {
                  BlockPos feet = at.add(dx, dy, dz);
                  if (!avoid.test(feet) && standable(level, feet)) {
                     double score = Math.abs(dx) + Math.abs(dz) + Math.abs(dy) * 1.5;
                     if (score < bestScore) {
                        bestScore = score;
                        best = feet;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   static boolean standable(ClientWorld level, BlockPos feet) {
      return !Double.isNaN(floor(level, feet));
   }

   static boolean facingFlat(Vec3d to, Direction look) {
      double along = to.x * look.getOffsetX() + to.z * look.getOffsetZ();
      double across = Math.abs(to.x * look.getOffsetZ()) + Math.abs(to.z * look.getOffsetX());
      return along > across + 0.1;
   }

   static boolean dominant(Vec3d to, Direction look) {
      return dominant(to, look, 0.2);
   }

   static boolean dominant(Vec3d to, Direction look, double margin) {
      double along = to.x * look.getOffsetX() + to.y * look.getOffsetY() + to.z * look.getOffsetZ();
      if (along <= 0.0) {
         return false;
      }

      double ax = Math.abs(to.x);
      double ay = Math.abs(to.y);
      double az = Math.abs(to.z);

      return switch (look.getAxis()) {
         case X -> ax > ay + margin && ax > az + margin;
         case Y -> ay > ax + margin && ay > az + margin;
         case Z -> az > ax + margin && az > ay + margin;
      };
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      ARRIVED,
      FAILED;
   }
}
