package net.anubis.client.module.impl.combat;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.totem.DoubleHand;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

@Environment(EnvType.CLIENT)
public final class AutoTotemModule extends ClientModule {
   private static final int MIN_CONFIRM_TICKS = 10;
   private static final int MAX_CONFIRM_TICKS = 40;
   private static final int GIVE_UP_COOLDOWN_TICKS = 10;
   private static AutoTotemModule instance;
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("reaction-delay", new NumberSetting("Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting doubleHand = this.addSetting("double-hand", new BooleanSetting("Double Hand", false));
   private final DoubleHand totemHand = new DoubleHand();
   private int tick;
   private int reactionAt = -1;
   private int idleUntil;
   private int confirmDeadline = -1;
   private int clickedAt = -1;
   private boolean settling;
   private int heldSlot = -1;
   private int previousSlot = -1;

   public AutoTotemModule() {
      super(
         "autototem",
         "Auto Totem",
         "Fills an empty offhand with a totem without opening the inventory; Double Hand also keeps one in the main hand while in danger.",
         ModuleCategory.COMBAT
      );
      instance = this;
      this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
   }

   public static boolean wantsHotbar() {
      AutoTotemModule module = instance;
      return module != null && module.isEnabled() && (module.heldSlot != -1 || module.totemHand.holding());
   }

   public static boolean busy() {
      AutoTotemModule module = instance;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (module != null && module.isEnabled() && player != null && !player.isSpectator() && !player.isInCreativeMode()) {
         PlayerInventory inventory = player.getInventory();
         return wantsHotbar()
            || module.awaitingServer(player)
            || module.clickedAt == module.tick
            || player.getOffHandStack().isEmpty() && (findStoredTotem(inventory) != -1 || findHotbarTotem(inventory) != -1);
      } else {
         return false;
      }
   }

   @Override
   protected void onEnable() {
      AutoInvTotemModule other = ModuleRegistry.get(AutoInvTotemModule.class);
      if (other != null) {
         other.setEnabled(false);
      }

      this.idleUntil = 0;
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         PlayerInventory inventory = player.getInventory();
         if (this.heldSlot != -1) {
            this.restoreHeld(inventory);
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
      boolean settled = this.settling;
      this.settling = false;
      if (player != null
         && mc.world != null
         && mc.interactionManager != null
         && mc.getNetworkHandler() != null
         && !player.isDead()
         && !player.isSpectator()
         && !player.isInCreativeMode()) {
         if (this.heldSlot != -1) {
            this.restoreHeld(player.getInventory());
         }

         this.totemHand.tick(mc, player, this.doubleHand.get());
         if (!player.getOffHandStack().isEmpty()) {
            this.reactionAt = -1;
            this.confirmDeadline = -1;
            if (this.hotbarRefill.get()) {
               this.refillHotbar(mc, player);
            }
         } else if (!this.awaitingServer(player)) {
            if (this.reactionAt < 0) {
               this.reactionAt = this.tick + this.delay.getInt();
            }

            if (this.tick >= this.reactionAt) {
               this.refillOffhand(mc, player, settled);
            }
         }
      } else {
         this.reset();
      }
   }

   private void refillOffhand(MinecraftClient mc, ClientPlayerEntity player, boolean settled) {
      PlayerInventory inventory = player.getInventory();
      int stored = findStoredTotem(inventory);
      if (stored != -1 && AutoInvTotemModule.clickSafe(player)) {
         click(mc, player, stored, 40);
         this.clickedAt = this.tick;
      } else if (mc.currentScreen == null) {
         int hotbar = findHotbarTotem(inventory);
         if (hotbar != -1) {
            this.swapOffhand(mc, player, hotbar);
         } else if (stored != -1 && !settled && this.tick >= this.idleUntil && !((AccessorLocalPlayer)player).anubis$lastSentInput().sneak()) {
            InputControl.idle();
            this.settling = true;
         } else if (settled) {
            this.idleUntil = this.tick + 10;
         }
      }
   }

   private void refillHotbar(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int target = this.hotbarSlot.getInt() - 1;
      if (!inventory.getStack(target).isOf(Items.TOTEM_OF_UNDYING) && AutoInvTotemModule.clickSafe(player)) {
         int stored = findStoredTotem(inventory);
         if (stored != -1) {
            click(mc, player, stored, target);
            this.clickedAt = this.tick;
         }
      }
   }

   private void swapOffhand(MinecraftClient mc, ClientPlayerEntity player, int slot) {
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      PlayerInventory inventory = player.getInventory();
      int previous = inventory.getSelectedSlot();
      inventory.setSelectedSlot(slot);
      ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
      connection.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
      this.confirmDeadline = this.tick + confirmWindow(connection, player);
      if (slot != previous) {
         if (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) {
            this.heldSlot = slot;
            this.previousSlot = previous;
         } else {
            inventory.setSelectedSlot(previous);
         }
      }
   }

   private void restoreHeld(PlayerInventory inventory) {
      if (inventory.getSelectedSlot() == this.heldSlot) {
         inventory.setSelectedSlot(this.previousSlot);
      }

      this.heldSlot = -1;
      this.previousSlot = -1;
   }

   private static void click(MinecraftClient mc, ClientPlayerEntity player, int inventorySlot, int button) {
      ScreenHandler menu = player.currentScreenHandler;
      if (menu.getCursorStack().isEmpty()) {
         ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
         menu.getSlotIndex(player.getInventory(), inventorySlot)
            .ifPresent(slot -> mc.interactionManager.clickSlot(menu.syncId, slot, button, SlotActionType.SWAP, player));
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

   private static int findHotbarTotem(PlayerInventory inventory) {
      int selected = inventory.getSelectedSlot();

      for (int i = 0; i < 9; i++) {
         if (i != selected && inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return inventory.getStack(selected).isOf(Items.TOTEM_OF_UNDYING) ? selected : -1;
   }

   private static int confirmWindow(ClientPlayNetworkHandler connection, ClientPlayerEntity player) {
      PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
      int latencyMs = info != null ? info.getLatency() : 0;
      return MathHelper.clamp(latencyMs / 25 + 4, 10, 40);
   }

   private boolean awaitingServer(ClientPlayerEntity player) {
      return this.confirmDeadline >= 0 && this.tick < this.confirmDeadline && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
   }

   private void reset() {
      this.reactionAt = -1;
      this.confirmDeadline = -1;
      this.settling = false;
      this.heldSlot = -1;
      this.previousSlot = -1;
      this.totemHand.reset();
   }
}
