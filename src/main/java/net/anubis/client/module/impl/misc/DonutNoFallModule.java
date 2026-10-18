package net.anubis.client.module.impl.misc;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;

@Environment(EnvType.CLIENT)
public final class DonutNoFallModule extends ClientModule {
   private static final double TRIGGER_FALL_DISTANCE = 3.0;
   private static final int GROUND_PACKETS_TO_RESET = 4;
   private boolean noFall;
   private double lastFallDistance;
   private int groundTicks;
   private boolean sendingReplacement;

   public DonutNoFallModule() {
      super("donut-nofall", "Donut NoFall", "Take no fall damage on DonutSMP.", ModuleCategory.DONUT);
   }

   @Override
   protected void onEnable() {
      this.noFall = false;
      this.lastFallDistance = 0.0;
      this.groundTicks = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (this.noFall) {
            player.setOnGround(false);
         }

         if (this.lastFallDistance > 3.0) {
            this.noFall = true;
         }

         this.lastFallDistance = player.fallDistance;
      }
   }

   @EventHandler
   public void onPacketSend(PacketSendEvent event) {
      if (!this.sendingReplacement && event.packet instanceof PlayerMoveC2SPacket move) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         if (player != null && connection != null) {
            event.setCancelled(true);
            if (this.noFall && move.isOnGround()) {
               this.groundTicks++;
            }

            if (this.groundTicks % 2 == 1) {
               player.setPos(player.getX(), player.getY() + 1.0E-8, player.getZ());
            }

            boolean onGround = !this.noFall && player.isOnGround();
            this.sendingReplacement = true;

            try {
               connection.sendPacket(
                  new Full(
                     player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), onGround, false
                  )
               );
            } finally {
               this.sendingReplacement = false;
            }

            if (this.groundTicks == 4) {
               this.noFall = false;
               this.groundTicks = 0;
            }
         }
      }
   }
}
