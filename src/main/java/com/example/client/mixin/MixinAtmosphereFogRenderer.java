package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.anubis.client.module.impl.render.PeepWorld;
import net.anubis.client.module.impl.render.PinkWater;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class MixinAtmosphereFogRenderer {
   @Inject(method = "getFogColor", at = @At("RETURN"))
   private void anubis$fogColour(Camera camera, float partialTick, ClientWorld level, int renderDistance, float darkness, CallbackInfoReturnable<Vector4f> cir) {
      CameraSubmersionType fluid = camera.getSubmersionType();
      if (fluid == CameraSubmersionType.WATER) {
         PinkWater.underwater((Vector4f)cir.getReturnValue());
      }

      if (fluid == CameraSubmersionType.NONE && !(darkness > 0.0F)) {
         if (!(
            camera.getFocusedEntity() instanceof LivingEntity living
               && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))
         )) {
            AtmosphereModule atmosphere = AtmosphereModule.get();
            if (atmosphere != null) {
               atmosphere.fog(camera, level, (Vector4f)cir.getReturnValue());
            }

            PeepWorld.fog((Vector4f)cir.getReturnValue());
         }
      }
   }
}
