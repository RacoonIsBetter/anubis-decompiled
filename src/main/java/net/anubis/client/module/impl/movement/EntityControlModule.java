package net.anubis.client.module.impl.movement;

import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.anubis.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;

@Environment(EnvType.CLIENT)
public final class EntityControlModule extends ClientModule {
   public static volatile EntityControlModule active;
   private final BooleanSetting boats = this.addSetting("boats", new BooleanSetting("Boats", true));
   private final BooleanSetting horses = this.addSetting("horses", new BooleanSetting("Horses", true));
   private final BooleanSetting camels = this.addSetting("camels", new BooleanSetting("Camels", true));
   private final BooleanSetting pigs = this.addSetting("pigs", new BooleanSetting("Pigs", true));
   private final BooleanSetting striders = this.addSetting("striders", new BooleanSetting("Striders", true));
   private final BooleanSetting nautiluses = this.addSetting("nautiluses", new BooleanSetting("Nautiluses", true));
   private final BooleanSetting happyGhasts = this.addSetting("happy-ghasts", new BooleanSetting("Happy Ghasts", true));
   private final BooleanSetting spoofSaddle = this.addSetting("spoof-saddle", new BooleanSetting("Spoof Saddle", false));
   private final BooleanSetting maxJump = this.addSetting("max-jump", new BooleanSetting("Max Jump", true));
   private final BooleanSetting lockYaw = this.addSetting("lock-yaw", new BooleanSetting("Lock Yaw", true));
   private final BooleanSetting cancelServerPackets = this.addSetting("cancel-server-packets", new BooleanSetting("Cancel Server Packets", false));

   public EntityControlModule() {
      super("entity-control", "Entity Control", "Lets you control rideable entities without a saddle.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceive);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
   }

   private void onPacketReceive(PacketReceiveNettyEvent event) {
      if (this.cancelServerPackets.get() && event.packet instanceof VehicleMoveS2CPacket) {
         event.setCancelled(true);
      }
   }

   public static boolean spoofSaddle() {
      EntityControlModule module = active;
      return module != null && module.spoofSaddle.get();
   }

   public static boolean maxJump() {
      EntityControlModule module = active;
      return module != null && module.maxJump.get();
   }

   public boolean locksYaw(Entity vehicle) {
      return this.lockYaw.get() && this.isTarget(vehicle);
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof AbstractBoatEntity) {
         return this.boats.get();
      } else {
         EntityType<?> type = entity.getType();
         if (type == EntityType.HORSE
            || type == EntityType.DONKEY
            || type == EntityType.MULE
            || type == EntityType.SKELETON_HORSE
            || type == EntityType.ZOMBIE_HORSE) {
            return this.horses.get();
         } else if (type == EntityType.CAMEL || type == EntityType.CAMEL_HUSK) {
            return this.camels.get();
         } else if (type == EntityType.PIG) {
            return this.pigs.get();
         } else if (type == EntityType.STRIDER) {
            return this.striders.get();
         } else if (type == EntityType.NAUTILUS || type == EntityType.ZOMBIE_NAUTILUS) {
            return this.nautiluses.get();
         } else {
            return type == EntityType.HAPPY_GHAST ? this.happyGhasts.get() : false;
         }
      }
   }
}
