package net.anubis.client.module.impl.combat.melee;

import com.example.client.mixin.AccessorCombatMinecraft;
import com.example.client.mixin.AccessorLocalPlayer;
import com.example.client.mixin.AccessorTriggerbotKeyMapping;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.anubis.client.config.FriendStore;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.ShieldBreakerModule;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.util.Hand;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class MeleeAttacks {
   public static final float CRIT_CHARGE = 0.9F;
   private static final double LINE_EPSILON_SQ = 0.001;
   private static final int SHIELD_BROKEN_TICKS = 20;
   private static int actorId;
   private static int actionTick = -1;
   private static int shieldBrokenId;
   private static int shieldBrokenUntil;

   private MeleeAttacks() {
   }

   public static void noteAction(PlayerEntity player) {
      actorId = player.getId();
      actionTick = player.age;
   }

   public static boolean actedThisTick(PlayerEntity player) {
      return actionTick == player.age && actorId == player.getId();
   }

   public static boolean handsFree(MinecraftClient mc, ClientPlayerEntity player) {
      return !player.isUsingItem()
         && !player.isRiding()
         && !mc.interactionManager.isBreakingBlock()
         && !rowing(player)
         && !clickPending(mc.options.swapHandsKey)
         && !clickPending(mc.options.dropKey);
   }

   private static boolean rowing(ClientPlayerEntity player) {
      PlayerInput sent = ((AccessorLocalPlayer)player).anubis$lastSentInput();
      return player.getVehicle() instanceof AbstractBoatEntity && (sent.forward() != sent.backward() || sent.left() != sent.right());
   }

   public static boolean clickPending(MinecraftClient mc) {
      return clickPending(mc.options.attackKey);
   }

   public static boolean clickPending(KeyBinding key) {
      return ((AccessorTriggerbotKeyMapping)key).anubis$triggerbotClickCount() > 0;
   }

   public static boolean rightClickComing(MinecraftClient mc) {
      return mc.options.useKey.isPressed() || clickPending(mc.options.useKey);
   }

   public static void takeAttackClicks(MinecraftClient mc) {
      KeyBinding attack = mc.options.attackKey;

      while (attack.wasPressed()) {
      }
   }

   public static void claimTick(MinecraftClient mc) {
      takeAttackClicks(mc);
      KeyBinding use = mc.options.useKey;

      while (use.wasPressed()) {
      }

      mc.attackCooldown = Math.max(mc.attackCooldown, 1);
      AccessorCombatMinecraft access = (AccessorCombatMinecraft)mc;
      access.anubis$setRightClickDelay(Math.max(access.anubis$rightClickDelay(), 2));
   }

   public static boolean canAct(MinecraftClient mc) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
         return false;
      }

      if (AutoEatModule.isEating()) {
         return false;
      }

      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam == null || !freecam.isEnabled();
   }

   public static EntityHitResult crosshairHit(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         if (player.getCrosshairTarget(1.0F, player) instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
            return lineIsClear(player, player.getEyePos(), hit.getPos()) ? hit : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean lineIsClear(ClientPlayerEntity player, Vec3d from, Vec3d to) {
      BlockHitResult block = player.getEntityWorld().raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, player));
      return block.getType() == Type.MISS || block.getPos().squaredDistanceTo(from) >= from.squaredDistanceTo(to) - 0.001;
   }

   public static boolean attack(MinecraftClient mc, EntityHitResult hit) {
      return attack(mc, hit, 0.0);
   }

   public static boolean attack(MinecraftClient mc, EntityHitResult hit, double extraRange) {
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null) {
         return false;
      }

      if (mc.attackCooldown > 0 || actedThisTick(player)) {
         return false;
      }

      if (handsFree(mc, player) && !player.isSpectator()) {
         if (player.isSprinting() != ((AccessorLocalPlayer)player).anubis$wasSprinting()) {
            return false;
         }

         ItemStack held = player.getMainHandStack();
         if (held.contains(DataComponentTypes.PIERCING_WEAPON)) {
            return false;
         }

         if (held.isItemEnabled(mc.world.getEnabledFeatures()) && !player.isBelowMinimumAttackCharge(held, 0)) {
            Entity target = hit.getEntity();
            if (target.isRemoved()) {
               return false;
            }

            AttackRangeComponent range = (AttackRangeComponent)held.get(DataComponentTypes.ATTACK_RANGE);
            boolean inReach = range != null ? range.isWithinRange(player, hit.getPos()) : player.canInteractWithEntityIn(target.getBoundingBox(), extraRange);
            if (!inReach) {
               return false;
            }

            mc.interactionManager.attackEntity(player, target);
            if (!actedThisTick(player)) {
               return false;
            }

            player.swingHand(Hand.MAIN_HAND);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean stab(MinecraftClient mc, EntityHitResult hit) {
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null) {
         return false;
      }

      if (mc.attackCooldown > 0 || actedThisTick(player)) {
         return false;
      }

      if (handsFree(mc, player) && !player.isSpectator()) {
         if (player.isSprinting() != ((AccessorLocalPlayer)player).anubis$wasSprinting()) {
            return false;
         }

         ItemStack held = player.getMainHandStack();
         PiercingWeaponComponent piercing = (PiercingWeaponComponent)held.get(DataComponentTypes.PIERCING_WEAPON);
         if (piercing == null) {
            return false;
         }

         if (held.isItemEnabled(mc.world.getEnabledFeatures()) && !player.isBelowMinimumAttackCharge(held, 0)) {
            Entity target = hit.getEntity();
            if (target.isRemoved()) {
               return false;
            }

            AttackRangeComponent range = (AttackRangeComponent)held.get(DataComponentTypes.ATTACK_RANGE);
            boolean inReach = range != null ? range.isWithinRange(player, hit.getPos()) : player.canInteractWithEntityIn(target.getBoundingBox(), 0.0);
            if (!inReach) {
               return false;
            }

            mc.interactionManager.attackWithPiercingWeapon(piercing);
            if (!actedThisTick(player)) {
               return false;
            }

            player.swingHand(Hand.MAIN_HAND);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Entity stabTarget(ClientPlayerEntity player, Predicate<Entity> wanted) {
      AttackRangeComponent range = player.getAttackRange();
      Rotation sent = ServerRotation.sent();
      Vec3d look = sent != null ? Vec3d.fromPolar(sent.pitch(), sent.yaw()) : player.getRotationVec(1.0F);
      Vec3d eye = player.getEyePos();
      Vec3d start = eye.add(look.multiply(range.getEffectiveMinRange(player)));
      Vec3d end = eye.add(look.multiply(range.getEffectiveMaxRange(player) + Math.max(0.0, player.getMovement().dotProduct(look))));
      BlockHitResult wall = player.getEntityWorld().raycast(new RaycastContext(eye, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
      if (wall.getType() != Type.MISS) {
         end = wall.getPos();
      }

      float margin = range.hitboxMargin();

      for (Entity entity : player.getEntityWorld().getOtherEntities(player, new Box(eye, end).expand(margin + 1.0), wanted)) {
         Box box = entity.getBoundingBox().expand(margin);
         if (box.contains(start) || box.raycast(start, end).isPresent()) {
            return entity;
         }
      }

      return null;
   }

   private static boolean isHittable(ClientPlayerEntity self, Entity entity) {
      if (!(entity instanceof LivingEntity living && entity != self && !(entity instanceof ArmorStandEntity))) {
         return false;
      } else {
         return living.isAlive() && entity.isAttackable() && self.getVehicle() != entity
            ? !(entity instanceof PlayerEntity player && (player.isSpectator() || player.isCreative()))
            : false;
      }
   }

   public static boolean isEnemy(ClientPlayerEntity self, Entity entity) {
      return isHittable(self, entity) && !isFriend(entity)
         ? !(entity instanceof PlayerEntity player && (!self.shouldDamagePlayer(player) || self.networkHandler.getPlayerListEntry(player.getUuid()) == null))
         : false;
   }

   public static boolean isHostile(LivingEntity entity) {
      return entity instanceof Monster && (!(entity instanceof Angerable) || entity instanceof MobEntity mob && mob.isAttacking());
   }

   public static float charge(PlayerEntity player) {
      return player.getAttackCooldownProgress(0.5F);
   }

   public static int ticksToFull(PlayerEntity player) {
      return Math.max(0, MathHelper.ceil((1.0F - charge(player)) * player.getAttackCooldownProgressPerTick()));
   }

   public static boolean airborne(PlayerEntity player) {
      return !player.isOnGround()
         && !player.isClimbing()
         && !player.isTouchingWater()
         && !player.hasBlindnessEffect()
         && !player.hasVehicle()
         && !player.getAbilities().flying
         && !player.isGliding();
   }

   public static boolean wouldCrit(PlayerEntity player, Entity target) {
      return charge(player) > 0.9F && player.fallDistance > 0.0 && critStance(player, target);
   }

   public static boolean critStance(PlayerEntity player, Entity target) {
      return !player.isOnGround()
         && !player.isClimbing()
         && !player.isTouchingWater()
         && !player.hasBlindnessEffect()
         && !player.hasVehicle()
         && !player.isSprinting()
         && target instanceof LivingEntity;
   }

   public static boolean shieldBlocks(LivingEntity target, PlayerEntity attacker) {
      return shieldBlocks(target, attacker, target.getBlockingItem());
   }

   public static boolean shieldBlocks(LivingEntity target, PlayerEntity attacker, ItemStack shield) {
      if (shield == null) {
         return false;
      }

      BlocksAttacksComponent blocks = (BlocksAttacksComponent)shield.get(DataComponentTypes.BLOCKS_ATTACKS);
      if (blocks == null) {
         return false;
      }

      DamageSource source = attacker.getEntityWorld().getDamageSources().playerAttack(attacker);
      if (blocks.bypassedBy().map(tag -> source.isIn(tag)).orElse(false)) {
         return false;
      }

      Vec3d facing = target.getRotationVector(0.0F, target.getHeadYaw());
      Vec3d toAttacker = attacker.getEntityPos().subtract(target.getEntityPos());
      Vec3d flat = new Vec3d(toAttacker.x, 0.0, toAttacker.z).normalize();
      double angle = Math.acos(MathHelper.clamp(flat.dotProduct(facing), -1.0, 1.0));
      return blocks.getDamageReductionAmount(source, 1.0F, angle) > 0.0F;
   }

   public static boolean wastedOnShield(ClientPlayerEntity self, LivingEntity target) {
      if (shieldBlocks(target, self) && !shieldBroken(target) && !disablesShields(self.getMainHandStack())) {
         ShieldBreakerModule breaker = ModuleRegistry.get(ShieldBreakerModule.class);
         if (breaker != null && breaker.isEnabled()) {
            PlayerInventory inventory = self.getInventory();

            for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
               if (disablesShields(inventory.getStack(slot))) {
                  return false;
               }
            }

            return true;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static void noteShieldBroken(LivingEntity target) {
      shieldBrokenId = target.getId();
      shieldBrokenUntil = target.age + 20;
   }

   public static boolean shieldBroken(LivingEntity target) {
      return target.getId() == shieldBrokenId && target.age < shieldBrokenUntil;
   }

   public static boolean disablesShields(ItemStack stack) {
      WeaponComponent weapon = (WeaponComponent)stack.get(DataComponentTypes.WEAPON);
      return weapon != null && weapon.disableBlockingForSeconds() > 0.0F;
   }

   public static boolean isMeleeWeapon(ItemStack stack) {
      return stack.isIn(ItemTags.SWORDS)
         || stack.isIn(ItemTags.AXES)
         || stack.isIn(ItemTags.SPEARS)
         || stack.isOf(Items.MACE)
         || stack.isOf(Items.TRIDENT);
   }

   public static double attackDamage(ItemStack stack) {
      double[] total = new double[]{0.0};
      stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
         if (EntityAttributes.ATTACK_DAMAGE.equals(attribute) && modifier.operation() == Operation.ADD_VALUE) {
            total[0] += modifier.value();
         }
      });
      return total[0];
   }

   public static boolean isFriend(Entity entity) {
      return entity instanceof PlayerEntity player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
   }

   public static int spread(int maxTicks) {
      return maxTicks > 0 ? ThreadLocalRandom.current().nextInt(maxTicks + 1) : 0;
   }
}
