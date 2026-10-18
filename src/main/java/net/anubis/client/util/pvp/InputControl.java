package net.anubis.client.util.pvp;

import net.anubis.client.module.impl.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class InputControl {
   private static boolean dropSprint;
   private static boolean releaseForward;
   private static boolean backward;
   private static boolean jump;
   private static boolean idle;

   private InputControl() {
   }

   public static void dropSprint() {
      dropSprint = true;
   }

   public static void releaseForward(boolean backward) {
      releaseForward = true;
      InputControl.backward |= backward;
   }

   public static void jump() {
      jump = true;
   }

   public static void idle() {
      idle = true;
   }

   public static PlayerInput shape(ClientPlayerEntity player, PlayerInput keys) {
      boolean forward = keys.forward();
      boolean back = keys.backward();
      boolean left = keys.left();
      boolean right = keys.right();
      boolean up = keys.jump();
      boolean sprint = keys.sprint() || SprintModule.holding();
      if (idle) {
         sprint = false;
         up = false;
         right = false;
         left = false;
         back = false;
         forward = false;
      }

      if (releaseForward && !idle) {
         forward = false;
         back |= backward;
      }

      if (jump && !idle) {
         up = true;
      }

      if (dropSprint || idle) {
         sprint = false;
         player.setSprinting(false);
      }

      idle = false;
      jump = false;
      backward = false;
      releaseForward = false;
      dropSprint = false;
      return forward == keys.forward()
            && back == keys.backward()
            && left == keys.left()
            && right == keys.right()
            && up == keys.jump()
            && sprint == keys.sprint()
         ? keys
         : new PlayerInput(forward, back, left, right, up, keys.sneak(), sprint);
   }
}
