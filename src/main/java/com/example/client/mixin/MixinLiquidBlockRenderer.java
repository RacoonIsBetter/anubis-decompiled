package com.example.client.mixin;

import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(FluidRenderer.class)
public abstract class MixinLiquidBlockRenderer {
   @ModifyArg(
      method = "vertex(Lnet/minecraft/client/render/VertexConsumer;FFFFFFFFI)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"),
      index = 3
   )
   private float anubis$xrayFluidAlpha(float alpha) {
      return XrayModule.active == null ? alpha : alpha * XrayModule.CURRENT_ALPHA.get()[0];
   }
}
