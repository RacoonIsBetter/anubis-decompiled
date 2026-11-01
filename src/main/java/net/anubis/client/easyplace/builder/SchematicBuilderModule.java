package net.anubis.client.easyplace.builder;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.easyplace.compat.BaritoneCompat;
import net.anubis.client.easyplace.inventory.InventoryPickSourceMode;
import net.anubis.client.easyplace.module.EasyPlaceModule;
import net.anubis.client.easyplace.placement.BuildScope;
import net.anubis.client.easyplace.placement.PlacementConfig;
import net.anubis.client.easyplace.placement.PlacementManager;
import net.anubis.client.easyplace.schematic.SchematicManager;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.donutmacros.AhPurchase;
import net.anubis.client.module.impl.donutmacros.DonutSell;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
public final class SchematicBuilderModule extends ClientModule {
   private static final int SCAN_TICKS = 20;
   private static final int SCAN_AFTER_PLACE_TICKS = 4;
   private static final int NO_TARGET_TICKS = 4;
   private static final int WALK_FAILS = 2;
   private static final int LEAVES_TO_BUY = 16;
   private static final int SCAFFOLD_DEPTH = 4;
   private static final int SWEEP_TICKS = 1200;
   private static final int NOTHING_MOVED_TICKS = 1200;
   private static final String GLASS_SEARCH = "glass stack";
   private static final int SMALL_NEED = 16;
   private static final int POUR_REST_TICKS = 1200;
   private static final int RELOG_REFUSALS = 8;
   private static final int RELOG_WINDOW_TICKS = 1200;
   private static final int RELOG_GAP_TICKS = 6000;
   private static final int LAVA_FIRST_RETRY = 200;
   private static final double LOW_WEIGHT = 40.0;
   private static final int WALK_CANDIDATES = 16;
   private static final int STUBBORN_TRIES = 3;
   private static final int GIVE_UP_TRIES = 8;
   private static final int BAD_CLIMB_TICKS = 1200;
   private static final float SWIM_DEPTH = 0.4F;
   private static final double CLOSE_REACH = 2.8;
   private static final double CLOSE_STAND = 2.2;
   private static final int TOTEM_POP = 35;
   private static final long SELL_GAP_TICKS = 200L;
   private static final long BUY_SETTLE_TICKS = 40L;
   private static final int BUSY_LIMIT_TICKS = 100;
   private static final int STRAY_MENU_TICKS = 100;
   private static final int RELAX_LIMIT = 32;
   private static final int DEFER_BATCH = 4;
   private static final long PLAN_BUDGET_NANOS = 3000000L;
   private static final int PLAN_TARGETS = 600;
   private static final int BUCKET_BATCH = 8;
   private static final int SPARE_SLOTS = 4;
   private static final int TOTEM_RETRY_TICKS = 1200;
   private static final int SPRINT_RUN = 3;
   private static final int EAT_GAP_TICKS = 60;
   private static final float LOW_HEALTH = 8.0F;
   private static final float SAFE_FALL_HEALTH = 15.0F;
   private static final double MAX_FALL = 5.0;
   private static final float FULL_FALL_HEALTH = 18.0F;
   private static final double FULL_FALL = 8.0;
   private static final int STUCK_GAP_TICKS = 60;
   private static final int ARRIVE_GRACE_TICKS = 30;
   private static final Predicate<ItemStack> TOTEM = stack -> stack.isOf(Items.TOTEM_OF_UNDYING);
   private static final Direction[] SUPPORT_SIDES = new Direction[]{
      Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
   };
   private final BooleanSetting buyFromAh = this.addSetting("buy-from-ah", new BooleanSetting("Buy From AH", true));
   private final StringSetting maxPerListing = this.addSetting(
      "max-per-listing", new StringSetting("Max Price Per Listing", "10m").hint("e.g. 10m, one whole listing")
   );
   private final StringSetting budget = this.addSetting("budget", new StringSetting("Max Total Spend", "").hint("e.g. 500m, empty = no limit"));
   private final NumberSetting refresh = this.addSetting("refresh", new NumberSetting("AH Refresh (ms)", 1000.0F, 300.0F, 3000.0F).step(100.0F));
   private final BooleanSetting climb = this.addSetting("climb", new BooleanSetting("Climb With Leaves", true));
   private final BooleanSetting supportLeaves = this.addSetting("support-leaves", new BooleanSetting("Support Leaves", true));
   private final BooleanSetting walk = this.addSetting("walk", new BooleanSetting("Walk With Baritone", false));
   private final BooleanSetting crafterSlots = this.addSetting("crafter-slots", new BooleanSetting("Crafter Slots", true));
   private final BooleanSetting clickStates = this.addSetting("click-states", new BooleanSetting("Repeaters & Comparators", true));
   private final BooleanSetting placeWater = this.addSetting("place-water", new BooleanSetting("Place Water & Lava", true));
   private final BooleanSetting sellLeftovers = this.addSetting("sell-leftovers", new BooleanSetting("Sell Leftovers", true));
   private final BooleanSetting keepTotem = this.addSetting("keep-totem", new BooleanSetting("Keep Totem In Offhand", true));
   private final BooleanSetting relogOnGhosts = this.addSetting("relog-ghosts", new BooleanSetting("Relog On Ghost Blocks", true));
   private final BooleanSetting placeTnt = this.addSetting("place-tnt", new BooleanSetting("Place TNT", false));
   private final BooleanSetting machineLast = this.addSetting("machine-last", new BooleanSetting("Pistons & Observers Last", true));
   private final BooleanSetting flipLevers = this.addSetting("flip-levers", new BooleanSetting("Flip Levers On", true));
   private final BooleanSetting relaxFacing = this.addSetting("relax-facing", new BooleanSetting("Relax Facing When Stuck", true));
   private final BooleanSetting eat = this.addSetting("eat", new BooleanSetting("Eat When Hungry", true));
   private final NumberSetting tickDelay = this.addSetting("tick-delay", new NumberSetting("Tick Delay", 1.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting chatFeedback = this.addSetting("chat-feedback", new BooleanSetting("Chat Feedback", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final PlacementManager placement = new PlacementManager(this.mc, (key, message, interval) -> {
      if (key.startsWith("missing-item-") || key.startsWith("unsupported-")) {
         this.feedback(key, message, interval);
      }
   });
   private final SchematicManager schematics = new SchematicManager(this.mc);
   private final Walker walker = new Walker();
   private final Set<Long> scaffold = new HashSet<>();
   private final Set<Long> spentSupport = new HashSet<>();
   private long proppedAt = Long.MIN_VALUE;
   private boolean leftoversAdopted;
   private final ScaffoldStore scaffoldStore = new ScaffoldStore();
   private String scaffoldWorld;
   private final Set<Long> stuckScaffold = new HashSet<>();
   private final Map<Long, Long> supportFor = new HashMap<>();
   private final Set<Long> craftersDone = new HashSet<>();
   private final Set<Long> clickFailed = new HashSet<>();
   private final Set<Long> deferred = new HashSet<>();
   private final Set<Long> missingKeys = new HashSet<>();
   private final Set<Long> stubborn = new HashSet<>();
   private final Map<Long, Integer> tries = new HashMap<>();
   private final Map<Long, Integer> walkFails = new HashMap<>();
   private final Map<Long, Long> badStands = new HashMap<>();
   private final Map<Long, Integer> waterFails = new HashMap<>();
   private final Map<Long, Integer> scaffoldFails = new HashMap<>();
   private final Set<Long> badScaffold = new HashSet<>();
   private final Set<Long> buildScaffold = new HashSet<>();
   private final Map<Item, Integer> startCounts = new HashMap<>();
   private final Map<Item, Integer> bought = new HashMap<>();
   private final Map<String, Long> lastFeedbackTick = new HashMap<>();
   private BuildScan scan;
   private long tick;
   private long nextScan;
   private long lastScan;
   private long placedAtScan;
   private int stuckWalks;
   private Vec3d walkFrom;
   private BlockPos walkStand;
   private int climbFails;
   private long noClimbUntil;
   private final Map<Long, Long> badClimbs = new HashMap<>();
   private long placingSince;
   private boolean placing;
   private AhPurchase purchase;
   private Item purchaseItem;
   private int purchaseNeed;
   private long lastPlaceTick = -4611686018427387904L;
   private int busyTicks;
   private StandPlanner planner;
   private List<StandPlanner.Step> route;
   private int routeIndex;
   private BlockPos routeTarget;
   private BlockPos routeStand;
   private BlockPos routeStart;
   private StandPlanner.Use routeUse;
   private int sweptLayer = Integer.MAX_VALUE;
   private int refusedSeen;
   private long lastLookUpTrace;
   private final Relogger relogger = new Relogger();
   private final ArrayDeque<Long> refusalTicks = new ArrayDeque<>();
   private int refusedTracked;
   private BlockPos refusedStand;
   private int refusedHere;
   private long lastRelogTick = -6000L;
   private ClientWorld seenLevel;
   private boolean sweepDue;
   private boolean sweepPlanning;
   private long sweepSince;
   private int climbTo = Integer.MIN_VALUE;
   private boolean fallsNext;
   private long lavaFirstUntil;
   private boolean lavaPlanning;
   private boolean planWithFalls;
   private int routeItemTicks;
   private long placedAtArrival;
   private long arrivedAt = -4611686018427387904L;
   private long noPlanUntil;
   private long lastStuckTick = -4611686018427387904L;
   private int strayMenuTicks;
   private String purchaseName = "";
   private DonutSell sell;
   private long nextSellTick;
   private Climber climber;
   private Hop hop;
   private Bridger bridger;
   private Breaker breaker;
   private BlockUser user;
   private Eater eater;
   private long nextEatTick;
   private CellFiller filler;
   private BlockPos walkTarget;
   private long spent;
   private long spendLimit;
   private long maxListing;
   private int leavesBought;
   private int bucketsEmptied;
   private boolean retriedDeferred;
   private boolean waterGivenUp;
   private boolean lavaGivenUp;
   private boolean latePhase;
   private long stallSince;
   private long stallProgress = -1L;
   private int leastLeft = Integer.MAX_VALUE;
   private BlockPos stallAt;
   private final Set<Long> lackingKeys = new HashSet<>();
   private final Map<Long, Long> pouredAt = new HashMap<>();
   private long nextRefillTick;
   private boolean totemPopped;
   private long totemSwapTick;
   private long nextTotemBuyTick;
   private int shownLayer = Integer.MAX_VALUE;
   private boolean walking;
   private boolean armed;
   private int leversFlipped;
   private boolean verified;
   private boolean relaxTried;
   private final List<BlockPos> relaxed = new ArrayList<>();
   private final List<Long> plannedKeys = new ArrayList<>();

   public SchematicBuilderModule() {
      super(
         "schematic-builder",
         "Schematic Builder",
         "Builds your active Litematica schematic layer by layer: buys missing blocks from the AH, walks with Baritone, climbs with leaves, sets crafter slots, puts the water and lava in and sells what is left over.",
         ModuleCategory.DONUT
      );
      this.usesNotifications();
      this.maxPerListing.visibleWhen(this.buyFromAh::get);
      this.budget.visibleWhen(this.buyFromAh::get);
      this.refresh.visibleWhen(this.buyFromAh::get);
      this.placement.setOutcome(new PlacementManager.Outcome() {
         @Override
         public void placed(BlockPos pos) {
            if (SchematicBuilderModule.this.mc.world != null && SchematicBuilderModule.this.mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
               SchematicBuilderModule.trace("chest confirmed at " + pos.toShortString() + " as " + SchematicBuilderModule.this.mc.world.getBlockState(pos));
            }

            SchematicBuilderModule.this.tries.merge(pos.asLong(), 1, Integer::sum);
            SchematicBuilderModule.this.lastPlaceTick = SchematicBuilderModule.this.tick;
            SchematicBuilderModule.this.verified = false;
         }

         @Override
         public void failed(BlockPos pos) {
            SchematicBuilderModule.this.tries.merge(pos.asLong(), 1, Integer::sum);
            SchematicBuilderModule.this.lastPlaceTick = SchematicBuilderModule.this.tick;
         }
      });
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      if (this.mc.player == null || this.mc.world == null) {
         this.say(ModuleChat.body().warn("Join a world first."));
         this.requestDisable();
      } else if (!this.schematics.hasActiveSchematic()) {
         this.say(ModuleChat.body().warn("No active Litematica placement").text(", load and place a schematic first."));
         this.requestDisable();
      } else {
         this.maxListing = AhPurchase.parseMoney(this.maxPerListing.get());
         if (this.buyFromAh.get() && this.maxListing <= 0L) {
            this.say(ModuleChat.body().warn("Set Max Price Per Listing to a price like 10m."));
            this.requestDisable();
         } else {
            this.spendLimit = this.budget.get().isBlank() ? 0L : AhPurchase.parseMoney(this.budget.get());
            if (this.buyFromAh.get() && !this.budget.get().isBlank() && this.spendLimit <= 0L) {
               this.say(ModuleChat.body().warn("Set Max Total Spend to a price like 500m, or leave it empty."));
               this.requestDisable();
            } else {
               EasyPlaceModule easyPlace = ModuleRegistry.get(EasyPlaceModule.class);
               if (easyPlace != null && easyPlace.isEnabled()) {
                  easyPlace.setEnabled(false);
               }

               this.resetState();
               this.snapshotInventory(this.mc.player);
               this.placement.start();
               this.walking = this.walk.get() && BaritoneCompat.canWalk();
               if (this.walk.get() && !this.walking) {
                  this.say(ModuleChat.body().warn("Baritone not found").text(", walking with the builder's own planner."));
               }

               if (this.walking) {
                  BaritoneCompat.lockForBuild();
               }

               this.on(TickEvent.TYPE, Stage.PRE, event -> {
                  this.relogger.tick(this.mc);
                  if (!this.relogger.active()) {
                     this.placement.tick(this.config());
                     this.step();
                  }

                  if (this.placing && this.mc.currentScreen == null) {
                     this.placement.tickPre(this.config());
                  }
               });
               this.on(
                  PacketAppliedEvent.TYPE,
                  event -> {
                     if (event.packet instanceof EntityStatusS2CPacket status
                        && status.getStatus() == 35
                        && this.mc.world != null
                        && this.mc.player != null
                        && status.getEntity(this.mc.world) == this.mc.player) {
                        this.totemPopped = true;
                     }
                  }
               );
               this.say(ModuleChat.body().text("Building the schematic layer by layer."));
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      if (this.purchase != null) {
         this.purchase.cancel();
      }

      if (this.sell != null) {
         this.sell.cancel();
      }

      if (this.climber != null) {
         this.climber.release(this.mc);
      }

      if (this.hop != null) {
         this.hop.release(this.mc);
      }

      if (this.bridger != null) {
         this.bridger.release(this.mc);
      }

      if (this.breaker != null && this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }

      if (this.breaker != null) {
         this.breaker.release(this.mc);
      }

      if (this.user != null) {
         this.user.close(this.mc);
      }

      if (this.eater != null) {
         this.eater.release(this.mc);
      }

      this.walker.stop();
      this.placement.reset();
      if (this.walking) {
         BaritoneCompat.restoreSettings();
      }

      this.scaffoldStore.save(this.scaffoldWorld, this.scaffold);
      this.scaffoldWorld = null;
      this.resetState();
   }

   static boolean tracing() {
      return Boolean.getBoolean("easyplace.builder.debug");
   }

   static void trace(String message) {
      if (Boolean.getBoolean("easyplace.builder.debug")) {
         EasyPlace.LOG.info("[builder] " + message);
      }
   }

   private void resetState() {
      this.scan = null;
      this.tick = 0L;
      this.nextScan = 0L;
      this.lastScan = 0L;
      this.placedAtScan = 0L;
      this.stuckWalks = 0;
      this.placing = false;
      this.purchase = null;
      this.purchaseItem = null;
      this.sell = null;
      this.nextSellTick = 0L;
      this.climber = null;
      this.hop = null;
      this.bridger = null;
      this.breaker = null;
      this.user = null;
      this.eater = null;
      this.nextEatTick = 0L;
      this.filler = null;
      this.planner = null;
      this.route = null;
      this.routeTarget = null;
      this.noPlanUntil = 0L;
      this.lastStuckTick = -4611686018427387904L;
      this.walkTarget = null;
      this.spent = 0L;
      this.leavesBought = 0;
      this.bucketsEmptied = 0;
      this.retriedDeferred = false;
      this.armed = false;
      this.leversFlipped = 0;
      this.verified = false;
      this.relaxTried = false;
      this.relaxed.clear();
      this.waterGivenUp = false;
      this.lavaGivenUp = false;
      this.latePhase = false;
      this.nextRefillTick = 0L;
      this.totemPopped = false;
      this.totemSwapTick = 0L;
      this.nextTotemBuyTick = 0L;
      this.shownLayer = Integer.MAX_VALUE;
      this.sweptLayer = Integer.MAX_VALUE;
      this.refusedSeen = 0;
      this.refusalTicks.clear();
      this.refusedTracked = 0;
      this.refusedStand = null;
      this.refusedHere = 0;
      this.lastRelogTick = -6000L;
      this.seenLevel = null;
      this.climbTo = Integer.MIN_VALUE;
      this.fallsNext = false;
      this.planWithFalls = false;
      this.lavaFirstUntil = 0L;
      this.lavaPlanning = false;
      this.sweepDue = false;
      this.sweepPlanning = false;
      this.scaffold.clear();
      this.spentSupport.clear();
      this.proppedAt = Long.MIN_VALUE;
      this.leftoversAdopted = false;
      this.stuckScaffold.clear();
      this.supportFor.clear();
      this.craftersDone.clear();
      this.clickFailed.clear();
      this.deferred.clear();
      this.missingKeys.clear();
      this.stubborn.clear();
      this.tries.clear();
      this.walkFails.clear();
      this.badStands.clear();
      this.climbFails = 0;
      this.noClimbUntil = 0L;
      this.badClimbs.clear();
      this.pouredAt.clear();
      this.leastLeft = Integer.MAX_VALUE;
      this.stallAt = null;
      this.walkStand = null;
      this.waterFails.clear();
      this.scaffoldFails.clear();
      this.badScaffold.clear();
      this.buildScaffold.clear();
      this.startCounts.clear();
      this.bought.clear();
      this.lastFeedbackTick.clear();
      this.placement.setScope(null);
   }

   private boolean watchRefusals(ClientPlayerEntity player) {
      int refused = this.placement.refusedCount();
      if (refused < this.refusedTracked) {
         this.refusedTracked = refused;
      }

      if (refused > this.refusedTracked) {
         for (int i = this.refusedTracked; i < refused; i++) {
            this.refusalTicks.addLast(this.tick);
         }

         this.refusedTracked = refused;
         BlockPos at = player.getBlockPos();
         if (at.equals(this.refusedStand)) {
            if (++this.refusedHere >= 2) {
               this.badStands.put(at.asLong(), this.tick + 600L);
               this.stopMoving();
               this.route = null;
               this.noPlanUntil = this.tick;
               this.refusedHere = 0;
               this.feedback("refused-move", "The server refuses blocks placed from this spot, trying from another", 100);
            }
         } else {
            this.refusedStand = at;
            this.refusedHere = 1;
         }
      }

      while (!this.refusalTicks.isEmpty() && this.tick - this.refusalTicks.peekFirst() > 1200L) {
         this.refusalTicks.pollFirst();
      }

      if (!this.relogOnGhosts.get() || this.refusalTicks.size() < 8 || this.tick - this.lastRelogTick < 6000L) {
         return false;
      }

      if (this.purchase != null || this.sell != null || this.user != null) {
         return false;
      }

      if (!this.relogger.start(this.mc)) {
         return false;
      }

      this.lastRelogTick = this.tick;
      this.refusalTicks.clear();
      this.say(ModuleChat.body().warn("The server keeps refusing blocks (ghost blocks)").text(": relogging to sync up, the build carries on after."));
      this.releaseMovement();
      return true;
   }

   private boolean watchStall(ClientPlayerEntity player) {
      if (this.purchase == null && this.sell == null && !this.relogger.active()) {
         int left = this.scan == null ? Integer.MAX_VALUE : this.missingKeys.size() + this.scan.liquids.size() + this.scaffold.size();
         if (this.placement.placedCount() != this.stallProgress
            || left < this.leastLeft
            || this.stallAt == null
            || !this.stallAt.isWithinDistance(player.getBlockPos(), 3.0)) {
            this.stallProgress = this.placement.placedCount();
            this.leastLeft = Math.min(this.leastLeft, left);
            this.stallAt = player.getBlockPos();
            this.stallSince = this.tick;
            return false;
         }

         if (this.tick - this.stallSince < 1200L) {
            return false;
         }

         this.stallSince = this.tick;
         trace(
            "nothing moved for 1200 ticks at "
               + player.getBlockPos().toShortString()
               + ": hop "
               + (this.hop != null)
               + " climber "
               + (this.climber != null)
               + " bridger "
               + (this.bridger != null)
               + " breaker "
               + (this.breaker == null ? "-" : this.breaker.pos().toShortString())
               + " filler "
               + (this.filler == null ? "-" : this.filler.kind() + " " + this.filler.cell().toShortString())
               + " user "
               + (this.user != null)
               + " eater "
               + (this.eater != null)
               + " planner "
               + (this.planner != null)
               + " route "
               + (this.route == null ? "-" : this.routeIndex + "/" + this.route.size())
               + " walker "
               + this.walker.active()
               + " placing "
               + this.placing
               + " noPlan "
               + (this.noPlanUntil - this.tick)
               + " layer "
               + (this.scan == null ? "?" : this.scan.layer)
               + " "
               + this.placement.debugState(player.getBlockPos())
               + " | "
               + this.scanState()
         );
         this.feedback("stall", "Nothing moved for a minute, starting over from here", 100);
         if (this.filler != null) {
            this.waterFails.put(this.filler.cell().asLong(), 2);
         }

         if (this.routeTarget != null) {
            this.deferred.add(this.routeTarget.asLong());
         }

         this.releaseMovement();
         if (this.user != null) {
            this.user.close(this.mc);
         }

         this.user = null;
         this.filler = null;
         this.planner = null;
         this.route = null;
         this.routeTarget = null;
         this.stopMoving();
         this.placement.abort();
         this.noPlanUntil = this.tick;
         this.rescanSoon();
         return true;
      } else {
         this.stallSince = this.tick;
         return false;
      }
   }

   private String scanState() {
      if (this.scan == null) {
         return "no scan";
      }

      StringBuilder out = new StringBuilder(
         "missing "
            + this.scan.missing.size()
            + " waiting "
            + this.scan.waiting.size()
            + " deferred "
            + this.deferred.size()
            + " liquids "
            + this.scan.liquids.size()
            + " late "
            + this.scan.late
            + " latePhase "
            + this.latePhase
            + " whole "
            + this.scan.wholeSchematic
            + " waterGivenUp "
            + this.waterGivenUp
            + " retriedDeferred "
            + this.retriedDeferred
            + " buckets "
            + (this.mc.player == null ? "?" : count(this.mc.player, Items.WATER_BUCKET))
      );
      int shown = 0;

      for (BuildScan.Cell cell : this.scan.missing) {
         if (shown++ >= 6) {
            break;
         }

         long key = cell.pos().asLong();
         out.append(" [")
            .append(cell.pos().toShortString())
            .append(' ')
            .append(cell.expected().getBlock().getName().getString())
            .append(this.scan.waiting.contains(key) ? " waiting" : "")
            .append(this.deferred.contains(key) ? " deferred" : "")
            .append(this.lackingKeys.contains(key) ? " lacking" : "")
            .append(']');
      }

      return out.toString();
   }

   private void releaseMovement() {
      if (this.climber != null) {
         this.climber.release(this.mc);
      }

      if (this.hop != null) {
         this.hop.release(this.mc);
      }

      if (this.bridger != null) {
         this.bridger.release(this.mc);
      }

      if (this.breaker != null && this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }

      if (this.breaker != null) {
         this.breaker.release(this.mc);
      }

      if (this.eater != null) {
         this.eater.release(this.mc);
      }

      this.climber = null;
      this.hop = null;
      this.bridger = null;
      this.breaker = null;
      this.eater = null;
      this.filler = null;
      this.planner = null;
      this.route = null;
      this.routeTarget = null;
      this.walker.stop();
      this.placing = false;
   }

   private void restartAfterRejoin() {
      this.releaseMovement();
      if (this.user != null) {
         this.user.close(this.mc);
      }

      this.user = null;
      if (this.purchase != null) {
         this.purchase.cancel();
      }

      this.purchase = null;
      if (this.sell != null) {
         this.sell.cancel();
      }

      this.sell = null;
      this.placement.reset();
      this.refusedTracked = 0;
      this.scan = null;
      this.nextScan = 0L;
      this.noPlanUntil = 0L;
      trace("back in the world, starting over from a fresh scan");
   }

   private void keepScaffoldList() {
      String world = ScaffoldStore.worldKey(this.mc);
      if (!Objects.equals(world, this.scaffoldWorld)) {
         this.scaffoldStore.save(this.scaffoldWorld, this.scaffold);
         this.scaffold.clear();
         this.scaffoldWorld = world;
         if (world != null) {
            Set<Long> earlier = this.scaffoldStore.load(world);
            this.scaffold.addAll(earlier);
            if (!earlier.isEmpty()) {
               this.feedback("scaffold-kept", earlier.size() + " scaffold blocks from an earlier session are still there, they get mined too", 1);
            }
         }
      } else {
         if (this.tick % 100L == 0L) {
            this.scaffoldStore.save(this.scaffoldWorld, this.scaffold);
         }
      }
   }

   private PlacementConfig config() {
      return new PlacementConfig(
         true, true, 180.0, InventoryPickSourceMode.SERVER_RANGE, true, this.tickDelay.getInt(), true, 4.5, true, false, false, false, 360.0
      );
   }

   private void step() {
      ClientPlayerEntity player = this.mc.player;
      ClientWorld level = this.mc.world;
      if (player != null && level != null && this.mc.interactionManager != null) {
         this.tick++;
         if (level != this.seenLevel) {
            if (this.seenLevel != null) {
               this.restartAfterRejoin();
            }

            this.seenLevel = level;
         }

         if (!this.watchRefusals(player)) {
            this.keepScaffoldList();
            if (!this.watchStall(player)) {
               if (this.mc.currentScreen instanceof AbstractSignEditScreen) {
                  this.mc.currentScreen.close();
               } else if (this.mc.currentScreen instanceof HandledScreen && this.purchase == null && this.sell == null && this.user == null) {
                  if (tracing()) {
                     trace(
                        "menu "
                           + this.mc.currentScreen.getTitle().getString()
                           + " opened; aiming at "
                           + (
                              this.mc.crosshairTarget instanceof BlockHitResult hit
                                 ? hit.getBlockPos().toShortString() + " " + level.getBlockState(hit.getBlockPos()).getBlock()
                                 : "-"
                           )
                           + " shift "
                           + player.isSneaking()
                           + " key "
                           + this.mc.options.sneakKey.isPressed()
                           + " hop "
                           + (this.hop != null)
                           + " climber "
                           + (this.climber != null)
                           + " bridger "
                           + (this.bridger != null)
                           + " filler "
                           + (this.filler != null)
                           + " user "
                           + (this.user != null)
                           + " breaker "
                           + (this.breaker != null)
                           + " "
                           + this.placement.debugState(player.getBlockPos())
                     );
                  }

                  player.closeHandledScreen();
                  this.feedback("stray-menu", "Closed a menu that was opened by mistake", 100);
               } else if (this.purchase != null) {
                  this.placing = false;
                  if (this.purchaseItem != null && this.purchase.status() == AhPurchase.Status.RUNNING && count(player, this.purchaseItem) >= this.purchaseNeed
                     )
                   {
                     this.purchase.cancel();
                     this.spent = this.spent + this.purchase.spent();
                     this.bought.merge(this.purchaseItem, this.purchase.boughtItems(), Integer::sum);
                     this.feedback("enough-" + this.purchaseName, "Already have enough " + this.purchaseName + ", stopped buying", 1);
                     this.purchase = null;
                     this.rescanSoon();
                  } else if (this.keepTotem.get()
                     && this.totemPopped
                     && this.purchaseItem != Items.TOTEM_OF_UNDYING
                     && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                     && !Hands.has(player, TOTEM)) {
                     this.purchase.cancel();
                     this.spent = this.spent + this.purchase.spent();
                     if (this.purchaseItem != null) {
                        this.bought.merge(this.purchaseItem, this.purchase.boughtItems(), Integer::sum);
                     } else {
                        this.leavesBought = this.leavesBought + this.purchase.boughtItems();
                     }

                     this.purchase = null;
                  } else {
                     this.followPurchase();
                  }
               } else if (this.sell != null) {
                  this.placing = false;
                  if (!this.sell.tick()) {
                     this.sell = null;
                     this.nextSellTick = this.tick + 200L;
                     this.rescanSoon();
                  }
               } else if (this.user != null) {
                  this.placing = false;
                  BlockUser.Result result = this.user.tick(this.mc);
                  if (result != BlockUser.Result.RUNNING) {
                     if (this.user.crafter()) {
                        this.craftersDone.add(this.user.pos().asLong());
                        if (result == BlockUser.Result.FAILED) {
                           this.feedback("crafter", "Could not set the crafter at " + this.user.pos().toShortString(), 1);
                        }
                     } else if (result == BlockUser.Result.FAILED) {
                        this.clickFailed.add(this.user.pos().asLong());
                     }

                     this.user.close(this.mc);
                     this.user = null;
                     this.rescanSoon();
                  }
               } else if (this.eater != null) {
                  this.placing = false;
                  Eater.Result result = this.eater.tick(this.mc);
                  if (result != Eater.Result.RUNNING) {
                     this.eater.release(this.mc);
                     this.eater = null;
                  }
               } else if (this.climber != null) {
                  this.placing = false;
                  Climber.Result result = this.climber.tick(this.mc);
                  if (result != Climber.Result.RUNNING) {
                     this.climber.release(this.mc);
                     this.climber = null;
                     this.walkFails.clear();
                     if (result == Climber.Result.FAILED) {
                        trace("climb failed at " + player.getBlockPos().toShortString() + " in " + level.getFluidState(player.getBlockPos()));
                        this.badClimbs.put(this.climber.column().asLong(), this.tick + 1200L);
                        this.route = null;
                        if (++this.climbFails >= 2) {
                           this.climbFails = 0;
                           this.noClimbUntil = this.tick + 600L;
                           this.feedback("climb-pause", "Pillaring keeps failing here, trying other spots for 30s", 1);
                        } else {
                           this.feedback("climb-failed", "Could not climb here", 60);
                        }
                     } else {
                        this.climbFails = 0;
                     }

                     this.rescanSoon();
                  }
               } else if (this.bridger != null) {
                  this.placing = false;
                  Bridger.Result result = this.bridger.tick(this.mc);
                  if (result != Bridger.Result.RUNNING) {
                     this.bridger.release(this.mc);
                     if (result == Bridger.Result.FAILED) {
                        trace("bridge failed to " + this.bridger.spot().toShortString() + " from " + player.getBlockPos().toShortString());
                        this.badStands.put(this.bridger.spot().asLong(), this.tick + 400L);
                        this.route = null;
                     }

                     this.bridger = null;
                     this.rescanSoon();
                  }
               } else if (this.hop != null) {
                  this.placing = false;
                  Hop.Result result = this.hop.tick(this.mc);
                  if (result != Hop.Result.RUNNING) {
                     if (result == Hop.Result.FAILED) {
                        trace("hop failed to " + this.hop.spot().toShortString() + " from " + player.getBlockPos().toShortString() + " y=" + player.getY());
                        this.badStands.put(this.hop.spot().asLong(), this.tick + 400L);
                        this.route = null;
                     }

                     this.hop = null;
                     if (result == Hop.Result.DONE) {
                        this.stuckWalks = 0;
                     }

                     this.rescanSoon();
                  }
               } else if (this.breaker != null) {
                  this.placing = false;
                  Breaker.Result result = this.breaker.tick(this.mc);
                  if (result != Breaker.Result.RUNNING) {
                     if (result == Breaker.Result.DONE) {
                        long key = this.breaker.pos().asLong();
                        this.scaffold.remove(key);
                        this.spentSupport.remove(key);
                        this.supportFor.remove(key);
                     } else {
                        this.stuckScaffold.add(this.breaker.pos().asLong());
                     }

                     this.breaker = null;
                     this.rescanSoon();
                  }
               } else if (this.filler != null) {
                  this.placing = false;
                  CellFiller.Result result = this.filler.tick(this.mc);
                  if (result != CellFiller.Result.RUNNING) {
                     this.fillerDone(result == CellFiller.Result.DONE);
                     this.filler = null;
                     this.rescanSoon();
                  }
               } else {
                  if (this.route != null) {
                     this.placing = false;
                     if (this.routeIndex < this.route.size()) {
                        BlockPos passing = this.cleanupLeaf(player, this.routeFloor());
                        if (passing != null) {
                           this.mc.options.forwardKey.setPressed(false);
                           this.mc.options.sprintKey.setPressed(false);
                           this.mc.options.jumpKey.setPressed(false);
                           if (player.isOnGround() && !(player.getVelocity().horizontalLengthSquared() > 4.0E-4)) {
                              trace("mining " + passing.toShortString() + " in passing, standing at " + player.getBlockPos().toShortString());
                              this.stopMoving();
                              this.breaker = new Breaker(passing, false);
                              return;
                           }

                           return;
                        }

                        BlockPos expected = this.routeIndex == 0 ? this.routeStart : this.route.get(this.routeIndex - 1).pos();
                        BlockPos at = player.getBlockPos();
                        if (!player.isOnGround()) {
                           return;
                        }

                        if (expected == null || at.getX() == expected.getX() && at.getZ() == expected.getZ() && Math.abs(at.getY() - expected.getY()) <= 1) {
                           StandPlanner.Step next = this.route.get(this.routeIndex++);
                           if (next.move() != StandPlanner.Move.WALK && next.move() != StandPlanner.Move.LEAP) {
                              if (next.move() != StandPlanner.Move.BRIDGE) {
                                 int rise = next.pos().getY() - player.getBlockPos().getY();
                                 BlockPos column = next.pos().down(Math.max(1, rise));
                                 if (rise >= 1 && rise <= 2 && this.pillarFits(level, column) && Climber.leavesCount(player) >= rise) {
                                    this.climber = new Climber(column, this.scaffold, rise);
                                    return;
                                 }

                                 trace(
                                    "route pillar refused at "
                                       + player.getBlockPos().toShortString()
                                       + " column="
                                       + column.toShortString()
                                       + " y="
                                       + player.getY()
                                       + " ground="
                                       + player.isOnGround()
                                 );
                                 this.route = null;
                                 this.noPlanUntil = this.tick + 40L;
                                 return;
                              }

                              BlockPos under = next.pos().down();
                              WorldSchematic schematic = this.schematics.getSchematicWorld();
                              Item material = null;
                              if (schematic != null && !schematic.getBlockState(under).isAir()) {
                                 material = BlockStateUtil.requiredItem(schematic.getBlockState(under));
                                 if (material == null || count(player, material) <= 0) {
                                    trace("route bridge into " + under.toShortString() + " without its block");
                                    this.route = null;
                                    this.noPlanUntil = this.tick + 40L;
                                    return;
                                 }
                              }

                              this.bridger = new Bridger(player.getBlockPos(), next.pos(), this.scaffold, material);
                              if (this.routeIndex < this.route.size()
                                 && this.route.get(this.routeIndex).move() == StandPlanner.Move.BRIDGE
                                 && this.route.get(this.routeIndex).pos().subtract(next.pos()).equals(next.pos().subtract(player.getBlockPos()))) {
                                 this.bridger.chained();
                              }

                              return;
                           }

                           boolean through = next.move() == StandPlanner.Move.WALK
                              && this.routeIndex < this.route.size()
                              && this.route.get(this.routeIndex).move() == StandPlanner.Move.WALK;
                           this.hop = new Hop(next.pos(), through, next.move() == StandPlanner.Move.LEAP);
                           boolean far = next.pos().getManhattanDistance(player.getBlockPos()) - Math.abs(next.pos().getY() - player.getBlockPos().getY()) >= 3;
                           if (this.flatRunAhead(next.pos()) >= 3 || next.move() == StandPlanner.Move.LEAP && far) {
                              this.hop.sprinting();
                           }

                           if (next.move() == StandPlanner.Move.WALK
                              && (
                                 Walker.risky(level, player.getBlockPos())
                                    || Walker.risky(level, next.pos())
                                    || Walker.icy(level, player.getBlockPos())
                                    || Walker.icy(level, next.pos())
                              )) {
                              if (next.pos().getY() >= player.getBlockPos().getY()) {
                                 this.hop.careful();
                              } else {
                                 this.hop.slow();
                              }
                           }

                           return;
                        }

                        this.mc.options.forwardKey.setPressed(false);
                        this.mc.options.sprintKey.setPressed(false);
                        this.mc.options.jumpKey.setPressed(false);
                        if (player.getVelocity().horizontalLengthSquared() > 4.0E-4) {
                           this.mc.options.sneakKey.setPressed(true);
                           return;
                        }

                        this.mc.options.sneakKey.setPressed(false);
                        trace("off the route at " + at.toShortString() + ", expected " + expected.toShortString() + ", planning again");
                        this.route = null;
                        this.routeTarget = null;
                        this.noPlanUntil = this.tick;
                        return;
                     }

                     this.route = null;
                     this.arrivedAt = this.tick;
                     this.placedAtArrival = this.placement.placedCount();
                     this.rescanSoon();
                  }

                  if (this.planner != null) {
                     this.placing = false;
                     this.followPlanner();
                  } else if (this.walker.active()) {
                     this.placing = false;
                     Walker.Result result = this.walker.tick(player, this.tick);
                     if (result == Walker.Result.FAILED) {
                        if (this.walkStand != null) {
                           this.badStands.put(this.walkStand.asLong(), this.tick + 1200L);
                        }

                        if (this.walkTarget != null && this.walkFails.merge(this.walkTarget.asLong(), 1, Integer::sum) >= 2) {
                           this.deferred.add(this.walkTarget.asLong());
                        }

                        if (this.walkFrom != null && player.getEntityPos().distanceTo(this.walkFrom) < 1.0) {
                           this.stuckWalks++;
                        }
                     } else if (result == Walker.Result.ARRIVED) {
                        this.stuckWalks = 0;
                     }

                     if (result != Walker.Result.RUNNING) {
                        this.walkTarget = null;
                        this.rescanSoon();
                     }
                  } else if (this.mc.currentScreen != null) {
                     this.placing = false;
                     if (player.currentScreenHandler != player.playerScreenHandler && ++this.strayMenuTicks > 100) {
                        this.strayMenuTicks = 0;
                        player.closeHandledScreen();
                        this.feedback("stray-menu", "Closed a menu that was left open", 100);
                     }
                  } else {
                     this.strayMenuTicks = 0;
                     if (this.placement.busy()) {
                        this.placing = true;
                        if (++this.busyTicks > 100) {
                           this.busyTicks = 0;
                           this.placement.abort();
                           this.feedback("busy-reset", "A placement got stuck, skipping it", 100);
                        }
                     } else {
                        this.busyTicks = 0;
                        if (!this.keepTotem.get() || !this.mindTotem(player)) {
                           if (this.eat.get() && Eater.hungry(player) && this.tick >= this.nextEatTick) {
                              this.nextEatTick = this.tick + 60L;
                              if (Eater.hasFood(player)) {
                                 this.stopMoving();
                                 this.eater = new Eater();
                                 return;
                              }

                              if (player.getHealth() <= 8.0F) {
                                 this.feedback("no-food", "Low on health and nothing to eat: health won't come back", 600);
                              }
                           }

                           if (this.scan == null
                              || this.tick >= this.nextScan
                              || this.placement.placedCount() != this.placedAtScan && this.tick - this.lastScan >= 4L) {
                              this.rescan(level, player);
                           }

                           if (this.scan != null) {
                              this.decide(level, player);
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.placing = false;
      }
   }

   private void fillerDone(boolean done) {
      long key = this.filler.cell().asLong();
      switch (this.filler.kind()) {
         case SUPPORT_LEAF:
            if (done) {
               this.scaffold.add(key);
            } else {
               this.supportFor.remove(key);
               if (this.scaffoldFails.merge(key, 1, Integer::sum) >= 2) {
                  this.badScaffold.add(key);
               }
            }
            break;
         case WATER_SOURCE:
         case WATERLOG:
         case LAVA_SOURCE:
            if (done) {
               this.bucketsEmptied++;
               this.pouredAt.put(key, this.tick);
            } else {
               this.waterFails.merge(key, 1, Integer::sum);
            }

            trace((done ? "poured into " : "could not pour into ") + this.filler.cell().toShortString() + " (" + this.filler.kind() + ")");
            break;
         case REFILL:
            if (done) {
               this.bucketsEmptied = Math.max(0, this.bucketsEmptied - 1);
            } else {
               this.nextRefillTick = this.tick + 100L;
            }
      }
   }

   private boolean mindTotem(ClientPlayerEntity player) {
      if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         if (this.totemSwapTick > 0L) {
            this.feedback("totem-ok", "Totem is in the offhand", 1);
         }

         this.totemPopped = false;
         this.totemSwapTick = 0L;
         return false;
      } else if (Hands.has(player, TOTEM)) {
         if (this.totemSwapTick > 0L && this.tick - this.totemSwapTick < 5L) {
            return true;
         }

         if (!Hands.toOffhand(this.mc, player, TOTEM)) {
            return false;
         }

         this.totemSwapTick = this.tick;
         return true;
      } else {
         if (this.tick < this.nextTotemBuyTick) {
            return false;
         }

         this.nextTotemBuyTick = this.tick + 1200L;
         if (!this.buyFromAh.get() || room(player, new ItemStack(Items.TOTEM_OF_UNDYING)) <= 0 && !this.startSelling(player, false)) {
            this.feedback("totem-none", "No totem for the offhand" + (this.buyFromAh.get() ? " and no room to buy one" : " (Buy From AH is off)"), 1200);
            this.totemPopped = false;
            return false;
         }

         if (this.sell != null) {
            return true;
         }

         this.feedback("totem-buy", this.totemPopped ? "Totem popped, buying a new one" : "No totem in the offhand, buying one", 1);
         this.totemPopped = false;
         return this.startPurchase(Items.TOTEM_OF_UNDYING, 1);
      }
   }

   private void debugLookUp(ClientWorld level, WorldSchematic schematic) {
      if (tracing() && this.tick - this.lastLookUpTrace >= 400L && this.scan != null) {
         for (BuildScan.Cell cell : this.scan.missing) {
            BlockPos pos = cell.pos();
            if (StateRules.lookFor(cell.expected()) == Direction.UP && !level.getBlockState(pos.up()).isAir()) {
               StringBuilder out = new StringBuilder(
                  "lookup "
                     + pos.toShortString()
                     + " waiting="
                     + this.scan.waiting.contains(pos.asLong())
                     + " deferred="
                     + this.deferred.contains(pos.asLong())
               );

               for (Direction side : Type.HORIZONTAL) {
                  BlockPos feet = pos.down().offset(side);
                  double floor = Walker.floor(level, feet);
                  Vec3d eye = new Vec3d(feet.getX() + 0.5, (Double.isNaN(floor) ? feet.getY() : floor) + 1.62, feet.getZ() + 0.5);
                  out.append(" ")
                     .append(side)
                     .append("[feet=")
                     .append(level.getBlockState(feet).getBlock().getName().getString())
                     .append(" head=")
                     .append(level.getBlockState(feet.up()).getBlock().getName().getString())
                     .append(" floor=")
                     .append(floor)
                     .append(" wait=")
                     .append(this.scan.waiting.contains(feet.asLong()))
                     .append(" ok=")
                     .append(this.placement.placeableFrom(cell.expected(), pos, eye))
                     .append("]");
               }

               trace(out.toString());
               this.lastLookUpTrace = this.tick;
               return;
            }
         }
      }
   }

   private void rescan(ClientWorld level, ClientPlayerEntity player) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      List<SchematicManager.ActiveBox> boxes = this.schematics.activeBoxes();
      this.nextScan = this.tick + 20L;
      this.lastScan = this.tick;
      if (this.placement.refusedCount() > this.refusedSeen) {
         this.refusedSeen = this.placement.refusedCount();
         this.feedback("refused", "The server refused " + this.refusedSeen + " blocks so far (ghost blocks); placing slower and trying them again", 200);
      }

      if (this.placement.placedCount() != this.placedAtScan) {
         this.stuckWalks = 0;
      }

      this.placedAtScan = this.placement.placedCount();
      if (schematic != null && !boxes.isEmpty()) {
         Box body = player.getBoundingBox().expand(0.05);
         this.scan = BuildScan.run(
            level,
            schematic,
            boxes,
            pos -> this.deferred.contains(pos.asLong()) || body.intersects(new Box(pos)) || enclosed(level, pos),
            pos -> this.deferred.contains(pos.asLong()),
            this::liquidPending,
            this.latePhase,
            this.scaffold,
            this.craftersDone,
            this.crafterSlots.get(),
            this.placeTnt.get(),
            this.machineLast.get()
         );
         this.debugLookUp(level, schematic);
         this.scaffold.removeIf(key -> {
            BlockPos pos = BlockPos.fromLong(key);
            if (!level.isChunkLoaded(pos)) {
               return false;
            }

            BlockState state = level.getBlockState(pos);
            return !Scaffold.block(state) || !schematic.getBlockState(pos).isAir() && StateRules.done(state, schematic.getBlockState(pos));
         });

         for (long key : this.scaffold) {
            if (!schematic.getBlockState(BlockPos.fromLong(key)).isAir()) {
               this.buildScaffold.add(key);
            }
         }

         this.missingKeys.clear();

         for (BuildScan.Cell cell : this.scan.missing) {
            this.missingKeys.add(cell.pos().asLong());
         }

         this.lackingKeys.clear();
         if (!this.buyFromAh.get()) {
            Map<Item, Boolean> lacks = new HashMap<>();

            for (BuildScan.Cell cell : this.scan.missing) {
               if (lacking(player, cell.expected(), lacks)) {
                  this.lackingKeys.add(cell.pos().asLong());
               }
            }
         }

         this.trackStubborn();
         if (this.scan.layer != Integer.MAX_VALUE && this.scan.layer != this.shownLayer) {
            this.shownLayer = this.scan.layer;
            long left = this.scan.missing.stream().filter(cellx -> cellx.pos().getY() == this.scan.layer).count();
            this.feedback("layer", "Layer Y=" + this.scan.layer + ", " + left + (left == 1L ? " block" : " blocks") + " to place", 1);
         }
      } else {
         this.scan = null;
         this.placing = false;
         this.feedback("no-schematic", "No active Litematica placement", 100);
      }
   }

   private void trackStubborn() {
      if (!this.scan.wholeSchematic) {
         int top = this.scan.layer + 1;
         this.tries.keySet().removeIf(key -> BlockPos.unpackLongY(key) <= top && !this.missingKeys.contains(key));
      } else {
         this.tries.keySet().retainAll(this.missingKeys);
      }

      this.stubborn.clear();

      for (Entry<Long, Integer> entry : this.tries.entrySet()) {
         if (entry.getValue() >= 8) {
            if (this.deferred.add(entry.getKey())) {
               this.feedback(
                  "gave-up-" + entry.getKey(), "The block at " + BlockPos.fromLong(entry.getKey()).toShortString() + " keeps disappearing, skipping it", 1
               );
            }
         } else if (entry.getValue() >= 3) {
            this.stubborn.add(entry.getKey());
         }
      }
   }

   private void rescanSoon() {
      this.nextScan = Math.min(this.nextScan, this.tick + 1L);
   }

   private void decide(ClientWorld level, ClientPlayerEntity player) {
      if (this.armed) {
         this.endOfBuild(level, player);
      } else {
         int layer = this.scan.layer;
         WorldSchematic shown = this.schematics.getSchematicWorld();
         Item routeItem = this.routeTarget != null && shown != null ? BlockStateUtil.requiredItem(shown.getBlockState(this.routeTarget)) : null;
         this.routeItemTicks = routeItem != null && count(player, routeItem) > 0 ? this.routeItemTicks + 1 : 0;
         int top = layer != Integer.MAX_VALUE ? layer : this.highestMissing();
         int water = top == Integer.MIN_VALUE ? 0 : this.waterWanted(top);
         int lava = top == Integer.MIN_VALUE ? 0 : this.cappedLavaWanted(top);
         if (water <= 0 || !this.refillWater(level, player, water)) {
            if (this.buyFromAh.get() && top != Integer.MIN_VALUE) {
               Map<Item, Integer> needs = this.scan.needs(top);
               if (water > 0) {
                  needs.put(Items.WATER_BUCKET, water);
               }

               if (lava > 0) {
                  needs.put(Items.LAVA_BUCKET, Math.min(lava, 8));
               }

               if (this.buyMissing(player, needs)) {
                  return;
               }
            }

            BlockPos leaf = this.cleanupLeaf(player);
            if (leaf != null) {
               this.breaker = new Breaker(leaf, false);
               this.placing = false;
            } else if (!this.sweepFinishedLayers(level, player)) {
               if (this.crafterSlots.get()) {
                  for (BlockPos pos : this.scan.crafters) {
                     if (this.inReach(player, pos) && Aim.at(this.mc, pos) != null) {
                        this.user = BlockUser.crafter(pos, this.scan.crafterSlots.get(pos));
                        this.placing = false;
                        return;
                     }
                  }
               }

               if (this.clickStates.get()) {
                  for (BuildScan.Cell cell : this.scan.adjust) {
                     if (!this.clickFailed.contains(cell.pos().asLong()) && this.inReach(player, cell.pos()) && Aim.at(this.mc, cell.pos()) != null) {
                        this.user = BlockUser.adjust(cell.pos(), cell.expected());
                        this.placing = false;
                        return;
                     }
                  }
               }

               if (!this.placeWater.get() || !this.waterNow(player) && !this.cappedLavaNow(level, player)) {
                  if (top == Integer.MIN_VALUE) {
                     this.endOfBuild(level, player);
                  } else {
                     Vec3d eye = player.getEyePos();
                     Set<Long> waiting = this.scan.waiting;
                     Set<Long> exit = lastExit(level, player);
                     this.placement
                        .setScope(
                           new BuildScope(
                              top,
                              40.0,
                              pos -> waiting.contains(pos.asLong())
                                 || exit.contains(pos.asLong())
                                 || this.stubborn.contains(pos.asLong()) && pos.toCenterPos().distanceTo(eye) > 2.8
                                 || this.deferred.contains(pos.asLong()) && this.tries.getOrDefault(pos.asLong(), 0) >= 8
                           )
                        );
                     if (!this.placing) {
                        this.placing = true;
                        this.placingSince = this.tick;
                     }

                     if (this.tick - this.placingSince >= 4L && this.placement.ticksWithoutTarget() >= 4L) {
                        if (!this.supportLeaves.get() || !this.placeSupport(level, player, top, true)) {
                           if (!this.supportLeaves.get()
                              || this.routeTarget == null
                              || this.tick - this.arrivedAt >= 34L
                              || !this.placeSupportFor(level, player, this.routeTarget)) {
                              if (this.walking || this.tick < this.noPlanUntil || !this.plan(level, player, top)) {
                                 int feet = player.getBlockPos().getY();
                                 if (!this.walking || layer == Integer.MAX_VALUE || layer < feet + 2 || !this.goUp(level, player)) {
                                    if (this.walking && layer != Integer.MAX_VALUE && layer >= feet && this.stuckWalks >= 2 && this.goUp(level, player)) {
                                       this.stuckWalks = 0;
                                    } else if (!this.walkTowardsWork(level, player, top)) {
                                       if (!this.supportLeaves.get() || !this.placeSupport(level, player, top, false)) {
                                          if (!this.walking || layer == Integer.MAX_VALUE || layer < feet || !this.goUp(level, player)) {
                                             if (layer == Integer.MAX_VALUE) {
                                                this.endOfBuild(level, player);
                                             } else if (this.tick - this.lastStuckTick >= 60L) {
                                                this.lastStuckTick = this.tick;
                                                BuildScan.Cell stuck = this.nearestOpen(player, top);
                                                if (stuck != null) {
                                                   trace("stuck, skipping " + stuck.pos().toShortString() + " at " + player.getBlockPos().toShortString());
                                                   this.deferred.add(stuck.pos().asLong());
                                                }

                                                this.feedback("stuck", "Can't reach the rest of layer Y=" + layer + " from here, skipping it for now", 100);
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
   }

   private static Set<Long> lastExit(ClientWorld level, ClientPlayerEntity player) {
      BlockPos feet = player.getBlockPos();
      List<BlockPos> exits = Walker.steps(level, feet);
      if (exits.size() != 1) {
         return Set.of();
      }

      BlockPos out = exits.get(0);
      Set<Long> keep = new HashSet<>();

      for (int dy = 0; dy <= 2; dy++) {
         keep.add(out.up(dy).asLong());
      }

      if (out.getY() > feet.getY()) {
         keep.add(feet.up(2).asLong());
      }

      keep.add(feet.offset(Direction.getFacing(out.getX() - feet.getX(), 0.0F, out.getZ() - feet.getZ())).up().asLong());
      return keep;
   }

   private int highestMissing() {
      int best = Integer.MIN_VALUE;

      for (BuildScan.Cell cell : this.scan.missing) {
         if (!this.scan.waiting.contains(cell.pos().asLong())) {
            best = Math.max(best, cell.pos().getY());
         }
      }

      return best;
   }

   private boolean goUp(ClientWorld level, ClientPlayerEntity player) {
      BlockPos spot = Hop.spot(level, player, pos -> false);
      if (spot != null) {
         this.stopMoving();
         this.hop = new Hop(spot);
         return true;
      } else {
         return this.startClimb(player);
      }
   }

   private boolean placeSupport(ClientWorld level, ClientPlayerEntity player, int top, boolean waitForNeighbours) {
      for (BuildScan.Cell cell : this.scan.missing) {
         BlockPos pos = cell.pos();
         if (pos.getY() <= top
            && !this.deferred.contains(pos.asLong())
            && !this.scan.waiting.contains(pos.asLong())
            && this.inReach(player, pos)
            && !(cell.expected().getBlock() instanceof FallingBlock)
            && (StateRules.lookFor(cell.expected()) != Direction.UP || pos.equals(this.routeTarget))
            && (!waitForNeighbours || !this.missingNeighbour(pos) && !bridgeable(level, cell.expected(), pos))
            && this.placeSupportFor(level, player, pos)) {
            return true;
         }
      }

      return false;
   }

   private static boolean bridgeable(ClientWorld level, BlockState expected, BlockPos pos) {
      if (StateRules.lookFor(expected) == null
         && StateRules.flatLookFor(expected) == null
         && !(expected.getBlock() instanceof FallingBlock)
         && expected.isFullCube(level, pos)) {
         for (Direction side : Type.HORIZONTAL) {
            BlockPos next = pos.offset(side);
            if (Block.isFaceFullSquare(level.getBlockState(next).getOutlineShape(level, next), side.getOpposite()) && Walker.roomAt(level, next.up())) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean placeSupportFor(ClientWorld level, ClientPlayerEntity player, BlockPos pos) {
      if ((!this.latePhase || !this.placeTnt.get()) && this.missingKeys.contains(pos.asLong()) && !this.scan.waiting.contains(pos.asLong())) {
         WorldSchematic schematic = this.schematics.getSchematicWorld();
         if (schematic == null) {
            return false;
         }

         BlockState expected = schematic.getBlockState(pos);
         if (!(expected.getBlock() instanceof FallingBlock) && !this.placement.placeableFrom(expected, pos, player.getEyePos())) {
            List<BlockPos> chain = this.scaffoldFor(level, expected, pos, player.getEyePos());
            if (chain == null) {
               return false;
            }

            BlockPos next = chain.get(0);
            if (!CellFiller.reachable(this.mc, next, CellFiller.Kind.SUPPORT_LEAF)) {
               return false;
            }

            if (Scaffold.count(player) >= 1) {
               trace("scaffold " + next.toShortString() + " (" + chain.size() + " to go) for " + pos.toShortString());
               this.stopMoving();
               this.supportFor.put(next.asLong(), pos.asLong());
               if (pos.equals(this.routeTarget)) {
                  this.proppedAt = this.tick;
               }

               if (!schematic.getBlockState(next).isAir()) {
                  this.buildScaffold.add(next.asLong());
               }

               this.filler = new CellFiller(next, CellFiller.Kind.SUPPORT_LEAF);
               return true;
            } else {
               if (this.buyFromAh.get()) {
                  return this.buyLeaves();
               }

               this.feedback("no-scaffold", "Need glass (or leaves) to prop blocks up", 200);
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private List<BlockPos> scaffoldFor(ClientWorld level, BlockState expected, BlockPos pos, Vec3d eye) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (schematic == null) {
         return null;
      }

      double reach = (this.mc.player == null ? 4.5 : this.mc.player.getBlockInteractionRange()) - 0.6;
      Direction look = StateRules.lookFor(expected);
      Direction flat = StateRules.flatLookFor(expected);
      Box body = new Box(eye.x - 0.3, eye.y - 1.62, eye.z - 0.3, eye.x + 0.3, eye.y + 0.18, eye.z + 0.3);

      for (int pass = 0; pass < 2; pass++) {
         for (BlockPos top : BuildOrder.supportCells(expected, pos)) {
            boolean fits = pass == 0 ? scaffoldFits(level, schematic, top) : this.buildCellFits(level, schematic, top);
            if (fits && !this.badScaffold.contains(top.asLong()) && !body.intersects(new Box(top))) {
               Direction toward = Direction.getFacing(pos.getX() - top.getX(), pos.getY() - top.getY(), pos.getZ() - top.getZ());
               Vec3d face = top.toCenterPos().offset(toward, 0.5);
               Vec3d to = face.subtract(eye);
               boolean fromBelowLater = look == Direction.UP && top.equals(pos.up());
               if (fromBelowLater
                  ? !(eye.y <= top.getY() + 0.05) && !(top.toCenterPos().distanceTo(eye) > reach + 0.5)
                  : !(to.length() > reach)
                     && PlacementUtil.isFaceVisibleToEye(top, toward, eye)
                     && (look == null || Walker.dominant(to, look))
                     && (flat == null || Walker.facingFlat(to, flat))) {
                  List<BlockPos> chain = new ArrayList<>();
                  BlockPos cell = top;
                  boolean grounded = false;

                  for (int i = 0;
                     i < 4
                        && (i <= 0 || scaffoldFits(level, schematic, cell) && !body.intersects(new Box(cell)))
                        && !(cell.toCenterPos().distanceTo(eye) > reach + 0.5);
                     i++
                  ) {
                     chain.add(cell);
                     if (i == 0 && sideBase(level, cell, pos, eye) || solidUnder(level, cell) && eye.y > cell.getY() + 0.05) {
                        grounded = true;
                        break;
                     }

                     cell = cell.down();
                  }

                  if (!grounded && chain.size() >= 1 && scaffoldFits(level, schematic, top)) {
                     List<BlockPos> beside = this.besideChain(level, schematic, top, pos, eye, body, reach);
                     if (beside != null) {
                        return beside;
                     }
                  }

                  if (grounded && (chain.size() <= 1 || !(eye.y <= top.getY() + 0.05))) {
                     Collections.reverse(chain);
                     if (Aim.against(this.mc, chain.get(0), null, eye) != null) {
                        return chain;
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private List<BlockPos> besideChain(ClientWorld level, WorldSchematic schematic, BlockPos top, BlockPos forPos, Vec3d eye, Box body, double reach) {
      if (eye.y <= top.getY() + 0.05) {
         return null;
      }

      for (Direction side : Type.HORIZONTAL) {
         BlockPos piece = top.offset(side);
         if (!piece.equals(forPos)
            && scaffoldFits(level, schematic, piece)
            && solidUnder(level, piece)
            && !body.intersects(new Box(piece))
            && !(piece.toCenterPos().distanceTo(eye) > reach + 0.5)
            && !this.badScaffold.contains(piece.asLong())
            && Aim.against(this.mc, piece, null, eye) != null) {
            return new ArrayList<>(List.of(piece, top));
         }
      }

      return null;
   }

   private boolean buildCellFits(ClientWorld level, WorldSchematic schematic, BlockPos cell) {
      if (this.missingKeys.contains(cell.asLong()) && level.getBlockState(cell).isAir() && !waterCell(level, schematic, cell)) {
         for (Direction way : Direction.values()) {
            BlockPos next = cell.offset(way);
            BlockState watcher = schematic.getBlockState(next);
            if (watcher.getBlock() instanceof ObserverBlock && next.offset(watcher.get(Properties.FACING)).equals(cell)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean sideBase(ClientWorld level, BlockPos cell, BlockPos forPos, Vec3d eye) {
      for (Direction way : Direction.values()) {
         BlockPos base = cell.offset(way);
         if (!base.equals(forPos)) {
            BlockState state = level.getBlockState(base);
            if (!state.isReplaceable() && !state.getOutlineShape(level, base).isEmpty() && PlacementUtil.isFaceVisibleToEye(base, way.getOpposite(), eye)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean solidUnder(ClientWorld level, BlockPos cell) {
      BlockState under = level.getBlockState(cell.down());
      return !under.isReplaceable() && !under.getOutlineShape(level, cell.down()).isEmpty();
   }

   private boolean neededNow(BlockPos pos) {
      int top = this.scan != null && this.scan.layer != Integer.MAX_VALUE ? this.scan.layer : Integer.MAX_VALUE;
      Mutable cell = new Mutable();

      for (int dy = -1; dy <= 1; dy++) {
         if (pos.getY() + dy <= top) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  long key = cell.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz).asLong();
                  if (this.missingKeys.contains(key) && !this.deferred.contains(key) && !this.scan.waiting.contains(key) && !this.lackingKeys.contains(key)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean missingNeighbour(BlockPos pos) {
      for (Direction direction : Direction.values()) {
         long key = pos.offset(direction).asLong();
         if (this.missingKeys.contains(key) && !this.deferred.contains(key) && !this.scan.waiting.contains(key) && !this.lackingKeys.contains(key)) {
            return true;
         }
      }

      return false;
   }

   private boolean pillarFits(ClientWorld level, BlockPos column) {
      Long bad = this.badClimbs.get(column.asLong());
      if (bad != null && bad > this.tick) {
         return false;
      }

      if (level.getFluidState(column).getHeight(level, column) > 0.4F) {
         return false;
      }

      WorldSchematic schematic = this.schematics.getSchematicWorld();

      for (int dy = 0; dy < 2; dy++) {
         BlockPos cell = column.up(dy);
         if (!scaffoldFits(level, schematic, cell) && !runningWaterFits(level, schematic, cell)) {
            return false;
         }
      }

      return Climber.hasRoom(this.mc, column);
   }

   private boolean digOut(ClientWorld level, ClientPlayerEntity player) {
      BlockPos feet = player.getBlockPos();
      BlockState here = level.getBlockState(feet);
      if (!here.isAir()
         && !here.isReplaceable()
         && !here.isFullCube(level, feet)
         && !(level.getBlockEntity(feet) instanceof Inventory)
         && !(here.getHardness(level, feet) < 0.0F)) {
         for (Direction side : Direction.values()) {
            if (!level.getFluidState(feet.offset(side)).isEmpty()) {
               return false;
            }
         }

         trace("shut in on " + here + " at " + feet.toShortString() + ", mining it to pillar out");
         this.feedback("dig-out", "Shut in at " + feet.toShortString() + ", mining the block underfoot to climb out (it goes back in later)", 100);
         this.stopMoving();
         this.breaker = new Breaker(feet, false);
         return true;
      } else {
         return false;
      }
   }

   private boolean escapeClimb(ClientWorld level) {
      BlockPos column = Climber.column(this.mc);
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (column != null && schematic != null && Climber.hasRoom(this.mc, column)) {
         for (int dy = 0; dy < 2; dy++) {
            BlockPos cell = column.up(dy);
            if (!level.getBlockState(cell).isReplaceable()
               || !level.getFluidState(cell).isEmpty() && !runningWaterFits(level, schematic, cell)
               || schematic.getBlockState(cell).getBlock() instanceof FluidBlock) {
               return false;
            }
         }

         for (int dy = 0; dy < 2; dy++) {
            BlockPos cell = column.up(dy);
            if (!schematic.getBlockState(cell).isAir()) {
               this.buildScaffold.add(cell.asLong());
            }
         }

         trace("shut in at " + column.toShortString() + ", pillaring out");
         this.stopMoving();
         this.climber = new Climber(column, this.scaffold);
         return true;
      } else {
         return false;
      }
   }

   private static boolean runningWaterFits(ClientWorld level, WorldSchematic schematic, BlockPos cell) {
      FluidState now = level.getFluidState(cell);
      if (!now.isEmpty() && !now.isStill() && level.getBlockState(cell).isReplaceable()) {
         BlockState planned = schematic == null ? Blocks.AIR.getDefaultState() : schematic.getBlockState(cell);
         return planned.isAir() || planned.getBlock() instanceof FluidBlock && !planned.getFluidState().isStill();
      } else {
         return false;
      }
   }

   static boolean scaffoldFits(ClientWorld level, WorldSchematic schematic, BlockPos cell) {
      BlockState there = level.getBlockState(cell);
      if (there.isReplaceable() && !waterCell(level, schematic, cell)) {
         if (schematic != null && !schematic.getBlockState(cell).isAir()) {
            return false;
         }

         for (Direction way : Direction.values()) {
            BlockPos next = cell.offset(way);
            BlockState watcher = schematic != null ? schematic.getBlockState(next) : level.getBlockState(next);
            if (watcher.getBlock() instanceof ObserverBlock && next.offset(watcher.get(Properties.FACING)).equals(cell)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean waterCell(ClientWorld level, WorldSchematic schematic, BlockPos pos) {
      return !level.getFluidState(pos).isEmpty() || schematic != null && !schematic.getBlockState(pos).getFluidState().isEmpty();
   }

   private static boolean floating(ClientWorld level, BlockPos pos) {
      for (Direction direction : Direction.values()) {
         if (!level.getBlockState(pos.offset(direction)).isReplaceable()) {
            return false;
         }
      }

      return true;
   }

   private boolean buyMissing(ClientPlayerEntity player, Map<Item, Integer> needs) {
      if (this.tick - this.lastPlaceTick < 40L) {
         return false;
      }

      List<Entry<Item, Integer>> wanted = new ArrayList<>(needs.entrySet());
      wanted.sort(Comparator.comparing(entryx -> Registries.ITEM.getId((Item)entryx.getKey()).toString()));

      for (Entry<Item, Integer> entry : wanted) {
         Item item = entry.getKey();
         int missing = entry.getValue() - count(player, item);
         if (missing > 0) {
            int room = room(player, new ItemStack(item));
            if (item == Items.WATER_BUCKET) {
               room = Math.min(room - 4, 8);
            }

            if (room > 0) {
               return this.startPurchase(item, Math.min(buyAmount(item, missing), room));
            }

            if (this.startSelling(player, false)) {
               return true;
            }
         }
      }

      return false;
   }

   private static int buyAmount(Item item, int missing) {
      int stack = new ItemStack(item).getMaxCount();
      return missing > 16 && stack > 16 ? (missing + stack - 1) / stack * stack : missing;
   }

   private boolean startPurchase(Item item, int amount) {
      long left = this.remainingBudget();
      if (left < 0L) {
         this.say(ModuleChat.body().warn("Max Total Spend reached").text(", stopping."));
         this.requestDisable();
         return true;
      } else {
         this.stopMoving();
         this.purchaseItem = item;
         this.purchaseNeed = this.mc.player == null ? amount : count(this.mc.player, item) + amount;
         this.purchaseName = new ItemStack(item).getName().getString();
         this.purchase = item == Items.GLASS
            ? AhPurchase.startAny(
               this, Items.GLASS, "Glass", "glass stack", stack -> stack.isOf(Items.GLASS), amount, this.maxListing, this.refresh.getInt(), left
            )
            : AhPurchase.start(this, item, amount, this.maxListing, this.refresh.getInt(), left);
         return true;
      }
   }

   private boolean buyLeaves() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && (room(player, new ItemStack(Items.GLASS)) > 0 || this.startSelling(player, false))) {
         if (this.sell != null) {
            return true;
         } else {
            long left = this.remainingBudget();
            if (left < 0L) {
               this.say(ModuleChat.body().warn("Max Total Spend reached").text(", stopping."));
               this.requestDisable();
               return true;
            } else {
               this.stopMoving();
               this.purchaseItem = null;
               this.purchaseName = "Leaves";
               this.purchase = AhPurchase.startAny(this, Items.GLASS, "Glass", "glass stack", Scaffold::glass, 16, this.maxListing, this.refresh.getInt(), left);
               return true;
            }
         }
      } else {
         this.feedback("leaves-room", "No free inventory slot for leaves", 200);
         return false;
      }
   }

   private long remainingBudget() {
      if (this.spendLimit <= 0L) {
         return 0L;
      }

      long left = this.spendLimit - this.spent;
      return left > 0L ? left : -1L;
   }

   private void followPurchase() {
      AhPurchase.Status status = this.purchase.status();
      if (status != AhPurchase.Status.RUNNING) {
         this.spent = this.spent + this.purchase.spent();
         if (this.purchaseItem != null) {
            this.bought.merge(this.purchaseItem, this.purchase.boughtItems(), Integer::sum);
         } else {
            this.leavesBought = this.leavesBought + this.purchase.boughtItems();
         }

         this.purchase = null;
         this.rescanSoon();
         if (status == AhPurchase.Status.FAILED) {
            this.say(ModuleChat.body().danger("Could not buy " + this.purchaseName).text(", Schematic Builder stopped."));
            this.requestDisable();
         }
      }
   }

   private boolean startClimb(ClientPlayerEntity player) {
      if (!this.climb.get()) {
         this.feedback("climb-off", "The next layer is out of reach; turn on Climb With Leaves to go up", 200);
         return false;
      }

      if (Climber.leavesCount(player) < 2) {
         if (this.buyFromAh.get()) {
            return this.buyLeaves();
         }

         this.feedback("no-leaves", "Need at least 2 leaves to climb", 200);
         return false;
      } else if (this.tick < this.noClimbUntil) {
         return false;
      } else {
         BlockPos column = Climber.column(this.mc);
         if (column != null && this.pillarFits(this.mc.world, column)) {
            this.stopMoving();
            this.placing = false;
            this.climber = new Climber(column, this.scaffold);
            this.feedback("climbing", "Climbing 2 blocks with leaves", 20);
            return true;
         } else {
            this.feedback("climb-room", "No room to pillar here (solid ground, no water, no placed block in the way)", 200);
            return false;
         }
      }
   }

   private boolean walkTowardsWork(ClientWorld level, ClientPlayerEntity player, int top) {
      List<BuildScan.Cell> open = new ArrayList<>();

      for (BuildScan.Cell cell : this.scan.missing) {
         long key = cell.pos().asLong();
         if (cell.pos().getY() <= top && !this.deferred.contains(key) && !this.scan.waiting.contains(key) && !enclosed(level, cell.pos())) {
            open.add(cell);
         }
      }

      open.sort(Comparator.comparingDouble(cellx -> cellx.pos().getSquaredDistance(player.getEntityPos())));
      BlockPos at = player.getBlockPos();

      for (int i = 0; i < open.size() && i < 16; i++) {
         BuildScan.Cell cell = open.get(i);
         if (!this.walking) {
            return false;
         }

         boolean close = this.stubborn.contains(cell.pos().asLong());
         BlockPos stand = close
            ? Walker.standFor(level, player, cell.pos(), cell.expected(), this::badStand, 2.2)
            : Walker.standFor(level, player, cell.pos(), cell.expected(), this::badStand);
         if (stand != null && !stand.equals(at)) {
            this.walkTarget = cell.pos();
            this.walkTo(player, stand);
            return true;
         }

         if (this.walkFails.merge(cell.pos().asLong(), 1, Integer::sum) >= 2) {
            this.deferred.add(cell.pos().asLong());
         }
      }

      return false;
   }

   private boolean badStand(BlockPos pos) {
      Long until = this.badStands.get(pos.asLong());
      if (until == null) {
         return false;
      }

      if (until > this.tick) {
         return true;
      }

      this.badStands.remove(pos.asLong());
      return false;
   }

   private boolean plan(ClientWorld level, ClientPlayerEntity player, int top) {
      this.missedRouteTarget(player);
      if (this.climbTo != Integer.MIN_VALUE) {
         BlockPos column = player.getBlockPos().getY() < this.climbTo && Climber.leavesCount(player) >= 2 && this.tick >= this.noClimbUntil
            ? Climber.column(this.mc)
            : null;
         if (column != null && this.pillarFits(level, column)) {
            this.stopMoving();
            this.climber = new Climber(column, this.scaffold);
            return true;
         }

         this.climbTo = Integer.MIN_VALUE;
      }

      List<StandPlanner.Target> targets = new ArrayList<>();
      Map<Item, Integer> lacking = new HashMap<>();

      for (BuildScan.Cell cell : this.scan.missing) {
         if (cell.pos().getY() <= top && !this.deferred.contains(cell.pos().asLong()) && !this.scan.waiting.contains(cell.pos().asLong())) {
            Item item = BlockStateUtil.requiredItem(cell.expected());
            if (item != null) {
               if (count(player, item) <= 0) {
                  lacking.merge(item, 1, Integer::sum);
               } else {
                  targets.add(
                     new StandPlanner.Target(
                        cell.pos(),
                        cell.expected(),
                        StandPlanner.Use.PLACE,
                        this.stubborn.contains(cell.pos().asLong()) ? 2.5999999999999996 : Double.MAX_VALUE
                     )
                  );
               }
            }
         }
      }

      if (Hands.has(player, stack -> stack.isOf(Items.WATER_BUCKET))) {
         for (BuildScan.Liquid liquid : this.scan.liquids) {
            if (liquid.kind() != CellFiller.Kind.LAVA_SOURCE && this.dueNow(liquid) && this.waterPending(liquid.pos())) {
               targets.add(liquidTarget(level, liquid));
            }
         }
      }

      if (Hands.has(player, stack -> stack.isOf(Items.LAVA_BUCKET)) && !this.lavaGivenUp) {
         WorldSchematic schematic = this.schematics.getSchematicWorld();
         List<StandPlanner.Target> lava = new ArrayList<>();

         for (BuildScan.Liquid liquid : this.scan.liquids) {
            if (liquid.capped()
               && (this.scan.layer == Integer.MAX_VALUE || liquid.pos().getY() < this.scan.layer)
               && this.liquidPending(liquid.pos())
               && schematic != null
               && walledIn(level, schematic, liquid.pos())) {
               lava.add(liquidTarget(level, liquid));
            }
         }

         if (!lava.isEmpty() && this.tick >= this.lavaFirstUntil) {
            this.lavaFirstUntil = this.tick + 200L;
            this.lavaPlanning = true;
            this.startPlanner(level, player, lava);
            return true;
         }

         targets.addAll(lava);
      }

      if (!lacking.isEmpty() && !this.buyFromAh.get()) {
         List<String> names = new ArrayList<>();
         lacking.forEach((itemx, n) -> names.add(n + "x " + new ItemStack(itemx).getName().getString()));
         this.feedback(
            "lacking",
            "Missing in inventory: "
               + String.join(", ", names.subList(0, Math.min(6, names.size())))
               + (names.size() > 6 ? " and more" : "")
               + " (Buy From AH is off)",
            400
         );
      }

      if (targets.isEmpty()) {
         return false;
      }

      this.startPlanner(level, player, targets);
      return true;
   }

   private void missedRouteTarget(ClientPlayerEntity player) {
      if (this.routeTarget != null && this.tick - this.arrivedAt < 34L) {
         long key = this.routeTarget.asLong();
         if (this.missingKeys.contains(key) && this.placement.placedCount() > this.placedAtArrival) {
            trace("placed others instead of " + this.routeTarget.toShortString());
         } else if (this.missingKeys.contains(key) && this.proppedAt >= this.arrivedAt) {
            trace("propped " + this.routeTarget.toShortString() + ", placing it comes next");
         } else if (this.missingKeys.contains(key) && this.routeItemTicks < 4) {
            trace("arrived without the item for " + this.routeTarget.toShortString());
         } else if (this.missingKeys.contains(key)) {
            WorldSchematic schematic = this.schematics.getSchematicWorld();
            boolean now = tracing()
               && schematic != null
               && this.placement.placeableFrom(schematic.getBlockState(this.routeTarget), this.routeTarget, player.getEyePos());
            if (tracing()) {
               trace(
                  "arrived but did not place "
                     + this.routeTarget.toShortString()
                     + " standing at "
                     + player.getBlockPos().toShortString()
                     + " planned "
                     + (this.routeStand == null ? "?" : this.routeStand.toShortString())
                     + " pos="
                     + player.getEntityPos()
                     + " exactNow="
                     + now
                     + " waiting="
                     + this.scan.waiting.contains(key)
                     + " stubborn="
                     + this.stubborn.contains(key)
                     + " "
                     + this.placement.debugState(this.routeTarget)
               );
            }

            if (this.walkFails.merge(key, 1, Integer::sum) >= 2) {
               this.deferred.add(key);
            }
         } else if (this.routeUse == StandPlanner.Use.BREAK && this.scaffold.contains(key)) {
            trace("arrived but did not mine the leaf at " + this.routeTarget.toShortString());
            this.walkFails.merge(key, 1, Integer::sum);
         } else if ((this.routeUse == StandPlanner.Use.POUR || this.routeUse == StandPlanner.Use.WATERLOG)
            && this.scan.liquids.stream().anyMatch(liquid -> liquid.pos().asLong() == key)) {
            trace("arrived but could not pour into " + this.routeTarget.toShortString());
            this.waterFails.merge(key, 1, Integer::sum);
         }
      }

      this.routeTarget = null;
   }

   private static StandPlanner.Target liquidTarget(ClientWorld level, BuildScan.Liquid liquid) {
      return new StandPlanner.Target(
         liquid.pos(), level.getBlockState(liquid.pos()), liquid.kind() == CellFiller.Kind.WATERLOG ? StandPlanner.Use.WATERLOG : StandPlanner.Use.POUR
      );
   }

   private void startPlanner(ClientWorld level, ClientPlayerEntity player, List<StandPlanner.Target> targets) {
      Vec3d at = player.getEntityPos();
      targets.sort(
         Comparator.<StandPlanner.Target>comparingInt(target -> target.pos().getY()).thenComparingDouble(target -> target.pos().getSquaredDistance(at))
      );
      if (targets.size() > 600) {
         targets = new ArrayList<>(targets.subList(0, 600));
      }

      this.plannedKeys.clear();

      for (StandPlanner.Target target : targets) {
         this.plannedKeys.add(target.pos().asLong());
      }

      final WorldSchematic schematic = this.schematics.getSchematicWorld();
      boolean scaffoldOk = !this.latePhase || !this.placeTnt.get();
      boolean pillars = this.climb.get() && scaffoldOk && Climber.leavesCount(player) >= 2 && this.tick >= this.noClimbUntil;
      boolean bridges = this.climb.get() && scaffoldOk && !this.sweepPlanning && Climber.leavesCount(player) >= 1;
      trace(
         "plan from "
            + player.getBlockPos().toShortString()
            + " targets="
            + targets.size()
            + " first="
            + targets.get(0).pos().toShortString()
            + " pillars="
            + pillars
            + " leaves="
            + Climber.leavesCount(player)
      );
      this.planner = new StandPlanner(
            level,
            player,
            targets,
            pos -> this.badStand(pos) || this.scaffold.contains(pos.down().asLong()) && !supports(player, pos.down()) && !this.walkway(pos.down()),
            pillars ? (pos, onLeaf) -> (onLeaf || Walker.floor(level, pos) == pos.getY()) && this.pillarFits(level, pos) : null,
            bridges ? cell -> scaffoldFits(level, schematic, cell) && !this.scan.waiting.contains(cell.asLong()) : null,
            this.placement::placeableFrom,
            new StandPlanner.Support() {
               @Override
               public boolean possible(BlockPos pos, Vec3d eye) {
                  return schematic != null && SchematicBuilderModule.this.scaffoldFor(level, schematic.getBlockState(pos), pos, eye) != null;
               }

               @Override
               public boolean behind(BlockState expected, BlockPos pos, Vec3d eye) {
                  return SchematicBuilderModule.this.scaffoldFor(level, expected, pos, eye) != null;
               }
            }
         )
         .falls(!this.fallsNext ? 0.0 : (player.getHealth() >= 18.0F ? 8.0 : (player.getHealth() >= 15.0F ? 5.0 : 0.0)));
      this.planWithFalls = this.fallsNext;
      this.fallsNext = false;
      this.placing = false;
      this.followPlanner();
   }

   private int flatRunAhead(BlockPos from) {
      int run = 0;

      for (int i = this.routeIndex; i < this.route.size(); i++) {
         StandPlanner.Step step = this.route.get(i);
         if (step.move() != StandPlanner.Move.WALK || step.pos().getY() != from.getY()) {
            break;
         }

         run++;
      }

      return run;
   }

   private void followPlanner() {
      StandPlanner.Status status = this.planner.step(System.nanoTime() + 3000000L);
      if (status != StandPlanner.Status.RUNNING) {
         StandPlanner done = this.planner;
         this.planner = null;
         if (status == StandPlanner.Status.NONE && done.visited() <= 2 && this.mc.world != null && this.mc.player != null && tracing()) {
            trace(
               "boxed in: " + Walker.explain(this.mc.world, this.mc.player.getBlockPos()) + " pos " + this.mc.player.getEntityPos() + " why " + done.whyStuck()
            );
         }

         if (status == StandPlanner.Status.NONE && done.visited() > 2 && tracing()) {
            trace("plan NONE extent " + done.extent());
         }

         trace(
            "plan "
               + status
               + " visited="
               + done.visited()
               + (status == StandPlanner.Status.FOUND ? " target=" + done.target().pos().toShortString() + " route=" + done.route() : "")
         );
         if (status == StandPlanner.Status.APPROACH && !done.route().isEmpty()) {
            trace("approach " + done.route());
            if (this.lavaPlanning) {
               this.lavaFirstUntil = 0L;
            }

            this.lavaPlanning = false;
            this.sweepPlanning = false;
            this.route = done.route();
            this.routeIndex = 0;
            this.routeStart = this.mc.player == null ? null : this.mc.player.getBlockPos();
            this.routeTarget = null;
            this.routeUse = null;
            this.routeStand = this.route.get(this.route.size() - 1).pos();
         } else {
            if (this.lavaPlanning && status == StandPlanner.Status.FOUND) {
               this.lavaFirstUntil = 0L;
            }

            boolean wasLava = this.lavaPlanning;
            this.lavaPlanning = false;
            if (wasLava && status == StandPlanner.Status.NONE) {
               this.noPlanUntil = this.tick;
            } else if (status == StandPlanner.Status.NONE
               && !this.planWithFalls
               && !this.sweepPlanning
               && this.mc.player != null
               && this.mc.player.getHealth() >= 15.0F) {
               this.fallsNext = true;
               this.noPlanUntil = this.tick;
            } else {
               if (this.sweepPlanning && status == StandPlanner.Status.NONE) {
                  this.sweepDue = false;
               }

               if (!this.sweepPlanning && status == StandPlanner.Status.NONE) {
                  this.deferUnplanned();
               }

               this.sweepPlanning = false;
               if (status == StandPlanner.Status.FOUND && !done.route().isEmpty()) {
                  this.route = done.route();
                  this.routeIndex = 0;
                  this.routeStart = this.mc.player == null ? null : this.mc.player.getBlockPos();
                  this.routeTarget = done.target().pos();
                  this.routeUse = done.target().use();
                  this.routeStand = this.route.get(this.route.size() - 1).pos();
               } else {
                  this.noPlanUntil = this.tick + 40L;
                  if (status == StandPlanner.Status.NONE
                     && this.mc.player != null
                     && this.mc.world != null
                     && done.lowestTargetY() - this.mc.player.getBlockPos().getY() > 4
                     && Climber.leavesCount(this.mc.player) >= 2
                     && this.tick >= this.noClimbUntil) {
                     BlockPos column = Climber.column(this.mc);
                     if (column != null && this.pillarFits(this.mc.world, column)) {
                        trace("far below the work, climbing at " + column.toShortString());
                        this.climbTo = done.lowestTargetY() - 4;
                        this.stopMoving();
                        this.climber = new Climber(column, this.scaffold);
                        return;
                     }
                  }

                  if (status != StandPlanner.Status.NONE
                     || done.visited() > 3
                     || this.mc.player == null
                     || this.mc.world == null
                     || Climber.leavesCount(this.mc.player) < 2
                     || !this.escapeClimb(this.mc.world) && !this.digOut(this.mc.world, this.mc.player)) {
                     ClientPlayerEntity player = this.mc.player;
                     BlockPos leaf = player != null && done.visited() <= 3 ? this.supportingLeaf(player) : null;
                     if (leaf != null && this.dropBelow(leaf) <= 4) {
                        trace("stranded on the leaf at " + leaf.toShortString() + ", mining it to get down");
                        this.stopMoving();
                        this.breaker = new Breaker(leaf, true);
                     }
                  }
               }
            }
         }
      }
   }

   private void deferUnplanned() {
      int counted = 0;

      for (long key : this.plannedKeys) {
         if (counted++ >= 4) {
            break;
         }

         if (this.walkFails.merge(key, 1, Integer::sum) >= 2 && this.deferred.add(key)) {
            trace("no plan reaches " + BlockPos.fromLong(key).toShortString() + ", leaving it for later");
         }
      }
   }

   private void walkTo(ClientPlayerEntity player, BlockPos stand) {
      this.placing = false;
      this.walkStand = stand;
      this.walkFrom = player.getEntityPos();
      this.walker.go(player, stand, this.tick);
   }

   private boolean walkToFix(ClientWorld level, ClientPlayerEntity player) {
      List<BlockPos> targets = new ArrayList<>();
      if (this.crafterSlots.get()) {
         targets.addAll(this.scan.crafters);
      }

      if (this.clickStates.get()) {
         for (BuildScan.Cell cell : this.scan.adjust) {
            if (!this.clickFailed.contains(cell.pos().asLong())) {
               targets.add(cell.pos());
            }
         }
      }

      targets.sort(Comparator.comparingDouble(posx -> posx.getSquaredDistance(player.getEntityPos())));
      if (!this.walking) {
         return this.planToClick(level, player, targets, posx -> {
            if (this.scan.crafters.contains(posx)) {
               this.craftersDone.add(posx.asLong());
            } else {
               this.clickFailed.add(posx.asLong());
            }
         });
      }

      BlockPos at = player.getBlockPos();

      for (BlockPos pos : targets) {
         BlockPos stand = Walker.standFor(level, player, pos, Blocks.AIR.getDefaultState(), this::badStand);
         if (stand != null && !stand.equals(at) && this.walkFails.merge(pos.asLong(), 1, Integer::sum) <= 2) {
            this.walkTarget = null;
            this.walkTo(player, stand);
            return true;
         }

         if (this.scan.crafters.contains(pos)) {
            this.craftersDone.add(pos.asLong());
         } else {
            this.clickFailed.add(pos.asLong());
         }
      }

      return false;
   }

   private boolean planToClick(ClientWorld level, ClientPlayerEntity player, List<BlockPos> targets, Consumer<BlockPos> giveUp) {
      List<StandPlanner.Target> wanted = new ArrayList<>();

      for (BlockPos pos : targets) {
         if (this.walkFails.getOrDefault(pos.asLong(), 0) >= 2) {
            giveUp.accept(pos);
         } else {
            wanted.add(new StandPlanner.Target(pos, level.getBlockState(pos), StandPlanner.Use.CLICK));
         }
      }

      if (wanted.isEmpty()) {
         return false;
      }

      if (this.tick < this.noPlanUntil) {
         return true;
      }

      this.walkFails.merge(wanted.get(0).pos().asLong(), 1, Integer::sum);
      this.startPlanner(level, player, wanted);
      return true;
   }

   private boolean flipLevers(ClientWorld level, ClientPlayerEntity player) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (schematic == null) {
         return false;
      }

      List<BlockPos> far = new ArrayList<>();

      for (BlockPos pos : this.scan.levers) {
         if (!this.clickFailed.contains(pos.asLong())) {
            if (this.inReach(player, pos) && Aim.at(this.mc, pos) != null) {
               if (!this.armed) {
                  this.feedback("levers", "Everything is in, flipping the levers on last", 1);
               }

               this.armed = true;
               this.leversFlipped++;
               this.user = BlockUser.lever(pos, schematic.getBlockState(pos));
               return true;
            }

            far.add(pos);
         }
      }

      far.sort(Comparator.comparingDouble(posx -> posx.getSquaredDistance(player.getEntityPos())));
      return this.planToClick(level, player, far, posx -> this.clickFailed.add(posx.asLong()));
   }

   private boolean relaxStuck(ClientWorld level, ClientPlayerEntity player) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (schematic == null) {
         return false;
      }

      int changed = 0;

      for (BuildScan.Cell cell : this.scan.missing) {
         if (changed >= 32) {
            break;
         }

         BlockPos pos = cell.pos();
         if (this.deferred.contains(pos.asLong()) && BuildOrder.lookOnly(cell.expected())) {
            BlockState best = null;
            int bestSpots = 0;

            for (BlockState variant : BuildOrder.turnedVariants(cell.expected())) {
               int spots = this.placeSpots(level, player, pos, variant);
               if (spots > bestSpots) {
                  bestSpots = spots;
                  best = variant;
               }
            }

            if (best != null) {
               trace("relaxing " + pos.toShortString() + " " + cell.expected() + " -> " + best + " (" + bestSpots + " spots)");
               schematic.setBlockState(pos, best, 18);
               this.relaxed.add(pos);
               this.deferred.remove(pos.asLong());
               this.tries.remove(pos.asLong());
               this.walkFails.remove(pos.asLong());
               this.stubborn.remove(pos.asLong());
               changed++;
            }
         }
      }

      if (changed == 0) {
         return false;
      }

      this.feedback("relax", changed + (changed == 1 ? " block" : " blocks") + " could not go in the schematic's way round, placing them another way", 1);
      this.rescanSoon();
      return true;
   }

   private boolean releaseProps() {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (schematic == null) {
         return false;
      }

      boolean released = false;
      Iterator<Entry<Long, Long>> it = this.supportFor.entrySet().iterator();

      while (it.hasNext()) {
         long held = it.next().getValue();
         if (this.deferred.contains(held) && this.missingKeys.contains(held) && BuildOrder.lookOnly(schematic.getBlockState(BlockPos.fromLong(held)))) {
            it.remove();
            released = true;
         }
      }

      if (released) {
         this.rescanSoon();
      }

      return released;
   }

   private int placeSpots(ClientWorld level, ClientPlayerEntity player, BlockPos pos, BlockState state) {
      double reach = player.getBlockInteractionRange() - 0.65;
      int spots = 0;

      for (int dy = -2; dy <= 2; dy++) {
         for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
               BlockPos feet = pos.add(dx, dy, dz);
               if (!feet.equals(pos) && !feet.up().equals(pos) && Walker.standable(level, feet)) {
                  Vec3d eye = new Vec3d(feet.getX() + 0.5, Walker.floor(level, feet) + player.getStandingEyeHeight(), feet.getZ() + 0.5);
                  if (!(eye.distanceTo(pos.toCenterPos()) > reach) && this.placement.placeableFrom(state, pos, eye)) {
                     spots++;
                  }
               }
            }
         }
      }

      return spots;
   }

   private BuildScan.Cell nearestOpen(ClientPlayerEntity player, int top) {
      BuildScan.Cell best = null;
      double bestDist = Double.MAX_VALUE;

      for (BuildScan.Cell cell : this.scan.missing) {
         if (cell.pos().getY() <= top && !this.deferred.contains(cell.pos().asLong()) && !this.scan.waiting.contains(cell.pos().asLong())) {
            double dist = cell.pos().getSquaredDistance(player.getEntityPos());
            if (dist < bestDist) {
               bestDist = dist;
               best = cell;
            }
         }
      }

      return best;
   }

   private void endOfBuild(ClientWorld level, ClientPlayerEntity player) {
      this.placing = false;
      if (this.armed) {
         this.finishBuild(level, player);
      } else if (this.scan.wholeSchematic) {
         BlockPos support = this.supportingLeaf(player);
         if (support != null && this.walking) {
            BlockPos aside = Walker.stepAside(level, player, pos -> this.scaffold.contains(pos.down().asLong()));
            if (aside != null) {
               this.walkTarget = null;
               this.walkTo(player, aside);
               return;
            }
         } else if (support != null) {
            for (BlockPos step : Walker.steps(level, player.getBlockPos())) {
               if (!this.scaffold.contains(step.down().asLong()) && !this.badStand(step)) {
                  this.badStands.put(step.asLong(), this.tick + 200L);
                  this.stopMoving();
                  this.hop = new Hop(step);
                  return;
               }
            }
         }

         if (!this.walkToFix(level, player)) {
            if (!this.retriedDeferred && !this.deferred.isEmpty()) {
               this.retriedDeferred = true;
               this.deferred.clear();
               this.walkFails.clear();
               this.clickFailed.clear();
               this.tries.clear();
               this.rescanSoon();
            } else {
               if (this.relaxFacing.get() && !this.relaxTried && !this.deferred.isEmpty()) {
                  if (this.releaseProps() || this.tick < this.noPlanUntil || this.planner != null || this.sweepScaffold(level, player)) {
                     return;
                  }

                  this.relaxTried = true;
                  if (this.relaxStuck(level, player)) {
                     return;
                  }
               }

               if (!this.placeWater.get() || this.waterGivenUp || !this.liquids(level, player, false)) {
                  if (!this.latePhase && this.scan.late > 0) {
                     if (!this.leftoversAdopted) {
                        this.leftoversAdopted = true;
                        int adopted = this.adoptLeftoverScaffold(level);
                        if (adopted > 0) {
                           this.feedback("leftover", adopted + " stray glass or leaf blocks are in empty cells of the build, mining them first", 1);
                        }
                     }

                     if (this.tick >= this.noPlanUntil && this.planner == null) {
                        if (!this.sweepScaffold(level, player)) {
                           if (!this.scaffold.isEmpty()) {
                              List<String> where = new ArrayList<>();

                              for (long key : this.scaffold) {
                                 if (where.size() < 5) {
                                    where.add(BlockPos.fromLong(key).toShortString());
                                 }
                              }

                              this.say(
                                 ModuleChat.body()
                                    .warn(this.scaffold.size() + " scaffold blocks could not be mined")
                                    .text(
                                       " ("
                                          + String.join("; ", where)
                                          + (this.scaffold.size() > 5 ? "; ..." : "")
                                          + "). The redstone power and TNT are not placed: mine these, then turn the builder on again."
                                    )
                              );
                              this.requestDisable();
                           } else {
                              this.latePhase = true;
                              this.feedback(
                                 "late",
                                 "Every other block is in and no scaffold is left, now the "
                                    + this.scan.late
                                    + (this.machineLast.get() ? " pistons, observers and redstone power sources, in that order" : " redstone power sources")
                                    + (this.placeTnt.get() ? " and TNT (TNT last)" : ""),
                                 1
                              );
                              this.rescanSoon();
                           }
                        }
                     }
                  } else if (!this.placeWater.get() || this.lavaGivenUp || !this.liquids(level, player, true)) {
                     if (!this.sweepScaffold(level, player)) {
                        if (!this.verified) {
                           this.verified = true;
                           this.feedback("verify", "Scaffold is out, checking the whole build once more", 1);
                           this.rescanSoon();
                        } else {
                           this.finishBuild(level, player);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void finishBuild(ClientWorld level, ClientPlayerEntity player) {
      if (!this.flipLevers.get() || !this.flipLevers(level, player)) {
         if (!this.startSelling(player, true)) {
            int held = (int)this.scan.missing.stream().filter(cell -> this.scan.waiting.contains(cell.pos().asLong())).count();
            int left = this.scan.missing.size() - held;
            int dry = this.liquidsLeft(false);
            int lava = this.liquidsLeft(true);
            ModuleChat.Body body = ModuleChat.body().good("Build finished").text(left == 0 ? "." : ", " + left + " blocks could not be reached.");
            if (this.spent > 0L) {
               body.text(" Spent $").value(AhPurchase.money(this.spent)).text(".");
            }

            if (this.scan.wrong > 0) {
               body.text(" ").warn(this.scan.wrong + " blocks differ from the schematic").text(" and were left alone.");
            }

            if (dry > 0) {
               body.text(" " + dry + " water cells are still dry.");
            }

            if (lava > 0) {
               body.text(" " + lava + " lava cells are still empty.");
            }

            if (!this.scan.poweredTnt.isEmpty()) {
               body.text(" ").warn(this.scan.poweredTnt.size() + " TNT left out").text(", power reaches those spots.");
            }

            if (!this.scan.tntForPlayer.isEmpty()) {
               body.text(" ").warn(this.scan.tntForPlayer.size() + " TNT left for you").text(" (" + someOf(this.scan.tntForPlayer) + ")");
               if (!this.scan.afterTntForPlayer.isEmpty()) {
                  body.text(", then " + this.scan.afterTntForPlayer.size() + " blocks in front of their pistons (" + someOf(this.scan.afterTntForPlayer) + ")");
               }

               body.text(". Place them when you are ready for the machine to run (Litematica shows them as missing).");
            }

            if (held > this.scan.poweredTnt.size()) {
               body.text(" " + (held - this.scan.poweredTnt.size()) + " blocks were held back (sand over a gap, an observer before its block).");
            }

            if (this.scan.leversOff > 0 && !this.flipLevers.get()) {
               body.text(
                  " "
                     + this.scan.leversOff
                     + (this.scan.leversOff == 1 ? " lever is" : " levers are")
                     + " on in the schematic and left off, flip them when you are ready."
               );
            }

            if (this.scan.leversOff > 0 && this.flipLevers.get()) {
               body.text(" ")
                  .warn(this.scan.leversOff + (this.scan.leversOff == 1 ? " lever" : " levers") + " could not be flipped on")
                  .text(" (" + someOf(this.scan.levers) + ").");
            }

            if (this.leversFlipped > 0) {
               body.text(" " + this.leversFlipped + (this.leversFlipped == 1 ? " lever was" : " levers were") + " flipped on last.");
            }

            if (!this.relaxed.isEmpty()) {
               body.text(" ")
                  .warn(this.relaxed.size() + (this.relaxed.size() == 1 ? " block" : " blocks") + " went in facing another way")
                  .text(", no spot could place them the schematic's way (" + someOf(this.relaxed) + ").");
            }

            if (this.scan.railsOff > 0) {
               body.text(
                  " "
                     + this.scan.railsOff
                     + (this.scan.railsOff == 1 ? " rail has" : " rails have")
                     + " another shape than the schematic, vanilla picks it from the rails around."
               );
            }

            if (this.scan.noItem > 0) {
               body.text(" " + this.scan.noItem + " cells need blocks without an item.");
            }

            if (!this.scaffold.isEmpty()) {
               body.text(" " + this.scaffold.size() + " scaffold leaves are still there.");
            }

            this.say(body);
            this.requestDisable();
         }
      }
   }

   private static String someOf(List<BlockPos> cells) {
      List<String> shown = new ArrayList<>();

      for (BlockPos pos : cells) {
         if (shown.size() == 4) {
            break;
         }

         shown.add(pos.toShortString());
      }

      return String.join("; ", shown) + (cells.size() > 4 ? "; ..." : "");
   }

   private boolean waterNow(ClientPlayerEntity player) {
      if (!Hands.has(player, stack -> stack.isOf(Items.WATER_BUCKET))) {
         return false;
      }

      for (BuildScan.Liquid target : this.scan.liquids) {
         if (target.kind() != CellFiller.Kind.LAVA_SOURCE
            && this.dueNow(target)
            && this.waterPending(target.pos())
            && CellFiller.reachable(this.mc, target.pos(), target.kind())) {
            this.stopMoving();
            this.filler = new CellFiller(target.pos(), target.kind());
            return true;
         }
      }

      return false;
   }

   private boolean dueNow(BuildScan.Liquid liquid) {
      return liquid.kind() == CellFiller.Kind.WATERLOG || this.scan.layer == Integer.MAX_VALUE || liquid.pos().getY() < this.scan.layer;
   }

   private boolean waterPending(BlockPos pos) {
      return this.placeWater.get() && !this.waterGivenUp && this.waterFails.getOrDefault(pos.asLong(), 0) < 2;
   }

   private boolean liquidPending(BlockPos pos) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      return schematic != null && schematic.getBlockState(pos).getFluidState().isIn(FluidTags.LAVA)
         ? this.placeWater.get() && !this.lavaGivenUp && this.waterFails.getOrDefault(pos.asLong(), 0) < 2
         : this.waterPending(pos);
   }

   private boolean cappedLavaNow(ClientWorld level, ClientPlayerEntity player) {
      if (!this.lavaGivenUp && Hands.has(player, stack -> stack.isOf(Items.LAVA_BUCKET))) {
         WorldSchematic schematic = this.schematics.getSchematicWorld();
         if (schematic == null) {
            return false;
         }

         for (BuildScan.Liquid target : this.scan.liquids) {
            BlockPos pos = target.pos();
            if (target.capped() && (this.scan.layer == Integer.MAX_VALUE || pos.getY() < this.scan.layer) && this.liquidPending(pos)) {
               FluidState below = schematic.getBlockState(pos.down()).getFluidState();
               if ((
                     !below.isStill()
                        || !below.isIn(FluidTags.LAVA)
                        || level.getFluidState(pos.down()).isStill() && level.getFluidState(pos.down()).isIn(FluidTags.LAVA)
                  )
                  && walledIn(level, schematic, pos)
                  && CellFiller.reachable(this.mc, pos, target.kind())) {
                  trace("capped lava " + pos.toShortString());
                  this.stopMoving();
                  this.filler = new CellFiller(pos, target.kind());
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private int cappedLavaWanted(int top) {
      int wanted = 0;

      for (BuildScan.Liquid liquid : this.scan.liquids) {
         if (liquid.capped() && liquid.pos().getY() < top && this.liquidPending(liquid.pos())) {
            wanted++;
         }
      }

      return wanted;
   }

   private int waterWanted(int top) {
      int wanted = 0;

      for (BuildScan.Liquid liquid : this.scan.liquids) {
         if (liquid.kind() != CellFiller.Kind.LAVA_SOURCE && liquid.pos().getY() <= top && this.waterPending(liquid.pos())) {
            wanted++;
         }
      }

      return wanted;
   }

   private boolean refillWater(ClientWorld level, ClientPlayerEntity player, int wanted) {
      if (wanted > count(player, Items.WATER_BUCKET) && count(player, Items.BUCKET) > 0 && this.tick >= this.nextRefillTick) {
         BlockPos center = BlockPos.ofFloored(player.getEyePos());

         for (BlockPos pos : BlockPos.iterate(center.add(-5, -5, -5), center.add(5, 5, 5))) {
            Long poured = this.pouredAt.get(pos.asLong());
            if ((poured == null || this.tick - poured >= 1200L) && CellFiller.endless(level, pos) && Aim.water(this.mc, pos) != null) {
               this.stopMoving();
               this.filler = new CellFiller(pos.toImmutable(), CellFiller.Kind.REFILL);
               return true;
            }
         }

         this.nextRefillTick = this.tick + 40L;
         return false;
      } else {
         return false;
      }
   }

   private int liquidsLeft(boolean lava) {
      return !this.placeWater.get() ? 0 : (int)this.scan.liquids.stream().filter(liquid -> liquid.kind() == CellFiller.Kind.LAVA_SOURCE == lava).count();
   }

   private boolean liquids(ClientWorld level, ClientPlayerEntity player, boolean lava) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      List<BuildScan.Liquid> targets = new ArrayList<>();
      int open = 0;

      for (BuildScan.Liquid target : this.scan.liquids) {
         if (target.kind() == CellFiller.Kind.LAVA_SOURCE == lava && this.waterFails.getOrDefault(target.pos().asLong(), 0) < 2) {
            if (lava && !walledIn(level, schematic, target.pos())) {
               open++;
            } else {
               targets.add(target);
            }
         }
      }

      if (open > 0) {
         this.feedback("lava-open", open + " lava cells are not walled in, leaving them empty", 400);
      }

      if (targets.isEmpty()) {
         return false;
      }

      targets.sort(
         Comparator.<BuildScan.Liquid>comparingInt(targetx -> targetx.pos().getY())
            .thenComparingDouble(targetx -> targetx.pos().getSquaredDistance(player.getEntityPos()))
      );
      Item bucket = lava ? Items.LAVA_BUCKET : Items.WATER_BUCKET;
      String what = lava ? "lava" : "water";
      if (!Hands.has(player, stack -> stack.isOf(bucket))) {
         if (!lava && this.refillWater(level, player, targets.size())) {
            return true;
         }

         if (!this.buyFromAh.get()) {
            this.feedback("no-" + what, "Need " + what + " buckets for " + targets.size() + " " + what + " cells", 200);
            this.giveUpLiquid(lava);
            return false;
         }

         int free = freeSlots(player);
         if (free <= 0) {
            if (this.startSelling(player, false)) {
               return true;
            }

            this.feedback(what + "-room", "No free inventory slot for " + what + " buckets", 200);
            this.giveUpLiquid(lava);
            return false;
         } else {
            this.feedback(what + "-buy", "Buying " + what + " buckets for " + targets.size() + " " + what + " cells", 1);
            return this.startPurchase(bucket, Math.min(targets.size(), Math.min(free, 8)));
         }
      } else {
         for (BuildScan.Liquid target : targets) {
            if (CellFiller.reachable(this.mc, target.pos(), target.kind())) {
               this.stopMoving();
               this.filler = new CellFiller(target.pos(), target.kind());
               return true;
            }
         }

         if (this.walking) {
            BlockPos at = player.getBlockPos();

            for (BuildScan.Liquid target : targets) {
               BlockPos stand = Walker.standFor(level, player, target.pos(), Blocks.AIR.getDefaultState(), this::badStand, 2.8);
               if (stand != null && !stand.equals(at)) {
                  this.walkTarget = null;
                  this.walkTo(player, stand);
                  return true;
               }

               this.waterFails.merge(target.pos().asLong(), 1, Integer::sum);
            }

            return false;
         } else {
            this.missedRouteTarget(player);
            if (this.tick < this.noPlanUntil) {
               this.giveUpLiquid(lava);
               return false;
            }

            List<StandPlanner.Target> spots = new ArrayList<>();

            for (BuildScan.Liquid target : targets) {
               spots.add(liquidTarget(level, target));
            }

            this.startPlanner(level, player, spots);
            return true;
         }
      }
   }

   private boolean sweepFinishedLayers(ClientWorld level, ClientPlayerEntity player) {
      if (this.scan.layer != Integer.MAX_VALUE && !this.walking) {
         if (this.sweptLayer == Integer.MAX_VALUE || this.scan.layer > this.sweptLayer) {
            this.sweptLayer = this.scan.layer;
            this.sweepDue = true;
            this.sweepSince = this.tick;
         }

         if (this.sweepDue && this.tick - this.sweepSince > 1200L) {
            this.sweepDue = false;
         }

         if (this.sweepDue && this.tick >= this.noPlanUntil) {
            List<StandPlanner.Target> targets = new ArrayList<>();

            for (long key : this.scaffold) {
               BlockPos pos = BlockPos.fromLong(key);
               if (pos.getY() < this.scan.layer - 1 && level.isChunkLoaded(pos) && this.walkFails.getOrDefault(key, 0) < 2 && !this.keepScaffold(player, key)) {
                  targets.add(new StandPlanner.Target(pos, level.getBlockState(pos), StandPlanner.Use.BREAK));
               }
            }

            if (targets.isEmpty()) {
               this.sweepDue = false;
               return false;
            } else {
               this.feedback("layer-sweep", "Mining " + targets.size() + " scaffold blocks under the finished layers", 200);
               this.missedRouteTarget(player);
               this.sweepPlanning = true;
               this.startPlanner(level, player, targets);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int adoptLeftoverScaffold(ClientWorld level) {
      WorldSchematic schematic = this.schematics.getSchematicWorld();
      if (schematic == null) {
         return 0;
      }

      int added = 0;
      Mutable cursor = new Mutable();

      for (SchematicManager.ActiveBox box : this.schematics.activeBoxes()) {
         for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
               for (int z = box.minZ(); z <= box.maxZ(); z++) {
                  cursor.set(x, y, z);
                  if (level.isChunkLoaded(cursor)
                     && schematic.getBlockState(cursor).isAir()
                     && Scaffold.block(level.getBlockState(cursor))
                     && this.scaffold.add(cursor.asLong())) {
                     added++;
                  }
               }
            }
         }
      }

      return added;
   }

   private boolean sweepScaffold(ClientWorld level, ClientPlayerEntity player) {
      if (!this.walking && !this.scaffold.isEmpty()) {
         this.missedRouteTarget(player);
         if (this.tick < this.noPlanUntil) {
            return false;
         }

         List<StandPlanner.Target> targets = new ArrayList<>();

         for (long key : this.scaffold) {
            BlockPos pos = BlockPos.fromLong(key);
            if (level.isChunkLoaded(pos) && this.walkFails.getOrDefault(key, 0) < 2 && !this.keepScaffold(player, key)) {
               targets.add(new StandPlanner.Target(pos, level.getBlockState(pos), StandPlanner.Use.BREAK));
            }
         }

         if (targets.isEmpty()) {
            return false;
         }

         this.feedback("sweep", "Mining " + targets.size() + " scaffold leaves left behind", 200);
         this.startPlanner(level, player, targets);
         return true;
      } else {
         return false;
      }
   }

   private void giveUpLiquid(boolean lava) {
      if (lava) {
         this.lavaGivenUp = true;
      } else {
         this.waterGivenUp = true;
      }
   }

   private static boolean walledIn(ClientWorld level, WorldSchematic schematic, BlockPos pos) {
      return walledIn(level, schematic, pos, new HashSet<>());
   }

   private static boolean walledIn(ClientWorld level, WorldSchematic schematic, BlockPos pos, Set<Long> seen) {
      if (seen.add(pos.asLong()) && seen.size() <= 64) {
         for (Direction direction : Direction.values()) {
            if (direction != Direction.UP) {
               BlockPos next = pos.offset(direction);
               BlockState there = level.getBlockState(next);
               if (there.isReplaceable()
                  && (!there.getFluidState().isIn(FluidTags.LAVA) || !there.getFluidState().isStill())
                  && (schematic == null || !schematic.getBlockState(next).getFluidState().isIn(FluidTags.LAVA) || !walledIn(level, schematic, next, seen))) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return seen.size() <= 64;
      }
   }

   private void snapshotInventory(ClientPlayerEntity player) {
      this.startCounts.clear();
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getStack(i);
         if (!stack.isEmpty()) {
            this.startCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
         }
      }
   }

   private boolean startSelling(ClientPlayerEntity player, boolean finished) {
      if (this.sellLeftovers.get() && this.sell == null && this.tick >= this.nextSellTick) {
         Map<Item, Integer> still = Map.of();
         if (!finished) {
            WorldSchematic schematic = this.schematics.getSchematicWorld();
            if (schematic == null || this.mc.world == null) {
               return false;
            }

            still = BuildScan.remainingItems(this.mc.world, schematic, this.schematics.activeBoxes());
         }

         Map<Integer, Item> slots = this.leftoverSlots(player, still);
         if (slots.isEmpty()) {
            this.nextSellTick = this.tick + 200L;
            return false;
         } else {
            this.stopMoving();
            this.feedback("selling", "Selling leftovers with /sell", 1);
            this.sell = DonutSell.start(this, slots);
            return true;
         }
      } else {
         return false;
      }
   }

   private Map<Integer, Item> leftoverSlots(ClientPlayerEntity player, Map<Item, Integer> still) {
      PlayerInventory inventory = player.getInventory();
      Map<Item, List<Integer>> where = new LinkedHashMap<>();
      Map<Item, Integer> have = new HashMap<>();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getStack(i);
         if (!stack.isEmpty() && !stack.isOf(Items.TOTEM_OF_UNDYING) && !stack.isOf(Items.WATER_BUCKET) && !stack.isOf(Items.LAVA_BUCKET)) {
            where.computeIfAbsent(stack.getItem(), itemx -> new ArrayList<>()).add(i);
            have.merge(stack.getItem(), stack.getCount(), Integer::sum);
         }
      }

      Map<Integer, Item> out = new HashMap<>();

      for (Entry<Item, List<Integer>> entry : where.entrySet()) {
         Item item = entry.getKey();
         ItemStack sample = new ItemStack(item);
         int made = this.bought.getOrDefault(item, 0) + (item == Items.BUCKET ? this.bucketsEmptied : 0) + (Scaffold.item(sample) ? this.leavesBought : 0);
         int extra = Math.min(made, have.get(item) - this.startCounts.getOrDefault(item, 0) - still.getOrDefault(item, 0));
         if (extra > 0) {
            List<Integer> slots = new ArrayList<>(entry.getValue());
            slots.sort(Comparator.comparingInt(slotx -> -inventory.getStack(slotx).getCount()));

            for (int slot : slots) {
               int size = inventory.getStack(slot).getCount();
               if (size <= extra) {
                  out.put(slot, item);
                  extra -= size;
               }
            }
         }
      }

      return out;
   }

   private BlockPos cleanupLeaf(ClientPlayerEntity player) {
      return this.cleanupLeaf(player, Set.of());
   }

   private Set<Long> routeFloor() {
      Set<Long> floor = new HashSet<>();

      for (int i = Math.max(0, this.routeIndex - 1); i < this.route.size(); i++) {
         floor.add(this.route.get(i).pos().down().asLong());
      }

      return floor;
   }

   private BlockPos cleanupLeaf(ClientPlayerEntity player, Set<Long> keep) {
      double reach = player.getBlockInteractionRange();
      Iterator<Long> it = this.scaffold.iterator();

      while (it.hasNext()) {
         long key = it.next();
         BlockPos pos = BlockPos.fromLong(key);
         if (this.mc.world.isChunkLoaded(pos)) {
            if (!Scaffold.block(this.mc.world.getBlockState(pos))) {
               it.remove();
               this.spentSupport.remove(key);
               this.supportFor.remove(key);
               this.buildScaffold.remove(key);
            } else {
               Long held = this.supportFor.get(key);
               if (held != null) {
                  if (this.missingKeys.contains(held)) {
                     continue;
                  }

                  this.supportFor.remove(key);
                  this.spentSupport.add(key);
               }

               if (!this.stuckScaffold.contains(key)
                  && !supports(player, pos)
                  && !this.walkway(pos)
                  && !keep.contains(key)
                  && (this.buildScaffold.contains(key) || this.spentSupport.contains(key) || !this.neededNow(pos))
                  && !(pos.toCenterPos().distanceTo(player.getEyePos()) > reach)
                  && Aim.at(this.mc, pos) != null) {
                  return pos;
               }
            }
         }
      }

      return null;
   }

   private boolean keepScaffold(ClientPlayerEntity player, long key) {
      BlockPos pos = BlockPos.fromLong(key);
      Long held = this.supportFor.get(key);
      return held != null && this.missingKeys.contains(held) && !this.deferred.contains(held)
         ? true
         : this.stuckScaffold.contains(key)
            || supports(player, pos)
            || this.walkway(pos)
            || !this.buildScaffold.contains(key) && !this.spentSupport.contains(key) && held == null && this.neededNow(pos);
   }

   private boolean walkway(BlockPos pos) {
      return this.scan != null && this.scan.layer != Integer.MAX_VALUE && pos.getY() == this.scan.layer - 1 && !this.buildScaffold.contains(pos.asLong());
   }

   private int dropBelow(BlockPos leaf) {
      for (int depth = 1; depth <= 8; depth++) {
         BlockPos cell = leaf.down(depth - 1);
         if (!Double.isNaN(Walker.floor(this.mc.world, cell))) {
            return depth;
         }
      }

      return Integer.MAX_VALUE;
   }

   private BlockPos supportingLeaf(ClientPlayerEntity player) {
      for (long key : this.scaffold) {
         BlockPos pos = BlockPos.fromLong(key);
         if (supports(player, pos)) {
            return pos;
         }
      }

      return null;
   }

   private static boolean supports(ClientPlayerEntity player, BlockPos pos) {
      Box body = player.getBoundingBox();
      Box cell = new Box(pos);
      return body.intersects(cell) || body.offset(0.0, -0.25, 0.0).intersects(cell);
   }

   private boolean inReach(ClientPlayerEntity player, BlockPos pos) {
      return pos.toCenterPos().distanceTo(player.getEyePos()) <= player.getBlockInteractionRange() - 0.3;
   }

   private static boolean enclosed(ClientWorld level, BlockPos pos) {
      for (Direction direction : Direction.values()) {
         BlockPos next = pos.offset(direction);
         if (level.getBlockState(next).isReplaceable()) {
            return false;
         }
      }

      return true;
   }

   private void stopMoving() {
      this.walker.stop();
      this.walkTarget = null;
      this.placing = false;
   }

   private static boolean lacking(ClientPlayerEntity player, BlockState expected, Map<Item, Boolean> seen) {
      Item item = BlockStateUtil.requiredItem(expected);
      return item != null && item != Items.AIR ? seen.computeIfAbsent(item, it -> count(player, it) <= 0) : false;
   }

   private static int count(ClientPlayerEntity player, Item item) {
      int total = 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (inventory.getStack(i).isOf(item)) {
            total += inventory.getStack(i).getCount();
         }
      }

      if (player.getOffHandStack().isOf(item)) {
         total += player.getOffHandStack().getCount();
      }

      return total;
   }

   private static int room(ClientPlayerEntity player, ItemStack wanted) {
      int room = 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack held = inventory.getStack(i);
         if (held.isEmpty()) {
            room += wanted.getMaxCount();
         } else if (held.isOf(wanted.getItem())) {
            room += Math.max(0, held.getMaxCount() - held.getCount());
         }
      }

      return room;
   }

   private static int freeSlots(ClientPlayerEntity player) {
      int free = 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (inventory.getStack(i).isEmpty()) {
            free++;
         }
      }

      return free;
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   private void feedback(String key, String message, int minimumIntervalTicks) {
      if (this.chatFeedback.get()) {
         long last = this.lastFeedbackTick.getOrDefault(key, -4611686018427387904L);
         if (this.tick - last >= minimumIntervalTicks) {
            this.lastFeedbackTick.put(key, this.tick);
            ModuleChat.send(this, message);
         }
      }
   }
}
