package net.anubis.client.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public final class ServerRangeInventorySource implements InventoryPickSource {
   private static final int SYNC_TIMEOUT_TICKS = 12;
   private static final int HOTBAR_SIZE = 9;
   private static final int MAIN_END = 35;
   private static final int OFFHAND_SLOT = 40;
   private final MinecraftClient mc;
   private ServerRangeInventorySource.PendingPick pending;

   public ServerRangeInventorySource(MinecraftClient mc) {
      this.mc = mc;
   }

   @Override
   public InventoryPickResult prepare(Item item, long tick) {
      return this.prepare(item, tick, false);
   }

   public InventoryPickResult prepare(Item item, long tick, boolean driving) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.interactionManager != null) {
         if (this.pending != null) {
            if (this.pending.item == item && tick - this.pending.startedTick <= 12L) {
               return this.pollPending();
            }

            this.pending = null;
         }

         if (player.getOffHandStack().isOf(item)) {
            return InventoryPickResult.ready(item, Hand.OFF_HAND, 40);
         }

         PlayerInventory inventory = player.getInventory();
         if (player.getMainHandStack().isOf(item)) {
            return InventoryPickResult.ready(item, Hand.MAIN_HAND, inventory.getSelectedSlot());
         }

         int found = find(inventory, item);
         if (found < 0) {
            return InventoryPickResult.missing(item);
         }

         if (found < 9) {
            return InventoryPickResult.ready(item, Hand.MAIN_HAND, found);
         }

         if (player.currentScreenHandler != player.playerScreenHandler) {
            return InventoryPickResult.missing(item);
         }

         int destination = findHotbarDestination(inventory);
         if (driving ? InventoryCalm.settle(this.mc, player) : InventoryCalm.idle(player)) {
            this.mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, found, destination, SlotActionType.SWAP, player);
            if (this.mc.currentScreen == null) {
               player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.playerScreenHandler.syncId));
            }

            this.pending = new ServerRangeInventorySource.PendingPick(item, destination, tick);
            return InventoryPickResult.waiting(item, destination);
         } else {
            return InventoryPickResult.waiting(item, destination);
         }
      } else {
         return InventoryPickResult.missing(item);
      }
   }

   private InventoryPickResult pollPending() {
      if (!this.mc.player.getInventory().getStack(this.pending.hotbarSlot).isOf(this.pending.item)) {
         return InventoryPickResult.waiting(this.pending.item, this.pending.hotbarSlot);
      }

      InventoryPickResult result = InventoryPickResult.ready(this.pending.item, Hand.MAIN_HAND, this.pending.hotbarSlot);
      this.pending = null;
      return result;
   }

   private static int find(PlayerInventory inventory, Item item) {
      for (int slot = 0; slot <= 35; slot++) {
         if (inventory.getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private static int findHotbarDestination(PlayerInventory inventory) {
      for (int slot = 0; slot < 9; slot++) {
         if (inventory.getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return inventory.getSelectedSlot();
   }

   @Override
   public void reset() {
      this.pending = null;
   }

   @Environment(EnvType.CLIENT)
   private record PendingPick(Item item, int hotbarSlot, long startedTick) {
   }
}
