package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class KillAuraModule extends ClientModule {
   private static final String PRIORITY_DISTANCE = "Distance";
   private static final String PRIORITY_HEALTH = "Health";
   private static final String PRIORITY_ANGLE = "Angle";
   private static final String CRITS_OFF = "Off";
   private static final String CRITS_WAIT = "Wait";
   private static final String CRITS_JUMP = "Jump";
   private static final int CRIT_HOLD_TICKS = 8;
   private static final int JUMP_LEAD = 7;
   private static final double REACH_MARGIN = 0.01;
   private static final double RIDING_BONUS = 2.5;
   private static final double MAX_STEP_SQ = 1.0;
   private static final double[] AIM_HEIGHTS = new double[]{0.85, 0.5, 0.15};
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
   private final ModeSetting priority = this.addSetting("priority", new ModeSetting("Priority", "Distance", "Distance", "Health", "Angle"));
   private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Wait", "Off", "Wait", "Jump"));
   private LivingEntity target;
   private int critWait;

   public KillAuraModule() {
      super("kill-aura", "Kill Aura", "Attacks nearby targets without moving your camera.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.critWait = 0;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (MeleeAttacks.canAct(mc)
         && !player.isDead()
         && !player.isSpectator()
         && mc.attackCooldown <= 0
         && MeleeAttacks.handsFree(mc, player)
         && !CombatMacroLock.heldByOther(this)
         && !MeleeAttacks.actedThisTick(player)
         && !MeleeAttacks.rightClickComing(mc)) {
         boolean riding = player.hasVehicle();
         ItemStack held = player.getMainHandStack();
         boolean spear = held.contains(DataComponentTypes.PIERCING_WEAPON);
         double reach = spear ? player.getAttackRange().getEffectiveMaxRange(player) - 0.01 : player.getEntityInteractionRange() - 0.01 + (riding ? 2.5 : 0.0);
         Vec3d eye = player.getEyePos();
         this.target = this.pick(mc, player, eye, reach);
         if (this.target == null) {
            this.critWait = 0;
         } else {
            MaceModule mace = ModuleRegistry.get(MaceModule.class);
            boolean smash = !spear && mace != null && mace.smashReady(player, this.target);
            boolean ready = spear ? !player.isBelowMinimumAttackCharge(held, 0) : smash || MeleeAttacks.charge(player) >= 1.0F;
            boolean aiming = !ready && MeleeAttacks.ticksToFull(player) <= 1;
            boolean jump = !spear
               && this.crits.is("Jump")
               && !riding
               && player.isOnGround()
               && !player.isTouchingWater()
               && MeleeAttacks.ticksToFull(player) <= 7;
            if (jump) {
               InputControl.jump();
            }

            Box raw = this.target.getBoundingBox();
            if (aiming) {
               raw = raw.offset(stride(this.target));
            }

            Vec3d point = aimPoint(player, eye, raw);
            if (point != null) {
               double side = Math.min(0.03, raw.getLengthX() * 0.15);
               Box hitBox = raw.contract(side, 0.01, side);
               Rotation aim = RotationUtils.fromTo(eye, point);
               Rotation next = !riding && !hitBox.contains(eye) && ServerRotation.canRotate(player) && !ServerRotation.pending()
                  ? ServerRotation.nextStep(player, aim)
                  : null;
               Vec3d nextAt = hits(player, eye, hitBox, reach, next);
               if (!ready) {
                  if (aiming && nextAt != null) {
                     ServerRotation.request(aim);
                  }
               } else {
                  Rotation look = riding ? RotationUtils.entityRotation(player) : ServerRotation.sent();
                  Vec3d at = hits(player, eye, hitBox, reach, look);
                  if (at != null || nextAt != null) {
                     if (spear) {
                        if (at == null) {
                           ServerRotation.request(aim);
                        } else if (!hitsOthers(player, this.target, eye, look) && MeleeAttacks.stab(mc, new EntityHitResult(this.target, at))) {
                           MeleeAttacks.claimTick(mc);
                        }
                     } else {
                        boolean hold = smash ? mace.holdsSmash(player, this.target) : !this.crits.is("Off") && !riding && this.waitForCrit(player, jump);
                        if (hold) {
                           if (smash) {
                              InputControl.dropSprint();
                           }

                           if (nextAt != null && !mc.options.attackKey.isPressed() && !MeleeAttacks.clickPending(mc)) {
                              ServerRotation.request(aim);
                           }
                        } else {
                           if (at == null) {
                              ServerRotation.request(aim);
                              at = nextAt;
                           }

                           if (MeleeAttacks.attack(mc, new EntityHitResult(this.target, at), riding ? 2.5 : 0.0)) {
                              this.critWait = 0;
                              if (smash) {
                                 mace.noteSmash();
                              }

                              MeleeAttacks.claimTick(mc);
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.critWait = 0;
      }
   }

   private boolean waitForCrit(ClientPlayerEntity player, boolean jump) {
      if (MeleeAttacks.airborne(player)) {
         if (player.isSprinting() || !(player.fallDistance > 0.0) && this.critWait++ < 8) {
            InputControl.dropSprint();
            return true;
         } else {
            return false;
         }
      } else {
         return player.isOnGround() && (jump || player.input.playerInput.jump()) && this.critWait++ < 8;
      }
   }

   private LivingEntity pick(MinecraftClient mc, ClientPlayerEntity player, Vec3d eye, double reach) {
      LivingEntity best = null;
      double bestRank = 0.0;
      double bestDistance = 0.0;

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof LivingEntity living) {
            Box raw = living.getBoundingBox();
            double distance = eye.distanceTo(TargetingUtils.closestPoint(eye, raw));
            if (!(distance > reach) && this.wanted(player, living)) {
               Vec3d point = aimPoint(player, eye, raw);
               if (point != null) {
                  if (living == this.target) {
                     return living;
                  }

                  double rank = this.rank(player, eye, living, point, distance);
                  if (best != null) {
                     int order = Double.compare(rank, bestRank);
                     if (order == 0) {
                        order = Boolean.compare(living.hurtTime > 0, best.hurtTime > 0);
                     }

                     if (order == 0) {
                        order = Double.compare(distance, bestDistance);
                     }

                     if (order >= 0) {
                        continue;
                     }
                  }

                  best = living;
                  bestRank = rank;
                  bestDistance = distance;
               }
            }
         }
      }

      return best;
   }

   private double rank(ClientPlayerEntity player, Vec3d eye, LivingEntity living, Vec3d point, double distance) {
      if (this.priority.is("Health")) {
         return CrystalDamage.health(living);
      }

      if (!this.priority.is("Angle")) {
         return distance;
      }

      Rotation sent = ServerRotation.sent();
      Rotation look = sent != null ? sent : RotationUtils.entityRotation(player);
      return RotationUtils.angleTo(look, RotationUtils.fromTo(eye, point));
   }

   private boolean wanted(ClientPlayerEntity player, LivingEntity living) {
      if (!MeleeAttacks.isEnemy(player, living) || MeleeAttacks.wastedOnShield(player, living)) {
         return false;
      } else {
         return living instanceof PlayerEntity ? this.players.get() : this.hostiles.get() && MeleeAttacks.isHostile(living);
      }
   }

   private static Vec3d aimPoint(ClientPlayerEntity player, Vec3d eye, Box raw) {
      double side = Math.min(0.15, raw.getLengthX() * 0.3);
      Vec3d near = TargetingUtils.closestPoint(eye, raw.contract(side, Math.min(0.2, raw.getLengthY() * 0.25), side));
      if (MeleeAttacks.lineIsClear(player, eye, near)) {
         return near;
      }

      Vec3d center = raw.getCenter();

      for (double height : AIM_HEIGHTS) {
         Vec3d point = new Vec3d(center.x, raw.minY + raw.getLengthY() * height, center.z);
         if (MeleeAttacks.lineIsClear(player, eye, point)) {
            return point;
         }
      }

      return null;
   }

   private static Vec3d stride(LivingEntity entity) {
      double dx = entity.getX() - entity.lastX;
      double dz = entity.getZ() - entity.lastZ;
      return dx * dx + dz * dz > 1.0 ? Vec3d.ZERO : new Vec3d(dx, 0.0, dz);
   }

   private static Vec3d hits(ClientPlayerEntity player, Vec3d eye, Box box, double reach, Rotation look) {
      if (look == null) {
         return null;
      }

      Vec3d direction = Vec3d.fromPolar(look.pitch(), look.yaw());
      Vec3d at = box.raycast(eye, eye.add(direction.multiply(reach))).orElse(box.contains(eye) ? eye : null);
      return at != null && eye.distanceTo(at) <= reach && MeleeAttacks.lineIsClear(player, eye, at) ? at : null;
   }

   private static boolean hitsOthers(ClientPlayerEntity player, Entity target, Vec3d eye, Rotation look) {
      AttackRangeComponent range = player.getAttackRange();
      Vec3d direction = Vec3d.fromPolar(look.pitch(), look.yaw());
      Vec3d start = eye.add(direction.multiply(range.getEffectiveMinRange(player)));
      double carried = Math.max(0.0, player.getMovement().dotProduct(direction));
      Vec3d end = eye.add(direction.multiply(range.getEffectiveMaxRange(player) + carried));

      for (Entity entity : player.getEntityWorld().getOtherEntities(player, new Box(start, end).expand(1.0))) {
         if (entity != target && entity instanceof LivingEntity && PiercingWeaponComponent.canHit(player, entity) && !MeleeAttacks.isEnemy(player, entity)) {
            Box box = entity.getBoundingBox().expand(range.hitboxMargin());
            if (box.contains(start) || box.raycast(start, end).isPresent()) {
               return true;
            }
         }
      }

      return false;
   }
}
