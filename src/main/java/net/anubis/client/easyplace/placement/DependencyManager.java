package net.anubis.client.easyplace.placement;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerRange;
import java.util.function.LongPredicate;
import net.anubis.client.easyplace.hopper.HopperPlacementHandler;
import net.anubis.client.easyplace.mixin.InvokerBlockItem;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ChorusFlowerBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

@Environment(EnvType.CLIENT)
public final class DependencyManager {
   private static final int SEARCH_RADIUS = 5;
   private final MinecraftClient mc;
   private final HopperPlacementHandler hopperHandler = new HopperPlacementHandler();
   private BuildScope currentScope;
   private double reachSpare;
   private double feetY = Double.NaN;
   private boolean builderEyes;
   private static final double BUILDER_SPARE = 0.3;
   private static final double REACH_SPARE = 0.6499999999999999;
   private static final double STEADY_SHIFT = 0.02;
   private static final float[][] STEADY_TURNS = new float[][]{{0.5F, 0.0F}, {-0.5F, 0.0F}, {0.0F, 0.5F}, {0.0F, -0.5F}};

   public DependencyManager(MinecraftClient mc) {
      this.mc = mc;
   }

   public PlacementTarget searchForPlaceables(
      WorldSchematic schematicWorld,
      boolean hopperForceFacing,
      boolean fovFilter,
      float fovDegrees,
      double maxYawChange,
      float lastYawDelta,
      BuildScope scope,
      LongPredicate held
   ) {
      if (this.mc.player != null && this.mc.world != null && schematicWorld != null) {
         BlockPos center = this.mc.player.getBlockPos();
         Vec3d eyePos = new Vec3d(this.mc.player.getX(), this.mc.player.getEyeY(), this.mc.player.getZ());
         this.feetY = this.mc.player.getY();
         this.builderEyes = scope != null;
         this.reachSpare = scope != null ? 0.3 : 0.0;
         float currentYaw = this.mc.player.getYaw();
         PlacementTarget best = null;
         double bestScore = Double.MAX_VALUE;

         for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
               for (int dz = -5; dz <= 5; dz++) {
                  int x = center.getX() + dx;
                  int y = center.getY() + dy;
                  int z = center.getZ() + dz;
                  BlockPos pos = new BlockPos(x, y, z);
                  if ((scope == null || scope.allows(pos)) && !held.test(pos.asLong())) {
                     BlockState expected = schematicWorld.getBlockState(pos);
                     if (!expected.isAir() && BlockStateUtil.isPrimaryPlacementState(expected) && (scope != null || this.isInRenderLayer(pos))) {
                        BlockState current = this.mc.world.getBlockState(pos);
                        boolean secondSlab = StateRules.slabHalf(current, expected);
                        if ((secondSlab || isReplaceable(current))
                           && !StateRules.placed(current, expected)
                           && (secondSlab || !this.mc.player.getBoundingBox().intersects(new Box(pos)))) {
                           this.currentScope = scope;
                           PlacementTarget placement = secondSlab
                              ? this.secondSlab(expected, current, pos, eyePos, scope != null)
                              : this.possibleToPlace(expected, x, y, z, eyePos, hopperForceFacing, scope != null);
                           if (placement != null) {
                              if (fovFilter) {
                                 double fdx = x + 0.5 - eyePos.x;
                                 double fdy = y + 0.5 - eyePos.y;
                                 double fdz = z + 0.5 - eyePos.z;
                                 double distSq = fdx * fdx + fdy * fdy + fdz * fdz;
                                 if (distSq < 1.0E-4) {
                                    continue;
                                 }

                                 double yawRad = Math.toRadians(currentYaw);
                                 double pitchRad = Math.toRadians(this.mc.player.getPitch());
                                 double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
                                 double lookY = -Math.sin(pitchRad);
                                 double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);
                                 double dot = (fdx * lookX + fdy * lookY + fdz * lookZ) / Math.sqrt(distSq);
                                 dot = Math.max(-1.0, Math.min(1.0, dot));
                                 double angleDeg = Math.toDegrees(Math.acos(dot));
                                 if (angleDeg > fovDegrees * 0.5) {
                                    continue;
                                 }
                              }

                              double yawDiff = Math.abs(MathHelper.wrapDegrees(placement.yaw() - currentYaw));
                              if (!(yawDiff > maxYawChange)) {
                                 double dist = eyePos.squaredDistanceTo(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
                                 double score = yawDiff * 2.0 + dist * 0.5;
                                 if (scope != null) {
                                    score = yawDiff * 0.2 - Math.sqrt(dist) * 20.0 + (y - center.getY() + 5) * scope.lowWeight();
                                 }

                                 if (lastYawDelta != 0.0F) {
                                    double candidateDelta = MathHelper.wrapDegrees(placement.yaw() - currentYaw);
                                    if (Math.signum(candidateDelta) == -Math.signum(lastYawDelta) && Math.abs(candidateDelta) > 10.0) {
                                       score += Math.abs(candidateDelta) * 5.0;
                                    }
                                 }

                                 if (score < bestScore) {
                                    bestScore = score;
                                    best = placement;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private PlacementTarget possibleToPlace(BlockState toPlace, int x, int y, int z, Vec3d eyePos, boolean hopperForceFacing, boolean wholeInventory) {
      BlockPos targetPos = new BlockPos(x, y, z);
      boolean skipChestType = this.partnerMissing(toPlace, targetPos);
      int slot = this.findItem(toPlace, wholeInventory);
      if (slot < 0) {
         return null;
      }

      for (Direction against : Direction.values()) {
         BlockPos placeAgainstPos = targetPos.offset(against);
         BlockState placeAgainstState = this.mc.world.getBlockState(placeAgainstPos);
         if (!isReplaceable(placeAgainstState) && toPlace.canPlaceAt(this.mc.world, targetPos)) {
            Direction clickedFace = against.getOpposite();
            if (!hopperForceFacing || this.hopperHandler.faceCanProduceExpectedFacing(toPlace, clickedFace)) {
               boolean sneak = !placeAgainstPos.equals(targetPos) && Clicks.usesClick(this.mc.world, placeAgainstPos, this.wholeInventory(slot));
               boolean visible = PlacementUtil.isFaceVisibleToEye(placeAgainstPos, clickedFace, this.builderEye(eyePos, sneak))
                  || this.builderEyes && !sneak && PlacementUtil.isFaceVisibleToEye(placeAgainstPos, clickedFace, this.builderEye(eyePos, true));
               if (visible) {
                  VoxelShape shape = placeAgainstState.getOutlineShape(this.mc.world, placeAgainstPos);
                  if (!shape.isEmpty()) {
                     Box aabb = shape.getBoundingBox();

                     for (Vec3d multiplier : aabbSideMultipliers(against)) {
                        double placeX = placeAgainstPos.getX() + aabb.minX * multiplier.x + aabb.maxX * (1.0 - multiplier.x);
                        double placeY = placeAgainstPos.getY() + aabb.minY * multiplier.y + aabb.maxY * (1.0 - multiplier.y);
                        double placeZ = placeAgainstPos.getZ() + aabb.minZ * multiplier.z + aabb.maxZ * (1.0 - multiplier.z);
                        Vec3d cursor = new Vec3d(placeX, placeY, placeZ);
                        PlacementTarget target = this.tryAim(toPlace, targetPos, placeAgainstPos, clickedFace, cursor, eyePos, slot, skipChestType);
                        if (target != null) {
                           return target;
                        }
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   public boolean placeableFrom(BlockState expected, BlockPos pos, Vec3d eyePos, boolean hopperForceFacing) {
      if (this.mc.player != null && this.mc.world != null) {
         BlockState current = this.mc.world.getBlockState(pos);
         this.reachSpare = 0.6499999999999999;
         double wasFeet = this.feetY;
         boolean wasBuilder = this.builderEyes;
         this.feetY = eyePos.y - this.mc.player.getEyeHeight(EntityPose.STANDING);
         this.builderEyes = true;

         try {
            if (StateRules.slabHalf(current, expected)) {
               return this.secondSlab(expected, current, pos, eyePos, true) != null;
            } else {
               return isReplaceable(current) && !StateRules.placed(current, expected)
                  ? this.possibleToPlace(expected, pos.getX(), pos.getY(), pos.getZ(), eyePos, hopperForceFacing, true) != null
                  : false;
            }
         } finally {
            this.reachSpare = 0.0;
            this.feetY = wasFeet;
            this.builderEyes = wasBuilder;
         }
      } else {
         return false;
      }
   }

   private boolean partnerMissing(BlockState toPlace, BlockPos targetPos) {
      if (!toPlace.contains(Properties.CHEST_TYPE)) {
         return true;
      }

      ChestType type = toPlace.get(Properties.CHEST_TYPE);
      if (type == ChestType.SINGLE) {
         return true;
      }

      Direction facing = toPlace.get(Properties.HORIZONTAL_FACING);
      Direction partnerDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
      BlockState partnerState = this.mc.world.getBlockState(targetPos.offset(partnerDir));
      return !partnerState.contains(Properties.CHEST_TYPE)
         || partnerState.get(Properties.CHEST_TYPE) != ChestType.SINGLE
         || partnerState.get(Properties.HORIZONTAL_FACING) != facing;
   }

   public boolean verify(BlockState desired, BlockPos targetPos, BlockHitResult hit, float yaw, float pitch, int slot, boolean sneaking) {
      return this.mc.player != null && this.mc.world != null && slot >= 0
         ? this.wouldPlace(desired, targetPos, hit, new float[]{yaw, pitch}, slot, this.partnerMissing(desired, targetPos), sneaking)
         : false;
   }

   public String wouldBe(BlockPos targetPos, BlockHitResult hit, float yaw, float pitch, int slot, boolean sneaking) {
      if (this.mc.player != null && this.mc.world != null && slot >= 0) {
         ClientPlayerEntity player = this.mc.player;
         PlayerInput keys = player.input.playerInput;
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.getItem() instanceof BlockItem blockItem) {
            float var20 = player.getYaw();
            float originalPitch = player.getPitch();
            player.setYaw(yaw);
            player.setPitch(pitch);
            player.input.playerInput = new PlayerInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), sneaking, keys.sprint());

            try {
               ItemPlacementContext context = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit);
               if (!context.canPlace()) {
                  return "cannot place";
               } else {
                  return !context.getBlockPos().equals(targetPos)
                     ? "goes to " + context.getBlockPos().toShortString()
                     : ((InvokerBlockItem)blockItem).easyplace$getPlacementState(context) + " facing " + context.getHorizontalPlayerFacing();
               }
            } catch (Exception e) {
               return e.toString();
            } finally {
               player.setYaw(var20);
               player.setPitch(originalPitch);
               player.input.playerInput = keys;
            }
         } else {
            return "no block item";
         }
      } else {
         return "?";
      }
   }

   private PlacementTarget secondSlab(BlockState expected, BlockState current, BlockPos pos, Vec3d eyePos, boolean wholeInventory) {
      int slot = this.findItem(expected, wholeInventory);
      if (slot < 0) {
         return null;
      }

      boolean bottom = current.get(Properties.SLAB_TYPE) == SlabType.BOTTOM;
      Direction face = bottom ? Direction.UP : Direction.DOWN;
      double faceY = pos.getY() + 0.5;
      if (bottom ? !(eyePos.y <= faceY + 0.05) : !(eyePos.y >= faceY - 0.05)) {
         for (double[] offset : new double[][]{{0.5, 0.5}, {0.25, 0.5}, {0.75, 0.5}, {0.5, 0.25}, {0.5, 0.75}}) {
            Vec3d cursor = new Vec3d(pos.getX() + offset[0], faceY, pos.getZ() + offset[1]);
            PlacementTarget target = this.tryAim(expected, pos, pos, face, cursor, eyePos, slot, true);
            if (target != null) {
               return target;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private PlacementTarget tryAim(
      BlockState toPlace, BlockPos targetPos, BlockPos againstPos, Direction clickedFace, Vec3d cursor, Vec3d eyePos, int slot, boolean skipChestType
   ) {
      boolean supportSneak = !againstPos.equals(targetPos) && Clicks.usesClick(this.mc.world, againstPos, this.wholeInventory(slot));
      PlacementTarget target = this.aimWith(toPlace, targetPos, againstPos, clickedFace, cursor, eyePos, slot, skipChestType, supportSneak, false);
      if (target != null) {
         return target;
      } else {
         return supportSneak || !(toPlace.getBlock() instanceof ChestBlock) && !this.builderEyes
            ? null
            : this.aimWith(toPlace, targetPos, againstPos, clickedFace, cursor, eyePos, slot, skipChestType, true, true);
      }
   }

   private PlacementTarget aimWith(
      BlockState toPlace,
      BlockPos targetPos,
      BlockPos againstPos,
      Direction clickedFace,
      Vec3d cursor,
      Vec3d eyePos,
      int slot,
      boolean skipChestType,
      boolean sneaking,
      boolean sneakFlag
   ) {
      Vec3d eye = this.builderEye(eyePos, sneaking);
      float[] rot = PlacementUtil.calcRotation(eye, cursor);
      double reach = this.mc.player.getBlockInteractionRange() - this.reachSpare;
      BlockHitResult result = PlacementUtil.rayTraceFrom(this.mc.player, eye, rot[0], rot[1], reach);
      if (result == null || result.getType() != Type.BLOCK) {
         return null;
      } else if (!result.getBlockPos().equals(againstPos) || result.getSide() != clickedFace) {
         return null;
      } else if (!this.wouldPlace(toPlace, targetPos, result, rot, slot, skipChestType, sneaking)) {
         return null;
      } else {
         return !this.steady(toPlace, targetPos, result, rot, slot, skipChestType, sneaking)
            ? null
            : new PlacementTarget(targetPos, againstPos, clickedFace, clickedFace, slot, rot[0], rot[1], 0, result.getPos(), sneakFlag);
      }
   }

   private boolean steady(BlockState toPlace, BlockPos targetPos, BlockHitResult result, float[] rot, int slot, boolean skipChestType, boolean sneaking) {
      Vec3d at = result.getPos();
      Direction face = result.getSide();

      for (Direction side : Direction.values()) {
         if (side.getAxis() != face.getAxis()) {
            Vec3d moved = at.add(side.getOffsetX() * 0.02, side.getOffsetY() * 0.02, side.getOffsetZ() * 0.02);
            BlockHitResult nudged = new BlockHitResult(moved, face, result.getBlockPos(), result.isInsideBlock());
            if (!this.wouldPlace(toPlace, targetPos, nudged, rot, slot, skipChestType, sneaking)) {
               return false;
            }
         }
      }

      for (float[] turn : STEADY_TURNS) {
         if (!this.wouldPlace(toPlace, targetPos, result, new float[]{rot[0] + turn[0], rot[1] + turn[1]}, slot, skipChestType, sneaking)) {
            return false;
         }
      }

      return true;
   }

   private Vec3d builderEye(Vec3d eyePos, boolean sneaking) {
      return this.builderEyes && !Double.isNaN(this.feetY)
         ? new Vec3d(eyePos.x, this.feetY + this.mc.player.getEyeHeight(sneaking ? EntityPose.CROUCHING : EntityPose.STANDING), eyePos.z)
         : eyePos;
   }

   private int findItem(BlockState desired, boolean wholeInventory) {
      Item required = BlockStateUtil.requiredItem(desired);
      if (required != null && required instanceof BlockItem && this.mc.player != null) {
         PlayerInventory inventory = this.mc.player.getInventory();
         int end = wholeInventory ? 36 : 9;

         for (int i = 0; i < end; i++) {
            if (inventory.getStack(i).isOf(required)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private boolean wholeInventory(int slot) {
      return slot >= 9 || this.currentScope != null;
   }

   private boolean wouldPlace(BlockState desired, BlockPos targetPos, BlockHitResult result, float[] rot, int slot, boolean skipChestType, boolean sneaking) {
      ClientPlayerEntity player = this.mc.player;
      PlayerInput keys = player.input.playerInput;
      ItemStack stack = player.getInventory().getStack(slot);
      if (stack.getItem() instanceof BlockItem blockItem) {
         float var25 = player.getYaw();
         float originalPitch = player.getPitch();
         float delta = MathHelper.wrapDegrees(rot[0] - var25);
         player.setYaw(var25 + delta);
         player.setPitch(rot[1]);
         player.input.playerInput = new PlayerInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), sneaking, keys.sprint());

         try {
            ItemPlacementContext context = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, result);
            if (context.canPlace() && context.getBlockPos().equals(targetPos)) {
               BlockState wouldBe = ((InvokerBlockItem)blockItem).easyplace$getPlacementState(context);
               if (wouldBe == null) {
                  return false;
               } else if (desired.getBlock() instanceof ChestBlock && wouldBe.getBlock() instanceof ChestBlock) {
                  ChestType want = desired.get(Properties.CHEST_TYPE);
                  ChestType got = wouldBe.get(Properties.CHEST_TYPE);
                  return got == want || got == ChestType.SINGLE && skipChestType ? StateRules.wouldFit(wouldBe, desired, true) : false;
               } else {
                  return StateRules.wouldFit(wouldBe, desired, false);
               }
            } else {
               return false;
            }
         } catch (Exception ignored) {
            return false;
         } finally {
            player.setYaw(var25);
            player.setPitch(originalPitch);
            player.input.playerInput = keys;
         }
      } else {
         return false;
      }
   }

   private static Vec3d[] aabbSideMultipliers(Direction dir) {
      return switch (dir) {
         case DOWN -> new Vec3d[]{
            new Vec3d(0.5, 0.0, 0.5),
            new Vec3d(0.1, 0.0, 0.5),
            new Vec3d(0.9, 0.0, 0.5),
            new Vec3d(0.5, 0.0, 0.1),
            new Vec3d(0.5, 0.0, 0.9),
            new Vec3d(0.03, 0.0, 0.5),
            new Vec3d(0.97, 0.0, 0.5),
            new Vec3d(0.5, 0.0, 0.03),
            new Vec3d(0.5, 0.0, 0.97),
            new Vec3d(0.25, 0.0, 0.25),
            new Vec3d(0.75, 0.0, 0.25),
            new Vec3d(0.25, 0.0, 0.75),
            new Vec3d(0.75, 0.0, 0.75)
         };
         case UP -> new Vec3d[]{
            new Vec3d(0.5, 1.0, 0.5),
            new Vec3d(0.1, 1.0, 0.5),
            new Vec3d(0.9, 1.0, 0.5),
            new Vec3d(0.5, 1.0, 0.1),
            new Vec3d(0.5, 1.0, 0.9),
            new Vec3d(0.25, 1.0, 0.25),
            new Vec3d(0.75, 1.0, 0.25),
            new Vec3d(0.25, 1.0, 0.75),
            new Vec3d(0.75, 1.0, 0.75)
         };
         default -> {
            double x = dir.getOffsetX() == 0 ? 0.5 : (1 + dir.getOffsetX()) / 2.0;
            double z = dir.getOffsetZ() == 0 ? 0.5 : (1 + dir.getOffsetZ()) / 2.0;
            yield new Vec3d[]{
               new Vec3d(x, 0.5, z),
               new Vec3d(x, 0.25, z),
               new Vec3d(x, 0.75, z),
               new Vec3d(dir.getOffsetX() == 0 ? 0.15 : x, 0.5, dir.getOffsetZ() == 0 ? 0.15 : z),
               new Vec3d(dir.getOffsetX() == 0 ? 0.85 : x, 0.5, dir.getOffsetZ() == 0 ? 0.85 : z)
            };
         }
      };
   }

   private static boolean isReplaceable(BlockState state) {
      if (state.isAir()) {
         return true;
      } else {
         Block block = state.getBlock();
         if (block instanceof FluidBlock) {
            return true;
         } else if (block instanceof CropBlock && state.contains(CropBlock.AGE) && state.get(CropBlock.AGE) == 7) {
            return true;
         } else {
            return !(block instanceof FireBlock) && !(block instanceof ChorusFlowerBlock) ? state.isReplaceable() : true;
         }
      }
   }

   private boolean isInRenderLayer(BlockPos pos) {
      try {
         LayerRange range = DataManager.getRenderLayerRange();
         return range == null || range.isPositionWithinRange(pos);
      } catch (Exception e) {
         return true;
      }
   }
}
