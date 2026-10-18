package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinElytraFlyEntity {
   @ModifyReturnValue(method = "getPose()Lnet/minecraft/entity/EntityPose;", at = @At("RETURN"))
   private EntityPose anubis$elytraFlyGetPose(EntityPose original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if ((Object)this == player && mc.isOnThread()) {
            return module.canPacketEfly(player) ? EntityPose.GLIDING : original;
         } else {
            return original;
         }
      }
   }
}
