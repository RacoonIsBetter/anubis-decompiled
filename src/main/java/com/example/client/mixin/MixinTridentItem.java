package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.misc.TridentUtilModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TridentItem.class)
public abstract class MixinTridentItem {
   @ModifyExpressionValue(
      method = {"use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)Z"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"),
      require = 0
   )
   private boolean anubis$allowOutOfWater(boolean original) {
      return original || TridentUtilModule.allowOutOfWater();
   }

   @ModifyExpressionValue(method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)Z", at = @At(value = "CONSTANT", args = "intValue=10"), require = 0)
   private int anubis$scaleMinCharge(int original) {
      return TridentUtilModule.minChargeTicks(original);
   }
}
