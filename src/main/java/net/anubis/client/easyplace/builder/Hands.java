package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.anubis.client.easyplace.inventory.InventoryCalm;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@Environment(EnvType.CLIENT)
final class Hands {
   private static final long SWAP_WAIT_NANOS = 1000000000L;
   private static long swapSentAt = System.nanoTime() - 1000000000L;

   private Hands() {
   }

   static boolean hold(MinecraftClient mc, ClientPlayerEntity player, Predicate<ItemStack> want) {
      PlayerInventory inventory = player.getInventory();
      if (want.test(player.getMainHandStack())) {
         return true;
      }

      for (int i = 0; i < 9; i++) {
         if (want.test(inventory.getStack(i))) {
            inventory.setSelectedSlot(i);
            return true;
         }
      }

      if (player.currentScreenHandler == player.playerScreenHandler && mc.interactionManager != null) {
         for (int i = 9; i < 36; i++) {
            if (want.test(inventory.getStack(i))) {
               if (!InventoryCalm.settle(mc, player)) {
                  return false;
               }

               mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, inventory.getSelectedSlot(), SlotActionType.SWAP, player);
               close(mc, player);
               return false;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static boolean has(ClientPlayerEntity player, Predicate<ItemStack> want) {
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (want.test(inventory.getStack(i))) {
            return true;
         }
      }

      return want.test(player.getOffHandStack());
   }

   static boolean toOffhand(MinecraftClient mc, ClientPlayerEntity player, Predicate<ItemStack> want) {
      if (player.currentScreenHandler == player.playerScreenHandler
         && mc.interactionManager != null
         && mc.getNetworkHandler() != null
         && player.playerScreenHandler.getCursorStack().isEmpty()) {
         PlayerInventory inventory = player.getInventory();
         if (want.test(player.getMainHandStack())) {
            long now = System.nanoTime();
            if (now - swapSentAt < 1000000000L) {
               return true;
            }

            swapSentAt = now;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            return true;
         } else {
            for (int i = 0; i < 9; i++) {
               if (want.test(inventory.getStack(i))) {
                  inventory.setSelectedSlot(i);
                  return true;
               }
            }

            for (int i = 9; i < 36; i++) {
               if (want.test(inventory.getStack(i))) {
                  if (!InventoryCalm.settle(mc, player)) {
                     return true;
                  }

                  mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, 40, SlotActionType.SWAP, player);
                  close(mc, player);
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static void close(MinecraftClient mc, ClientPlayerEntity player) {
      if (mc.currentScreen == null) {
         player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.playerScreenHandler.syncId));
      }
   }
}
