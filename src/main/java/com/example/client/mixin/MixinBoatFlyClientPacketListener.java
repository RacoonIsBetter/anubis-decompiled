package com.example.client.mixin;

import net.anubis.client.module.impl.movement.BoatFlyModule;
import net.anubis.client.module.impl.movement.BoatNoclipModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinBoatFlyClientPacketListener {
   @Inject(
      method = "onVehicleMove(Lnet/minecraft/network/packet/s2c/play/VehicleMoveS2CPacket;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/c2s/play/VehicleMoveC2SPacket;fromVehicle(Lnet/minecraft/entity/Entity;)Lnet/minecraft/network/packet/c2s/play/VehicleMoveC2SPacket;")
   )
   private void anubis$boatFlyVehicleSetback(VehicleMoveS2CPacket packet, CallbackInfo ci) {
      BoatFlyModule fly = BoatFlyModule.active;
      if (fly != null) {
         fly.onVehicleSetback();
      }

      BoatNoclipModule noclip = BoatNoclipModule.active;
      if (noclip != null) {
         noclip.onVehicleSetback();
      }
   }
}
