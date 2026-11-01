package net.anubis.client.easyplace.builder;

import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@Environment(EnvType.CLIENT)
final class Eater {
   static final int HUNGRY = 14;
   private static final int EAT_TICKS = 70;
   private final Predicate<ItemStack> food;
   private int ticks;
   private int startFood = -1;

   Eater() {
      this(Eater::edible);
   }

   Eater(Predicate<ItemStack> food) {
      this.food = food;
   }

   static boolean hungry(ClientPlayerEntity player) {
      return player.getHungerManager().getFoodLevel() <= 14 || player.getHealth() < player.getMaxHealth() && player.getHungerManager().getFoodLevel() < 18;
   }

   static boolean hasFood(ClientPlayerEntity player) {
      return Hands.has(player, Eater::edible);
   }

   static boolean edible(ItemStack stack) {
      FoodComponent food = stack.get(DataComponentTypes.FOOD);
      return food != null && food.nutrition() > 0
         ? !stack.isOf(Items.CHORUS_FRUIT)
            && !stack.isOf(Items.ROTTEN_FLESH)
            && !stack.isOf(Items.SPIDER_EYE)
            && !stack.isOf(Items.POISONOUS_POTATO)
            && !stack.isOf(Items.PUFFERFISH)
            && !stack.isOf(Items.SUSPICIOUS_STEW)
            && !stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
            && !stack.isOf(Items.GOLDEN_APPLE)
            && !stack.isOf(Items.CHICKEN)
         : false;
   }

   Eater.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null) {
         if (this.startFood < 0) {
            this.startFood = player.getHungerManager().getFoodLevel();
         }

         if (player.getHungerManager().getFoodLevel() > this.startFood) {
            this.release(mc);
            return Eater.Result.DONE;
         }

         if (++this.ticks > 70) {
            this.release(mc);
            return Eater.Result.FAILED;
         }

         if (!Hands.hold(mc, player, this.food)) {
            return Eater.Result.RUNNING;
         }

         Aim.turn(mc, player.getYaw(), -75.0F, 22.0F);
         mc.options.useKey.setPressed(player.getPitch() < -60.0F);
         return Eater.Result.RUNNING;
      } else {
         return Eater.Result.FAILED;
      }
   }

   void release(MinecraftClient mc) {
      mc.options.useKey.setPressed(false);
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
