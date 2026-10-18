package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.render.CustomFovModule;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public abstract class MixinScreenEffectRenderer {
   @ModifyExpressionValue(method = "renderOverlays(ZFLnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V", at = @At(value = "NEW", target = "()Lnet/minecraft/client/util/math/MatrixStack;"))
   private MatrixStack anubis$undoCustomFov(MatrixStack poseStack) {
      float scale = CustomFovModule.screenEffectScale();
      if (scale != 1.0F) {
         poseStack.peek().getPositionMatrix().scale(scale, scale, 1.0F);
      }

      return poseStack;
   }

   @Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V", at = @At("HEAD"), cancellable = true)
   private static void anubis$cancelFireOverlay(MatrixStack poseStack, VertexConsumerProvider bufferSource, Sprite sprite, CallbackInfo ci) {
      if (NoRenderModule.hideFireOverlay()) {
         ci.cancel();
      }
   }
}
