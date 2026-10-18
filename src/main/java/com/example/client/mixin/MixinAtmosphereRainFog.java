package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AtmosphericFogModifier.class)
public abstract class MixinAtmosphereRainFog {
   @Shadow
   private float fogMultiplier;

   @Inject(method = "method_76304", at = @At("TAIL"))
   private void anubis$keepClearInRain(Camera camera, ClientWorld level, RenderTickCounter deltaTracker, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.keepsSkyInRain()) {
         this.fogMultiplier = 0.0F;
      }
   }
}
