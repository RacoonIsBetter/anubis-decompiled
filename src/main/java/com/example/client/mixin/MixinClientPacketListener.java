package com.example.client.mixin;

import net.anubis.client.event.impl.ChatSendEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPacketListener {
   @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
   private void anubis$onSendChat(String message, CallbackInfo ci) {
      if (ChatSendEvent.dispatch(message)) {
         ci.cancel();
      }
   }

   @Inject(method = "onEntityStatus", at = @At("TAIL"))
   private void anubis$afterEntityEvent(EntityStatusS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onGameJoin", at = @At("TAIL"))
   private void anubis$afterLogin(GameJoinS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onChunkData", at = @At("TAIL"))
   private void anubis$afterChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onBlockUpdate", at = @At("TAIL"))
   private void anubis$afterBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
   private void anubis$afterSectionUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onLightUpdate", at = @At("TAIL"))
   private void anubis$afterLightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }

   @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
   private void anubis$afterEntityMotion(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      PacketAppliedEvent.dispatch(packet);
   }
}
