package net.anubis.client.module.impl.misc;

import java.util.Set;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class AutoEatModule extends ClientModule {
   private static final Set<Item> NEVER_EAT = Set.of(
      Items.ROTTEN_FLESH,
      Items.SPIDER_EYE,
      Items.POISONOUS_POTATO,
      Items.PUFFERFISH,
      Items.CHICKEN,
      Items.CHORUS_FRUIT,
      Items.SUSPICIOUS_STEW,
      Items.ENCHANTED_GOLDEN_APPLE
   );
   private static final int NOT_FOUND = -1;
   private static final int OFFHAND_SLOT = -2;
   private static final float HEALING_BONUS = 100.0F;
   private static AutoEatModule instance;
   private final NumberSetting hunger = this.addSetting("hunger", new NumberSetting("Hunger", 16.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting health = this.addSetting("health", new NumberSetting("Health", 10.0F, 0.0F, 20.0F).step(1.0F));
   private final SlotReturn slots = new SlotReturn(this);
   private boolean eating;

   public AutoEatModule() {
      super("auto-eat", "Auto Eat", "Eats hotbar or offhand food once hunger or health drops too low.", ModuleCategory.MISC);
      instance = this;
   }

   @Override
   protected void onDisable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.stop(mc);
      this.slots.drop(mc, true);
   }

   public static boolean isEating() {
      AutoEatModule module = instance;
      return module != null && module.isEnabled() && module.eating;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.eat(mc);
      if (!this.eating) {
         this.slots.tick(mc, true);
      }
   }

   private void eat(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.interactionManager != null && mc.currentScreen == null && !AutoInvTotemModule.wantsHotbar()) {
         if (player.isUsingItem() && player.getActiveItem().contains(DataComponentTypes.FOOD)) {
            if (this.eating) {
               mc.options.useKey.setPressed(true);
            }
         } else if (!this.shouldEat(player)) {
            this.stop(mc);
         } else {
            int slot = this.findBestSlot(player);
            if (slot == -1 || MeleeAttacks.actedThisTick(player) || !MeleeAttacks.handsFree(mc, player)) {
               this.stop(mc);
            } else if (CombatMacroLock.heldByOther(this)) {
               this.eating = true;
            } else {
               if (slot == -2) {
                  mc.interactionManager.interactItem(player, Hand.OFF_HAND);
                  MeleeAttacks.claimTick(mc);
               } else if (!this.slots.take(player, slot)) {
                  this.stop(mc);
                  return;
               }

               this.eating = true;
               MeleeAttacks.takeAttackClicks(mc);
               mc.options.useKey.setPressed(true);
            }
         }
      } else {
         this.stop(mc);
      }
   }

   private boolean hungry(ClientPlayerEntity player) {
      return player.getHungerManager().getFoodLevel() <= this.hunger.getInt();
   }

   private boolean shouldEat(ClientPlayerEntity player) {
      boolean hurt = this.health.getInt() > 0 && player.getHealth() <= this.health.getInt();
      return this.hungry(player) || hurt;
   }

   private int findBestSlot(ClientPlayerEntity player) {
      boolean healing = !this.hungry(player);
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      float bestScore = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         FoodComponent food = foodOf(player, inventory.getStack(slot));
         if (food != null) {
            float score = score(food, healing);
            if (score > bestScore) {
               bestScore = score;
               best = slot;
            }
         }
      }

      FoodComponent offhandFood = foodOf(player, player.getOffHandStack());
      if (offhandFood != null && score(offhandFood, healing) > bestScore) {
         best = -2;
      }

      return best;
   }

   private static float score(FoodComponent food, boolean healing) {
      float score = food.nutrition() + food.saturation();
      return healing && food.canAlwaysEat() ? score + 100.0F : score;
   }

   private static FoodComponent foodOf(ClientPlayerEntity player, ItemStack stack) {
      if (!stack.isEmpty() && !NEVER_EAT.contains(stack.getItem()) && !player.getItemCooldownManager().isCoolingDown(stack)) {
         FoodComponent food = (FoodComponent)stack.get(DataComponentTypes.FOOD);
         return food != null && player.canConsume(food.canAlwaysEat()) ? food : null;
      } else {
         return null;
      }
   }

   private void stop(MinecraftClient mc) {
      if (this.eating) {
         this.eating = false;
         mc.options.useKey.setPressed(Crystals.isPhysicallyDown(mc, mc.options.useKey));
      }
   }
}
