package net.anubis.client.easyplace.builder;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class Breaker {
   private static final int TIME_LIMIT = 100;
   private static final int LEAN_TICKS = 40;
   private static final float TURN = 22.0F;
   private static final Random HAND = new Random();
   private static final float WOBBLE_CHANCE = 0.3F;
   private static final double WOBBLE_DEGREES = 0.35;
   private final BlockPos pos;
   private final boolean mayLean;
   private final Direction side;
   private static final float VARIED_CHANCE = 0.2F;
   private final boolean varied = HAND.nextFloat() < 0.2F;
   private final double u = this.varied ? MathHelper.clamp(0.5 + HAND.nextGaussian() * 0.12, 0.22, 0.78) : 0.5;
   private final double v = this.varied ? MathHelper.clamp(0.5 + HAND.nextGaussian() * 0.12, 0.22, 0.78) : 0.5;
   private Direction face;
   private Direction leanWay;
   private boolean leaning;
   private float yaw;
   private float pitch;
   private int phase;
   private int ticks;
   private int react = -1;

   Breaker(BlockPos pos, boolean mayLean) {
      this(pos, mayLean, null);
   }

   Breaker(BlockPos pos, boolean mayLean, Direction side) {
      this.pos = pos;
      this.mayLean = mayLean;
      this.side = side;
   }

   private Aim.Shot shot(MinecraftClient mc) {
      return this.side == null ? Aim.at(mc, this.pos) : Aim.mine(mc, this.pos, this.side, this.u, this.v);
   }

   BlockPos pos() {
      return this.pos;
   }

   Breaker.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null) {
         return this.finish(mc, Breaker.Result.FAILED);
      }

      if (mc.world.getBlockState(this.pos).isAir()) {
         if (this.phase >= 2) {
            mc.interactionManager.cancelBlockBreaking();
         }

         return this.finish(mc, Breaker.Result.DONE);
      } else {
         if (++this.ticks > 100) {
            mc.interactionManager.cancelBlockBreaking();
            return this.finish(mc, Breaker.Result.FAILED);
         }

         switch (this.phase) {
            case 0:
               Aim.Shot shot = this.shot(mc);
               if (shot == null) {
                  if (this.mayLean && this.ticks <= 40) {
                     if (this.leanWay == null) {
                        this.leanWay = this.leanWay(mc, player);
                     }

                     if (this.leanWay == null) {
                        return this.finish(mc, Breaker.Result.FAILED);
                     }

                     this.leaning = true;
                     mc.options.sneakKey.setPressed(true);
                     float off = Aim.turn(mc, this.leanWay.getPositiveHorizontalDegrees(), 70.0F, 22.0F);
                     mc.options.forwardKey.setPressed(off < 20.0F);
                     return Breaker.Result.RUNNING;
                  }

                  return this.finish(mc, Breaker.Result.FAILED);
               }

               if (this.leaning) {
                  mc.options.forwardKey.setPressed(false);
               }

               this.face = shot.hit().getSide();
               this.yaw = shot.yaw();
               this.pitch = shot.pitch();
               this.phase = 1;
               break;
            case 1:
               if (this.react < 0) {
                  if (!(Aim.turn(mc, this.yaw, this.pitch, 22.0F) > 1.0F) && !(Math.abs(player.getPitch() - this.pitch) > 1.0F)) {
                     Aim.Shot aimShot = this.shot(mc);
                     if (aimShot == null) {
                        this.phase = 0;
                        return Breaker.Result.RUNNING;
                     }

                     this.face = aimShot.hit().getSide();
                     Aim.look(mc, aimShot.yaw(), aimShot.pitch());
                     this.yaw = player.getYaw();
                     this.pitch = player.getPitch();
                     this.react = this.varied ? (int)MathHelper.clamp(Math.round(Math.exp(Math.log(2.6) + HAND.nextGaussian() * 0.35)), 2L, 6L) : 2;
                     return Breaker.Result.RUNNING;
                  }

                  return Breaker.Result.RUNNING;
               }

               if (--this.react > 0) {
                  return Breaker.Result.RUNNING;
               }

               BlockHitResult hit = Aim.trace(mc, player, player.getYaw(), player.getPitch(), player.getBlockInteractionRange());
               if (hit == null || hit.getType() != Type.BLOCK || !hit.getBlockPos().equals(this.pos) || hit.getSide() != this.face) {
                  this.react = -1;
                  return Breaker.Result.RUNNING;
               }

               mc.interactionManager.attackBlock(this.pos, this.face);
               player.swingHand(Hand.MAIN_HAND);
               this.phase = 2;
               break;
            default:
               if (this.side != null && this.varied && HAND.nextFloat() < 0.3F) {
                  this.wobble(mc, player);
               }

               Aim.look(mc, this.yaw, this.pitch);
               mc.interactionManager.updateBlockBreakingProgress(this.pos, this.face);
               player.swingHand(Hand.MAIN_HAND);
         }

         return Breaker.Result.RUNNING;
      }
   }

   private void wobble(MinecraftClient mc, ClientPlayerEntity player) {
      float nextYaw = this.yaw + (float)(HAND.nextGaussian() * 0.35);
      float nextPitch = this.pitch + (float)(HAND.nextGaussian() * 0.35 * 0.7);
      BlockHitResult hit = Aim.trace(mc, player, nextYaw, nextPitch, player.getBlockInteractionRange());
      if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(this.pos) && hit.getSide() == this.face) {
         Aim.look(mc, nextYaw, nextPitch);
         this.yaw = player.getYaw();
         this.pitch = player.getPitch();
      }
   }

   private Direction leanWay(MinecraftClient mc, ClientPlayerEntity player) {
      BlockPos feet = player.getBlockPos();
      Direction best = null;

      for (Direction way : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         if (mc.world.getBlockState(this.pos.offset(way)).isReplaceable()) {
            BlockPos beside = feet.offset(way);
            if (mc.world.getBlockState(beside).getCollisionShape(mc.world, beside).isEmpty()
               && mc.world.getBlockState(beside.up()).getCollisionShape(mc.world, beside.up()).isEmpty()
               && (
                  best == null
                     || Math.abs(MathHelper.wrapDegrees(way.getPositiveHorizontalDegrees() - player.getYaw()))
                        < Math.abs(MathHelper.wrapDegrees(best.getPositiveHorizontalDegrees() - player.getYaw()))
               )) {
               best = way;
            }
         }
      }

      return best;
   }

   void release(MinecraftClient mc) {
      this.finish(mc, Breaker.Result.FAILED);
   }

   private Breaker.Result finish(MinecraftClient mc, Breaker.Result result) {
      if (this.leaning) {
         mc.options.forwardKey.setPressed(false);
         mc.options.sneakKey.setPressed(false);
         this.leaning = false;
      }

      return result;
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
