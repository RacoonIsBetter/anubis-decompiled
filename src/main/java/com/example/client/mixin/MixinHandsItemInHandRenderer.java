package com.example.client.mixin;

import net.anubis.client.module.impl.render.HandsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class MixinHandsItemInHandRenderer {
   @Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = Shift.AFTER))
   private void anubis$handsPose(
      AbstractClientPlayerEntity player,
      float partialTick,
      float pitch,
      Hand hand,
      float swing,
      ItemStack stack,
      float equip,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      int light,
      CallbackInfo ci
   ) {
      if (HandsModule.live != null) {
         boolean mainHand = hand == Hand.MAIN_HAND;
         Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
         HandsModule.shape(poseStack, mainHand, arm == Arm.RIGHT ? 1 : -1);
      }
   }
}
