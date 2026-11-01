package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.anchor.AnchorGuard;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.crystal.LootGuard;
import net.anubis.client.module.impl.combat.crystal.LootLatch;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class SingleAnchorMacroModule extends ClientModule {
   private static final String SINGLE = "Single";
   private static final String DOUBLE = "Double";
   private static final String DETONATE = "Detonate";
   private static final String CANCEL = "Cancel";
   private static final int MAX_STEPS_PER_TICK = 4;
   private static final int GUARD_TRIES = 2;
   private static final int SETTLE_TICKS = 5;
   private static final float BLAST_PROOF = 600.0F;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Single", "Single", "Double"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 5.0F).step(1.0F));
   private final BooleanSetting blastGuard = this.addSetting("safe-anchor", new BooleanSetting("Safe Anchor", true));
   private final ModeSetting withoutGuard = this.addSetting("without-guard", new ModeSetting("If Unguarded", "Detonate", "Detonate", "Cancel"));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", false));
   private final BooleanSetting protectLoot = this.addSetting("protect-loot", new BooleanSetting("Protect Loot", true));
   private SingleAnchorMacroModule.Step step = SingleAnchorMacroModule.Step.IDLE;
   private int tick;
   private int readyAt;
   private int deadline;
   private int previousSlot = -1;
   private int ownSlot = -1;
   private boolean refilled;
   private BlockPos anchorPos;
   private Vec3d sightline;
   private BlockHitResult replaceHit;
   private ClientWorld activeLevel;
   private AnchorGuard.Spot guard;
   private Block guardBlock;
   private BlockState guardReported;
   private int guardTries;

   public SingleAnchorMacroModule() {
      super("anchor-macro", "Anchor Macro", "Places, charges and blows up respawn anchors on right-click.", ModuleCategory.COMBAT);
      this.withoutGuard.visibleWhen(this.blastGuard::get);
   }

   @Override
   protected void onEnable() {
      this.stop();
   }

   @Override
   protected void onDisable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.step != SingleAnchorMacroModule.Step.IDLE && mc.player != null && !(mc.currentScreen instanceof HandledScreen)) {
         PlayerInventory inventory = mc.player.getInventory();
         if (this.switchBackDue(inventory)) {
            inventory.setSelectedSlot(this.previousSlot);
         }
      }

      this.stop();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.tick++;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.interactionManager != null && !player.isDead()) {
         LootLatch.update(mc, player);
         LootGuard.update(mc.world, player);
         PlayerInventory inventory = player.getInventory();
         if (mc.currentScreen != null) {
            if (this.step != SingleAnchorMacroModule.Step.IDLE && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK) {
               this.wrapUp(inventory);
            }
         } else if (!AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
            if (this.step != SingleAnchorMacroModule.Step.IDLE && (this.hotbarMovedByPlayer(inventory) || mc.world != this.activeLevel)) {
               this.stop();
            }

            if ((LootLatch.latched() || LootLatch.killedNow())
               && this.step != SingleAnchorMacroModule.Step.IDLE
               && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK) {
               this.wrapUp(inventory);
            }

            if (LootLatch.latched() && this.step == SingleAnchorMacroModule.Step.IDLE) {
               if (!player.isUsingItem() && anchorInPlay(mc, player)) {
                  suppressUse(mc);
               }
            } else if (this.step != SingleAnchorMacroModule.Step.IDLE || this.tryStart(mc, player)) {
               swallowClicks(mc);
               if (this.step == SingleAnchorMacroModule.Step.SWITCH_BACK) {
                  if (!MeleeAttacks.actedThisTick(player)) {
                     if (this.switchBackDue(inventory)) {
                        inventory.setSelectedSlot(this.previousSlot);
                     }

                     this.stop();
                  }
               } else {
                  for (int i = 0;
                     i < 4
                        && this.tick >= this.readyAt
                        && this.step != SingleAnchorMacroModule.Step.IDLE
                        && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK;
                     i++
                  ) {
                     if (this.tick >= this.deadline) {
                        this.expire(inventory);
                     } else {
                        if (MeleeAttacks.actedThisTick(player) || !MeleeAttacks.handsFree(mc, player)) {
                           break;
                        }

                        this.runStep(mc, player);
                     }
                  }
               }
            }
         } else {
            this.stop();
         }
      } else {
         this.stop();
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (this.step == SingleAnchorMacroModule.Step.AWAIT_GUARD && this.guard != null) {
         BlockPos spot = this.guard.pos();
         if (event.packet instanceof BlockUpdateS2CPacket update) {
            if (update.getPos().equals(spot)) {
               this.guardReported = update.getState();
            }
         } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket section) {
            section.visitUpdates((pos, state) -> {
               if (pos.equals(spot)) {
                  this.guardReported = state;
               }
            });
         }
      }
   }

   private boolean tryStart(MinecraftClient mc, ClientPlayerEntity player) {
      if (MeleeAttacks.rightClickComing(mc) && !CombatMacroLock.heldByOther(this)) {
         if (!player.isSpectator()
            && canClick(player)
            && !MeleeAttacks.actedThisTick(player)
            && MeleeAttacks.handsFree(mc, player)
            && !player.getOffHandStack().isOf(Items.GLOWSTONE)) {
            if (player.raycast(player.getBlockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
               ClientWorld level = mc.world;
               BlockState clicked = level.getBlockState(hit.getBlockPos());
               boolean onAnchor = clicked.isOf(Blocks.RESPAWN_ANCHOR);
               BlockPos target;
               if (onAnchor) {
                  target = hit.getBlockPos().toImmutable();
               } else {
                  if (!player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR) || ServerRotation.pending()) {
                     return false;
                  }

                  target = Crystals.placedObsidian(level, player, hit, clicked);
               }

               if (target == null || level.getRegistryKey() == World.NETHER) {
                  return false;
               }

               if (this.protectLoot.get() && LootGuard.near(Vec3d.ofCenter(target))) {
                  return false;
               }

               PlayerInventory inventory = player.getInventory();
               boolean charged = chargeOf(clicked) > 0;
               if (!charged && findHotbar(inventory, Items.GLOWSTONE) == -1) {
                  return false;
               }

               if (AnchorGuard.detonatorSlot(inventory) == -1) {
                  return false;
               }

               if (this.blastGuard.get() && this.withoutGuard.is("Cancel") && guardSlot(inventory, this.glowstoneNeeded(charged)) == -1) {
                  return false;
               }

               if (!CombatMacroLock.tryAcquire(this)) {
                  return false;
               }

               this.previousSlot = inventory.getSelectedSlot();
               this.activeLevel = level;
               this.anchorPos = target;
               this.sightline = hit.getPos();
               SingleAnchorMacroModule.Step first = this.blastGuard.get() ? SingleAnchorMacroModule.Step.GUARD : SingleAnchorMacroModule.Step.CHARGE;
               if (onAnchor) {
                  this.scheduleNow(first);
               } else {
                  AnchorGuard.click(mc, player, hit);
                  this.schedule(first);
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void swallowClicks(MinecraftClient mc) {
      MeleeAttacks.takeAttackClicks(mc);
      suppressUse(mc);
   }

   private static boolean anchorInPlay(MinecraftClient mc, ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      return !main.isOf(Items.RESPAWN_ANCHOR) && !main.isOf(Items.GLOWSTONE)
         ? mc.crosshairTarget instanceof BlockHitResult hit
            && hit.getType() == Type.BLOCK
            && mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)
         : true;
   }

   private static void suppressUse(MinecraftClient mc) {
      KeyBinding use = mc.options.useKey;
      use.setPressed(false);

      while (use.wasPressed()) {
      }
   }

   private void runStep(MinecraftClient mc, ClientPlayerEntity player) {
      switch (this.step) {
         case GUARD:
            this.placeGuard(mc, player);
            break;
         case AWAIT_GUARD:
            this.awaitGuard(mc, player);
            break;
         case CHARGE:
            this.charge(mc, player);
            break;
         case DETONATE:
            this.detonate(mc, player);
            break;
         case REPLACE:
            this.replace(mc, player);
      }
   }

   private void expire(PlayerInventory inventory) {
      if (this.step != SingleAnchorMacroModule.Step.GUARD && this.step != SingleAnchorMacroModule.Step.AWAIT_GUARD) {
         this.wrapUp(inventory);
      } else {
         this.skipGuard(inventory);
      }
   }

   private void placeGuard(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      BlockState anchor = mc.world.getBlockState(this.anchorPos);
      if (!anchor.isOf(Blocks.RESPAWN_ANCHOR)) {
         this.wrapUp(inventory);
      } else {
         AnchorGuard.Spot spot = AnchorGuard.find(mc.world, player, this.anchorPos, this.sightline);
         if (spot != null && spot.standing()) {
            this.guarded();
         } else {
            int slot = guardSlot(inventory, this.glowstoneNeeded(chargeOf(anchor) > 0));
            Rotation sent = ServerRotation.sent();
            BlockHitResult hit = spot != null && sent != null ? AnchorGuard.hitAlong(mc.world, player, sent, spot) : null;
            if (spot != null && slot != -1 && (hit != null || ServerRotation.canRotate(player))) {
               if (hit == null) {
                  ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), spot.aim()));
                  MeleeAttacks.claimTick(mc);
                  this.readyAt = this.tick + 1;
               } else {
                  this.guard = spot;
                  this.guardBlock = Block.getBlockFromItem(inventory.getStack(slot).getItem());
                  this.guardReported = null;
                  this.select(inventory, slot);
                  AnchorGuard.click(mc, player, hit);
                  ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), Vec3d.ofCenter(this.anchorPos)));
                  this.guardTries++;
                  if (this.withoutGuard.is("Cancel")) {
                     this.schedule(SingleAnchorMacroModule.Step.AWAIT_GUARD);
                     this.deadline = this.readyAt + Crystals.confirmWindow(mc, player);
                  } else {
                     this.schedule(SingleAnchorMacroModule.Step.CHARGE);
                  }
               }
            } else {
               this.skipGuard(inventory);
            }
         }
      }
   }

   private void awaitGuard(MinecraftClient mc, ClientPlayerEntity player) {
      boolean shown = mc.world.getBlockState(this.guard.pos()).isOf(this.guardBlock);
      if (shown && this.guardReported != null && this.guardReported.isOf(this.guardBlock)) {
         this.guarded();
      } else if (shown) {
         this.readyAt = this.tick + 1;
      } else if (this.guardTries < 2) {
         this.scheduleNow(SingleAnchorMacroModule.Step.GUARD);
      } else {
         this.skipGuard(player.getInventory());
      }
   }

   private void guarded() {
      this.guard = null;
      this.scheduleNow(SingleAnchorMacroModule.Step.CHARGE);
   }

   private void skipGuard(PlayerInventory inventory) {
      this.guard = null;
      if (this.withoutGuard.is("Cancel")) {
         this.wrapUp(inventory);
      } else {
         this.scheduleNow(SingleAnchorMacroModule.Step.CHARGE);
      }
   }

   private void charge(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      BlockState state = mc.world.getBlockState(this.anchorPos);
      if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
         this.wrapUp(inventory);
      } else if (chargeOf(state) > 0 && !this.refilled) {
         this.scheduleNow(SingleAnchorMacroModule.Step.DETONATE);
      } else {
         int glowstone = findHotbar(inventory, Items.GLOWSTONE);
         if (glowstone != -1 && canClick(player)) {
            BlockHitResult hit = AnchorGuard.anchorHit(mc.world, player, this.anchorPos);
            if (hit == null) {
               this.readyAt = this.tick + 1;
            } else {
               this.select(inventory, glowstone);
               AnchorGuard.click(mc, player, hit);
               this.schedule(SingleAnchorMacroModule.Step.DETONATE);
            }
         } else {
            this.wrapUp(inventory);
         }
      }
   }

   private void detonate(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      if (chargeOf(mc.world.getBlockState(this.anchorPos)) != 0 && canClick(player) && !player.getOffHandStack().isOf(Items.GLOWSTONE)) {
         BlockHitResult hit = AnchorGuard.anchorHit(mc.world, player, this.anchorPos);
         if (hit == null) {
            this.readyAt = this.tick + 1;
         } else {
            int refillSlot = this.mode.is("Double")
                  && !this.refilled
                  && hit.getSide() == Direction.UP
                  && this.anchorPos.getY() >= player.getBlockY()
                  && findHotbar(inventory, Items.GLOWSTONE) != -1
               ? findHotbar(inventory, Items.RESPAWN_ANCHOR)
               : -1;
            int slot = refillSlot != -1 ? refillSlot : AnchorGuard.detonatorSlot(inventory);
            if (slot == -1) {
               this.wrapUp(inventory);
            } else if (this.lethal(player, inventory.getStack(slot))) {
               this.wrapUp(inventory);
            } else {
               this.select(inventory, slot);
               AnchorGuard.click(mc, player, hit);
               if (slot == refillSlot) {
                  this.refilled = true;
                  this.replaceHit = hit;
                  this.schedule(SingleAnchorMacroModule.Step.REPLACE);
               } else {
                  this.wrapUp(inventory);
               }
            }
         }
      } else {
         this.wrapUp(inventory);
      }
   }

   private void replace(MinecraftClient mc, ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      Rotation sent = ServerRotation.sent();
      Vec3d eye = player.getEyePos();
      boolean stillAimed = sent != null
         && new Box(this.anchorPos).raycast(eye, eye.add(Vec3d.fromPolar(sent.pitch(), sent.yaw()).multiply(player.getBlockInteractionRange()))).isPresent();
      if (this.replaceHit != null && stillAimed && player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR) && canClick(player)) {
         AnchorGuard.click(mc, player, this.replaceHit);
         this.replaceHit = null;
         this.schedule(SingleAnchorMacroModule.Step.CHARGE);
      } else {
         this.wrapUp(inventory);
      }
   }

   private boolean lethal(ClientPlayerEntity player, ItemStack held) {
      if (!held.isOf(Items.TOTEM_OF_UNDYING) && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         float damage = CrystalDamage.estimate(Vec3d.ofCenter(this.anchorPos), player, 5.0F, this.anchorPos);
         return CrystalDamage.endangers(player, damage);
      } else {
         return false;
      }
   }

   private void wrapUp(PlayerInventory inventory) {
      if (this.switchBackDue(inventory)) {
         this.step = SingleAnchorMacroModule.Step.SWITCH_BACK;
      } else {
         this.stop();
      }
   }

   private boolean switchBackDue(PlayerInventory inventory) {
      return this.switchBack.get()
         && PlayerInventory.isValidHotbarIndex(this.previousSlot)
         && this.ownSlot != -1
         && this.ownSlot != this.previousSlot
         && inventory.getSelectedSlot() == this.ownSlot;
   }

   private boolean hotbarMovedByPlayer(PlayerInventory inventory) {
      return inventory.getSelectedSlot() != (this.ownSlot != -1 ? this.ownSlot : this.previousSlot);
   }

   private void stop() {
      this.step = SingleAnchorMacroModule.Step.IDLE;
      this.anchorPos = null;
      this.sightline = null;
      this.replaceHit = null;
      this.activeLevel = null;
      this.previousSlot = -1;
      this.ownSlot = -1;
      this.refilled = false;
      this.guard = null;
      this.guardBlock = null;
      this.guardReported = null;
      this.guardTries = 0;
      CombatMacroLock.release(this);
   }

   private void schedule(SingleAnchorMacroModule.Step next) {
      this.step = next;
      this.readyAt = this.tick + 1 + this.delay.getInt();
      this.deadline = this.readyAt + 5;
   }

   private void scheduleNow(SingleAnchorMacroModule.Step next) {
      this.step = next;
      this.readyAt = this.tick;
      this.deadline = this.tick + 5;
   }

   private void select(PlayerInventory inventory, int slot) {
      if (inventory.getSelectedSlot() != slot) {
         inventory.setSelectedSlot(slot);
         this.ownSlot = slot;
      }
   }

   private static boolean canClick(ClientPlayerEntity player) {
      return !player.shouldCancelInteraction();
   }

   private static int chargeOf(BlockState state) {
      return state.isOf(Blocks.RESPAWN_ANCHOR) ? state.get(RespawnAnchorBlock.CHARGES) : 0;
   }

   private int glowstoneNeeded(boolean charged) {
      int needed = charged ? 0 : 1;
      return this.mode.is("Double") ? needed + 1 : needed;
   }

   private static int guardSlot(PlayerInventory inventory, int glowstoneNeeded) {
      int weaker = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = inventory.getStack(i);
         if (!stack.isOf(Items.GLOWSTONE) && AnchorGuard.isGuardBlock(stack)) {
            if (Block.getBlockFromItem(stack.getItem()).getBlastResistance() >= 600.0F) {
               return i;
            }

            if (weaker == -1) {
               weaker = i;
            }
         }
      }

      if (weaker != -1) {
         return weaker;
      }

      int glowstone = findHotbar(inventory, Items.GLOWSTONE);
      return glowstone != -1 && countHotbar(inventory, Items.GLOWSTONE) > glowstoneNeeded ? glowstone : -1;
   }

   private static int findHotbar(PlayerInventory inventory, Item item) {
      for (int i = 0; i < 9; i++) {
         if (inventory.getStack(i).isOf(item)) {
            return i;
         }
      }

      return -1;
   }

   private static int countHotbar(PlayerInventory inventory, Item item) {
      int count = 0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = inventory.getStack(i);
         if (stack.isOf(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @Environment(EnvType.CLIENT)
   private enum Step {
      IDLE,
      GUARD,
      AWAIT_GUARD,
      CHARGE,
      DETONATE,
      REPLACE,
      SWITCH_BACK;
   }
}
