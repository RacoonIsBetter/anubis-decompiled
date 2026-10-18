package net.anubis.client.module.impl.misc;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.service.humanize.HumanInventoryTiming;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class AutoReplenishModule extends ClientModule {
   private static final int TRACKED_SLOTS = 10;
   private static final int OFFHAND_TRACKED_SLOT = 9;
   private static final int OFFHAND_SWAP_BUTTON = 40;
   private static final int MAIN_INVENTORY_FIRST = 9;
   private static final int MAIN_INVENTORY_LAST = 35;
   private static final double FAST_MOVEMENT_THRESHOLD_SQ = 0.06;
   private static final String MODE_INSTANT = "Instant";
   private static final String MODE_LEGIT = "Legit";
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Instant", "Instant", "Legit"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 1.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting threshold = this.addSetting("threshold", new NumberSetting("Threshold", 8.0F, 1.0F, 64.0F).step(1.0F));
   private final BooleanSetting offhand = this.addSetting("offhand", new BooleanSetting("Offhand", true));
   private final ItemStack[] lastKnown = new ItemStack[10];
   private final ItemStack[] pendingRefill = new ItemStack[10];
   private static final int QUIET_TICKS_BEFORE_OPEN = 10;
   private static final int MAX_WAIT_TICKS = 10;
   private ClientPlayerEntity trackedPlayer;
   private int stage;
   private int delayTimer;
   private int quietTicks;
   private int waitTicks;
   private int lastSelectedSlot = -1;
   private int activeSlot = -1;
   private ItemStack activeReference = ItemStack.EMPTY;
   private boolean wasScreenOpen;

   public AutoReplenishModule() {
      super("auto-replenish", "Auto Replenish", "Refills a hotbar or offhand slot from the main inventory once it runs low.", ModuleCategory.MISC);
      this.delay.visibleWhen(() -> this.mode.is("Instant"));
      this.mode.onChange(this::resetJob);
   }

   @Override
   protected void onEnable() {
      this.resetJob();
   }

   @Override
   protected void onDisable() {
      this.resetJob();
      this.trackedPlayer = null;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null) {
         if (player != this.trackedPlayer) {
            this.resync(player);
         }

         this.updateTracking(mc, player);
         if (this.delayTimer > 0) {
            this.delayTimer--;
         } else if (this.mode.is("Instant")) {
            this.refillNow(mc, player);
         } else {
            for (int transitions = 0; transitions < 3; transitions++) {
               switch (this.stage) {
                  case 0:
                     if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
                        return;
                     }

                     if (player.isInCreativeMode() || AutoInvTotemModule.busy()) {
                        return;
                     }

                     if (this.isMovingFast(player) || ((AccessorLocalPlayer)player).anubis$lastSentInput().sneak()) {
                        return;
                     }

                     if (player.isUsingItem() || mc.interactionManager.isBreakingBlock()) {
                        return;
                     }

                     if (this.quietTicks < 10) {
                        return;
                     }

                     int slot = this.nextPending(player);
                     if (slot < 0) {
                        return;
                     }

                     this.activeSlot = slot;
                     this.activeReference = this.pendingRefill[slot];
                     this.pendingRefill[slot] = ItemStack.EMPTY;
                     this.wasScreenOpen = mc.currentScreen instanceof InventoryScreen;
                     this.stage = this.wasScreenOpen ? 2 : 1;
                     break;
                  case 1:
                     mc.setScreen(new InventoryScreen(player));
                     this.stage = 2;
                     this.delayTimer = HumanInventoryTiming.openGuiDelayTicks();
                     return;
                  case 2:
                     if (!(mc.currentScreen instanceof InventoryScreen screen)) {
                        if (this.activeSlot >= 0 && !this.activeReference.isEmpty() && this.pendingRefill[this.activeSlot].isEmpty()) {
                           this.pendingRefill[this.activeSlot] = this.activeReference;
                        }

                        this.resetJob();
                        return;
                     }

                     boolean safe = AutoInvTotemModule.clickSafe(player) && !AutoInvTotemModule.busy();
                     if (!safe && ++this.waitTicks <= 10) {
                        return;
                     }

                     if (!safe && this.activeSlot >= 0 && this.pendingRefill[this.activeSlot].isEmpty()) {
                        this.pendingRefill[this.activeSlot] = this.activeReference;
                     }

                     if (safe && this.stillNeedsRefill(player)) {
                        Slot source = this.findSourceSlot(screen.getScreenHandler(), player);
                        if (source != null) {
                           int button = this.activeSlot == 9 ? 40 : this.activeSlot;
                           ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
                           mc.interactionManager.clickSlot(((PlayerScreenHandler)screen.getScreenHandler()).syncId, source.id, button, SlotActionType.SWAP, player);
                        }
                     }

                     this.stage = 3;
                     this.delayTimer = HumanInventoryTiming.closeGuiDelayTicks();
                     return;
                  case 3:
                     if (!this.wasScreenOpen && mc.currentScreen instanceof InventoryScreen) {
                        mc.player.closeHandledScreen();
                     }

                     this.resetJob();
                     return;
                  default:
                     this.resetJob();
               }
            }
         }
      }
   }

   private void refillNow(MinecraftClient mc, ClientPlayerEntity player) {
      if (mc.currentScreen == null && !player.isInCreativeMode() && !player.isUsingItem()) {
         if (AutoInvTotemModule.clickSafe(player) && !AutoInvTotemModule.busy()) {
            int slot = this.nextPending(player);
            if (slot >= 0) {
               this.activeSlot = slot;
               this.activeReference = this.pendingRefill[slot];
               this.pendingRefill[slot] = ItemStack.EMPTY;
               ScreenHandler menu = player.playerScreenHandler;
               Slot source = this.findSourceSlot(menu, player);
               if (source != null && menu.getCursorStack().isEmpty()) {
                  int button = this.activeSlot == 9 ? 40 : this.activeSlot;
                  ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
                  mc.interactionManager.clickSlot(menu.syncId, source.id, button, SlotActionType.SWAP, player);
                  this.delayTimer = this.delay.getInt();
               }

               this.activeSlot = -1;
               this.activeReference = ItemStack.EMPTY;
            }
         }
      }
   }

   private void resync(ClientPlayerEntity player) {
      this.trackedPlayer = player;

      for (int i = 0; i < 10; i++) {
         ItemStack current = i == 9 ? player.getOffHandStack() : player.getInventory().getStack(i);
         this.lastKnown[i] = current.copy();
         this.pendingRefill[i] = ItemStack.EMPTY;
      }

      this.resetJob();
   }

   private void updateTracking(MinecraftClient mc, ClientPlayerEntity player) {
      boolean foreignScreenOpen = this.stage == 0 && mc.currentScreen instanceof HandledScreen;
      boolean infinite = player.isInCreativeMode();
      boolean changed = false;
      int selectedSlot = player.getInventory().getSelectedSlot();
      if (selectedSlot != this.lastSelectedSlot) {
         this.lastSelectedSlot = selectedSlot;
         changed = true;
      }

      for (int i = 0; i < 10; i++) {
         if (i == 9 && !this.offhand.get()) {
            this.lastKnown[i] = player.getOffHandStack().copy();
            this.pendingRefill[i] = ItemStack.EMPTY;
         } else {
            ItemStack current = i == 9 ? player.getOffHandStack() : player.getInventory().getStack(i);
            ItemStack previous = this.lastKnown[i];
            if (!ItemStack.areEqual(previous, current)) {
               changed = true;
            }

            this.lastKnown[i] = current.copy();
            if (current.isOf(Items.TOTEM_OF_UNDYING) || previous.isOf(Items.TOTEM_OF_UNDYING)) {
               this.pendingRefill[i] = ItemStack.EMPTY;
            } else if (infinite) {
               this.pendingRefill[i] = ItemStack.EMPTY;
            } else if (!foreignScreenOpen) {
               boolean sameItem = previous.isEmpty() || current.getItem() == previous.getItem();
               if (current.isEmpty() && !previous.isEmpty()) {
                  this.pendingRefill[i] = previous.copy();
               } else if (!previous.isEmpty() && !previous.isStackable() && !sameItem) {
                  this.pendingRefill[i] = previous.copy();
               } else if (!current.isStackable()
                  || current.isEmpty()
                  || !sameItem
                  || current.getCount() > this.threshold.getInt()
                  || !this.pendingRefill[i].isEmpty() && this.pendingRefill[i].getItem() != current.getItem()) {
                  if (!current.isEmpty() && current.getCount() > this.threshold.getInt()) {
                     this.pendingRefill[i] = ItemStack.EMPTY;
                  }
               } else {
                  this.pendingRefill[i] = current.copy();
               }
            }
         }
      }

      this.quietTicks = changed ? 0 : Math.min(this.quietTicks + 1, 1000);
   }

   private int nextPending(ClientPlayerEntity player) {
      for (int i = 0; i < 10; i++) {
         if (!this.pendingRefill[i].isEmpty() && this.hasSource(player, i, this.pendingRefill[i])) {
            return i;
         }
      }

      return -1;
   }

   private boolean stillNeedsRefill(ClientPlayerEntity player) {
      ItemStack current = this.activeSlot == 9 ? player.getOffHandStack() : player.getInventory().getStack(this.activeSlot);
      if (current.isEmpty()) {
         return true;
      } else {
         return current.getItem() != this.activeReference.getItem() ? true : current.isStackable() && current.getCount() <= this.threshold.getInt();
      }
   }

   private boolean hasSource(ClientPlayerEntity player, int trackedSlot, ItemStack reference) {
      ItemStack targetNow = trackedSlot == 9 ? player.getOffHandStack() : player.getInventory().getStack(trackedSlot);

      for (int i = 9; i <= 35; i++) {
         if (isViableSource(player.getInventory().getStack(i), targetNow, reference)) {
            return true;
         }
      }

      return false;
   }

   private Slot findSourceSlot(ScreenHandler menu, ClientPlayerEntity player) {
      ItemStack targetNow = this.activeSlot == 9 ? player.getOffHandStack() : player.getInventory().getStack(this.activeSlot);
      int bestCount = ItemStack.areItemsEqual(targetNow, this.activeReference) ? targetNow.getCount() : 0;
      Slot best = null;

      for (Slot slot : menu.slots) {
         if (slot.id >= 9 && slot.id <= 35) {
            ItemStack candidate = slot.getStack();
            if (matchesReference(candidate, this.activeReference) && candidate.getCount() > bestCount) {
               best = slot;
               bestCount = candidate.getCount();
            }
         }
      }

      return best;
   }

   private static boolean isViableSource(ItemStack candidate, ItemStack targetNow, ItemStack reference) {
      int have = ItemStack.areItemsEqual(targetNow, reference) ? targetNow.getCount() : 0;
      return matchesReference(candidate, reference) && candidate.getCount() > have;
   }

   private static boolean matchesReference(ItemStack candidate, ItemStack reference) {
      if (candidate.isEmpty()) {
         return false;
      } else {
         return reference.isStackable()
            ? ItemStack.areItemsAndComponentsEqual(candidate, reference)
            : ItemStack.shouldSkipHandAnimationOnSwap(candidate, reference, type -> type == DataComponentTypes.DAMAGE);
      }
   }

   private boolean isMovingFast(ClientPlayerEntity player) {
      if (player.isSprinting()) {
         return true;
      } else if (player.hasVehicle()) {
         Entity vehicle = player.getRootVehicle();
         double dx = vehicle.getX() - vehicle.lastX;
         double dz = vehicle.getZ() - vehicle.lastZ;
         return dx * dx + dz * dz > 0.06;
      } else {
         Vec3d velocity = player.getVelocity();
         return velocity.x * velocity.x + velocity.z * velocity.z > 0.06;
      }
   }

   private void resetJob() {
      this.stage = 0;
      this.delayTimer = 0;
      this.waitTicks = 0;
      this.activeSlot = -1;
      this.activeReference = ItemStack.EMPTY;
      this.wasScreenOpen = false;
   }
}
