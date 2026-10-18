package net.anubis.client.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
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
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class SpearLungeModule extends ClientModule {
   private static final int BUFFER_TICKS = 10;
   private static final double POKE_RANGE = 4.4;
   private static final double POKE_MARGIN = 0.125;
   private final KeySetting key = this.addSetting("lunge-key", new KeySetting("Lunge Key", -1));
   private final BooleanSetting reachPoke = this.addSetting("reach-poke", new BooleanSetting("Reach Poke", false));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final SlotReturn slots = new SlotReturn(this);
   private boolean pending;
   private int pendingTicks;
   private ClientPlayerEntity owner;

   public SpearLungeModule() {
      super("spear-swap", "Spear Swap", "Swaps to your Lunge spear and lunges. Reach Poke stabs with a spear past sword reach.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.pending = false;
      this.owner = MinecraftClient.getInstance().player;
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.pending = false;
      this.owner = null;
      this.slots.drop(MinecraftClient.getInstance(), this.switchBack.get());
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
         if (mc.currentScreen == null && mc.player != null && mc.world != null) {
            if (bestSpear(mc.player.getInventory(), true) < 0) {
               ModuleChat.actionBar(this, ModuleChat.body().warn("No Lunge spear").text(" in your hotbar"));
            } else {
               this.pending = true;
               this.pendingTicks = 0;
            }
         }
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.slots.tick(mc, this.switchBack.get());
      ClientPlayerEntity player = mc.player;
      if (player != this.owner) {
         this.owner = player;
         this.pending = false;
      }

      if (player != null && mc.world != null && mc.interactionManager != null) {
         if (this.pending) {
            this.tryLunge(mc, player);
         }

         if (this.reachPoke.get() && MeleeAttacks.clickPending(mc)) {
            this.tryPoke(mc, player);
         }
      }
   }

   private void tryLunge(MinecraftClient mc, ClientPlayerEntity player) {
      if (++this.pendingTicks > 10) {
         this.pending = false;
      } else if (this.canStab(mc, player) && !player.isTouchingWater() && !player.isGliding() && !player.hasVehicle()) {
         if (player.getHungerManager().canSprint() || player.getAbilities().allowFlying) {
            PlayerInventory inventory = player.getInventory();
            int slot = bestSpear(inventory, true);
            if (slot < 0) {
               this.pending = false;
            } else {
               ItemStack spear = inventory.getStack(slot);
               if (canJab(mc, player, spear) && this.slots.take(player, slot)) {
                  this.pending = false;
                  stab(mc, player, (PiercingWeaponComponent)spear.get(DataComponentTypes.PIERCING_WEAPON));
               }
            }
         }
      }
   }

   private void tryPoke(MinecraftClient mc, ClientPlayerEntity player) {
      if (this.canStab(mc, player) && !player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON)) {
         if (MeleeAttacks.crosshairHit(mc) == null) {
            PlayerInventory inventory = player.getInventory();
            int slot = bestSpear(inventory, false);
            if (slot >= 0) {
               ItemStack spear = inventory.getStack(slot);
               if (canJab(mc, player, spear) && enemyPastReach(player) && this.slots.take(player, slot)) {
                  stab(mc, player, (PiercingWeaponComponent)spear.get(DataComponentTypes.PIERCING_WEAPON));
               }
            }
         }
      }
   }

   private static void stab(MinecraftClient mc, ClientPlayerEntity player, PiercingWeaponComponent weapon) {
      mc.interactionManager.attackWithPiercingWeapon(weapon);
      if (MeleeAttacks.actedThisTick(player)) {
         player.swingHand(Hand.MAIN_HAND);
         MeleeAttacks.claimTick(mc);
      }
   }

   private boolean canStab(MinecraftClient mc, ClientPlayerEntity player) {
      return MeleeAttacks.canAct(mc) && MeleeAttacks.handsFree(mc, player) && !MeleeAttacks.actedThisTick(player)
         ? !CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()
         : false;
   }

   private static boolean canJab(MinecraftClient mc, ClientPlayerEntity player, ItemStack spear) {
      return mc.attackCooldown <= 0 && !mc.interactionManager.isFlyingLocked() ? spear.isItemEnabled(mc.world.getEnabledFeatures()) && !player.isBelowMinimumAttackCharge(spear, 0) : false;
   }

   private static boolean enemyPastReach(ClientPlayerEntity player) {
      Rotation sent = ServerRotation.sent();
      Vec3d look = sent != null ? Vec3d.fromPolar(sent.pitch(), sent.yaw()) : player.getRotationVec(1.0F);
      Vec3d eye = player.getEyePos();
      Vec3d end = eye.add(look.multiply(4.4));
      double reach = player.getEntityInteractionRange();
      Box area = player.getBoundingBox().stretch(look.multiply(4.4)).expand(1.0);

      for (Entity entity : player.getEntityWorld().getOtherEntities(player, area)) {
         if (MeleeAttacks.isEnemy(player, entity)) {
            Optional<Vec3d> at = entity.getBoundingBox().expand(0.125).raycast(eye, end);
            if (at.isPresent() && eye.distanceTo(at.get()) > reach && MeleeAttacks.lineIsClear(player, eye, at.get())) {
               return true;
            }
         }
      }

      return false;
   }

   private static int bestSpear(PlayerInventory inventory, boolean lunge) {
      int selected = inventory.getSelectedSlot();
      int best = -1;
      int bestLevel = 0;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.contains(DataComponentTypes.PIERCING_WEAPON) && !stack.willBreakNextUse()) {
            int level = lungeLevel(stack);
            boolean better = !lunge ? best < 0 || level < bestLevel : level > bestLevel || level > 0 && level == bestLevel && slot == selected;
            if (better) {
               best = slot;
               bestLevel = level;
            }
         }
      }

      return best;
   }

   private static int lungeLevel(ItemStack stack) {
      for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)entry.getKey()).matchesKey(Enchantments.LUNGE)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }
}
