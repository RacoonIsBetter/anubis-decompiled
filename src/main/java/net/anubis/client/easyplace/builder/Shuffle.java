package net.anubis.client.easyplace.builder;

import net.anubis.client.easyplace.util.PlacementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class Shuffle {
   private static final double CLOSE = 0.3;
   private static final double SLOW = 0.0025;
   private static final double KEY = 0.12;
   private static final double SLIDE = 1.2;
   private static final int TIME_LIMIT = 60;
   private static final double QUICK_SLOW = 0.0144;
   private final Vec3d spot;
   private final Vec3d look;
   private final double slow;
   private int ticks;

   Shuffle(Vec3d spot, Vec3d look, boolean quick) {
      this.spot = spot;
      this.look = look;
      this.slow = quick ? 0.0144 : 0.0025;
   }

   boolean quick() {
      return this.slow == 0.0144;
   }

   Vec3d spot() {
      return this.spot;
   }

   Shuffle.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         return Shuffle.Result.FAILED;
      } else {
         float[] rot = PlacementUtil.calcRotation(player.getEyePos(), this.look);
         Aim.turn(mc, rot[0], rot[1], 22.0F);
         double dx = this.spot.x - player.getX();
         double dz = this.spot.z - player.getZ();
         Vec3d motion = player.getVelocity();
         double left = Math.hypot(dx, dz);
         if (left < 0.3 && motion.horizontalLengthSquared() < this.slow) {
            this.release(mc);
            return Shuffle.Result.DONE;
         } else if (++this.ticks > 60) {
            this.release(mc);
            return left < 0.45 ? Shuffle.Result.DONE : Shuffle.Result.FAILED;
         } else {
            double yaw = Math.toRadians(player.getYaw());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double ahead = -sin * dx + cos * dz - 1.2 * (-sin * motion.x + cos * motion.z);
            double toLeft = cos * dx + sin * dz - 1.2 * (cos * motion.x + sin * motion.z);
            mc.options.forwardKey.setPressed(ahead > 0.12);
            mc.options.backKey.setPressed(ahead < -0.12);
            mc.options.leftKey.setPressed(toLeft > 0.12);
            mc.options.rightKey.setPressed(toLeft < -0.12);
            return Shuffle.Result.RUNNING;
         }
      }
   }

   void release(MinecraftClient mc) {
      mc.options.forwardKey.setPressed(false);
      mc.options.backKey.setPressed(false);
      mc.options.leftKey.setPressed(false);
      mc.options.rightKey.setPressed(false);
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
