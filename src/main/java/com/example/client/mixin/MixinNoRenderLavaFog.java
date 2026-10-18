package com.example.client.mixin;

import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LavaFogModifier.class)
public abstract class MixinNoRenderLavaFog {
   @Inject(method = "applyStartEndModifier(Lnet/minecraft/client/render/fog/FogData;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/world/ClientWorld;FLnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void anubis$clearLavaFog(FogData fog, Camera camera, ClientWorld level, float renderDistance, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (NoRenderModule.hideLavaFog()) {
         fog.environmentalStart = -8.0F;
         fog.environmentalEnd = renderDistance;
         fog.skyEnd = renderDistance;
         fog.cloudEnd = renderDistance;
      }
   }
}
