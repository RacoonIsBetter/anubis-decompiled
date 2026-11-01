package net.anubis.client.module.impl.combat.bot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.anubis.client.easyplace.builder.Aim;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
final class BotBody {
   private static final double KEEP_EXTRA = 8.0;
   private static final float FAR_OFF = 40.0F;
   private static final int FLIP_COOLDOWN = 8;
   private static final int DROP_SCAN = 16;
   private PlayerEntity target;
   private boolean grounded;
   private int side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
   private int sideUntil;
   private int flippedAt = -100;
   private double sideCarry;
   private int tick;

   PlayerEntity target() {
      return this.target;
   }

   int tick() {
      return this.tick;
   }

   void forget() {
      this.target = null;
   }

   void hold(PlayerEntity who) {
      this.target = who;
   }

   void stayGrounded(boolean grounded) {
      this.grounded = grounded;
   }

   void release(MinecraftClient mc) {
      mc.options.forwardKey.setPressed(false);
      mc.options.backKey.setPressed(false);
      mc.options.leftKey.setPressed(false);
      mc.options.rightKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
      mc.options.sprintKey.setPressed(false);
      mc.options.useKey.setPressed(false);
   }

   PlayerEntity pick(MinecraftClient mc, ClientPlayerEntity self, double range) {
      return this.pick(mc, self, range, range + 8.0);
   }

   PlayerEntity pick(MinecraftClient mc, ClientPlayerEntity self, double range, double keep) {
      this.tick++;
      if (this.tick >= this.sideUntil) {
         this.side = -this.side;
         this.sideUntil = this.tick + 10 + ThreadLocalRandom.current().nextInt(13);
      }

      if (this.target != null
         && (
            !this.target.isAlive()
               || this.target.isRemoved()
               || this.target.getEntityWorld() != self.getEntityWorld()
               || this.target.distanceTo(self) > keep
               || !MeleeAttacks.isEnemy(self, this.target)
         )) {
         this.target = null;
      }

      if (this.target != null) {
         return this.target;
      }

      PlayerEntity best = null;

      for (PlayerEntity other : mc.world.getPlayers()) {
         if (other != self && other.isAlive() && !other.isSpectator() && MeleeAttacks.isEnemy(self, other)) {
            double distance = other.distanceTo(self);
            if (distance <= range && (best == null || distance < best.distanceTo(self))) {
               best = other;
            }
         }
      }

      this.target = best;
      return best;
   }

   void flipSide() {
      if (this.tick - this.flippedAt >= 8) {
         this.flippedAt = this.tick;
         this.side = -this.side;
         this.sideUntil = this.tick + 10 + ThreadLocalRandom.current().nextInt(13);
      }
   }

   void face(MinecraftClient mc, ClientPlayerEntity self, PlayerEntity who, float speed) {
      Vec3d eye = self.getEyePos();
      Vec3d drift = who.getEntityPos().subtract(who.lastX, who.lastY, who.lastZ);
      Box box = who.getBoundingBox().offset(drift);
      Box core = box.contract(box.getLengthX() * 0.2, box.getLengthY() * 0.15, box.getLengthZ() * 0.2);
      Rotation look = RotationUtils.fromTo(eye, TargetingUtils.closestPoint(eye, core));
      float off = Math.abs(MathHelper.wrapDegrees(look.yaw() - self.getYaw()));
      Aim.turn(mc, look.yaw(), look.pitch(), (off > 40.0F ? 27.0F : 22.0F) * speed);
   }

   void drive(MinecraftClient mc, ClientPlayerEntity self, int forward, double sideways, boolean sprint, boolean jump) {
      float yaw = self.getYaw();
      Vec3d ahead = Vec3d.fromPolar(0.0F, yaw);
      if (forward > 0 && safeStep(self, ahead)) {
         mc.options.forwardKey.setPressed(true);
         mc.options.sprintKey.setPressed(sprint);
         if (self.horizontalCollision && self.isOnGround()) {
            jump = true;
         }
      } else if (forward < 0 && safeStep(self, ahead.multiply(-1.0))) {
         mc.options.backKey.setPressed(true);
      }

      if (jump && !this.grounded && self.isOnGround() && !self.isTouchingWater()) {
         mc.options.jumpKey.setPressed(true);
      }

      this.sideCarry = this.sideCarry + Math.max(0.0, Math.min(1.0, sideways));
      if (!(this.sideCarry < 1.0)) {
         this.sideCarry--;
         Vec3d left = Vec3d.fromPolar(0.0F, yaw - 90.0F);
         Vec3d way = this.side > 0 ? left : left.multiply(-1.0);
         if (!safeStep(self, way)) {
            this.side = -this.side;
            way = way.multiply(-1.0);
            if (!safeStep(self, way)) {
               return;
            }
         }

         (this.side > 0 ? mc.options.leftKey : mc.options.rightKey).setPressed(true);
      }
   }

   void keepDistance(MinecraftClient mc, ClientPlayerEntity self, PlayerEntity who, double min, double ideal, double max, boolean dance) {
      double distance = Math.hypot(who.getX() - self.getX(), who.getZ() - self.getZ());
      int forward = distance > ideal + 0.25 ? 1 : (distance < min ? -1 : 0);
      this.drive(mc, self, forward, dance && distance <= max + 1.0 ? 1.0 : 0.0, distance > max, false);
   }

   void flee(MinecraftClient mc, ClientPlayerEntity self, PlayerEntity from) {
      float away = RotationUtils.fromTo(from.getEyePos(), self.getEyePos()).yaw();
      Aim.turn(mc, away, 5.0F, 27.0F);
      this.drive(mc, self, 1, 0.0, true, false);
   }

   void away(MinecraftClient mc, ClientPlayerEntity self, Vec3d point) {
      Vec3d way = self.getEntityPos().subtract(point).multiply(1.0, 0.0, 1.0);
      if (way.lengthSquared() < 1.0E-6) {
         way = Vec3d.fromPolar(0.0F, self.getYaw()).multiply(-1.0);
      }

      this.go(mc, self, way);
   }

   boolean toward(MinecraftClient mc, ClientPlayerEntity self, Vec3d point) {
      Vec3d way = point.subtract(self.getEntityPos()).multiply(1.0, 0.0, 1.0);
      if (way.lengthSquared() < 0.09) {
         return true;
      }

      this.go(mc, self, way);
      if (!this.grounded && self.horizontalCollision && self.isOnGround()) {
         mc.options.jumpKey.setPressed(true);
      }

      return false;
   }

   void dropToward(MinecraftClient mc, ClientPlayerEntity self, Vec3d point) {
      Vec3d way = point.subtract(self.getEntityPos()).multiply(1.0, 0.0, 1.0);
      if (!(way.lengthSquared() < 0.09) && safeStep(self, way.normalize(), 16)) {
         keys(mc, self, way);
      }
   }

   private void go(MinecraftClient mc, ClientPlayerEntity self, Vec3d way) {
      if (safeStep(self, way.normalize())) {
         keys(mc, self, way);
      }
   }

   private static void keys(MinecraftClient mc, ClientPlayerEntity self, Vec3d way) {
      float wanted = (float)Math.toDegrees(Math.atan2(-way.x, way.z));
      float relative = MathHelper.wrapDegrees(wanted - self.getYaw());
      float off = Math.abs(relative);
      if (off <= 67.5F) {
         mc.options.forwardKey.setPressed(true);
      }

      if (off >= 112.5F) {
         mc.options.backKey.setPressed(true);
      }

      if (relative <= -22.5F && relative >= -157.5F) {
         mc.options.leftKey.setPressed(true);
      }

      if (relative >= 22.5F && relative <= 157.5F) {
         mc.options.rightKey.setPressed(true);
      }
   }

   void backOff(MinecraftClient mc, ClientPlayerEntity self) {
      this.drive(mc, self, -1, 0.0, false, false);
   }

   boolean use(MinecraftClient mc, ClientPlayerEntity self, Predicate<ItemStack> want) {
      int slot = hotbarSlot(self, want);
      if (slot < 0) {
         return false;
      } else {
         PlayerInventory inventory = self.getInventory();
         if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
            return true;
         } else {
            mc.options.useKey.setPressed(true);
            return true;
         }
      }
   }

   static int hotbarSlot(ClientPlayerEntity self, Predicate<ItemStack> want) {
      PlayerInventory inventory = self.getInventory();
      if (want.test(inventory.getStack(inventory.getSelectedSlot()))) {
         return inventory.getSelectedSlot();
      }

      for (int i = 0; i < 9; i++) {
         if (want.test(inventory.getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   static boolean safeStep(ClientPlayerEntity self, Vec3d way) {
      return safeStep(self, way, 3);
   }

   private static boolean safeStep(ClientPlayerEntity self, Vec3d way, int deepest) {
      World level = self.getEntityWorld();
      BlockPos feet = BlockPos.ofFloored(self.getX() + way.x * 0.8, self.getY() + 0.1, self.getZ() + way.z * 0.8);

      for (int down = 0; down <= deepest; down++) {
         BlockPos pos = feet.down(down);
         BlockState state = level.getBlockState(pos);
         if (state.isOf(Blocks.LAVA)
            || state.isIn(BlockTags.FIRE)
            || state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.COBWEB)
            || down <= 1 && state.isOf(Blocks.MAGMA_BLOCK)) {
            return false;
         }

         if (!state.getCollisionShape(level, pos).isEmpty()) {
            return true;
         }
      }

      return false;
   }
}
