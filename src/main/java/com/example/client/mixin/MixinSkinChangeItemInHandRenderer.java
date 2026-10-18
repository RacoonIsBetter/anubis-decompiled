package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.SkinChangeModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class MixinSkinChangeItemInHandRenderer {
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   @Final
   private EntityRenderManager entityRenderDispatcher;

   @WrapOperation(
      method = {"renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V", "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Arm;)V"},
      at = {
            @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
            ),
            @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
            )
      }
   )
   private void anubis$skinChangeArm(
      PlayerEntityRenderer<?> renderer, MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, Identifier texture, boolean sleeve, Operation<Void> original
   ) {
      SkinChangeModule mod = ModuleRegistry.get(SkinChangeModule.class);
      ClientPlayerEntity player = this.client.player;
      if (mod != null && mod.isEnabled() && player != null) {
         SkinTextures own = player.getSkin();
         SkinTextures skin = mod.skinFor(player.getId(), own);
         if (skin != own) {
            texture = skin.body().texturePath();
            PlayerEntityRenderer<AbstractClientPlayerEntity> byModel = ((AccessorSkinChangeEntityRenderDispatcher)this.entityRenderDispatcher)
               .anubis$skinChangePlayerRenderers()
               .get(skin.model());
            if (byModel != null) {
               renderer = byModel;
            }
         }
      }

      original.call(new Object[]{renderer, poseStack, collector, light, texture, sleeve});
   }
}
