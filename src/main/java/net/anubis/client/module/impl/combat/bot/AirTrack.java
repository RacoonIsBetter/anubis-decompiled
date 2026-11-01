package net.anubis.client.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.EquipmentSlot.Type;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
final class AirTrack {
   private static final double GRAVITY = 0.08;
   private static final double VERTICAL_DRAG = 0.98;
   private static final double AIR_DRAG = 0.91;
   private static final double HIT_KNOCK = 0.4;
   private static final double SPRINT_KNOCK = 0.5;
   private static final double MAX_LIFT = 0.4;
   private static final double KEY_PUSH = 0.026;
   private static final double MAX_PUSH = 0.045;
   private static final double MAX_LEARNED = 0.1;
   static final int LAND_TICKS = 40;
   private static final int KICK_WAIT = 8;
   private static final double BLAST_NEAR = 10.0;
   private PlayerEntity target;
   private RivalBook.Rival rival;
   private final List<AirTrack.Sample> flight = new ArrayList<>();
   private Vec3d lastSeen;
   private boolean airborne;
   private Vec3d pushSum = Vec3d.ZERO;
   private int pushes;
   private Vec3d early;
   private Vec3d kickFrom;
   private Vec3d kick;
   private Vec3d kickWay;
   private int kickAt = -1;
   private boolean kickSeen;
   private int kickSeenAt;
   private boolean kickSpoiled;
   AirTrack.Landing bare;
   private double gain;
   AirTrack.Landing guessed;

   void forget() {
      this.target = null;
      this.rival = null;
      this.flight.clear();
      this.lastSeen = null;
      this.airborne = false;
      this.pushSum = Vec3d.ZERO;
      this.pushes = 0;
      this.kickAt = -1;
      this.bare = this.guessed = null;
   }

   void note(PlayerEntity target, int tick, RivalBook.Rival rival) {
      if (target != this.target) {
         this.forget();
         this.target = target;
      }

      this.rival = rival;
      Vec3d at = server(target);
      boolean fresh = !at.equals(this.lastSeen);
      this.lastSeen = at;
      if (this.kickAt >= 0 && !this.kickSeen) {
         if (at.y > this.kickFrom.y + 0.05 || at.subtract(this.kickFrom).horizontalLengthSquared() > 0.01) {
            this.kickSeen = true;
            this.kickSeenAt = tick;
         } else if (tick - this.kickAt > 8) {
            this.kickAt = -1;
         }
      }

      if (this.kickAt >= 0 && tick - this.kickAt > 40) {
         this.kickAt = -1;
      }

      boolean up = !target.isOnGround() && !floating(target);
      if (up) {
         if (!this.airborne) {
            this.airborne = true;
            this.restart();
         }

         if (fresh) {
            this.flight.add(new AirTrack.Sample(tick, at));
            if (this.flight.size() == 2) {
               this.readEarly();
            }

            this.read();
         }
      } else if (this.airborne) {
         this.airborne = false;
         if (this.kickAt >= 0 && this.kickSeen) {
            this.learn(at);
         }

         this.kickAt = -1;
         this.restart();
      }
   }

   void kicked(ClientPlayerEntity self, PlayerEntity target, float yaw, boolean sprinting, int tick) {
      if (target == this.target) {
         this.kickAt = -1;
         double keep = 1.0 - resistance(target);
         if (!(keep <= 0.0)) {
            Vec3d from = server(target);
            Vec3d away = from.subtract(self.getEntityPos()).multiply(1.0, 0.0, 1.0);
            if (!(away.lengthSquared() < 1.0E-6)) {
               away = away.normalize();
               Vec3d sideways = away.multiply(0.4 * keep);
               double lift = Math.min(0.4, 0.4 * keep);
               if (sprinting) {
                  double radians = Math.toRadians(yaw);
                  Vec3d look = new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));
                  sideways = sideways.multiply(0.5).add(look.multiply(0.5 * keep));
                  lift = Math.min(0.4, lift / 2.0 + 0.5 * keep);
               }

               this.kick = new Vec3d(sideways.x, lift, sideways.z);
               this.kickWay = sideways.lengthSquared() < 1.0E-6 ? away : sideways.normalize();
               this.kickFrom = from;
               this.kickAt = tick;
               this.kickSeen = this.kickSpoiled = false;
               this.early = null;
               World level = target.getEntityWorld();
               AirTrack.Stop still = fly(level, target, from, this.kick, Vec3d.ZERO, true, 40);
               AirTrack.Stop nudged = fly(level, target, from, this.kick, this.kickWay.multiply(0.01), true, 40);
               this.bare = still.down() ? new AirTrack.Landing(still.at(), still.ticks()) : null;
               this.gain = still.down() && nudged.down() ? nudged.at().subtract(still.at()).dotProduct(this.kickWay) / 0.01 : 0.0;
               AirTrack.Stop usual = fly(level, target, from, this.kick, this.learned(0.1), true, 40);
               this.guessed = usual.down() ? new AirTrack.Landing(usual.at(), usual.ticks()) : null;
            }
         }
      }
   }

   void blast(Vec3d center) {
      if (this.target != null && !(server(this.target).distanceTo(center) > 10.0)) {
         this.kickSpoiled = true;
         if (!this.kickSeen) {
            this.kickAt = -1;
         }

         this.restart();
      }
   }

   AirTrack.Landing landing(int tick) {
      AirTrack.Stop stop = this.path(40, tick);
      return stop != null && stop.down() ? new AirTrack.Landing(stop.at(), stop.ticks()) : null;
   }

   Vec3d lead(int ahead, int tick) {
      if (this.target == null || ahead <= 0) {
         return null;
      }

      if (this.kickAt < 0 && this.pushes < 2) {
         return null;
      }

      AirTrack.Stop stop = this.path(ahead, tick);
      return stop == null ? null : stop.at().subtract(this.target.getEntityPos());
   }

   private AirTrack.Stop path(int ahead, int tick) {
      if (this.target != null && !floating(this.target)) {
         World level = this.target.getEntityWorld();
         Vec3d velocity = this.airborne ? this.velocity() : null;
         if (this.kickAt < 0 || this.kickSeen && velocity != null) {
            if (velocity == null) {
               return null;
            }

            AirTrack.Sample now = this.flight.get(this.flight.size() - 1);
            int age = Math.min(3, tick - now.tick());
            AirTrack.Stop stop = fly(level, this.target, now.at(), velocity, this.push(velocity), false, ahead + age);
            return new AirTrack.Stop(stop.at(), Math.max(0, stop.ticks() - age), stop.down());
         } else {
            int gone = this.kickSeen ? tick - this.kickSeenAt + 1 : 0;
            AirTrack.Stop stop = fly(level, this.target, this.kickFrom, this.kick, this.learned(0.1), true, ahead + gone);
            return new AirTrack.Stop(stop.at(), Math.max(0, stop.ticks() - gone), stop.down());
         }
      } else {
         return null;
      }
   }

   private Vec3d push(Vec3d velocity) {
      if (this.pushes >= 2) {
         return limit(this.pushSum.multiply(1.0 / this.pushes), 0.045);
      }

      boolean known = this.kickAt >= 0 && this.rival != null && this.rival.knowsFlight();
      Vec3d usual = this.kickAt >= 0 ? this.learned(0.026) : limit(new Vec3d(velocity.x, 0.0, velocity.z).multiply(0.08999999999999997), 0.026);
      Vec3d seen = this.pushes == 1 ? (this.early != null ? this.pushSum.add(this.early).multiply(0.5) : this.pushSum) : this.early;
      return seen == null ? usual : limit(known ? seen.add(usual).multiply(0.5) : seen, 0.045);
   }

   private Vec3d learned(double most) {
      return this.rival != null && this.rival.knowsFlight() && this.kickWay != null
         ? limit(this.kickWay.multiply(this.rival.airAlong).add(right(this.kickWay).multiply(this.rival.airAcross)), most)
         : Vec3d.ZERO;
   }

   private void learn(Vec3d down) {
      if (this.rival != null && !this.kickSpoiled && this.bare != null && !(this.gain < 1.0)) {
         if (!(Math.abs(down.y - this.bare.at().y) > 0.6)) {
            Vec3d off = down.subtract(this.bare.at());
            double along = off.dotProduct(this.kickWay) / this.gain;
            double across = off.dotProduct(right(this.kickWay)) / this.gain;
            if (!(Math.abs(along) > 0.15000000000000002) && !(Math.abs(across) > 0.15000000000000002)) {
               this.rival.flew(along, across);
            }
         }
      }
   }

   private void read() {
      int n = this.flight.size();
      if (n >= 3) {
         AirTrack.Sample a = this.flight.get(n - 3);
         AirTrack.Sample b = this.flight.get(n - 2);
         AirTrack.Sample c = this.flight.get(n - 1);
         int first = b.tick() - a.tick();
         int second = c.tick() - b.tick();
         if (first > 0 && second > 0 && first <= 4 && second <= 4) {
            Vec3d before = b.at().subtract(a.at()).multiply(1.0 / first);
            Vec3d after = c.at().subtract(b.at()).multiply(1.0 / second);
            double decay = Math.pow(0.91, (first + second) / 2.0);
            double scale = 0.08999999999999997 / (1.0 - decay);
            Vec3d push = new Vec3d((after.x - decay * before.x) * scale, 0.0, (after.z - decay * before.z) * scale);
            if (!(push.horizontalLength() > 0.045)) {
               this.pushSum = this.pushSum.add(push);
               this.pushes++;
            }
         }
      }
   }

   private void readEarly() {
      if (this.kickAt >= 0 && this.kickSeen && !this.kickSpoiled) {
         AirTrack.Sample b = this.flight.get(0);
         AirTrack.Sample c = this.flight.get(1);
         int span = c.tick() - b.tick();
         if (span > 0 && span <= 4) {
            Vec3d mean = c.at().subtract(b.at()).multiply(1.0 / span);
            double grip = friction(this.target.getEntityWorld(), this.kickFrom) * 0.91;
            int steps = 20;
            double[] rise = new double[steps + 1];
            double[] carry = new double[steps + 1];
            double[] perPush = new double[steps + 1];
            double y = this.kick.y;
            double h = 1.0;
            double unit = 1.0;

            for (int step = 1; step <= steps; step++) {
               if (step > 1) {
                  double drag = step == 2 ? grip : 0.91;
                  h *= drag;
                  unit = unit * drag + 1.0;
                  y = (y - 0.08) * 0.98;
               }

               rise[step] = y;
               carry[step] = h;
               perPush[step] = unit;
            }

            int best = -1;
            double closest = Double.MAX_VALUE;

            for (int end = span; end <= steps; end++) {
               double off = Math.abs(window(rise, end, span) - mean.y);
               if (off < closest) {
                  closest = off;
                  best = end;
               }
            }

            if (best >= 0 && !(closest > 0.04)) {
               double alone = window(carry, best, span);
               double gain = window(perPush, best, span);
               Vec3d push = new Vec3d(mean.x - this.kick.x * alone, 0.0, mean.z - this.kick.z * alone).multiply(1.0 / gain);
               if (push.horizontalLength() <= 0.1) {
                  this.early = push;
               }
            }
         }
      }
   }

   private static double window(double[] values, int end, int span) {
      double sum = 0.0;

      for (int i = end - span + 1; i <= end; i++) {
         sum += values[i];
      }

      return sum / span;
   }

   private Vec3d velocity() {
      int n = this.flight.size();
      if (n < 2) {
         return null;
      } else {
         AirTrack.Sample b = this.flight.get(n - 2);
         AirTrack.Sample c = this.flight.get(n - 1);
         int span = c.tick() - b.tick();
         if (span > 0 && span <= 4) {
            Vec3d mean = c.at().subtract(b.at()).multiply(1.0 / span);
            double half = (span - 1) / 2.0;
            double drag = Math.pow(0.91, half);
            return new Vec3d(mean.x * drag, mean.y - 0.0784 * half, mean.z * drag);
         } else {
            return null;
         }
      }
   }

   private void restart() {
      this.flight.clear();
      this.pushSum = Vec3d.ZERO;
      this.pushes = 0;
      this.early = null;
   }

   private static AirTrack.Stop fly(World level, PlayerEntity body, Vec3d from, Vec3d velocity, Vec3d push, boolean knocked, int ticks) {
      Box box = body.getBoundingBox().offset(from.subtract(body.getEntityPos()));
      double grip = knocked ? friction(level, from) * 0.91 : 0.91;
      double x = velocity.x;
      double y = velocity.y;
      double z = velocity.z;
      Vec3d at = from;

      for (int tick = 1; tick <= ticks; tick++) {
         if (tick > 1 || !knocked) {
            double drag = knocked && tick == 2 ? grip : 0.91;
            x *= drag;
            z *= drag;
            y = (y - 0.08) * 0.98;
         }

         x += push.x;
         z += push.z;
         Vec3d step = Entity.adjustMovementForCollisions(body, new Vec3d(x, y, z), box, level, List.of());
         box = box.offset(step);
         at = at.add(step);
         if (step.x != x) {
            x = 0.0;
         }

         if (step.z != z) {
            z = 0.0;
         }

         if (step.y != y) {
            if (y < 0.0) {
               return new AirTrack.Stop(at, tick, true);
            }

            y = 0.0;
         }
      }

      return new AirTrack.Stop(at, ticks, false);
   }

   private static double friction(World level, Vec3d at) {
      return level.getBlockState(BlockPos.ofFloored(at.x, at.y - 0.5000001, at.z)).getBlock().getSlipperiness();
   }

   private static double resistance(PlayerEntity target) {
      double[] total = new double[]{0.0};

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         if (slot.getType() == Type.HUMANOID_ARMOR) {
            target.getEquippedStack(slot).applyAttributeModifiers(slot, (attribute, modifier) -> {
               if (attribute.value() == EntityAttributes.KNOCKBACK_RESISTANCE.value() && modifier.operation() == Operation.ADD_VALUE) {
                  total[0] += modifier.value();
               }
            });
         }
      }

      return Math.min(1.0, Math.max(0.0, total[0]));
   }

   private static boolean floating(PlayerEntity target) {
      return target.isGliding()
         || target.isInFluid()
         || target.isClimbing()
         || target.hasVehicle()
         || target.getAbilities().flying
         || target.hasStatusEffect(StatusEffects.LEVITATION)
         || target.hasStatusEffect(StatusEffects.SLOW_FALLING);
   }

   private static Vec3d server(PlayerEntity target) {
      PositionInterpolator handler = target.getInterpolator();
      return handler == null ? target.getEntityPos() : handler.getLerpedPos();
   }

   private static Vec3d right(Vec3d way) {
      return new Vec3d(-way.z, 0.0, way.x);
   }

   private static Vec3d limit(Vec3d push, double most) {
      double length = push.horizontalLength();
      return length <= most ? push : push.multiply(most / length);
   }

   @Environment(EnvType.CLIENT)
   record Landing(Vec3d at, int ticks) {
   }

   @Environment(EnvType.CLIENT)
   private record Sample(int tick, Vec3d at) {
   }

   @Environment(EnvType.CLIENT)
   private record Stop(Vec3d at, int ticks, boolean down) {
   }
}
