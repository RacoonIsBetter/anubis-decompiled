package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.render.HandModelModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class MixinHandModelItemInHandRenderer {
   @WrapOperation(
      method = {"renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V", "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Arm;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
      )
   )
   private void anubis$longRightArm(
      PlayerEntityRenderer<?> renderer, MatrixStack pose, OrderedRenderCommandQueue collector, int light, Identifier skin, boolean sleeve, Operation<Void> original
   ) {
      original.call(new Object[]{renderer, pose, collector, light, skin, sleeve});
      if (HandModelModule.active != null) {
         pose.push();
         HandModelModule.shoulder(pose, true, renderer);
         original.call(new Object[]{renderer, pose, collector, light, skin, sleeve});
         pose.pop();
      }
   }

   @WrapOperation(
      method = {"renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V", "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Arm;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
      )
   )
   private void anubis$longLeftArm(
      PlayerEntityRenderer<?> renderer, MatrixStack pose, OrderedRenderCommandQueue collector, int light, Identifier skin, boolean sleeve, Operation<Void> original
   ) {
      original.call(new Object[]{renderer, pose, collector, light, skin, sleeve});
      if (HandModelModule.active != null) {
         pose.push();
         HandModelModule.shoulder(pose, false, renderer);
         original.call(new Object[]{renderer, pose, collector, light, skin, sleeve});
         pose.pop();
      }
   }
}
