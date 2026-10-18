package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBoatEntity.class)
public abstract class MixinEntityControlAbstractBoat {
   @ModifyExpressionValue(method = "updatePaddles()V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;pressingLeft:Z", opcode = 180))
   private boolean anubis$entityControlInputLeft(boolean original) {
      return original && !EntityControlVehicleHook.locksBoatTurning((AbstractBoatEntity)(Object)this);
   }

   @ModifyExpressionValue(method = "updatePaddles()V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;pressingRight:Z", opcode = 180))
   private boolean anubis$entityControlInputRight(boolean original) {
      return original && !EntityControlVehicleHook.locksBoatTurning((AbstractBoatEntity)(Object)this);
   }
}
