package net.anubis.client.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
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
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.MaceItem;

@Environment(EnvType.CLIENT)
public final class MaceModule extends ClientModule {
   private static final String PICK_DAMAGE = "Damage";
   private static final String PICK_WIND_BURST = "Wind Burst";
   private static final String SMASH_DIRECT = "Direct";
   private static final String SMASH_DELAYED = "Delayed";
   private static final double REACH_MARGIN = 0.35;
   private static final float BREACH_GAIN = 1.15F;
   private final BooleanSetting autoSwap = this.addSetting("auto-swap", new BooleanSetting("Auto Swap", true));
   private final ModeSetting pick = this.addSetting("pick", new ModeSetting("Pick", "Damage", "Damage", "Wind Burst"));
   private final BooleanSetting breachSwap = this.addSetting("breach-swap", new BooleanSetting("Breach Swap", false));
   private final BooleanSetting swapBack = this.addSetting("swap-back", new BooleanSetting("Swap Back", true));
   private final BooleanSetting smashAssist = this.addSetting("smash-assist", new BooleanSetting("Smash Assist", false));
   private final ModeSetting smashTiming = this.addSetting("smash-timing", new ModeSetting("Smash Timing", "Delayed", "Direct", "Delayed"));
   private final NumberSetting minFall = this.addSetting("min-fall", new NumberSetting("Min Fall", 3.0F, 1.5F, 30.0F).step(0.5F));
   private final BooleanSetting mobs = this.addSetting("mobs", new BooleanSetting("Mobs", false));
   private final SlotReturn slots = new SlotReturn(this);
   private boolean smashed;

   public MaceModule() {
      super("mace", "Mace", "Swaps to the strongest mace for smash hits, with an optional smash assist.", ModuleCategory.COMBAT);
      this.pick.visibleWhen(this.autoSwap::get);
      this.breachSwap.visibleWhen(this.autoSwap::get);
      this.swapBack.visibleWhen(() -> this.autoSwap.get() || this.smashAssist.get());
      this.smashTiming.visibleWhen(this.smashAssist::get);
      this.minFall.visibleWhen(this.smashAssist::get);
      this.mobs.visibleWhen(this.smashAssist::get);
   }

   @Override
   protected void onDisable() {
      this.smashed = false;
      this.slots.drop(MinecraftClient.getInstance(), this.swapBack.get());
   }

   public void beforeAttack(ClientPlayerEntity player, Entity target) {
      if (this.autoSwap.get() && target instanceof LivingEntity living) {
         if (!MeleeAttacks.shieldBlocks(living, player) || MeleeAttacks.shieldBroken(living)) {
            boolean smash = MaceItem.shouldDealAdditionalDamage(player);
            if (smash || this.breachSwap.get()) {
               int slot = this.bestMace(player, living, smash);
               if (slot >= 0) {
                  if (smash || !(expected(player, player.getInventory().getStack(slot), living) < expected(player, player.getMainHandStack(), living) * 1.15F)) {
                     this.slots.take(player, slot);
                  }
               }
            }
         }
      }
   }

   private int bestMace(ClientPlayerEntity player, LivingEntity target, boolean smash) {
      boolean windBurst = smash && this.pick.is("Wind Burst");
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      boolean bestBurst = false;
      float bestDamage = 0.0F;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.isOf(Items.MACE) && !stack.willBreakNextUse() && (smash || level(stack, Enchantments.BREACH) != 0)) {
            boolean burst = windBurst && level(stack, Enchantments.WIND_BURST) > 0;
            float damage = expected(player, stack, target);
            if (best < 0 || (!bestBurst || burst) && (bestBurst != burst || !(damage <= bestDamage))) {
               best = slot;
               bestBurst = burst;
               bestDamage = damage;
            }
         }
      }

      return best;
   }

   private static float expected(ClientPlayerEntity player, ItemStack stack, LivingEntity target) {
      float charge = MeleeAttacks.charge(player);
      double fall = player.fallDistance;
      float damage = (float)(1.0 + MeleeAttacks.attackDamage(player.getMainHandStack())) * (0.2F + 0.8F * charge * charge);
      if (stack.isOf(Items.MACE) && MaceItem.shouldDealAdditionalDamage(player)) {
         damage += (float)(smashBonus(fall) + 0.5 * level(stack, Enchantments.DENSITY) * fall);
      }

      if (MeleeAttacks.wouldCrit(player, target)) {
         damage *= 1.5F;
      }

      int sharpness = level(stack, Enchantments.SHARPNESS);
      if (sharpness > 0) {
         damage += charge * (0.5F * sharpness + 0.5F);
      }

      return afterArmor(target, damage, level(stack, Enchantments.BREACH));
   }

   private static double smashBonus(double fall) {
      if (fall <= 3.0) {
         return 4.0 * fall;
      } else {
         return fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + fall - 8.0;
      }
   }

   private static float afterArmor(LivingEntity target, float damage, int breach) {
      float armor = target.getArmor();
      float toughness = (float)target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      float cut = MathHelper.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F) / 25.0F;
      return damage * (1.0F - MathHelper.clamp(cut - 0.15F * breach, 0.0F, 1.0F));
   }

   private static int level(ItemStack stack, RegistryKey<Enchantment> enchantment) {
      for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)entry.getKey()).matchesKey(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.player.isOnGround()) {
         this.smashed = false;
      }

      this.slots.tick(mc, this.swapBack.get());
   }

   @EventHandler
   public void onSmashTick(TickEvent event) {
      if (this.smashAssist.get()) {
         this.trySmash(MinecraftClient.getInstance());
      }
   }

   private void trySmash(MinecraftClient mc) {
      if (MeleeAttacks.canAct(mc) && !CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
         ClientPlayerEntity player = mc.player;
         if (!MeleeAttacks.actedThisTick(player)
            && !MeleeAttacks.clickPending(mc)
            && !MeleeAttacks.rightClickComing(mc)
            && !ServerRotation.pending()
            && mc.attackCooldown <= 0
            && MeleeAttacks.handsFree(mc, player)) {
            if (!(player.getVelocity().y >= 0.0)) {
               EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
               if (hit != null && hit.getEntity() instanceof LivingEntity target && MeleeAttacks.isEnemy(player, target)) {
                  if (target instanceof PlayerEntity || this.mobs.get()) {
                     if (this.smashReady(player, target)) {
                        if (MeleeAttacks.shieldBlocks(target, player) && !MeleeAttacks.shieldBroken(target)) {
                           int axe = ShieldBreakerModule.bestAxe(player);
                           if (canHoldOneTick(player, target) && axe >= 0 && (this.autoSwap.get() || this.swapBack.get()) && this.slots.take(player, axe)) {
                              if (MeleeAttacks.attack(mc, hit)) {
                                 MeleeAttacks.noteShieldBroken(target);
                                 MeleeAttacks.claimTick(mc);
                              } else {
                                 this.slots.drop(mc, true);
                              }
                           }
                        } else if (this.holdsSmash(player, target)) {
                           InputControl.dropSprint();
                           mc.attackCooldown = Math.max(mc.attackCooldown, 1);
                        } else {
                           if (MeleeAttacks.attack(mc, hit)) {
                              this.smashed = true;
                              MeleeAttacks.claimTick(mc);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void noteSmash() {
      this.smashed = true;
   }

   public boolean smashReady(ClientPlayerEntity player, LivingEntity target) {
      return this.isEnabled()
         && !this.smashed
         && MaceItem.shouldDealAdditionalDamage(player)
         && player.fallDistance >= this.minFall.get()
         && (player.getMainHandStack().isOf(Items.MACE) || this.autoSwap.get() && this.bestMace(player, target, true) >= 0);
   }

   public boolean holdsSmash(ClientPlayerEntity player, LivingEntity target) {
      boolean chargeSoon = MeleeAttacks.charge(player) <= 0.9F && player.getAttackCooldownProgress(1.5F) > 0.9F;
      return canHoldOneTick(player, target) && (this.smashTiming.is("Delayed") || chargeSoon || target.hurtTime > 0 || player.isSprinting());
   }

   private static boolean canHoldOneTick(ClientPlayerEntity player, LivingEntity target) {
      Vec3d motion = player.getVelocity();
      if (!player.getEntityWorld().isSpaceEmpty(player, player.getBoundingBox().offset(motion))) {
         return false;
      }

      double reach = player.getEntityInteractionRange() - 0.35;
      if (reach <= 0.0) {
         return false;
      }

      Vec3d nextEye = player.getEyePos().add(motion);
      Vec3d targetStep = target.getEntityPos().subtract(target.lastX, target.lastY, target.lastZ);
      Box nextBox = target.getBoundingBox().offset(targetStep).expand(target.getTargetingMargin());
      if (nextBox.contains(nextEye)) {
         return true;
      }

      Optional<Vec3d> aim = nextBox.raycast(nextEye, nextEye.add(player.getRotationVec(1.0F).multiply(reach)));
      return aim.isPresent() && MeleeAttacks.lineIsClear(player, nextEye, aim.get());
   }
}
