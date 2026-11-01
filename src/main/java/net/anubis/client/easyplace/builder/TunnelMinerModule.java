package net.anubis.client.easyplace.builder;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.easyplace.inventory.InventoryCalm;
import net.anubis.client.easyplace.util.PlacementUtil;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.donutmacros.AhPurchase;
import net.anubis.client.module.impl.donutmacros.DonutSell;
import net.anubis.client.module.impl.misc.AutoReconnectModule;
import net.anubis.client.module.impl.render.BetterSusChunkModule;
import net.anubis.client.module.impl.render.SusChunkFinderModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.ChunkOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class TunnelMinerModule extends ClientModule implements ChunkScanner.Consumer {
   private static final String SIZE_3X3 = "3x3 Shard Pickaxe";
   private static final String SIZE_1X2 = "1x2";
   private static final Predicate<ItemStack> TOTEM = stack -> stack.isOf(Items.TOTEM_OF_UNDYING);
   private static final Predicate<ItemStack> XP = stack -> stack.isOf(Items.EXPERIENCE_BOTTLE);
   private static final Predicate<ItemStack> APPLE = stack -> stack.isOf(Items.GOLDEN_APPLE);
   private static final Predicate<ItemStack> PICKAXE = stack -> stack.isIn(ItemTags.PICKAXES);
   private static final Predicate<ItemStack> SHARD = stack -> PICKAXE.test(stack) && shardName(stack);
   private static final Set<Block> STORAGE = Set.of(
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.BARREL,
      Blocks.ENDER_CHEST,
      Blocks.HOPPER,
      Blocks.FURNACE,
      Blocks.BLAST_FURNACE,
      Blocks.SMOKER,
      Blocks.DROPPER,
      Blocks.DISPENSER
   );
   private static final Set<Item> JUNK = Set.of(
      Items.COBBLESTONE,
      Items.COBBLED_DEEPSLATE,
      Items.DEEPSLATE,
      Items.STONE,
      Items.TUFF,
      Items.GRANITE,
      Items.DIORITE,
      Items.ANDESITE,
      Items.DIRT,
      Items.GRAVEL,
      Items.FLINT,
      Items.NETHERRACK,
      Items.CALCITE,
      Items.SMOOTH_BASALT,
      Items.BASALT,
      Items.BLACKSTONE
   );
   private static final int PLAN_AHEAD = 40;
   private static final int PLAN_SIDE = 15;
   private static final int PLAN_NODES = 6000;
   private static final int REPLAN_STEPS = 9;
   private static final double STAND_MIN = 1.0;
   private static final double STAND_MAX = 2.6;
   private static final double STEP_TO_MAX = 1.6;
   private static final double STAND_CLOSE = 0.3;
   private static final float VARIED_STAND = 0.2F;
   private static final double QUICK_STEP_SPREAD = 0.3;
   private static final double STREAM_ASIDE = 0.45;
   private static final double STREAM_GAP = 1.0;
   private static final int FEET_WAIT = 4;
   private static final float OLD_WAY_CHANCE = 0.06F;
   private static final int OLD_WAY_MIN = 2;
   private static final int OLD_WAY_SPREAD = 4;
   private static final float LINGER_CHANCE = 0.01F;
   private static final double FALL_BEHIND = 3.0;
   private static final int PERCEIVE_MIN = 3;
   private static final int PERCEIVE_MAX = 5;
   private static final int BUY_RETRY_TICKS = 1200;
   private static final int SELL_STACKS = 4;
   private static final int SELL_RETRY_TICKS = 1200;
   private static final int VERIFY_TICKS = 60;
   private static final int XP_STACK = 64;
   private static final int APPLE_STACK = 64;
   private static final int START_ZONE = 48;
   private static final int CLOSE_SCREEN_TICKS = 100;
   private static final String FLAGS_OFF = "Off";
   private static final String FLAGS_SUS = "Sus Chunk";
   private static final String FLAGS_BETTER = "Better Sus Chunk";
   private static final String FLAGS_BOTH = "Both";
   private static final int ARRIVE_BLOCKS = 3;
   private static final int TOTEM_POP = 35;
   private static final int STALL_PLANS = 3;
   private static final double VISITED_COST = 4.0;
   private static final int UNFLAGGED_CHECKS = 10;
   private static final int VISITED_COLOR = 1431357695;
   private final ModeSetting size = this.addSetting("size", new ModeSetting("Tunnel Size", "3x3 Shard Pickaxe", "3x3 Shard Pickaxe", "1x2"));
   private final BooleanSetting wander = this.addSetting("wander", new BooleanSetting("Wander", true));
   private final ModeSetting flags = this.addSetting("flags", new ModeSetting("Dig To Flagged Chunks", "Off", "Off", "Sus Chunk", "Better Sus Chunk", "Both"));
   private final BooleanSetting showVisited = this.addSetting("show-visited", new BooleanSetting("Show Visited Chunks", true));
   private final NumberSetting storageToStop = this.addSetting("storage-to-stop", new NumberSetting("Storage To Stop", 30.0F, 3.0F, 300.0F).step(1.0F));
   private final NumberSetting baseRadius = this.addSetting("base-radius", new NumberSetting("Base Radius", 80.0F, 16.0F, 192.0F).step(8.0F));
   private final BooleanSetting spawners = this.addSetting("spawners", new BooleanSetting("Stop At Spawners", true));
   private final BooleanSetting disconnect = this.addSetting("disconnect", new BooleanSetting("Disconnect On Find", true));
   private final BooleanSetting quitOnPop = this.addSetting("quit-on-pop", new BooleanSetting("Quit On Totem Pop", true));
   private final BooleanSetting keepTotem = this.addSetting("keep-totem", new BooleanSetting("Keep Totem In Offhand", true));
   private final BooleanSetting mend = this.addSetting("mend", new BooleanSetting("Mend Pickaxe", true));
   private final NumberSetting mendBelow = this.addSetting("mend-below", new NumberSetting("Mend Below Durability", 150.0F, 20.0F, 1000.0F).step(10.0F));
   private final BooleanSetting eat = this.addSetting("eat", new BooleanSetting("Eat Golden Apples", true));
   private final BooleanSetting sellJunk = this.addSetting("sell-junk", new BooleanSetting("Sell Junk For Room", true));
   private final BooleanSetting buyFromAh = this.addSetting("buy-from-ah", new BooleanSetting("Buy From AH", true));
   private final StringSetting maxPerListing = this.addSetting(
      "max-per-listing", new StringSetting("Max Price Per Listing", "10m").hint("e.g. 10m, one whole listing")
   );
   private final NumberSetting refresh = this.addSetting("refresh", new NumberSetting("AH Refresh (ms)", 1000.0F, 300.0F, 3000.0F).step(100.0F));
   private final BooleanSetting chatFeedback = this.addSetting("chat-feedback", new BooleanSetting("Chat Feedback", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final Random random = new Random();
   private final Map<Long, Boolean> finds = new HashMap<>();
   private final Map<Long, Integer> breakFails = new HashMap<>();
   private final Set<Long> blocked = new HashSet<>();
   private final Map<Item, Long> nextBuy = new HashMap<>();
   private final Map<String, Long> lastFeedback = new HashMap<>();
   private final List<BlockPos> steps = new ArrayList<>();
   private final LongOpenHashSet visitedChunks = new LongOpenHashSet();
   private final ChunkOverlay visitedOverlay = new ChunkOverlay();
   private boolean hasTarget;
   private long target;
   private int targetBest;
   private int targetNear;
   private int targetStalls;
   private int unflaggedChecks;
   private long tick;
   private int cellSize;
   private Direction heading;
   private BlockPos start;
   private BlockPos axis;
   private int drift;
   private long nextDrift;
   private int noiseSeed;
   private int stepIndex;
   private int stepsSincePlan;
   private int blockedPlans;
   private Breaker breaker;
   private Hop hop;
   private Shuffle shuffle;
   private Eater eater;
   private int originX;
   private int originZ;
   private BlockPos routeFrom;
   private BlockPos seenFrom;
   private int perceive;
   private BlockPos standTarget;
   private Vec3d standSpot;
   private boolean standVaried;
   private boolean streaming;
   private BlockPos streamTarget;
   private long headBrokeAt = -100L;
   private BlockPos decidedSlab;
   private int oldWayLeft;
   private float[] idleLook;
   private boolean lingering;
   private long leaveAt;
   private String foundWhat;
   private BlockPos foundAt;
   private int settle;
   private long restUntil;
   private long nextRest;
   private long screenSince;
   private DonutSell sell;
   private long nextSell;
   private boolean stopped;
   private boolean popped;
   private AhPurchase purchase;
   private Item purchaseItem;
   private String purchaseName;
   private int purchaseBefore;
   private long verifyUntil;
   private long maxListing;
   private boolean mending;
   private long mendStarted;

   public TunnelMinerModule() {
      super(
         "tunnel-miner",
         "Tunnel Base Finder",
         "Digs a tunnel the way you face (3x3 with a Shard Pickaxe) like a player would, around lava, water and gravel, or from one Sus Chunk flagged chunk to the next, and stops at a base or a spawner. Buys totems, XP bottles and golden apples from the AH.",
         ModuleCategory.BASE_RAID
      );
      this.usesNotifications();
      this.showVisited.visibleWhen(() -> !this.flags.is("Off"));
      this.mendBelow.visibleWhen(this.mend::get);
      this.maxPerListing.visibleWhen(this.buyFromAh::get);
      this.refresh.visibleWhen(this.buyFromAh::get);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null) {
         this.tick = 0L;
         this.finds.clear();
         this.breakFails.clear();
         this.blocked.clear();
         this.nextBuy.clear();
         this.steps.clear();
         this.stepIndex = this.stepsSincePlan = this.blockedPlans = this.settle = 0;
         this.breaker = null;
         this.hop = null;
         this.eater = null;
         this.purchase = null;
         this.sell = null;
         this.nextSell = 0L;
         this.oldWayLeft = 0;
         this.decidedSlab = null;
         this.verifyUntil = 0L;
         this.mending = this.stopped = false;
         this.heading = player.getHorizontalFacing();
         this.start = player.getBlockPos();
         this.axis = this.start;
         this.drift = 0;
         this.nextDrift = 0L;
         this.noiseSeed = this.random.nextInt();
         this.restUntil = 0L;
         this.nextRest = this.nextGap();
         this.routeFrom = player.getBlockPos();
         this.seenFrom = this.routeFrom;
         this.shuffle = null;
         this.standTarget = null;
         this.standSpot = null;
         this.idleLook = null;
         this.lingering = false;
         this.leaveAt = 0L;
         this.maxListing = AhPurchase.parseMoney(this.maxPerListing.get());
         this.cellSize = 1;
         if (this.size.is("3x3 Shard Pickaxe")) {
            if (Hands.has(player, SHARD)) {
               this.cellSize = 3;
            } else {
               this.say(ModuleChat.body().warn("No Shard Pickaxe").text(", digging a 1x2 tunnel instead."));
            }
         }

         this.originX = player.getBlockX() - this.cellSize / 2;
         this.originZ = player.getBlockZ() - this.cellSize / 2;
         this.visitedChunks.clear();
         this.hasTarget = false;
         if (!this.flags.is("Off")) {
            this.enableSources();
         }

         ChunkScanner.register(this);
         this.on(TickEvent.TYPE, Stage.PRE, event -> this.step());
         this.on(Render3DEvent.TYPE, Stage.POST, this::render);
         this.popped = false;
         this.on(
            PacketAppliedEvent.TYPE,
            event -> {
               if (event.packet instanceof EntityStatusS2CPacket status
                  && status.getStatus() == 35
                  && this.mc.world != null
                  && this.mc.player != null
                  && status.getEntity(this.mc.world) == this.mc.player) {
                  this.popped = true;
               }
            }
         );
         String tunnel = "Digging a " + (this.cellSize == 3 ? "3x3" : "1x2") + " tunnel";
         this.say(
            ModuleChat.body()
               .text(
                  this.flags.is("Off")
                     ? tunnel + " " + this.heading.getId() + " at Y " + this.start.getY() + "."
                     : tunnel
                        + " at Y "
                        + this.start.getY()
                        + " to the chunks "
                        + this.flags.get()
                        + " flags, "
                        + this.heading.getId()
                        + " while there are none."
               )
         );
      } else {
         this.say(ModuleChat.body().warn("Join a world first."));
         this.requestDisable();
      }
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      if (this.purchase != null) {
         this.purchase.cancel();
      }

      this.purchase = null;
      if (this.sell != null) {
         this.sell.cancel();
      }

      this.sell = null;
      this.release();
      this.finds.clear();
   }

   private void step() {
      ClientPlayerEntity player = this.mc.player;
      ClientWorld level = this.mc.world;
      if (player != null && level != null && this.mc.interactionManager != null) {
         this.tick++;
         if (this.popped) {
            if (this.quitOnPop.get()) {
               this.quitOnPop(player);
               return;
            }

            this.popped = false;
         }

         if (this.stopped) {
            this.linger(player);
         } else {
            if (!this.flags.is("Off")) {
               this.visitedChunks.add(ChunkPos.toLong(player.getBlockX() >> 4, player.getBlockZ() >> 4));
               if (this.hasTarget && this.tick % 20L == 0L) {
                  this.watchTarget(player, level);
               }
            }

            if (player.isDead()) {
               this.say(ModuleChat.body().danger("You died").text(", Tunnel Base Finder stopped."));
               this.requestDisable();
            } else if (this.purchase != null) {
               this.followPurchase(player);
            } else if (this.verifyUntil > 0L) {
               this.verifyPurchase(player);
            } else if (this.sell != null) {
               if (!this.sell.tick()) {
                  this.sell = null;
                  if (freeSlots(player) == 0) {
                     this.nextSell = this.tick + 1200L;
                  }
               }
            } else if (this.mending) {
               this.mend(player);
            } else if (this.mc.currentScreen instanceof HandledScreen) {
               this.release();
               if (this.screenSince == 0L) {
                  this.screenSince = this.tick;
               }

               if (this.tick - this.screenSince > 100L) {
                  player.closeHandledScreen();
               }
            } else {
               this.screenSince = 0L;
               if (this.eater != null) {
                  if (this.eater.tick(this.mc) != Eater.Result.RUNNING) {
                     this.eater = null;
                  }
               } else if (this.breaker != null || this.hop != null || this.supplies(player)) {
                  if (this.tick % 20L != 0L || !this.checkFinds(player, level)) {
                     this.dig(player, level);
                  }
               }
            }
         }
      }
   }

   private boolean supplies(ClientPlayerEntity player) {
      if (this.keepTotem.get() && !TOTEM.test(player.getOffHandStack())) {
         this.release();
         if (Hands.has(player, TOTEM)) {
            if (this.tick % 5L == 0L && this.calm(player)) {
               Hands.toOffhand(this.mc, player, TOTEM);
            }

            return false;
         } else {
            if (!this.startBuy(player, Items.TOTEM_OF_UNDYING, "Totem", "totem", 1)) {
               this.say(ModuleChat.body().danger("No totem").text(" and Buy From AH is off, Tunnel Base Finder stopped."));
               this.requestDisable();
            }

            return false;
         }
      } else {
         if (this.eat.get() && Eater.hungry(player)) {
            if (Hands.has(player, APPLE)) {
               if (this.calm(player)) {
                  this.eater = new Eater(APPLE);
               }

               return false;
            }

            if (this.startBuy(player, Items.GOLDEN_APPLE, "Golden Apples", "golden apple stack", 64)) {
               return false;
            }
         }

         ItemStack pickaxe = this.pickaxe(player);
         if (pickaxe == null) {
            this.say(ModuleChat.body().danger("No pickaxe").text(this.cellSize == 3 ? " (Shard Pickaxe)" : "").text(", Tunnel Base Finder stopped."));
            this.requestDisable();
            return false;
         }

         int left = pickaxe.getMaxDamage() - pickaxe.getDamage();
         if (this.mend.get() && mendable(pickaxe) && left < this.mendBelow.getInt()) {
            if (Hands.has(player, XP)) {
               this.release();
               this.mending = true;
               this.mendStarted = this.tick;
               this.feedback("mend", "Mending the pickaxe with XP bottles", 200);
               return false;
            }

            if (this.startBuy(player, Items.EXPERIENCE_BOTTLE, "XP Bottles", "xp bottle stack", 64)) {
               return false;
            }
         }

         if (left >= 10 || this.mend.get() && mendable(pickaxe)) {
            return true;
         }

         this.say(ModuleChat.body().danger("Pickaxe almost broken").text(", Tunnel Base Finder stopped before it breaks."));
         this.requestDisable();
         return false;
      }
   }

   private void mend(ClientPlayerEntity player) {
      ItemStack held = player.getMainHandStack();
      boolean repaired = this.tool().test(held) && held.getDamage() == 0;
      if (repaired || this.tick - this.mendStarted > 1200L || !XP.test(player.getOffHandStack()) && !Hands.has(player, XP)) {
         this.mc.options.useKey.setPressed(false);
         this.mending = false;
         if (repaired) {
            this.feedback("mended", "Pickaxe mended", 200);
         }
      } else if (!XP.test(player.getOffHandStack())) {
         this.mc.options.useKey.setPressed(false);
         if (this.tick % 5L == 0L && this.calm(player)) {
            Hands.toOffhand(this.mc, player, XP);
         }
      } else if (Hands.hold(this.mc, player, this.tool())) {
         Aim.turn(this.mc, player.getYaw(), 80.0F, 22.0F);
         this.mc.options.useKey.setPressed(player.getPitch() > 60.0F);
      }
   }

   private boolean startBuy(ClientPlayerEntity player, Item item, String name, String search, int amount) {
      if (!this.buyFromAh.get()) {
         return false;
      }

      if (this.tick < this.nextBuy.getOrDefault(item, 0L)) {
         this.feedback("wait-" + name, "Waiting to try buying " + name + " again", 600);
         return true;
      }

      if (freeSlots(player) != 0) {
         if (!this.calm(player)) {
            return true;
         }

         this.purchaseItem = item;
         this.purchaseName = name;
         this.purchaseBefore = count(player, item);
         this.feedback("buy-" + name, "Buying " + name + " (/ah " + search + ")", 1);
         this.purchase = AhPurchase.startAny(this, item, name, search, stack -> stack.isOf(item), amount, this.maxListing, this.refresh.getInt(), 0L);
         return true;
      } else {
         if (!this.sellJunk.get() || this.tick < this.nextSell || !this.startSelling(player)) {
            this.feedback("room-" + name, "No room in the inventory to buy " + name, 600);
            this.nextBuy.put(item, this.tick + 200L);
         }

         return true;
      }
   }

   private void followPurchase(ClientPlayerEntity player) {
      AhPurchase.Status status = this.purchase.status();
      if (status != AhPurchase.Status.RUNNING) {
         this.purchase = null;
         if (status == AhPurchase.Status.FAILED) {
            this.say(ModuleChat.body().warn("Could not buy " + this.purchaseName).text(", trying again in a minute."));
            this.nextBuy.put(this.purchaseItem, this.tick + 1200L);
            this.purchaseItem = null;
         } else {
            this.verifyUntil = this.tick + 60L;
         }
      }
   }

   private void verifyPurchase(ClientPlayerEntity player) {
      int got = count(player, this.purchaseItem) - this.purchaseBefore;
      if (got > 0) {
         this.say(ModuleChat.body().good("Bought " + got + " " + this.purchaseName).text(", it is in the inventory."));
         EasyPlace.LOG.info("[Tunnel Base Finder] bought {} {} (verified)", got, this.purchaseName);
      } else {
         if (this.tick < this.verifyUntil) {
            return;
         }

         this.say(ModuleChat.body().warn("The AH sold " + this.purchaseName + " but it never showed up").text(", trying again later."));
         this.nextBuy.put(this.purchaseItem, this.tick + 1200L);
      }

      this.verifyUntil = 0L;
      this.purchaseItem = null;
   }

   private boolean startSelling(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      Map<Integer, Item> slots = new HashMap<>();

      for (int i = 35; i >= 0 && slots.size() < 4; i--) {
         Item item = inventory.getStack(i).getItem();
         if (i != inventory.getSelectedSlot() && JUNK.contains(item)) {
            slots.put(i, item);
         }
      }

      if (slots.isEmpty()) {
         return false;
      }

      if (!this.calm(player)) {
         return true;
      }

      this.feedback("selling", "Selling " + slots.size() + " stacks of junk stone with /sell to make room", 1);
      this.sell = DonutSell.start(this, slots);
      return true;
   }

   private void dig(ClientPlayerEntity player, ClientWorld level) {
      if (this.settle > 0) {
         this.settle--;
         if (this.lingering) {
            this.idle(player);
         }
      } else {
         this.lingering = false;
         if (this.breaker != null) {
            Breaker.Result result = this.breaker.tick(this.mc);
            if (result != Breaker.Result.RUNNING) {
               BlockPos at = this.breaker.pos();
               this.breaker = null;
               this.settle = this.cellSize == 3 ? this.lognormal(2.2, 0.3, 2, 6) : this.lognormal(1.0, 0.5, 0, 4);
               if (result == Breaker.Result.FAILED && this.breakFails.merge(at.asLong(), 1, Integer::sum) >= 3) {
                  this.blocked.add(this.cellKey(at));
                  this.steps.clear();
               }
            }
         } else if (this.shuffle != null) {
            Shuffle.Result result = this.shuffle.tick(this.mc);
            if (result != Shuffle.Result.RUNNING) {
               boolean quick = this.shuffle.quick();
               this.shuffle = null;
               if (result == Shuffle.Result.FAILED) {
                  this.standSpot = null;
               } else if (!quick) {
                  this.settle = this.lognormal(1.5, 0.5, 0, 6);
               }
            }
         } else if (this.hop != null) {
            Hop.Result result = this.hop.tick(this.mc);
            if (result != Hop.Result.RUNNING) {
               this.hop = null;
               if (result == Hop.Result.FAILED) {
                  this.steps.clear();
               }
            }
         } else if (!this.rest(player)) {
            if (Hands.hold(this.mc, player, this.tool())) {
               if (this.hasTarget && this.arrived(player)) {
                  this.endTarget(player, null);
               }

               if (this.stepIndex < this.steps.size() && this.stepsSincePlan < 9 || this.plan(player, level)) {
                  BlockPos next = this.steps.get(this.stepIndex);
                  BlockPos from = this.stepIndex > 0 ? this.steps.get(this.stepIndex - 1) : this.routeFrom;
                  Direction way = beside(from, next);
                  boolean newCell = this.cellKey(next) != this.cellKey(from);
                  this.seenFrom = player.getBlockPos();
                  if (way != null && (!newCell || Boolean.TRUE.equals(this.safe(level, this.cellX(next.getX()), this.cellZ(next.getZ()), next.getY())))) {
                     BlockPos target = this.toMine(level, next, way);
                     if (!this.stream(player, level, next, way, target)) {
                        if (target != null) {
                           Vec3d spot = this.stand(level, player, target, way);
                           if (spot == null) {
                              if (flat(player, from) > 0.7) {
                                 this.hop = new Hop(from, false);
                                 return;
                              }
                           } else if (Math.hypot(spot.x - player.getX(), spot.z - player.getZ()) > 0.3) {
                              this.shuffle = new Shuffle(spot, target.toCenterPos(), !this.standVaried);
                              return;
                           }

                           if (this.random.nextFloat() < 0.01F) {
                              this.settle = this.lognormal(20.0, 0.5, 8, 100);
                              this.idleLook = null;
                              this.lingering = true;
                           } else {
                              this.breaker = new Breaker(target.toImmutable(), false, this.face(next, target, way));
                              this.standTarget = null;
                           }
                        } else {
                           if (flat(player, next) <= 3.0) {
                              this.stepIndex++;
                              this.stepsSincePlan++;
                           } else if (flat(player, from) > 1.2) {
                              this.hop = new Hop(from, true);
                           } else {
                              boolean through = this.stepIndex + 1 < this.steps.size()
                                 && beside(next, this.steps.get(this.stepIndex + 1)) == way
                                 && this.toMine(level, this.steps.get(this.stepIndex + 1), way) == null;
                              this.hop = new Hop(next, through);
                              this.stepIndex++;
                              this.stepsSincePlan++;
                           }
                        }
                     }
                  } else {
                     this.endStream();
                     this.steps.clear();
                  }
               }
            }
         }
      }
   }

   private boolean stream(ClientPlayerEntity player, ClientWorld level, BlockPos next, Direction way, BlockPos target) {
      if (this.streamTarget != null && level.getBlockState(this.streamTarget).isAir()) {
         this.headBrokeAt = this.tick;
         this.streamTarget = null;
      }

      BlockPos head = next.up();
      boolean feetPending = target != null && target.equals(next) && this.tick - this.headBrokeAt < 4L;
      if (this.cellSize == 3 && this.straight(way) && !(Math.abs(aside(player, next, way)) > 0.45) && (target == null || target.equals(head) || feetPending)) {
         if (target != null && target.equals(head) && !head.equals(this.decidedSlab)) {
            this.decidedSlab = head.toImmutable();
            if (this.oldWayLeft > 0) {
               this.oldWayLeft--;
            }

            if (this.oldWayLeft == 0 && this.random.nextFloat() < 0.06F) {
               this.oldWayLeft = 2 + this.random.nextInt(4);
            }
         }

         if (this.oldWayLeft > 0) {
            this.endStream();
            return false;
         }

         this.streaming = true;
         Vec3d face = Vec3d.ofCenter(head).subtract(way.getOffsetX() * 0.5, 0.0, way.getOffsetZ() * 0.5);
         float[] rot = PlacementUtil.calcRotation(player.getEyePos(), face);
         Aim.turn(this.mc, rot[0], rot[1], 22.0F);
         double gap = (face.x - player.getX()) * way.getOffsetX() + (face.z - player.getZ()) * way.getOffsetZ();
         this.mc.options.forwardKey.setPressed(target == null || gap > 1.0);
         if (target == null) {
            this.stepIndex++;
            this.stepsSincePlan++;
            return true;
         }

         if (feetPending) {
            return true;
         }

         BlockHitResult hit = Aim.trace(this.mc, player, player.getYaw(), player.getPitch(), player.getBlockInteractionRange());
         if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(head)) {
            this.streamTarget = head.toImmutable();
            if (this.mc.interactionManager.updateBlockBreakingProgress(head, hit.getSide())) {
               player.swingHand(Hand.MAIN_HAND);
            }
         }

         return true;
      } else {
         this.endStream();
         return false;
      }
   }

   private boolean straight(Direction way) {
      return this.stepIndex + 1 < this.steps.size() && beside(this.steps.get(this.stepIndex), this.steps.get(this.stepIndex + 1)) == way;
   }

   private static double aside(ClientPlayerEntity player, BlockPos next, Direction way) {
      Direction side = way.rotateYClockwise();
      return (player.getX() - next.getX() - 0.5) * side.getOffsetX() + (player.getZ() - next.getZ() - 0.5) * side.getOffsetZ();
   }

   private void endStream() {
      if (this.streaming) {
         this.streaming = false;
         this.streamTarget = null;
         this.mc.options.forwardKey.setPressed(false);
         if (this.mc.interactionManager != null && this.mc.interactionManager.isBreakingBlock()) {
            this.mc.interactionManager.cancelBlockBreaking();
         }
      }
   }

   private int lognormal(double median, double sigma, int min, int max) {
      return (int)MathHelper.clamp(Math.round(Math.exp(Math.log(median) + this.random.nextGaussian() * sigma)), min, max);
   }

   private static double flat(ClientPlayerEntity player, BlockPos pos) {
      return Math.hypot(pos.getX() + 0.5 - player.getX(), pos.getZ() + 0.5 - player.getZ());
   }

   private Vec3d stand(ClientWorld level, ClientPlayerEntity player, BlockPos target, Direction way) {
      if (target.equals(this.standTarget)) {
         return this.standSpot;
      }

      this.standTarget = target.toImmutable();
      this.standSpot = null;
      double tx = target.getX() + 0.5;
      double tz = target.getZ() + 0.5;
      double y = player.getY();
      Direction side = way.rotateYClockwise();
      double room = this.cellSize == 3 ? 0.7 : 0.2;
      double back = (tx - player.getX()) * way.getOffsetX() + (tz - player.getZ()) * way.getOffsetZ();
      double aside = (player.getX() - tx) * side.getOffsetX() + (player.getZ() - tz) * side.getOffsetZ();
      double keepAside = this.cellSize == 3 && this.oldWayLeft == 0 ? Math.min(room, 0.45) : room;
      if (back >= 1.0 && back <= 2.6 && Math.abs(aside) <= keepAside && open(level, player, player.getEntityPos())) {
         this.standSpot = player.getEntityPos();
         return this.standSpot;
      }

      this.standVaried = this.random.nextFloat() < 0.2F;

      for (int i = 0; i < 16; i++) {
         double b = 1.0 + this.random.nextDouble() * (this.standVaried ? 0.6000000000000001 : 0.3);
         double l = this.standVaried ? (this.random.nextDouble() * 2.0 - 1.0) * room : 0.0;
         Vec3d spot = new Vec3d(tx - way.getOffsetX() * b + side.getOffsetX() * l, y, tz - way.getOffsetZ() * b + side.getOffsetZ() * l);
         if (open(level, player, spot)) {
            this.standSpot = spot;
            return spot;
         }
      }

      return null;
   }

   private static boolean open(ClientWorld level, ClientPlayerEntity player, Vec3d spot) {
      Box box = player.getDimensions(EntityPose.STANDING).getBoxAt(spot).contract(0.02);
      if (!level.isSpaceEmpty(player, box)) {
         return false;
      }

      BlockPos floor = BlockPos.ofFloored(spot.x, spot.y - 0.5, spot.z);
      return !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
   }

   private BlockPos toMine(ClientWorld level, BlockPos next, Direction way) {
      BlockPos head = next.up();
      if (solid(level, head)) {
         return head;
      } else {
         return solid(level, next) ? next : null;
      }
   }

   private Direction face(BlockPos next, BlockPos target, Direction way) {
      return this.cellSize == 3 && target.equals(next) ? Direction.UP : way.getOpposite();
   }

   private static boolean solid(ClientWorld level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.getCollisionShape(level, pos).isEmpty();
   }

   private boolean rest(ClientPlayerEntity player) {
      if (this.tick < this.restUntil) {
         this.idle(player);
         return true;
      } else if (this.tick >= this.nextRest) {
         this.release();
         this.restUntil = this.tick + this.lognormal(30.0, 0.6, 10, 200);
         this.nextRest = this.restUntil + this.nextGap();
         this.idleLook = null;
         return true;
      } else {
         return false;
      }
   }

   private long nextGap() {
      return (long)MathHelper.clamp(-Math.log(1.0 - this.random.nextDouble()) * 2400.0, 600.0, 12000.0);
   }

   private void idle(ClientPlayerEntity player) {
      if (this.idleLook == null || this.random.nextFloat() < 0.025F) {
         this.idleLook = new float[]{
            player.getYaw() + (float)(this.random.nextGaussian() * 40.0), MathHelper.clamp((float)(10.0 + this.random.nextGaussian() * 22.0), -45.0F, 70.0F)
         };
      }

      Aim.turn(this.mc, this.idleLook[0], this.idleLook[1], 22.0F);
   }

   private boolean plan(ClientPlayerEntity player, ClientWorld level) {
      this.steps.clear();
      this.stepIndex = 0;
      this.stepsSincePlan = 0;
      BlockPos feet = player.getBlockPos();
      this.routeFrom = feet;
      this.seenFrom = feet;
      this.perceive = 3 + this.random.nextInt(3);
      int y = feet.getY();
      int cx = this.cellX(feet.getX());
      int cz = this.cellZ(feet.getZ());
      this.pickTarget(player);
      boolean toTarget = this.hasTarget;
      int tx = toTarget ? this.cellX(middle(ChunkPos.getPackedX(this.target))) : 0;
      int tz = toTarget ? this.cellZ(middle(ChunkPos.getPackedZ(this.target))) : 0;
      if (!toTarget && this.wander.get() && this.tick >= this.nextDrift) {
         this.drift = MathHelper.clamp(this.drift + this.random.nextInt(3) - 1, -2, 2);
         this.nextDrift = this.tick + 20L * (30 + this.random.nextInt(60));
      }

      int fx = this.heading.getOffsetX();
      int fz = this.heading.getOffsetZ();
      Direction side = this.heading.rotateYClockwise();
      int lx = side.getOffsetX();
      int lz = side.getOffsetZ();
      int ax = this.cellX(this.axis.getX());
      int az = this.cellZ(this.axis.getZ());
      int ahead = Math.max(4, 40 / this.cellSize);
      int aside = Math.max(3, 15 / this.cellSize);
      int startLateral = (cx - ax) * lx + (cz - az) * lz;
      long here = this.cellChunk(cx, cz);
      long goal = toTarget ? this.target : Long.MIN_VALUE;
      Map<Long, Boolean> safeCache = new HashMap<>();
      PriorityQueue<TunnelMinerModule.Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.rank, b.rank));
      Set<Long> visited = new HashSet<>();
      TunnelMinerModule.Node origin = new TunnelMinerModule.Node(cx, cz, 0.0, 0.0, null);
      TunnelMinerModule.Node best = origin;
      double bestScore = toTarget ? -apart(cx, cz, tx, tz) : this.score(cx, cz, cx, cz, fx, fz, lx, lz, ax, az);
      open.add(origin);
      int expanded = 0;

      while (!open.isEmpty() && expanded++ < 6000) {
         TunnelMinerModule.Node node = open.poll();
         if (visited.add(key(node.x, node.z))) {
            double nodeScore = toTarget ? -apart(node.x, node.z, tx, tz) : this.score(node.x, node.z, cx, cz, fx, fz, lx, lz, ax, az);
            if (nodeScore > bestScore || nodeScore == bestScore && node.cost < best.cost) {
               best = node;
               bestScore = nodeScore;
            }

            for (Direction way : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
               int x = node.x + way.getOffsetX();
               int z = node.z + way.getOffsetZ();
               long k = key(x, z);
               if (!visited.contains(k) && !this.blocked.contains(k)) {
                  if (toTarget) {
                     if (Math.abs(x - cx) > ahead || Math.abs(z - cz) > ahead) {
                        continue;
                     }
                  } else {
                     int forward = (x - cx) * fx + (z - cz) * fz;
                     int lateral = (x - ax) * lx + (z - az) * lz;
                     if (forward < -2 || forward > ahead || Math.abs(lateral - startLateral) > aside) {
                        continue;
                     }
                  }

                  Boolean safe = safeCache.computeIfAbsent(k, unused -> this.safe(level, x, z, y));
                  if (Boolean.TRUE.equals(safe)) {
                     double cost = node.cost + 1.0 + this.noise(x, z) + this.visitedCost(x, z, here, goal);
                     double estimate = toTarget ? apart(x, z, tx, tz) : -this.score(x, z, cx, cz, fx, fz, lx, lz, ax, az);
                     open.add(new TunnelMinerModule.Node(x, z, cost, cost + estimate, node));
                  }
               }
            }
         }
      }

      if (toTarget) {
         int left = apart(best.x, best.z, tx, tz);
         int now = apart(cx, cz, tx, tz);
         if (best == origin) {
            this.endTarget(player, left == 0 ? null : "could not get any closer");
            return false;
         }

         if (left > 0) {
            if (left >= this.targetBest && now >= this.targetNear) {
               if (++this.targetStalls >= 3) {
                  this.endTarget(player, "could not get any closer");
                  return false;
               }
            } else {
               this.targetBest = Math.min(this.targetBest, left);
               this.targetNear = Math.min(this.targetNear, now);
               this.targetStalls = 0;
            }
         }
      } else if (best == origin) {
         if (++this.blockedPlans > 4) {
            this.say(ModuleChat.body().danger("Nowhere safe to dig").text(", Tunnel Base Finder stopped."));
            this.requestDisable();
            return false;
         }

         this.heading = this.blockedPlans % 2 == 1 ? this.heading.rotateYClockwise() : this.heading.rotateYCounterclockwise().rotateYCounterclockwise();
         this.axis = feet;
         this.drift = 0;
         this.feedback("turn", "Blocked ahead, digging " + this.heading.getId() + " instead", 40);
         return false;
      }

      this.blockedPlans = 0;
      List<TunnelMinerModule.Node> cells = new ArrayList<>();

      for (TunnelMinerModule.Node node = best; node != null; node = node.parent) {
         cells.add(0, node);
      }

      BlockPos at = feet;
      BlockPos middle = this.center(cells.get(0), y);
      at = this.walkTo(at, middle);

      for (int i = 1; i < cells.size(); i++) {
         at = this.walkTo(at, this.center(cells.get(i), y));
      }

      return !this.steps.isEmpty();
   }

   private BlockPos walkTo(BlockPos from, BlockPos to) {
      BlockPos at = from;

      while (at.getX() != to.getX()) {
         at = at.add(Integer.signum(to.getX() - at.getX()), 0, 0);
         this.steps.add(at);
      }

      while (at.getZ() != to.getZ()) {
         at = at.add(0, 0, Integer.signum(to.getZ() - at.getZ()));
         this.steps.add(at);
      }

      return at;
   }

   private BlockPos center(TunnelMinerModule.Node cell, int y) {
      int half = this.cellSize / 2;
      return new BlockPos(cell.x * this.cellSize + this.originX + half, y, cell.z * this.cellSize + this.originZ + half);
   }

   private int cellX(int x) {
      return Math.floorDiv(x - this.originX, this.cellSize);
   }

   private int cellZ(int z) {
      return Math.floorDiv(z - this.originZ, this.cellSize);
   }

   private double score(int x, int z, int cx, int cz, int fx, int fz, int lx, int lz, int ax, int az) {
      int forward = (x - cx) * fx + (z - cz) * fz;
      int lateral = (x - ax) * lx + (z - az) * lz;
      return forward - 2.0 * Math.abs(lateral - this.drift);
   }

   private LongSet flagged() {
      LongOpenHashSet all = new LongOpenHashSet();
      if (!this.flags.is("Better Sus Chunk")) {
         SusChunkFinderModule sus = ModuleRegistry.get(SusChunkFinderModule.class);
         if (sus != null && sus.isEnabled()) {
            all.addAll(sus.flaggedChunks());
         }
      }

      if (!this.flags.is("Sus Chunk")) {
         BetterSusChunkModule better = ModuleRegistry.get(BetterSusChunkModule.class);
         if (better != null && better.isEnabled()) {
            all.addAll(better.flaggedChunks());
         }
      }

      return all;
   }

   private void enableSources() {
      if (!this.flags.is("Better Sus Chunk")) {
         this.turnOn(ModuleRegistry.get(SusChunkFinderModule.class), "Sus Chunk Finder");
      }

      if (!this.flags.is("Sus Chunk")) {
         this.turnOn(ModuleRegistry.get(BetterSusChunkModule.class), "Better Sus Chunk");
      }
   }

   private void turnOn(ClientModule module, String name) {
      if (module != null && !module.isEnabled()) {
         module.setEnabled(true);
         this.say(ModuleChat.body().text("Turned on " + name + " to find chunks to dig to."));
      }
   }

   private void pickTarget(ClientPlayerEntity player) {
      if (!this.flags.is("Off") && !this.hasTarget) {
         double bestDistance = Double.MAX_VALUE;
         long best = 0L;
         LongIterator keys = this.flagged().iterator();

         while (keys.hasNext()) {
            long key = keys.nextLong();
            if (!this.visitedChunks.contains(key)) {
               int mx = middle(ChunkPos.getPackedX(key));
               int mz = middle(ChunkPos.getPackedZ(key));
               if (Math.abs(mx - this.start.getX()) > 48 || Math.abs(mz - this.start.getZ()) > 48) {
                  double distance = Math.hypot(mx - player.getX(), mz - player.getZ());
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = key;
                  }
               }
            }
         }

         if (bestDistance != Double.MAX_VALUE) {
            this.hasTarget = true;
            this.target = best;
            this.targetBest = Integer.MAX_VALUE;
            this.targetNear = Integer.MAX_VALUE;
            this.targetStalls = 0;
            this.unflaggedChecks = 0;
            int mx = middle(ChunkPos.getPackedX(best));
            int mz = middle(ChunkPos.getPackedZ(best));
            this.feedback("target", "Digging to a flagged chunk at " + mx + " " + mz + " (" + (int)bestDistance + " blocks)", 1);
            EasyPlace.LOG.info("[Tunnel Base Finder] digging to flagged chunk at {} {} ({} blocks)", new Object[]{mx, mz, (int)bestDistance});
         }
      }
   }

   private void watchTarget(ClientPlayerEntity player, ClientWorld level) {
      if (!this.flagged().contains(this.target) && level.isChunkLoaded(ChunkPos.getPackedX(this.target), ChunkPos.getPackedZ(this.target))) {
         if (++this.unflaggedChecks >= 10) {
            this.endTarget(player, "it is not flagged any more");
         }
      } else {
         this.unflaggedChecks = 0;
      }
   }

   private boolean arrived(ClientPlayerEntity player) {
      return Math.abs(player.getBlockX() - middle(ChunkPos.getPackedX(this.target))) <= 3
         && Math.abs(player.getBlockZ() - middle(ChunkPos.getPackedZ(this.target))) <= 3;
   }

   private void endTarget(ClientPlayerEntity player, String why) {
      int mx = middle(ChunkPos.getPackedX(this.target));
      int mz = middle(ChunkPos.getPackedZ(this.target));
      this.visitedChunks.add(this.target);
      this.hasTarget = false;
      this.steps.clear();
      this.heading = player.getHorizontalFacing();
      this.axis = player.getBlockPos();
      this.drift = 0;
      String what = why == null ? "Dug through the flagged chunk at " + mx + " " + mz : "Left the flagged chunk at " + mx + " " + mz + " (" + why + ")";
      this.feedback("target-end", what, 1);
      EasyPlace.LOG.info("[Tunnel Base Finder] {}", what);
   }

   private double visitedCost(int x, int z, long here, long goal) {
      if (this.flags.is("Off")) {
         return 0.0;
      }

      long chunk = this.cellChunk(x, z);
      return chunk != here && chunk != goal && this.visitedChunks.contains(chunk) ? 4.0 : 0.0;
   }

   private long cellChunk(int x, int z) {
      int half = this.cellSize / 2;
      return ChunkPos.toLong(x * this.cellSize + this.originX + half >> 4, z * this.cellSize + this.originZ + half >> 4);
   }

   private static int middle(int chunk) {
      return chunk * 16 + 8;
   }

   private static int apart(int x, int z, int tx, int tz) {
      return Math.abs(x - tx) + Math.abs(z - tz);
   }

   private void render(Render3DEvent event) {
      if (this.showVisited.get() && !this.flags.is("Off") && !this.visitedChunks.isEmpty()) {
         this.visitedOverlay.draw(this.visitedChunks, 1431357695, event.partialTick);
      }
   }

   private double noise(int x, int z) {
      if (!this.wander.get()) {
         return 0.0;
      }

      int h = x * 73856093 ^ z * 19349663 ^ this.noiseSeed;
      h ^= h >>> 13;
      h *= 1540483477;
      h ^= h >>> 15;
      return (h & 1023) / 1023.0 * 0.7;
   }

   private Boolean safe(ClientWorld level, int cx, int cz, int y) {
      int s = this.cellSize;
      int height = s == 3 ? 3 : 2;
      int x0 = cx * s + this.originX;
      int z0 = cz * s + this.originZ;
      int dx = Math.max(0, Math.max(x0 - this.seenFrom.getX(), this.seenFrom.getX() - (x0 + s - 1)));
      int dz = Math.max(0, Math.max(z0 - this.seenFrom.getZ(), this.seenFrom.getZ() - (z0 + s - 1)));
      if (Math.max(dx, dz) > this.perceive) {
         return level.isPosLoaded(new BlockPos(x0, y, z0)) ? Boolean.TRUE : null;
      }

      Mutable pos = new Mutable();
      Mutable around = new Mutable();

      for (int x = x0; x < x0 + s; x++) {
         for (int z = z0; z < z0 + s; z++) {
            pos.set(x, y - 1, z);
            if (!level.isPosLoaded(pos)) {
               return null;
            }

            BlockState floor = level.getBlockState(pos);
            if (!floor.getFluidState().isEmpty() || floor.getCollisionShape(level, pos).isEmpty()) {
               return false;
            }

            pos.set(x, y + height, z);
            if (level.getBlockState(pos).getBlock() instanceof FallingBlock) {
               return false;
            }

            for (int dy = 0; dy < height; dy++) {
               pos.set(x, y + dy, z);
               BlockState state = level.getBlockState(pos);
               if (!state.getFluidState().isEmpty() || state.getBlock() instanceof InfestedBlock || state.getHardness(level, pos) < 0.0F) {
                  return false;
               }

               for (Direction direction : Direction.values()) {
                  around.set(pos, direction);
                  if (!level.getBlockState(around).getFluidState().isEmpty()) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private long cellKey(BlockPos pos) {
      return key(this.cellX(pos.getX()), this.cellZ(pos.getZ()));
   }

   private static long key(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   private static Direction beside(BlockPos from, BlockPos to) {
      if (from.getY() != to.getY()) {
         return null;
      } else {
         int dx = to.getX() - from.getX();
         int dz = to.getZ() - from.getZ();
         if (Math.abs(dx) + Math.abs(dz) != 1) {
            return null;
         } else {
            return dx > 0 ? Direction.EAST : (dx < 0 ? Direction.WEST : (dz > 0 ? Direction.SOUTH : Direction.NORTH));
         }
      }
   }

   @Override
   public boolean wants(BlockState state) {
      return state.isOf(Blocks.SPAWNER) || state.isIn(BlockTags.SHULKER_BOXES) || STORAGE.contains(state.getBlock());
   }

   @Override
   public void found(long pos, BlockState state) {
      this.finds.put(pos, state.isOf(Blocks.SPAWNER));
   }

   @Override
   public void removed(long pos) {
      this.finds.remove(pos);
   }

   @Override
   public void reset() {
      this.finds.clear();
   }

   private boolean checkFinds(ClientPlayerEntity player, ClientWorld level) {
      int radius = this.baseRadius.getInt();
      int storage = 0;
      long sumX = 0L;
      long sumZ = 0L;
      BlockPos spawner = null;

      for (Entry<Long, Boolean> entry : this.finds.entrySet()) {
         BlockPos pos = BlockPos.fromLong(entry.getKey());
         if ((Math.abs(pos.getX() - this.start.getX()) > 48 || Math.abs(pos.getZ() - this.start.getZ()) > 48)
            && Math.abs(pos.getX() - player.getBlockX()) <= radius
            && Math.abs(pos.getZ() - player.getBlockZ()) <= radius) {
            if (entry.getValue()) {
               if (spawner == null && !spiderSpawner(level, pos)) {
                  spawner = pos;
               }
            } else {
               storage++;
               sumX += pos.getX();
               sumZ += pos.getZ();
            }
         }
      }

      if (this.spawners.get() && spawner != null) {
         this.found("a spawner", spawner);
         return true;
      } else if (storage >= this.storageToStop.getInt()) {
         this.found("a base (" + storage + " storage blocks)", new BlockPos((int)(sumX / storage), player.getBlockY(), (int)(sumZ / storage)));
         return true;
      } else {
         return false;
      }
   }

   private static boolean spiderSpawner(ClientWorld level, BlockPos pos) {
      if (!(level.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner)) {
         return false;
      } else {
         Entity shown = spawner.getLogic().getRenderedEntity(level, pos);
         return shown != null && (shown.getType() == EntityType.SPIDER || shown.getType() == EntityType.CAVE_SPIDER);
      }
   }

   private void found(String what, BlockPos at) {
      this.stopped = true;
      this.release();
      this.foundWhat = what;
      this.foundAt = at.toImmutable();
      EasyPlace.LOG.info("[Tunnel Base Finder] found {} at {} {} {}", new Object[]{what, at.getX(), at.getY(), at.getZ()});
      this.say(ModuleChat.body().danger("Found " + what).text(" at ").coords(at.getX(), at.getY(), at.getZ()));
      if (this.notificationsOn()) {
         this.mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0F));
      }

      if (!this.disconnect.get()) {
         this.requestDisable();
      } else {
         this.leaveAt = this.tick + this.lognormal(260.0, 0.6, 70, 1400);
         this.idleLook = null;
      }
   }

   private void quitOnPop(ClientPlayerEntity player) {
      this.popped = false;
      this.release();
      this.stopped = true;
      BlockPos at = player.getBlockPos();
      EasyPlace.LOG.info("[Tunnel Base Finder] totem popped at {} {} {}, leaving the server", new Object[]{at.getX(), at.getY(), at.getZ()});
      if (this.mc.getNetworkHandler() != null) {
         AutoReconnectModule.holdOff();
         this.mc
            .getNetworkHandler()
            .getConnection()
            .disconnect(Text.literal("[Tunnel Base Finder] Totem popped at " + at.getX() + " " + at.getY() + " " + at.getZ() + ", left the server"));
      }

      this.requestDisable();
   }

   private void linger(ClientPlayerEntity player) {
      if (this.leaveAt != 0L) {
         this.idle(player);
         if (this.tick >= this.leaveAt) {
            this.leaveAt = 0L;
            if (this.mc.getNetworkHandler() != null) {
               AutoReconnectModule.holdOff();
               this.mc
                  .getNetworkHandler()
                  .getConnection()
                  .disconnect(
                     Text.literal(
                        "[Tunnel Base Finder] Found " + this.foundWhat + " at " + this.foundAt.getX() + " " + this.foundAt.getY() + " " + this.foundAt.getZ()
                     )
                  );
            }

            this.requestDisable();
         }
      }
   }

   private Predicate<ItemStack> tool() {
      return this.cellSize == 3 ? SHARD : PICKAXE;
   }

   private ItemStack pickaxe(ClientPlayerEntity player) {
      Predicate<ItemStack> tool = this.tool();
      if (tool.test(player.getMainHandStack())) {
         return player.getMainHandStack();
      }

      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (tool.test(inventory.getStack(i))) {
            return inventory.getStack(i);
         }
      }

      return null;
   }

   static boolean shardName(ItemStack stack) {
      String name = stack.getName().getString().toLowerCase(Locale.ROOT);
      return name.contains("shard") || name.contains("amethyst");
   }

   private static boolean mendable(ItemStack stack) {
      return stack.getEnchantments().getEnchantments().stream().anyMatch(enchantment -> enchantment.matchesKey(Enchantments.MENDING));
   }

   private static int count(ClientPlayerEntity player, Item item) {
      int total = player.getOffHandStack().isOf(item) ? player.getOffHandStack().getCount() : 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getStack(i);
         if (stack.isOf(item)) {
            total += stack.getCount();
         }
      }

      return total;
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

   private boolean calm(ClientPlayerEntity player) {
      this.release();
      return InventoryCalm.still(player);
   }

   private void release() {
      if (this.breaker != null) {
         this.breaker.release(this.mc);
      }

      this.breaker = null;
      if (this.hop != null) {
         this.hop.release(this.mc);
      }

      this.hop = null;
      if (this.eater != null) {
         this.eater.release(this.mc);
      }

      this.eater = null;
      if (this.shuffle != null) {
         this.shuffle.release(this.mc);
      }

      this.shuffle = null;
      this.standTarget = null;
      this.streaming = false;
      this.streamTarget = null;
      this.mc.options.useKey.setPressed(false);
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.sprintKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
      if (this.mc.interactionManager != null) {
         this.mc.interactionManager.cancelBlockBreaking();
      }
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   private void feedback(String key, String message, int minimumIntervalTicks) {
      if (this.chatFeedback.get()) {
         long last = this.lastFeedback.getOrDefault(key, -4611686018427387904L);
         if (this.tick - last >= minimumIntervalTicks) {
            this.lastFeedback.put(key, this.tick);
            ModuleChat.send(this, message);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Node(int x, int z, double cost, double rank, TunnelMinerModule.Node parent) {
   }
}
