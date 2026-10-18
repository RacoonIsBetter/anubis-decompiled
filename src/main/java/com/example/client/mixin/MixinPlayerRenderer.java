package com.example.client.mixin;

import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.SkinChangeModule;
import net.anubis.client.module.impl.render.NametagsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerRenderer {
   @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
   private void anubis$injectSkin(PlayerLikeEntity entity, PlayerEntityRenderState state, float partialTick, CallbackInfo ci) {
      SkinChangeModule mod = ModuleRegistry.get(SkinChangeModule.class);
      if (mod != null && mod.isEnabled()) {
         state.skinTextures = mod.skinFor(state.id, state.skinTextures);
      }
   }

   @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$suppressVanillaNameTag(PlayerEntityRenderState state, MatrixStack poseStack, OrderedRenderCommandQueue collector, CameraRenderState cameraState, CallbackInfo ci) {
      if (NametagsModule.hideVanillaNametags()) {
         ci.cancel();
      }
   }
}
