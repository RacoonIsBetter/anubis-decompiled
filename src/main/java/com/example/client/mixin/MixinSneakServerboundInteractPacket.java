package com.example.client.mixin;

import net.anubis.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class MixinSneakServerboundInteractPacket {
   @ModifyVariable(method = "<init>(IZLnet/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket$InteractTypeHandler;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static boolean anubis$packetSneak(boolean usingSecondaryAction) {
      if (usingSecondaryAction) {
         return true;
      }

      SneakModule sneak = SneakModule.active;
      return sneak != null && sneak.doPacket();
   }
}
