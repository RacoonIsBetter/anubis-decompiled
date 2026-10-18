package com.example.client.mixin;

import net.anubis.client.module.impl.render.ColorGradingModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinColorGradingGameRenderer {
   @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;rotate()V"))
   private void anubis$colorGrading(RenderTickCounter deltaTracker, boolean renderLevel, CallbackInfo ci) {
      if (ColorGradingModule.active != null) {
         ColorGradingModule.afterWorld(renderLevel);
      }
   }
}
