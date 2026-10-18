package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinJesusLivingEntity {
   @ModifyReturnValue(method = "canWalkOnFluid(Lnet/minecraft/fluid/FluidState;)Z", at = @At("RETURN"))
   private boolean anubis$jesusCanStandOnFluid(boolean original, FluidState fluidState) {
      if (original) {
         return true;
      }

      JesusModule jesus = JesusModule.active;
      return jesus != null && (Object)this == MinecraftClient.getInstance().player ? jesus.canWalkOnFluid(fluidState) : false;
   }
}
