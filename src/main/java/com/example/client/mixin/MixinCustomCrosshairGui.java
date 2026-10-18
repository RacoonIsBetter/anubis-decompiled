package com.example.client.mixin;

import net.anubis.client.module.impl.render.CustomCrosshairModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class MixinCustomCrosshairGui {
   @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$hideVanillaCrosshair(DrawContext graphics, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (CustomCrosshairModule.active != null) {
         ci.cancel();
      }
   }
}
