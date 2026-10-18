package net.anubis.client.module.impl.combat.crystal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
public final class Combo {
   public static final double TARGET_RANGE = 10.0;
   public static final int MAX_TARGETS = 3;
   public static final float LETHAL_RANK = 1000.0F;
   public static final float POP_RANK = 500.0F;
   public static final float SELF_WEIGHT = 0.5F;
   public static final float ANCHOR_EDGE = 1.0F;
   private static final float BLAST_PROOF = 600.0F;
   private static final double FOCUS_BONUS = 2.0;
   private static final double WEAK_BONUS = 2.0;
   private static final float WEAK_HEALTH = 10.0F;
   private static final double MAX_STEP_SQR = 1.0;
   private static final double GRAVITY = 0.08;
   private static final double AIR_DRAG = 0.98;
   private static final Combo.Kind[] SETUP = new Combo.Kind[]{Combo.Kind.CHARGE, Combo.Kind.CRYSTAL, Combo.Kind.OBSIDIAN, Combo.Kind.ANCHOR};
   private static final Map<LivingEntity, Combo.Track> TRACKS = new WeakHashMap<>();
   private static LivingEntity focus;

   private Combo() {
   }

   public static List<LivingEntity> enemies(MinecraftClient mc, ClientPlayerEntity player) {
      List<LivingEntity> enemies = new ArrayList<>();

      for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
         if (MeleeAttacks.isEnemy(player, other) && player.squaredDistanceTo(other) < 100.0) {
            enemies.add(other);
         }
      }

      enemies.sort(
         Comparator.comparingDouble(enemy -> player.distanceTo(enemy) - (enemy == focus ? 2.0 : 0.0) - (CrystalDamage.health(enemy) <= 10.0F ? 2.0 : 0.0))
      );
      focus = enemies.isEmpty() ? null : enemies.get(0);
      return enemies.size() > 3 ? enemies.subList(0, 3) : enemies;
   }

   public static List<LivingEntity> friends(MinecraftClient mc, ClientPlayerEntity player) {
      List<LivingEntity> friends = new ArrayList<>();

      for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
         if (other != player && other.isAlive() && MeleeAttacks.isFriend(other) && player.squaredDistanceTo(other) < 100.0) {
            friends.add(other);
         }
      }

      return friends;
   }

   public static Vec3d lead(LivingEntity entity, int ticks) {
      PositionInterpolator handler = entity.getInterpolator();
      Vec3d server = handler == null ? entity.getEntityPos() : handler.getLerpedPos();
      Vec3d lag = server.subtract(entity.getEntityPos());
      int ahead = ticks;
      Vec3d velocity;
      if (entity instanceof ClientPlayerEntity) {
         velocity = entity.getEntityPos().subtract(entity.lastX, entity.lastY, entity.lastZ);
      } else {
         Combo.Track track = TRACKS.computeIfAbsent(entity, ignored -> new Combo.Track());
         track.note(entity, server);
         velocity = track.velocity(entity);
         ahead += Math.min(track.age(entity), entity.getType().getTrackTickInterval());
      }

      return ticks > 0 && !(velocity.horizontalLengthSquared() > 1.0) ? lag.add(travel(entity, server, velocity, ahead)) : lag;
   }

   private static Vec3d travel(LivingEntity entity, Vec3d from, Vec3d velocity, int ticks) {
      Box box = entity.getBoundingBox().offset(from.subtract(entity.getEntityPos()));
      boolean floating = entity.isGliding() || entity.isInFluid() || entity.isClimbing() || entity.hasNoGravity() || entity.hasVehicle();
      double x = velocity.x;
      double y = velocity.y;
      double z = velocity.z;
      Vec3d moved = Vec3d.ZERO;

      for (int tick = 0; tick < ticks; tick++) {
         if (!floating) {
            y = (y - 0.08) * 0.98;
         }

         Vec3d step = Entity.adjustMovementForCollisions(entity, new Vec3d(x, y, z), box, entity.getEntityWorld(), List.of());
         if (step.x != x) {
            x = 0.0;
         }

         if (step.y != y) {
            y = 0.0;
         }

         if (step.z != z) {
            z = 0.0;
         }

         box = box.offset(step);
         moved = moved.add(step);
      }

      return moved;
   }

   public static boolean inHole(LivingEntity target) {
      BlockPos feet = target.getBlockPos();

      for (Direction side : Type.HORIZONTAL) {
         BlockState state = target.getEntityWorld().getBlockState(feet.offset(side));
         if (state.getBlock().getBlastResistance() < 600.0F) {
            return false;
         }
      }

      return true;
   }

   public static boolean kills(float effective, LivingEntity target) {
      return effective >= CrystalDamage.health(target);
   }

   public static float value(float effective, LivingEntity target) {
      float health = CrystalDamage.health(target);
      float rank = effective < health ? 0.0F : (target.isHolding(Items.TOTEM_OF_UNDYING) ? 500.0F : 1000.0F);
      return Math.min(effective, health) + rank;
   }

   public static Combo.Shot pick(List<Combo.Shot> shots) {
      Combo.Shot crystal = best(shots, Combo.Kind.BREAK);
      Combo.Shot anchor = best(shots, Combo.Kind.DETONATE);
      if (crystal == null && anchor == null) {
         for (Combo.Kind kind : SETUP) {
            Combo.Shot shot = best(shots, kind);
            if (shot != null) {
               return shot;
            }
         }

         return null;
      } else {
         boolean anchorWins = crystal == null || anchor != null && (anchor.value() > crystal.value() + 1.0F || anchor.kills() && !crystal.kills());
         return anchorWins ? anchor : crystal;
      }
   }

   private static Combo.Shot best(List<Combo.Shot> shots, Combo.Kind kind) {
      Combo.Shot best = null;

      for (Combo.Shot shot : shots) {
         if (shot.kind() == kind && (best == null || shot.value() > best.value() || shot.value() == best.value() && shot.readyIn() < best.readyIn())) {
            best = shot;
         }
      }

      return best;
   }

   @Environment(EnvType.CLIENT)
   public enum Kind {
      BREAK,
      CRYSTAL,
      OBSIDIAN,
      ANCHOR,
      CHARGE,
      DETONATE;
   }

   @Environment(EnvType.CLIENT)
   public record Shot(Combo.Kind kind, Vec3d aim, int route, float value, boolean kills, int readyIn, Object plan) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Track {
      private Vec3d last;
      private int lastAt;
      private int sampledAt;
      private Vec3d velocity = Vec3d.ZERO;

      void note(LivingEntity entity, Vec3d server) {
         int now = entity.age;
         if (now < this.sampledAt) {
            this.last = null;
         }

         boolean watched = now - this.sampledAt <= 1;
         this.sampledAt = now;
         if (!server.equals(this.last)) {
            int interval = entity.getType().getTrackTickInterval();
            int span = watched ? interval : Math.max(interval, now - this.lastAt);
            this.velocity = this.last == null ? Vec3d.ZERO : server.subtract(this.last).multiply(1.0 / span);
            this.last = server;
            this.lastAt = now;
         }
      }

      int age(LivingEntity entity) {
         return entity.age - this.lastAt;
      }

      Vec3d velocity(LivingEntity entity) {
         return this.age(entity) > entity.getType().getTrackTickInterval() + 1 ? Vec3d.ZERO : this.velocity;
      }
   }
}
