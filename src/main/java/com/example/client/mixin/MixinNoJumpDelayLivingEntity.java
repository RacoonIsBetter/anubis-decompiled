package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.movement.NoJumpDelayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinNoJumpDelayLivingEntity {
   @Shadow
   private int jumpingCooldown;

   @WrapOperation(method = "tickMovement()V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;jumpingCooldown:I", opcode = 181))
   private void anubis$noJumpDelay(LivingEntity self, int ticks, Operation<Void> original) {
      NoJumpDelayModule module = NoJumpDelayModule.active;
      if (module != null && ticks > this.jumpingCooldown && self == MinecraftClient.getInstance().player) {
         ticks = module.rearm(ticks);
      }

      original.call(new Object[]{self, ticks});
   }
}
