package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(CloudRenderer.class)
public abstract class MixinAtmosphereCloudRenderer {
   @ModifyVariable(method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;JF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private int anubis$cloudColour(int color) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      return atmosphere != null ? atmosphere.clouds(color) : color;
   }
}
