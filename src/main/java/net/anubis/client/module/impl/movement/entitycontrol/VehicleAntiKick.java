package net.anubis.client.module.impl.movement.entitycontrol;

import com.example.client.mixin.AccessorBoatFlyServerboundMoveVehiclePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class VehicleAntiKick {
   private static final double Y_OFFSET = 0.0313;
   private int delayLeft;
   private double lastPacketY = Double.MAX_VALUE;
   private boolean sentPacket;
   private Entity trackedVehicle;

   public void start(int delay) {
      this.delayLeft = delay;
      this.forget();
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      this.trackedVehicle = player != null ? player.getVehicle() : null;
   }

   public void stop() {
      this.trackedVehicle = null;
   }

   public void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      Entity vehicle = player != null ? player.getVehicle() : null;
      this.trackVehicle(vehicle);
      if (this.sentPacket) {
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         if (vehicle != null && connection != null && this.lastPacketY != Double.MAX_VALUE) {
            VehicleMoveC2SPacket current = VehicleMoveC2SPacket.fromVehicle(vehicle);
            Vec3d pos = current.position();
            connection.sendPacket(
               new VehicleMoveC2SPacket(new Vec3d(pos.x, this.lastPacketY, pos.z), current.yaw(), current.pitch(), current.onGround())
            );
         }

         this.sentPacket = false;
      }

      if (this.delayLeft > 0) {
         this.delayLeft--;
      }
   }

   public void onPacketSend(VehicleMoveC2SPacket packet, int delay) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.isOnThread()) {
         ClientPlayerEntity player = mc.player;
         Entity vehicle = player != null ? player.getVehicle() : null;
         if (vehicle != null) {
            this.trackVehicle(vehicle);
            Vec3d pos = packet.position();
            double currentY = pos.y;
            if (this.delayLeft <= 0 && !this.sentPacket && this.shouldFlyDown(currentY) && EntityControlVehicleHook.isOnAir(vehicle) && !vehicle.isFlyingVehicle()
               )
             {
               ((AccessorBoatFlyServerboundMoveVehiclePacket)(Object)packet)
                  .anubis$setPosition(new Vec3d(pos.x, this.lastPacketY - 0.0313, pos.z));
               this.sentPacket = true;
               this.delayLeft = delay;
            }

            this.lastPacketY = currentY;
         }
      }
   }

   public void forget() {
      this.lastPacketY = Double.MAX_VALUE;
      this.sentPacket = false;
   }

   private boolean shouldFlyDown(double currentY) {
      return currentY >= this.lastPacketY ? true : this.lastPacketY - currentY < 0.0313;
   }

   private void trackVehicle(Entity vehicle) {
      if (vehicle != this.trackedVehicle) {
         this.trackedVehicle = vehicle;
         this.forget();
      }
   }
}
