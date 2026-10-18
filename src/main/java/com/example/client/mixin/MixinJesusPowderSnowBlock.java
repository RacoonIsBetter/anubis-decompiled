package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(PowderSnowBlock.class)
public abstract class MixinJesusPowderSnowBlock {
   @ModifyReturnValue(method = "canWalkOnPowderSnow(Lnet/minecraft/entity/Entity;)Z", at = @At("RETURN"))
   private static boolean anubis$jesusWalkOnPowderSnow(boolean original, Entity entity) {
      if (original) {
         return true;
      }

      JesusModule jesus = JesusModule.active;
      return jesus != null && entity == MinecraftClient.getInstance().player && jesus.canWalkOnPowderSnow();
   }
}
