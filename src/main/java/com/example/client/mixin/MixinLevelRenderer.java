package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.anubis.client.util.ShaderOverlay;
import net.anubis.client.util.TracerEngine;
import net.anubis.client.util.WorldQuads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class MixinLevelRenderer {
   @ModifyArg(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/render/Frustum;DDDF)V"),
      index = 0
   )
   private Frustum anubis$onRenderLevel(
      Frustum frustum, @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation, @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      float partialTick = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
      TracerEngine.beginFrame(viewRotation, projection);
      WorldQuads.beginFrame(MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos());

      try (CollectorScope capture = ShaderOverlay.capture()) {
         Render3DEvent.dispatch(partialTick, frustum);
      }

      return frustum;
   }

   @ModifyArg(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;renderLateDebug(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/state/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"
      ),
      index = 2
   )
   private GpuBufferSlice anubis$noFluidFogOnTop(GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
      return anubis$onTopFog(fog, camera);
   }

   @Unique
   private static GpuBufferSlice anubis$onTopFog(GpuBufferSlice fog, Camera camera) {
      return camera.getSubmersionType() == CameraSubmersionType.NONE
         ? fog
         : ((AccessorGameRenderer)MinecraftClient.getInstance().gameRenderer).anubis$getFogRenderer().getFogBuffer(FogType.NONE);
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void anubis$overlayAfterShaderPack(
      CallbackInfo ci,
      @Local(argsOnly = true) GpuBufferSlice fog,
      @Local(argsOnly = true) Camera camera,
      @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
      @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      WorldQuads.render(viewRotation, projection);
      if (ShaderOverlay.pending()) {
         ShaderOverlay.render(camera, viewRotation, projection, fog, anubis$onTopFog(fog, camera));
      }
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void anubis$worldFogForHand(CallbackInfo ci, @Local(argsOnly = true) GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
      if (camera.getSubmersionType() != CameraSubmersionType.NONE) {
         RenderSystem.setShaderFog(fog);
      }
   }

   @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
   private void anubis$keepSkyDuringBlindnessDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
      if (NoRenderModule.hideBlindnessDarkness()) {
         cir.setReturnValue(false);
      }
   }
}
