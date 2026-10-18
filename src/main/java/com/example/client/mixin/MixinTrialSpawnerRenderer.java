package com.example.client.mixin;

import net.anubis.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.entity.state.MobSpawnerBlockEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.block.entity.TrialSpawnerBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(TrialSpawnerBlockEntityRenderer.class)
public abstract class MixinTrialSpawnerRenderer {
   @Inject(method = "updateSpawnerRenderState(Lnet/minecraft/client/render/block/entity/state/MobSpawnerBlockEntityRenderState;FLnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/EntityRenderManager;DD)V", at = @At("HEAD"), cancellable = true)
   private static void anubis$cancelSpawnerMobExtract(
      MobSpawnerBlockEntityRenderState state, float partialTick, Entity displayEntity, EntityRenderManager dispatcher, double x, double y, CallbackInfo ci
   ) {
      if (NoRenderModule.hideSpawnerMob()) {
         ci.cancel();
      }
   }
}
