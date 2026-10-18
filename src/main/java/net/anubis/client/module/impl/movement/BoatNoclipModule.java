package net.anubis.client.module.impl.movement;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.anubis.client.module.impl.movement.entitycontrol.VehicleAntiKick;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class BoatNoclipModule extends ClientModule {
   public static volatile BoatNoclipModule active;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 10.0F, 0.0F, 50.0F).step(0.5F));
   private final NumberSetting speedInsideBlocks = this.addSetting(
      "speed-inside-blocks", new NumberSetting("Speed Inside Blocks", 5.0F, 0.0F, 50.0F).step(0.5F)
   );
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical Speed", 6.0F, 0.0F, 20.0F).step(0.5F));
   private final NumberSetting fallSpeed = this.addSetting("fall-speed", new NumberSetting("Fall Speed", 0.0F, 0.0F, 10.0F).step(0.1F));
   private final BooleanSetting antiKick = this.addSetting("anti-fly-kick", new BooleanSetting("Anti Fly Kick", true));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 40.0F, 1.0F, 80.0F).step(1.0F));
   private final VehicleAntiKick antiKicker = new VehicleAntiKick();
   private AbstractBoatEntity noclipBoat;

   public BoatNoclipModule() {
      super("boat-noclip", "Boat Noclip", "Fly your boat through blocks and entities.", ModuleCategory.MOVEMENT);
      this.delay.visibleWhen(this.antiKick::get);
      this.antiKick.onChange(this.antiKicker::forget);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      BoatFlyModule fly = ModuleRegistry.get(BoatFlyModule.class);
      if (fly != null) {
         fly.setEnabled(false);
      }

      this.antiKicker.start(this.delay.getInt());
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
      this.antiKicker.stop();
      this.releaseBoat();
   }

   private void onPreTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (this.noclipBoat != null && (player == null || player.getVehicle() != this.noclipBoat)) {
         this.releaseBoat();
      }

      this.antiKicker.tick();
   }

   private void onPacketSend(PacketSendEvent event) {
      if (this.antiKick.get() && event.packet instanceof VehicleMoveC2SPacket packet) {
         this.antiKicker.onPacketSend(packet, this.delay.getInt());
      }
   }

   public void onVehicleSetback() {
      this.antiKicker.forget();
   }

   private void releaseBoat() {
      if (this.noclipBoat != null) {
         this.noclipBoat.noClip = false;
      }

      this.noclipBoat = null;
   }

   public Vec3d boatMovement(AbstractBoatEntity boat, ClientPlayerEntity player) {
      if (this.noclipBoat != boat) {
         this.releaseBoat();
         this.noclipBoat = boat;
      }

      boat.noClip = true;
      double bps = isInsideBlock(boat, player) ? this.speedInsideBlocks.get() : this.speed.get();
      Vec3d horizontal = EntityControlVehicleHook.horizontalVelocity(player, bps);
      MinecraftClient mc = MinecraftClient.getInstance();
      double velY = 0.0;
      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.jumpKey)) {
         velY += this.verticalSpeed.get() / 20.0;
      }

      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.sprintKey)) {
         velY -= this.verticalSpeed.get() / 20.0;
      } else {
         velY -= this.fallSpeed.get() / 20.0;
      }

      return new Vec3d(horizontal.x, velY, horizontal.z);
   }

   private static boolean isInsideBlock(AbstractBoatEntity boat, ClientPlayerEntity player) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return false;
      }

      Box boatBox = boat.getBoundingBox();
      Box box = boatBox.shrink(0.0, 0.05, 0.0).expand(0.5, 0.0, 0.5);
      int maxX = MathHelper.floor(box.maxX);
      int maxY = MathHelper.floor(box.maxY);
      int maxZ = MathHelper.floor(box.maxZ);
      Mutable pos = new Mutable();

      for (int x = MathHelper.floor(box.minX); x <= maxX; x++) {
         for (int y = MathHelper.floor(box.minY); y <= maxY; y++) {
            for (int z = MathHelper.floor(box.minZ); z <= maxZ; z++) {
               BlockState state = level.getBlockState(pos.set(x, y, z));
               if (!state.isAir()) {
                  VoxelShape shape = state.getCollisionShape(level, pos, ShapeContext.absent());
                  if (!shape.isEmpty() && boatBox.intersects(shape.getBoundingBox().offset(pos))) {
                     return true;
                  }
               }
            }
         }
      }

      for (Entity other : level.getOtherEntities(boat, box)) {
         if (other != player && !other.hasPassenger(boat) && !boat.hasPassenger(other) && other.isAlive() && other.getBoundingBox().intersects(box)) {
            return true;
         }
      }

      return false;
   }
}
