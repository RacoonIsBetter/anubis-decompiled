package net.anubis.client.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.anubis.client.easyplace.inventory.InventoryCalm;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;

@Environment(EnvType.CLIENT)
final class BotKit {
   private static final int CLICK_GAP = 4;
   private int lastClick = -4;

   boolean needed(ClientPlayerEntity player, boolean anchors) {
      return next(player, wants(anchors)) != null;
   }

   boolean refill(MinecraftClient mc, ClientPlayerEntity player, boolean anchors) {
      if (mc.interactionManager != null
         && mc.currentScreen == null
         && player.currentScreenHandler == player.playerScreenHandler
         && player.playerScreenHandler.getCursorStack().isEmpty()
         && !AutoInvTotemModule.busy()
         && !player.isUsingItem()) {
         int[] move = next(player, wants(anchors));
         if (move == null) {
            return false;
         } else {
            int tick = player.age;
            if (InventoryCalm.settle(mc, player) && (tick < this.lastClick || tick - this.lastClick >= 4)) {
               mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, move[0], move[1], SlotActionType.SWAP, player);
               player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.playerScreenHandler.syncId));
               MeleeAttacks.noteAction(player);
               this.lastClick = tick;
               return true;
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private static List<BotKit.Want> wants(boolean anchors) {
      List<BotKit.Want> wants = new ArrayList<>();
      wants.add(new BotKit.Want(stack -> stack.isOf(Items.END_CRYSTAL), 16, 32));
      wants.add(new BotKit.Want(stack -> stack.isOf(Items.OBSIDIAN), 8, 16));
      wants.add(new BotKit.Want(MeleeAttacks::isMeleeWeapon, 1, 1));
      wants.add(new BotKit.Want(stack -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE), 1, 1));
      wants.add(new BotKit.Want(stack -> stack.isOf(Items.ENDER_PEARL), 1, 1));
      wants.add(new BotKit.Want(stack -> stack.isIn(ItemTags.PICKAXES), 1, 1));
      wants.add(new BotKit.Want(stack -> stack.isOf(Items.EXPERIENCE_BOTTLE), 1, 1));
      if (anchors) {
         wants.add(new BotKit.Want(stack -> stack.isOf(Items.RESPAWN_ANCHOR), 2, 4));
         wants.add(new BotKit.Want(stack -> stack.isOf(Items.GLOWSTONE), 2, 4));
      }

      return wants;
   }

   private static int[] next(ClientPlayerEntity player, List<BotKit.Want> wants) {
      PlayerInventory inventory = player.getInventory();

      for (BotKit.Want want : wants) {
         int inHotbar = 0;
         int smallest = -1;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (want.item().test(stack)) {
               inHotbar += stack.getCount();
               if (smallest < 0 || stack.getCount() < inventory.getStack(smallest).getCount()) {
                  smallest = i;
               }
            }
         }

         if (inHotbar < want.low()) {
            int source = -1;

            for (int i = 9; i < 36; i++) {
               ItemStack stack = inventory.getStack(i);
               if (want.item().test(stack) && (source < 0 || stack.getCount() > inventory.getStack(source).getCount())) {
                  source = i;
               }
            }

            if (source >= 0) {
               int bigger = inventory.getStack(source).getCount();
               if (smallest >= 0 && bigger > inHotbar && (inHotbar < want.topUp() || bigger >= inHotbar + 8)) {
                  return new int[]{source, smallest};
               }

               if (smallest < 0) {
                  int free = freeSlot(inventory, wants);
                  if (free >= 0) {
                     return new int[]{source, free};
                  }
               }
            }
         }
      }

      return null;
   }

   private static int freeSlot(PlayerInventory inventory, List<BotKit.Want> wants) {
      for (int i = 0; i < 9; i++) {
         if (inventory.getStack(i).isEmpty()) {
            return i;
         }
      }

      for (int i = 8; i >= 0; i--) {
         ItemStack stack = inventory.getStack(i);
         if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
            boolean used = false;

            for (BotKit.Want want : wants) {
               used |= want.item().test(stack);
            }

            if (!used) {
               return i;
            }
         }
      }

      return -1;
   }

   @Environment(EnvType.CLIENT)
   private record Want(Predicate<ItemStack> item, int low, int topUp) {
   }
}
