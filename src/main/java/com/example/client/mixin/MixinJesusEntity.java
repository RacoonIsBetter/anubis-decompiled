package com.example.client.mixin;

import net.anubis.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinJesusEntity {
   @Inject(method = "onBubbleColumnSurfaceCollision(ZLnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"))
   private void anubis$jesusAboveBubbleColumn(CallbackInfo ci) {
      this.anubis$jesusMarkBubbleColumn();
   }

   @Inject(method = "onBubbleColumnCollision(Z)V", at = @At("HEAD"))
   private void anubis$jesusInsideBubbleColumn(CallbackInfo ci) {
      this.anubis$jesusMarkBubbleColumn();
   }

   @Unique
   private void anubis$jesusMarkBubbleColumn() {
      JesusModule jesus = JesusModule.active;
      if (jesus != null && (Object)this == MinecraftClient.getInstance().player) {
         jesus.markInBubbleColumn();
      }
   }
}
