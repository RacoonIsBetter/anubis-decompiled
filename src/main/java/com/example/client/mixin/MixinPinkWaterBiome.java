package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.render.PinkWater;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Biome.class)
public abstract class MixinPinkWaterBiome {
   @Unique
   private volatile byte anubis$cave;

   @ModifyReturnValue(method = "getWaterColor", at = @At("RETURN"))
   private int anubis$pinkWater(int color) {
      if (!PinkWater.shows()) {
         return color;
      }

      byte cave = this.anubis$cave;
      if (cave == 0) {
         cave = PinkWater.caveBiome((Biome)(Object)this);
         if (cave != 0) {
            this.anubis$cave = cave;
         }
      }

      return PinkWater.water(color, cave == 2);
   }
}
