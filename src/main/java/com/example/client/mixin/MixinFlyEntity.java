package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinFlyEntity {
   @ModifyReturnValue(method = "isTouchingWater()Z", at = @At("RETURN"))
   private boolean anubis$flyIsInWater(boolean original) {
      return original && !this.anubis$flyAffects();
   }

   @ModifyReturnValue(method = "isInLava()Z", at = @At("RETURN"))
   private boolean anubis$flyIsInLava(boolean original) {
      return original && !this.anubis$flyAffects();
   }

   @ModifyExpressionValue(method = "updateSwimming()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSubmergedInWater()Z"))
   private boolean anubis$flyIsUnderWater(boolean original) {
      return original && !this.anubis$flyAffects();
   }

   @Unique
   private boolean anubis$flyAffects() {
      return FlyModule.active != null && (Object)this == MinecraftClient.getInstance().player;
   }
}
