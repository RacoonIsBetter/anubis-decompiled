package net.anubis.client.module.impl.combat;

import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.Combo;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;


import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class AutoCityModule extends ClientModule {
   private static final float CRYSTAL_POWER = 6.0F;
   private static final double FACE_INSET = 0.05;
   private static final int GIVE_UP_TICKS = 200;
   private static volatile AutoCityModule active;
   private final SlotReturn slots = new SlotReturn(this);
   private LivingEntity target;
   private BlockPos feet;
   private BlockPos block;
   private int ticks;
   private boolean broken;

   public AutoCityModule() {
      super(
         "auto-city",
         "Auto City",
         "Mines a surround block of the best enemy in a hole, then turns off so Crystal Aura can blow through it.",
         ModuleCategory.COMBAT
      );
      this.usesNotifications();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.target = null;
      this.feet = null;
      this.block = null;
      this.ticks = 0;
      this.broken = false;
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!this.broken && this.block != null && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
         mc.interactionManager.cancelBlockBreaking();
      }

      this.slots.drop(mc, true);
      CombatMacroLock.release(this);
      this.target = null;
      this.block = null;
   }

   public static boolean mining() {
      AutoCityModule module = active;
      return module != null && module.block != null && !module.broken;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null || player.isDead() || this.broken || userClicks(mc)) {
         this.requestDisable();
      } else if (this.block == null && !this.pick(mc, player)) {
         this.stop("No enemy in a hole within reach");
      } else {
         ClientWorld level = mc.world;
         BlockState state = level.getBlockState(this.block);
         if (state.isReplaceable()) {
            this.opened();
         } else {
            this.ticks++;
            String problem = this.problem(player, state);
            if (problem != null) {
               this.stop(problem);
            } else if (MeleeAttacks.canAct(mc) && !AutoInvTotemModule.wantsHotbar() && !player.isUsingItem() && !player.isRiding()) {
               if (CombatMacroLock.tryAcquire(this) && this.slots.take(player, toolSlot(player.getInventory(), state))) {
                  BlockHitResult aim = aim(level, player, this.block);
                  if (aim == null) {
                     this.stop("Lost sight of the block");
                  } else {
                     ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), aim.getPos()));
                     BlockHitResult held = held(level, player, this.block);
                     if (held != null && !MeleeAttacks.actedThisTick(player)) {
                        if (mc.interactionManager.updateBlockBreakingProgress(this.block, held.getSide())) {
                           player.swingHand(Hand.MAIN_HAND);
                        }

                        MeleeAttacks.noteAction(player);
                        if (level.getBlockState(this.block).isReplaceable()) {
                           this.opened();
                           this.note(ModuleChat.body().text("Opened ").name(this.target.getName().getString()).text("'s surround"));
                        }
                     }
                  }
               }
            } else {
               if (mc.interactionManager.isBreakingBlock()) {
                  mc.interactionManager.cancelBlockBreaking();
               }

               CombatMacroLock.release(this);
            }
         }
      }
   }

   private void opened() {
      this.broken = true;
      CombatMacroLock.release(this);
   }

   private void stop(String reason) {
      this.note(ModuleChat.body().text(reason));
      this.requestDisable();
   }

   private static boolean userClicks(MinecraftClient mc) {
      return mc.currentScreen == null
         && (
            Crystals.isPhysicallyDown(mc, mc.options.attackKey) || MeleeAttacks.clickPending(mc) || Crystals.isPhysicallyDown(mc, mc.options.useKey)
         );
   }

   private boolean pick(MinecraftClient mc, ClientPlayerEntity player) {
      ClientWorld level = mc.world;
      float best = 0.0F;

      for (LivingEntity enemy : Combo.enemies(mc, player)) {
         if (Combo.inHole(enemy)) {
            BlockPos at = enemy.getBlockPos();

            for (Direction side : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               BlockPos pos = at.offset(side);
               BlockState state = level.getBlockState(pos);
               if (!(state.getHardness(level, pos) < 0.0F)
                  && Crystals.isBase(level.getBlockState(pos.down()))
                  && player.canInteractWithBlockAt(pos, 0.0)
                  && toolSlot(player.getInventory(), state) != -1
                  && aim(level, player, pos) != null) {
                  Vec3d blast = Crystals.explosionAt(pos.down());
                  float own = CrystalDamage.estimate(blast, player, 6.0F, pos);
                  if (!CrystalDamage.endangers(player, own)) {
                     float value = CrystalDamage.estimate(blast, enemy, 6.0F, pos) - 0.5F * own;
                     if (!(value <= best)) {
                        best = value;
                        this.target = enemy;
                        this.feet = at;
                        this.block = pos;
                     }
                  }
               }
            }
         }
      }

      return this.block != null;
   }

   private String problem(ClientPlayerEntity player, BlockState state) {
      BlockPos at = this.target.getBlockPos();
      if (!this.target.isAlive()
         || at.getX() != this.feet.getX()
         || at.getZ() != this.feet.getZ()
         || Math.abs(at.getY() - this.feet.getY()) > 1) {
         return "Target left the hole";
      } else if (!player.canInteractWithBlockAt(this.block, 0.0)) {
         return "Block went out of reach";
      } else if (toolSlot(player.getInventory(), state) == -1) {
         return "No pickaxe that can break it";
      } else if (!ServerRotation.canRotate(player)) {
         return "Can't aim while flying, swimming or riding";
      } else {
         return this.ticks > 200 ? "Gave up, the block took too long" : null;
      }
   }

   private void note(ModuleChat.Body body) {
      if (this.notificationsOn()) {
         ModuleChat.actionBar(this, body);
      }
   }

   private static int toolSlot(PlayerInventory inventory, BlockState state) {
      int best = -1;
      float fastest = 0.0F;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.isSuitableFor(state) && !(stack.getMiningSpeedMultiplier(state) <= fastest)) {
            best = slot;
            fastest = stack.getMiningSpeedMultiplier(state);
         }
      }

      return best;
   }

   private static BlockHitResult aim(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      Vec3d eye = player.getEyePos();
      double reach = player.getBlockInteractionRange();
      BlockHitResult best = null;
      double nearest = reach * reach;

      for (Direction face : Direction.values()) {
         Vec3d point = Vec3d.ofCenter(pos).offset(face, 0.5);
         double distance = eye.squaredDistanceTo(point);
         if (!(distance > nearest)) {
            BlockHitResult hit = level.raycast(new RaycastContext(eye, point.offset(face, -0.05), ShapeType.OUTLINE, FluidHandling.NONE, player));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getSide() == face) {
               best = hit;
               nearest = distance;
            }
         }
      }

      return best;
   }

   private static BlockHitResult held(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      Rotation sent = ServerRotation.sent();
      if (sent == null) {
         return null;
      }

      Vec3d eye = player.getEyePos();
      Vec3d end = eye.add(Vec3d.fromPolar(sent.pitch(), sent.yaw()).multiply(player.getBlockInteractionRange()));
      BlockHitResult hit = level.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
      return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
   }
}
