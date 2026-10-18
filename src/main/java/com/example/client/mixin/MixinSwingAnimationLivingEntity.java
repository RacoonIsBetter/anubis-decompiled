package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.render.SwingAnimationModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinSwingAnimationLivingEntity {
   @ModifyExpressionValue(method = {"tickHandSwing()V", "swingHand(Lnet/minecraft/util/Hand;Z)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHandSwingDuration()I"))
   private int anubis$swingTempo(int ticks) {
      SwingAnimationModule module = SwingAnimationModule.active;
      return module != null && (Object)this == MinecraftClient.getInstance().player ? module.swingTicks(ticks) : ticks;
   }

   @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;handSwinging:Z", opcode = 181))
   private void anubis$latchSwing(Hand hand, boolean broadcast, CallbackInfo ci) {
      SwingAnimationModule module = SwingAnimationModule.active;
      if (module != null && (Object)this == MinecraftClient.getInstance().player) {
         module.latch();
      }
   }
}
