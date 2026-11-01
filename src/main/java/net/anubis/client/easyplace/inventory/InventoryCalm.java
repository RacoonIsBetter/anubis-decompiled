package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class InventoryCalm {
   private static final double STOPPED = 0.0025;
   private static final double HALTED = 1.0E-4;

   private InventoryCalm() {
   }

   public static boolean idle(ClientPlayerEntity player) {
      PlayerInput in = player.input.playerInput;
      return !moving(in) && !in.sneak();
   }

   public static boolean still(ClientPlayerEntity player) {
      return idle(player) && player.isOnGround() && player.getVelocity().horizontalLengthSquared() < 0.0025;
   }

   public static boolean settle(MinecraftClient mc, ClientPlayerEntity player) {
      if (still(player)) {
         return true;
      }

      GameOptions options = mc.options;
      options.forwardKey.setPressed(false);
      options.backKey.setPressed(false);
      options.leftKey.setPressed(false);
      options.rightKey.setPressed(false);
      options.jumpKey.setPressed(false);
      options.sprintKey.setPressed(false);
      options.useKey.setPressed(false);
      options.attackKey.setPressed(false);
      PlayerInput in = player.input.playerInput;
      if (moving(in) || !player.isOnGround() || player.getVelocity().horizontalLengthSquared() >= 1.0E-4) {
         return false;
      }

      if (!overGround(player)) {
         return true;
      }

      options.sneakKey.setPressed(false);
      return false;
   }

   private static boolean moving(PlayerInput in) {
      return in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.sprint();
   }

   private static boolean overGround(ClientPlayerEntity player) {
      BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 0.05, player.getZ());
      return !player.getEntityWorld().getBlockState(below).getCollisionShape(player.getEntityWorld(), below).isEmpty();
   }
}
