package net.anubis.client.module.impl.movement.speed;

import net.anubis.client.module.impl.movement.SpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public abstract class SpeedMode {
   private static final double BASE_SPEED = 0.2873;
   protected final SpeedModule settings;
   protected int stage;
   protected double distance;
   protected double speed;

   protected SpeedMode(SpeedModule settings) {
      this.settings = settings;
      this.reset();
   }

   public void onTick(ClientPlayerEntity player) {
   }

   public Vec3d onMove(ClientPlayerEntity player, Vec3d movement) {
      return movement;
   }

   public void onRubberband() {
      this.reset();
   }

   public void onActivate() {
   }

   public void onDeactivate() {
   }

   protected double getDefaultSpeed(ClientPlayerEntity player) {
      double defaultSpeed = 0.2873;
      StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
      if (speedEffect != null) {
         defaultSpeed *= 1.0 + 0.2 * (speedEffect.getAmplifier() + 1);
      }

      StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
      if (slowness != null) {
         defaultSpeed /= 1.0 + 0.2 * (slowness.getAmplifier() + 1);
      }

      return defaultSpeed;
   }

   protected void reset() {
      this.stage = 0;
      this.distance = 0.0;
      this.speed = 0.2873;
   }

   protected double getHop(ClientPlayerEntity player, double height) {
      StatusEffectInstance jumpBoost = player.getStatusEffect(StatusEffects.JUMP_BOOST);
      if (jumpBoost != null) {
         height += (jumpBoost.getAmplifier() + 1) * 0.1F;
      }

      return height;
   }
}
