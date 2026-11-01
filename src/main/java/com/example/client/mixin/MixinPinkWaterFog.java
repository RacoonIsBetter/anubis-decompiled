package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.render.PinkWater;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.fog.WaterFogModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(WaterFogModifier.class)
public abstract class MixinPinkWaterFog {
   @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
   private int anubis$pinkWaterFog(int color) {
      return PinkWater.waterFog(color);
   }
}
