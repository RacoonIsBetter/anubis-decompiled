package net.anubis.client.easyplace.placement;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.easyplace.compat.BaritoneCompat;
import net.anubis.client.easyplace.inventory.InventoryManager;
import net.anubis.client.easyplace.inventory.InventoryPickResult;
import net.anubis.client.easyplace.mixin.AccessorMouseHandler;
import net.anubis.client.easyplace.schematic.SchematicBlockState;
import net.anubis.client.easyplace.schematic.SchematicManager;
import net.anubis.client.easyplace.schematic.SchematicTarget;
import net.anubis.client.easyplace.schematic.TargetStatus;
import net.anubis.client.easyplace.sync.BlockAcks;
import net.anubis.client.easyplace.sync.ServerAckManager;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.EasyPlaceDebug;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class PlacementManager {
   private static final int ACK_TIMEOUT_TICKS = 40;
   private static final int MAX_SLOWDOWN = 20;
   private static final int MAX_RETRIES = 2;
   private static final int FAIL_BLOCK_TICKS = 20;
   private static final int FAILS_BEFORE_HOLD = 3;
   private static final int MAX_HOLD_TICKS = 600;
   private static final float TURN_STEP = 20.0F;
   private static final float TURN_EASE = 0.42F;
   private static final float TURN_SNAP = 1.0F;
   private static final int NO_CANDIDATE_COOLDOWN = 0;
   private static final int SNEAK_WAIT_TICKS = 20;
   private static final int SHIFT_STEADY_TICKS = 1;
   private final MinecraftClient mc;
   private final SchematicManager schematicManager;
   private final InventoryManager inventoryManager;
   private final DependencyManager dependencyManager;
   private final ServerAckManager ackManager;
   private final PlacementListener listener;
   private final Map<Long, Long> blockedUntil = new HashMap<>();
   private final Map<Long, Long> recentlyPlaced = new HashMap<>();
   private final Map<Long, Integer> failsAt = new HashMap<>();
   private boolean reaimed;
   private boolean turning;
   private float turnYaw;
   private float turnPitch;
   private final Random hand = new Random();
   private PlacementState state = PlacementState.IDLE;
   private SchematicTarget target;
   private InventoryPickResult inventoryPick;
   private PlacementTarget placementTarget;
   private ServerAckManager.Result ackResult = ServerAckManager.Result.IDLE;
   private int slowdown;
   private int goodInRow;
   private int refusedCount;
   private long tick;
   private int retry;
   private BlockPos lastNavTarget;
   private long noCandidateUntil;
   private long lastPlacementTick = -1L;
   private float lastYawDelta = 0.0F;
   private int rotationTicksRemaining = 0;
   private float lastSentPitch = Float.NaN;
   private BuildScope scope;
   private PlacementManager.Outcome outcome;
   private Hand placeHand = Hand.MAIN_HAND;
   private long placedCount;
   private long lastTargetTick = Long.MIN_VALUE;
   private int sneakWait;
   private boolean sneakPressed;
   private boolean lastShift;
   private int shiftSteady;
   private long lastPreTick;

   public PlacementManager(MinecraftClient mc, PlacementListener listener) {
      this.mc = mc;
      this.listener = listener;
      this.schematicManager = new SchematicManager(mc);
      this.inventoryManager = new InventoryManager(mc);
      this.dependencyManager = new DependencyManager(mc);
      this.ackManager = new ServerAckManager();
   }

   public void start() {
      this.reset();
      this.state = PlacementState.FIND_TARGET;
   }

   public void setOutcome(PlacementManager.Outcome outcome) {
      this.outcome = outcome;
   }

   public void setScope(BuildScope scope) {
      if (this.scope != null && scope == null || this.scope == null && scope != null) {
         this.schematicManager.invalidate();
      }

      this.scope = scope;
   }

   public long placedCount() {
      return this.placedCount;
   }

   public int refusedCount() {
      return this.refusedCount;
   }

   public boolean placeableFrom(BlockState expected, BlockPos pos, Vec3d eye) {
      return this.dependencyManager.placeableFrom(expected, pos, eye, true);
   }

   public String debugState(BlockPos pos) {
      return "state="
         + this.state
         + " scopeAllows="
         + (this.scope == null ? "noscope" : this.scope.allows(pos))
         + " blocked="
         + this.blockedUntil.containsKey(pos.asLong())
         + " recent="
         + this.recentlyPlaced.containsKey(pos.asLong())
         + " noCand="
         + (this.noCandidateUntil - this.tick)
         + " lastTarget="
         + this.ticksWithoutTarget()
         + " target="
         + (this.target == null ? "-" : this.target.pos().toShortString())
         + " sincePre="
         + (this.tick - this.lastPreTick)
         + " searchNow="
         + describe(
            this.schematicManager.getSchematicWorld() == null
               ? null
               : this.dependencyManager
                  .searchForPlaceables(this.schematicManager.getSchematicWorld(), true, false, 360.0F, 180.0, 0.0F, this.scope, this.blockedUntil::containsKey)
         );
   }

   private static String describe(PlacementTarget found) {
      return found == null ? "none" : found.targetPos().toShortString();
   }

   public long ticksWithoutTarget() {
      return this.lastTargetTick == Long.MIN_VALUE ? Long.MAX_VALUE : this.tick - this.lastTargetTick;
   }

   public void abort() {
      this.failCurrent();
   }

   public boolean busy() {
      return this.state == PlacementState.CALCULATE_ROTATION
         || this.state == PlacementState.WAIT_ROTATION
         || this.state == PlacementState.EXECUTE_PLACE
         || this.state == PlacementState.WAIT_ACK;
   }

   public void tickPre(PlacementConfig config) {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         this.lastPreTick = this.tick;
         if (this.state == PlacementState.IDLE || this.state == PlacementState.FIND_TARGET) {
            if (this.lastPlacementTick >= 0L && this.tick - this.lastPlacementTick <= config.tickDelay() + this.slowdown) {
               this.state = PlacementState.FIND_TARGET;
               return;
            }

            this.resolve(config);
         }

         if (this.state == PlacementState.CALCULATE_ROTATION) {
            this.prepareRotation(config);
         }

         if (this.state == PlacementState.EXECUTE_PLACE) {
            this.lastPlacementTick = this.tick;
            this.executePlace(config);
         }
      }
   }

   public void reset() {
      this.releaseSneak();
      this.state = PlacementState.IDLE;
      this.target = null;
      this.inventoryPick = null;
      this.placementTarget = null;
      this.ackResult = ServerAckManager.Result.IDLE;
      this.retry = 0;
      this.recentlyPlaced.clear();
      this.blockedUntil.clear();
      this.failsAt.clear();
      this.reaimed = false;
      this.turning = false;
      this.ackManager.clear();
      this.inventoryManager.reset();
      this.schematicManager.clear();
      this.lastNavTarget = null;
      this.noCandidateUntil = 0L;
      this.lastYawDelta = 0.0F;
      this.rotationTicksRemaining = 0;
      this.lastSentPitch = Float.NaN;
      this.slowdown = 0;
      this.goodInRow = 0;
      this.refusedCount = 0;
   }

   public void tick(PlacementConfig config) {
      this.tick++;
      if (this.mc.player != null) {
         boolean shift = this.mc.player.isSneaking();
         this.shiftSteady = shift == this.lastShift ? this.shiftSteady + 1 : 0;
         this.lastShift = shift;
      }

      this.blockedUntil.entrySet().removeIf(entry -> entry.getValue() <= this.tick);
      this.recentlyPlaced.entrySet().removeIf(entry -> entry.getValue() <= this.tick);
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (this.state == PlacementState.WAIT_ROTATION && this.turning) {
            if (this.mc.player != null && this.turnTowards(this.mc.player)) {
               this.turning = false;
               this.rotationTicksRemaining = 1;
            }
         } else if (this.state == PlacementState.WAIT_ROTATION) {
            if (this.rotationTicksRemaining > 0) {
               this.rotationTicksRemaining--;
            } else {
               this.state = PlacementState.EXECUTE_PLACE;
            }
         }

         if (this.mc.currentScreen == null || this.state == PlacementState.WAIT_ACK) {
            if (this.state == PlacementState.WAIT_ACK) {
               this.waitForAck(config);
            }
         }
      } else {
         this.softReset();
      }
   }

   private void resolve(PlacementConfig config) {
      if (!this.schematicManager.hasActiveSchematic()) {
         if (this.state != PlacementState.FIND_TARGET) {
            this.state = PlacementState.FIND_TARGET;
            this.listener.feedback("no-schematic", "No active Litematica schematic", 100);
         }
      } else if (this.tick >= this.noCandidateUntil) {
         WorldSchematic schematicWorld = this.schematicManager.getSchematicWorld();
         if (schematicWorld == null) {
            this.state = PlacementState.FIND_TARGET;
         } else {
            this.placementTarget = this.dependencyManager
               .searchForPlaceables(
                  schematicWorld,
                  config.hopperForceFacing(),
                  config.fovFilter(),
                  (float)config.fovDegrees(),
                  config.maxYawChange(),
                  this.lastYawDelta,
                  this.scope,
                  this.blockedUntil::containsKey
               );
            if (this.placementTarget == null) {
               if (!config.schematicOnly()) {
                  boolean hasWrong = this.schematicManager
                     .getTargets(this.tick, config.auraRange(), config.schematicAura(), config.allBlocks())
                     .stream()
                     .anyMatch(c -> c.status() == TargetStatus.WRONG);
                  if (hasWrong) {
                     this.listener.feedback("wrong-safe-skip", "Wrong blocks detected; automatic breaking is disabled for safety", 100);
                  }
               }

               this.noCandidateUntil = this.tick + 0L;
               if (this.scope == null && config.baritoneWalk() && BaritoneCompat.isAvailable()) {
                  SchematicTarget navTarget = this.schematicManager.nearestMissingForNavigation(this.tick, config.allBlocks());
                  if (navTarget != null) {
                     BlockPos navPos = navTarget.pos();
                     if (!navPos.equals(this.lastNavTarget)) {
                        this.lastNavTarget = navPos;
                        int walkRange = Math.max(1, (int)config.auraRange() - 1);
                        BaritoneCompat.goNear(navPos, walkRange);
                        this.listener.feedback("baritone-nav", "Walking to " + navPos.toShortString(), 40);
                     }
                  } else if (this.lastNavTarget != null) {
                     BaritoneCompat.cancel();
                     this.lastNavTarget = null;
                  }
               }

               this.state = PlacementState.FIND_TARGET;
            } else {
               this.lastTargetTick = this.tick;
               if (this.lastNavTarget != null) {
                  BaritoneCompat.cancel();
                  this.lastNavTarget = null;
               }

               BlockPos targetPos = this.placementTarget.targetPos();
               BlockState expected = schematicWorld.getBlockState(targetPos);
               BlockState actual = this.mc.world.getBlockState(targetPos);
               TargetStatus status = BlockStateUtil.classify(expected, actual);
               this.target = new SchematicTarget(
                  targetPos, SchematicBlockState.of(expected), actual, status, targetPos.toCenterPos().squaredDistanceTo(this.mc.player.getEyePos())
               );
               Item required = BlockStateUtil.requiredItem(this.target.expected().state());
               if (required == null) {
                  this.listener.feedback("unsupported-" + this.target.expected().blockId(), "No placeable item for: " + this.target.expected().blockId(), 100);
                  this.blockedUntil.put(this.target.pos().asLong(), this.tick + 1L);
                  this.schematicManager.invalidate();
                  this.target = null;
                  this.placementTarget = null;
                  this.state = PlacementState.FIND_TARGET;
               } else {
                  this.inventoryPick = this.inventoryManager.prepare(config.inventoryPickSource(), required, this.tick, this.scope != null);
                  if (this.inventoryPick.status() != InventoryPickResult.Status.WAITING) {
                     if (this.inventoryPick.status() == InventoryPickResult.Status.MISSING) {
                        this.listener.feedback("missing-item-" + Registries.ITEM.getId(required), "Missing item: " + Registries.ITEM.getId(required), 100);
                        this.blockedUntil.put(this.target.pos().asLong(), this.tick + 1L);
                        this.schematicManager.invalidate();
                        this.target = null;
                        this.inventoryPick = null;
                        this.placementTarget = null;
                        this.state = PlacementState.FIND_TARGET;
                     } else {
                        this.placeHand = Hand.MAIN_HAND;
                        if (this.scope != null) {
                           if (this.inventoryPick.hand() == Hand.OFF_HAND) {
                              this.placeHand = Hand.OFF_HAND;
                           } else {
                              if (this.inventoryPick.slot() < 0 || this.inventoryPick.slot() >= 9) {
                                 this.state = PlacementState.FIND_TARGET;
                                 return;
                              }

                              this.placementTarget = withSlot(this.placementTarget, this.inventoryPick.slot());
                           }
                        }

                        this.debug(config, "target-selected");
                        this.state = PlacementState.CALCULATE_ROTATION;
                     }
                  }
               }
            }
         }
      }
   }

   private void prepareRotation(PlacementConfig config) {
      if (this.mc.player != null && this.placementTarget != null && this.mc.interactionManager != null) {
         ClientPlayerEntity player = this.mc.player;
         boolean needsSneak = Clicks.usesClick(this.mc.world, this.placementTarget.neighborPos(), this.scope != null) || this.placementTarget.sneak();
         if (this.scope != null) {
            this.mc.options.sneakKey.setPressed(needsSneak);
            this.sneakPressed = needsSneak;
            if (player.isSneaking() != needsSneak || this.shiftSteady < 1) {
               if (++this.sneakWait > 20) {
                  if (Boolean.getBoolean("easyplace.builder.debug")) {
                     EasyPlace.LOG
                        .info(
                           "[builder] sneak never settled: want {} shift {} key {} steady {} target {}",
                           new Object[]{
                              needsSneak,
                              player.isSneaking(),
                              this.mc.options.sneakKey.isPressed(),
                              this.shiftSteady,
                              this.placementTarget.targetPos().toShortString()
                           }
                        );
                  }

                  this.failCurrent();
               }

               return;
            }
         } else if (needsSneak && !player.isSneaking()) {
            return;
         }

         this.sneakWait = 0;
         if (this.placeHand == Hand.MAIN_HAND && player.getInventory().getSelectedSlot() != this.placementTarget.hotbarSlot()) {
            player.getInventory().setSelectedSlot(this.placementTarget.hotbarSlot());
         }

         if (config.rotationSpoof()) {
            this.zeroMouseDeltas();
            float targetYaw = this.placementTarget.yaw();
            float targetPitch = this.placementTarget.pitch();
            float currentYaw = player.getYaw();
            float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float newYaw = currentYaw + deltaYaw;
            float pitchDelta = Math.abs(targetPitch - (Float.isNaN(this.lastSentPitch) ? player.getPitch() : this.lastSentPitch));
            float absDeltaYaw = Math.abs(deltaYaw);
            this.rotationTicksRemaining = !(pitchDelta > 15.0F) && !(absDeltaYaw > 45.0F) ? 0 : 1;
            this.lastYawDelta = deltaYaw;
            if (this.scope != null) {
               this.startTurn(targetYaw, targetPitch);
               return;
            }

            this.lastSentPitch = targetPitch;
            player.setYaw(newYaw);
            player.setPitch(targetPitch);
            this.state = PlacementState.WAIT_ROTATION;
         } else {
            this.state = PlacementState.EXECUTE_PLACE;
         }

         this.debug(config, "rotation-prepared");
      } else {
         this.failCurrent();
      }
   }

   private void startTurn(float yaw, float pitch) {
      this.turnYaw = yaw;
      this.turnPitch = pitch;
      this.turning = true;
      this.state = PlacementState.WAIT_ROTATION;
   }

   private boolean turnTowards(ClientPlayerEntity player) {
      this.zeroMouseDeltas();
      float yawLeft = MathHelper.wrapDegrees(this.turnYaw - player.getYaw());
      float pitchLeft = this.turnPitch - player.getPitch();
      if (Math.abs(yawLeft) < 1.0F && Math.abs(pitchLeft) < 1.0F) {
         player.setYaw(player.getYaw() + yawLeft);
         player.setPitch(this.turnPitch);
         this.lastSentPitch = this.turnPitch;
         return true;
      } else {
         float ease = 0.42F * (0.85F + this.hand.nextFloat() * 0.3F);
         float yawStep = Math.abs(yawLeft) < 1.0F ? yawLeft : MathHelper.clamp(yawLeft * ease, -20.0F, 20.0F);
         float pitchStep = Math.abs(pitchLeft) < 1.0F ? pitchLeft : MathHelper.clamp(pitchLeft * ease, -20.0F, 20.0F);
         player.setYaw(player.getYaw() + yawStep);
         player.setPitch(MathHelper.clamp(player.getPitch() + pitchStep, -90.0F, 90.0F));
         return false;
      }
   }

   private void zeroMouseDeltas() {
      if (this.mc.mouse != null) {
         AccessorMouseHandler mouse = (AccessorMouseHandler)this.mc.mouse;
         mouse.easyplace$setAccumulatedDX(0.0);
         mouse.easyplace$setAccumulatedDY(0.0);
      }
   }

   private void executePlace(PlacementConfig config) {
      if (this.mc.player != null && this.placementTarget != null && this.mc.interactionManager != null) {
         ClientPlayerEntity player = this.mc.player;
         if (player.getBoundingBox().intersects(new Box(this.placementTarget.targetPos()))) {
            this.traceFail("body in the cell");
            this.failCurrent();
            this.noCandidateUntil = this.tick + 2L;
         } else {
            double reach = player.getBlockInteractionRange();
            BlockHitResult hit = PlacementUtil.rayTraceTowards(player, player.getYaw(), player.getPitch(), reach);
            if (hit != null
               && hit.getType() == Type.BLOCK
               && hit.getBlockPos().equals(this.placementTarget.neighborPos())
               && hit.getSide() == this.placementTarget.clickedFace()) {
               BlockHitResult finalHit = hit;
               if (this.scope == null
                  || this.placeHand != Hand.MAIN_HAND
                  || this.target == null
                  || this.dependencyManager
                     .verify(
                        this.target.expected().state(),
                        this.placementTarget.targetPos(),
                        finalHit,
                        player.getYaw(),
                        player.getPitch(),
                        player.getInventory().getSelectedSlot(),
                        player.isSneaking()
                     )) {
                  BlockState before = this.mc.world.getBlockState(this.placementTarget.targetPos());
                  ActionResult placementResult = this.mc.interactionManager.interactBlock(player, this.placeHand, finalHit);
                  if (Boolean.getBoolean("easyplace.builder.debug") && this.target != null && this.target.expected().state().getBlock() instanceof ChestBlock) {
                     EasyPlace.LOG
                        .info(
                           "[builder] chest at {} want {} got {} (was {}) shift={} sneakFlag={} against {} face {}",
                           new Object[]{
                              this.placementTarget.targetPos().toShortString(),
                              this.target.expected().state(),
                              this.mc.world.getBlockState(this.placementTarget.targetPos()),
                              before,
                              player.isSneaking(),
                              this.placementTarget.sneak(),
                              this.placementTarget.neighborPos().toShortString(),
                              finalHit.getSide()
                           }
                        );
                  }

                  this.releaseSneak();
                  if (placementResult.isAccepted()) {
                     player.swingHand(this.placeHand);
                     this.ackManager
                        .begin(
                           this.target,
                           this.tick,
                           40,
                           this.retry,
                           BlockAcks.lastSent(this.mc.world),
                           this.mc.world.getBlockState(this.placementTarget.targetPos())
                        );
                     this.ackResult = ServerAckManager.Result.WAITING;
                     this.state = PlacementState.WAIT_ACK;
                     this.debug(config, "placement-sent");
                  } else {
                     this.traceFail("use rejected: " + placementResult);
                     this.debug(config, "placement-rejected");
                     this.failCurrent();
                     this.noCandidateUntil = this.tick + 2L;
                  }
               } else if (!this.reaimed && this.placementTarget.hitCursor() != null) {
                  this.reaimed = true;
                  float[] rot = PlacementUtil.calcRotation(PlacementUtil.getEyePos(player), this.placementTarget.hitCursor());
                  if (this.scope != null) {
                     this.startTurn(rot[0], rot[1]);
                  } else {
                     this.zeroMouseDeltas();
                     player.setYaw(player.getYaw() + MathHelper.wrapDegrees(rot[0] - player.getYaw()));
                     player.setPitch(rot[1]);
                     this.lastSentPitch = rot[1];
                     this.rotationTicksRemaining = 0;
                     this.state = PlacementState.WAIT_ROTATION;
                  }
               } else {
                  this.traceFail(
                     "would not come out right, holding "
                        + player.getInventory().getStack(player.getInventory().getSelectedSlot())
                        + " shift "
                        + player.isSneaking()
                        + " face "
                        + finalHit.getSide()
                        + " hit "
                        + finalHit.getPos()
                        + " planned "
                        + this.placementTarget.hitCursor()
                        + " eye "
                        + PlacementUtil.getEyePos(player)
                        + " yaw "
                        + player.getYaw()
                        + " pitch "
                        + player.getPitch()
                        + " got "
                        + this.dependencyManager
                           .wouldBe(
                              this.placementTarget.targetPos(),
                              finalHit,
                              player.getYaw(),
                              player.getPitch(),
                              player.getInventory().getSelectedSlot(),
                              player.isSneaking()
                           )
                  );
                  this.failCurrent();
                  this.noCandidateUntil = this.tick + 1L;
               }
            } else {
               Vec3d eyePos = PlacementUtil.getEyePos(player);
               if (!PlacementUtil.isFaceVisibleToEye(this.placementTarget.neighborPos(), this.placementTarget.clickedFace(), eyePos)) {
                  this.traceFail("face not visible any more");
                  this.failCurrent();
                  this.noCandidateUntil = this.tick + 1L;
               } else {
                  Vec3d targetCursor = this.placementTarget.hitCursor() != null
                     ? this.placementTarget.hitCursor()
                     : PlacementUtil.faceCenterCursor(this.placementTarget.neighborPos(), this.placementTarget.clickedFace());
                  float[] newRot = PlacementUtil.calcRotation(eyePos, targetCursor);
                  BlockHitResult recheck = PlacementUtil.rayTraceTowards(player, newRot[0], newRot[1], reach);
                  if (recheck != null
                     && recheck.getType() == Type.BLOCK
                     && recheck.getBlockPos().equals(this.placementTarget.neighborPos())
                     && recheck.getSide() == this.placementTarget.clickedFace()) {
                     if (this.scope != null) {
                        this.startTurn(newRot[0], newRot[1]);
                     } else {
                        this.zeroMouseDeltas();
                        float deltaYaw = MathHelper.wrapDegrees(newRot[0] - player.getYaw());
                        player.setYaw(player.getYaw() + deltaYaw);
                        player.setPitch(newRot[1]);
                        this.lastSentPitch = newRot[1];
                        this.rotationTicksRemaining = 0;
                        this.state = PlacementState.WAIT_ROTATION;
                     }
                  } else {
                     this.traceFail("aim hits " + (recheck == null ? "nothing" : recheck.getBlockPos().toShortString() + " " + recheck.getSide()));
                     this.failCurrent();
                     this.noCandidateUntil = this.tick + 1L;
                  }
               }
            }
         }
      } else {
         this.failCurrent();
      }
   }

   private void waitForAck(PlacementConfig config) {
      this.ackResult = this.ackManager.poll(this.mc.world, this.tick);
      if (this.ackResult != ServerAckManager.Result.WAITING) {
         if (this.ackResult == ServerAckManager.Result.CONFIRMED) {
            if (this.slowdown > 0 && ++this.goodInRow >= 10) {
               this.slowdown--;
               this.goodInRow = 0;
            }

            this.listener.feedback("confirmed-" + this.target.pos().asLong(), "Placement confirmed: " + this.target.pos().toShortString(), 1);
            this.debug(config, "confirmed");
            this.recentlyPlaced.put(this.target.pos().asLong(), this.tick + 1L);
            this.failsAt.remove(this.target.pos().asLong());
            this.reaimed = false;
            this.placedCount++;
            if (this.outcome != null) {
               this.outcome.placed(this.target.pos());
            }

            this.schematicManager.invalidate();
            this.target = null;
            this.inventoryPick = null;
            this.placementTarget = null;
            this.ackResult = ServerAckManager.Result.IDLE;
            this.retry = 0;
            this.ackManager.clear();
            this.inventoryManager.reset();
            this.state = PlacementState.FIND_TARGET;
         } else {
            this.goodInRow = 0;
            this.slowdown = Math.min(20, this.slowdown + 3);
            this.refusedCount++;
            if (Boolean.getBoolean("easyplace.builder.debug")) {
               EasyPlace.LOG
                  .info(
                     "[builder] server {} the block at {}, slowing to {} extra ticks",
                     new Object[]{
                        this.ackResult == ServerAckManager.Result.REFUSED ? "refused" : "never answered", this.target.pos().toShortString(), this.slowdown
                     }
                  );
            }

            if (this.retry < 2) {
               this.retry++;
               this.ackManager.clear();
               this.placementTarget = null;
               this.state = PlacementState.CALCULATE_ROTATION;
               this.listener.feedback("retry-" + this.target.pos().asLong(), "Placement ACK timeout; retry " + this.retry + "/2", 10);
               this.debug(config, "ack-timeout-retry");
            } else {
               this.failCurrent();
            }
         }
      }
   }

   private static PlacementTarget withSlot(PlacementTarget target, int slot) {
      return target.hotbarSlot() == slot
         ? target
         : new PlacementTarget(
            target.targetPos(),
            target.neighborPos(),
            target.placeSide(),
            target.clickedFace(),
            slot,
            target.yaw(),
            target.pitch(),
            target.score(),
            target.hitCursor(),
            target.sneak()
         );
   }

   private void traceFail(String why) {
      if (Boolean.getBoolean("easyplace.builder.debug") && this.placementTarget != null) {
         EasyPlace.LOG
            .info(
               "[builder] place fail {} {}: {}",
               new Object[]{this.placementTarget.targetPos().toShortString(), this.target == null ? "?" : this.target.expected().state(), why}
            );
      }
   }

   private void releaseSneak() {
      if (this.sneakPressed) {
         this.sneakPressed = false;
         this.sneakWait = 0;
         this.mc.options.sneakKey.setPressed(false);
      }
   }

   private void failCurrent() {
      this.releaseSneak();
      if (this.target != null) {
         long key = this.target.pos().asLong();
         int fails = this.failsAt.merge(key, 1, Integer::sum);
         if (fails >= 3) {
            this.blockedUntil.put(key, this.tick + Math.min(600, 20 << Math.min(5, fails - 3)));
         }

         if (this.outcome != null) {
            this.outcome.failed(this.target.pos());
         }
      }

      this.reaimed = false;
      this.turning = false;
      this.schematicManager.invalidate();
      this.target = null;
      this.inventoryPick = null;
      this.placementTarget = null;
      this.ackResult = ServerAckManager.Result.IDLE;
      this.retry = 0;
      this.ackManager.clear();
      this.inventoryManager.reset();
      this.state = PlacementState.FIND_TARGET;
   }

   private void softReset() {
      this.releaseSneak();
      this.reaimed = false;
      this.turning = false;
      this.state = PlacementState.FIND_TARGET;
      this.target = null;
      this.ackManager.clear();
      this.inventoryManager.reset();
      this.schematicManager.clear();
   }

   private void debug(PlacementConfig config, String event) {
      if (config.debug()) {
         float yaw = this.placementTarget != null ? this.placementTarget.yaw() : Float.NaN;
         float pitch = this.placementTarget != null ? this.placementTarget.pitch() : Float.NaN;
         EasyPlaceDebug.log(event, this.state, this.placementTarget, new float[]{yaw, pitch}, this.ackResult, this.retry);
      }
   }

   public PlacementState getState() {
      return this.state;
   }

   @Environment(EnvType.CLIENT)
   public interface Outcome {
      void placed(BlockPos var1);

      void failed(BlockPos var1);
   }
}
