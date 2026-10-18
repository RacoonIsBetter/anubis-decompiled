package net.anubis.client.module.impl.combat.crystal;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class CrystalDamage {
   private static final double REACH = 12.0;
   private static final float SELF_SCALE = 1.15F;
   private static final float SELF_MARGIN = 2.0F;
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private CrystalDamage() {
   }

   public static float estimate(Vec3d center, LivingEntity entity) {
      return new CrystalDamage.Victim(entity, Vec3d.ZERO).damage(center);
   }

   public static float estimate(Vec3d center, LivingEntity entity, float power, BlockPos ignored) {
      return new CrystalDamage.Victim(entity, Vec3d.ZERO).damage(center, power, ignored);
   }

   public static float health(LivingEntity entity) {
      return entity.getHealth() + entity.getAbsorptionAmount();
   }

   public static boolean endangers(PlayerEntity self, float damage) {
      return !self.isCreative() && damage * 1.15F + 2.0F >= health(self);
   }

   private static float falloff(double exposure) {
      return falloff(exposure, 12.0);
   }

   private static float falloff(double exposure, double diameter) {
      return (float)((exposure * exposure + exposure) / 2.0 * 7.0 * diameter + 1.0);
   }

   private static float protection(LivingEntity entity) {
      Registry<Enchantment> enchantments = entity.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
      int total = 0;

      for (EquipmentSlot slot : ARMOR) {
         ItemStack stack = entity.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            total += level(enchantments, Enchantments.PROTECTION, stack) + 2 * level(enchantments, Enchantments.BLAST_PROTECTION, stack);
         }
      }

      return total;
   }

   private static int level(Registry<Enchantment> enchantments, RegistryKey<Enchantment> key, ItemStack stack) {
      return enchantments.getOptional(key).map(holder -> EnchantmentHelper.getLevel(holder, stack)).orElse(0);
   }

   @Environment(EnvType.CLIENT)
   private record Blast(Vec3d center, BlockPos ignored) {
   }

   @Environment(EnvType.CLIENT)
   public static final class Victim {
      private final LivingEntity entity;
      private final Vec3d position;
      private final Box box;
      private final DamageSource source;
      private final float armor;
      private final float toughness;
      private final float protection;
      private final float resistance;
      private final BlocksAttacksComponent shield;
      private final Map<Vec3d, Float> exposures = new HashMap<>();
      private final Map<CrystalDamage.Blast, Float> blastExposures = new HashMap<>();

      public Victim(LivingEntity entity, Vec3d offset) {
         this.entity = entity;
         this.position = entity.getEntityPos().add(offset);
         this.box = entity.getBoundingBox().offset(offset);
         this.source = entity.getDamageSources().explosion(null, null);
         this.armor = entity.getArmor();
         this.toughness = (float)entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         this.protection = CrystalDamage.protection(entity);
         StatusEffectInstance effect = entity.getStatusEffect(StatusEffects.RESISTANCE);
         this.resistance = effect == null ? 1.0F : Math.max(25 - (effect.getAmplifier() + 1) * 5, 0) / 25.0F;
         ItemStack blocking = entity.getBlockingItem();
         this.shield = blocking == null ? null : (BlocksAttacksComponent)blocking.get(DataComponentTypes.BLOCKS_ATTACKS);
      }

      public LivingEntity entity() {
         return this.entity;
      }

      public float damage(Vec3d center) {
         return this.damage(center, false);
      }

      public float damage(Vec3d center, boolean baseToCome) {
         return this.reduce(this.raw(center, baseToCome));
      }

      public float raw(Vec3d center, boolean baseToCome) {
         double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / 12.0;
         if (distance > 1.0) {
            return 0.0F;
         }

         float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * this.exposure(center, baseToCome)));
         return damage - this.blocked(center, damage);
      }

      public float reduce(float raw) {
         float damage = DamageUtil.getDamageLeft(this.entity, raw, this.source, this.armor, this.toughness);
         return DamageUtil.getInflictedDamage(damage * this.resistance, this.protection);
      }

      public float damage(Vec3d center, float power, BlockPos ignored) {
         return this.reduce(this.raw(center, power, ignored));
      }

      public float raw(Vec3d center, float power, BlockPos ignored) {
         double diameter = power * 2.0;
         double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / diameter;
         if (distance > 1.0) {
            return 0.0F;
         }

         float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * this.blastExposure(center, ignored), diameter));
         return damage - this.blocked(center, damage);
      }

      private float blocked(Vec3d center, float damage) {
         if (this.shield != null && !(damage <= 0.0F)) {
            Vec3d facing = this.entity.getRotationVector(0.0F, this.entity.getHeadYaw());
            Vec3d toward = center.subtract(this.position);
            Vec3d flat = new Vec3d(toward.x, 0.0, toward.z).normalize();
            double angle = Math.acos(MathHelper.clamp(flat.dotProduct(facing), -1.0, 1.0));
            return this.shield.getDamageReductionAmount(this.source, damage, angle);
         } else {
            return 0.0F;
         }
      }

      public float upperBound(Vec3d center) {
         double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / 12.0;
         return distance > 1.0 ? 0.0F : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance)));
      }

      public float upperBound(Vec3d center, float power) {
         double diameter = power * 2.0;
         double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / diameter;
         return distance > 1.0 ? 0.0F : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance, diameter)));
      }

      private float exposure(Vec3d center, boolean baseToCome) {
         Float known = this.exposures.get(center);
         if (known != null) {
            return known;
         }

         Box base = baseToCome ? new Box(BlockPos.ofFloored(center.x, center.y - 0.5, center.z)) : null;
         float seen = !this.openAir(center, null) || base != null && this.hull(center).intersects(base) ? this.seenPercent(center, base, null) : 1.0F;
         this.exposures.put(center, seen);
         return seen;
      }

      private float blastExposure(Vec3d center, BlockPos ignored) {
         CrystalDamage.Blast key = new CrystalDamage.Blast(center, ignored);
         Float known = this.blastExposures.get(key);
         if (known != null) {
            return known;
         }

         float seen = this.openAir(center, ignored) ? 1.0F : this.seenPercent(center, null, ignored);
         this.blastExposures.put(key, seen);
         return seen;
      }

      private Box hull(Vec3d center) {
         return this.box.union(new Box(center, center));
      }

      private boolean openAir(Vec3d center, BlockPos ignored) {
         World level = this.entity.getEntityWorld();
         ShapeContext context = ShapeContext.of(this.entity);
         int minX = MathHelper.floor(Math.min(this.box.minX, center.x));
         int minY = MathHelper.floor(Math.min(this.box.minY, center.y));
         int minZ = MathHelper.floor(Math.min(this.box.minZ, center.z));
         int maxX = MathHelper.floor(Math.max(this.box.maxX, center.x));
         int maxY = MathHelper.floor(Math.max(this.box.maxY, center.y));
         int maxZ = MathHelper.floor(Math.max(this.box.maxZ, center.z));
         Mutable pos = new Mutable();

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  pos.set(x, y, z);
                  if (!pos.equals(ignored) && !level.getBlockState(pos).getCollisionShape(level, pos, context).isEmpty()) {
                     return false;
                  }
               }
            }
         }

         return true;
      }

      private float seenPercent(Vec3d center, Box base, BlockPos ignored) {
         double stepX = 1.0 / ((this.box.maxX - this.box.minX) * 2.0 + 1.0);
         double stepY = 1.0 / ((this.box.maxY - this.box.minY) * 2.0 + 1.0);
         double stepZ = 1.0 / ((this.box.maxZ - this.box.minZ) * 2.0 + 1.0);
         double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
         double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
         if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
            World level = this.entity.getEntityWorld();
            int seen = 0;
            int total = 0;

            for (double fx = 0.0; fx <= 1.0; fx += stepX) {
               for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                  for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                     Vec3d from = new Vec3d(
                        MathHelper.lerp(fx, this.box.minX, this.box.maxX) + offsetX,
                        MathHelper.lerp(fy, this.box.minY, this.box.maxY),
                        MathHelper.lerp(fz, this.box.minZ, this.box.maxZ) + offsetZ
                     );
                     BlockHitResult hit = level.raycast(new RaycastContext(from, center, ShapeType.COLLIDER, FluidHandling.NONE, this.entity));
                     if ((hit.getType() == Type.MISS || hit.getBlockPos().equals(ignored))
                        && (base == null || !throughBlock(base, from, center))) {
                        seen++;
                     }

                     total++;
                  }
               }
            }

            return (float)seen / total;
         } else {
            return 0.0F;
         }
      }

      private static boolean throughBlock(Box block, Vec3d from, Vec3d to) {
         Vec3d end = new Vec3d(
            MathHelper.lerp(1.0E-7, to.x, from.x),
            MathHelper.lerp(1.0E-7, to.y, from.y),
            MathHelper.lerp(1.0E-7, to.z, from.z)
         );
         return block.contains(from) || block.raycast(from, end).isPresent();
      }

      private float scaled(float damage) {
         if (!(this.entity instanceof PlayerEntity)) {
            return damage;
         }

         return switch (this.entity.getEntityWorld().getDifficulty()) {
            case PEACEFUL -> 0.0F;
            case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5F;
            default -> throw new MatchException(null, null);
         };
      }
   }
}
