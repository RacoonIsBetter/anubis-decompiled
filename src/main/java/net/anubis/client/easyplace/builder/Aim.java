package net.anubis.client.easyplace.builder;

import java.util.Random;
import net.anubis.client.easyplace.mixin.AccessorMouseHandler;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class Aim {
   private static final Direction[] ORDER = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
   private static final double[][] FACE_POINTS = new double[][]{{0.5, 0.5}, {0.5, 0.85}, {0.5, 0.15}, {0.15, 0.5}, {0.85, 0.5}};
   private static final float SETTLED = 0.6F;
   private static final float RETARGET = 25.0F;
   private static Aim.Motion motion;
   private static final Random HAND = new Random();
   public static final float HAND_TURN = 22.0F;

   private Aim() {
   }

   static Aim.Shot at(MinecraftClient mc, BlockPos pos) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         BlockState state = mc.world.getBlockState(pos);
         VoxelShape shape = state.getOutlineShape(mc.world, pos);
         if (shape.isEmpty()) {
            return null;
         }

         Box box = shape.getBoundingBox().offset(pos);
         Vec3d eye = player.getEyePos();
         double reach = player.getBlockInteractionRange();

         for (Direction face : ORDER) {
            if (faces(box, face, eye)) {
               Vec3d point = faceCenter(box, face);
               if (!(point.distanceTo(eye) > reach)) {
                  float[] rot = PlacementUtil.calcRotation(eye, point);
                  BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach);
                  if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getSide() == face) {
                     return new Aim.Shot(hit, rot[0], rot[1]);
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static Aim.Shot against(MinecraftClient mc, BlockPos cell, Fluid fluid) {
      return mc.player == null ? null : against(mc, cell, fluid, mc.player.getEyePos());
   }

   static Aim.Shot against(MinecraftClient mc, BlockPos cell, Fluid fluid, Vec3d eye) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         double reach = player.getBlockInteractionRange();

         for (Direction direction : ORDER) {
            BlockPos next = cell.offset(direction.getOpposite());
            BlockState state = mc.world.getBlockState(next);
            if (!state.isReplaceable()
               && !clickable(mc, next, state)
               && (fluid == null || !(state.getBlock() instanceof FluidFillable container && container.canFillWithFluid(player, mc.world, next, state, fluid)))
               )
             {
               VoxelShape shape = state.getOutlineShape(mc.world, next);
               if (!shape.isEmpty()) {
                  Box box = shape.getBoundingBox().offset(next);
                  if (faces(box, direction, eye)) {
                     for (double[] at : FACE_POINTS) {
                        Vec3d point = facePoint(box, direction, at[0], at[1]);
                        if (!(point.distanceTo(eye) > reach)) {
                           float[] rot = PlacementUtil.calcRotation(eye, point);
                           BlockHitResult hit = PlacementUtil.rayTraceFrom(player, eye, rot[0], rot[1], reach);
                           if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(next) && hit.getSide() == direction) {
                              return new Aim.Shot(hit, rot[0], rot[1]);
                           }
                        }
                     }
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Vec3d facePoint(Box box, Direction face, double u, double v) {
      double x = box.minX + (box.maxX - box.minX) * u;
      double z = box.minZ + (box.maxZ - box.minZ) * (face.getAxis() == Axis.Y ? v : u);
      double y = box.minY + (box.maxY - box.minY) * v;

      return switch (face) {
         case UP -> new Vec3d(x, box.maxY, z);
         case DOWN -> new Vec3d(x, box.minY, z);
         case NORTH -> new Vec3d(x, y, box.minZ);
         case SOUTH -> new Vec3d(x, y, box.maxZ);
         case WEST -> new Vec3d(box.minX, y, z);
         case EAST -> new Vec3d(box.maxX, y, z);
      };
   }

   static Aim.Shot side(MinecraftClient mc, BlockPos block, Direction face) {
      return side(mc, block, face, new double[][]{{0.5, 0.85}, {0.5, 0.5}, {0.5, 0.2}});
   }

   static Aim.Shot mine(MinecraftClient mc, BlockPos block, Direction face, double u, double v) {
      return side(mc, block, face, new double[][]{{u, v}, {0.5, 0.5}, {0.5, 0.85}, {0.5, 0.2}});
   }

   private static Aim.Shot side(MinecraftClient mc, BlockPos block, Direction face, double[][] spots) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         VoxelShape shape = mc.world.getBlockState(block).getOutlineShape(mc.world, block);
         if (shape.isEmpty()) {
            return null;
         }

         Box box = shape.getBoundingBox().offset(block);
         Vec3d eye = player.getEyePos();
         double reach = player.getBlockInteractionRange();
         if (!faces(box, face, eye)) {
            return null;
         }

         for (double[] spot : spots) {
            Vec3d point = facePoint(box, face, spot[0], spot[1]);
            if (!(point.distanceTo(eye) > reach)) {
               float[] rot = PlacementUtil.calcRotation(eye, point);
               BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach);
               if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(block) && hit.getSide() == face) {
                  return new Aim.Shot(hit, rot[0], rot[1]);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static Aim.Shot water(MinecraftClient mc, BlockPos pos) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         Vec3d eye = player.getEyePos();
         double reach = player.getBlockInteractionRange();
         Vec3d point = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
         if (!(eye.y <= point.y) && !(point.distanceTo(eye) > reach)) {
            float[] rot = PlacementUtil.calcRotation(eye, point);
            BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach, FluidHandling.SOURCE_ONLY);
            return hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) ? new Aim.Shot(hit, rot[0], rot[1]) : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   static boolean clickable(MinecraftClient mc, BlockPos pos) {
      return clickable(mc, pos, mc.world.getBlockState(pos));
   }

   private static boolean clickable(MinecraftClient mc, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      return mc.world.getBlockEntity(pos) != null
         || block instanceof DoorBlock
         || block instanceof TrapdoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof AbstractRedstoneGateBlock
         || block instanceof NoteBlock;
   }

   static void look(MinecraftClient mc, float yaw, float pitch) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         clearMouse(mc);
         motion = null;
         float current = player.getYaw();
         float step = mouseStep(mc);
         player.setYaw(current + mouse(MathHelper.wrapDegrees(yaw - current), step));
         player.setPitch(MathHelper.clamp(player.getPitch() + mouse(pitch - player.getPitch(), step), -90.0F, 90.0F));
      }
   }

   static float mouseStep(MinecraftClient mc) {
      double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      return (float)(s * s * s * 8.0 * 0.15);
   }

   private static float mouse(float degrees, float step) {
      return step <= 0.0F ? degrees : Math.round(degrees / step) * step;
   }

   public static float turn(MinecraftClient mc, float yaw, float pitch, float maxStep) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         return 0.0F;
      }

      clearMouse(mc);
      float yawNow = player.getYaw();
      float pitchNow = player.getPitch();
      float yawLeft = MathHelper.wrapDegrees(yaw - yawNow);
      float pitchLeft = pitch - pitchNow;
      float yawStep;
      float pitchStep;
      if (Math.abs(yawLeft) < 0.6F && Math.abs(pitchLeft) < 0.6F) {
         motion = null;
         yawStep = yawLeft;
         pitchStep = pitchLeft;
      } else {
         if (motion != null && !motion.done() && !motion.drifted(yaw, pitch)) {
            motion.retarget(yaw, pitch);
         } else {
            motion = Aim.Motion.start(yawNow, pitchNow, yaw, pitch, maxStep);
         }

         float[] want = motion.next();
         yawStep = MathHelper.clamp(MathHelper.wrapDegrees(want[0] - yawNow), -maxStep, maxStep);
         pitchStep = MathHelper.clamp(want[1] - pitchNow, -maxStep, maxStep);
      }

      float step = mouseStep(mc);
      player.setYaw(yawNow + mouse(yawStep, step));
      player.setPitch(MathHelper.clamp(pitchNow + mouse(pitchStep, step), -90.0F, 90.0F));
      return Math.abs(MathHelper.wrapDegrees(yaw - player.getYaw()));
   }

   private static void clearMouse(MinecraftClient mc) {
      if (mc.mouse != null) {
         AccessorMouseHandler mouse = (AccessorMouseHandler)mc.mouse;
         mouse.easyplace$setAccumulatedDX(0.0);
         mouse.easyplace$setAccumulatedDY(0.0);
      }
   }

   static boolean looking(ClientPlayerEntity player, float yaw, float pitch) {
      return Math.abs(MathHelper.wrapDegrees(player.getYaw() - yaw)) < 0.5F && Math.abs(player.getPitch() - pitch) < 0.5F;
   }

   static BlockHitResult trace(MinecraftClient mc, ClientPlayerEntity player, float yaw, float pitch, double reach) {
      return trace(mc, player, yaw, pitch, reach, FluidHandling.NONE);
   }

   private static BlockHitResult trace(MinecraftClient mc, ClientPlayerEntity player, float yaw, float pitch, double reach, FluidHandling fluids) {
      Vec3d eye = player.getEyePos();
      double yawRad = Math.toRadians(yaw);
      double cosPitch = Math.cos(Math.toRadians(pitch));
      Vec3d direction = new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(Math.toRadians(pitch)), Math.cos(yawRad) * cosPitch);
      return mc.world.raycast(new RaycastContext(eye, eye.add(direction.multiply(reach)), ShapeType.OUTLINE, fluids, player));
   }

   private static boolean faces(Box box, Direction face, Vec3d eye) {
      double gap = 0.02;

      return switch (face) {
         case UP -> eye.y > box.maxY + gap;
         case DOWN -> eye.y < box.minY - gap;
         case NORTH -> eye.z < box.minZ - gap;
         case SOUTH -> eye.z > box.maxZ + gap;
         case WEST -> eye.x < box.minX - gap;
         case EAST -> eye.x > box.maxX + gap;
      };
   }

   private static Vec3d faceCenter(Box box, Direction face) {
      Vec3d center = box.getCenter();

      return switch (face) {
         case UP -> new Vec3d(center.x, box.maxY, center.z);
         case DOWN -> new Vec3d(center.x, box.minY, center.z);
         case NORTH -> new Vec3d(center.x, center.y, box.minZ);
         case SOUTH -> new Vec3d(center.x, center.y, box.maxZ);
         case WEST -> new Vec3d(box.minX, center.y, center.z);
         case EAST -> new Vec3d(box.maxX, center.y, center.z);
      };
   }

   @Environment(EnvType.CLIENT)
   private static final class Motion {
      private final float startYaw;
      private final float startPitch;
      private final float bend;
      private final int ticks;
      private float endYaw;
      private float endPitch;
      private float targetYaw;
      private float targetPitch;
      private int elapsed;

      private Motion(float startYaw, float startPitch, float endYaw, float endPitch, float targetYaw, float targetPitch, float bend, int ticks) {
         this.startYaw = startYaw;
         this.startPitch = startPitch;
         this.endYaw = endYaw;
         this.endPitch = endPitch;
         this.targetYaw = targetYaw;
         this.targetPitch = targetPitch;
         this.bend = bend;
         this.ticks = ticks;
      }

      static Aim.Motion start(float yaw0, float pitch0, float yaw, float pitch, float maxStep) {
         float dy = MathHelper.wrapDegrees(yaw - yaw0);
         float dp = pitch - pitch0;
         double distance = Math.hypot(dy, dp);
         double seconds = (0.07 + 0.055 * Math.log(1.0 + distance / 3.0) / Math.log(2.0)) * Math.exp(Aim.HAND.nextGaussian() * 0.22);
         int ticks = (int)Math.max(Math.ceil(distance * 1.9 / maxStep), Math.max(1L, Math.round(seconds * 20.0)));
         double miss = distance > 6.0 ? Aim.HAND.nextGaussian() * 0.045 : 0.0;
         double scatter = Math.min(1.2, distance * 0.015);
         float endYaw = (float)(yaw0 + dy * (1.0 + miss) + Aim.HAND.nextGaussian() * scatter);
         float endPitch = (float)(pitch0 + dp * (1.0 + miss) + Aim.HAND.nextGaussian() * scatter * 0.7);
         float bend = (float)(Aim.HAND.nextGaussian() * 0.06);
         return new Aim.Motion(yaw0, pitch0, endYaw, endPitch, yaw0 + dy, pitch, bend, ticks);
      }

      float[] next() {
         this.elapsed++;
         double t = Math.min(1.0, (double)this.elapsed / this.ticks);
         double s = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
         double bulge = Math.sin(Math.PI * t) * this.bend;
         float dy = this.endYaw - this.startYaw;
         float dp = this.endPitch - this.startPitch;
         return new float[]{(float)(this.startYaw + dy * s - dp * bulge), (float)(this.startPitch + dp * s + dy * bulge)};
      }

      boolean done() {
         return this.elapsed >= this.ticks;
      }

      boolean drifted(float yaw, float pitch) {
         return Math.hypot(MathHelper.wrapDegrees(yaw - this.targetYaw), pitch - this.targetPitch) > 25.0;
      }

      void retarget(float yaw, float pitch) {
         float dy = MathHelper.wrapDegrees(yaw - this.targetYaw);
         float dp = pitch - this.targetPitch;
         this.endYaw += dy;
         this.endPitch += dp;
         this.targetYaw += dy;
         this.targetPitch = pitch;
      }
   }

   @Environment(EnvType.CLIENT)
   record Shot(BlockHitResult hit, float yaw, float pitch) {
   }
}
