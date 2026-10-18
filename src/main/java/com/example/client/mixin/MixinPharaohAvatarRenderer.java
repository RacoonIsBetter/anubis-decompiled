package com.example.client.mixin;

import net.anubis.client.module.impl.render.PharaohHeaddressLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPharaohAvatarRenderer<T extends PlayerLikeEntity & ClientPlayerLikeEntity> extends LivingEntityRenderer<T, PlayerEntityRenderState, PlayerEntityModel> {
   protected MixinPharaohAvatarRenderer(Context context, PlayerEntityModel model, float shadowRadius) {
      super(context, model, shadowRadius);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void anubis$addHeadCosmetics(Context context, boolean slim, CallbackInfo ci) {
      this.addFeature(new PharaohHeaddressLayer(this));
   }
}
