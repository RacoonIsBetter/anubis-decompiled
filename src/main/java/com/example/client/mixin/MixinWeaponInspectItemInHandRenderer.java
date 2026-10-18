package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.anubis.client.module.impl.render.HandModelModule;
import net.anubis.client.module.impl.render.WeaponInspectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class MixinWeaponInspectItemInHandRenderer {
   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
   private void anubis$inspectHands(float partialTick, MatrixStack poseStack, OrderedRenderCommandQueue collector, ClientPlayerEntity player, int light, CallbackInfo ci) {
      if (WeaponInspectModule.active != null) {
         WeaponInspectModule.handsBegin(poseStack);
      }
   }

   @WrapOperation(
      method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"
      )
   )
   private void anubis$inspectItem(
      HeldItemRenderer renderer,
      LivingEntity entity,
      ItemStack stack,
      ItemDisplayContext context,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      int light,
      Operation<Void> original,
      @Local(argsOnly = true) Hand hand
   ) {
      boolean mainHand = hand == Hand.MAIN_HAND;
      if (WeaponInspectModule.active != null) {
         if (!WeaponInspectModule.renderItem(poseStack, entity, stack, context, collector, light, mainHand)) {
            original.call(new Object[]{renderer, entity, stack, context, poseStack, collector, light});
         }
      } else {
         if (HandModelModule.active != null) {
            HandModelModule.renderArm(poseStack, entity, stack, context, collector, light, mainHand);
         }

         original.call(new Object[]{renderer, entity, stack, context, poseStack, collector, light});
      }
   }
}
