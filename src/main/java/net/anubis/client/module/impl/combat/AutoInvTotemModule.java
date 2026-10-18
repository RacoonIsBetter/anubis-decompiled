package net.anubis.client.module.impl.combat;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.totem.DoubleHand;
import net.anubis.client.module.impl.movement.SneakModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

@Environment(EnvType.CLIENT)
public final class AutoInvTotemModule extends ClientModule {
   private static final String INVENTORY = "Inventory";
   private static final String HOTBAR_SWAP = "Hotbar Swap";
   private static final int MIN_CONFIRM_TICKS = 10;
   private static final int MAX_CONFIRM_TICKS = 40;
   private static final int PASS_COOLDOWN_TICKS = 4;
   private static final int GIVE_UP_COOLDOWN_TICKS = 10;
   private static final int MAX_PASS_SWAPS = 2;
   private static final int MAX_RESELECTS = 2;
   private static final int MAX_STEPS_PER_TICK = 3;
   private static final int MAX_UNSAFE_TICKS = 10;
   private static AutoInvTotemModule instance;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Inventory", "Inventory", "Hotbar Swap"));
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
   private final NumberSetting reactionDelay = this.addSetting("reaction-delay", new NumberSetting("Reaction Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting swapDelay = this.addSetting("swap-delay", new NumberSetting("Swap Delay", 1.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting closeDelay = this.addSetting("close-delay", new NumberSetting("Close Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting switchBackDelay = this.addSetting("switch-back-delay", new NumberSetting("Switch Back Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting doubleHand = this.addSetting("double-hand", new BooleanSetting("Double Hand", false));
   private final DoubleHand totemHand = new DoubleHand();
   private AutoInvTotemModule.Step step = AutoInvTotemModule.Step.IDLE;
   private int tick;
   private int readyAt;
   private int reactionAt = -1;
   private int idleUntil;
   private int confirmDeadline = -1;
   private int clickedAt = -1;
   private int swappedAt = -1;
   private boolean openedInventory;
   private int passSwaps;
   private int unsafeTicks;
   private int totemSlot = -1;
   private int previousSlot = -1;
   private int reselects;

   public AutoInvTotemModule() {
      super(
         "autoinvtotem",
         "Auto Inventory Totem",
         "Refills the offhand totem after a pop through the inventory or a hotbar swap; Double Hand also keeps one in the main hand while in danger.",
         ModuleCategory.COMBAT
      );
      instance = this;
      this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
      this.switchBackDelay.visibleWhen(() -> this.mode.is("Hotbar Swap"));
   }

   public static boolean wantsHotbar() {
      AutoInvTotemModule module = instance;
      return module != null && module.isEnabled()
         ? module.step == AutoInvTotemModule.Step.HOTBAR_SWAP || module.step == AutoInvTotemModule.Step.HOTBAR_RETURN || module.totemHand.holding()
         : AutoTotemModule.wantsHotbar();
   }

   public static boolean busy() {
      AutoInvTotemModule module = instance;
      if (module != null && module.isEnabled()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && !player.isSpectator() && !player.isInCreativeMode()) {
            PlayerInventory inventory = player.getInventory();
            return module.step != AutoInvTotemModule.Step.IDLE
               || module.totemHand.holding()
               || module.awaitingServer(player)
               || module.clickedAt == module.tick
               || player.getOffHandStack().isEmpty() && (findStoredTotem(inventory) != -1 || module.hotbarSource(inventory) != -1);
         } else {
            return false;
         }
      } else {
         return AutoTotemModule.busy();
      }
   }

   public static boolean clickSafe(ClientPlayerEntity player) {
      AccessorLocalPlayer sent = (AccessorLocalPlayer)player;
      PlayerInput input = sent.anubis$lastSentInput();
      return !sent.anubis$wasSprinting()
         && !input.forward()
         && !input.backward()
         && !input.left()
         && !input.right()
         && !input.jump()
         && !input.sneak();
   }

   @Override
   protected void onEnable() {
      AutoTotemModule other = ModuleRegistry.get(AutoTotemModule.class);
      if (other != null) {
         other.setEnabled(false);
      }

      this.idleUntil = 0;
   }

   @Override
   protected void onDisable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         PlayerInventory inventory = player.getInventory();
         if (this.openedInventory && mc.currentScreen instanceof InventoryScreen) {
            player.closeHandledScreen();
         }

         if ((this.step == AutoInvTotemModule.Step.HOTBAR_SWAP || this.step == AutoInvTotemModule.Step.HOTBAR_RETURN) && mc.currentScreen == null) {
            this.restoreSlot(inventory);
         }

         this.totemHand.putBack(inventory);
      }

      this.reset();
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onTick(TickEvent event) {
      this.tick++;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null
         && mc.world != null
         && mc.interactionManager != null
         && mc.getNetworkHandler() != null
         && !player.isDead()
         && !player.isSpectator()
         && !player.isInCreativeMode()) {
         if (this.step == AutoInvTotemModule.Step.IDLE) {
            this.tryStart(mc, player);
         }

         if (this.step == AutoInvTotemModule.Step.IDLE) {
            this.totemHand.tick(mc, player, this.doubleHand.get());
         }

         for (int i = 0; i < 3 && this.step != AutoInvTotemModule.Step.IDLE && this.tick >= this.readyAt; i++) {
            switch (this.step) {
               case INVENTORY_SWAP:
                  this.inventorySwap(mc, player);
                  break;
               case INVENTORY_CLOSE:
                  this.closeInventory(mc, player);
                  break;
               case HOTBAR_SWAP:
                  this.hotbarSwap(mc, player);
                  break;
               case HOTBAR_RETURN:
                  this.hotbarReturn(mc, player);
            }
         }
      } else {
         if (player != null && this.openedInventory && mc.currentScreen instanceof InventoryScreen) {
            player.closeHandledScreen();
         }

         this.reset();
      }
   }

   private void tryStart(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      if (!player.getOffHandStack().isEmpty()) {
         this.confirmDeadline = -1;
      }

      boolean offhandJob = player.getOffHandStack().isEmpty()
         && !this.awaitingServer(player)
         && (findStoredTotem(inventory) != -1 || this.hotbarSource(inventory) != -1);
      if (!offhandJob && !this.refillDue(mc, player)) {
         this.reactionAt = -1;
      } else {
         if (this.reactionAt < 0) {
            this.reactionAt = this.tick + this.reactionDelay.getInt();
         }

         if (this.tick >= this.reactionAt && this.tick >= this.idleUntil) {
            if (mc.currentScreen instanceof HandledScreen) {
               this.startPass(false);
            } else if (mc.currentScreen == null) {
               boolean canOpen = !mc.interactionManager.hasRidingInventory() && !sneakForced();
               if (!offhandJob || this.hotbarSource(inventory) == -1 || !this.mode.is("Hotbar Swap") && canOpen) {
                  if (canOpen) {
                     mc.setScreen(new InventoryScreen(player));
                     this.startPass(true);
                  } else {
                     this.idleUntil = this.tick + 10;
                  }
               } else {
                  this.startHotbarSwap(inventory);
               }
            }
         }
      }
   }

   private boolean refillDue(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      return !player.getOffHandStack().isEmpty() && this.refillWanted(inventory) && findStoredTotem(inventory) != -1
         ? mc.currentScreen instanceof HandledScreen || mc.currentScreen == null && clickSafe(player) && !this.totemHand.holding() && !CombatMacroLock.heldByOther(this)
         : false;
   }

   private boolean refillWanted(PlayerInventory inventory) {
      return this.hotbarRefill.get() && !inventory.getStack(this.hotbarSlot.getInt() - 1).isOf(Items.TOTEM_OF_UNDYING);
   }

   private static boolean sneakForced() {
      SneakModule sneak = SneakModule.active;
      return sneak != null && (sneak.doVanilla() || sneak.doPacket());
   }

   private void startPass(boolean opened) {
      this.openedInventory = opened;
      this.passSwaps = 0;
      this.unsafeTicks = 0;
      this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, opened ? Math.max(1, this.swapDelay.getInt()) : this.swapDelay.getInt());
   }

   private void inventorySwap(MinecraftClient mc, ClientPlayerEntity player) {
      if (mc.currentScreen instanceof HandledScreen<?> screen && (!this.openedInventory || mc.currentScreen instanceof InventoryScreen)) {
         ScreenHandler menu = screen.getScreenHandler();
         if (clickSafe(player) && menu.getCursorStack().isEmpty()) {
            int source = this.passSource(player);
            if (source != -1 && this.passSwaps < 2) {
               int button = player.getOffHandStack().isEmpty() ? 40 : this.hotbarSlot.getInt() - 1;
               menu.getSlotIndex(player.getInventory(), source)
                  .ifPresent(index -> mc.interactionManager.clickSlot(menu.syncId, index, button, SlotActionType.SWAP, player));
               this.clickedAt = this.tick;
               this.passSwaps++;
               if (this.passSource(player) != -1 && this.passSwaps < 2) {
                  this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, Math.max(1, this.swapDelay.getInt()));
               } else {
                  this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay.getInt());
               }
            } else {
               this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay.getInt());
            }
         } else {
            if (++this.unsafeTicks > 10) {
               this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, 0);
            } else {
               this.readyAt = this.tick + 1;
            }
         }
      } else {
         this.finishPass();
      }
   }

   private void closeInventory(MinecraftClient mc, ClientPlayerEntity player) {
      if (this.passSource(player) != -1 && this.passSwaps < 2 && this.unsafeTicks <= 10 && mc.currentScreen instanceof HandledScreen) {
         this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, Math.max(1, this.swapDelay.getInt()));
      } else {
         if (this.openedInventory && mc.currentScreen instanceof InventoryScreen) {
            if (!clickSafe(player) && ++this.unsafeTicks <= 10) {
               this.readyAt = this.tick + 1;
               return;
            }

            player.closeHandledScreen();
         }

         this.finishPass();
      }
   }

   private void finishPass() {
      this.idleUntil = this.tick + 4;
      this.endSequence();
   }

   private int passSource(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int stored = findStoredTotem(inventory);
      if (player.getOffHandStack().isEmpty()) {
         return stored != -1 ? stored : this.hotbarSource(inventory);
      } else {
         return this.refillWanted(inventory) ? stored : -1;
      }
   }

   private void startHotbarSwap(PlayerInventory inventory) {
      this.totemSlot = this.hotbarSource(inventory);
      this.previousSlot = inventory.getSelectedSlot();
      this.reselects = 0;
      inventory.setSelectedSlot(this.totemSlot);
      this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay.getInt());
   }

   private void hotbarSwap(MinecraftClient mc, ClientPlayerEntity player) {
      if (mc.currentScreen != null) {
         this.readyAt = this.tick + 1;
      } else if (!player.getOffHandStack().isEmpty()) {
         this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, 0);
      } else {
         PlayerInventory inventory = player.getInventory();
         if (inventory.getSelectedSlot() == this.totemSlot && player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            ClientPlayNetworkHandler connection = mc.getNetworkHandler();
            ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();

            while (mc.options.swapHandsKey.wasPressed()) {
            }

            connection.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            this.confirmDeadline = this.tick + confirmWindow(connection, player);
            this.swappedAt = this.tick;
            this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, this.switchBackDelay.getInt());
         } else {
            int slot = this.hotbarSource(inventory);
            if (slot != -1 && this.reselects++ < 2) {
               this.totemSlot = slot;
               inventory.setSelectedSlot(slot);
               this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay.getInt());
            } else {
               this.idleUntil = this.tick + 10;
               this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, 0);
            }
         }
      }
   }

   private void hotbarReturn(MinecraftClient mc, ClientPlayerEntity player) {
      if (mc.currentScreen == null && (this.swappedAt != this.tick || !player.isUsingItem() || player.getActiveHand() != Hand.MAIN_HAND)) {
         this.restoreSlot(player.getInventory());
         this.endSequence();
      } else {
         this.readyAt = this.tick + 1;
      }
   }

   private void restoreSlot(PlayerInventory inventory) {
      if (PlayerInventory.isValidHotbarIndex(this.previousSlot) && this.previousSlot != this.totemSlot && inventory.getSelectedSlot() == this.totemSlot) {
         inventory.setSelectedSlot(this.previousSlot);
      }
   }

   private int hotbarSource(PlayerInventory inventory) {
      int selected = inventory.getSelectedSlot();
      int reserved = this.hotbarRefill.get() ? this.hotbarSlot.getInt() - 1 : -1;
      int fallback = -1;

      for (int i = 0; i < 9; i++) {
         if (i != selected && inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            if (i != reserved) {
               return i;
            }

            fallback = i;
         }
      }

      if (fallback != -1) {
         return fallback;
      } else {
         return !this.totemHand.holding() && inventory.getStack(selected).isOf(Items.TOTEM_OF_UNDYING) ? selected : -1;
      }
   }

   private static int findStoredTotem(PlayerInventory inventory) {
      for (int i = 9; i < 36; i++) {
         if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return -1;
   }

   private static int confirmWindow(ClientPlayNetworkHandler connection, ClientPlayerEntity player) {
      PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
      int latencyMs = info != null ? info.getLatency() : 0;
      return MathHelper.clamp(latencyMs / 25 + 4, 10, 40);
   }

   private boolean awaitingServer(ClientPlayerEntity player) {
      return this.confirmDeadline >= 0 && this.tick < this.confirmDeadline && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
   }

   private void schedule(AutoInvTotemModule.Step next, int ticks) {
      this.step = next;
      this.readyAt = this.tick + ticks;
   }

   private void endSequence() {
      this.step = AutoInvTotemModule.Step.IDLE;
      this.reactionAt = -1;
      this.openedInventory = false;
      this.passSwaps = 0;
      this.unsafeTicks = 0;
      this.totemSlot = -1;
      this.previousSlot = -1;
      this.reselects = 0;
   }

   private void reset() {
      this.endSequence();
      this.confirmDeadline = -1;
      this.totemHand.reset();
   }

   @Environment(EnvType.CLIENT)
   private enum Step {
      IDLE,
      INVENTORY_SWAP,
      INVENTORY_CLOSE,
      HOTBAR_SWAP,
      HOTBAR_RETURN;
   }
}
