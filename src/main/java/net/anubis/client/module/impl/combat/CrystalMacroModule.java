package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PlayerAttackEntityEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class CrystalMacroModule extends ClientModule {
   private static final int COUNTER_CAP = 1000;
   private static final int WEAPON_SETTLE_TICKS = 2;
   private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
   private final NumberSetting obsidianDelay = this.addSetting("obsidian-delay", new NumberSetting("Obsidian Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private boolean engaged;
   private int savedSlot = -1;
   private int ownSlot = -1;
   private int ticksSinceAction = 1000;
   private ItemStack lastMain;
   private int mainAge = 1000;
   private int pendingBreakId = -1;
   private int pendingBreakAge = 1000;
   private BlockPos pendingCrystalBase;
   private int pendingCrystalAge = 1000;
   private BlockPos pendingObsidian;
   private int pendingObsidianAge = 1000;

   public CrystalMacroModule() {
      super("crystal-macro", "Crystal Macro", "Breaks and places crystals while you hold right-click.", ModuleCategory.COMBAT);
      this.obsidianDelay.visibleWhen(this.autoObsidian::get);
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1) {
         this.breakDelay.set(this.breakDelay.get() - 1.0F);
         this.placeDelay.set(this.placeDelay.get() - 1.0F);
         this.obsidianDelay.set(this.obsidianDelay.get() - 1.0F);
      }
   }

   @Override
   protected void onEnable() {
      this.resetState();
   }

   @Override
   protected void onDisable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.endHold(mc);
      ClientPlayerEntity player = mc.player;
      if (player != null && !hotbarTakenByOther()) {
         this.restoreSlot(player);
      }

      this.resetState();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.ageCounters();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.interactionManager != null && !player.isDead()) {
         ItemStack main = player.getMainHandStack();
         if (main != this.lastMain) {
            this.lastMain = main;
            this.mainAge = 0;
         }

         if (!this.holdActive(mc, player)) {
            this.endHold(mc);
            if (mc.currentScreen == null && !hotbarTakenByOther() && !MeleeAttacks.actedThisTick(player)) {
               this.restoreSlot(player);
            }
         } else if (!AutoInvTotemModule.wantsHotbar() && !CombatMacroLock.heldByOther(this)) {
            if (this.engaged) {
               if (!this.keepEngaged(player)) {
                  this.endHold(mc);
                  return;
               }
            } else if (player.isUsingItem() || !this.ownsSelection(player) && !activationAllows(player)) {
               return;
            }

            CrystalMacroModule.Plan plan = this.plan(mc, player);
            if (this.engaged || plan != null || this.ownsSelection(player)) {
               this.engaged = true;
               CombatMacroLock.tryAcquire(this);
               suppressVanillaUse(mc);
               if (plan != null && MeleeAttacks.handsFree(mc, player) && !MeleeAttacks.actedThisTick(player) && !ServerRotation.pending()) {
                  switch (plan.step()) {
                     case BREAK:
                        this.breakCrystal(mc, player, plan);
                        break;
                     case CRYSTAL:
                        if (this.useOnCrosshair(mc, player, this.placeDelay, plan)) {
                           this.pendingCrystalBase = plan.target();
                           this.pendingCrystalAge = 0;
                        }
                        break;
                     case OBSIDIAN:
                        if (this.useOnCrosshair(mc, player, this.obsidianDelay, plan)) {
                           this.pendingObsidian = plan.target();
                           this.pendingObsidianAge = 0;
                        }
                  }
               }
            }
         } else {
            CombatMacroLock.release(this);
            if (this.engaged) {
               suppressVanillaUse(mc);
            }
         }
      } else {
         this.endHold(mc);
         this.forgetSlots();
      }
   }

   @EventHandler
   public void onAttack(PlayerAttackEntityEvent event) {
      if (event.target instanceof EndCrystalEntity crystal && crystal.getEntityWorld().isClient()) {
         this.markBreak(crystal);
      }
   }

   private boolean holdActive(MinecraftClient mc, ClientPlayerEntity player) {
      return MeleeAttacks.canAct(mc) && mc.isWindowFocused() && !player.isSpectator() && Crystals.isPhysicallyDown(mc, mc.options.useKey);
   }

   private static boolean hotbarTakenByOther() {
      return AutoEatModule.isEating() || AutoInvTotemModule.wantsHotbar();
   }

   private boolean keepEngaged(ClientPlayerEntity player) {
      if (this.ownsSelection(player)) {
         return true;
      }

      this.forgetSlots();
      return activationAllows(player);
   }

   private boolean ownsSelection(ClientPlayerEntity player) {
      return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
   }

   private static boolean activationAllows(ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      ItemStack offhand = player.getOffHandStack();
      return !main.isOf(Items.END_CRYSTAL) && (!offhand.isOf(Items.END_CRYSTAL) || !Crystals.isCombatItem(main))
         ? (main.isIn(ItemTags.SWORDS) || main.isIn(ItemTags.AXES) || main.isOf(Items.MACE))
            && (offhand.isEmpty() || offhand.isOf(Items.TOTEM_OF_UNDYING))
            && Crystals.hotbarSlotOf(player.getInventory(), Items.END_CRYSTAL) != -1
         : true;
   }

   private void endHold(MinecraftClient mc) {
      CombatMacroLock.release(this);
      if (this.engaged) {
         this.engaged = false;
         mc.options.useKey.setPressed(mc.currentScreen == null && Crystals.isPhysicallyDown(mc, mc.options.useKey));
      }
   }

   private void restoreSlot(ClientPlayerEntity player) {
      if (this.ownSlot != -1) {
         PlayerInventory inventory = player.getInventory();
         if (this.switchBack.get() && PlayerInventory.isValidHotbarIndex(this.savedSlot) && this.savedSlot != this.ownSlot && inventory.getSelectedSlot() == this.ownSlot) {
            inventory.setSelectedSlot(this.savedSlot);
         }

         this.forgetSlots();
      }
   }

   private void forgetSlots() {
      this.savedSlot = -1;
      this.ownSlot = -1;
   }

   private void resetState() {
      this.engaged = false;
      this.forgetSlots();
      this.ticksSinceAction = 1000;
      this.lastMain = null;
      this.mainAge = 1000;
      this.pendingBreakId = -1;
      this.pendingBreakAge = 1000;
      this.pendingCrystalBase = null;
      this.pendingCrystalAge = 1000;
      this.pendingObsidian = null;
      this.pendingObsidianAge = 1000;
      CombatMacroLock.release(this);
   }

   private static void suppressVanillaUse(MinecraftClient mc) {
      KeyBinding use = mc.options.useKey;
      use.setPressed(false);

      while (use.wasPressed()) {
      }
   }

   private CrystalMacroModule.Plan plan(MinecraftClient mc, ClientPlayerEntity player) {
      HitResult hit = player.getCrosshairTarget(1.0F, player);
      int window = Crystals.confirmWindow(mc, player);
      if (this.pendingCrystalBase != null && Crystals.hasCrystal(mc.world, this.pendingCrystalBase, null)) {
         this.pendingCrystalBase = null;
      }

      if (hit instanceof EntityHitResult entityHit) {
         if (!(entityHit.getEntity() instanceof EndCrystalEntity crystal && crystal.isAlive())) {
            return null;
         } else {
            if (crystal.getId() == this.pendingBreakId && this.pendingBreakAge < window) {
               return null;
            }

            if (CrystalDamage.endangers(player, CrystalDamage.estimate(crystal.getEntityPos(), player))) {
               return null;
            }

            int weapon = Crystals.weaponRoute(player);
            return weapon == -1 ? null : CrystalMacroModule.Plan.forBreak(crystal, weapon);
         }
      } else if (hit instanceof BlockHitResult blockHit && blockHit.getType() == Type.BLOCK) {
         ClientWorld level = mc.world;
         BlockPos pos = blockHit.getBlockPos();
         if (!level.getWorldBorder().contains(pos)) {
            return null;
         }

         BlockState state = level.getBlockState(pos);
         if (Crystals.isBase(state)) {
            if (pos.equals(this.pendingCrystalBase) && this.pendingCrystalAge < window) {
               return null;
            }

            if (!Crystals.canHost(level, pos, null)) {
               return null;
            }

            int route = Crystals.route(player, Items.END_CRYSTAL, state, this.savedSlot);
            return route == -1 ? null : CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.CRYSTAL, blockHit, pos, route);
         } else {
            if (!this.autoObsidian.get()) {
               return null;
            }

            if (Crystals.route(player, Items.END_CRYSTAL, Blocks.OBSIDIAN.getDefaultState(), this.savedSlot) == -1) {
               return null;
            }

            int route = Crystals.route(player, Items.OBSIDIAN, state, this.savedSlot);
            if (route == -1) {
               return null;
            }

            BlockPos target = Crystals.obsidianTarget(level, player, blockHit, state);
            return target != null && (!target.equals(this.pendingObsidian) || this.pendingObsidianAge >= window)
               ? CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.OBSIDIAN, blockHit, target, route)
               : null;
         }
      } else {
         return null;
      }
   }

   private void breakCrystal(MinecraftClient mc, ClientPlayerEntity player, CrystalMacroModule.Plan plan) {
      if (this.ticksSinceAction > this.breakDelay.getInt()) {
         if (plan.route() != -2) {
            this.select(player, plan.route());
         }

         if (this.mainAge >= 2 || Crystals.enchantsAddDamage(player.getMainHandStack()) || Crystals.dealsDamage(player, ItemStack.EMPTY)) {
            if (Crystals.strike(mc, player, plan.crystal())) {
               MeleeAttacks.claimTick(mc);
               this.markBreak(plan.crystal());
               this.ticksSinceAction = 0;
            }
         }
      }
   }

   private boolean useOnCrosshair(MinecraftClient mc, ClientPlayerEntity player, NumberSetting delay, CrystalMacroModule.Plan plan) {
      if (this.ticksSinceAction <= delay.getInt()) {
         return false;
      }

      if (plan.route() != -2) {
         this.select(player, plan.route());
      }

      if (!Crystals.rightClick(mc, player, plan.blockHit())) {
         return false;
      }

      MeleeAttacks.claimTick(mc);
      this.ticksSinceAction = 0;
      return true;
   }

   private void select(ClientPlayerEntity player, int slot) {
      PlayerInventory inventory = player.getInventory();
      if (this.savedSlot == -1) {
         this.savedSlot = inventory.getSelectedSlot();
      }

      inventory.setSelectedSlot(slot);
      this.ownSlot = slot;
      this.lastMain = player.getMainHandStack();
      this.mainAge = 0;
   }

   private void markBreak(EndCrystalEntity crystal) {
      this.pendingBreakId = crystal.getId();
      this.pendingBreakAge = 0;
   }

   private void ageCounters() {
      this.ticksSinceAction = Math.min(this.ticksSinceAction + 1, 1000);
      this.mainAge = Math.min(this.mainAge + 1, 1000);
      this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
      this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
      this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
   }

   @Environment(EnvType.CLIENT)
   private record Plan(CrystalMacroModule.Step step, EndCrystalEntity crystal, BlockHitResult blockHit, BlockPos target, int route) {
      static CrystalMacroModule.Plan forBreak(EndCrystalEntity crystal, int weapon) {
         return new CrystalMacroModule.Plan(CrystalMacroModule.Step.BREAK, crystal, null, null, weapon);
      }

      static CrystalMacroModule.Plan forPlace(CrystalMacroModule.Step step, BlockHitResult blockHit, BlockPos target, int route) {
         return new CrystalMacroModule.Plan(step, null, blockHit, target, route);
      }
   }

   @Environment(EnvType.CLIENT)
   private enum Step {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }
}
