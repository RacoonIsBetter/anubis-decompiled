package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.render.FullbrightModule;
import net.anubis.client.module.impl.render.NoRenderModule;
import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LightmapTextureManager.class)
public abstract class MixinLightTexture {
   @Shadow
   @Final
   private GpuTexture glTexture;

   @Inject(
      method = "update(F)V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/LightmapTextureManager;dirty:Z", opcode = 181, shift = Shift.AFTER),
      cancellable = true
   )
   private void anubis$xrayFullbright(float partialTick, CallbackInfo ci) {
      if (XrayModule.active != null) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.glTexture, -1);
         ci.cancel();
      }
   }

   @ModifyExpressionValue(method = "update(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEffectFadeFactor(Lnet/minecraft/registry/entry/RegistryEntry;F)F"))
   private float anubis$hideDarknessPulse(float blendFactor) {
      return NoRenderModule.hideBlindnessDarkness() ? 0.0F : blendFactor;
   }

   @ModifyArg(
      method = "update(F)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 6),
      index = 0
   )
   private float anubis$fullbrightGamma(float gamma) {
      FullbrightModule fullbright = ModuleRegistry.get(FullbrightModule.class);
      return fullbright != null && fullbright.isEnabled() ? fullbright.gamma() : gamma;
   }
}
