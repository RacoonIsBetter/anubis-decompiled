package com.example.client.mixin;

import net.anubis.client.module.impl.misc.AirStuckModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class MixinAirStuckLocalPlayer {
   @Inject(method = "tickMovement()V", at = @At("HEAD"), cancellable = true)
   private void anubis$airStuck(CallbackInfo ci) {
      if (AirStuckModule.active != null) {
         ci.cancel();
      }
   }
}
