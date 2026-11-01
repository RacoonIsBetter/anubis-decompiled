package net.anubis.client.module.impl.combat;

import imgui.ImDrawList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.anchor.AnchorGuard;
import net.anubis.client.module.impl.combat.crystal.Combo;
import net.anubis.client.module.impl.combat.crystal.CrystalDamage;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.crystal.HurtClock;
import net.anubis.client.module.impl.combat.crystal.LootGuard;
import net.anubis.client.module.impl.combat.crystal.ShotView;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class AnchorAuraModule extends ClientModule implements HudOverlayProvider {
   private static final double REACH_MARGIN = 0.01;
   private static final int ARM_TICKS = 2;
   private static final int HOLD_TICKS = 10;
   private static final int MAX_LEAD_TICKS = 5;
   private static final int AIM_TIMEOUT_TICKS = 12;
   private static final int SKIP_TICKS = 40;
   private final NumberSetting minDamage = this.addSetting("min-damage", new NumberSetting("Min Damage", 6.0F, 0.0F, 20.0F).step(0.5F));
   private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 6.0F, 0.0F, 20.0F).step(0.5F));
   private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Render", true));
   private final ColorSetting renderColor = this.addSetting("render-color", new ColorSetting("Render Color", -26052));
   private final NumberSetting turnSpeed = this.addSetting("turn-speed", new NumberSetting("Turn Speed", 4.5F, 1.0F, 10.0F).step(0.5F));
   private final BooleanSetting protectLoot = this.addSetting("protect-loot", new BooleanSetting("Protect Loot", true));
   private final SlotReturn slots = new SlotReturn(this);
   private final ShotView view = new ShotView();
   private int tick;
   private int holdUntil;
   private BlockPos spent;
   private int spentUntil;
   private float aimTurn;
   private float placedTurn;
   private BlockPos aimingAt;
   private int aimingTicks;
   private BlockPos skipped;
   private int skippedUntil;
   private List<CrystalDamage.Victim> lastTargets;
   private List<CrystalDamage.Victim> lastSpared;
   private int lastRtt;
   private float steerMin;
   private float steerSelf;
   private int steerUntil = -1;
   private float tickMin;
   private float tickSelf;

   public AnchorAuraModule() {
      super("anchor-aura", "Anchor Aura", "Places, charges and detonates respawn anchors on nearby enemies.", ModuleCategory.COMBAT);
      this.renderColor.visibleWhen(this.render::get);
   }

   @Override
   protected void onDisable() {
      this.slots.drop(MinecraftClient.getInstance(), true);
      this.spent = null;
      this.view.clear();
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.tick++;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         LootGuard.update(mc.world, player);
      }

      CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
      if ((crystal == null || !crystal.isEnabled())
         && MeleeAttacks.canAct(mc)
         && !player.isSpectator()
         && !AutoInvTotemModule.wantsHotbar()
         && !AutoCityModule.mining()
         && !CombatMacroLock.heldByOther(this)
         && mc.world.getRegistryKey() != World.NETHER) {
         HurtClock.tick(player);
         if (this.tick > this.holdUntil) {
            this.slots.tick(mc, true);
         }

         if (!MeleeAttacks.actedThisTick(player)
            && MeleeAttacks.handsFree(mc, player)
            && !player.shouldCancelInteraction()
            && !MeleeAttacks.rightClickComing(mc)) {
            Combo.Shot shot = Combo.pick(this.shots(mc, player, Combo.enemies(mc, player), Combo.friends(mc, player), Crystals.rttTicks(mc, player), 0.0F));
            if (shot == null) {
               if (this.spent != null && this.tick < this.spentUntil && mc.world.getBlockState(this.spent).isOf(Blocks.RESPAWN_ANCHOR)) {
                  ServerRotation.request(ServerRotation.sent());
               }

               if (this.tick > this.holdUntil) {
                  this.release();
               }
            } else {
               this.show(shot);
               if (CombatMacroLock.tryAcquire(this)) {
                  if (!this.ready(mc, player, shot)) {
                     BlockPos cell = cellOf(shot);
                     this.aimingTicks = cell.equals(this.aimingAt) ? this.aimingTicks + 1 : 0;
                     this.aimingAt = cell;
                     if (this.aimingTicks >= 12) {
                        this.skipped = cell;
                        this.skippedUntil = this.tick + 40;
                        this.aimingAt = null;
                     } else {
                        ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), shot.aim()), this.turnSpeed.get());
                     }
                  } else {
                     this.aimingAt = null;
                     if (shot.route() == -2 || this.slots.take(player, shot.route())) {
                        this.fire(mc, player, shot);
                        this.holdUntil = this.tick + 10;
                        ServerRotation.request(ServerRotation.sent());
                     }
                  }
               }
            }
         }
      } else {
         this.slots.tick(mc, true);
         this.release();
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         HurtClock.note(player, event.packet);
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

   private static BlockPos cellOf(Combo.Shot shot) {
      return shot.plan() instanceof AnchorGuard.Spot spot ? spot.pos() : (BlockPos)shot.plan();
   }

   private boolean ruledOut(BlockPos cell) {
      return cell.equals(this.skipped) && this.tick < this.skippedUntil || this.protectLoot.get() && LootGuard.near(Vec3d.ofCenter(cell));
   }

   public void show(Combo.Shot shot) {
      if (this.render.get()) {
         BlockPos cell = cellOf(shot);
         Vec3d center = Vec3d.ofCenter(cell);
         float most = 0.0F;

         for (CrystalDamage.Victim victim : this.lastTargets) {
            most = Math.max(most, victim.damage(center, 5.0F, cell));
         }

         float own = this.lastSpared.get(0).damage(center, 5.0F, cell);
         this.view.show(new Box(cell), this.renderColor.argb(), most, own);
      }
   }

   public void steer(ClientPlayerEntity player, float minDamage, float maxSelf) {
      this.steerMin = minDamage;
      this.steerSelf = maxSelf;
      this.steerUntil = player.age + 2;
   }

   public List<Combo.Shot> shots(MinecraftClient mc, ClientPlayerEntity player, List<LivingEntity> enemies, List<LivingEntity> friends, int rtt, float rival) {
      ClientWorld level = mc.world;
      boolean steered = player.age <= this.steerUntil;
      this.tickMin = steered ? this.steerMin : this.minDamage.get();
      this.tickSelf = steered ? this.steerSelf : this.maxSelfDamage.get();
      PlayerInventory inventory = player.getInventory();
      ItemStack main = player.getMainHandStack();
      int detonator = AnchorGuard.detonatorRoute(inventory, main);
      if (level.getRegistryKey() != World.NETHER
         && ServerRotation.canRotate(player)
         && !player.getOffHandStack().isOf(Items.GLOWSTONE)
         && !player.shouldCancelInteraction()
         && !enemies.isEmpty()
         && detonator != -1) {
         List<LivingEntity> spared = new ArrayList<>();
         spared.add(player);
         spared.addAll(friends);
         this.lastRtt = rtt;
         this.lastTargets = victims(enemies, rtt);
         this.lastSpared = victims(spared, rtt);
         List<Combo.Shot> shots = new ArrayList<>();
         int glowstone = routeOf(inventory, main, Items.GLOWSTONE);
         boolean existing = false;

         for (BlockPos cell : this.anchors(level, player, enemies)) {
            if (!this.ruledOut(cell)) {
               Vec3d aim = AnchorGuard.anchorAim(level, player, cell);
               if (aim != null && this.safe(this.lastSpared, cell)) {
                  boolean charged = level.getBlockState(cell).get(RespawnAnchorBlock.CHARGES) > 0;
                  int ahead = charged ? 0 : 1;
                  AnchorAuraModule.Blast due = this.blast(cell, ahead, false);
                  if (due != null || this.blast(cell, ahead, true) != null) {
                     existing = true;
                     if (due != null) {
                        if (charged) {
                           shots.add(new Combo.Shot(Combo.Kind.DETONATE, aim, detonator, due.value(), due.kills(), 0, cell));
                        } else if (glowstone != -1 && HurtClock.opensIn(due.source(), rtt) <= 1 && (rival <= 0.0F || due.value() > rival + 1.0F)) {
                           shots.add(new Combo.Shot(Combo.Kind.CHARGE, aim, glowstone, due.value(), due.kills(), 1, cell));
                        }
                     }
                  }
               }
            }
         }

         int anchor = routeOf(inventory, main, Items.RESPAWN_ANCHOR);
         if (!existing && anchor != -1 && glowstone != -1) {
            List<Box> reserved = Crystals.reservedBoxes(player, Crystals.confirmWindow(mc, player));
            Combo.Shot placement = this.place(level, player, anchor, reserved);
            if (placement != null) {
               shots.add(placement);
            }
         }

         return shots;
      } else {
         return List.of();
      }
   }

   public boolean ready(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
      ClientWorld level = mc.world;
      if (shot.kind() == Combo.Kind.ANCHOR) {
         AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
         Rotation sent = ServerRotation.sent();
         if (sent != null && AnchorGuard.hitAlong(level, player, sent, spot) != null) {
            return true;
         }

         float turn = turnTo(player, spot);
         if (turn > 0.0F) {
            this.aimTurn = turn;
         }

         return false;
      } else {
         BlockPos cell = (BlockPos)shot.plan();
         BlockState state = level.getBlockState(cell);
         if (state.isOf(Blocks.RESPAWN_ANCHOR) && AnchorGuard.anchorHit(level, player, cell) != null) {
            boolean charged = state.get(RespawnAnchorBlock.CHARGES) > 0;
            return shot.kind() == Combo.Kind.CHARGE ? !charged : charged && this.safe(this.lastSpared, cell) && this.blast(cell, 0, false) != null;
         } else {
            return false;
         }
      }
   }

   public void fire(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
      ClientWorld level = mc.world;
      switch (shot.kind()) {
         case ANCHOR:
            AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
            AnchorGuard.click(mc, player, AnchorGuard.hitAlong(level, player, ServerRotation.sent(), spot));
            if (this.aimTurn > 0.0F) {
               this.placedTurn = this.aimTurn;
            }
            break;
         case CHARGE:
            AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, (BlockPos)shot.plan()));
            break;
         case DETONATE:
            BlockPos cell = (BlockPos)shot.plan();
            AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, cell));
            this.spent = cell;
            this.spentUntil = this.tick + Crystals.confirmWindow(mc, player);
            Vec3d center = Vec3d.ofCenter(cell);

            for (CrystalDamage.Victim victim : this.lastTargets) {
               float raw = victim.raw(center, 5.0F, cell);
               if (raw > 0.0F) {
                  HurtClock.shot(player, victim.entity(), raw, center, this.lastRtt);
               }
            }
      }
   }

   private Combo.Shot place(ClientWorld level, ClientPlayerEntity player, int route, List<Box> reserved) {
      Map<BlockPos, Float> bounds = new LinkedHashMap<>();

      for (CrystalDamage.Victim victim : this.lastTargets) {
         BlockPos feet = victim.entity().getBlockPos();

         for (BlockPos pos : BlockPos.iterate(feet.add(-2, -1, -2), feet.add(2, 2, 2))) {
            if (!bounds.containsKey(pos)
               && level.getBlockState(pos).isAir()
               && player.canInteractWithBlockAt(pos, -0.01)
               && !reservedAt(reserved, pos)
               && !this.ruledOut(pos)
               && !this.walkedInto(level, pos)) {
               float bound = this.upperValue(Vec3d.ofCenter(pos));
               if (bound >= 0.0F) {
                  bounds.put(pos.toImmutable(), bound);
               }
            }
         }
      }

      List<BlockPos> cells = new ArrayList<>(bounds.keySet());
      cells.sort((a, b) -> Float.compare(bounds.get(b), bounds.get(a)));
      Vec3d eye = player.getEyePos();
      Combo.Shot best = null;

      for (BlockPos pos : cells) {
         if (best != null && bounds.get(pos) <= best.value()) {
            break;
         }

         AnchorAuraModule.Blast blast = this.blast(pos, 2, true);
         if (blast != null && (best == null || !(blast.value() <= best.value())) && this.safe(this.lastSpared, pos)) {
            AnchorGuard.Spot candidate = AnchorGuard.supportFor(level, player, eye, pos);
            if (candidate != null) {
               Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, candidate.aim()));
               Vec3d reach = Vec3d.fromPolar(look.pitch(), look.yaw()).multiply(player.getBlockInteractionRange());
               if (!new Box(pos).raycast(eye, eye.add(reach)).isEmpty()) {
                  float turn = turnTo(player, candidate);
                  if (!(turn > 2.0F) || !(Math.abs(turn - this.placedTurn) < 1.0E-4F)) {
                     best = new Combo.Shot(Combo.Kind.ANCHOR, candidate.aim(), route, blast.value(), blast.kills(), 2, candidate);
                  }
               }
            }
         }
      }

      return best;
   }

   private Set<BlockPos> anchors(ClientWorld level, ClientPlayerEntity player, List<LivingEntity> enemies) {
      Set<BlockPos> anchors = new LinkedHashSet<>();

      for (LivingEntity enemy : enemies) {
         BlockPos feet = enemy.getBlockPos();

         for (BlockPos pos : BlockPos.iterate(feet.add(-4, -3, -4), feet.add(4, 4, 4))) {
            if (level.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)
               && player.canInteractWithBlockAt(pos, -0.01)
               && (!pos.equals(this.spent) || this.tick >= this.spentUntil)) {
               anchors.add(pos.toImmutable());
            }
         }
      }

      return anchors;
   }

   private AnchorAuraModule.Blast blast(BlockPos cell, int ahead, boolean waiting) {
      Vec3d center = Vec3d.ofCenter(cell);
      LivingEntity source = null;
      float best = 0.0F;
      boolean kills = false;

      for (CrystalDamage.Victim victim : this.lastTargets) {
         LivingEntity enemy = victim.entity();
         int when = waiting ? Math.max(ahead, HurtClock.opensIn(enemy, this.lastRtt)) : ahead;
         float effective = HurtClock.effective(victim, victim.raw(center, 5.0F, cell), when, this.lastRtt);
         if (!(effective < this.needed(enemy))) {
            kills = kills || Combo.kills(effective, enemy);
            float value = Combo.value(effective, enemy);
            if (source == null || value > best) {
               source = enemy;
               best = value;
            }
         }
      }

      if (source == null) {
         return null;
      }

      float own = this.lastSpared.get(0).damage(center, 5.0F, cell);
      return new AnchorAuraModule.Blast(best - 0.5F * own, kills, source);
   }

   private float upperValue(Vec3d center) {
      float bound = -1.0F;

      for (CrystalDamage.Victim victim : this.lastTargets) {
         float upper = victim.upperBound(center, 5.0F);
         if (upper >= this.needed(victim.entity())) {
            bound = Math.max(bound, Combo.value(upper, victim.entity()));
         }
      }

      return bound;
   }

   private boolean safe(List<CrystalDamage.Victim> spared, BlockPos cell) {
      Vec3d center = Vec3d.ofCenter(cell);

      for (CrystalDamage.Victim other : spared) {
         float damage = other.damage(center, 5.0F, cell);
         if (damage > this.tickSelf || CrystalDamage.endangers((PlayerEntity)other.entity(), damage)) {
            return false;
         }
      }

      return true;
   }

   private float needed(LivingEntity enemy) {
      return Math.min(this.tickMin, CrystalDamage.health(enemy));
   }

   private static List<CrystalDamage.Victim> victims(List<LivingEntity> entities, int rtt) {
      List<CrystalDamage.Victim> victims = new ArrayList<>(entities.size());

      for (LivingEntity entity : entities) {
         int lead = entity instanceof ClientPlayerEntity ? 0 : Math.min(rtt, 5);
         victims.add(new CrystalDamage.Victim(entity, Combo.lead(entity, lead)));
      }

      return victims;
   }

   private boolean walkedInto(ClientWorld level, BlockPos pos) {
      Box cell = new Box(pos);
      int lead = Math.min(this.lastRtt, 5);

      for (PlayerEntity other : level.getPlayers()) {
         if (!other.isSpectator() && !(other instanceof ClientPlayerEntity)) {
            Box box = other.getBoundingBox();
            if (box.intersects(cell) || box.offset(Combo.lead(other, lead)).intersects(cell)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean reservedAt(List<Box> reserved, BlockPos pos) {
      for (Box box : reserved) {
         if (box.contains(pos)) {
            return true;
         }
      }

      return false;
   }

   private static int routeOf(PlayerInventory inventory, ItemStack main, Item item) {
      if (main.isOf(item)) {
         return -2;
      }

      int slot = Crystals.hotbarSlotOf(inventory, item);
      return slot == -1 ? -1 : slot;
   }

   private static float turnTo(ClientPlayerEntity player, AnchorGuard.Spot spot) {
      Rotation sent = ServerRotation.sent();
      Rotation from = sent != null ? sent : RotationUtils.entityRotation(player);
      Rotation aimed = ServerRotation.next(player, RotationUtils.fromTo(player.getEyePos(), spot.aim()));
      return Math.abs(aimed.yaw() - from.yaw());
   }

   private void release() {
      if (!this.slots.holding()) {
         CombatMacroLock.release(this);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Blast(float value, boolean kills, LivingEntity source) {
   }
}
