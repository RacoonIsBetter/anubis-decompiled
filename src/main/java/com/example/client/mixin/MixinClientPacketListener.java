package com.example.client.mixin;

import net.anubis.client.event.impl.ChatSendEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPacketListener {
   @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$onSendChat(String message, CallbackInfo ci) {
      if (ChatSendEvent.dispatch(message)) {
         ci.cancel();
      }
   }

   @Inject(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterLogin(GameJoinS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onChunkData(Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onBlockUpdate(Lnet/minecraft/network/packet/s2c/play/BlockUpdateS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onChunkDeltaUpdate(Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterSectionUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onLightUpdate(Lnet/minecraft/network/packet/s2c/play/LightUpdateS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterLightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onEntityVelocityUpdate(Lnet/minecraft/network/packet/s2c/play/EntityVelocityUpdateS2CPacket;)V", at = @At("TAIL"))
   private void anubis$afterEntityMotion(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }
}
