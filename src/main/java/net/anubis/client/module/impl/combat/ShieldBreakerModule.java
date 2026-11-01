package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class ShieldBreakerModule extends ClientModule {
   private static final String MODE_PASSIVE = "Passive";
   private static final String MODE_AUTO = "Auto";
   private static final int REARM_TICKS = 10;
   private static final int MAX_LEAD_TICKS = 4;
   private static final int HOLD_TICKS = 2;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Passive", "Passive", "Auto"));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final SlotReturn slots = new SlotReturn(this);
   private int ticks;
   private int rearmTick;
   private int takenTick;

   public ShieldBreakerModule() {
      super("shield-breaker", "Shield Breaker", "Swaps to an axe for any hit into a raised shield. Auto also makes the hit.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
      this.rearmTick = 0;
   }

   @Override
   protected void onDisable() {
      this.slots.drop(MinecraftClient.getInstance(), this.switchBack.get());
   }

   public void beforeAttack(ClientPlayerEntity player, Entity target) {
      if (target instanceof PlayerEntity other && !MeleeAttacks.disablesShields(player.getMainHandStack())) {
         if (fightingHand(player)) {
            if (!MeleeAttacks.shieldBroken(other) && blocksSoon(player, other)) {
               int slot = bestAxe(player);
               if (slot >= 0 && this.slots.take(player, slot)) {
                  this.takenTick = this.ticks;
                  MeleeAttacks.noteShieldBroken(other);
               }
            }
         }
      }
   }

   private static boolean fightingHand(ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      return main.isEmpty() || MeleeAttacks.isMeleeWeapon(main);
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      this.ticks++;
      if (this.ticks - this.takenTick >= 2) {
         this.slots.tick(MinecraftClient.getInstance(), this.switchBack.get());
      }
   }

   @EventHandler
   public void onAutoTick(TickEvent event) {
      if (this.mode.is("Auto")) {
         this.tryHit(MinecraftClient.getInstance());
      }
   }

   private void tryHit(MinecraftClient mc) {
      if (this.ticks >= this.rearmTick && MeleeAttacks.canAct(mc)) {
         if (!CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
            ClientPlayerEntity player = mc.player;
            if (!MeleeAttacks.actedThisTick(player) && !ServerRotation.pending() && !MeleeAttacks.clickPending(mc)) {
               EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
               if (hit != null && hit.getEntity() instanceof PlayerEntity target && MeleeAttacks.isEnemy(player, target)) {
                  if (!MeleeAttacks.shieldBroken(target) && blocksSoon(player, target)) {
                     if (MeleeAttacks.disablesShields(player.getMainHandStack()) || bestAxe(player) >= 0 && fightingHand(player)) {
                        if (MeleeAttacks.attack(mc, hit)) {
                           this.rearmTick = this.ticks + 10;
                           MeleeAttacks.claimTick(mc);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean blocksSoon(ClientPlayerEntity player, PlayerEntity target) {
      if (!target.isUsingItem()) {
         return false;
      }

      ItemStack shield = target.getActiveItem();
      BlocksAttacksComponent blocks = shield.get(DataComponentTypes.BLOCKS_ATTACKS);
      return blocks != null && target.getItemUseTime() + leadTicks(player) >= blocks.getBlockDelayTicks()
         ? MeleeAttacks.shieldBlocks(target, player, shield)
         : false;
   }

   private static int leadTicks(ClientPlayerEntity player) {
      PlayerListEntry info = player.networkHandler.getPlayerListEntry(player.getUuid());
      return info == null ? 0 : MathHelper.clamp(Math.round(info.getLatency() / 50.0F), 0, 4);
   }

   static int bestAxe(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      double bestDamage = Double.NEGATIVE_INFINITY;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (MeleeAttacks.disablesShields(stack) && !stack.willBreakNextUse()) {
            double damage = MeleeAttacks.attackDamage(stack);
            if (damage > bestDamage) {
               best = slot;
               bestDamage = damage;
            }
         }
      }

      return best;
   }
}
