package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.movement.TridentBoostModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TridentItem.class)
public abstract class MixinTridentBoostTridentItem {
   @WrapOperation(method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"), require = 0)
   private void anubis$tridentBoostPush(PlayerEntity player, double x, double y, double z, Operation<Void> original) {
      double multiplier = TridentBoostModule.riptideMultiplier();
      original.call(new Object[]{player, x * multiplier, y * multiplier, z * multiplier});
   }

   @ModifyExpressionValue(method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"), require = 0)
   private boolean anubis$tridentBoostUseOutOfWater(boolean original) {
      return original || TridentBoostModule.allowOutOfWater();
   }

   @ModifyExpressionValue(method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"), require = 0)
   private boolean anubis$tridentBoostReleaseOutOfWater(boolean original) {
      return original || TridentBoostModule.allowOutOfWater();
   }
}
