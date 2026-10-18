package net.anubis.client.module.impl.movement;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.anubis.client.module.impl.movement.elytrafly.ElytraFlyInventory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;

@Environment(EnvType.CLIENT)
public final class ElytraSwapModule extends ClientModule {
   private static final int[] SOURCE_INDICES = sourceIndices();
   private static final int MAX_WAIT_TICKS = 20;
   private static final int CHEST_SLOT_ID = 6;
   private static final Text SWAP_FAILED = ModuleChat.body().danger("Swap failed").text(", the item didn't move into the chest slot").build();
   private final KeySetting key = this.addSetting("key", new KeySetting("Key", -1));
   private final BooleanSetting swapBackOnLanding = this.addSetting("swap-back-on-landing", new BooleanSetting("Swap Back on Landing", true));
   private final NumberSetting landingDelay = this.addSetting("landing-delay", new NumberSetting("Landing Delay", 4.0F, 0.0F, 20.0F).step(1.0F));
   private boolean pending;
   private int pendingTicks;
   private boolean armed;
   private boolean glided;
   private int groundTicks;
   private int settleTicks;
   private int storedIndex = -1;
   private Item storedItem;

   public ElytraSwapModule() {
      super("elytra-swap", "Elytra Swap", "Swaps between your chestplate and elytra with one key.", ModuleCategory.MOVEMENT);
      this.usesNotifications();
      this.landingDelay.visibleWhen(this.swapBackOnLanding::get);
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.LOWEST, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.pending = false;
      this.pendingTicks = 0;
      this.disarm();
   }

   private void disarm() {
      this.armed = false;
      this.glided = false;
      this.groundTicks = 0;
      this.settleTicks = 0;
      this.storedIndex = -1;
      this.storedItem = null;
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
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.currentScreen == null && mc.player != null) {
            this.pending = true;
            this.pendingTicks = 0;
         }
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null) {
         this.reset();
      } else if (!this.pending) {
         if (this.armed) {
            this.followFlight(mc, player);
         }
      } else {
         String busy = this.busyReason(player);
         if (busy == null) {
            if (mc.currentScreen != null || this.swap(mc, player)) {
               this.pending = false;
               return;
            }

            busy = "you kept moving";
         }

         if (++this.pendingTicks > 20) {
            this.pending = false;
            ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", " + busy));
         }
      }
   }

   private String busyReason(ClientPlayerEntity player) {
      if (CombatMacroLock.heldByOther(this)) {
         return "another macro kept the inventory busy";
      } else if (AutoInvTotemModule.busy()) {
         return "the totem refill kept the inventory busy";
      } else if (player.isUsingItem()) {
         return "you kept using an item";
      } else {
         return ((AccessorLocalPlayer)player).anubis$lastSentInput().sneak() ? "you kept sneaking" : null;
      }
   }

   private void followFlight(MinecraftClient mc, ClientPlayerEntity player) {
      if (!this.swapBackOnLanding.get() || !player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) {
         this.disarm();
      } else if (player.isGliding()) {
         this.glided = true;
         this.groundTicks = 0;
      } else if (this.glided) {
         if (!player.isOnGround() && !player.isTouchingWater()) {
            this.groundTicks = 0;
         } else if (this.groundTicks++ >= this.landingDelay.getInt()) {
            if (mc.currentScreen == null && inventoryReady(player) && this.busyReason(player) == null) {
               if (!isBound(player, player.getEquippedStack(EquipmentSlot.CHEST))) {
                  int chestplate = this.chestplateToEquip(player);
                  if (chestplate == -1) {
                     ModuleChat.send(this, ModuleChat.body().danger("Can't swap back").text(", no ").name("chestplate").text(" left in your inventory"));
                  } else if (!AutoInvTotemModule.clickSafe(player)) {
                     if (++this.settleTicks <= 20) {
                        InputControl.idle();
                        return;
                     }

                     ModuleChat.send(this, ModuleChat.body().warn("Swap back skipped").text(", you kept moving"));
                  } else if (!equip(mc, player, chestplate)) {
                     ModuleChat.send(this, SWAP_FAILED);
                  }
               }

               this.disarm();
            }
         }
      }
   }

   private boolean swap(MinecraftClient mc, ClientPlayerEntity player) {
      if (player.isSpectator()) {
         return true;
      }

      if (!inventoryReady(player)) {
         ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", close the open container first"));
         return true;
      }

      ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
      if (isBound(player, worn)) {
         ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", your chest piece has ").name("Curse of Binding"));
         return true;
      }

      if (worn.contains(DataComponentTypes.GLIDER)) {
         int chestplate = this.chestplateToEquip(player);
         if (chestplate == -1) {
            ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no ").name("chestplate").text(" in your inventory"));
         } else {
            if (!AutoInvTotemModule.clickSafe(player)) {
               InputControl.idle();
               return false;
            }

            if (!equip(mc, player, chestplate)) {
               ModuleChat.send(this, SWAP_FAILED);
            }
         }

         this.disarm();
         return true;
      } else {
         int elytra = findElytra(player);
         if (elytra == -1) {
            ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no usable ").name("elytra").text(" in your inventory"));
            return true;
         }

         if (!AutoInvTotemModule.clickSafe(player)) {
            InputControl.idle();
            return false;
         }

         Item takenOff = isChestplate(worn) ? worn.getItem() : null;
         if (!equip(mc, player, elytra)) {
            ModuleChat.send(this, SWAP_FAILED);
            return true;
         }

         this.disarm();
         if (takenOff != null) {
            this.armed = this.swapBackOnLanding.get();
            this.storedIndex = elytra;
            this.storedItem = takenOff;
         }

         return true;
      }
   }

   private int chestplateToEquip(ClientPlayerEntity player) {
      if (this.storedIndex >= 0 && this.storedItem != null) {
         ItemStack stored = player.getInventory().getStack(this.storedIndex);
         if (stored.isOf(this.storedItem) && !isBound(player, stored)) {
            return this.storedIndex;
         }
      }

      return findBestChestplate(player);
   }

   private static boolean equip(MinecraftClient mc, ClientPlayerEntity player, int index) {
      Item item = player.getInventory().getStack(index).getItem();
      ((AccessorElytraFlyMultiPlayerGameMode)mc.interactionManager).anubis$elytraFlySyncSelected();
      if (!PlayerInventory.isValidHotbarIndex(index) && index != 40) {
         ElytraFlyInventory.moveToChest(index);
      } else {
         mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, 6, index, SlotActionType.SWAP, player);
      }

      player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
      return player.getEquippedStack(EquipmentSlot.CHEST).isOf(item);
   }

   private static boolean inventoryReady(ClientPlayerEntity player) {
      return player.currentScreenHandler == player.playerScreenHandler && player.currentScreenHandler.getCursorStack().isEmpty();
   }

   private static boolean isBound(ClientPlayerEntity player, ItemStack stack) {
      return !player.isCreative() && EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE);
   }

   private static int findElytra(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getStack(index);
         if (LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST) && !isBound(player, stack)) {
            int left = durabilityLeft(stack);
            if (left > bestLeft) {
               best = index;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static int findBestChestplate(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      double bestScore = -1.0;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getStack(index);
         if (isChestplate(stack) && !isBound(player, stack)) {
            double score = armorScore(stack);
            int left = durabilityLeft(stack);
            if (score > bestScore || score == bestScore && left > bestLeft) {
               best = index;
               bestScore = score;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.contains(DataComponentTypes.GLIDER)) {
         EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private static double armorScore(ItemStack stack) {
      AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
      return modifiers.applyOperations(EntityAttributes.ARMOR, 0.0, EquipmentSlot.CHEST)
         + modifiers.applyOperations(EntityAttributes.ARMOR_TOUGHNESS, 0.0, EquipmentSlot.CHEST);
   }

   private static int durabilityLeft(ItemStack stack) {
      return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : Integer.MAX_VALUE;
   }

   private static int[] sourceIndices() {
      int[] indices = new int[37];
      int i = 0;

      while (i < 36) {
         indices[i] = i++;
      }

      indices[36] = 40;
      return indices;
   }
}
