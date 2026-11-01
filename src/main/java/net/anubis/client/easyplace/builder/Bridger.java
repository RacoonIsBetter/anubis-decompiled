package net.anubis.client.easyplace.builder;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.AxisDirection;

@Environment(EnvType.CLIENT)
final class Bridger {
   private static final int EDGE_TICKS = 40;
   private static final int HOLD_TICKS = 20;
   private static final int ANSWER_TICKS = 12;
   private static final int TURN_TICKS = 30;
   private static final int STEP_TICKS = 40;
   private static final int TRIES = 2;
   private static final double LEANING = 0.18;
   private final BlockPos from;
   private final BlockPos spot;
   private final BlockPos leaf;
   private final Direction way;
   private final Set<Long> scaffold;
   private final Item material;
   private boolean chained;
   private int phase;
   private int phaseTicks;
   private int stillTicks;
   private int onAim;
   private int clickedAt = -1;
   private int tries;
   private Vec3d lastPos;

   Bridger(BlockPos from, BlockPos spot, Set<Long> scaffold, Item material) {
      this.from = from;
      this.spot = spot;
      this.leaf = spot.down();
      this.way = spot.getX() > from.getX()
         ? Direction.EAST
         : (spot.getX() < from.getX() ? Direction.WEST : (spot.getZ() > from.getZ() ? Direction.SOUTH : Direction.NORTH));
      this.scaffold = scaffold;
      this.material = material;
   }

   BlockPos spot() {
      return this.spot;
   }

   Bridger chained() {
      this.chained = true;
      return this;
   }

   Bridger.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.interactionManager != null) {
         mc.options.sneakKey.setPressed(true);
         this.phaseTicks++;
         boolean leafThere = this.material == null ? Scaffold.block(mc.world.getBlockState(this.leaf)) : !mc.world.getBlockState(this.leaf).isReplaceable();
         switch (this.phase) {
            case 0:
               if (leafThere) {
                  this.next(3);
                  return Bridger.Result.RUNNING;
               }

               if (this.material == null ? !Scaffold.has(player) : !Hands.has(player, stack -> stack.isOf(this.material))) {
                  return Bridger.Result.FAILED;
               }

               boolean held = this.material == null ? Scaffold.hold(mc, player) : Hands.hold(mc, player, stack -> stack.isOf(this.material));
               if (!held) {
                  return this.phaseTicks > 20 ? Bridger.Result.FAILED : Bridger.Result.RUNNING;
               }

               this.lastPos = player.getEntityPos();
               this.next(1);
               break;
            case 1:
               double out = this.past(player.getEntityPos());
               Vec3d pos = player.getEntityPos();
               this.stillTicks = pos.squaredDistanceTo(this.lastPos) < 1.0E-4 ? this.stillTicks + 1 : 0;
               this.lastPos = pos;
               if (out > 0.18 || out > 0.03 && this.stillTicks >= 3) {
                  mc.options.forwardKey.setPressed(false);
                  this.next(2);
                  return Bridger.Result.RUNNING;
               }

               if (this.phaseTicks > 40) {
                  return Bridger.Result.FAILED;
               }

               float off = Aim.turn(mc, this.way.getPositiveHorizontalDegrees(), 30.0F, 22.0F);
               mc.options.forwardKey.setPressed(off < 20.0F);
               break;
            case 2:
               if (leafThere) {
                  if (this.material == null) {
                     this.scaffold.add(this.leaf.asLong());
                  }

                  this.next(3);
                  return Bridger.Result.RUNNING;
               }

               if (!player.isSneaking()) {
                  if (this.phaseTicks > 10) {
                     return this.retry(mc);
                  }

                  return Bridger.Result.RUNNING;
               }

               if (this.clickedAt < 0) {
                  Aim.Shot shot = Aim.side(mc, this.from.down(), this.way);
                  if (shot == null) {
                     return this.retry(mc);
                  }

                  Aim.turn(mc, shot.yaw(), shot.pitch(), 22.0F);
                  if (!Aim.looking(player, shot.yaw(), shot.pitch())) {
                     this.onAim = 0;
                     if (this.phaseTicks > 30) {
                        return this.retry(mc);
                     }

                     return Bridger.Result.RUNNING;
                  }

                  if (++this.onAim < 2) {
                     return Bridger.Result.RUNNING;
                  }

                  ItemStack hand = player.getMainHandStack();
                  if (this.material == null ? !Scaffold.item(hand) : !hand.isOf(this.material)) {
                     if (++this.tries >= 2) {
                        return Bridger.Result.FAILED;
                     }

                     this.next(0);
                     return Bridger.Result.RUNNING;
                  }

                  if (mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, shot.hit()).isAccepted()) {
                     player.swingHand(Hand.MAIN_HAND);
                  }

                  this.clickedAt = this.phaseTicks;
               } else if (this.phaseTicks - this.clickedAt > 12) {
                  return this.retry(mc);
               }
               break;
            case 3:
               Vec3d here = player.getEntityPos();
               double dx = this.spot.getX() + 0.5 - here.x;
               double dz = this.spot.getZ() + 0.5 - here.z;
               boolean there = player.isOnGround() && player.getBlockPos().equals(this.spot);
               if (there && (this.chained || Math.abs(dx) < 0.25 && Math.abs(dz) < 0.25)) {
                  this.release(mc);
                  return Bridger.Result.DONE;
               }

               if (this.phaseTicks > 40) {
                  return Bridger.Result.FAILED;
               }

               float turnOff = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-dx, dz)), 30.0F, 22.0F);
               mc.options.forwardKey.setPressed(turnOff < 30.0F);
               break;
            default:
               return Bridger.Result.FAILED;
         }

         return Bridger.Result.RUNNING;
      } else {
         return Bridger.Result.FAILED;
      }
   }

   private double past(Vec3d pos) {
      double edge = this.way.getDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;

      return switch (this.way.getAxis()) {
         case X -> (pos.x - (this.from.getX() + edge)) * this.way.getOffsetX();
         case Z -> (pos.z - (this.from.getZ() + edge)) * this.way.getOffsetZ();
         default -> 0.0;
      };
   }

   private Bridger.Result retry(MinecraftClient mc) {
      if (++this.tries >= 2) {
         return Bridger.Result.FAILED;
      }

      this.lastPos = mc.player == null ? this.lastPos : mc.player.getEntityPos();
      this.next(1);
      return Bridger.Result.RUNNING;
   }

   private void next(int value) {
      this.phase = value;
      this.phaseTicks = 0;
      this.stillTicks = 0;
      this.onAim = 0;
      this.clickedAt = -1;
   }

   void release(MinecraftClient mc) {
      mc.options.forwardKey.setPressed(false);
      mc.options.sneakKey.setPressed(false);
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
