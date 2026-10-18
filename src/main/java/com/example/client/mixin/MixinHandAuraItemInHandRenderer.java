package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.render.HandAuraModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class MixinHandAuraItemInHandRenderer {
   @WrapOperation(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V"))
   private void anubis$handAura(RenderDispatcher features, Operation<Void> original) {
      if (HandAuraModule.active != null && HandAuraModule.beginCapture()) {
         boolean drawn = false;

         try {
            original.call(new Object[]{features});
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
            drawn = true;
         } finally {
            HandAuraModule.endCapture(drawn);
         }
      } else {
         original.call(new Object[]{features});
      }
   }
}
