package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.event.impl.PlayerAttackEntityEvent;
import net.anubis.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntity.class)
public abstract class MixinPlayer {
   @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
   private void anubis$onAttack(Entity target, CallbackInfo ci) {
      if ((Object)this == MinecraftClient.getInstance().player) {
         PlayerAttackEntityEvent.dispatch(target);
      }
   }

   @ModifyReturnValue(method = "getOffGroundSpeed()F", at = @At("RETURN"))
   private float anubis$flySpeed(float original) {
      FlyModule fly = FlyModule.active;
      if (fly == null) {
         return original;
      } else {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && (Object)this == player) {
            float override = fly.getOffGroundSpeed(player);
            return override != -1.0F ? override : original;
         } else {
            return original;
         }
      }
   }
}
