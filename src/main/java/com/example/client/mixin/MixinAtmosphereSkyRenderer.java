package com.example.client.mixin;

import net.anubis.client.module.impl.render.AtmosphereModule;
import net.anubis.client.module.impl.render.SkyLyrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.MoonPhase;
import net.minecraft.world.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class MixinAtmosphereSkyRenderer {
   @Inject(method = "updateRenderState", at = @At("TAIL"))
   private void anubis$readSky(ClientWorld level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && state.skybox == Skybox.OVERWORLD) {
         atmosphere.extract(level, partialTick, state);
      }
   }

   @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
   private void anubis$dome(int color, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.skyDisc()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderTopSky", at = @At("TAIL"))
   private void anubis$afterDome(int color, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null) {
         atmosphere.afterSkyDisc();
      }
   }

   @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
   private void anubis$noSunriseFan(MatrixStack pose, float sunAngle, int color, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.replacesSunrise()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
   private void anubis$stars(float starBrightness, MatrixStack pose, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.stars(pose)) {
         ci.cancel();
      }
   }

   @Inject(
      method = "renderCelestialBodies",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/SkyRendering;renderMoon(Lnet/minecraft/world/MoonPhase;FLnet/minecraft/client/util/math/MatrixStack;)V"
      )
   )
   private void anubis$beforeMoon(
      MatrixStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
   ) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null) {
         atmosphere.beforeMoon(pose);
      }
   }

   @Inject(
      method = "renderCelestialBodies",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/SkyRendering;renderMoon(Lnet/minecraft/world/MoonPhase;FLnet/minecraft/client/util/math/MatrixStack;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$afterMoon(
      MatrixStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
   ) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null) {
         atmosphere.afterMoon(pose);
      }
   }

   @Inject(method = "renderCelestialBodies", at = @At("TAIL"))
   private void anubis$afterCelestials(
      MatrixStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
   ) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null) {
         atmosphere.afterCelestials();
      }

      SkyLyrics.render();
   }

   @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
   private void anubis$endSky(CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.endSky()) {
         ci.cancel();
      }
   }
}
