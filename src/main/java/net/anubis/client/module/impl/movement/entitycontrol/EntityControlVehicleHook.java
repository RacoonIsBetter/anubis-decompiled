package net.anubis.client.module.impl.movement.entitycontrol;

import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.movement.BoatFlyModule;
import net.anubis.client.module.impl.movement.BoatNoclipModule;
import net.anubis.client.module.impl.movement.EntityControlModule;
import net.anubis.client.module.impl.movement.EntitySpeedModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class EntityControlVehicleHook {
   private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);
   public static volatile boolean anyActive;

   private EntityControlVehicleHook() {
   }

   public static void refresh() {
      anyActive = EntityControlModule.active != null || EntitySpeedModule.active != null || BoatFlyModule.active != null || BoatNoclipModule.active != null;
   }

   public static Vec3d onEntityMove(Entity entity, MovementType type, Vec3d movement) {
      if (type == MovementType.SELF && entity.getEntityWorld().isClient()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && player.getVehicle() == entity && entity.getControllingPassenger() == player) {
            Vec3d velocity = entity.getVelocity();
            BoatNoclipModule noclip = BoatNoclipModule.active;
            if (noclip != null && entity instanceof AbstractBoatEntity boat) {
               Vec3d result = noclip.boatMovement(boat, player);
               entity.setYaw(player.getYaw());
               if (movement == velocity) {
                  entity.setVelocity(result);
               }

               return result;
            } else {
               EntityControlModule control = EntityControlModule.active;
               EntitySpeedModule speed = EntitySpeedModule.active;
               BoatFlyModule fly = BoatFlyModule.active;
               double velX = velocity.x;
               double velY = velocity.y;
               double velZ = velocity.z;
               boolean overridden = false;
               if (speed != null) {
                  if (speed.appliesTo(entity)) {
                     Vec3d horizontal = horizontalVelocity(player, speed.horizontalSpeed());
                     velX = horizontal.x;
                     velZ = horizontal.z;
                     overridden = true;
                  }
               } else if (fly != null) {
                  Vec3d horizontal = horizontalVelocity(player, fly.horizontalSpeed());
                  velX = horizontal.x;
                  velZ = horizontal.z;
                  overridden = true;
               }

               if (fly != null) {
                  velY = fly.verticalVelocity();
                  overridden = true;
               }

               if (fly != null || control != null && control.locksYaw(entity)) {
                  entity.setYaw(player.getYaw());
               }

               if (!overridden) {
                  return movement;
               }

               Vec3d result = new Vec3d(velX, velY, velZ);
               if (movement == velocity) {
                  entity.setVelocity(result);
               }

               return result;
            }
         } else {
            return movement;
         }
      } else {
         return movement;
      }
   }

   public static boolean locksBoatTurning(AbstractBoatEntity boat) {
      if (!anyActive || !boat.getEntityWorld().isClient()) {
         return false;
      } else if (BoatFlyModule.active == null && BoatNoclipModule.active == null) {
         EntityControlModule control = EntityControlModule.active;
         return control != null && control.locksYaw(boat);
      } else {
         return true;
      }
   }

   public static Vec3d horizontalVelocity(ClientPlayerEntity player, double bps) {
      float yaw = player.getYaw();
      Vec3d forward = Vec3d.fromPolar(0.0F, yaw);
      Vec3d right = Vec3d.fromPolar(0.0F, yaw + 90.0F);
      PlayerInput input = player.input.playerInput;
      double velX = 0.0;
      double velZ = 0.0;
      boolean longitudinal = false;
      if (input.forward()) {
         velX += forward.x / 20.0 * bps;
         velZ += forward.z / 20.0 * bps;
         longitudinal = true;
      }

      if (input.backward()) {
         velX -= forward.x / 20.0 * bps;
         velZ -= forward.z / 20.0 * bps;
         longitudinal = true;
      }

      boolean lateral = false;
      if (input.right()) {
         velX += right.x / 20.0 * bps;
         velZ += right.z / 20.0 * bps;
         lateral = true;
      }

      if (input.left()) {
         velX -= right.x / 20.0 * bps;
         velZ -= right.z / 20.0 * bps;
         lateral = true;
      }

      if (longitudinal && lateral) {
         velX *= DIAGONAL;
         velZ *= DIAGONAL;
      }

      return new Vec3d(velX, 0.0, velZ);
   }

   public static boolean isPhysicallyDown(KeyBinding mapping) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.currentScreen == null && !mapping.isUnbound() && !isFreecamActive()) {
         Key key = KeyBindingHelper.getBoundKeyOf(mapping);
         Type type = key.getCategory();
         if (type == Type.KEYSYM) {
            return AnubisKeyPolling.isKeyDown(mc.getWindow(), key.getCode());
         } else {
            return type == Type.MOUSE ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == 1 : mapping.isPressed();
         }
      } else {
         return false;
      }
   }

   public static boolean isOnAir(Entity entity) {
      return entity.getEntityWorld().getStatesInBox(entity.getBoundingBox().expand(0.0625).stretch(0.0, -0.55, 0.0)).allMatch(AbstractBlockState::isAir);
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }
}
