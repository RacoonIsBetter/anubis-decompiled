package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.EntityControlModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(MobEntity.class)
public abstract class MixinEntityControlMob {
   @ModifyReturnValue(method = "hasSaddleEquipped()Z", at = @At("RETURN"))
   private boolean anubis$entityControlSpoofSaddle(boolean original) {
      return original || EntityControlModule.spoofSaddle();
   }
}
