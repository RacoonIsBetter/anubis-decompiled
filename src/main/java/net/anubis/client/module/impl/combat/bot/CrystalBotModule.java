package net.anubis.client.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.builder.Aim;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.AnchorAuraModule;
import net.anubis.client.module.impl.combat.AutoCityModule;
import net.anubis.client.module.impl.combat.AutoInvTotemModule;
import net.anubis.client.module.impl.combat.AutoTotemModule;
import net.anubis.client.module.impl.combat.AutoTrapModule;
import net.anubis.client.module.impl.combat.CrystalAuraModule;
import net.anubis.client.module.impl.combat.anchor.AnchorGuard;
import net.anubis.client.module.impl.combat.crystal.Combo;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class CrystalBotModule extends ClientModule {
   private static final double MIN = 2.4;
   private static final double IDEAL = 3.2;
   private static final double MAX = 4.0;
   private static final Predicate<ItemStack> GAPPLE = stack -> stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
   private static final Predicate<ItemStack> XP = stack -> stack.isOf(Items.EXPERIENCE_BOTTLE);
   private static final Predicate<ItemStack> PEARL = stack -> stack.isOf(Items.ENDER_PEARL);
   private static final Predicate<ItemStack> OBSIDIAN = stack -> stack.isOf(Items.OBSIDIAN);
   private static final Predicate<ItemStack> ANCHOR = stack -> stack.isOf(Items.RESPAWN_ANCHOR);
   private static final Predicate<ItemStack> GLOWSTONE = stack -> stack.isOf(Items.GLOWSTONE);
   private static final float ARMOR_LOW = 0.3F;
   private static final int RUN_HOLD = 40;
   private static final int POP_COVER = 40;
   private static final int LAUNCH_GAP = 80;
   private static final int LAUNCH_STALL = 20;
   private static final int LAUNCH_TIMEOUT = 16;
   private static final double LAUNCH_START = 3.9;
   private static final double LAUNCH_GIVE_UP = 4.1;
   private static final double LAUNCH_STEP_IN = 2.9;
   private static final int LAUNCH_WATCH = 8;
   private static final double LAUNCH_LIFT = 0.3;
   private static final double HIT_RANGE = 2.95;
   private static final int THROW_TIMEOUT = 14;
   private static final int PEARL_GAP = 30;
   private static final int SHIELD_GAP = 20;
   private static final int BREACH_WAIT = 40;
   private static final int BREACH_WAIT_CAMPER = 15;
   private static final int BREACH_GAP = 100;
   private static final double ESCAPE_FAR = 20.0;
   private static final double ESCAPE_NEAR = 12.0;
   private static final int SEARCH_GAP = 10;
   private static final double EAT_CLEAR = 8.0;
   private static final int FOLLOW_TICKS = 12;
   private static final int OPENER_WINDOW = 40;
   private static final int CAMP_MAX = 2;
   private static final int SPOT_SCAN_GAP = 5;
   private static final float SPOT_DANGER = 8.0F;
   private static final int WORKING = 0;
   private static final int PLACED = 1;
   private static final int REGROUP_TICKS = 300;
   private static final double REGROUP_RANGE = 64.0;
   private static final int BOX_AIM_TIMEOUT = 10;
   private static final float RETREAT_HEALTH = 6.0F;
   private static final int POP_STREAK = 3;
   private static final int POP_STREAK_GAP = 600;
   private static final int POP_RUN = 200;
   private static final double BLAST_WATCH = 7.0;
   private static final double BELOW_DROP = 3.0;
   private static final double BELOW_REACH = 8.0;
   private static final double DIG_DEEPEST = 6.0;
   private static final int BELOW_SETTLE = 20;
   private static final int DIG_TIMEOUT = 80;
   private static final int DIG_WALK = 40;
   private static final int DIG_MAX = 2;
   private static final int DIG_GAP = 100;
   private static final int DIG_DOWN_TICKS = 50;
   private static final int DIG_BASE_TICKS = 25;
   private static final float LEVEL_MIN_DAMAGE = 2.5F;
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Fight Range", 16.0F, 8.0F, 32.0F).step(1.0F));
   private final BooleanSetting anchors = this.addSetting("anchors", new BooleanSetting("Use Anchors", true));
   private final BooleanSetting launches = this.addSetting("knock-up", new BooleanSetting("Knock-Up Hits", true));
   private final BooleanSetting breach = this.addSetting("break-holes", new BooleanSetting("Break Holes", true));
   private final BooleanSetting guardAnchors = this.addSetting("guard-anchors", new BooleanSetting("Guard Anchors", true));
   private final BooleanSetting surround = this.addSetting("surround", new BooleanSetting("Surround Self", true));
   private final NumberSetting eatBelow = this.addSetting("eat-below", new NumberSetting("Gapple Below HP", 14.0F, 0.0F, 20.0F).step(1.0F));
   private final BooleanSetting mendArmor = this.addSetting("mend-armor", new BooleanSetting("Mend Armor", true));
   private final BooleanSetting escape = this.addSetting("escape-pearls", new BooleanSetting("Escape Pearl", true));
   private final BooleanSetting chase = this.addSetting("chase-pearls", new BooleanSetting("Chase Pearls", true));
   private final BooleanSetting refill = this.addSetting("refill", new BooleanSetting("Refill Hotbar", true));
   private final BooleanSetting learn = this.addSetting("learn", new BooleanSetting("Learn Rivals", true));
   private final BooleanSetting dance = this.addSetting("strafe", new BooleanSetting("Strafe", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final BotBody body = new BotBody();
   private final BotKit kit = new BotKit();
   private final RivalBook book = new RivalBook();
   private final BotPause pause = new BotPause();
   private final AirTrack air = new AirTrack();
   private PlayerEntity challenger;
   private int challengerSince;
   private int attackedBy = -1;
   private int attackedAt = -1000;
   private double closest;
   private int closerAt;
   private StandPicker.Spot standing;
   private int standScanAt;
   private int defuseAt = -1000;
   private int retreatAt = -1000;
   private Boolean crystalWasOn;
   private Boolean anchorWasOn;
   private Boolean totemWasOn;
   private int popStreak;
   private boolean poppedOut;
   private BlockPos digAnchor;
   private boolean digPlaced;
   private boolean digAnnounced;
   private int digGuards;
   private int digUntil;
   private int digWalkUntil;
   private int digBlasts;
   private int digAfter;
   private int belowSince = -1;
   private int stepDigAfter;
   private int baseDigAfter;
   private int diggingAt = -100;
   private boolean eating;
   private boolean needsGapple;
   private boolean mending;
   private BlockPos boxingAt;
   private int boxingUntil;
   private int tick;
   private PlayerEntity fought;
   private CrystalBotModule.Stance stance = CrystalBotModule.Stance.HOLD;
   private int runUntil;
   private int ownPopAt = -1000;
   private CrystalBotModule.Throw throwing;
   private Rotation throwLook;
   private Vec3d throwSpot;
   private double throwAway;
   private int throwUntil;
   private int pearlAt = -1000;
   private int searchAfter;
   private int theirPearl = -1;
   private int followedPearl = -1;
   private boolean launching;
   private int launchUntil;
   private int nextLaunch;
   private int launchedAt = -1;
   private double launchY;
   private double launchPeak;
   private int shieldAt = -1000;
   private BlockPos boxAiming;
   private int boxAimTicks;
   private BlockPos boxSkipped;
   private int boxSkippedUntil;
   private int followUntil;
   private float selfCapNow;
   private boolean followLaid = true;
   private BlockPos blocking;
   private int spotScanAt;
   private int cityFails;
   private int openerArm = -1;
   private double openerReach;
   private int openerAt;
   private float openerHealth;
   private int openerPops;
   private boolean cityStarted;
   private int breachAfter;
   private float lastTheirHealth;
   private int hurtAt;
   private int progressAt;
   private int theirPopAt = -1;
   private int holeSince = -1;
   private boolean lowSpell;
   private boolean pearledInSpell;

   public CrystalBotModule() {
      super(
         "crystal-bot",
         "Crystal PvP Bot",
         "Crystal-fights the nearest enemy player: steers the Crystal and Anchor Aura, knocks them up, breaks their hole, guards, eats, mends, refills and pearls.",
         ModuleCategory.COMBAT
      );
   }

   @Override
   protected void onEnable() {
      this.body.forget();
      this.eating = this.mending = this.launching = this.cityStarted = this.needsGapple = false;
      this.boxingAt = null;
      this.fought = null;
      this.throwing = null;
      this.tick = 0;
      this.runUntil = 0;
      this.nextLaunch = this.followUntil = this.boxSkippedUntil = 0;
      this.launchedAt = -1;
      this.ownPopAt = this.pearlAt = this.shieldAt = -1000;
      this.searchAfter = this.breachAfter = this.spotScanAt = this.standScanAt = this.stepDigAfter = this.baseDigAfter = 0;
      this.defuseAt = this.attackedAt = this.retreatAt = -1000;
      this.attackedBy = -1;
      this.challenger = null;
      this.standing = null;
      this.air.forget();
      this.pause.reset();
      this.crystalWasOn = this.anchorWasOn = this.totemWasOn = null;
      this.popStreak = 0;
      this.forgetDig();
      this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
   }

   @Override
   protected void onDisable() {
      this.body.release(this.mc);
      this.body.forget();
      CombatMacroLock.release(this);
      this.restoreAuras();
      AutoCityModule city = ModuleRegistry.get(AutoCityModule.class);
      if (this.cityStarted && city != null && city.isEnabled()) {
         city.setEnabled(false);
      }

      this.cityStarted = false;
      this.book.save(true);
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null) {
         if (event.packet instanceof EntityStatusS2CPacket status && status.getStatus() == 35) {
            if (status.getEntity(this.mc.world) == player) {
               this.popStreak = this.tick - this.ownPopAt <= 600 ? this.popStreak + 1 : 1;
               this.ownPopAt = this.tick;
            } else if (this.fought != null && status.getEntity(this.mc.world) == this.fought) {
               this.theirPopAt = this.tick;
               this.openerPops++;
            }
         }
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         if (event.packet instanceof PlayerPositionLookS2CPacket moved) {
            Vec3d to = moved.change().position();
            Vec3d from = player.getEntityPos();
            Set<PositionFlag> relative = moved.relatives();
            this.pause
               .moved(
                  new Vec3d(
                     relative.contains(PositionFlag.X) ? from.x + to.x : to.x,
                     relative.contains(PositionFlag.Y) ? from.y + to.y : to.y,
                     relative.contains(PositionFlag.Z) ? from.z + to.z : to.z
                  ),
                  this.tick - this.pearlAt
               );
         } else if (event.packet instanceof WorldTimeUpdateS2CPacket time) {
            this.pause.time(time.time());
         } else if (event.packet instanceof ExplosionS2CPacket blast) {
            this.air.blast(blast.center());
         } else if (event.packet instanceof EntityDamageS2CPacket hurt && hurt.entityId() == player.getId() && hurt.sourceCauseId() >= 0) {
            this.attackedBy = hurt.sourceCauseId();
            this.attackedAt = this.tick;
         }
      }
   }

   private void tick() {
      this.tick++;
      ClientPlayerEntity player = this.mc.player;
      ClientWorld level = this.mc.world;
      this.body.release(this.mc);
      if (player != null && level != null && MeleeAttacks.canAct(this.mc) && !player.isDead()) {
         CombatMacroLock.release(this);
         this.keepTotem();
         this.body.stayGrounded(blastNear(level, player));
         if (this.learn.get()) {
            this.book.save(false);
         }

         BotPause.State state = this.pause.tick(this.mc, player);
         if (state == BotPause.State.STOP) {
            ModuleChat.send(this, ModuleChat.body().danger("The server keeps pulling us back").text(", stopping."));
            this.requestDisable();
         } else if (state != BotPause.State.RUN) {
            this.throwing = null;
            this.launching = this.eating = false;
            if (state == BotPause.State.HOLD) {
               CombatMacroLock.take(this);
            }
         } else {
            boolean regrouping = this.needsGapple || this.eating || this.tick - this.retreatAt < 300;
            PlayerEntity target = this.choose(level, player, this.body.pick(this.mc, player, this.range.get(), regrouping ? 64.0 : this.range.get() + 8.0));
            if (target != this.fought) {
               this.newFight(target);
            }

            boolean low = player.getHealth() <= this.eatAt() && player.getAbsorptionAmount() < 4.0F;
            if (player.getHealth() > this.eatAt() + 4.0F || player.getAbsorptionAmount() >= 4.0F) {
               this.needsGapple = false;
            }

            if (target == null) {
               this.launching = false;
               this.throwing = null;
               CombatMacroLock.release(this);
               if ((low || this.needsGapple || this.eating && player.isUsingItem()) && this.hands() && this.body.use(this.mc, player, GAPPLE)) {
                  this.eating = true;
               } else {
                  this.eating = false;
                  if (!this.refill.get() || !this.kit.refill(this.mc, player, this.anchors.get())) {
                     if (!this.mendIdle(player)) {
                        this.mending = false;
                     }
                  }
               }
            } else {
               RivalBook.Rival rival = this.learn.get() ? this.book.of(target.getUuid()) : null;
               this.watch(level, target, rival);
               if (feetY(player) < MathHelper.floor(target.getY() + 0.01)) {
                  this.body.stayGrounded(true);
               }

               this.air.note(target, this.tick, rival);
               Combo.prefer(player, target);
               Combo.flight(player, target, ahead -> this.air.lead(ahead, this.tick));
               this.runAuras(level);
               CrystalBotModule.Stance was = this.stance;
               this.stance = this.stance(level, player, target, rival);
               if (this.stance == CrystalBotModule.Stance.RUN && was != CrystalBotModule.Stance.RUN) {
                  ModuleChat.send(
                     this,
                     this.poppedOut
                        ? ModuleChat.body().warn("3 totems popped").text(", getting away.")
                        : ModuleChat.body().warn("Losing").text(", getting away.")
                  );
               }

               this.poppedOut = false;
               this.steer(player, target, rival);
               double distance = player.distanceTo(target);
               if (distance < this.closest - 0.5) {
                  this.closest = distance;
                  this.closerAt = this.tick;
               }

               if (this.throwing == null || !this.keepThrowing(player, target)) {
                  if (this.stance != CrystalBotModule.Stance.RUN || !this.getAway(level, player, target)) {
                     if (!this.getDown(level, player, target)) {
                        if (!this.guardAnchors.get() || !this.defuse(level, player) && !this.guard(level, player, target)) {
                           if (!this.launching || !this.launch(player, target)) {
                              if (this.followLaid || this.tick > this.followUntil || !this.followUp(level, player, target)) {
                                 boolean punish = target.isUsingItem()
                                    && target.getActiveItem().contains(DataComponentTypes.FOOD)
                                    && totems(player) >= 1
                                    && player.getHealth() > 8.0F;
                                 if (low && !punish) {
                                    this.needsGapple = true;
                                 }

                                 if (this.needsGapple || this.eating && player.isUsingItem()) {
                                    if (distance >= 8.0) {
                                       if (BotBody.hotbarSlot(player, GAPPLE) >= 0) {
                                          if (this.hands() && this.body.use(this.mc, player, GAPPLE)) {
                                             this.eating = true;
                                          }

                                          this.body.face(this.mc, player, target, 1.0F);
                                          this.body.backOff(this.mc, player);
                                          return;
                                       }
                                    } else if (this.critical(player) && BotBody.hotbarSlot(player, GAPPLE) >= 0) {
                                       this.eating = false;
                                       this.retreatAt = this.tick;
                                       if (this.pearlAway(level, player, target)) {
                                          return;
                                       }

                                       this.body.flee(this.mc, player, target);
                                       return;
                                    }
                                 }

                                 this.eating = false;
                                 if (this.cover(level, player, target, distance)) {
                                    this.body.face(this.mc, player, target, 1.0F);
                                 } else if (this.stance == CrystalBotModule.Stance.GUARD && this.closeSpot(level, player, target)) {
                                    this.body.face(this.mc, player, target, 1.0F);
                                 } else if (!this.breach.get() || !this.breakHole(level, player, target, rival)) {
                                    if (!this.mendArmor.get() || !(distance >= 12.0) || !armorLow(player) || !this.hands() || !this.throwXp(player)) {
                                       this.mending = false;
                                       if (!this.chase.get() || !this.chasePearl(level, player, target, rival)) {
                                          boolean calm = distance >= 8.0 || BotBody.hotbarSlot(player, stack -> stack.isOf(Items.END_CRYSTAL)) < 0;
                                          if (this.refill.get()
                                             && calm
                                             && this.kit.needed(player, this.anchors.get())
                                             && this.hands()
                                             && this.kit.refill(this.mc, player, this.anchors.get())) {
                                             this.body.face(this.mc, player, target, 1.0F);
                                          } else if (!this.finish(player, target)) {
                                             if (!this.launches.get() || !this.startLaunch(player, target, rival)) {
                                                if (!this.digDown(level, player, target, distance) && !this.digBase(level, player, target, distance)) {
                                                   CombatMacroLock.release(this);
                                                   handBack(player);
                                                   this.body.face(this.mc, player, target, 1.0F);
                                                   if (!surrounded(level, player)) {
                                                      if (!this.stayLow(level, player, target, distance)) {
                                                         if (this.tick >= this.standScanAt) {
                                                            this.standScanAt = this.tick + 10;
                                                            this.standing = StandPicker.pick(level, player, target, 2.4, 3.2, 4.0, this.stance.cover);
                                                         }

                                                         if (this.standing == null || this.body.toward(this.mc, player, this.standing.center())) {
                                                            this.standing = null;
                                                            this.body.keepDistance(this.mc, player, target, 2.4, 3.2, 4.0, this.dance.get());
                                                         }
                                                      }
                                                   }
                                                } else {
                                                   this.body.face(this.mc, player, target, 1.0F);
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.body.forget();
         CombatMacroLock.release(this);
         this.throwing = null;
         this.launching = false;
      }
   }

   private PlayerEntity choose(ClientWorld level, ClientPlayerEntity player, PlayerEntity kept) {
      PlayerEntity best = null;
      double bestWorth = Double.NEGATIVE_INFINITY;

      for (PlayerEntity other : enemies(level, player, this.range.get())) {
         double worth = this.worth(player, other);
         if (worth > bestWorth) {
            best = other;
            bestWorth = worth;
         }
      }

      if (kept == null || !kept.isAlive() || this.fought == null) {
         this.challenger = null;
         if (best != null) {
            this.body.hold(best);
         }

         return best;
      } else if (best != null && best != kept) {
         boolean close = player.distanceTo(kept) <= 6.0;
         if (bestWorth < this.worth(player, kept) + (close ? 8.0 : 2.0)) {
            this.challenger = null;
            return kept;
         }

         if (best != this.challenger) {
            this.challenger = best;
            this.challengerSince = this.tick;
            return kept;
         }

         if (this.tick - this.challengerSince < (close ? 20 : 6)) {
            return kept;
         }

         this.challenger = null;
         this.body.hold(best);
         return best;
      } else {
         this.challenger = null;
         return kept;
      }
   }

   private double worth(ClientPlayerEntity player, PlayerEntity other) {
      double distance = player.distanceTo(other);
      double near = distance <= 6.0 ? 1.0 : (distance >= 10.0 ? 0.0 : (10.0 - distance) / 4.0);
      double worth = 4.0 * near - 0.2 * distance + (20.0F - Math.min(20.0F, CrystalDamage.health(other))) / 5.0;
      if (!other.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && !other.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         worth += 1.5;
      }

      if (armorLow(other)) {
         worth++;
      }

      if (other.getId() == this.attackedBy && this.tick - this.attackedAt <= 60 && distance <= 4.5) {
         worth += 4.0;
      }

      return worth;
   }

   private boolean finish(ClientPlayerEntity player, PlayerEntity target) {
      if (this.pause.slowServer() || target.hurtTime > 0 || reach(player, target) > 2.95 || CrystalDamage.health(target) > 3.0F) {
         return false;
      }

      if (!MeleeAttacks.isMeleeWeapon(player.getMainHandStack()) || MeleeAttacks.charge(player) < 0.9F) {
         return false;
      }

      if (!this.hands()) {
         return true;
      }

      this.body.face(this.mc, player, target, 1.0F);
      this.strike(player, target);
      return true;
   }

   private void newFight(PlayerEntity target) {
      if (this.fought != null && this.lowSpell && this.pearledInSpell && this.learn.get()) {
         this.book.of(this.fought.getUuid()).lowSpell(true);
      }

      this.fought = target;
      this.launching = false;
      this.launchedAt = this.theirPopAt = this.holeSince = -1;
      this.lowSpell = this.pearledInSpell = false;
      this.theirPearl = this.followedPearl = -1;
      this.openerArm = -1;
      this.closest = Double.MAX_VALUE;
      this.closerAt = this.tick;
      this.standing = null;
      this.followLaid = true;
      this.blocking = null;
      this.cityFails = 0;
      this.air.forget();
      this.forgetDig();
      this.progressAt = this.hurtAt = this.tick;
      this.lastTheirHealth = target == null ? 0.0F : CrystalDamage.health(target);
      if (target != null) {
         ModuleChat.send(this, ModuleChat.body().text("Fighting ").name(target.getName().getString()).text("."));
         if (this.learn.get()) {
            this.book.of(target.getUuid()).fought();
         }
      } else {
         ModuleChat.send(this, ModuleChat.body().text("No enemy in range, waiting."));
      }
   }

   private CrystalBotModule.Stance stance(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, RivalBook.Rival rival) {
      int totems = totems(player);
      float health = player.getHealth() + player.getAbsorptionAmount();
      float risk = worstThreat(level, player, this.digAnchor) / Math.max(1.0F, health);
      int near = enemiesWithin(level, player, 8.0);
      boolean run = totems == 0 && near >= 2;
      if (run) {
         this.runUntil = Math.max(this.runUntil, this.tick + 40);
      }

      if (this.popStreak >= 3) {
         this.popStreak = 0;
         this.poppedOut = true;
         this.runUntil = this.tick + 200;
      }

      if (this.tick < this.runUntil) {
         return CrystalBotModule.Stance.RUN;
      }

      if (this.tick - this.ownPopAt <= 40) {
         return CrystalBotModule.Stance.GUARD;
      }

      if (this.finishing(target, rival)) {
         return CrystalBotModule.Stance.PRESS;
      }

      boolean guard = health <= this.retreatAt() + 2.0F || totems <= 1 || risk >= 0.35F;
      return guard && health <= 8.0F ? CrystalBotModule.Stance.GUARD : CrystalBotModule.Stance.PRESS;
   }

   private boolean finishing(PlayerEntity target, RivalBook.Rival rival) {
      if (this.theirPopAt >= 0 && !target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         int window = rival == null ? 8 : MathHelper.clamp(rival.retotemWindow(8), 3, 30);
         return this.tick - this.theirPopAt <= window;
      } else {
         return false;
      }
   }

   private void steer(ClientPlayerEntity player, PlayerEntity target, RivalBook.Rival rival) {
      float shift = 2.0F;
      float health = player.getHealth() + player.getAbsorptionAmount();
      float room = health - (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1.0F : 4.0F);
      float selfCap = Math.max(0.5F, Math.min(this.stance.selfCap + shift, room));
      this.selfCapNow = selfCap;
      float min = this.finishing(target, rival) ? 2.0F : this.stance.minDamage;
      if (feetY(player) >= MathHelper.floor(target.getY() + 0.01) && !Combo.inHole(target)) {
         min = Math.min(min, 2.5F);
      }

      CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
      if (crystal != null) {
         crystal.steer(player, min, selfCap);
         crystal.crystalsFrom(player, feetY(player) + 1);
         crystal.fullSpeed(player);
         crystal.buildEagerly(player);
      }

      AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
      if (anchor != null) {
         anchor.steer(player, this.finishing(target, rival) ? 2.0F : this.stance.anchorMin, Math.min(selfCap, 11.0F));
      }
   }

   private float eatAt() {
      return this.eatBelow.get() - 2.0F;
   }

   private float retreatAt() {
      return 4.0F;
   }

   private boolean critical(ClientPlayerEntity player) {
      return player.getHealth() + player.getAbsorptionAmount() <= this.retreatAt() && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
   }

   private static int totems(ClientPlayerEntity player) {
      int count = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1 : 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
            count += inventory.getStack(i).getCount();
         }
      }

      return count;
   }

   private static float worstThreat(ClientWorld level, ClientPlayerEntity player, BlockPos ours) {
      List<PlayerEntity> enemies = enemies(level, player, 16.0);
      float worst = 0.0F;

      for (EndCrystalEntity crystal : level.getNonSpectatingEntities(EndCrystalEntity.class, player.getBoundingBox().expand(12.0))) {
         Vec3d center = crystal.getEntityPos();
         float us = CrystalDamage.estimate(center, player);
         if (us > worst && worthIt(enemies, crystal.getBoundingBox(), center, us, 4.0, 6.0F, null)) {
            worst = us;
         }
      }

      BlockPos feet = player.getBlockPos();

      for (BlockPos pos : BlockPos.iterate(feet.add(-5, -2, -5), feet.add(5, 3, 5))) {
         BlockState state = level.getBlockState(pos);
         if (state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0 && !pos.equals(ours)) {
            BlockPos anchor = pos.toImmutable();
            Vec3d center = Vec3d.ofCenter(anchor);
            float us = CrystalDamage.estimate(center, player, 5.0F, anchor);
            if (us > worst && worthIt(enemies, new Box(anchor), center, us, 5.5, 5.0F, anchor)) {
               worst = us;
            }
         }
      }

      return worst;
   }

   private static boolean worthIt(List<PlayerEntity> enemies, Box source, Vec3d center, float us, double reach, float power, BlockPos ignored) {
      for (PlayerEntity enemy : enemies) {
         Vec3d eye = enemy.getEyePos();
         if (!(eye.squaredDistanceTo(TargetingUtils.closestPoint(eye, source)) > reach * reach) && CrystalDamage.estimate(center, enemy, power, ignored) < us) {
            return true;
         }
      }

      return false;
   }

   private static int enemiesWithin(ClientWorld level, ClientPlayerEntity player, double reach) {
      int count = 0;

      for (PlayerEntity other : level.getPlayers()) {
         if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other) && other.distanceTo(player) <= reach) {
            count++;
         }
      }

      return count;
   }

   private static List<PlayerEntity> enemies(ClientWorld level, ClientPlayerEntity player, double reach) {
      List<PlayerEntity> enemies = new ArrayList<>();

      for (PlayerEntity other : level.getPlayers()) {
         if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other) && other.distanceTo(player) <= reach) {
            enemies.add(other);
         }
      }

      return enemies;
   }

   private void runAuras(ClientWorld level) {
      CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
      if (crystal != null && !crystal.isEnabled()) {
         if (this.crystalWasOn == null) {
            this.crystalWasOn = false;
         }

         crystal.setEnabled(true);
      }

      AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
      boolean wantAnchors = this.anchors.get() && level.getRegistryKey() != World.NETHER;
      if (anchor != null && wantAnchors && !anchor.isEnabled()) {
         if (this.anchorWasOn == null) {
            this.anchorWasOn = false;
         }

         anchor.setEnabled(true);
      }
   }

   private void keepTotem() {
      AutoInvTotemModule inventory = ModuleRegistry.get(AutoInvTotemModule.class);
      AutoTotemModule plain = ModuleRegistry.get(AutoTotemModule.class);
      if (inventory != null && !inventory.isEnabled() && (plain == null || !plain.isEnabled())) {
         if (this.totemWasOn == null) {
            this.totemWasOn = false;
         }

         inventory.setEnabled(true);
      }
   }

   private void restoreAuras() {
      CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
      if (crystal != null && Boolean.FALSE.equals(this.crystalWasOn)) {
         crystal.setEnabled(false);
      }

      AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
      if (anchor != null && Boolean.FALSE.equals(this.anchorWasOn)) {
         anchor.setEnabled(false);
      }

      AutoInvTotemModule totem = ModuleRegistry.get(AutoInvTotemModule.class);
      if (totem != null && Boolean.FALSE.equals(this.totemWasOn)) {
         totem.setEnabled(false);
      }

      this.crystalWasOn = this.anchorWasOn = this.totemWasOn = null;
   }

   private static boolean blastNear(ClientWorld level, ClientPlayerEntity player) {
      for (EndCrystalEntity crystal : level.getNonSpectatingEntities(EndCrystalEntity.class, player.getBoundingBox().expand(7.0))) {
         if (CrystalDamage.estimate(crystal.getEntityPos(), player) >= 1.0F) {
            return true;
         }
      }

      BlockPos feet = player.getBlockPos();
      int reach = (int)Math.ceil(7.0);

      for (BlockPos pos : BlockPos.iterate(feet.add(-reach, -2, -reach), feet.add(reach, 3, reach))) {
         BlockState state = level.getBlockState(pos);
         if (state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0 && Vec3d.ofCenter(pos).distanceTo(player.getEntityPos()) <= 7.0) {
            return true;
         }
      }

      return false;
   }

   private static void handBack(ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      if (!main.isOf(Items.END_CRYSTAL)
         && !main.isOf(Items.OBSIDIAN)
         && !main.isOf(Items.RESPAWN_ANCHOR)
         && !main.isOf(Items.GLOWSTONE)
         && !Crystals.isCombatItem(main)) {
         int slot = BotBody.hotbarSlot(player, stack -> stack.isOf(Items.END_CRYSTAL));
         if (slot < 0) {
            slot = BotBody.hotbarSlot(player, MeleeAttacks::isMeleeWeapon);
         }

         if (slot >= 0) {
            player.getInventory().setSelectedSlot(slot);
         }
      }
   }

   private boolean hands() {
      CombatMacroLock.take(this);
      return !MeleeAttacks.actedThisTick(this.mc.player);
   }

   private void watch(ClientWorld level, PlayerEntity target, RivalBook.Rival rival) {
      float theirs = CrystalDamage.health(target);
      if (theirs < this.lastTheirHealth - 0.5F) {
         this.hurtAt = this.tick;
      }

      if (theirs < this.lastTheirHealth - 0.5F || !Combo.inHole(target)) {
         this.progressAt = this.tick;
      }

      this.lastTheirHealth = theirs;
      if (this.openerArm >= 0 && this.tick - this.openerAt >= 40) {
         float taken = this.openerHealth - theirs + 10.0F * this.openerPops;
         if (rival != null) {
            rival.opened(this.openerArm, this.openerReach, MathHelper.clamp(taken / 12.0F, 0.0F, 1.5F));
         }

         this.openerArm = -1;
      }

      if (this.launchedAt >= 0) {
         this.launchPeak = Math.max(this.launchPeak, target.getY() - this.launchY);
         if (this.tick - this.launchedAt >= 8) {
            if (rival != null) {
               rival.launched(this.launchPeak >= 0.3);
            }

            this.launchedAt = -1;
         }
      }

      if (this.theirPopAt >= 0 && rival != null) {
         if (target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            rival.retotemed(this.tick - this.theirPopAt);
            this.theirPopAt = -1;
         } else if (this.tick - this.theirPopAt > 100) {
            rival.retotemed(100);
            this.theirPopAt = -1;
         }
      } else if (this.theirPopAt >= 0 && this.tick - this.theirPopAt > 100) {
         this.theirPopAt = -1;
      }

      if (Combo.inHole(target)) {
         if (this.holeSince < 0) {
            this.holeSince = this.tick;
         }
      } else if (this.holeSince >= 0) {
         if (rival != null) {
            rival.leftHole(Math.min(600, this.tick - this.holeSince));
         }

         this.holeSince = -1;
      }

      if (!this.lowSpell && theirs <= 8.0F) {
         this.lowSpell = true;
         this.pearledInSpell = false;
      } else if (this.lowSpell && theirs > 12.0F) {
         if (rival != null) {
            rival.lowSpell(this.pearledInSpell);
         }

         this.lowSpell = false;
      }

      for (EnderPearlEntity pearl : level.getNonSpectatingEntities(EnderPearlEntity.class, target.getBoundingBox().expand(4.0))) {
         if (pearl.getOwner() == target && pearl.age <= 2 && pearl.getId() != this.followedPearl) {
            if (this.lowSpell) {
               this.pearledInSpell = true;
            }

            this.theirPearl = pearl.getId();
         }
      }
   }

   private boolean getAway(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      if (player.distanceTo(target) < 12.0 && this.pearlAway(level, player, target)) {
         return true;
      } else if (player.distanceTo(target) >= 16.0 && player.getHealth() <= this.eatAt() + 4.0F && this.hands() && this.body.use(this.mc, player, GAPPLE)) {
         this.eating = true;
         return true;
      } else {
         this.body.flee(this.mc, player, target);
         return true;
      }
   }

   private boolean pearlAway(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      if (this.escape.get() && this.pearlReady(player) && this.tick >= this.searchAfter) {
         List<PlayerEntity> enemies = enemies(level, player, 48.0);
         Rotation look = PearlFlight.escape(player, enemies, 20.0);
         this.throwAway = 16.0;
         if (look == null) {
            look = PearlFlight.escape(player, enemies, 12.0);
            this.throwAway = 12.0;
         }

         if (look == null) {
            this.searchAfter = this.tick + 10;
            return false;
         } else {
            this.startThrow(CrystalBotModule.Throw.ESCAPE, look, null);
            return this.keepThrowing(player, target);
         }
      } else {
         return false;
      }
   }

   private boolean pearlReady(ClientPlayerEntity player) {
      return this.tick - this.pearlAt >= 30
         && BotBody.hotbarSlot(player, PEARL) >= 0
         && !player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))
         && (player.getHealth() + player.getAbsorptionAmount() > 6.0F || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
   }

   private void startThrow(CrystalBotModule.Throw kind, Rotation look, Vec3d spot) {
      this.throwing = kind;
      this.throwLook = look;
      this.throwSpot = spot;
      this.throwUntil = this.tick + 14;
   }

   private boolean keepThrowing(ClientPlayerEntity player, PlayerEntity target) {
      if (this.tick > this.throwUntil || !this.pearlReady(player)) {
         this.throwing = null;
         this.pearlAt = this.tick - 15;
         return false;
      }

      if (!this.hands()) {
         return true;
      }

      Aim.turn(this.mc, this.throwLook.yaw(), this.throwLook.pitch(), 27.0F);
      int slot = BotBody.hotbarSlot(player, PEARL);
      if (player.getInventory().getSelectedSlot() != slot) {
         player.getInventory().setSelectedSlot(slot);
         return true;
      }

      if (!ServerRotation.desynced() && !player.isUsingItem() && MeleeAttacks.handsFree(this.mc, player)) {
         if (player.isOnGround() && !(player.getMovement().horizontalLengthSquared() > 1.0E-4)) {
            float yaw = player.getYaw();
            float pitch = player.getPitch();
            Rotation sent = ServerRotation.commit(player);
            if (!(Math.abs(MathHelper.wrapDegrees(sent.yaw() - yaw)) > 0.01F) && !(Math.abs(sent.pitch() - pitch) > 0.01F)) {
               boolean good = this.throwing == CrystalBotModule.Throw.ESCAPE
                  ? PearlFlight.escapes(player, yaw, pitch, enemies(this.mc.world, player, 48.0), this.throwAway)
                  : PearlFlight.reaches(player, yaw, pitch, this.throwSpot, 0.0, 3.0);
               if (!good) {
                  return true;
               }

               if (this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND).isAccepted()) {
                  player.swingHand(Hand.MAIN_HAND);
                  MeleeAttacks.claimTick(this.mc);
                  this.pearlAt = this.tick;
                  if (this.throwing == CrystalBotModule.Throw.ESCAPE) {
                     ModuleChat.send(this, ModuleChat.body().warn("Pearled away").text("."));
                  } else {
                     ModuleChat.send(this, ModuleChat.body().text("Pearled after ").name(target.getName().getString()).text("."));
                  }
               }

               this.throwing = null;
               return true;
            } else {
               return true;
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private boolean chasePearl(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, RivalBook.Rival rival) {
      if ((this.stance == CrystalBotModule.Stance.PRESS || this.stance == CrystalBotModule.Stance.HOLD) && this.pearlReady(player)) {
         float health = player.getHealth() + player.getAbsorptionAmount();
         if (health < 6.0F) {
            return false;
         }

         int pearls = count(player, PEARL);
         boolean following = this.theirPearl >= 0;
         boolean keepOne = totems(player) == 0 && player.getHealth() <= 12.0F
            || !following && rival != null && rival.pearlsWhenLow() && CrystalDamage.health(target) <= 8.0F;
         if (pearls <= (keepOne ? 1 : 0)) {
            return false;
         }

         Vec3d spot = null;
         if (following) {
            this.followedPearl = this.theirPearl;
            this.theirPearl = -1;
            if (level.getEntityById(this.followedPearl) instanceof EnderPearlEntity pearl) {
               PearlFlight.Landing landing = PearlFlight.fly(level, pearl, pearl.getEntityPos(), pearl.getVelocity());
               if (landing != null && landing.at().distanceTo(player.getEntityPos()) > 8.0) {
                  spot = landing.at();
               }
            }
         }

         double distance = player.distanceTo(target);
         if (spot == null && distance <= this.range.get() + 8.0) {
            Vec3d away = target.getEntityPos().subtract(player.getEntityPos()).multiply(1.0, 0.0, 1.0).normalize();
            Vec3d motion = target.getEntityPos().subtract(target.lastX, target.lastY, target.lastZ);
            boolean running = distance >= 12.0 && motion.x * away.x + motion.z * away.z > 0.12;
            BlockPos under = target.getBlockPos().down();
            boolean standing = !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
            boolean above = distance >= 3.0 && target.getY() - player.getY() >= 3.0 && standing;
            boolean below = distance >= 4.0 && player.getY() - target.getY() >= 4.0 && standing;
            boolean stuck = distance > 6.0 && this.tick - this.closerAt > 60;
            if (running || above || below || stuck) {
               spot = target.getEntityPos().add(motion.multiply(1.0, 0.0, 1.0).multiply(PearlFlight.flightTicks(distance)));
            }

            if (spot == null && !target.isOnGround() && distance >= 6.0) {
               AirTrack.Landing down = this.air.landing(this.tick);
               if (down != null && target.getY() - down.at().y >= 3.0) {
                  spot = down.at();
               }
            }
         }

         if (spot != null && this.tick >= this.searchAfter) {
            Rotation look = PearlFlight.toward(player, spot, 0.8, 3.0);
            if (look == null) {
               this.searchAfter = this.tick + 10;
               return false;
            } else {
               this.startThrow(CrystalBotModule.Throw.CHASE, look, spot);
               return this.keepThrowing(player, target);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean getDown(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      double drop = player.getY() - target.getY();
      double sideways = Math.hypot(target.getX() - player.getX(), target.getZ() - player.getZ());
      if (!(drop < 3.0) && !(sideways > 8.0) && this.stance != CrystalBotModule.Stance.RUN && !this.critical(player)) {
         if (this.belowSince < 0) {
            this.belowSince = this.tick;
         }

         if (this.tick - this.belowSince >= 20 && player.isOnGround() && target.isOnGround()) {
            if (this.digAnchor != null && !level.getBlockState(this.digAnchor).isOf(Blocks.RESPAWN_ANCHOR)) {
               if (this.digPlaced) {
                  this.digBlasts++;
                  this.digWalkUntil = this.tick + 40;
               }

               this.digAnchor = null;
            }

            if (this.tick >= this.digWalkUntil && !openDrop(level, player, target)) {
               boolean blast = drop <= 6.0
                  && this.digBlasts < 2
                  && this.tick >= this.digAfter
                  && this.anchors.get()
                  && level.getRegistryKey() != World.NETHER
                  && BotBody.hotbarSlot(player, ANCHOR) >= 0
                  && BotBody.hotbarSlot(player, GLOWSTONE) >= 0;
               return blast && this.blastDown(level, player, target) ? true : this.pearlDown(player, target);
            } else {
               this.body.face(this.mc, player, target, 1.0F);
               this.body.dropToward(this.mc, player, target.getEntityPos());
               return true;
            }
         } else {
            return false;
         }
      } else {
         this.forgetDig();
         return false;
      }
   }

   private boolean blastDown(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      if (this.digAnchor == null) {
         this.digAnchor = digCell(level, player, target);
         if (this.digAnchor == null) {
            this.digAfter = this.tick + 100;
            return false;
         }

         this.digPlaced = false;
         this.digGuards = 0;
         this.digUntil = this.tick + 80;
         if (!this.digAnnounced) {
            ModuleChat.send(this, ModuleChat.body().text("Blasting down to ").name(target.getName().getString()).text("."));
         }

         this.digAnnounced = true;
      }

      if (this.tick > this.digUntil) {
         this.digBlasts = 2;
         this.digAnchor = null;
         return false;
      }

      this.body.face(this.mc, player, target, 1.0F);
      BlockState state = level.getBlockState(this.digAnchor);
      if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
         AnchorGuard.Spot spot = state.isReplaceable() ? AnchorGuard.supportFor(level, player, player.getEyePos(), this.digAnchor) : null;
         if (spot == null) {
            this.digAnchor = null;
            this.digAfter = this.tick + 100;
            return false;
         }

         if (this.placeAt(level, player, spot, BotBody.hotbarSlot(player, ANCHOR)) == 1) {
            this.digPlaced = true;
         }

         return true;
      } else {
         if (state.get(RespawnAnchorBlock.CHARGES) <= 0) {
            return this.clickDigAnchor(level, player, BotBody.hotbarSlot(player, GLOWSTONE));
         }

         Vec3d aim = AnchorGuard.anchorAim(level, player, this.digAnchor);
         if (aim == null) {
            this.digAnchor = null;
            return false;
         }

         int shieldSlot = guardBlockSlot(player);
         if (this.digGuards < 2 && shieldSlot >= 0) {
            AnchorGuard.Spot shield = AnchorGuard.find(level, player, this.digAnchor, aim);
            if (shield != null && !shield.standing()) {
               if (this.placeAt(level, player, shield, shieldSlot) == 1) {
                  this.digGuards++;
               }

               return true;
            }
         }

         float own = CrystalDamage.estimate(Vec3d.ofCenter(this.digAnchor), player, 5.0F, this.digAnchor);
         int route = AnchorGuard.detonatorRoute(player.getInventory(), player.getMainHandStack());
         if (!(own > Math.min(this.selfCapNow, 10.0F)) && !CrystalDamage.endangers(player, own) && route != -1) {
            return this.clickDigAnchor(level, player, route == -2 ? player.getInventory().getSelectedSlot() : route);
         }

         this.digBlasts = 2;
         this.digAnchor = null;
         return false;
      }
   }

   private static BlockPos digCell(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      Vec3d eye = player.getEyePos();
      double reach = player.getBlockInteractionRange() - 0.5;
      BlockPos feet = player.getBlockPos();
      List<BlockPos> cells = new ArrayList<>();

      for (BlockPos cell : BlockPos.iterate(feet.add(-4, -2, -4), feet.add(4, 0, 4))) {
         if (!(eye.distanceTo(Vec3d.ofCenter(cell)) > reach)) {
            BlockPos under = cell.down();
            if (level.getBlockState(cell).isReplaceable()
               && level.getFluidState(cell).isEmpty()
               && level.getBlockState(under).isSideSolidFullSquare(level, under, Direction.UP)) {
               Box box = new Box(cell);
               if (!box.intersects(player.getBoundingBox()) && !box.intersects(target.getBoundingBox())) {
                  cells.add(cell.toImmutable());
               }
            }
         }
      }

      cells.sort(Comparator.comparingDouble(cellx -> Vec3d.ofCenter(cellx).squaredDistanceTo(target.getEntityPos())));
      BlockPos fallback = null;

      for (BlockPos cell : cells.subList(0, Math.min(cells.size(), 8))) {
         if (AnchorGuard.supportFor(level, player, eye, cell) != null) {
            if (fallback == null) {
               fallback = cell;
            }

            Vec3d sight = TargetingUtils.closestPoint(eye, new Box(cell).contract(0.1));
            if (AnchorGuard.find(level, player, cell, sight) != null) {
               return cell;
            }
         }
      }

      return fallback;
   }

   private static boolean openDrop(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      Vec3d way = target.getEntityPos().subtract(player.getEntityPos()).multiply(1.0, 0.0, 1.0);
      if (way.lengthSquared() < 0.25) {
         return false;
      }

      Vec3d step = player.getEntityPos().add(way.normalize().multiply(0.8));
      BlockPos column = BlockPos.ofFloored(step.x, player.getY() + 0.1, step.z);
      int floor = MathHelper.floor(target.getY() + 0.01);

      for (BlockPos pos = column; pos.getY() >= floor - 1; pos = pos.down()) {
         BlockState state = level.getBlockState(pos);
         if (state.isOf(Blocks.LAVA) || state.isIn(BlockTags.FIRE)) {
            return false;
         }

         if (!state.getCollisionShape(level, pos).isEmpty()) {
            return pos.getY() < player.getY() - 1.5 && pos.getY() <= floor + 1;
         }
      }

      return false;
   }

   private boolean clickDigAnchor(ClientWorld level, ClientPlayerEntity player, int slot) {
      if (slot < 0) {
         this.digAnchor = null;
         return false;
      }

      if (!this.hands()) {
         return true;
      }

      BlockHitResult hit = AnchorGuard.anchorHit(level, player, this.digAnchor);
      if (hit == null) {
         Vec3d aim = AnchorGuard.anchorAim(level, player, this.digAnchor);
         if (aim == null) {
            this.digAnchor = null;
            return false;
         } else {
            ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), aim), 6.0F);
            return true;
         }
      } else {
         if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            return true;
         }

         if (MeleeAttacks.handsFree(this.mc, player)) {
            AnchorGuard.click(this.mc, player, hit);
         }

         return true;
      }
   }

   private boolean pearlDown(ClientPlayerEntity player, PlayerEntity target) {
      if (this.chase.get() && this.pearlReady(player) && this.tick >= this.searchAfter) {
         Vec3d spot = target.getEntityPos();
         Rotation look = PearlFlight.toward(player, spot, 0.8, 3.0);
         if (look == null) {
            this.searchAfter = this.tick + 10;
            return false;
         } else {
            this.startThrow(CrystalBotModule.Throw.CHASE, look, spot);
            return this.keepThrowing(player, target);
         }
      } else {
         return false;
      }
   }

   private static int feetY(ClientPlayerEntity player) {
      return MathHelper.floor(player.getY() + 0.01);
   }

   private boolean wantsLower(ClientPlayerEntity player, PlayerEntity target, double distance) {
      return this.stance != CrystalBotModule.Stance.RUN
         && player.isOnGround()
         && target.isOnGround()
         && !Combo.inHole(target)
         && distance <= 5.5
         && feetY(player) == MathHelper.floor(target.getY() + 0.01)
         && !this.justLaunched();
   }

   private boolean justLaunched() {
      return this.launching || this.launchedAt >= 0 || this.tick <= this.followUntil + 20;
   }

   private boolean stayLow(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, double distance) {
      if (this.stance != CrystalBotModule.Stance.RUN && player.isOnGround() && target.isOnGround() && !Combo.inHole(target) && !(distance > 5.5)) {
         if (feetY(player) < MathHelper.floor(target.getY() + 0.01)) {
            return true;
         }

         if (!this.wantsLower(player, target, distance)) {
            return false;
         }

         BlockPos step = lowerStep(level, player, target, feetY(player) - 1);
         if (step == null) {
            return false;
         }

         this.body.toward(this.mc, player, Vec3d.ofBottomCenter(step));
         return true;
      } else {
         return false;
      }
   }

   private boolean digDown(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, double distance) {
      if (this.wantsLower(player, target, distance) && this.tick >= this.stepDigAfter) {
         if (lowerStep(level, player, target, feetY(player) - 1) != null) {
            return false;
         }

         BlockPos under = player.getBlockPos().down();
         BlockState state = level.getBlockState(under);
         if (BotBody.hotbarSlot(player, OBSIDIAN) < 0 && !Crystals.isBase(state)) {
            return false;
         }

         BlockPos landing = under.down();
         boolean diggable = !state.hasBlockEntity()
            && digTicks(level, player, under) <= 50
            && !level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()
            && !nearLava(level, under);
         if (diggable && this.mine(level, player, under)) {
            return true;
         }

         this.stepDigAfter = this.tick + 100;
         return false;
      } else {
         return false;
      }
   }

   private boolean digBase(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, double distance) {
      int theirs = MathHelper.floor(target.getY() + 0.01);
      if (this.stance != CrystalBotModule.Stance.RUN
         && player.isOnGround()
         && target.isOnGround()
         && !Combo.inHole(target)
         && !(distance > 5.5)
         && feetY(player) == theirs - 1
         && this.tick >= this.baseDigAfter
         && BotBody.hotbarSlot(player, OBSIDIAN) >= 0
         && !this.justLaunched()) {
         Vec3d eye = player.getEyePos();
         BlockPos theirFeet = target.getBlockPos();
         BlockPos best = null;
         double nearest = Double.MAX_VALUE;

         for (BlockPos pos : BlockPos.iterate(theirFeet.add(-2, -1, -2), theirFeet.add(2, -1, 2))) {
            BlockState state = level.getBlockState(pos);
            if (Crystals.isBase(state) && Crystals.canHost(level, pos, null)) {
               return false;
            }

            if ((pos.getX() != theirFeet.getX() || pos.getZ() != theirFeet.getZ())
               && Math.max(Math.abs(pos.getX() - player.getBlockX()), Math.abs(pos.getZ() - player.getBlockZ())) >= 2
               && !state.isReplaceable()
               && !state.hasBlockEntity()
               && level.isAir(pos.up())) {
               Vec3d top = Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0);
               double reach = top.distanceTo(target.getEntityPos());
               if (!(reach > 2.5)
                  && !(eye.distanceTo(top) > player.getBlockInteractionRange() - 0.5)
                  && digTicks(level, player, pos) <= 25
                  && !(reach >= nearest)) {
                  best = pos.toImmutable();
                  nearest = reach;
               }
            }
         }

         if (best != null && this.mine(level, player, best)) {
            return true;
         }

         this.baseDigAfter = this.tick + 100;
         return false;
      } else {
         return false;
      }
   }

   private static BlockPos lowerStep(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, int feet) {
      BlockPos here = player.getBlockPos();
      BlockPos best = null;
      double nearest = Double.MAX_VALUE;

      for (BlockPos cell : BlockPos.iterate(here.add(-2, -1, -2), here.add(2, -1, 2))) {
         BlockPos spot = StandPicker.standable(level, cell);
         if (spot != null && spot.getY() == feet && spot.equals(cell)) {
            Vec3d at = Vec3d.ofBottomCenter(spot);
            double reach = Math.hypot(at.x - target.getX(), at.z - target.getZ());
            if (!(reach < 2.4) && !(reach > 4.5) && walkable(level, player.getEntityPos(), at)) {
               double distance = spot.getSquaredDistance(here);
               if (distance < nearest) {
                  best = spot.toImmutable();
                  nearest = distance;
               }
            }
         }
      }

      return best;
   }

   private static int digTicks(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      float hardness = state.getHardness(level, pos);
      if (hardness < 0.0F) {
         return Integer.MAX_VALUE;
      }

      if (hardness == 0.0F) {
         return 1;
      }

      int slot = toolSlot(player.getInventory(), state);
      ItemStack tool = slot < 0 ? ItemStack.EMPTY : player.getInventory().getStack(slot);
      float speed = tool.getMiningSpeedMultiplier(state);
      if (speed > 1.0F) {
         Registry<Enchantment> enchantments = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
         int efficiency = enchantments.getOptional(Enchantments.EFFICIENCY).map(holder -> EnchantmentHelper.getLevel(holder, tool)).orElse(0);
         if (efficiency > 0) {
            speed += efficiency * efficiency + 1;
         }
      }

      boolean right = tool.isSuitableFor(state) || !state.isToolRequired();
      return MathHelper.ceil(hardness * (right ? 30.0F : 100.0F) / speed);
   }

   private static boolean nearLava(ClientWorld level, BlockPos pos) {
      for (Direction side : Direction.values()) {
         if (level.getFluidState(pos.offset(side)).isIn(FluidTags.LAVA)) {
            return true;
         }
      }

      return false;
   }

   private void forgetDig() {
      this.digAnchor = null;
      this.digPlaced = this.digAnnounced = false;
      this.digGuards = this.digBlasts = 0;
      this.digUntil = this.digWalkUntil = this.digAfter = 0;
      this.belowSince = -1;
   }

   private boolean startLaunch(ClientPlayerEntity player, PlayerEntity target, RivalBook.Rival rival) {
      if (player.age - this.diggingAt <= 1) {
         return false;
      }

      if (this.tick >= this.nextLaunch
         && !this.pause.slowServer()
         && (this.stance == CrystalBotModule.Stance.PRESS || this.stance == CrystalBotModule.Stance.HOLD)
         && (rival == null || !rival.shrugsOffLaunches())) {
         if (target.isOnGround() && !Combo.inHole(target) && (!target.isBlocking() || axeInHotbar(player))) {
            if (!this.mc.world.getNonSpectatingEntities(EndCrystalEntity.class, target.getBoundingBox().expand(3.0)).isEmpty()) {
               return false;
            }

            double reach = reach(player, target);
            if (weaponSlot(player, target.isBlocking()) >= 0 && !(reach > 3.9)) {
               boolean armed = MeleeAttacks.isMeleeWeapon(player.getMainHandStack()) && MeleeAttacks.charge(player) >= 0.5F;
               if (!armed) {
                  int arm = rival != null ? rival.opener(reach) : (this.tick - this.hurtAt >= 20 ? 0 : 1);
                  if (this.openerArm < 0) {
                     this.judgeOpener(arm, reach, target);
                  }

                  if (arm == 1) {
                     this.nextLaunch = this.tick + 40;
                     return false;
                  }
               }

               this.launching = true;
               this.launchUntil = this.tick + 16;
               return this.launch(player, target);
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

   private void judgeOpener(int arm, double reach, PlayerEntity target) {
      this.openerArm = arm;
      this.openerReach = reach;
      this.openerAt = this.tick;
      this.openerHealth = CrystalDamage.health(target);
      this.openerPops = 0;
   }

   private boolean followUp(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      AirTrack.Landing down = this.air.landing(this.tick);
      Vec3d landing = down != null ? down.at() : target.getEntityPos();
      if (this.anchorUnder(level, player, target, landing)) {
         return true;
      }

      int slot = BotBody.hotbarSlot(player, OBSIDIAN);
      if (slot < 0) {
         this.followLaid = true;
         return false;
      }

      BlockPos feet = BlockPos.ofFloored(landing.x, landing.y + 0.2, landing.z);
      CrystalDamage.Victim them = new CrystalDamage.Victim(target, landing.subtract(target.getEntityPos()));
      CrystalDamage.Victim us = new CrystalDamage.Victim(player, Vec3d.ZERO);
      Vec3d eye = player.getEyePos();
      AnchorGuard.Spot best = null;
      float most = 4.0F;

      for (BlockPos base : BlockPos.iterate(feet.add(-1, -1, -1), feet.add(1, 0, 1))) {
         if (base.getX() != feet.getX() || base.getZ() != feet.getZ()) {
            BlockState state = level.getBlockState(base);
            Vec3d center = Crystals.explosionAt(base);
            if (Crystals.isBase(state) && Crystals.canHost(level, base, null)) {
               if (them.damage(center, false) >= most) {
                  this.followLaid = true;
                  return false;
               }
            } else if (state.isReplaceable() && level.isAir(base.up()) && level.isAir(base.up(2))) {
               BlockPos below = base.down();
               if (level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP)
                  && level.canPlace(Blocks.OBSIDIAN.getDefaultState(), base, ShapeContext.absent())
                  && !(eye.distanceTo(Vec3d.ofCenter(base)) > player.getBlockInteractionRange())) {
                  float hurt = them.damage(center, true);
                  float own = us.damage(center, true);
                  if (!(hurt <= most) && !(own > this.selfCapNow) && !CrystalDamage.endangers(player, own)) {
                     AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, eye, base.toImmutable());
                     if (spot != null) {
                        best = spot;
                        most = hurt;
                     }
                  }
               }
            }
         }
      }

      this.body.face(this.mc, player, target, 1.0F);
      if (best == null) {
         if (eye.distanceTo(landing) > 3.5) {
            this.body.drive(this.mc, player, 1, 0.0, true, false);
         }

         return true;
      } else {
         if (this.placeAt(level, player, best, slot) == 1) {
            this.followLaid = true;
         }

         return true;
      }
   }

   private boolean anchorUnder(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, Vec3d landing) {
      if (this.anchors.get() && level.getRegistryKey() != World.NETHER && !target.isOnGround()) {
         int slot = BotBody.hotbarSlot(player, stack -> stack.isOf(Items.RESPAWN_ANCHOR));
         if (slot >= 0 && BotBody.hotbarSlot(player, stack -> stack.isOf(Items.GLOWSTONE)) >= 0) {
            BlockPos cell = BlockPos.ofFloored(landing.x, landing.y + 0.2, landing.z);
            BlockPos below = cell.down();
            if (level.getBlockState(cell).isReplaceable() && level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP)) {
               if (!target.getBoundingBox().intersects(new Box(cell)) && !player.getBoundingBox().intersects(new Box(cell))) {
                  Vec3d eye = player.getEyePos();
                  if (eye.distanceTo(Vec3d.ofCenter(cell)) > player.getBlockInteractionRange()) {
                     return false;
                  }

                  Vec3d blast = Vec3d.ofCenter(cell);
                  float own = CrystalDamage.estimate(blast, player, 5.0F, cell);
                  Vec3d onTop = new Vec3d(landing.x, cell.getY() + 1.0, landing.z);
                  float theirs = new CrystalDamage.Victim(target, onTop.subtract(target.getEntityPos())).damage(blast, 5.0F, cell);
                  if (!(theirs < 6.0F) && !(own > Math.min(this.selfCapNow, 11.0F)) && !CrystalDamage.endangers(player, own)) {
                     AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, eye, cell);
                     if (spot == null) {
                        return false;
                     }

                     this.body.face(this.mc, player, target, 1.0F);
                     if (this.placeAt(level, player, spot, slot) == 1) {
                        this.followLaid = true;
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
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean launch(ClientPlayerEntity player, PlayerEntity target) {
      if (this.tick > this.launchUntil
         || this.stance == CrystalBotModule.Stance.RUN
         || this.stance == CrystalBotModule.Stance.GUARD
         || reach(player, target) > 4.1
         || Combo.inHole(target)) {
         this.launching = false;
         this.nextLaunch = this.tick + 40;
         return false;
      }

      if (!this.hands()) {
         return true;
      }

      int slot = weaponSlot(player, target.isBlocking());
      if (slot < 0) {
         this.launching = false;
         return false;
      }

      if (player.getInventory().getSelectedSlot() != slot) {
         player.getInventory().setSelectedSlot(slot);
      }

      this.body.face(this.mc, player, target, 1.0F);
      float charge = MeleeAttacks.charge(player);
      double reach = reach(player, target);
      Rotation sent = ServerRotation.sent();
      boolean sprinting = player.isSprinting();
      if (charge >= 0.95F && reach <= 2.95 && this.strike(player, target)) {
         this.launching = false;
         this.nextLaunch = this.tick + 80;
         this.launchedAt = this.tick;
         this.launchY = target.getY();
         this.launchPeak = 0.0;
         this.followLaid = false;
         this.followUntil = this.tick + 12;
         this.air.kicked(player, target, sent != null ? sent.yaw() : player.getYaw(), sprinting, this.tick);
         this.body.drive(this.mc, player, 0, 1.0, false, false);
         return true;
      } else {
         this.body.drive(this.mc, player, reach > 2.9 ? 1 : 0, 0.0, charge >= 0.85F, false);
         return true;
      }
   }

   private boolean strike(ClientPlayerEntity player, PlayerEntity target) {
      if (!this.pause.slowServer()
         && !MeleeAttacks.actedThisTick(player)
         && this.mc.attackCooldown <= 0
         && MeleeAttacks.isMeleeWeapon(player.getMainHandStack())) {
         Rotation sent = ServerRotation.sent();
         if (sent == null) {
            return false;
         }

         Vec3d eye = player.getEyePos();
         double reachLimit = Math.min(2.95, player.getEntityInteractionRange());
         Box box = target.getBoundingBox();
         Vec3d at = box.contains(eye) ? eye : box.raycast(eye, eye.add(Vec3d.fromPolar(sent.pitch(), sent.yaw()).multiply(reachLimit))).orElse(null);
         if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
            if (!MeleeAttacks.attack(this.mc, new EntityHitResult(target, at))) {
               return false;
            }

            MeleeAttacks.claimTick(this.mc);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static double reach(ClientPlayerEntity player, PlayerEntity target) {
      Vec3d eye = player.getEyePos();
      return eye.distanceTo(TargetingUtils.closestPoint(eye, target.getBoundingBox()));
   }

   private static boolean axeInHotbar(ClientPlayerEntity player) {
      return BotBody.hotbarSlot(player, stack -> stack.isIn(ItemTags.AXES)) >= 0;
   }

   private static int weaponSlot(ClientPlayerEntity player, boolean shield) {
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      double most = 0.0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = inventory.getStack(i);
         if (MeleeAttacks.isMeleeWeapon(stack) && !stack.isOf(Items.MACE) && (!shield || stack.isIn(ItemTags.AXES))) {
            double damage = MeleeAttacks.attackDamage(stack);
            if (best < 0 || damage > most) {
               best = i;
               most = damage;
            }
         }
      }

      return best;
   }

   private boolean breakHole(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, RivalBook.Rival rival) {
      AutoCityModule city = ModuleRegistry.get(AutoCityModule.class);
      if (city == null) {
         return false;
      }

      if (city.isEnabled()) {
         if (!this.cityStarted) {
            return false;
         }

         CombatMacroLock.release(this);
         this.body.face(this.mc, player, target, 1.0F);
         return true;
      } else {
         if (this.cityStarted) {
            this.cityStarted = false;
            this.breachAfter = this.tick + 100;
            this.progressAt = this.tick;
            if (Combo.inHole(target)) {
               this.cityFails++;
            }
         }

         if (this.stance != CrystalBotModule.Stance.RUN
            && this.tick >= this.breachAfter
            && Combo.inHole(target)
            && !(player.getHealth() + player.getAbsorptionAmount() < 8.0F)) {
            int wait = rival != null && rival.campsInHoles() ? 15 : 40;
            if (this.tick - this.progressAt < wait) {
               return false;
            }

            boolean pickaxe = BotBody.hotbarSlot(player, stack -> stack.isIn(ItemTags.PICKAXES)) >= 0;
            if (this.cityFails >= 2 && pickaxe && this.roofOff(level, player, target)) {
               return true;
            }

            if (this.cityFails >= 2 || !pickaxe) {
               return this.camp(level, player, target);
            }

            if (player.distanceTo(target) > 3.6) {
               this.body.face(this.mc, player, target, 1.0F);
               this.body.keepDistance(this.mc, player, target, 1.6, 2.6, 3.4, false);
               return true;
            }

            city.setEnabled(true);
            if (!city.isEnabled()) {
               return false;
            }

            this.cityStarted = true;
            ModuleChat.send(this, ModuleChat.body().text("Breaking ").name(target.getName().getString()).text("'s hole open."));
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean roofOff(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      if (this.anchors.get()
         && level.getRegistryKey() != World.NETHER
         && BotBody.hotbarSlot(player, stack -> stack.isOf(Items.RESPAWN_ANCHOR)) >= 0
         && BotBody.hotbarSlot(player, stack -> stack.isOf(Items.GLOWSTONE)) >= 0) {
         BlockPos roof = target.getBlockPos().up(2);
         BlockState state = level.getBlockState(roof);
         if (state.isReplaceable() || state.getHardness(level, roof) < 0.0F) {
            return false;
         }

         if (this.mine(level, player, roof)) {
            this.body.face(this.mc, player, target, 1.0F);
            return true;
         }

         BlockPos view = roofView(level, player, roof);
         if (view == null) {
            return false;
         }

         this.body.face(this.mc, player, target, 1.0F);
         this.body.toward(this.mc, player, Vec3d.ofBottomCenter(view));
         return true;
      } else {
         return false;
      }
   }

   private static boolean walkable(ClientWorld level, Vec3d from, Vec3d to) {
      Vec3d way = to.subtract(from);
      int steps = Math.max(1, (int)Math.ceil(way.horizontalLength() / 0.5));
      int lastY = MathHelper.floor(from.y + 0.01);

      for (int i = 1; i <= steps; i++) {
         Vec3d at = from.add(way.multiply((double)i / steps));
         BlockPos feet = StandPicker.standable(level, BlockPos.ofFloored(at.x, lastY, at.z));
         if (feet == null || Math.abs(feet.getY() - lastY) > 1) {
            return false;
         }

         lastY = feet.getY();
      }

      return true;
   }

   private static BlockPos roofView(ClientWorld level, ClientPlayerEntity player, BlockPos roof) {
      BlockPos here = player.getBlockPos();
      BlockPos best = null;
      double nearest = Double.MAX_VALUE;
      double reach = player.getBlockInteractionRange() - 0.2;

      for (BlockPos cell : BlockPos.iterate(here.add(-4, -1, -4), here.add(4, 1, 4))) {
         BlockPos feet = StandPicker.standable(level, cell);
         if (feet != null && feet.equals(cell) && walkable(level, player.getEntityPos(), Vec3d.ofBottomCenter(feet))) {
            Vec3d eye = Vec3d.ofBottomCenter(feet).add(0.0, player.getStandingEyeHeight(), 0.0);
            double distance = feet.getSquaredDistance(here);
            if (!(distance >= nearest)) {
               for (Direction face : Direction.values()) {
                  Vec3d point = Vec3d.ofCenter(roof).offset(face, 0.5);
                  if (!(eye.distanceTo(point) > reach)) {
                     BlockHitResult hit = level.raycast(new RaycastContext(eye, point.offset(face, -0.05), ShapeType.OUTLINE, FluidHandling.NONE, player));
                     if (hit.getType() == Type.BLOCK && hit.getBlockPos().equals(roof) && hit.getSide() == face) {
                        best = feet.toImmutable();
                        nearest = distance;
                        break;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean mine(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      int slot = toolSlot(player.getInventory(), state);
      if (slot < 0) {
         return false;
      }

      BlockHitResult face = visibleFace(level, player, pos);
      if (face == null) {
         return false;
      }

      if (!this.hands()) {
         return true;
      }

      Rotation sent = ServerRotation.sent();
      BlockHitResult held = sent == null ? null : AnchorGuard.trace(level, player, sent);
      if (held == null || !held.getBlockPos().equals(pos)) {
         ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), face.getPos()), 6.0F);
         return true;
      }

      if (player.getInventory().getSelectedSlot() != slot) {
         player.getInventory().setSelectedSlot(slot);
         return true;
      }

      ServerRotation.request(sent);
      if (MeleeAttacks.actedThisTick(player)) {
         return true;
      }

      if (this.mc.interactionManager.updateBlockBreakingProgress(pos, held.getSide())) {
         player.swingHand(Hand.MAIN_HAND);
      }

      MeleeAttacks.noteAction(player);
      this.diggingAt = player.age;
      return true;
   }

   public static boolean digging() {
      CrystalBotModule module = ModuleRegistry.get(CrystalBotModule.class);
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return module != null && module.isEnabled() && player != null && player.age - module.diggingAt <= 1;
   }

   private static int toolSlot(PlayerInventory inventory, BlockState state) {
      int best = -1;
      float fastest = 0.0F;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = inventory.getStack(i);
         boolean fits = stack.isSuitableFor(state) || !state.isToolRequired();
         if (fits && !(stack.getMiningSpeedMultiplier(state) <= fastest)) {
            best = i;
            fastest = stack.getMiningSpeedMultiplier(state);
         }
      }

      return best;
   }

   private static BlockHitResult visibleFace(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      Vec3d eye = player.getEyePos();
      BlockHitResult best = null;
      double nearest = player.getBlockInteractionRange() * player.getBlockInteractionRange();

      for (Direction face : Direction.values()) {
         Vec3d point = Vec3d.ofCenter(pos).offset(face, 0.5);
         double distance = eye.squaredDistanceTo(point);
         if (!(distance > nearest)) {
            BlockHitResult hit = AnchorGuard.trace(level, player, RotationUtils.fromTo(eye, point.offset(face, -0.05)));
            if (hit != null && hit.getBlockPos().equals(pos) && hit.getSide() == face) {
               best = hit;
               nearest = distance;
            }
         }
      }

      return best;
   }

   private boolean camp(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      BlockPos feet = target.getBlockPos();
      if (level.getNonSpectatingEntities(EndCrystalEntity.class, new Box(feet).expand(3.0, 1.0, 3.0)).size() >= 2) {
         return false;
      }

      Vec3d eye = player.getEyePos();
      BlockPos best = null;
      float least = this.selfCapNow - 2.0F;
      double hit = player.getEntityInteractionRange() - 0.1;

      for (BlockPos cell : BlockPos.iterate(feet.add(-2, 0, -2), feet.add(2, 0, 2))) {
         if (Math.max(Math.abs(cell.getX() - feet.getX()), Math.abs(cell.getZ() - feet.getZ())) == 2) {
            BlockPos base = cell.down().toImmutable();
            if (base.getY() >= feetY(player)
               && Crystals.isBase(level.getBlockState(base))
               && Crystals.canHost(level, base, null)
               && !(eye.squaredDistanceTo(TargetingUtils.closestPoint(eye, Crystals.boxOn(base))) > hit * hit)) {
               float own = CrystalDamage.estimate(Crystals.explosionAt(base), player);
               if (!(own > least) && !CrystalDamage.endangers(player, own) && topAim(level, player, base) != null) {
                  best = base;
                  least = own;
               }
            }
         }
      }

      if (best == null) {
         return false;
      }

      this.body.face(this.mc, player, target, 1.0F);
      this.placeCrystal(level, player, best);
      return true;
   }

   private static Vec3d topAim(ClientWorld level, ClientPlayerEntity player, BlockPos base) {
      Vec3d eye = player.getEyePos();
      double[][] spots = new double[][]{{0.5, 0.5}, {0.2, 0.2}, {0.8, 0.2}, {0.2, 0.8}, {0.8, 0.8}};

      for (double[] at : spots) {
         Vec3d point = new Vec3d(base.getX() + at[0], base.getY() + 1.0, base.getZ() + at[1]);
         if (!(eye.distanceTo(point) > player.getBlockInteractionRange() - 0.1)) {
            BlockHitResult hit = AnchorGuard.trace(level, player, RotationUtils.fromTo(eye, point));
            if (hit != null && hit.getBlockPos().equals(base) && hit.getSide() == Direction.UP) {
               return point;
            }
         }
      }

      return null;
   }

   private void placeCrystal(ClientWorld level, ClientPlayerEntity player, BlockPos base) {
      int slot = BotBody.hotbarSlot(player, stack -> stack.isOf(Items.END_CRYSTAL));
      if (slot >= 0 && this.hands()) {
         Rotation sent = ServerRotation.sent();
         BlockHitResult hit = sent == null ? null : AnchorGuard.trace(level, player, sent);
         if (hit == null || !hit.getBlockPos().equals(base) || hit.getSide() != Direction.UP) {
            Vec3d aim = topAim(level, player, base);
            if (aim != null) {
               ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), aim), 6.0F);
            }
         } else if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
         } else if (MeleeAttacks.handsFree(this.mc, player)) {
            if (Crystals.rightClick(this.mc, player, hit)) {
               Crystals.notePlace(player, base);
               MeleeAttacks.claimTick(this.mc);
            }

            ServerRotation.request(sent);
         }
      }
   }

   private boolean closeSpot(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      int slot = guardBlockSlot(player);
      if (slot < 0) {
         return false;
      }

      if (this.blocking == null && this.tick >= this.spotScanAt) {
         this.spotScanAt = this.tick + 5;
         this.blocking = dangerSpot(level, player, target);
      }

      if (this.blocking == null) {
         return false;
      }

      if (level.getBlockState(this.blocking).isReplaceable() && level.canPlace(Blocks.OBSIDIAN.getDefaultState(), this.blocking, ShapeContext.absent())) {
         AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, player.getEyePos(), this.blocking);
         if (spot == null) {
            this.blocking = null;
            return false;
         }

         if (this.placeAt(level, player, spot, slot) == 1) {
            this.blocking = null;
         }

         return true;
      } else {
         this.blocking = null;
         return false;
      }
   }

   private static BlockPos dangerSpot(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      BlockPos feet = player.getBlockPos();
      Vec3d theirEye = target.getEyePos();
      double reach = Math.max(4.6, player.getBlockInteractionRange() + 0.1);
      BlockPos worst = null;
      float first = 0.0F;
      float second = 0.0F;

      for (BlockPos base : BlockPos.iterate(feet.add(-4, -2, -4), feet.add(4, 1, 4))) {
         if (Crystals.isBase(level.getBlockState(base))
            && Crystals.canHost(level, base, null)
            && !(theirEye.distanceTo(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5)) > reach)) {
            Vec3d center = Crystals.explosionAt(base);
            float us = CrystalDamage.estimate(center, player);
            if (!(us <= second) && !(CrystalDamage.estimate(center, target) >= us + 2.0F)) {
               if (us > first) {
                  second = first;
                  first = us;
                  worst = base.up().toImmutable();
               } else {
                  second = us;
               }
            }
         }
      }

      return first >= 8.0F && second <= first / 2.0F ? worst : null;
   }

   private int placeAt(ClientWorld level, ClientPlayerEntity player, AnchorGuard.Spot spot, int slot) {
      if (!this.hands()) {
         return 0;
      }

      Rotation sent = ServerRotation.sent();
      BlockHitResult hit = sent == null ? null : AnchorGuard.hitAlong(level, player, sent, spot);
      if (hit == null) {
         ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), spot.aim()), 6.0F);
         return 0;
      }

      if (player.getInventory().getSelectedSlot() != slot) {
         player.getInventory().setSelectedSlot(slot);
         return 0;
      }

      if (!MeleeAttacks.handsFree(this.mc, player)) {
         return 0;
      }

      AnchorGuard.click(this.mc, player, hit);
      ServerRotation.request(sent);
      return 1;
   }

   private boolean defuse(ClientWorld level, ClientPlayerEntity player) {
      if (this.tick - this.defuseAt >= 10 && level.getRegistryKey() != World.NETHER) {
         BlockPos feet = player.getBlockPos();

         for (BlockPos pos : BlockPos.iterate(feet.add(-5, -2, -5), feet.add(5, 3, 5))) {
            BlockState state = level.getBlockState(pos);
            if (state.isOf(Blocks.RESPAWN_ANCHOR)
               && state.get(RespawnAnchorBlock.CHARGES) > 0
               && player.canInteractWithBlockAt(pos, -0.1)
               && !pos.equals(this.digAnchor)) {
               BlockPos anchor = pos.toImmutable();
               Vec3d center = Vec3d.ofCenter(anchor);
               if (!(CrystalDamage.estimate(center, player, 5.0F, anchor) > Math.max(1.0F, 0.15F * player.getHealth()))) {
                  boolean theirs = false;

                  for (PlayerEntity enemy : enemies(level, player, 16.0)) {
                     theirs |= enemy.getEyePos().distanceTo(center) <= 5.5;
                  }

                  if (theirs) {
                     PlayerInventory inventory = player.getInventory();
                     int route = AnchorGuard.detonatorRoute(inventory, player.getMainHandStack());
                     if (route == -1 || !this.hands()) {
                        return false;
                     }

                     BlockHitResult hit = AnchorGuard.anchorHit(level, player, anchor);
                     if (hit != null) {
                        if (route != -2) {
                           inventory.setSelectedSlot(route);
                           return true;
                        }

                        if (!MeleeAttacks.handsFree(this.mc, player)) {
                           return true;
                        }

                        AnchorGuard.click(this.mc, player, hit);
                        this.defuseAt = this.tick;
                        return true;
                     }

                     Vec3d aim = AnchorGuard.anchorAim(level, player, anchor);
                     if (aim != null) {
                        ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), aim), 6.0F);
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean guard(ClientWorld level, ClientPlayerEntity player, PlayerEntity target) {
      float health = player.getHealth() + player.getAbsorptionAmount();
      BlockPos danger = null;
      float worst = 0.0F;
      BlockPos feet = player.getBlockPos();

      for (BlockPos pos : BlockPos.iterate(feet.add(-5, -2, -5), feet.add(5, 3, 5))) {
         if (level.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR) && !pos.equals(this.digAnchor)) {
            Vec3d center = Vec3d.ofCenter(pos);
            float hurt = CrystalDamage.estimate(center, player, 5.0F, pos.toImmutable());
            if ((!(hurt < Math.max(4.0F, health * 0.3F)) || !(hurt < health - 1.0F)) && !(target.getEyePos().distanceTo(center) > 5.5) && !(hurt <= worst)) {
               worst = hurt;
               danger = pos.toImmutable();
            }
         }
      }

      if (danger == null) {
         return false;
      }

      AnchorGuard.Spot spot = AnchorGuard.find(level, player, danger, player.getEyePos());
      if (spot != null && spot.standing()) {
         return false;
      }

      int slot = guardBlockSlot(player);
      if (spot != null && slot >= 0 && this.tick - this.shieldAt >= 20) {
         this.body.face(this.mc, player, target, 1.0F);
         if (this.placeAt(level, player, spot, slot) == 1) {
            this.shieldAt = this.tick;
         }

         return true;
      } else {
         this.body.face(this.mc, player, target, 1.0F);
         this.body.away(this.mc, player, Vec3d.ofCenter(danger));
         return true;
      }
   }

   private static int guardBlockSlot(ClientPlayerEntity player) {
      int obsidian = BotBody.hotbarSlot(player, OBSIDIAN);
      return obsidian >= 0 ? obsidian : BotBody.hotbarSlot(player, AnchorGuard::isGuardBlock);
   }

   private boolean cover(ClientWorld level, ClientPlayerEntity player, PlayerEntity target, double distance) {
      boolean boxing = this.boxingAt != null && this.boxingAt.equals(player.getBlockPos()) && this.tick < this.boxingUntil;
      boolean wants = player.getHealth() + player.getAbsorptionAmount() <= this.retreatAt() + 4.0F || this.stance == CrystalBotModule.Stance.GUARD;
      if (this.surround.get() && !boxing && wants && player.isOnGround()) {
         this.boxingAt = player.getBlockPos();
         this.boxingUntil = this.tick + 60;
         boxing = true;
      }

      if (!boxing) {
         this.boxingAt = null;
      }

      return this.surround.get() && boxing && player.isOnGround() && this.boxIn(level, player);
   }

   private boolean boxIn(ClientWorld level, ClientPlayerEntity player) {
      List<BlockPos> open = openAroundFeet(level, player);
      if (!open.isEmpty() && BotBody.hotbarSlot(player, OBSIDIAN) >= 0) {
         if (!this.hands()) {
            return true;
         }

         for (BlockPos cell : open) {
            BlockHitResult support = AutoTrapModule.support(level, player, cell);
            if (support != null && (!cell.equals(this.boxSkipped) || this.tick >= this.boxSkippedUntil)) {
               BlockHitResult seen = AutoTrapModule.seenByServer(level, player, support);
               if (seen != null) {
                  int slot = BotBody.hotbarSlot(player, OBSIDIAN);
                  if (player.getInventory().getSelectedSlot() != slot) {
                     player.getInventory().setSelectedSlot(slot);
                     return true;
                  }

                  if (!MeleeAttacks.handsFree(this.mc, player)) {
                     return true;
                  }

                  ActionResult result = this.mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, seen);
                  if (result.isAccepted()) {
                     player.swingHand(Hand.MAIN_HAND);
                     MeleeAttacks.noteAction(player);
                     ServerRotation.request(ServerRotation.sent());
                  }

                  return true;
               }

               this.boxAimTicks = cell.equals(this.boxAiming) ? this.boxAimTicks + 1 : 0;
               this.boxAiming = cell;
               if (this.boxAimTicks < 10) {
                  ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), support.getPos()), 6.0F);
                  return true;
               }

               this.boxSkipped = cell;
               this.boxSkippedUntil = this.tick + 40;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static List<BlockPos> openAroundFeet(ClientWorld level, ClientPlayerEntity player) {
      List<BlockPos> open = new ArrayList<>();

      for (BlockPos cell : ring(player)) {
         BlockState state = level.getBlockState(cell);
         if (state.isReplaceable()
            && !(cell.toCenterPos().distanceTo(player.getEyePos()) > player.getBlockInteractionRange())
            && level.canPlace(Blocks.OBSIDIAN.getDefaultState(), cell, ShapeContext.absent())) {
            open.add(cell);
         }
      }

      return open;
   }

   private static boolean surrounded(ClientWorld level, ClientPlayerEntity player) {
      for (BlockPos cell : ring(player)) {
         if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private static Set<BlockPos> ring(ClientPlayerEntity player) {
      Box box = player.getBoundingBox();
      int feet = MathHelper.floor(box.minY + 0.01);
      Set<Long> columns = new LinkedHashSet<>();

      for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX - 1.0E-4); x++) {
         for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ - 1.0E-4); z++) {
            columns.add(BlockPos.asLong(x, 0, z));
         }
      }

      Set<BlockPos> ring = new LinkedHashSet<>();

      for (long key : columns) {
         BlockPos column = BlockPos.fromLong(key).withY(feet);

         for (Direction side : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos cell = column.offset(side);
            if (!columns.contains(BlockPos.asLong(cell.getX(), 0, cell.getZ()))) {
               ring.add(cell);
            }
         }
      }

      return ring;
   }

   private static boolean armorLow(LivingEntity player) {
      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         if (slot.isArmorSlot()) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() < stack.getMaxDamage() * 0.3F) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean throwXp(ClientPlayerEntity player) {
      if (BotBody.hotbarSlot(player, XP) < 0) {
         return false;
      }

      this.mending = true;
      Aim.turn(this.mc, player.getYaw(), 86.0F, 22.0F);
      if (player.getPitch() > 70.0F) {
         this.body.use(this.mc, player, XP);
      }

      return true;
   }

   private boolean mendIdle(ClientPlayerEntity player) {
      if (this.mendArmor.get() && (armorLow(player) || this.mending) && hasDamagedArmor(player)) {
         CombatMacroLock.take(this);
         return this.throwXp(player);
      } else {
         return false;
      }
   }

   private static boolean hasDamagedArmor(ClientPlayerEntity player) {
      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         if (slot.isArmorSlot() && player.getEquippedStack(slot).isDamaged()) {
            return true;
         }
      }

      return false;
   }

   private static int count(ClientPlayerEntity player, Predicate<ItemStack> want) {
      int count = 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (want.test(inventory.getStack(i))) {
            count += inventory.getStack(i).getCount();
         }
      }

      return count + (want.test(player.getOffHandStack()) ? player.getOffHandStack().getCount() : 0);
   }

   @Environment(EnvType.CLIENT)
   private enum Stance {
      PRESS(3.5F, 10.0F, 5.0F, 0.7),
      HOLD(4.5F, 8.0F, 6.0F, 1.0),
      GUARD(6.0F, 5.0F, 7.0F, 1.6),
      RUN(99.0F, 3.0F, 99.0F, 2.0);

      final float minDamage;
      final float selfCap;
      final float anchorMin;
      final double cover;

      Stance(float minDamage, float selfCap, float anchorMin, double cover) {
         this.minDamage = minDamage;
         this.selfCap = selfCap;
         this.anchorMin = anchorMin;
         this.cover = cover;
      }
   }

   @Environment(EnvType.CLIENT)
   private enum Throw {
      ESCAPE,
      CHASE;
   }
}
