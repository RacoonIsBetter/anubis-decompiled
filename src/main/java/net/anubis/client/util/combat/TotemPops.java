package net.anubis.client.util.combat;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;

@Environment(EnvType.CLIENT)
public final class TotemPops {
   private TotemPops() {
   }

   public static void scan(Packet<?> packet, Consumer<LivingEntity> sink) {
      if (packet instanceof EntityStatusS2CPacket event) {
         check(event, sink);
      } else if (packet instanceof BundleS2CPacket bundle) {
         for (Packet<?> inner : bundle.getPackets()) {
            if (inner instanceof EntityStatusS2CPacket event) {
               check(event, sink);
            }
         }
      }
   }

   private static void check(EntityStatusS2CPacket event, Consumer<LivingEntity> sink) {
      if (event.getStatus() == 35) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            if (event.getEntity(level) instanceof LivingEntity living && !living.isRemoved()) {
               sink.accept(living);
            }
         }
      }
   }
}
