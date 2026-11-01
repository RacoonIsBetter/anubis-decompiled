package net.anubis.client.module.impl.combat.bot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class SwordBotModule extends ClientModule {
   private static final String PATIENT = "Patient";
   private static final String AGGRESSIVE = "Aggressive";
   private static final Predicate<ItemStack> GAPPLE = stack -> stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
   private static final double HIT_RANGE = 3.0;
   private static final double CHASE_RANGE = 6.0;
   private static final int RESET_WINDOW = 3;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Patient", "Patient", "Aggressive"));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Fight Range", 16.0F, 6.0F, 32.0F).step(1.0F));
   private final NumberSetting turnSpeed = this.addSetting("turn-speed", new NumberSetting("Turn Speed", 1.0F, 0.5F, 1.5F).step(0.05F));
   private final BooleanSetting strafe = this.addSetting("strafe", new BooleanSetting("Strafe", true));
   private final BooleanSetting crits = this.addSetting("crits", new BooleanSetting("Jump Crits", true));
   private final BooleanSetting jumpReset = this.addSetting("jump-reset", new BooleanSetting("Jump Reset", true));
   private final NumberSetting eatBelow = this.addSetting("eat-below", new NumberSetting("Gapple Below HP", 10.0F, 0.0F, 20.0F).step(1.0F));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final BotBody body = new BotBody();
   private PlayerEntity fought;
   private double closing;
   private double lastTheirReach = Double.NaN;
   private double jitter;
   private int tapTicks;
   private boolean tapBack;
   private boolean critJump;
   private int landedTicks;
   private int resetUntil;
   private int lastHurt;
   private boolean eating;

   public SwordBotModule() {
      super(
         "sword-bot",
         "Sword PvP Bot",
         "Fights the nearest enemy player with a sword or axe: keeps out of their reach while charging, times crits, strafes and eats gapples.",
         ModuleCategory.COMBAT
      );
   }

   @Override
   protected void onEnable() {
      this.body.forget();
      this.newFight(null);
      this.eating = false;
      this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
   }

   @Override
   protected void onDisable() {
      this.body.release(this.mc);
      this.body.forget();
      CombatMacroLock.release(this);
   }

   private void newFight(PlayerEntity target) {
      this.fought = target;
      this.closing = 0.0;
      this.lastTheirReach = Double.NaN;
      this.jitter = randomJitter();
      this.tapTicks = 0;
      this.critJump = false;
      this.resetUntil = 0;
   }

   private void tick() {
      ClientPlayerEntity player = this.mc.player;
      this.body.release(this.mc);
      if (player != null && this.mc.world != null && MeleeAttacks.canAct(this.mc) && !player.isDead()) {
         PlayerEntity target = this.body.pick(this.mc, player, this.range.get());
         int tick = this.body.tick();
         if (target != this.fought) {
            if (target != null) {
               ModuleChat.send(this, ModuleChat.body().text("Fighting ").name(target.getName().getString()).text("."));
            } else if (this.fought != null) {
               ModuleChat.send(this, ModuleChat.body().text("No enemy in range, waiting."));
            }

            this.newFight(target);
         }

         if (target != null) {
            this.body.face(this.mc, player, target, this.turnSpeed.get());
            boolean low = player.getHealth() <= this.eatBelow.get() && player.getAbsorptionAmount() < 4.0F;
            if ((low || this.eating && player.isUsingItem()) && this.body.use(this.mc, player, GAPPLE)) {
               this.eating = true;
               this.body.backOff(this.mc, player);
            } else {
               this.eating = false;
               if (!player.isUsingItem()) {
                  holdWeapon(player);
                  Vec3d eye = player.getEyePos();
                  double myReach = eye.distanceTo(TargetingUtils.closestPoint(eye, target.getBoundingBox()));
                  Vec3d theirEye = target.getEyePos();
                  double theirReach = theirEye.distanceTo(TargetingUtils.closestPoint(theirEye, player.getBoundingBox()));
                  if (!Double.isNaN(this.lastTheirReach)) {
                     this.closing = this.closing * 0.5 + (this.lastTheirReach - theirReach) * 0.5;
                  }

                  this.lastTheirReach = theirReach;
                  int ready = MeleeAttacks.ticksToFull(player);
                  float charge = MeleeAttacks.charge(player);
                  double facing = facingAngle(target, player);
                  Vec3d away = new Vec3d(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ()).normalize();
                  Vec3d theirMotion = target.getEntityPos().subtract(target.lastX, target.lastY, target.lastZ);
                  double fleeing = theirMotion.x * away.x + theirMotion.z * away.z;
                  boolean iframes = target.hurtTime > 1;
                  boolean shielded = target.isBlocking();
                  boolean threatened = theirReach <= 3.15 && facing <= 40.0;
                  boolean close = myReach <= 3.0;
                  double enemyReach = 3.35
                     + 0.12 * MathHelper.clamp(Math.round(ping(player) / 50.0F), 0, 3)
                     + 2.5 * MathHelper.clamp(this.closing, 0.0, 0.35)
                     - (this.style.is("Aggressive") ? 0.2 : 0.0);
                  double gap = ready <= 0
                     ? 2.9
                     : (ready <= 2 ? 3.08 + 0.1 * (ready - 1) + this.jitter : Math.max(enemyReach + this.jitter, 2.9 + 0.26 * ready));
                  if (facing < 8.0 && theirReach < 4.2) {
                     this.body.flipSide();
                  }

                  double side = this.strafe.get() ? 1.0 : 0.0;
                  if (player.hurtTime > this.lastHurt && player.hurtTime >= 8) {
                     this.resetUntil = tick + 3;
                  }

                  this.lastHurt = player.hurtTime;
                  if (this.tapTicks > 0) {
                     this.tapTicks--;
                     this.body.drive(this.mc, player, this.tapBack ? -1 : 0, side * (this.tapBack ? 0.8 : 1.0), false, false);
                  } else if ((!(myReach > 6.0) || !(myReach > gap + 1.0)) && !(Math.abs(target.getY() - player.getY()) > 2.5)) {
                     if (this.critJump) {
                        if (player.isOnGround()) {
                           if (++this.landedTicks >= 2) {
                              this.critJump = false;
                           }
                        } else {
                           this.landedTicks = 0;
                        }

                        if (this.critJump) {
                           boolean falling = ready <= 1 && player.getVelocity().y < 0.1;
                           this.body.drive(this.mc, player, falling ? 0 : 1, falling ? side * 0.35 : 0.0, !falling, false);
                           if (ready <= 0 && close && !iframes) {
                              InputControl.dropSprint();
                              this.strike(player, target, 0.95F, true);
                           }

                           return;
                        }
                     }

                     if (this.crits.get()
                        && player.isOnGround()
                        && !player.isTouchingWater()
                        && !threatened
                        && this.critWindow(ready, myReach, facing, fleeing)) {
                        this.critJump = true;
                        this.landedTicks = 0;
                        this.body.drive(this.mc, player, 1, 0.0, true, true);
                     } else if (ready <= 0 && !shielded) {
                        if (iframes) {
                           this.body.drive(this.mc, player, myReach > 3.12 ? 1 : (myReach < 2.88 ? -1 : 0), side, false, false);
                        } else {
                           this.body.drive(this.mc, player, myReach < 1.6 ? -1 : 1, myReach <= 3.5 ? side * 0.35 : 0.0, myReach >= 1.6, false);
                           if (close) {
                              this.strike(player, target, 0.95F, false);
                           }
                        }
                     } else {
                        if (shielded) {
                           gap = Math.max(gap, enemyReach);
                        }

                        int band = theirReach > gap + 0.12 ? 1 : (theirReach < gap - 0.12 ? -1 : 0);
                        if (band < 0 && fleeing > 0.15) {
                           band = 0;
                        }

                        boolean counter = threatened && close && charge >= 0.6F && !iframes && !shielded;
                        boolean resetting = this.jumpReset.get() && tick < this.resetUntil && player.isOnGround() && !player.isTouchingWater();
                        if (resetting) {
                           this.body.drive(this.mc, player, 1, 0.0, true, true);
                        } else if (ready <= 2 && !counter && this.strafe.get() && !shielded) {
                           this.body.drive(this.mc, player, 1, 1.0, true, false);
                        } else {
                           double amount = !this.strafe.get() ? 0.0 : (band == 0 ? 1.0 : (band < 0 ? 0.8 : 0.5));
                           this.body.drive(this.mc, player, band, amount, band > 0, false);
                        }

                        if (counter) {
                           this.strike(player, target, 0.6F, false);
                        }
                     }
                  } else {
                     this.body.drive(this.mc, player, 1, 0.0, true, false);
                     if (close && ready <= 0 && !iframes && !shielded) {
                        this.strike(player, target, 0.95F, false);
                     }
                  }
               }
            }
         }
      } else {
         this.body.forget();
      }
   }

   private boolean critWindow(int ready, double myReach, double facing, double fleeing) {
      if (ready >= 7 && ready <= 10 && myReach >= 2.3 && myReach <= 5.0) {
         return true;
      } else {
         return ready >= 3 && ready <= 10 && !(myReach < 2.3) && !(myReach > 4.2)
            ? facing >= 100.0 && fleeing > 0.05 || this.style.is("Aggressive") && facing > 60.0
            : false;
      }
   }

   private void strike(ClientPlayerEntity player, PlayerEntity target, float charge, boolean crit) {
      if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.actedThisTick(player) && this.mc.attackCooldown <= 0) {
         if (!(MeleeAttacks.charge(player) < charge) && MeleeAttacks.isMeleeWeapon(player.getMainHandStack())) {
            if (!crit || MeleeAttacks.airborne(player) && !(player.getVelocity().y >= 0.0)) {
               Rotation sent = ServerRotation.sent();
               if (sent != null) {
                  Vec3d eye = player.getEyePos();
                  double reach = Math.min(3.0, player.getEntityInteractionRange());
                  Box box = target.getBoundingBox();
                  Vec3d at = box.contains(eye) ? eye : box.raycast(eye, eye.add(Vec3d.fromPolar(sent.pitch(), sent.yaw()).multiply(reach))).orElse(null);
                  if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
                     if (MeleeAttacks.attack(this.mc, new EntityHitResult(target, at))) {
                        MeleeAttacks.claimTick(this.mc);
                        this.jitter = randomJitter();
                        this.tapTicks = 1;
                        this.tapBack = player.getEyePos().distanceTo(TargetingUtils.closestPoint(eye, box)) < 2.3;
                        if (this.tapBack) {
                           this.tapTicks = 2;
                        }

                        this.critJump = false;
                     }
                  }
               }
            }
         }
      }
   }

   private static double facingAngle(PlayerEntity target, ClientPlayerEntity self) {
      Vec3d look = Vec3d.fromPolar(target.getPitch(), target.getHeadYaw());
      Vec3d toMe = self.getEntityPos().add(0.0, 1.1, 0.0).subtract(target.getEyePos()).normalize();
      return Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toMe), -1.0, 1.0)));
   }

   private static int ping(ClientPlayerEntity player) {
      PlayerListEntry info = player.networkHandler.getPlayerListEntry(player.getUuid());
      return info == null ? 0 : Math.max(0, info.getLatency());
   }

   private static double randomJitter() {
      return ThreadLocalRandom.current().nextDouble(-0.06, 0.06);
   }

   private static void holdWeapon(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      if (!MeleeAttacks.isMeleeWeapon(player.getMainHandStack())) {
         int best = -1;
         double most = 0.0;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (MeleeAttacks.isMeleeWeapon(stack)) {
               double damage = MeleeAttacks.attackDamage(stack);
               if (best < 0 || damage > most) {
                  best = i;
                  most = damage;
               }
            }
         }

         if (best >= 0) {
            inventory.setSelectedSlot(best);
         }
      }
   }
}
