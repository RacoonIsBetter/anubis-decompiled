package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.anubis.client.module.impl.render.ShaderEspModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class MixinShaderEspLevelRenderer {
   @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;", at = @At("RETURN"))
   private void anubis$glowEspKeepState(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
      if (ShaderEspModule.active != null) {
         ShaderEspModule.extracted(entity, (EntityRenderState)cir.getReturnValue());
      }
   }

   @Inject(method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", at = @At("TAIL"))
   private void anubis$glowEsp(
      CallbackInfo ci,
      @Local(argsOnly = true) Camera camera,
      @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
      @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      if (ShaderEspModule.active != null) {
         ShaderEspModule.afterLevel(camera, viewRotation, projection);
      }
   }
}
