package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult.Success;
import net.minecraft.util.ActionResult.SwingSource;

@Environment(EnvType.CLIENT)
public final class KeyThrowModule extends ClientModule {
   private static final int BUFFER_TICKS = 10;
   private final KeySetting pearlKey = this.addSetting("pearl-key", new KeySetting("Pearl Key", -1));
   private final KeySetting windChargeKey = this.addSetting("wind-charge-key", new KeySetting("Wind Charge Key", -1));
   private final SlotReturn slots = new SlotReturn(this);
   private Item pending;
   private int pendingTicks;

   public KeyThrowModule() {
      super("key-throw", "Key Throw", "Throws a pearl or a wind charge with a key and swaps back.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.pending = null;
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.pending = null;
      this.slots.drop(MinecraftClient.getInstance(), true);
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      if (ModuleKeybinds.isBindable(pressed)) {
         Item item = pressed == this.pearlKey.value() ? Items.ENDER_PEARL : (pressed == this.windChargeKey.value() ? Items.WIND_CHARGE : null);
         MinecraftClient mc = MinecraftClient.getInstance();
         if (item != null && mc.currentScreen == null && mc.player != null) {
            this.pending = item;
            this.pendingTicks = 0;
         }
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.slots.tick(mc, true);
      if (mc.player == null) {
         this.pending = null;
      } else if (this.pending != null) {
         this.tryThrow(mc, mc.player);
      }
   }

   private void tryThrow(MinecraftClient mc, ClientPlayerEntity player) {
      if (++this.pendingTicks > 10) {
         this.pending = null;
      } else if (MeleeAttacks.canAct(mc) && MeleeAttacks.handsFree(mc, player) && !MeleeAttacks.actedThisTick(player)) {
         if (!CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
            PlayerInventory inventory = player.getInventory();
            boolean offhand = player.getOffHandStack().isOf(this.pending);
            int slot = player.getMainHandStack().isOf(this.pending) ? inventory.getSelectedSlot() : Crystals.hotbarSlotOf(inventory, this.pending);
            if (!offhand && slot < 0) {
               this.pending = null;
            } else {
               ItemStack stack = offhand ? player.getOffHandStack() : inventory.getStack(slot);
               if (!player.getItemCooldownManager().isCoolingDown(stack)) {
                  boolean windCharge = this.pending == Items.WIND_CHARGE;
                  if (!ServerRotation.pending() && (!windCharge || ServerRotation.canRotate(player))) {
                     if (offhand || this.slots.take(player, slot)) {
                        this.pending = null;
                        if (windCharge) {
                           ServerRotation.request(new Rotation(player.getYaw(), 90.0F));
                           if (player.isOnGround()) {
                              InputControl.jump();
                           }
                        }

                        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
                        if (mc.interactionManager.interactItem(player, hand) instanceof Success success) {
                           if (success.swingSource() == SwingSource.CLIENT) {
                              player.swingHand(hand);
                           }

                           mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
                        }

                        MeleeAttacks.claimTick(mc);
                     }
                  }
               }
            }
         }
      }
   }
}
