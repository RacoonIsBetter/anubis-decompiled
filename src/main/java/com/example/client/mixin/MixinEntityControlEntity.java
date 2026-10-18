package com.example.client.mixin;

import net.anubis.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinEntityControlEntity {
   @ModifyVariable(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), argsOnly = true)
   private Vec3d anubis$entityControlVehicleMove(Vec3d movement, MovementType type) {
      return !EntityControlVehicleHook.anyActive ? movement : EntityControlVehicleHook.onEntityMove((Entity)(Object)this, type, movement);
   }
}
