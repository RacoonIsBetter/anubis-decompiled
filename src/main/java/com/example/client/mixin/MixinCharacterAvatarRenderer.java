package com.example.client.mixin;

import net.anubis.client.module.impl.render.character.CharacterLayer;
import net.anubis.client.module.impl.render.character.CharacterModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class MixinCharacterAvatarRenderer<T extends PlayerLikeEntity & ClientPlayerLikeEntity> extends LivingEntityRenderer<T, PlayerEntityRenderState, PlayerEntityModel> {
   protected MixinCharacterAvatarRenderer(Context context, PlayerEntityModel model, float shadowRadius) {
      super(context, model, shadowRadius);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void anubis$addCharacterLayer(Context context, boolean slim, CallbackInfo ci) {
      this.addFeature(new CharacterLayer(this));
   }

   @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
   private void anubis$extractCharacter(PlayerLikeEntity entity, PlayerEntityRenderState state, float partialTick, CallbackInfo ci) {
      CharacterModule.extract(entity, state);
   }

   @Inject(method = "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V", at = @At("HEAD"), cancellable = true)
   private void anubis$characterFirstPersonHand(
      MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci
   ) {
      if (CharacterLayer.firstPersonHand((PlayerEntityModel)this.getModel(), poseStack, collector, light, arm)) {
         ci.cancel();
      }
   }
}
