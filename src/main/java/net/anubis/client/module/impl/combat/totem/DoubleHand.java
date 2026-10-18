package net.anubis.client.module.impl.combat.totem;

import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class DoubleHand {
   private static final float DANGER_HEALTH = 6.0F;
   private static final double DANGER_RANGE = 6.0;
   private static final int SAFE_TICKS = 10;
   private boolean holding;
   private boolean rearmed = true;
   private int holdSlot = -1;
   private int returnSlot = -1;
   private int safeTicks;

   public boolean holding() {
      return this.holding;
   }

   public void tick(MinecraftClient mc, ClientPlayerEntity player, boolean on) {
      PlayerInventory inventory = player.getInventory();
      int selected = inventory.getSelectedSlot();
      boolean mainTotem = player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
      boolean danger = on && inDanger(mc.world, player);
      this.safeTicks = danger ? 0 : Math.min(this.safeTicks + 1, 10);
      if (this.safeTicks == 10) {
         this.holding = false;
         this.rearmed = true;
      }

      if (this.holding && selected != this.holdSlot && !mainTotem) {
         this.holding = false;
         this.rearmed = false;
         this.returnSlot = -1;
      }

      boolean clean = mc.currentScreen == null && !player.isUsingItem() && !MeleeAttacks.actedThisTick(player);
      if (this.holding) {
         if (mainTotem || !clean) {
            return;
         }

         int next = hotbarTotem(inventory);
         if (next != -1) {
            this.holdSlot = next;
            inventory.setSelectedSlot(next);
            return;
         }

         this.holding = false;
      } else if (danger && this.rearmed) {
         if (mainTotem) {
            this.holding = true;
            if (selected != this.holdSlot) {
               this.returnSlot = -1;
            }

            this.holdSlot = selected;
            return;
         }

         int slot = hotbarTotem(inventory);
         if (slot != -1 && clean && player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.holding = true;
            this.holdSlot = slot;
            this.returnSlot = selected;
            inventory.setSelectedSlot(slot);
            return;
         }
      }

      if (this.returnSlot != -1 && clean) {
         this.putBack(inventory);
      }
   }

   public void putBack(PlayerInventory inventory) {
      if (this.returnSlot != -1) {
         if (inventory.getSelectedSlot() == this.holdSlot) {
            inventory.setSelectedSlot(this.returnSlot);
         }

         this.returnSlot = -1;
      }
   }

   public void reset() {
      this.holding = false;
      this.rearmed = true;
      this.holdSlot = -1;
      this.returnSlot = -1;
      this.safeTicks = 0;
   }

   private static boolean inDanger(ClientWorld level, ClientPlayerEntity player) {
      float health = CrystalDamage.health(player);
      if (health <= 6.0F) {
         return true;
      }

      for (EndCrystalEntity crystal : level.getNonSpectatingEntities(EndCrystalEntity.class, player.getBoundingBox().expand(6.0))) {
         if (player.distanceTo(crystal) <= 6.0 && CrystalDamage.estimate(crystal.getEntityPos(), player) >= health) {
            return true;
         }
      }

      return false;
   }

   private static int hotbarTotem(PlayerInventory inventory) {
      int selected = inventory.getSelectedSlot();

      for (int i = 0; i < 9; i++) {
         if (i != selected && inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return inventory.getStack(selected).isOf(Items.TOTEM_OF_UNDYING) ? selected : -1;
   }
}
