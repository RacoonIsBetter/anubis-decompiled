package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.movement.BoatNoclipModule;
import net.anubis.client.module.impl.render.CustomFovModule;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public abstract class MixinScreenEffectRenderer {
   @ModifyExpressionValue(method = "renderOverlays", at = @At(value = "NEW", target = "()Lnet/minecraft/client/util/math/MatrixStack;"))
   private MatrixStack anubis$undoCustomFov(MatrixStack poseStack) {
      float scale = CustomFovModule.screenEffectScale();
      if (scale != 1.0F) {
         poseStack.peek().getPositionMatrix().scale(scale, scale, 1.0F);
      }

      return poseStack;
   }

   @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
   private static void anubis$cancelFireOverlay(MatrixStack poseStack, VertexConsumerProvider bufferSource, Sprite sprite, CallbackInfo ci) {
      if (NoRenderModule.hideFireOverlay()) {
         ci.cancel();
      }
   }

   @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true)
   private static void anubis$noWallOverlayInNoclipBoat(PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
      if (BoatNoclipModule.hidesWallOverlay(player)) {
         cir.setReturnValue(null);
      }
   }
}
