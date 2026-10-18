package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinElytraFlyLivingEntity {
   @ModifyReturnValue(method = "isGliding()Z", at = @At("RETURN"))
   private boolean anubis$elytraFlyIsFallFlying(boolean original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      return (Object)this == player && mc.isOnThread() ? module.modifyFallFlying(player, original) : original;
   }
}
