package net.anubis.client.module.impl.donutmacros;

import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class AutoSellModule extends ClientModule {
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final long POLL_MS = 350L;
   private static final long COMMAND_COOLDOWN_MS = 4000L;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long CONFIRM_COOLDOWN_MS = 900L;
   private static final long POST_CONFIRM_MS = 250L;
   private static final int STUCK_CONFIRMS = 3;
   private final StringListSetting neverSell = this.addSetting(
      "never-sell", new StringListSetting("Never Sell", "minecraft:netherite_sword", "golden_apple", "minecraft:totem_of_undying").itemPicker()
   );
   private int menuId = -1;
   private long openingUntil;
   private long lastCommandAt;
   private long lastConfirmAt;
   private long nextPollAt;
   private boolean confirmPending;
   private String lastFingerprint = "";
   private int sameAfterConfirm;
   private int sold;
   private int confirmedCount;
   private boolean warnedNoOpen;

   public AutoSellModule() {
      super(
         "auto-sell", "Auto Sell", "Keeps the /sell menu open and keeps selling everything that is not on your Never Sell list.", ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.menuId = -1;
      this.openingUntil = 0L;
      this.lastCommandAt = 0L;
      this.lastConfirmAt = 0L;
      this.nextPollAt = 0L;
      this.confirmPending = false;
      this.lastFingerprint = "";
      this.sameAfterConfirm = 0;
      this.sold = 0;
      this.confirmedCount = 0;
      this.warnedNoOpen = false;
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && this.ours(player)) {
         player.closeHandledScreen();
      }

      this.menuId = -1;
      if (this.sold > 0) {
         ModuleChat.send(this, ModuleChat.body().text("Sold ").value(this.sold).text(this.sold == 1 ? " item." : " items."));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.getNetworkHandler() != null && mc.interactionManager != null && !player.isDead()) {
         long now = System.currentTimeMillis();
         if (this.menuId < 0 && this.openingUntil > 0L) {
            if (player.currentScreenHandler != player.playerScreenHandler && mc.currentScreen instanceof HandledScreen && containerSlots(player.currentScreenHandler) > 53) {
               this.menuId = player.currentScreenHandler.syncId;
               this.openingUntil = 0L;
               this.nextPollAt = now + 150L;
               this.warnedNoOpen = false;
            } else if (now > this.openingUntil) {
               this.openingUntil = 0L;
               if (!this.warnedNoOpen) {
                  ModuleChat.send(this, ModuleChat.body().warn("The sell menu did not open").text(", retrying."));
               }

               this.warnedNoOpen = true;
            }
         } else if (!this.ours(player)) {
            this.menuId = -1;
            if (mc.currentScreen == null && now - this.lastCommandAt >= 4000L) {
               player.networkHandler.sendChatCommand("sell");
               this.lastCommandAt = now;
               this.openingUntil = now + 10000L;
            }
         } else if (now >= this.nextPollAt) {
            this.nextPollAt = now + 350L;
            ScreenHandler menu = player.currentScreenHandler;
            if (this.confirmedCount > 0) {
               int gone = this.confirmedCount - countWaiting(menu);
               this.confirmedCount = 0;
               if (gone > 0) {
                  this.sold += gone;
                  ModuleChat.actionBar(this, ModuleChat.body().text("Sold ").value(this.sold).text(" items this session"));
               }
            }

            if (this.confirmPending) {
               this.confirmPending = false;
               if (waiting(menu) && now - this.lastConfirmAt >= 900L && this.sameAfterConfirm < 3) {
                  int before = countWaiting(menu);
                  mc.interactionManager.clickSlot(menu.syncId, 53, 0, SlotActionType.PICKUP, player);
                  this.lastConfirmAt = now;
                  this.lastFingerprint = fingerprint(menu);
                  this.confirmedCount = before;
                  this.nextPollAt = now + 250L;
               }
            } else {
               int moved = 0;

               for (Slot slot : menu.slots) {
                  if (slot.inventory instanceof PlayerInventory
                     && slot.hasStack()
                     && slot.getIndex() < 36
                     && !SellRoutine.neverSell(slot.getStack(), this.neverSell.get())) {
                     mc.interactionManager.clickSlot(menu.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, player);
                     moved++;
                  }
               }

               String fingerprint = fingerprint(menu);
               if (moved > 0 || !fingerprint.equals(this.lastFingerprint)) {
                  this.sameAfterConfirm = 0;
               } else if (waiting(menu)) {
                  this.sameAfterConfirm++;
               }

               if (moved > 0 || waiting(menu)) {
                  this.confirmPending = true;
                  this.nextPollAt = now + 50L;
               }
            }
         }
      } else {
         this.menuId = -1;
      }
   }

   private boolean ours(ClientPlayerEntity player) {
      return this.menuId >= 0 && player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId;
   }

   private static boolean waiting(ScreenHandler menu) {
      return countWaiting(menu) > 0;
   }

   private static int countWaiting(ScreenHandler menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory) && slot.id != 53 && slot.hasStack()) {
            count += slot.getStack().getCount();
         }
      }

      return count;
   }

   private static int containerSlots(ScreenHandler menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.inventory instanceof PlayerInventory)) {
            count++;
         }
      }

      return count;
   }

   private static String fingerprint(ScreenHandler menu) {
      StringBuilder text = new StringBuilder();

      for (Slot slot : menu.slots) {
         if (slot.inventory instanceof PlayerInventory) {
            break;
         }

         ItemStack stack = slot.getStack();
         if (!stack.isEmpty()) {
            text.append(slot.id).append(':').append(stack.getItem()).append('x').append(stack.getCount()).append(';');
         }
      }

      return text.toString();
   }
}
