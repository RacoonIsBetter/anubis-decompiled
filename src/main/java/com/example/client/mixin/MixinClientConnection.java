package com.example.client.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.function.BooleanSupplier;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.NetworkSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public abstract class MixinClientConnection {
   @Unique
   private final BooleanSupplier anubis$connectionActive = this::isOpen;

   @Shadow
   public abstract boolean isOpen();

   @Shadow
   public abstract NetworkSide getSide();

   @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$onPacketReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
      if (this.getSide() == NetworkSide.CLIENTBOUND) {
         if (PacketReceiveNettyEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            PacketReceiveEvent.dispatchAsync(packet, this.anubis$connectionActive);
         }
      }
   }

   @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
   private void anubis$onPacketSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      if (this.getSide() == NetworkSide.CLIENTBOUND) {
         if (PacketSendEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            ServerRotation.noteSent(packet);
         }
      }
   }
}
