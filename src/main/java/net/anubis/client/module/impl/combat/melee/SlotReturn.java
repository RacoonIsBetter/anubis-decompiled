package net.anubis.client.module.impl.combat.melee;

import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class SlotReturn {
   private static final int NONE = -1;
   private final Object owner;
   private int previous = -1;
   private int taken = -1;

   public SlotReturn(Object owner) {
      this.owner = owner;
   }

   public boolean take(ClientPlayerEntity player, int slot) {
      PlayerInventory inventory = player.getInventory();
      if (inventory.getSelectedSlot() == slot && !this.holding()) {
         return true;
      }

      if (PlayerInventory.isValidHotbarIndex(slot) && !AutoInvTotemModule.wantsHotbar() && CombatMacroLock.tryAcquire(this.owner)) {
         if (!this.holding()) {
            this.previous = inventory.getSelectedSlot();
         }

         this.taken = slot;
         inventory.setSelectedSlot(slot);
         return true;
      } else {
         return false;
      }
   }

   public boolean holding() {
      return this.taken != -1;
   }

   public void tick(MinecraftClient mc, boolean back) {
      if (this.holding()) {
         ClientPlayerEntity player = mc.player;
         if (player == null) {
            this.finish(null, false);
         } else if (AutoInvTotemModule.wantsHotbar()) {
            CombatMacroLock.release(this.owner);
         } else if (mc.currentScreen == null && !MeleeAttacks.actedThisTick(player)) {
            this.finish(player, back);
         }
      }
   }

   public void drop(MinecraftClient mc, boolean back) {
      if (this.holding()) {
         this.finish(mc.player, back);
      }
   }

   private void finish(ClientPlayerEntity player, boolean back) {
      if (back && player != null && player.getInventory().getSelectedSlot() == this.taken && this.previous != this.taken) {
         player.getInventory().setSelectedSlot(this.previous);
      }

      CombatMacroLock.release(this.owner);
      this.previous = -1;
      this.taken = -1;
   }
}
