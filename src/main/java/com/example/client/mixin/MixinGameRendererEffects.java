package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererEffects {
   @ModifyExpressionValue(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
   private boolean anubis$freecamHands(boolean firstPerson) {
      FreecamModule freecam = FreecamModule.active;
      return freecam != null && freecam.keepHands() || firstPerson;
   }

   @Inject(method = "showFloatingItem(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$cancelTotemAnimation(ItemStack stack, CallbackInfo ci) {
      if (NoRenderModule.hideTotemAnimation()) {
         ci.cancel();
      }
   }

   @WrapOperation(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
   private float anubis$cancelNauseaWobble(float portal, float nausea, Operation<Float> original) {
      return NoRenderModule.hideNauseaPortal() ? 0.0F : (Float)original.call(new Object[]{portal, nausea});
   }
}
