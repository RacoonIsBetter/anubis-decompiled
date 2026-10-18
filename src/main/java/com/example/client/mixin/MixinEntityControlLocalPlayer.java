package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.BoatFlyModule;
import net.anubis.client.module.impl.movement.EntityControlModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.JumpingMount;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class MixinEntityControlLocalPlayer {
   @ModifyReturnValue(method = "getMountJumpStrength()F", at = @At("RETURN"))
   private float anubis$entityControlMaxJump(float original) {
      return EntityControlModule.maxJump() ? 1.0F : original;
   }

   @ModifyReturnValue(method = "getJumpingMount()Lnet/minecraft/entity/JumpingMount;", at = @At("RETURN"))
   private JumpingMount anubis$boatFlyCancelJump(JumpingMount original) {
      return original != null && BoatFlyModule.cancelsMountJump((ClientPlayerEntity)(Object)this) ? null : original;
   }
}
