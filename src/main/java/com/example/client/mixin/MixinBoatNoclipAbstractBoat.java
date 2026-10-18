package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.movement.BoatNoclipModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBoatEntity.class)
public abstract class MixinBoatNoclipAbstractBoat {
   @ModifyReturnValue(method = "collidesWith(Lnet/minecraft/entity/Entity;)Z", at = @At("RETURN"))
   private boolean anubis$boatNoclipCollide(boolean original) {
      if (original && BoatNoclipModule.active != null) {
         AbstractBoatEntity boat = (AbstractBoatEntity)(Object)this;
         return !boat.getEntityWorld().isClient() || boat.getControllingPassenger() != MinecraftClient.getInstance().player;
      } else {
         return original;
      }
   }
}
