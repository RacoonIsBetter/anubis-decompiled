package net.anubis.client.module.impl.movement.speed;

import net.anubis.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class VanillaSpeedMode extends SpeedMode {
   private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);

   public VanillaSpeedMode(SpeedModule settings) {
      super(settings);
   }

   @Override
   public Vec3d onMove(ClientPlayerEntity player, Vec3d movement) {
      Vec3d vel = horizontalVelocity(player, this.settings.vanillaSpeed());
      double velX = vel.x;
      double velZ = vel.z;
      StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
      if (speedEffect != null) {
         double value = (speedEffect.getAmplifier() + 1) * 0.205;
         velX += velX * value;
         velZ += velZ * value;
      }

      return new Vec3d(velX, movement.y, velZ);
   }

   private static Vec3d horizontalVelocity(ClientPlayerEntity player, double bps) {
      float yaw = player.getYaw();
      Vec3d forward = Vec3d.fromPolar(0.0F, yaw);
      Vec3d right = Vec3d.fromPolar(0.0F, yaw + 90.0F);
      PlayerInput keys = player.input.playerInput;
      double velX = 0.0;
      double velZ = 0.0;
      boolean a = false;
      if (keys.forward()) {
         velX += forward.x / 20.0 * bps;
         velZ += forward.z / 20.0 * bps;
         a = true;
      }

      if (keys.backward()) {
         velX -= forward.x / 20.0 * bps;
         velZ -= forward.z / 20.0 * bps;
         a = true;
      }

      boolean b = false;
      if (keys.right()) {
         velX += right.x / 20.0 * bps;
         velZ += right.z / 20.0 * bps;
         b = true;
      }

      if (keys.left()) {
         velX -= right.x / 20.0 * bps;
         velZ -= right.z / 20.0 * bps;
         b = true;
      }

      if (a && b) {
         velX *= DIAGONAL;
         velZ *= DIAGONAL;
      }

      return new Vec3d(velX, 0.0, velZ);
   }
}
