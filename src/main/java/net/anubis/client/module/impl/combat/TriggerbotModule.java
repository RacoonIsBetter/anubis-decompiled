package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class TriggerbotModule extends ClientModule {
   private static final String CRITS_OFF = "Off";
   private static final String CRITS_SMART = "Smart";
   private static final String CRITS_ONLY = "Only";
   private static final int TRACK_GRACE_TICKS = 4;
   private static final int SMART_MAX_WAIT_TICKS = 12;
   private final NumberSetting reaction = this.addSetting("reaction", new NumberSetting("Reaction", 3.0F, 0.0F, 10.0F).step(1.0F));
   private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Smart", "Off", "Smart", "Only"));
   private final BooleanSetting holdAttack = this.addSetting("hold-attack", new BooleanSetting("Hold Attack", false));
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
   private final BooleanSetting invisible = this.addSetting("invisible", new BooleanSetting("Invisible", false));
   private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
   private int ticks;
   private int trackedId = -1;
   private int seenTick;
   private int reactionDoneTick;
   private int chargedTick = -1;
   private int chargePause;
   private int critWait;
   private int critWaitTick = -1;

   public TriggerbotModule() {
      super("triggerbot", "Triggerbot", "Hits the entity under the crosshair once the swing is charged, with a reaction delay.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.ticks = 0;
      this.trackedId = -1;
      this.seenTick = 0;
      this.reactionDoneTick = 0;
      this.chargedTick = -1;
      this.chargePause = 0;
      this.critWait = 0;
      this.critWaitTick = -1;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      this.ticks++;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (!MeleeAttacks.canAct(mc)) {
         this.trackedId = -1;
      } else {
         this.trackCharge(player);
         EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
         if (this.holdAttack.get() && this.trackedId != -1 && mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.MISS) {
            MeleeAttacks.takeAttackClicks(mc);
         }

         if (hit != null && hit.getEntity() instanceof LivingEntity target && this.isTarget(player, target)) {
            if (target.getId() != this.trackedId) {
               this.trackedId = target.getId();
               this.reactionDoneTick = this.ticks + this.reaction.getInt() + MeleeAttacks.spread(2);
            }

            this.seenTick = this.ticks;
            MaceModule mace = ModuleRegistry.get(MaceModule.class);
            boolean smash = mace != null && mace.smashReady(player, target);
            if (this.ticks >= this.reactionDoneTick && (smash || this.chargedTick >= 0 && this.ticks - this.chargedTick >= this.chargePause)) {
               if (!this.holdAttack.get() || mc.options.attackKey.isPressed()) {
                  if (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(player.getMainHandStack())) {
                     if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.wastedOnShield(player, target)) {
                        if (mc.attackCooldown <= 0 && !MeleeAttacks.clickPending(mc) && !MeleeAttacks.rightClickComing(mc) && !ServerRotation.pending()) {
                           boolean stabbing = player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON);
                           if (stabbing || smash || this.critsAllow(mc, player, target)) {
                              if (stabbing) {
                                 hit = this.stabHit(player, target);
                                 if (hit == null) {
                                    return;
                                 }
                              }

                              if (stabbing ? MeleeAttacks.stab(mc, hit) : MeleeAttacks.attack(mc, hit)) {
                                 this.chargedTick = -1;
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
            if (this.ticks - this.seenTick > 4) {
               this.trackedId = -1;
            }
         }
      }
   }

   private void trackCharge(ClientPlayerEntity player) {
      if (MeleeAttacks.charge(player) < 1.0F) {
         this.chargedTick = -1;
      } else if (this.chargedTick < 0) {
         this.chargedTick = this.ticks;
         this.chargePause = MeleeAttacks.spread(1);
      }
   }

   private EntityHitResult stabHit(ClientPlayerEntity player, LivingEntity target) {
      Rotation sent = ServerRotation.sent();
      if (sent == null) {
         return null;
      }

      AttackRangeComponent range = player.getAttackRange();
      Vec3d look = Vec3d.fromPolar(sent.pitch(), sent.yaw());
      Vec3d eye = player.getEyePos();
      Vec3d start = eye.add(look.multiply(range.getEffectiveMinRange(player)));
      Vec3d end = eye.add(look.multiply(range.getEffectiveMaxRange(player) + Math.max(0.0, player.getMovement().dotProduct(look))));
      float margin = range.hitboxMargin();
      Vec3d at = target.getBoundingBox().expand(margin).raycast(eye, end).orElse(null);
      if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
         for (Entity entity : player.getEntityWorld().getOtherEntities(player, new Box(start, end).expand(1.0))) {
            if (entity != target && entity instanceof LivingEntity other && PiercingWeaponComponent.canHit(player, entity) && !this.isTarget(player, other)) {
               Box box = entity.getBoundingBox().expand(margin);
               if (box.contains(start) || box.raycast(start, end).isPresent()) {
                  return null;
               }
            }
         }

         return new EntityHitResult(target, at);
      } else {
         return null;
      }
   }

   private boolean isTarget(ClientPlayerEntity self, LivingEntity entity) {
      if (!MeleeAttacks.isEnemy(self, entity)) {
         return false;
      } else if (entity.isInvisible() && !this.invisible.get()) {
         return false;
      } else {
         return entity instanceof PlayerEntity ? this.players.get() : this.hostiles.get() && MeleeAttacks.isHostile(entity);
      }
   }

   private boolean critsAllow(MinecraftClient mc, ClientPlayerEntity player, LivingEntity target) {
      return switch (this.crits.get()) {
         case "Only" -> {
            if (MeleeAttacks.airborne(player) && player.isSprinting() && player.fallDistance > 0.0) {
               InputControl.dropSprint();
            }

            yield MeleeAttacks.wouldCrit(player, target);
         }
         case "Smart" -> {
            if (MeleeAttacks.wouldCrit(player, target)) {
               yield true;
            } else {
               boolean jumping = player.isOnGround() && mc.options.jumpKey.isPressed() && !player.isSprinting();
               if (!MeleeAttacks.critStance(player, target) && !jumping) {
                  yield true;
               } else {
                  if (this.critWaitTick != this.ticks - 1) {
                     this.critWait = 0;
                  }

                  this.critWaitTick = this.ticks;
                  yield this.critWait++ >= 12;
               }
            }
         }
         default -> true;
      };
   }
}
