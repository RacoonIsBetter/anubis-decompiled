package net.anubis.client.module.impl.movement.elytrafly;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class ElytraFlyInventory {
   public static final int OFFHAND = 40;
   private static final int CHEST_ARMOR_ID = 6;
   private static int previousSlot = -1;

   private ElytraFlyInventory() {
   }

   public static int find(Predicate<ItemStack> isGood) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player == null ? -1 : find(isGood, 0, player.getInventory().size() - 1);
   }

   public static int find(Predicate<ItemStack> isGood, int start, int end) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return -1;
      }

      PlayerInventory inventory = player.getInventory();

      for (int i = start; i <= end; i++) {
         if (isGood.test(inventory.getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   public static int findInHotbar(Predicate<ItemStack> isGood) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return -1;
      } else if (isGood.test(player.getOffHandStack())) {
         return 40;
      } else {
         return isGood.test(player.getMainHandStack()) ? player.getInventory().getSelectedSlot() : find(isGood, 0, 8);
      }
   }

   public static boolean isHotbar(int index) {
      return index >= 0 && index <= 8;
   }

   public static void moveToHotbar(int fromIndex, int hotbarSlot) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         move(player, indexToId(player, fromIndex), indexToId(player, hotbarSlot));
      }
   }

   public static void moveToChest(int fromIndex) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         int fromId = indexToId(player, fromIndex);
         move(player, fromId, fromId == -1 ? -1 : 6);
      }
   }

   private static void move(ClientPlayerEntity player, int fromId, int toId) {
      ClientPlayerInteractionManager gameMode = MinecraftClient.getInstance().interactionManager;
      if (gameMode != null && fromId != -1 && toId != -1) {
         ScreenHandler menu = player.currentScreenHandler;
         boolean hadEmptyCursor = menu.getCursorStack().isEmpty();
         gameMode.clickSlot(menu.syncId, fromId, 0, SlotActionType.PICKUP, player);
         gameMode.clickSlot(menu.syncId, toId, 0, SlotActionType.PICKUP, player);
         if (hadEmptyCursor && !menu.getCursorStack().isEmpty()) {
            gameMode.clickSlot(menu.syncId, fromId, 0, SlotActionType.PICKUP, player);
         }
      }
   }

   private static int indexToId(ClientPlayerEntity player, int index) {
      if (player.currentScreenHandler != player.playerScreenHandler) {
         return -1;
      } else if (index >= 0 && index <= 8) {
         return 36 + index;
      } else if (index >= 9 && index < 36) {
         return index;
      } else if (index >= 36 && index <= 39) {
         return 8 - (index - 36);
      } else {
         return index == 40 ? 45 : -1;
      }
   }

   public static boolean swap(int slot, boolean swapBack) {
      if (slot == 40) {
         return true;
      }

      if (slot >= 0 && slot <= 8) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.interactionManager != null) {
            if (swapBack && previousSlot == -1) {
               previousSlot = player.getInventory().getSelectedSlot();
            } else if (!swapBack) {
               previousSlot = -1;
            }

            player.getInventory().setSelectedSlot(slot);
            ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean swapBack() {
      if (previousSlot == -1) {
         return false;
      }

      boolean result = swap(previousSlot, false);
      previousSlot = -1;
      return result;
   }

   public static void chestSwap() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         ItemStack current = player.getEquippedStack(EquipmentSlot.CHEST);
         EquippableComponent equippable = (EquippableComponent)current.get(DataComponentTypes.EQUIPPABLE);
         if (current.contains(DataComponentTypes.GLIDER)) {
            equipChestplate(player);
         } else if (equippable != null && equippable.slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId()) {
            equipElytra(player);
         } else if (!equipChestplate(player)) {
            equipElytra(player);
         }
      }
   }

   private static boolean equipChestplate(ClientPlayerEntity player) {
      DefaultedList<ItemStack> items = player.getInventory().getMainStacks();
      int bestSlot = -1;

      for (int i = 0; i < items.size(); i++) {
         Item item = ((ItemStack)items.get(i)).getItem();
         if (item == Items.DIAMOND_CHESTPLATE) {
            bestSlot = i;
         } else if (item == Items.NETHERITE_CHESTPLATE) {
            bestSlot = i;
            break;
         }
      }

      if (bestSlot != -1) {
         equip(player, bestSlot);
      }

      return bestSlot != -1;
   }

   private static void equipElytra(ClientPlayerEntity player) {
      DefaultedList<ItemStack> items = player.getInventory().getMainStacks();

      for (int i = 0; i < items.size(); i++) {
         if (((ItemStack)items.get(i)).contains(DataComponentTypes.GLIDER)) {
            equip(player, i);
            break;
         }
      }
   }

   private static void equip(ClientPlayerEntity player, int slot) {
      if (indexToId(player, slot) != -1) {
         moveToChest(slot);
         player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
      }
   }
}
