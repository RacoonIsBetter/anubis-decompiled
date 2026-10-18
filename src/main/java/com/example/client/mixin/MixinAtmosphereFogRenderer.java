package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class MixinAtmosphereFogRenderer {
   @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;", at = @At("RETURN"))
   private void anubis$fogColour(
      Camera camera, float partialTick, ClientWorld level, int renderDistance, float darkness, CallbackInfoReturnable<Vector4f> cir
   ) {
      if (camera.getSubmersionType() == CameraSubmersionType.NONE && !(darkness > 0.0F)) {
         if (!(camera.getFocusedEntity() instanceof LivingEntity living && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS)))
            )
          {
            AtmosphereModule atmosphere = AtmosphereModule.get();
            if (atmosphere != null) {
               atmosphere.fog(camera, level, (Vector4f)cir.getReturnValue());
            }
         }
      }
   }
}
