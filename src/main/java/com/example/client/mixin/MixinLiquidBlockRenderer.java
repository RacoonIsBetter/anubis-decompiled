package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.render.PinkWater;
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
      method = "vertex",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"),
      index = 3
   )
   private float anubis$xrayFluidAlpha(float alpha) {
      return XrayModule.active == null ? alpha : alpha * XrayModule.CURRENT_ALPHA.get()[0];
   }

   @ModifyReturnValue(method = "getLight", at = @At("RETURN"))
   private int anubis$pinkWaterGlow(int light) {
      return PinkWater.light(light);
   }
}
