package com.example.client.mixin;

import net.anubis.client.module.impl.misc.SpamModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinSpamClientPacketListener {
   @Shadow
   private ClientWorld world;

   @Inject(
      method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$spamRelogin(GameJoinS2CPacket packet, CallbackInfo ci) {
      if (SpamModule.active != null && this.world != null) {
         SpamModule.onGameLeft();
      }
   }

   @Inject(
      method = "onEnterReconfiguration(Lnet/minecraft/network/packet/s2c/play/EnterReconfigurationS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$spamReconfiguration(EnterReconfigurationS2CPacket packet, CallbackInfo ci) {
      if (SpamModule.active != null) {
         SpamModule.onGameLeft();
      }
   }
}
