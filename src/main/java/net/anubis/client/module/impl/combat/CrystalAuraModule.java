package net.anubis.client.module.impl.combat;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.PlayerAttackEntityEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.crystal.Combo;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.crystal.HurtClock;
import net.anubis.client.module.impl.combat.crystal.ShotView;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
public final class CrystalAuraModule extends ClientModule implements HudOverlayProvider {
   private static final int COUNTER_CAP = 1000;
   private static final int IDLE_TICKS = 10;
   private static final float FACE_PLACE_HEALTH = 10.0F;
   private static final float FACE_PLACE_DAMAGE = 2.0F;
   private static final float ARMOR_LOW = 0.1F;
   private static final float BREAK_FACTOR = 0.6F;
   private static final double AIM_INSET = 0.1;
   private static final int MAX_BREAK_ATTEMPTS = 3;
   private static final double CHAIN_RANGE = 12.0;
   private static final int WEAPON_SETTLE_TICKS = 2;
   private static final float LOOK_BONUS = 2.0F;
   private static final int MAX_LEAD_TICKS = 5;
   private static final float LOOK_MATCH_DEGREES = 2.0F;
   private static final double PLACE_BREAK_MARGIN = 0.1;
   private static final float ONE_LOOK_BONUS = 2.0F;
   private static final float OBSIDIAN_UPGRADE_BAR = 8.0F;
   private static final float OBSIDIAN_MIN_GAIN = 5.0F;
   private static final float OBSIDIAN_MIN_RATIO = 2.0F;
   private final BooleanSetting requireHold = this.addSetting("require-hold", new BooleanSetting("Require Hold", false));
   private final KeySetting holdKey = this.addSetting("hold-key", new KeySetting("Hold Key", 1003));
   private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final NumberSetting minDamage = this.addSetting("min-damage", new NumberSetting("Min Damage", 4.0F, 0.0F, 20.0F).step(0.5F));
   private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 8.0F, 0.0F, 20.0F).step(0.5F));
   private final BooleanSetting facePlace = this.addSetting("face-place", new BooleanSetting("Face Place", true));
   private final KeySetting forceFacePlace = this.addSetting("force-face-place", new KeySetting("Force Face Place", -1));
   private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Render", true));
   private final ColorSetting renderColor = this.addSetting("render-color", new ColorSetting("Render Color", -2728705));
   private final ShotView view = new ShotView();
   private boolean engaged;
   private int idleTicks;
   private int savedSlot = -1;
   private int ownSlot = -1;
   private int ticksSinceBreak = 1000;
   private int ticksSincePlace = 1000;
   private ItemStack lastMain;
   private int mainAge = 1000;
   private int pendingBreakId = -1;
   private int pendingBreakAge = 1000;
   private BlockPos pendingObsidian;
   private int pendingObsidianAge = 1000;
   private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
   private final Int2IntOpenHashMap doomed = new Int2IntOpenHashMap();

   public CrystalAuraModule() {
      super("crystal-aura", "Crystal Aura", "Places and breaks end crystals for the most damage.", ModuleCategory.COMBAT);
      this.holdKey.visibleWhen(this.requireHold::get);
      this.renderColor.visibleWhen(this.render::get);
   }

   @Override
   protected void onEnable() {
      this.resetState();
   }

   @Override
   protected void onDisable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && !AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
         this.restoreSlot(player);
      }

      this.resetState();
      this.view.clear();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.ageCounters();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.interactionManager != null && !player.isDead()) {
         HurtClock.tick(player);
         this.forgetGone(mc.world);
         ItemStack main = player.getMainHandStack();
         if (main != this.lastMain) {
            this.lastMain = main;
            this.mainAge = 0;
         }

         CrystalAuraModule.Look look = ServerRotation.canRotate(player) ? CrystalAuraModule.Look.SILENT : CrystalAuraModule.Look.CROSSHAIR;
         boolean crystals = !this.requireHold.get() || Crystals.isKeyDown(mc, this.holdKey.value());
         AnchorAuraModule aura = ModuleRegistry.get(AnchorAuraModule.class);
         boolean anchors = aura != null && aura.isEnabled() && look == CrystalAuraModule.Look.SILENT;
         if (MeleeAttacks.canAct(mc) && !player.isSpectator() && (crystals || anchors)) {
            if (!AutoInvTotemModule.wantsHotbar() && !AutoCityModule.mining() && !CombatMacroLock.heldByOther(this)) {
               if (this.ownSlot != -1 && !this.ownsSelection(player)) {
                  this.forgetSlots();
               }

               ItemStack offhand = player.getOffHandStack();
               boolean offhandUse = !Crystals.isCombatItem(offhand)
                  && !offhand.isOf(Items.END_CRYSTAL)
                  && !offhand.isOf(Items.OBSIDIAN);
               if (!MeleeAttacks.rightClickComing(mc) || !offhandUse && !main.isOf(Items.RESPAWN_ANCHOR) && !main.isOf(Items.GLOWSTONE)
                  )
                {
                  if (!MeleeAttacks.actedThisTick(player) && MeleeAttacks.handsFree(mc, player) && (this.ownsSelection(player) || mayTakeHand(player, anchors))
                     )
                   {
                     List<LivingEntity> enemies = Combo.enemies(mc, player);
                     if (enemies.isEmpty()) {
                        this.disengage(mc, player);
                     } else {
                        Rotation fixedLook = null;
                        if (look == CrystalAuraModule.Look.CROSSHAIR) {
                           fixedLook = RotationUtils.entityRotation(player);
                           if (ServerRotation.pending() || !sameLook(ServerRotation.sent(), fixedLook)) {
                              this.idle(mc, player);
                              return;
                           }
                        }

                        int window = Crystals.confirmWindow(mc, player);
                        EndCrystalEntity lastHit = this.lastHit(mc.world, window);
                        CrystalAuraModule.Fight fight = new CrystalAuraModule.Fight(
                           player,
                           mc.world,
                           enemies,
                           Combo.friends(mc, player),
                           window,
                           Crystals.rttTicks(mc, player),
                           fixedLook,
                           look == CrystalAuraModule.Look.SILENT ? 2.0F : 0.0F,
                           Crystals.isKeyDown(mc, this.forceFacePlace.value())
                        );
                        Combo.Shot shot = this.choose(mc, fight, lastHit, crystals, anchors ? aura : null);
                        if (shot == null) {
                           this.idle(mc, player);
                        } else {
                           this.engage();
                           if (shot.plan() instanceof CrystalAuraModule.Plan plan) {
                              if (this.render.get()) {
                                 this.show(fight, plan);
                              }

                              if (!this.perform(mc, player, fight, plan, look) || look != CrystalAuraModule.Look.SILENT) {
                                 return;
                              }

                              CrystalAuraModule.Plan following = this.planCrystal(fight, this.lastHit(mc.world, window), 1, this.stagedBreak(fight));
                              Rotation sent = ServerRotation.sent();
                              ServerRotation.request(following != null && !fight.holds(following) ? fight.lookAt(following.aim()) : sent);
                           } else {
                              aura.show(shot);
                              if (this.fire(mc, player, fight, aura, shot)) {
                                 ServerRotation.request(ServerRotation.sent());
                              }
                           }
                        }
                     }
                  } else {
                     this.idle(mc, player);
                  }
               } else {
                  this.disengage(mc, player);
               }
            } else {
               CombatMacroLock.release(this);
            }
         } else {
            this.disengage(mc, player);
         }
      } else {
         this.disengage(mc, null);
         Crystals.forgetRtt();
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.render.get()) {
         this.view.draw();
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.render.get() && this.view.showing();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      this.view.label(dl, screenW, screenH);
   }

   @EventHandler
   public void onAttack(PlayerAttackEntityEvent event) {
      if (event.target instanceof EndCrystalEntity crystal && crystal.getEntityWorld().isClient()) {
         this.markBreak(crystal);
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (event.packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> packet : bundle.getPackets()) {
               this.note(player, packet);
            }
         } else {
            this.note(player, event.packet);
         }

         HurtClock.note(player, event.packet);
      }
   }

   private void note(ClientPlayerEntity player, Packet<?> packet) {
      if (packet instanceof EntitySpawnS2CPacket spawn && spawn.getEntityType() == EntityType.END_CRYSTAL) {
         Crystals.noteSpawn(player, BlockPos.ofFloored(spawn.getX(), spawn.getY() - 1.0, spawn.getZ()));
      }
   }

   private static boolean mayTakeHand(ClientPlayerEntity player, boolean anchors) {
      ItemStack main = player.getMainHandStack();
      return main.isOf(Items.END_CRYSTAL)
         || main.isOf(Items.OBSIDIAN)
         || Crystals.isCombatItem(main)
         || anchors && (main.isOf(Items.RESPAWN_ANCHOR) || main.isOf(Items.GLOWSTONE));
   }

   private boolean ownsSelection(ClientPlayerEntity player) {
      return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
   }

   private void engage() {
      this.engaged = true;
      this.idleTicks = 0;
      CombatMacroLock.tryAcquire(this);
   }

   private void idle(MinecraftClient mc, ClientPlayerEntity player) {
      if (!this.engaged || ++this.idleTicks >= 10) {
         this.disengage(mc, player);
      }
   }

   private void disengage(MinecraftClient mc, ClientPlayerEntity player) {
      this.engaged = false;
      this.idleTicks = 0;
      CombatMacroLock.release(this);
      if (player == null) {
         this.forgetSlots();
      } else if (mc.currentScreen == null && !AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar() && !MeleeAttacks.actedThisTick(player)) {
         this.restoreSlot(player);
      }
   }

   private void restoreSlot(ClientPlayerEntity player) {
      if (this.ownSlot != -1) {
         PlayerInventory inventory = player.getInventory();
         if (this.switchBack.get() && PlayerInventory.isValidHotbarIndex(this.savedSlot) && this.savedSlot != this.ownSlot && inventory.getSelectedSlot() == this.ownSlot) {
            inventory.setSelectedSlot(this.savedSlot);
         }

         this.forgetSlots();
      }
   }

   private void forgetSlots() {
      this.savedSlot = -1;
      this.ownSlot = -1;
   }

   private void resetState() {
      this.engaged = false;
      this.idleTicks = 0;
      this.forgetSlots();
      this.ticksSinceBreak = 1000;
      this.ticksSincePlace = 1000;
      this.lastMain = null;
      this.mainAge = 1000;
      this.pendingBreakId = -1;
      this.pendingBreakAge = 1000;
      this.pendingObsidian = null;
      this.pendingObsidianAge = 1000;
      this.breakAttempts.clear();
      this.doomed.clear();
      Crystals.forgetRtt();
      CombatMacroLock.release(this);
   }

   private void forgetGone(ClientWorld level) {
      forgetGone(level, this.breakAttempts.keySet());
      forgetGone(level, this.doomed.keySet());
   }

   private static void forgetGone(ClientWorld level, IntSet ids) {
      IntIterator iterator = ids.iterator();

      while (iterator.hasNext()) {
         if (level.getEntityById(iterator.nextInt()) == null) {
            iterator.remove();
         }
      }
   }

   private static boolean oneLook(Vec3d eye, Box crystal, Vec3d placeAim, double reach) {
      Vec3d direction = placeAim.subtract(eye).normalize();
      return crystal.raycast(eye, eye.add(direction.multiply(reach))).isPresent();
   }

   private boolean canHostSoon(CrystalAuraModule.Fight fight, BlockPos base, Entity breaking) {
      BlockPos above = base.up();
      if (!fight.level.isAir(above)) {
         return false;
      }

      Box space = new Box(
         above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0
      );

      for (Entity entity : fight.level.getOtherEntities(breaking, space)) {
         if (entity.isAlive()) {
            if (!(entity instanceof LivingEntity living) || !fight.enemies.contains(living)) {
               return false;
            }

            Box then = living.getBoundingBox().offset(Combo.lead(living, fight.arriveLead));
            boolean over = then.minY >= space.maxY;
            boolean aside = then.maxX <= space.minX
               || then.minX >= space.maxX
               || then.maxZ <= space.minZ
               || then.minZ >= space.maxZ;
            if (!over && !aside) {
               return false;
            }
         }
      }

      return true;
   }

   private EndCrystalEntity lastHit(ClientWorld level, int window) {
      if (this.pendingBreakId >= 0 && this.pendingBreakAge < window) {
         return level.getEntityById(this.pendingBreakId) instanceof EndCrystalEntity crystal && crystal.isAlive() ? crystal : null;
      } else {
         return null;
      }
   }

   private Combo.Shot choose(MinecraftClient mc, CrystalAuraModule.Fight fight, EndCrystalEntity lastHit, boolean crystals, AnchorAuraModule aura) {
      List<Combo.Shot> shots = new ArrayList<>();
      Combo.Shot aimed = null;
      float rival = 0.0F;
      if (crystals) {
         CrystalAuraModule.Plan opening = this.stagedBreak(fight);
         CrystalAuraModule.Plan plan = this.planCrystal(fight, lastHit, 0, opening);
         if (plan != null) {
            aimed = this.shot(fight, plan, false);
            if (this.due(plan, 0)) {
               shots.add(aimed);
            }
         }

         if (aura != null && opening != null) {
            rival = this.shot(fight, opening, true).value();
         }
      }

      if (aura != null) {
         shots.addAll(aura.shots(mc, fight.player, fight.enemies, fight.friends, fight.rtt, rival));
      }

      Combo.Shot picked = Combo.pick(shots);
      return picked != null ? picked : aimed;
   }

   private void show(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
      CrystalAuraModule.Side side = fight.side(plan.step());
      boolean baseToCome = plan.step() == CrystalAuraModule.Step.OBSIDIAN;
      Vec3d center = plan.step() == CrystalAuraModule.Step.BREAK ? plan.crystal().getEntityPos() : Crystals.explosionAt(plan.target());
      float most = 0.0F;

      for (CrystalDamage.Victim enemy : side.enemies) {
         most = Math.max(most, enemy.damage(center, baseToCome));
      }

      BlockPos base = plan.step() == CrystalAuraModule.Step.BREAK ? plan.crystal().getBlockPos().down() : plan.target();
      this.view.show(new Box(base), this.renderColor.argb(), most, side.self.damage(center, baseToCome));
   }

   private CrystalAuraModule.Plan planCrystal(CrystalAuraModule.Fight fight, EndCrystalEntity lastHit, int ahead, CrystalAuraModule.Plan opening) {
      CrystalAuraModule.Plan breakPlan = this.planBreak(fight, false);
      if (breakPlan != null && this.due(breakPlan, ahead)) {
         return breakPlan;
      }

      boolean staged = breakPlan != null || opening != null || Crystals.anyPlacing(fight.player, fight.window);
      CrystalAuraModule.Plan placePlan = staged ? null : this.planPlacement(fight, lastHit, lastHit == null);
      return placePlan != null ? placePlan : breakPlan;
   }

   private CrystalAuraModule.Plan stagedBreak(CrystalAuraModule.Fight fight) {
      return this.planBreak(fight, true);
   }

   private Combo.Shot shot(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan, boolean opening) {
      CrystalAuraModule.Step step = plan.step();
      Vec3d center = step == CrystalAuraModule.Step.BREAK ? plan.crystal().getEntityPos() : Crystals.explosionAt(plan.target());
      int readyIn = step == CrystalAuraModule.Step.BREAK ? 0 : (step == CrystalAuraModule.Step.CRYSTAL ? fight.rtt : fight.rtt + 1);
      return new Combo.Shot(
         Combo.Kind.valueOf(step.name()),
         plan.aim(),
         plan.route(),
         this.score(fight, step, center, Float.NEGATIVE_INFINITY, opening),
         kills(fight, step, center, opening),
         readyIn,
         plan
      );
   }

   private CrystalAuraModule.Plan planBreak(CrystalAuraModule.Fight fight, boolean opening) {
      double reach = fight.player.getEntityInteractionRange();
      CrystalAuraModule.Plan best = null;
      float bestScore = Float.NEGATIVE_INFINITY;
      Box around = fight.player.getBoundingBox().expand(reach + 2.0);

      for (EndCrystalEntity crystal : fight.level.getNonSpectatingEntities(EndCrystalEntity.class, around)) {
         if (crystal.isAlive()
            && this.breakAttempts.get(crystal.getId()) < 3
            && (crystal.getId() != this.pendingBreakId || this.pendingBreakAge >= fight.window)
            && (!this.doomed.containsKey(crystal.getId()) || fight.player.age - this.doomed.get(crystal.getId()) >= fight.window)) {
            Vec3d eye = fight.eye;
            if (!(eye.squaredDistanceTo(TargetingUtils.closestPoint(eye, crystal.getBoundingBox())) > reach * reach)) {
               Vec3d center = crystal.getEntityPos();
               if (!(this.bound(fight, CrystalAuraModule.Step.BREAK, center) + fight.lookBonus <= bestScore)) {
                  CrystalAuraModule.Plan plan = this.breakLook(fight, crystal);
                  if (plan != null) {
                     float score = this.score(fight, CrystalAuraModule.Step.BREAK, center, bestScore - fight.lookBonus, opening);
                     if (score != Float.NEGATIVE_INFINITY) {
                        score += fight.lookCredit(plan);
                        if (!(score <= bestScore)) {
                           best = plan;
                           bestScore = score;
                        }
                     }
                  }
               }
            }
         }
      }

      if (best == null) {
         return null;
      }

      int weapon = Crystals.weaponRoute(fight.player);
      return weapon == -1 ? null : best.withRoute(weapon);
   }

   private CrystalAuraModule.Plan breakLook(CrystalAuraModule.Fight fight, EndCrystalEntity crystal) {
      if (fight.fixedLook != null) {
         CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, crystal.getBoundingBox().getCenter(), -2);
         return hitAlong(fight.player, plan, fight.eye, fight.fixedLook) != null ? plan : null;
      }

      BlockPos base = crystal.getBlockPos().down();
      if (Crystals.isBase(fight.level.getBlockState(base))) {
         CrystalAuraModule.Plan throughBase = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, base, base, aimOnTop(fight.level, base), -2);
         if (hitAlong(fight.player, throughBase, fight.aimEye, fight.lookAt(throughBase.aim())) != null) {
            return throughBase;
         }
      }

      Vec3d nearest = TargetingUtils.closestPoint(fight.aimEye, crystal.getBoundingBox().contract(0.1));
      CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, nearest, -2);
      return hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(nearest)) != null ? plan : null;
   }

   private CrystalAuraModule.Plan planPlacement(CrystalAuraModule.Fight fight, EndCrystalEntity breaking, boolean mayLayBase) {
      CrystalAuraModule.Plan crystal = this.planBase(fight, CrystalAuraModule.Step.CRYSTAL, breaking);
      if (this.autoObsidian.get() && mayLayBase) {
         float crystalDamage = crystal == null ? 0.0F : this.enemyDamage(fight, crystal);
         if (crystal != null && crystalDamage >= 8.0F) {
            return crystal;
         } else {
            CrystalAuraModule.Plan obsidian = this.planBase(fight, CrystalAuraModule.Step.OBSIDIAN, null);
            if (obsidian != null && crystal != null) {
               float obsidianDamage = this.enemyDamage(fight, obsidian);
               boolean upgrade = obsidianDamage >= crystalDamage + 5.0F && obsidianDamage >= crystalDamage * 2.0F;
               return upgrade ? obsidian : crystal;
            } else {
               return obsidian == null ? crystal : obsidian;
            }
         }
      } else {
         return crystal;
      }
   }

   private float enemyDamage(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
      Vec3d center = Crystals.explosionAt(plan.target());
      float most = 0.0F;

      for (CrystalDamage.Victim enemy : fight.side(plan.step()).enemies) {
         most = Math.max(most, enemy.damage(center, plan.step() == CrystalAuraModule.Step.OBSIDIAN));
      }

      return most;
   }

   private CrystalAuraModule.Plan planBase(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, EndCrystalEntity breaking) {
      ClientPlayerEntity player = fight.player;
      ClientWorld level = fight.level;
      Item item = step == CrystalAuraModule.Step.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
      BlockState anyBase = Blocks.OBSIDIAN.getDefaultState();
      if (Crystals.route(player, item, anyBase, this.savedSlot) == -1) {
         return null;
      }

      if (step == CrystalAuraModule.Step.OBSIDIAN && Crystals.route(player, Items.END_CRYSTAL, anyBase, this.savedSlot) == -1) {
         return null;
      }

      double placeReach = player.getBlockInteractionRange();
      double breakable = player.getEntityInteractionRange() - 0.1;
      Vec3d eye = fight.eye;
      BlockPos origin = BlockPos.ofFloored(eye);
      int radius = MathHelper.ceil(placeReach);
      float cycle = step == CrystalAuraModule.Step.CRYSTAL && fight.lookBonus > 0.0F ? 2.0F : 0.0F;
      List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
      Mutable cell = new Mutable();
      Mutable below = new Mutable();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               cell.set(origin, dx, dy, dz);
               BlockState state = level.getBlockState(cell);
               if (step == CrystalAuraModule.Step.CRYSTAL ? Crystals.isBase(state) : state.isReplaceable()) {
                  below.set(cell, Direction.DOWN);
                  if ((step != CrystalAuraModule.Step.OBSIDIAN || level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP))
                     && (
                        step == CrystalAuraModule.Step.CRYSTAL
                           ? !Crystals.placing(player, cell, fight.window)
                           : !cell.equals(this.pendingObsidian) || this.pendingObsidianAge >= fight.window
                     )) {
                     BlockPos base = cell.toImmutable();
                     BlockPos clicked = step == CrystalAuraModule.Step.CRYSTAL ? base : below.toImmutable();
                     Vec3d aim = aimOnTop(level, clicked);
                     if (!(eye.squaredDistanceTo(aim) > placeReach * placeReach)) {
                        Box crystal = Crystals.boxOn(base);
                        if (!(eye.squaredDistanceTo(TargetingUtils.closestPoint(eye, crystal)) > breakable * breakable)) {
                           float bound = this.bound(fight, step, Crystals.explosionAt(base));
                           if (bound != Float.NEGATIVE_INFINITY) {
                              candidates.add(new CrystalAuraModule.Candidate(new CrystalAuraModule.Plan(step, null, clicked, base, aim, -2), bound));
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
      CrystalAuraModule.Plan best = null;
      float bestScore = Float.NEGATIVE_INFINITY;

      for (CrystalAuraModule.Candidate candidate : candidates) {
         if (candidate.bound() + fight.lookBonus + cycle <= bestScore) {
            break;
         }

         CrystalAuraModule.Plan found = candidate.plan();
         float score = this.score(fight, step, Crystals.explosionAt(found.target()), bestScore - fight.lookBonus - cycle, false);
         if (score != Float.NEGATIVE_INFINITY
            && level.getWorldBorder().contains(found.clicked())
            && this.canHostSoon(fight, found.target(), step == CrystalAuraModule.Step.CRYSTAL ? breaking : null)) {
            CrystalAuraModule.Plan plan = this.reachable(fight, found);
            if (plan != null) {
               score += fight.lookCredit(plan);
               if (cycle > 0.0F && oneLook(fight.aimEye, Crystals.boxOn(plan.target()), plan.aim(), breakable)) {
                  score += cycle;
               }

               if (!(score <= bestScore)) {
                  best = plan;
                  bestScore = score;
               }
            }
         }
      }

      if (best == null) {
         return null;
      }

      int route = Crystals.route(player, item, level.getBlockState(best.clicked()), this.savedSlot);
      return route == -1 ? null : best.withRoute(route);
   }

   private CrystalAuraModule.Plan reachable(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
      if (hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(plan.aim())) != null) {
         return plan;
      }

      if (plan.step() == CrystalAuraModule.Step.CRYSTAL && fight.fixedLook == null) {
         Vec3d middle = Vec3d.ofCenter(plan.clicked());

         for (Direction side : Type.HORIZONTAL) {
            Vec3d face = middle.add(side.getOffsetX() * 0.4, 0.0, side.getOffsetZ() * 0.4);
            if (!((fight.aimEye.x - face.x) * side.getOffsetX() + (fight.aimEye.z - face.z) * side.getOffsetZ() <= 0.0)
               )
             {
               CrystalAuraModule.Plan aimed = new CrystalAuraModule.Plan(plan.step(), null, plan.clicked(), plan.target(), face, plan.route());
               if (hitAlong(fight.player, aimed, fight.aimEye, fight.lookAt(face)) != null) {
                  return aimed;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Vec3d aimOnTop(ClientWorld level, BlockPos pos) {
      double top = level.getBlockState(pos).getOutlineShape(level, pos).getMax(Axis.Y);
      return new Vec3d(pos.getX() + 0.5, pos.getY() + top - 0.1, pos.getZ() + 0.5);
   }

   private float need(LivingEntity target, boolean breaking, boolean forced) {
      float damage = !forced && (!this.facePlace.get() || !facePlaced(target)) ? this.minDamage.get() : 2.0F;
      return breaking ? damage * 0.6F : damage;
   }

   private static boolean facePlaced(LivingEntity target) {
      return CrystalDamage.health(target) <= 10.0F || Combo.inHole(target) || armorLow(target);
   }

   private static boolean armorLow(LivingEntity target) {
      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         if (slot.isArmorSlot()) {
            ItemStack stack = target.getEquippedStack(slot);
            if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= stack.getMaxDamage() * 0.1F) {
               return true;
            }
         }
      }

      return false;
   }

   private float bound(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3d center) {
      float best = Float.NEGATIVE_INFINITY;

      for (CrystalDamage.Victim enemy : fight.side(step).enemies) {
         LivingEntity entity = enemy.entity();
         float upper = enemy.upperBound(center);
         if (Combo.kills(upper, entity) || !(upper < this.need(entity, step == CrystalAuraModule.Step.BREAK, fight.forced))) {
            best = Math.max(best, Combo.value(upper, entity));
         }
      }

      return best;
   }

   private float score(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3d center, float toBeat, boolean opening) {
      CrystalAuraModule.Side side = fight.side(step);
      boolean baseToCome = step == CrystalAuraModule.Step.OBSIDIAN;
      float selfDamage = side.self.damage(center, baseToCome);
      if (!(selfDamage > this.maxSelfDamage.get()) && !CrystalDamage.endangers(fight.player, selfDamage)) {
         for (CrystalDamage.Victim friend : side.friends) {
            float damage = friend.damage(center, baseToCome);
            if (damage > this.maxSelfDamage.get() || CrystalDamage.endangers((PlayerEntity)friend.entity(), damage)) {
               return Float.NEGATIVE_INFINITY;
            }
         }

         float best = Float.NEGATIVE_INFINITY;

         for (CrystalDamage.Victim enemy : side.enemies) {
            LivingEntity entity = enemy.entity();
            float effective = effective(fight, enemy, step, center, opening);
            if (Combo.kills(effective, entity) || !(effective < this.need(entity, step == CrystalAuraModule.Step.BREAK, fight.forced))) {
               best = Math.max(best, Combo.value(effective, entity) - 0.5F * selfDamage);
            }
         }

         return best > toBeat ? best : Float.NEGATIVE_INFINITY;
      } else {
         return Float.NEGATIVE_INFINITY;
      }
   }

   private static boolean kills(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3d center, boolean opening) {
      for (CrystalDamage.Victim enemy : fight.side(step).enemies) {
         if (Combo.kills(effective(fight, enemy, step, center, opening), enemy.entity())) {
            return true;
         }
      }

      return false;
   }

   private static float effective(CrystalAuraModule.Fight fight, CrystalDamage.Victim enemy, CrystalAuraModule.Step step, Vec3d center, boolean opening) {
      int ahead = step == CrystalAuraModule.Step.BREAK ? (opening ? HurtClock.opensIn(enemy.entity(), fight.rtt) : 0) : fight.rtt;
      return HurtClock.effective(enemy, enemy.raw(center, step == CrystalAuraModule.Step.OBSIDIAN), ahead, fight.rtt);
   }

   private static HitResult hitAlong(ClientPlayerEntity player, CrystalAuraModule.Plan plan, Vec3d eye, Rotation look) {
      if (look == null) {
         return null;
      }

      Vec3d direction = Vec3d.fromPolar(look.pitch(), look.yaw());
      if (plan.step() == CrystalAuraModule.Step.BREAK) {
         Box box = plan.crystal().getBoundingBox();
         if (box.contains(eye)) {
            return new EntityHitResult(plan.crystal(), eye);
         }

         Vec3d at = (Vec3d)box.raycast(eye, eye.add(direction.multiply(player.getEntityInteractionRange()))).orElse(null);
         return at != null && MeleeAttacks.lineIsClear(player, eye, at) ? new EntityHitResult(plan.crystal(), at) : null;
      } else {
         ClientWorld level = (ClientWorld)player.getEntityWorld();
         BlockState clicked = level.getBlockState(plan.clicked());
         BlockHitResult hit = clicked.getOutlineShape(level, plan.clicked())
            .raycast(eye, eye.add(direction.multiply(player.getBlockInteractionRange())), plan.clicked());
         if (hit != null && MeleeAttacks.lineIsClear(player, eye, hit.getPos())) {
            return plan.step() == CrystalAuraModule.Step.OBSIDIAN && !plan.target().equals(Crystals.placedObsidian(level, player, hit, clicked)) ? null : hit;
         } else {
            return null;
         }
      }
   }

   private boolean perform(MinecraftClient mc, ClientPlayerEntity player, CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan, CrystalAuraModule.Look look) {
      Vec3d eye = player.getEyePos();
      Rotation sent = ServerRotation.sent();
      Rotation next = look == CrystalAuraModule.Look.SILENT ? sent : RotationUtils.entityRotation(player);
      HitResult hit = sent == null ? null : hitAlong(player, plan, eye, sent);
      if (hit != null && next != sent && hitAlong(player, plan, eye, next) == null) {
         hit = null;
      }

      if (plan.route() != -2) {
         this.select(player, plan.route());
      }

      boolean acted = hit != null
         && this.due(plan, 0)
         && (plan.step() == CrystalAuraModule.Step.BREAK ? this.strike(mc, player, fight, plan.crystal()) : this.place(mc, player, plan, (BlockHitResult)hit));
      if (acted) {
         MeleeAttacks.claimTick(mc);
      } else if (look == CrystalAuraModule.Look.SILENT) {
         ServerRotation.request(hit != null ? sent : fight.lookAt(plan.aim()));
      }

      return acted;
   }

   private boolean fire(MinecraftClient mc, ClientPlayerEntity player, CrystalAuraModule.Fight fight, AnchorAuraModule aura, Combo.Shot shot) {
      if (shot.route() != -2) {
         this.select(player, shot.route());
      }

      if (!aura.ready(mc, player, shot)) {
         ServerRotation.request(fight.lookAt(shot.aim()));
         return false;
      } else {
         aura.fire(mc, player, shot);
         return true;
      }
   }

   private boolean strike(MinecraftClient mc, ClientPlayerEntity player, CrystalAuraModule.Fight fight, EndCrystalEntity crystal) {
      if (this.mainAge < 2 && !Crystals.enchantsAddDamage(player.getMainHandStack()) && !Crystals.dealsDamage(player, ItemStack.EMPTY)) {
         return false;
      }

      if (!Crystals.strike(mc, player, crystal)) {
         return false;
      }

      this.markBreak(crystal);
      this.breakAttempts.addTo(crystal.getId(), 1);
      this.ticksSinceBreak = 0;

      for (CrystalDamage.Victim enemy : fight.side(CrystalAuraModule.Step.BREAK).enemies) {
         float raw = enemy.raw(crystal.getEntityPos(), false);
         if (raw > 0.0F) {
            HurtClock.shot(player, enemy.entity(), raw, crystal.getId(), fight.rtt);
         }
      }

      Box blast = crystal.getBoundingBox().expand(12.0);

      for (EndCrystalEntity other : fight.level.getNonSpectatingEntities(EndCrystalEntity.class, blast)) {
         if (other != crystal && other.squaredDistanceTo(crystal) <= 144.0) {
            this.doomed.put(other.getId(), player.age);
         }
      }

      return true;
   }

   private boolean place(MinecraftClient mc, ClientPlayerEntity player, CrystalAuraModule.Plan plan, BlockHitResult hit) {
      if (!Crystals.rightClick(mc, player, hit)) {
         return false;
      }

      if (plan.step() == CrystalAuraModule.Step.CRYSTAL) {
         Crystals.notePlace(player, plan.target());
      } else {
         this.pendingObsidian = plan.target();
         this.pendingObsidianAge = 0;
      }

      this.ticksSincePlace = 0;
      return true;
   }

   private static boolean sameLook(Rotation a, Rotation b) {
      return a != null && Math.abs(RotationUtils.yawDelta(a.yaw(), b.yaw())) <= 2.0F && Math.abs(a.pitch() - b.pitch()) <= 2.0F;
   }

   private boolean due(CrystalAuraModule.Plan plan, int ahead) {
      return plan.step() == CrystalAuraModule.Step.BREAK
         ? this.ticksSinceBreak + ahead > this.breakDelay.getInt()
         : this.ticksSincePlace + ahead > this.placeDelay.getInt();
   }

   private void select(ClientPlayerEntity player, int slot) {
      PlayerInventory inventory = player.getInventory();
      if (this.savedSlot == -1) {
         this.savedSlot = inventory.getSelectedSlot();
      }

      inventory.setSelectedSlot(slot);
      this.ownSlot = slot;
      this.lastMain = player.getMainHandStack();
      this.mainAge = 0;
   }

   private void markBreak(EndCrystalEntity crystal) {
      this.pendingBreakId = crystal.getId();
      this.pendingBreakAge = 0;
   }

   private void ageCounters() {
      this.ticksSinceBreak = Math.min(this.ticksSinceBreak + 1, 1000);
      this.ticksSincePlace = Math.min(this.ticksSincePlace + 1, 1000);
      this.mainAge = Math.min(this.mainAge + 1, 1000);
      this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
      this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
   }

   @Environment(EnvType.CLIENT)
   private record Candidate(CrystalAuraModule.Plan plan, float bound) {
   }

   @Environment(EnvType.CLIENT)
   private final class Fight {
      final ClientPlayerEntity player;
      final ClientWorld level;
      final Vec3d eye;
      final Vec3d aimEye;
      final List<LivingEntity> enemies;
      final List<LivingEntity> friends;
      final int window;
      final int rtt;
      final Rotation fixedLook;
      final Rotation serverLook;
      final float lookBonus;
      final int arriveLead;
      private final int placeLead;
      private final int selfPlaceLead;
      final boolean forced;
      private CrystalAuraModule.Side breakSide;
      private CrystalAuraModule.Side placeSide;

      Fight(
         ClientPlayerEntity player,
         ClientWorld level,
         List<LivingEntity> enemies,
         List<LivingEntity> friends,
         int window,
         int rtt,
         Rotation fixedLook,
         float lookBonus,
         boolean forced
      ) {
         this.player = player;
         this.level = level;
         this.eye = player.getEyePos();
         this.aimEye = fixedLook != null ? this.eye : this.eye.add(player.getVelocity());
         this.enemies = enemies;
         this.friends = friends;
         this.window = window;
         this.rtt = rtt;
         this.fixedLook = fixedLook;
         this.serverLook = ServerRotation.sent();
         this.lookBonus = lookBonus;
         this.arriveLead = Math.min(rtt, 5);
         this.placeLead = Math.min(2 * rtt, 5);
         this.selfPlaceLead = Math.min(rtt, 5);
         this.forced = forced;
      }

      CrystalAuraModule.Side side(CrystalAuraModule.Step step) {
         if (step == CrystalAuraModule.Step.BREAK) {
            if (this.breakSide == null) {
               this.breakSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.arriveLead, 0);
            }

            return this.breakSide;
         } else {
            if (this.placeSide == null) {
               this.placeSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.placeLead, this.selfPlaceLead);
            }

            return this.placeSide;
         }
      }

      Rotation lookAt(Vec3d aim) {
         return this.fixedLook != null ? this.fixedLook : RotationUtils.fromTo(this.aimEye, aim);
      }

      boolean holds(CrystalAuraModule.Plan plan) {
         return CrystalAuraModule.hitAlong(this.player, plan, this.eye, this.serverLook) != null;
      }

      float lookCredit(CrystalAuraModule.Plan plan) {
         return this.lookBonus > 0.0F && this.holds(plan) ? this.lookBonus : 0.0F;
      }
   }

   @Environment(EnvType.CLIENT)
   private enum Look {
      SILENT,
      CROSSHAIR;
   }

   @Environment(EnvType.CLIENT)
   private record Plan(CrystalAuraModule.Step step, EndCrystalEntity crystal, BlockPos clicked, BlockPos target, Vec3d aim, int route) {
      CrystalAuraModule.Plan withRoute(int newRoute) {
         return new CrystalAuraModule.Plan(this.step, this.crystal, this.clicked, this.target, this.aim, newRoute);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Side {
      final List<CrystalDamage.Victim> enemies = new ArrayList<>();
      final List<CrystalDamage.Victim> friends = new ArrayList<>();
      final CrystalDamage.Victim self;

      Side(ClientPlayerEntity player, List<LivingEntity> targets, List<LivingEntity> friends, int lead, int selfLead) {
         for (LivingEntity target : targets) {
            this.enemies.add(new CrystalDamage.Victim(target, Combo.lead(target, lead)));
         }

         for (LivingEntity friend : friends) {
            this.friends.add(new CrystalDamage.Victim(friend, Combo.lead(friend, lead)));
         }

         this.self = new CrystalDamage.Victim(player, Combo.lead(player, selfLead));
      }
   }

   @Environment(EnvType.CLIENT)
   private enum Step {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }
}
