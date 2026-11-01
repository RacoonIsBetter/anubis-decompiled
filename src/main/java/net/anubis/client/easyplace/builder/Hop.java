package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class Hop {
   private static final int TIME_LIMIT = 60;
   private static final double CENTERED = 0.15;
   private static final double STEP = 0.55;
   private static final double LINED_UP = 0.25;
   private static final double LEAP_AT = 0.3;
   private static final double LANDED = 0.55;
   private static final int SAFE_DROP = 3;
   private static final float TURN = 22.0F;
   private static final float WALK_OFF = 30.0F;
   private static final double JUMP_AT = 0.15;
   private final BlockPos spot;
   private final boolean through;
   private final boolean leap;
   private boolean sprint;
   private boolean careful;
   private boolean slow;
   private BlockPos from;
   private double floor = Double.NaN;
   private boolean centered;
   private int ticks;

   Hop(BlockPos spot) {
      this(spot, false, false);
   }

   Hop(BlockPos spot, boolean through) {
      this(spot, through, false);
   }

   Hop(BlockPos spot, boolean through, boolean leap) {
      this.spot = spot;
      this.through = through;
      this.leap = leap;
   }

   BlockPos spot() {
      return this.spot;
   }

   Hop sprinting() {
      this.sprint = true;
      return this;
   }

   Hop careful() {
      this.careful = true;
      this.sprint = false;
      return this;
   }

   Hop slow() {
      this.slow = true;
      this.sprint = false;
      return this;
   }

   static BlockPos spot(ClientWorld level, ClientPlayerEntity player, Predicate<BlockPos> avoid) {
      if (!player.isOnGround()) {
         return null;
      }

      BlockPos feet = player.getBlockPos();
      double from = Walker.floorOr(level, feet);
      BlockPos best = null;
      double bestRise = 0.45;

      for (BlockPos up : Walker.steps(level, feet)) {
         double rise = Walker.floor(level, up) - from;
         if (rise > bestRise && !avoid.test(up)) {
            bestRise = rise;
            best = up;
         }
      }

      return best;
   }

   Hop.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         return Hop.Result.FAILED;
      }

      if (this.from == null) {
         this.from = player.getBlockPos();
      }

      if (Double.isNaN(this.floor) && mc.world != null) {
         this.floor = Walker.floorOr(mc.world, this.spot);
      }

      Vec3d pos = player.getEntityPos();
      mc.options.sneakKey.setPressed(this.careful);
      double dx = this.spot.getX() + 0.5 - pos.x;
      double dz = this.spot.getZ() + 0.5 - pos.z;
      double near = this.through ? 0.35 : 0.12;
      boolean over = Math.abs(dx) < near && Math.abs(dz) < near;
      boolean settled = this.through || player.getVelocity().horizontalLengthSquared() < 0.0025;
      boolean landed = pos.y < this.floor + 0.55;
      if (over && settled && landed && player.isOnGround() && pos.y >= this.floor - 0.05) {
         if (!this.through) {
            this.release(mc);
         }

         return Hop.Result.DONE;
      } else {
         if (++this.ticks > 60) {
            this.release(mc);
            return Hop.Result.FAILED;
         }

         if (mc.world != null && player.isOnGround() && this.intoHole(mc.world, player, pos)) {
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(true);
            return Hop.Result.RUNNING;
         }

         if (!this.centered) {
            double cx = this.from.getX() + 0.5 - pos.x;
            double cz = this.from.getZ() + 0.5 - pos.z;
            double aside = this.spot.getX() != this.from.getX() ? Math.abs(cz) : Math.abs(cx);
            boolean level = this.floor - pos.y <= 0.55 && aside < 0.25;
            if (!level && (!(Math.abs(cx) < 0.15) || !(Math.abs(cz) < 0.15)) && (player.isOnGround() || this.ticks <= 10)) {
               float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-cx, cz)), player.getPitch(), 22.0F);
               mc.options.forwardKey.setPressed(off < 30.0F);
               mc.options.jumpKey.setPressed(false);
               return Hop.Result.RUNNING;
            }

            this.centered = true;
         }

         float off = Aim.turn(mc, (float)Math.toDegrees(Math.atan2(-dx, dz)), player.getPitch(), 22.0F);
         boolean moving = player.getVelocity().horizontalLengthSquared() > 4.0E-4;
         mc.options.sprintKey.setPressed(this.sprint && !over && off < 10.0F);
         boolean push = (!over || !landed) && (this.through || Math.hypot(dx, dz) > 0.3 || !moving || !landed);
         if (this.slow) {
            boolean down = player.isOnGround() && pos.y < this.floor + 0.05 && Math.hypot(dx, dz) < 0.45;
            push = push && player.getVelocity().horizontalLength() < (down ? 0.03 : 0.08);
         }

         mc.options.forwardKey.setPressed(push && off < 30.0F);
         double ahead = (pos.x - (this.from.getX() + 0.5)) * Integer.signum(this.spot.getX() - this.from.getX())
            + (pos.z - (this.from.getZ() + 0.5)) * Integer.signum(this.spot.getZ() - this.from.getZ());
         boolean atStep = player.horizontalCollision || ahead > 0.15 || this.spot.getX() == this.from.getX() && this.spot.getZ() == this.from.getZ();
         mc.options
            .jumpKey
            .setPressed(player.isOnGround() && this.floor - pos.y > 0.55 && atStep || this.leap && player.isOnGround() && ahead > 0.3 && ahead < 1.0);
         return Hop.Result.RUNNING;
      }
   }

   private boolean intoHole(ClientWorld level, ClientPlayerEntity player, Vec3d pos) {
      Vec3d motion = player.getVelocity();
      double speed = motion.horizontalLength();
      if (speed < 0.02) {
         return false;
      }

      double ahead = speed * 1.3 + 0.35;
      BlockPos cell = BlockPos.ofFloored(pos.x + motion.x / speed * ahead, pos.y + 0.01, pos.z + motion.z / speed * ahead);
      BlockPos feet = player.getBlockPos();
      return (cell.getX() != this.spot.getX() || cell.getZ() != this.spot.getZ()) && (cell.getX() != feet.getX() || cell.getZ() != feet.getZ())
         ? Walker.dropAt(level, cell) > 3
         : false;
   }

   void release(MinecraftClient mc) {
      mc.options.forwardKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
      mc.options.sprintKey.setPressed(false);
      if (this.careful) {
         mc.options.sneakKey.setPressed(false);
      }
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
