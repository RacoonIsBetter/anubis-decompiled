package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class MixinFreecamLevelRenderer {
   @Inject(method = "isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
   private void anubis$freecamEntitiesReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (FreecamModule.active != null) {
         cir.setReturnValue(true);
      }
   }

   @WrapOperation(
      method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V")
   )
   private void anubis$freelookCullInsideBlocks(WorldRenderer renderer, Camera camera, Frustum frustum, boolean spectator, Operation<Void> original) {
      original.call(new Object[]{renderer, camera, frustum, spectator || camera.isThirdPerson() && FreelookModule.throughBlocks()});
   }
}
