package net.anubis.client.easyplace.builder;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.donutmacros.AhPurchase;
import net.anubis.client.module.impl.misc.AutoReconnectModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class ElytraAutopilotModule extends ClientModule {
   private static final Predicate<ItemStack> ROCKET = ElytraAutopilotModule::plainRocket;
   private static final Predicate<ItemStack> XP = stack -> stack.isOf(Items.EXPERIENCE_BOTTLE);
   private static final float URGENT_TURN = 27.0F;
   private static final int ROCKET_GAP = 10;
   private static final double ROCKET_RAY = 10.0;
   private static final double LOADED_AHEAD = 110.0;
   private static final double ARRIVE_RANGE = 30.0;
   private static final double TOUCHDOWN_RANGE = 72.0;
   private static final double GLIDE_SLOPE = 0.095;
   private static final double MIN_CLEAR = 14.0;
   private static final int SETBACK_WINDOW = 200;
   private static final int SETBACK_HOLD = 60;
   private static final int STALL_TICKS = 900;
   private static final double STALL_GAIN = 60.0;
   private static final int MAX_STALLS = 6;
   private static final int BUY_RETRY = 6000;
   private static final int VERIFY_TICKS = 60;
   private static final int MEND_STALL = 200;
   private static final int WATER_LIMIT = 1200;
   private final StringSetting target = this.addSetting("target", new StringSetting("Target", "").hint("x z, e.g. 12000 -3500 or 12k -3.5k"));
   private final NumberSetting altitude = this.addSetting("altitude", new NumberSetting("Cruise Altitude", 290.0F, 180.0F, 320.0F).step(10.0F));
   private final BooleanSetting mend = this.addSetting("mend", new BooleanSetting("Repair With XP", true));
   private final BooleanSetting buyRockets = this.addSetting("buy-rockets", new BooleanSetting("Buy Rockets", true));
   private final NumberSetting rocketStacks = this.addSetting("rocket-stacks", new NumberSetting("Rocket Stacks", 3.0F, 1.0F, 3.0F).step(1.0F));
   private final StringSetting rocketPrice = this.addSetting("rocket-price", new StringSetting("Max Rocket Stack Price", "60k").hint("per 64 rockets"));
   private final BooleanSetting buyXp = this.addSetting("buy-xp", new BooleanSetting("Buy XP Bottles", true));
   private final NumberSetting xpStacks = this.addSetting("xp-stacks", new NumberSetting("XP Stacks", 2.0F, 1.0F, 3.0F).step(1.0F));
   private final StringSetting xpPrice = this.addSetting("xp-price", new StringSetting("Max XP Stack Price", "450k").hint("per 64 bottles"));
   private final StringSetting tripLimit = this.addSetting("trip-limit", new StringSetting("Trip Spend Limit", "2m").hint("0 = no limit"));
   private final BooleanSetting disconnect = this.addSetting("disconnect", new BooleanSetting("Disconnect On Arrival", true));
   private final BooleanSetting status = this.addSetting("status", new BooleanSetting("Status Line", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private volatile int setbacksReceived;
   private ElytraAutopilotModule.State state = ElytraAutopilotModule.State.PREPARE;
   private ElytraAutopilotModule.Job job = ElytraAutopilotModule.Job.ARRIVE;
   private ElytraAutopilotModule.Task task = ElytraAutopilotModule.Task.NONE;
   private int tick;
   private int stateTicks;
   private int taskTicks;
   private double destX;
   private double destZ;
   private RegistryKey<World> dimension;
   private ClientPlayNetworkHandler connection;
   private boolean pauseWas;
   private boolean pauseSaved;
   private String stopReason;
   private int boostUntil;
   private int lastRocket = -1000;
   private int rocketHoldUntil;
   private int seenSetbacks;
   private final IntArrayList recentSetbacks = new IntArrayList();
   private int eatLockUntil;
   private boolean eatingInFlight;
   private boolean climbing;
   private ElytraFlight.Plan plan;
   private int planTick = -1000;
   private float bandJitter;
   private float glideJitter;
   private double bestDistance;
   private int progressTick;
   private int stalls;
   private double altitudeBump;
   private BlockPos spot;
   private int nextSpotSearch;
   private int spotSearches;
   private final List<BlockPos> banned = new ArrayList<>();
   private int goArounds;
   private boolean spiralling;
   private double bestDrop;
   private int dropTick;
   private int takeoffTries;
   private int jumps;
   private int jumpTick = -1000;
   private float escapeYaw = Float.NaN;
   private int swapTick = -1000;
   private int waterTicks;
   private Eater eater;
   private int mendBest;
   private int mendGainTick;
   private int mendFailures;
   private final EnumSet<ElytraAutopilotModule.Task> doneTasks = EnumSet.noneOf(ElytraAutopilotModule.Task.class);
   private AhPurchase purchase;
   private Item purchaseItem;
   private int purchaseBefore;
   private int verifyUntil;
   private final Map<Item, Integer> nextBuy = new HashMap<>();
   private final Map<Item, Integer> buyFails = new HashMap<>();
   private long spent;
   private int rocketsUsed;
   private int pitStops;
   private double flown;
   private Vec3d lastPos;
   private long startedAt;

   public ElytraAutopilotModule() {
      super(
         "elytra-autopilot",
         "Elytra Autopilot",
         "Flies an elytra with rockets to the target coordinates and lands there; repairs, eats and restocks on the way.",
         ModuleCategory.BASE_RAID
      );
      this.rocketStacks.visibleWhen(this.buyRockets::get);
      this.rocketPrice.visibleWhen(this.buyRockets::get);
      this.xpStacks.visibleWhen(this.buyXp::get);
      this.xpPrice.visibleWhen(this.buyXp::get);
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         double[] xz = parseTarget(this.target.get());
         if (xz == null) {
            this.say(ModuleChat.body().warn("Set the Target first").text(" (\"x z\", e.g. 12000 -3500)."));
            this.requestDisable();
         } else if (this.mc.world.getRegistryKey() == World.NETHER) {
            this.say(ModuleChat.body().warn("The nether roof is no place for this").text("; the overworld and the end only."));
            this.requestDisable();
         } else if (!usable(chest(player)) && !Hands.has(player, ElytraAutopilotModule::usable)) {
            this.say(ModuleChat.body().warn("No usable elytra").text(" worn or in the inventory."));
            this.requestDisable();
         } else if (count(player, ROCKET) == 0 && !this.buyRockets.get()) {
            this.say(ModuleChat.body().warn("No rockets").text(" (plain ones, without stars), and buying them is off."));
            this.requestDisable();
         } else {
            this.destX = Math.floor(xz[0]) + 0.5;
            this.destZ = Math.floor(xz[1]) + 0.5;
            this.dimension = this.mc.world.getRegistryKey();
            this.connection = this.mc.getNetworkHandler();
            this.reset();
            this.pauseWas = this.mc.options.pauseOnLostFocus;
            this.pauseSaved = true;
            this.mc.options.pauseOnLostFocus = false;
            this.lastPos = player.getEntityPos();
            this.startedAt = System.currentTimeMillis();
            this.say(
               ModuleChat.body()
                  .text("Flying to ")
                  .coords((int)Math.floor(this.destX), 0, (int)Math.floor(this.destZ))
                  .text(", " + Math.round(this.distance(player)) + " blocks away.")
            );
            EasyPlace.LOG
               .info(
                  "[Elytra Autopilot] start at {} to {} {} ({} blocks)",
                  new Object[]{player.getBlockPos().toShortString(), Math.floor(this.destX), Math.floor(this.destZ), Math.round(this.distance(player))}
               );
            this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
         }
      } else {
         this.say(ModuleChat.body().warn("Join a world first."));
         this.requestDisable();
      }
   }

   @Override
   protected void onDisable() {
      this.releaseKeys();
      if (this.purchase != null) {
         this.purchase.cancel();
      }

      this.purchase = null;
      if (this.eater != null) {
         this.eater.release(this.mc);
      }

      this.eater = null;
      if (this.pauseSaved) {
         this.mc.options.pauseOnLostFocus = this.pauseWas;
      }

      this.pauseSaved = false;
   }

   private void reset() {
      this.state = ElytraAutopilotModule.State.PREPARE;
      this.job = ElytraAutopilotModule.Job.ARRIVE;
      this.task = ElytraAutopilotModule.Task.NONE;
      this.tick = this.stateTicks = this.taskTicks = 0;
      this.stopReason = null;
      this.boostUntil = 0;
      this.lastRocket = -1000;
      this.rocketHoldUntil = 0;
      this.seenSetbacks = this.setbacksReceived;
      this.recentSetbacks.clear();
      this.eatLockUntil = 0;
      this.eatingInFlight = false;
      this.climbing = false;
      this.plan = null;
      this.planTick = -1000;
      this.bestDistance = Double.MAX_VALUE;
      this.progressTick = 0;
      this.stalls = 0;
      this.altitudeBump = 0.0;
      this.spot = null;
      this.nextSpotSearch = 0;
      this.spotSearches = 0;
      this.banned.clear();
      this.goArounds = 0;
      this.spiralling = false;
      this.takeoffTries = this.jumps = 0;
      this.jumpTick = -1000;
      this.escapeYaw = Float.NaN;
      this.swapTick = -1000;
      this.waterTicks = 0;
      this.eater = null;
      this.mendFailures = 0;
      this.doneTasks.clear();
      this.purchase = null;
      this.purchaseItem = null;
      this.verifyUntil = 0;
      this.nextBuy.clear();
      this.buyFails.clear();
      this.spent = 0L;
      this.rocketsUsed = 0;
      this.pitStops = 0;
      this.flown = 0.0;
      this.newJitter();
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveNettyEvent event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.setbacksReceived++;
      }
   }

   private void tick() {
      this.tick++;
      this.stateTicks++;
      ClientPlayerEntity player = this.mc.player;
      ClientWorld level = this.mc.world;
      if (player == null || level == null || this.mc.interactionManager == null || this.mc.getNetworkHandler() != this.connection) {
         this.requestDisable();
      } else if (level.getRegistryKey() != this.dimension) {
         this.say(ModuleChat.body().warn("Left the dimension").text(", the flight is off."));
         this.requestDisable();
      } else if (player.isDead()) {
         this.say(ModuleChat.body().danger("Died on the way").text(" at ").coords(player.getBlockX(), player.getBlockY(), player.getBlockZ()).text("."));
         this.requestDisable();
      } else {
         this.releaseKeys();
         this.bookkeeping(player);
         if (this.status.get() && this.tick % 20 == 0) {
            this.showStatus(player);
         }

         if (this.state == ElytraAutopilotModule.State.TAKEOFF || this.state == ElytraAutopilotModule.State.SERVICE || !this.emergencyDeploy(player)) {
            if (!this.midAirSwap(player)) {
               switch (this.state) {
                  case PREPARE:
                     this.prepare(player);
                     break;
                  case TAKEOFF:
                     this.takeoff(player, level);
                     break;
                  case CLIMB:
                     this.climb(player, level);
                     break;
                  case CRUISE:
                     this.cruise(player, level);
                     break;
                  case DESCEND:
                     this.descend(player, level);
                     break;
                  case LAND:
                     this.land(player, level);
                     break;
                  case SERVICE:
                     this.service(player, level);
               }
            }
         }
      }
   }

   private void go(ElytraAutopilotModule.State next) {
      if (next == ElytraAutopilotModule.State.TAKEOFF) {
         this.takeoffTries++;
         this.jumps = 0;
         this.escapeYaw = Float.NaN;
      }

      if (next == ElytraAutopilotModule.State.CRUISE) {
         this.takeoffTries = 0;
      }

      if (next == ElytraAutopilotModule.State.DESCEND || next == ElytraAutopilotModule.State.LAND) {
         this.plan = null;
      }

      this.state = next;
      this.stateTicks = 0;
   }

   private void releaseKeys() {
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.useKey.setPressed(false);
   }

   private void bookkeeping(ClientPlayerEntity player) {
      Vec3d now = player.getEntityPos();
      if (this.lastPos != null && player.isGliding() && now.distanceTo(this.lastPos) < 20.0) {
         this.flown = this.flown + now.distanceTo(this.lastPos);
      }

      this.lastPos = now;
      if (!player.isTouchingWater()) {
         this.waterTicks = 0;
      }

      int got = this.setbacksReceived;

      while (this.seenSetbacks != got) {
         this.seenSetbacks++;
         if (player.isGliding()) {
            this.recentSetbacks.add(this.tick);
         }
      }

      this.recentSetbacks.removeIf(at -> this.tick - at > 200);
      if (this.recentSetbacks.size() >= 3) {
         this.rocketHoldUntil = this.tick + 60;
         this.boostUntil = 0;
         this.recentSetbacks.clear();
         EasyPlace.LOG.info("[Elytra Autopilot] setbacks, rockets held for {} ticks", 60);
      }

      if (this.eatingInFlight && (!player.isGliding() || !Eater.hungry(player) && !player.isUsingItem())) {
         this.eatingInFlight = false;
      }
   }

   private boolean emergencyDeploy(ClientPlayerEntity player) {
      if (player.isGliding() || player.isOnGround() || player.isTouchingWater() || player.isClimbing() || !usable(chest(player))) {
         return false;
      } else if (this.state != ElytraAutopilotModule.State.PREPARE && !(player.getVelocity().y > -0.5) && !(player.fallDistance < 3.0)) {
         this.mc.options.jumpKey.setPressed(this.tick % 2 == 0);
         Aim.turn(this.mc, player.getYaw(), 8.0F, 27.0F);
         return true;
      } else {
         return false;
      }
   }

   private boolean midAirSwap(ClientPlayerEntity player) {
      if (player.isGliding() && this.tick - this.swapTick >= 6) {
         int left = durability(chest(player));
         if (left >= 0 && left <= 6) {
            PlayerInventory inventory = player.getInventory();

            for (int i = 0; i < 9; i++) {
               if (durability(inventory.getStack(i)) >= 20 && inventory.getStack(i).isOf(Items.ELYTRA)) {
                  this.swapTick = this.tick;
                  if (inventory.getSelectedSlot() != i) {
                     inventory.setSelectedSlot(i);
                  } else {
                     this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                  }

                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void prepare(ClientPlayerEntity player) {
      if (player.isTouchingWater()) {
         this.swim(player);
      } else if (player.isGliding()) {
         this.go(ElytraAutopilotModule.State.CLIMB);
      } else if (player.isOnGround()) {
         if (this.mc.currentScreen instanceof HandledScreen && this.purchase == null) {
            player.closeHandledScreen();
         }

         if (!usable(chest(player))) {
            if (!Hands.has(player, ElytraAutopilotModule::usable)) {
               this.finish(false, "no usable elytra left");
            } else {
               if (Hands.hold(this.mc, player, ElytraAutopilotModule::usable) && this.stateTicks % 6 == 3) {
                  this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
               }
            }
         } else if (this.distance(player) <= 30.0) {
            this.finish(true, null);
         } else if (this.stateTicks >= 4) {
            ElytraAutopilotModule.Task next = this.nextTask(player);
            if (next != ElytraAutopilotModule.Task.NONE) {
               this.job = ElytraAutopilotModule.Job.SERVICE;
               this.go(ElytraAutopilotModule.State.SERVICE);
            } else if (count(player, ROCKET) == 0) {
               this.finish(false, "out of rockets");
            } else if (durability(chest(player)) < 30) {
               this.finish(false, "the elytra is nearly broken");
            } else if (!inHotbar(player, ROCKET)) {
               Hands.hold(this.mc, player, ROCKET);
            } else {
               this.job = ElytraAutopilotModule.Job.ARRIVE;
               this.go(ElytraAutopilotModule.State.TAKEOFF);
            }
         }
      }
   }

   private void takeoff(ClientPlayerEntity player, ClientWorld level) {
      if (this.takeoffTries > 6) {
         this.finish(false, "could not take off here");
      } else if (player.isTouchingWater()) {
         this.go(ElytraAutopilotModule.State.PREPARE);
      } else if (player.isGliding()) {
         this.go(ElytraAutopilotModule.State.CLIMB);
      } else if (!inHotbar(player, ROCKET)) {
         this.go(ElytraAutopilotModule.State.PREPARE);
      } else {
         Hands.hold(this.mc, player, ROCKET);
         if (player.isOnGround()) {
            if (Float.isNaN(this.escapeYaw)) {
               this.escapeYaw = openSky(player, level) ? player.getYaw() : escapeHeading(player, level);
            }

            if (!Float.isNaN(this.escapeYaw) && this.escapeYaw != Float.NEGATIVE_INFINITY) {
               boolean open = openSky(player, level);
               float yaw = open ? player.getYaw() : this.escapeYaw;
               float pitch = open ? -88.0F : -35.0F;
               Aim.turn(this.mc, yaw, pitch, 22.0F);
               boolean aimed = Math.abs(MathHelper.wrapDegrees(player.getYaw() - yaw)) < 10.0F && Math.abs(player.getPitch() - pitch) < 10.0F;
               if (aimed && this.tick - this.jumpTick >= 14) {
                  if (++this.jumps > 5) {
                     this.finish(false, "could not get the elytra open");
                     return;
                  }

                  this.mc.options.jumpKey.setPressed(true);
                  this.jumpTick = this.tick;
               }
            } else {
               this.finish(false, "no open sky to take off into");
            }
         } else {
            Aim.turn(this.mc, player.getYaw(), player.getPitch() < -60.0F ? player.getPitch() : -85.0F, 22.0F);
            if (player.getVelocity().y < 0.12 && this.tick - this.jumpTick >= 2) {
               this.mc.options.jumpKey.setPressed(this.tick % 2 == 0);
            }
         }
      }
   }

   private static boolean openSky(ClientPlayerEntity player, ClientWorld level) {
      Box box = player.getBoundingBox().contract(0.02);

      for (int up = 1; up <= 16; up++) {
         if (!level.isSpaceEmpty(player, box.offset(0.0, up, 0.0))) {
            return false;
         }
      }

      return true;
   }

   private static float escapeHeading(ClientPlayerEntity player, ClientWorld level) {
      if (!level.isSpaceEmpty(player, player.getBoundingBox().contract(0.02).offset(0.0, 2.2, 0.0))) {
         return Float.NEGATIVE_INFINITY;
      }

      float[] offsets = new float[]{0.0F, 45.0F, -45.0F, 90.0F, -90.0F, 135.0F, -135.0F, 180.0F};

      for (float offset : offsets) {
         float yaw = player.getYaw() + offset;
         Vec3d direction = Vec3d.fromPolar(-32.0F, yaw);
         Vec3d side = Vec3d.fromPolar(0.0F, yaw + 90.0F).multiply(0.35);
         Vec3d feet = player.getEntityPos();
         Vec3d[] starts = new Vec3d[]{
            feet.add(0.0, 1.2, 0.0), feet.add(0.0, 1.8, 0.0), feet.add(0.0, 1.2, 0.0).add(side), feet.add(0.0, 1.2, 0.0).subtract(side)
         };
         boolean clear = true;

         for (Vec3d start : starts) {
            BlockHitResult hit = level.raycast(new RaycastContext(start, start.add(direction.multiply(22.0)), ShapeType.COLLIDER, FluidHandling.NONE, player));
            if (hit.getType() != Type.MISS) {
               clear = false;
               break;
            }
         }

         if (clear) {
            return yaw;
         }
      }

      return Float.NEGATIVE_INFINITY;
   }

   private void swim(ClientPlayerEntity player) {
      if (++this.waterTicks > 1200) {
         this.finish(false, "stuck in water");
      } else {
         this.mc.options.jumpKey.setPressed(true);
         BlockPos shore = this.shore(player);
         if (shore != null) {
            float yaw = ElytraFlight.bearing(player.getX(), player.getZ(), shore.getX() + 0.5, shore.getZ() + 0.5);
            Aim.turn(this.mc, yaw, 0.0F, 22.0F);
            this.mc.options.forwardKey.setPressed(Math.abs(MathHelper.wrapDegrees(player.getYaw() - yaw)) < 30.0F);
         }
      }
   }

   private BlockPos shore(ClientPlayerEntity player) {
      ClientWorld level = this.mc.world;
      int x0 = player.getBlockX();
      int z0 = player.getBlockZ();

      for (int r = 1; r <= 32; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int top = level.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x0 + dx, z0 + dz);
                  if (ElytraFlight.goodFloor(level, new BlockPos(x0 + dx, top - 1, z0 + dz))) {
                     return new BlockPos(x0 + dx, top, z0 + dz);
                  }
               }
            }
         }
      }

      return null;
   }

   private boolean flying(ClientPlayerEntity player) {
      if (player.isGliding()) {
         return true;
      }

      if (player.isTouchingWater()) {
         this.go(ElytraAutopilotModule.State.PREPARE);
      } else if (player.isOnGround()) {
         if (this.state != ElytraAutopilotModule.State.DESCEND && this.state != ElytraAutopilotModule.State.LAND) {
            this.go(ElytraAutopilotModule.State.PREPARE);
         } else {
            this.touchdown(player);
         }
      } else {
         this.mc.options.jumpKey.setPressed(this.tick % 2 == 0);
      }

      return false;
   }

   private void climb(ClientPlayerEntity player, ClientWorld level) {
      if (this.flying(player)) {
         double distance = this.distance(player);
         double cruiseAt = this.cruiseAltitude(level, distance);
         if (!this.supplyCheck(player, distance)) {
            if (!(player.getY() >= cruiseAt - 15.0) && this.stateTicks <= 600) {
               ElytraFlight.Terrain terrain = this.terrain(level);
               if (clearAround(player, terrain)) {
                  Aim.turn(this.mc, player.getYaw(), -85.0F, 22.0F);
                  if (player.getPitch() < -60.0F && !this.boosting() && this.rocketReady(player)) {
                     this.fire(player);
                  }
               } else if (terrain.surface(player.getX(), player.getZ()) > player.getY() + 1.5 && this.stateTicks < 200) {
                  float yaw = !Float.isNaN(this.escapeYaw) && this.escapeYaw != Float.NEGATIVE_INFINITY ? this.escapeYaw : player.getYaw();
                  Aim.turn(this.mc, yaw, -32.0F, 27.0F);
                  if (Math.abs(player.getPitch() + 32.0F) < 12.0F && !this.boosting() && this.rocketReady(player)) {
                     this.fire(player);
                  }
               } else {
                  this.steer(
                     player,
                     terrain,
                     new ElytraFlight.Goal(
                        this.destX, this.destZ, cruiseAt, 14.0, false, this.rocketsAllowed(player, level, cruiseAt), rocketCost(player), 9.0, floor(level)
                     ),
                     false
                  );
               }
            } else {
               this.go(distance <= this.descentStart(level, player) ? ElytraAutopilotModule.State.DESCEND : ElytraAutopilotModule.State.CRUISE);
            }
         }
      }
   }

   private void cruise(ClientPlayerEntity player, ClientWorld level) {
      if (this.flying(player)) {
         double distance = this.distance(player);
         if (!this.supplyCheck(player, distance)) {
            if (!this.progressStalled(distance)) {
               if (distance <= this.descentStart(level, player)) {
                  this.job = ElytraAutopilotModule.Job.ARRIVE;
                  this.go(ElytraAutopilotModule.State.DESCEND);
               } else {
                  double cruiseAt = this.cruiseAltitude(level, distance);
                  ElytraFlight.Terrain terrain = this.terrain(level);
                  if (this.eatInFlight(player)) {
                     this.glideOn(player, terrain, cruiseAt, false);
                  } else {
                     boolean rockets = this.rocketsAllowed(player, level, cruiseAt);
                     double need = this.terrainAhead(player, terrain, distance) + 14.0 + 4.0;
                     double low = Math.max(need, cruiseAt - 45.0 + this.bandJitter);
                     double high = Math.max(cruiseAt + 15.0 + this.bandJitter, low + 10.0);
                     if (!this.climbing && player.getY() < low && (rockets || this.boosting())) {
                        this.climbing = true;
                     }

                     if (this.climbing && player.getY() >= high - 8.0) {
                        this.climbing = false;
                        this.newJitter();
                     }

                     if (this.climbing && !rockets && !this.boosting() && player.getVelocity().y < 0.05 && player.getPitch() < -37.0F) {
                        this.climbing = false;
                     }

                     this.glideOn(player, terrain, cruiseAt, rockets);
                  }
               }
            }
         }
      }
   }

   private void glideOn(ClientPlayerEntity player, ElytraFlight.Terrain terrain, double cruiseAt, boolean rockets) {
      float yaw = ElytraFlight.bearing(player.getX(), player.getZ(), this.destX, this.destZ);
      double speed = player.getVelocity().horizontalLength();
      boolean rocket = false;
      float pitch;
      if (this.climbing) {
         pitch = -47.0F;
         rocket = rockets && !this.boosting() && player.getVelocity().y < 0.45;
      } else {
         pitch = (speed < 1.0 ? 14.0F : (speed < 1.4 ? 7.0F : 2.0F)) + this.glideJitter;
      }

      ElytraFlight.Goal goal = new ElytraFlight.Goal(this.destX, this.destZ, cruiseAt, 14.0, false, rockets, rocketCost(player), 9.0, floor(this.mc.world));
      ElytraFlight.Glide now = ElytraFlight.Glide.of(player, this.boostLeft());
      if (ElytraFlight.clear(now, goal, terrain, yaw, pitch, rocket, 22.0F)) {
         this.plan = null;
         Aim.turn(this.mc, yaw, pitch, 22.0F);
         if (rocket && this.rocketReady(player) && aimedAt(player, yaw, pitch)) {
            this.fire(player);
         }
      } else {
         this.steer(player, terrain, goal, false);
      }
   }

   private void steer(ClientPlayerEntity player, ElytraFlight.Terrain terrain, ElytraFlight.Goal goal, boolean urgent) {
      if (this.plan == null || this.tick - this.planTick >= 2) {
         this.plan = ElytraFlight.search(ElytraFlight.Glide.of(player, this.boostLeft()), goal, terrain, this.boosting(), 22.0F);
         this.planTick = this.tick;
      }

      float turn = !urgent && this.plan.safe() ? 22.0F : 27.0F;
      Aim.turn(this.mc, this.plan.yaw(), this.plan.pitch(), turn);
      if (this.plan.rocket() && !this.boosting() && this.rocketReady(player) && aimedAt(player, this.plan.yaw(), this.plan.pitch())) {
         this.fire(player);
      }
   }

   private static boolean aimedAt(ClientPlayerEntity player, float yaw, float pitch) {
      return Math.abs(player.getPitch() - pitch) < 20.0F && Math.abs(MathHelper.wrapDegrees(player.getYaw() - yaw)) < 30.0F;
   }

   private void descend(ClientPlayerEntity player, ClientWorld level) {
      if (this.flying(player)) {
         double distance = this.distance(player);
         if (this.job != ElytraAutopilotModule.Job.ARRIVE || !this.supplyCheck(player, distance)) {
            boolean urgent = this.job != ElytraAutopilotModule.Job.ARRIVE && (this.critical(player) || this.job == ElytraAutopilotModule.Job.STOP);
            ElytraFlight.Terrain terrain = this.terrain(level);
            if (this.spot == null && this.tick >= this.nextSpotSearch) {
               this.findSpot(player, terrain, urgent);
            }

            if (this.spot == null && this.job != ElytraAutopilotModule.Job.ARRIVE) {
               this.glideOn(player, terrain, this.cruiseAltitude(level, distance), this.rocketsAllowed(player, level, this.cruiseAltitude(level, distance)));
            } else {
               double tx = this.spot != null ? this.spot.getX() + 0.5 : this.destX;
               double tz = this.spot != null ? this.spot.getZ() + 0.5 : this.destZ;
               double ty = this.spot != null ? this.spot.getY() : surfaceAt(level, this.destX, this.destZ);
               double hd = Math.hypot(player.getX() - tx, player.getZ() - tz);
               double dh = player.getY() - ty;
               if (this.spot != null) {
                  if (dh < this.bestDrop - 3.0) {
                     this.bestDrop = dh;
                     this.dropTick = this.tick;
                  } else if (this.tick - this.dropTick > 700) {
                     this.ban(this.spot);
                     return;
                  }

                  if (hd <= 50.0 && dh >= -1.0 && dh <= 28.0) {
                     this.go(ElytraAutopilotModule.State.LAND);
                     return;
                  }
               }

               double path = ty + 14.0 + Math.max(0.0, hd - 45.0) * 0.095;
               double above = player.getY() - path;
               if (!this.spiralling && hd < 170.0 && dh > 32.0 && (above > 22.0 || hd < 60.0)) {
                  this.spiralling = true;
               }

               if (this.spiralling && (dh <= 26.0 || above < 8.0 && hd >= 60.0 || hd > 260.0)) {
                  this.spiralling = false;
               }

               double clear = Math.max(3.0, Math.min(14.0, (hd - 45.0) * 0.25 + 4.0));
               if (this.spiralling) {
                  float around = ElytraFlight.bearing(tx, tz, player.getX(), player.getZ()) + 90.0F + 55.0F;
                  Vec3d ahead = Vec3d.fromPolar(0.0F, around).multiply(42.0);
                  this.steer(
                     player,
                     terrain,
                     new ElytraFlight.Goal(
                        tx + ahead.x, tz + ahead.z, ty + 14.0, Math.max(10.0, clear), true, false, rocketCost(player), above > 40.0 ? 3.0 : 1.9, floor(level)
                     ),
                     false
                  );
               } else {
                  boolean rockets = this.rocketsAllowed(player, level, path) && dh < 10.0;
                  this.steer(player, terrain, new ElytraFlight.Goal(tx, tz, path, clear, true, rockets, rocketCost(player), 1.45, floor(level)), urgent);
               }
            }
         }
      }
   }

   private void findSpot(ClientPlayerEntity player, ElytraFlight.Terrain terrain, boolean urgent) {
      this.nextSpotSearch = this.tick + (urgent ? 10 : 20);
      int cx;
      int cz;
      int radius;
      if (this.job == ElytraAutopilotModule.Job.ARRIVE) {
         if (!terrain.known(MathHelper.floor(this.destX), MathHelper.floor(this.destZ))) {
            return;
         }

         cx = MathHelper.floor(this.destX);
         cz = MathHelper.floor(this.destZ);
         this.spotSearches++;
         radius = this.spotSearches > 12 ? 300 : (this.spotSearches > 4 ? 160 : 96);
         if (this.spotSearches > 40) {
            this.finish(false, "no safe landing near the target");
            return;
         }
      } else {
         Vec3d ahead = Vec3d.fromPolar(0.0F, player.getYaw()).multiply(urgent ? 30.0 : 110.0);
         cx = MathHelper.floor(player.getX() + ahead.x);
         cz = MathHelper.floor(player.getZ() + ahead.z);
         radius = urgent ? 120 : 150;
      }

      Predicate<BlockPos> bad = pos -> {
         for (BlockPos ban : this.banned) {
            if (ban.getManhattanDistance(pos) < 30 + Math.abs(ban.getY() - pos.getY())) {
               return true;
            }
         }

         return false;
      };
      BlockPos found = ElytraFlight.landingSpot(terrain, cx, cz, radius, true, bad);
      if (found == null) {
         found = ElytraFlight.landingSpot(terrain, cx, cz, radius, false, bad);
      }

      if (found != null) {
         this.spot = found;
         this.bestDrop = Double.MAX_VALUE;
         this.dropTick = this.tick;
         EasyPlace.LOG.info("[Elytra Autopilot] landing spot {}", found.toShortString());
      }
   }

   private void ban(BlockPos where) {
      this.banned.add(where);
      this.spot = null;
      this.goArounds = 0;
      this.nextSpotSearch = this.tick;
   }

   private void land(ClientPlayerEntity player, ClientWorld level) {
      if (!player.isGliding()) {
         if (player.isOnGround() || player.isTouchingWater()) {
            this.touchdown(player);
         } else if (player.getVelocity().y < -0.3) {
            this.mc.options.jumpKey.setPressed(this.tick % 2 == 0);
         }
      } else if (this.spot == null) {
         this.go(ElytraAutopilotModule.State.DESCEND);
      } else {
         double hd = Math.hypot(player.getX() - (this.spot.getX() + 0.5), player.getZ() - (this.spot.getZ() + 0.5));
         double dh = player.getY() - this.spot.getY();
         if (!(dh < -4.0) && this.stateTicks <= 700 && !(hd > 90.0)) {
            if (this.plan == null || this.tick - this.planTick >= 2) {
               this.plan = ElytraFlight.touchdown(ElytraFlight.Glide.of(player, this.boostLeft()), this.spot, this.terrain(level), 27.0F);
               this.planTick = this.tick;
            }

            Aim.turn(this.mc, this.plan.yaw(), this.plan.pitch(), 27.0F);
         } else {
            if (++this.goArounds >= 4) {
               this.ban(this.spot);
            }

            EasyPlace.LOG.info("[Elytra Autopilot] going round again ({})", this.goArounds);
            this.go(ElytraAutopilotModule.State.DESCEND);
         }
      }
   }

   private void touchdown(ClientPlayerEntity player) {
      this.plan = null;
      this.spiralling = false;
      switch (this.job) {
         case ARRIVE:
            if (this.distance(player) <= 72.0) {
               this.finish(true, null);
            } else {
               if (this.spot != null) {
                  this.ban(this.spot);
               }

               this.go(ElytraAutopilotModule.State.PREPARE);
            }
            break;
         case SERVICE:
            this.spot = null;
            this.pitStops++;
            this.doneTasks.clear();
            this.go(ElytraAutopilotModule.State.SERVICE);
            break;
         case STOP:
            this.finish(false, this.stopReason != null ? this.stopReason : "had to stop");
      }
   }

   private boolean supplyCheck(ClientPlayerEntity player, double distance) {
      if (this.tick % 20 == 0 && this.job == ElytraAutopilotModule.Job.ARRIVE) {
         ItemStack chest = chest(player);
         int left = durability(chest);
         int unbreaking = level(chest, Enchantments.UNBREAKING);
         int rockets = count(player, ROCKET);
         double seconds = distance / 27.0 + 50.0;
         double durNeeded = seconds / (1 + unbreaking) * 1.35 + 25.0;
         double rocketNeeded = distance / 1000.0 * 2.6 + 5.0;
         boolean enough = (left < 0 || left > durNeeded) && rockets > rocketNeeded;
         if (enough && !this.critical(player)) {
            return false;
         }

         boolean canMend = this.canMend(player);
         boolean spare = spare(player) >= 0;
         boolean needMend = left >= 0 && left <= mendThreshold(chest);
         ElytraAutopilotModule.Job next = null;
         String why = null;
         if (!needMend || !canMend && !spare) {
            if (needMend && left <= 40) {
               next = ElytraAutopilotModule.Job.STOP;
               why = "the elytra is worn out";
            } else if (rockets <= 20 && this.canBuy(Items.FIREWORK_ROCKET)) {
               next = ElytraAutopilotModule.Job.SERVICE;
               why = "to buy rockets";
            } else if (rockets <= 6) {
               next = ElytraAutopilotModule.Job.STOP;
               why = "out of rockets";
            }
         } else {
            next = ElytraAutopilotModule.Job.SERVICE;
            why = canMend ? "to repair the elytra" : "to put on the spare elytra";
         }

         if (next == null) {
            return false;
         }

         this.job = next;
         this.stopReason = next == ElytraAutopilotModule.Job.STOP ? why : null;
         this.spot = null;
         this.goArounds = 0;
         this.say(ModuleChat.body().warn(next == ElytraAutopilotModule.Job.SERVICE ? "Landing for a pit stop" : "Landing to stop").text(" (" + why + ")."));
         this.go(ElytraAutopilotModule.State.DESCEND);
         return true;
      } else {
         return false;
      }
   }

   private boolean critical(ClientPlayerEntity player) {
      ItemStack chest = chest(player);
      int left = durability(chest);
      int floor = level(chest, Enchantments.UNBREAKING) >= 1 ? 40 : 60;
      return left >= 0 && left <= floor || count(player, ROCKET) <= 3;
   }

   private boolean progressStalled(double distance) {
      if (distance < this.bestDistance - 60.0) {
         this.bestDistance = distance;
         this.progressTick = this.tick;
         return false;
      } else if (this.tick - this.progressTick < 900) {
         return false;
      } else {
         this.progressTick = this.tick;
         this.bestDistance = distance;
         if (++this.stalls >= 6) {
            this.job = ElytraAutopilotModule.Job.STOP;
            this.stopReason = "no progress";
            this.go(ElytraAutopilotModule.State.DESCEND);
            return true;
         } else {
            this.altitudeBump = Math.min(this.altitudeBump + 20.0, 316.0 - this.altitude.get());
            this.plan = null;
            this.say(ModuleChat.body().warn("Not getting closer").text(", flying higher."));
            return false;
         }
      }
   }

   private boolean eatInFlight(ClientPlayerEntity player) {
      if (this.eatingInFlight || Eater.hungry(player) && !this.climbing && !this.boosting() && !(player.getY() < 70.0)) {
         int food = hotbarSlot(player, Eater::edible);
         if (food < 0) {
            this.eatingInFlight = false;
            return false;
         }

         player.getInventory().setSelectedSlot(food);
         this.mc.options.useKey.setPressed(true);
         if (!this.eatingInFlight) {
            this.eatLockUntil = this.tick + 45;
         }

         this.eatingInFlight = true;
         return true;
      } else {
         return false;
      }
   }

   private void service(ClientPlayerEntity player, ClientWorld level) {
      if (player.isTouchingWater()) {
         this.swim(player);
      } else if (player.isGliding()) {
         this.go(ElytraAutopilotModule.State.LAND);
      } else if (player.isOnGround()) {
         if (!this.danger(player, level)) {
            if (this.task == ElytraAutopilotModule.Task.NONE) {
               this.task = this.nextTask(player);
               this.taskTicks = 0;
               if (this.task == ElytraAutopilotModule.Task.NONE) {
                  if (count(player, ROCKET) == 0) {
                     this.finish(false, "out of rockets");
                     return;
                  }

                  this.say(ModuleChat.body().text("Pit stop done: " + count(player, ROCKET) + " rockets, elytra " + durability(chest(player)) + "."));
                  this.job = ElytraAutopilotModule.Job.ARRIVE;
                  this.go(ElytraAutopilotModule.State.PREPARE);
                  return;
               }

               this.doneTasks.add(this.task);
            }

            this.taskTicks++;
            switch (this.task) {
               case EAT:
                  this.eat(player);
                  break;
               case SWAP:
                  this.swapElytra(player);
                  break;
               case MEND:
                  this.mendElytra(player);
                  break;
               case BUY_ROCKETS:
               case BUY_XP:
                  this.buy(player);
                  break;
               default:
                  this.task = ElytraAutopilotModule.Task.NONE;
            }
         } else {
            this.stopTask();
            if (count(player, ROCKET) >= 2 && durability(chest(player)) >= 30) {
               this.banned.add(player.getBlockPos());
               this.say(ModuleChat.body().warn("Trouble at the pit stop").text(", taking off again."));
               this.job = ElytraAutopilotModule.Job.ARRIVE;
               this.go(ElytraAutopilotModule.State.TAKEOFF);
            } else {
               this.finish(false, "attacked at the pit stop");
            }
         }
      }
   }

   private ElytraAutopilotModule.Task nextTask(ClientPlayerEntity player) {
      ItemStack chest = chest(player);
      int left = durability(chest);
      boolean worn = left >= 0 && left <= mendThreshold(chest);
      boolean mendable = this.mend.get() && mends(chest) && this.mendFailures < 3 && left >= 0 && left < chest.getMaxDamage() * 0.97;
      if (!this.doneTasks.contains(ElytraAutopilotModule.Task.EAT) && Eater.hungry(player) && Eater.hasFood(player)) {
         return ElytraAutopilotModule.Task.EAT;
      } else if (!this.doneTasks.contains(ElytraAutopilotModule.Task.SWAP) && worn && (!mendable || count(player, XP) == 0) && spare(player) >= 0) {
         return ElytraAutopilotModule.Task.SWAP;
      } else {
         int rockets = count(player, ROCKET);
         if (this.distance(player) < 1500.0 && rockets >= 4) {
            return !this.doneTasks.contains(ElytraAutopilotModule.Task.MEND) && worn && mendable && count(player, XP) > 0
               ? ElytraAutopilotModule.Task.MEND
               : ElytraAutopilotModule.Task.NONE;
         } else if (!this.doneTasks.contains(ElytraAutopilotModule.Task.BUY_XP) && mendable && count(player, XP) == 0 && this.canBuy(Items.EXPERIENCE_BOTTLE)) {
            return ElytraAutopilotModule.Task.BUY_XP;
         } else if (!this.doneTasks.contains(ElytraAutopilotModule.Task.MEND) && mendable && count(player, XP) > 0) {
            return ElytraAutopilotModule.Task.MEND;
         } else if (!this.doneTasks.contains(ElytraAutopilotModule.Task.BUY_ROCKETS)
            && rockets < this.rocketStacks.getInt() * 32
            && this.canBuy(Items.FIREWORK_ROCKET)) {
            return ElytraAutopilotModule.Task.BUY_ROCKETS;
         } else {
            return !this.doneTasks.contains(ElytraAutopilotModule.Task.BUY_XP)
                  && this.mend.get()
                  && mends(chest)
                  && count(player, XP) < this.xpStacks.getInt() * 32
                  && this.canBuy(Items.EXPERIENCE_BOTTLE)
               ? ElytraAutopilotModule.Task.BUY_XP
               : ElytraAutopilotModule.Task.NONE;
         }
      }
   }

   private void stopTask() {
      if (this.purchase != null) {
         this.purchase.cancel();
      }

      this.purchase = null;
      this.purchaseItem = null;
      this.verifyUntil = 0;
      if (this.eater != null) {
         this.eater.release(this.mc);
      }

      this.eater = null;
      this.task = ElytraAutopilotModule.Task.NONE;
      if (this.mc.currentScreen instanceof HandledScreen && this.mc.player != null) {
         this.mc.player.closeHandledScreen();
      }
   }

   private void eat(ClientPlayerEntity player) {
      if (this.eater == null) {
         this.eater = new Eater();
      }

      Eater.Result result = this.eater.tick(this.mc);
      if (result != Eater.Result.RUNNING || this.taskTicks > 200) {
         this.eater.release(this.mc);
         this.eater = null;
         this.task = ElytraAutopilotModule.Task.NONE;
      }
   }

   private void swapElytra(ClientPlayerEntity player) {
      int before = durability(chest(player));
      int spare = spare(player);
      if (spare >= 0 && this.taskTicks <= 120) {
         int want = durability(player.getInventory().getStack(spare));
         Predicate<ItemStack> better = stack -> stack.isOf(Items.ELYTRA) && durability(stack) == want;
         if (Hands.hold(this.mc, player, better) && this.taskTicks % 6 == 3) {
            this.mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
         }

         if (durability(chest(player)) > before) {
            this.say(ModuleChat.body().good("Put on the spare elytra").text(" (" + durability(chest(player)) + " left)."));
            this.task = ElytraAutopilotModule.Task.NONE;
         }
      } else {
         this.task = ElytraAutopilotModule.Task.NONE;
      }
   }

   private void mendElytra(ClientPlayerEntity player) {
      ItemStack chest = chest(player);
      int left = durability(chest);
      if (this.taskTicks == 1) {
         this.mendBest = left;
         this.mendGainTick = this.tick;
      }

      if (left < chest.getMaxDamage() && left >= 0) {
         if (left > this.mendBest) {
            this.mendBest = left;
            this.mendGainTick = this.tick;
         }

         if (this.tick - this.mendGainTick > 200) {
            this.mendFailures++;
            this.say(ModuleChat.body().warn("The XP is not reaching the elytra").text(" (other Mending gear may be taking it)."));
            this.task = ElytraAutopilotModule.Task.NONE;
         } else if (count(player, XP) == 0) {
            this.task = this.canBuy(Items.EXPERIENCE_BOTTLE) && !this.doneTasks.contains(ElytraAutopilotModule.Task.BUY_XP)
               ? ElytraAutopilotModule.Task.BUY_XP
               : ElytraAutopilotModule.Task.NONE;
            if (this.task == ElytraAutopilotModule.Task.BUY_XP) {
               this.doneTasks.add(ElytraAutopilotModule.Task.BUY_XP);
               this.taskTicks = 0;
            }
         } else if (Hands.hold(this.mc, player, XP)) {
            Aim.turn(this.mc, player.getYaw(), 86.0F, 22.0F);
            this.mc.options.useKey.setPressed(player.getPitch() > 70.0F);
         }
      } else {
         if (left >= 0) {
            this.say(ModuleChat.body().good("Elytra repaired").text("."));
         }

         this.mendFailures = 0;
         this.task = ElytraAutopilotModule.Task.NONE;
      }
   }

   private void buy(ClientPlayerEntity player) {
      if (this.verifyUntil > 0) {
         this.verifyPurchase(player);
      } else if (this.purchase != null) {
         Aim.turn(this.mc, player.getYaw(), 40.0F, 22.0F);
         this.followPurchase();
      } else {
         boolean rockets = this.task == ElytraAutopilotModule.Task.BUY_ROCKETS;
         Item item = rockets ? Items.FIREWORK_ROCKET : Items.EXPERIENCE_BOTTLE;
         Predicate<ItemStack> kind = rockets ? ElytraAutopilotModule::buyableRocket : XP;
         int want = (rockets ? this.rocketStacks.getInt() : this.xpStacks.getInt()) * 64 - count(player, kind);
         long stackCap = AhPurchase.parseMoney(rockets ? this.rocketPrice.get() : this.xpPrice.get());
         if (want > 0 && stackCap > 0L && this.canBuy(item) && freeSlots(player) != 0) {
            double unitCap = stackCap / 64.0;
            Predicate<ItemStack> listing = stack -> kind.test(stack) && AhPurchase.unitPrice(stack) <= unitCap;
            this.purchaseItem = item;
            this.purchaseBefore = count(player, kind);
            long limit = AhPurchase.parseMoney(this.tripLimit.get());
            long left = limit <= 0L ? 0L : Math.max(1L, limit - this.spent);
            this.say(ModuleChat.body().text("Buying " + want + (rockets ? " rockets" : " XP bottles") + " on the AH."));
            this.purchase = AhPurchase.startAny(
               this, item, rockets ? "Rockets" : "XP Bottles", rockets ? "firework rocket" : "xp bottle stack", listing, want, stackCap, 1000L, left
            );
         } else {
            this.task = ElytraAutopilotModule.Task.NONE;
         }
      }
   }

   private void followPurchase() {
      AhPurchase.Status now = this.purchase.status();
      if (now != AhPurchase.Status.RUNNING || this.taskTicks >= 4800) {
         this.spent = this.spent + this.purchase.spent();
         if (now == AhPurchase.Status.RUNNING) {
            this.purchase.cancel();
         }

         this.purchase = null;
         if (now != AhPurchase.Status.DONE) {
            this.buyFails.merge(this.purchaseItem, 1, Integer::sum);
            this.nextBuy.put(this.purchaseItem, this.tick + 6000);
            this.say(ModuleChat.body().warn("Could not buy " + (this.purchaseItem == Items.FIREWORK_ROCKET ? "rockets" : "XP bottles")).text(", flying on."));
            this.purchaseItem = null;
            this.task = ElytraAutopilotModule.Task.NONE;
         } else {
            this.verifyUntil = this.tick + 60;
         }
      }
   }

   private void verifyPurchase(ClientPlayerEntity player) {
      Predicate<ItemStack> kind = this.purchaseItem == Items.FIREWORK_ROCKET ? ElytraAutopilotModule::buyableRocket : XP;
      int got = count(player, kind) - this.purchaseBefore;
      if (got > 0 || this.tick >= this.verifyUntil) {
         if (got > 0) {
            this.say(ModuleChat.body().good("Bought " + got + (this.purchaseItem == Items.FIREWORK_ROCKET ? " rockets" : " XP bottles")).text("."));
            EasyPlace.LOG.info("[Elytra Autopilot] bought {} {} (verified), {} spent this trip", new Object[]{got, this.purchaseItem, this.spent});
         } else {
            this.say(ModuleChat.body().warn("The AH sold it but nothing showed up").text(", trying again later."));
            this.nextBuy.put(this.purchaseItem, this.tick + 6000);
         }

         this.verifyUntil = 0;
         this.purchaseItem = null;
         this.task = ElytraAutopilotModule.Task.NONE;
      }
   }

   private boolean canBuy(Item item) {
      boolean on = item == Items.FIREWORK_ROCKET ? this.buyRockets.get() : this.buyXp.get();
      if (on && this.tick >= this.nextBuy.getOrDefault(item, 0) && this.buyFails.getOrDefault(item, 0) < 6) {
         long limit = AhPurchase.parseMoney(this.tripLimit.get());
         return limit <= 0L || this.spent < limit;
      } else {
         return false;
      }
   }

   private boolean danger(ClientPlayerEntity player, ClientWorld level) {
      if (player.hurtTime > 0 && player.getHealth() < 14.0F) {
         return true;
      }

      Box around = player.getBoundingBox().expand(4.5, 2.0, 4.5);

      for (LivingEntity entity : level.getNonSpectatingEntities(LivingEntity.class, around)) {
         if (entity instanceof Monster && entity.isAlive() && player.canSee(entity)) {
            return true;
         }
      }

      return false;
   }

   private boolean rocketReady(ClientPlayerEntity player) {
      if (this.tick - this.lastRocket < 10 || this.tick < this.eatLockUntil || this.tick < this.rocketHoldUntil || player.isUsingItem()) {
         return false;
      } else {
         return this.mc.currentScreen != null ? false : hotbarSlot(player, ROCKET) >= 0 || ROCKET.test(player.getOffHandStack());
      }
   }

   private void fire(ClientPlayerEntity player) {
      if (player.isGliding()) {
         Vec3d eye = player.getEyePos();
         BlockHitResult ahead = this.mc
            .world
            .raycast(new RaycastContext(eye, eye.add(player.getRotationVector().multiply(10.0)), ShapeType.COLLIDER, FluidHandling.ANY, player));
         if (ahead.getType() == Type.MISS) {
            Hand hand;
            if (ROCKET.test(player.getMainHandStack())) {
               hand = Hand.MAIN_HAND;
            } else {
               if (!ROCKET.test(player.getOffHandStack())) {
                  int slot = hotbarSlot(player, ROCKET);
                  if (slot >= 0) {
                     player.getInventory().setSelectedSlot(slot);
                  }

                  return;
               }

               hand = Hand.OFF_HAND;
            }

            if (this.mc.interactionManager.interactItem(player, hand).isAccepted()) {
               player.swingHand(hand);
               this.lastRocket = this.tick;
               this.boostUntil = this.tick + 45;
               this.rocketsUsed++;
            }
         }
      }
   }

   private boolean boosting() {
      return this.tick < this.boostUntil;
   }

   private int boostLeft() {
      return Math.max(0, this.boostUntil - this.tick);
   }

   private boolean rocketsAllowed(ClientPlayerEntity player, ClientWorld level, double cruiseAt) {
      if (this.tick >= this.rocketHoldUntil && this.tick >= this.eatLockUntil && count(player, ROCKET) != 0) {
         Vec3d motion = player.getVelocity();
         Vec3d way = motion.horizontalLength() > 0.2 ? new Vec3d(motion.x, 0.0, motion.z).normalize() : Vec3d.fromPolar(0.0F, player.getYaw());
         Vec3d ahead = player.getEntityPos().add(way.multiply(110.0));
         return level.getChunkManager().isChunkLoaded(MathHelper.floor(ahead.x) >> 4, MathHelper.floor(ahead.z) >> 4) || player.getY() < cruiseAt - 50.0;
      } else {
         return false;
      }
   }

   private static double rocketCost(ClientPlayerEntity player) {
      int left = count(player, ROCKET);
      return left < 12 ? 140.0 : (left < 32 ? 95.0 : 70.0);
   }

   private ElytraFlight.Terrain terrain(ClientWorld level) {
      return new ElytraFlight.Terrain(level, (level.getRegistryKey() == World.END ? 185.0 : 262.0) - 14.0);
   }

   private static double floor(ClientWorld level) {
      return level.getRegistryKey() == World.END ? 35.0 : level.getBottomY();
   }

   private static double surfaceAt(ClientWorld level, double x, double z) {
      int bx = MathHelper.floor(x);
      int bz = MathHelper.floor(z);
      if (!level.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)) {
         return level.getRegistryKey() == World.END ? 60.0 : 68.0;
      } else {
         return level.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, bx, bz);
      }
   }

   private double cruiseAltitude(ClientWorld level, double distance) {
      double surface = surfaceAt(level, this.destX, this.destZ);
      double lowest = level.getRegistryKey() == World.END ? 150.0 : 140.0;
      double wanted = surface + 14.0 + Math.max(0.0, distance - 45.0) * 0.095 + 30.0;
      return Math.max(lowest, Math.min(this.altitude.get() + this.altitudeBump, wanted));
   }

   private double descentStart(ClientWorld level, ClientPlayerEntity player) {
      double surface = surfaceAt(level, this.destX, this.destZ);
      return 45.0 + Math.max(0.0, player.getY() - surface - 14.0) / 0.095 + 60.0;
   }

   private double terrainAhead(ClientPlayerEntity player, ElytraFlight.Terrain terrain, double distance) {
      float yaw = ElytraFlight.bearing(player.getX(), player.getZ(), this.destX, this.destZ);
      Vec3d way = Vec3d.fromPolar(0.0F, yaw);
      Vec3d side = Vec3d.fromPolar(0.0F, yaw + 90.0F);
      double length = Math.min(320.0, distance + 24.0);
      double top = 0.0;

      for (double along = 0.0; along <= length; along += 4.0) {
         for (int offset = -8; offset <= 8; offset += 8) {
            double x = player.getX() + way.x * along + side.x * offset;
            double z = player.getZ() + way.z * along + side.z * offset;
            if (terrain.known(MathHelper.floor(x), MathHelper.floor(z)) || !(along > 200.0)) {
               top = Math.max(top, terrain.surface(x, z));
            }
         }
      }

      return top;
   }

   private static boolean clearAround(ClientPlayerEntity player, ElytraFlight.Terrain terrain) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (terrain.surface(player.getX() + dx, player.getZ() + dz) > player.getY()) {
               return false;
            }
         }
      }

      return true;
   }

   private void newJitter() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      this.bandJitter = (float)random.nextDouble(-8.0, 8.0);
      this.glideJitter = (float)random.nextDouble(-1.0, 1.0);
   }

   private static boolean plainRocket(ItemStack stack) {
      if (!stack.isOf(Items.FIREWORK_ROCKET)) {
         return false;
      }

      FireworksComponent fireworks = stack.get(DataComponentTypes.FIREWORKS);
      return fireworks == null || fireworks.explosions().isEmpty();
   }

   private static boolean buyableRocket(ItemStack stack) {
      FireworksComponent fireworks = stack.get(DataComponentTypes.FIREWORKS);
      return plainRocket(stack) && fireworks != null && fireworks.flightDuration() == 3;
   }

   private static ItemStack chest(ClientPlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.CHEST);
   }

   private static int durability(ItemStack stack) {
      return stack.isOf(Items.ELYTRA) ? stack.getMaxDamage() - stack.getDamage() : -1;
   }

   private static boolean usable(ItemStack stack) {
      return durability(stack) > 1;
   }

   private static boolean mends(ItemStack stack) {
      return level(stack, Enchantments.MENDING) > 0;
   }

   private static int level(ItemStack stack, RegistryKey<Enchantment> enchantment) {
      for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)entry.getKey()).matchesKey(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   private static int mendThreshold(ItemStack chest) {
      int unbreaking = level(chest, Enchantments.UNBREAKING);
      int max = chest.getMaxDamage();
      if (unbreaking >= 3) {
         return Math.max(50, (int)(max * 0.15));
      } else {
         return unbreaking >= 1 ? Math.max(80, (int)(max * 0.2)) : Math.max(110, (int)(max * 0.27));
      }
   }

   private boolean canMend(ClientPlayerEntity player) {
      ItemStack chest = chest(player);
      return this.mend.get() && mends(chest) && this.mendFailures < 3 && (count(player, XP) > 0 || this.canBuy(Items.EXPERIENCE_BOTTLE));
   }

   private static int spare(ClientPlayerEntity player) {
      int worn = Math.max(0, durability(chest(player)));
      PlayerInventory inventory = player.getInventory();
      int best = -1;
      int bestLeft = worn + 40;

      for (int i = 0; i < 36; i++) {
         int left = durability(inventory.getStack(i));
         if (left > bestLeft) {
            best = i;
            bestLeft = left;
         }
      }

      return best;
   }

   private static int count(ClientPlayerEntity player, Predicate<ItemStack> want) {
      int total = want.test(player.getOffHandStack()) ? player.getOffHandStack().getCount() : 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getStack(i);
         if (want.test(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private static int hotbarSlot(ClientPlayerEntity player, Predicate<ItemStack> want) {
      PlayerInventory inventory = player.getInventory();
      if (want.test(inventory.getStack(inventory.getSelectedSlot()))) {
         return inventory.getSelectedSlot();
      }

      for (int i = 0; i < 9; i++) {
         if (want.test(inventory.getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   private static boolean inHotbar(ClientPlayerEntity player, Predicate<ItemStack> want) {
      return hotbarSlot(player, want) >= 0 || want.test(player.getOffHandStack());
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

   private void finish(boolean arrived, String reason) {
      ClientPlayerEntity player = this.mc.player;
      if (!arrived && player != null && player.isGliding() && this.job != ElytraAutopilotModule.Job.STOP) {
         this.job = ElytraAutopilotModule.Job.STOP;
         this.stopReason = reason;
         this.spot = null;
         this.spotSearches = 0;
         this.say(ModuleChat.body().warn("Landing to stop").text(" (" + reason + ")."));
         this.go(ElytraAutopilotModule.State.DESCEND);
      } else {
         long minutes = (System.currentTimeMillis() - this.startedAt) / 60000L;
         String summary = (arrived ? "Arrived" : "Stopped: " + reason)
            + " after "
            + Math.round(this.flown)
            + " blocks in "
            + minutes
            + " min, "
            + this.rocketsUsed
            + " rockets"
            + (this.pitStops > 0 ? ", " + this.pitStops + " pit stops" : "")
            + (this.spent > 0L ? ", $" + AhPurchase.money(this.spent) + " spent" : "")
            + ".";
         if (arrived) {
            this.say(ModuleChat.body().good(summary));
         } else {
            this.say(ModuleChat.body().warn(summary));
         }

         EasyPlace.LOG.info("[Elytra Autopilot] {}", summary);
         this.requestDisable();
         if (this.disconnect.get() && this.mc.getNetworkHandler() != null && !this.mc.isConnectedToLocalServer()) {
            AutoReconnectModule.holdOff();
            this.mc.getNetworkHandler().getConnection().disconnect(Text.literal("[Elytra Autopilot] " + summary));
         }
      }
   }

   private void showStatus(ClientPlayerEntity player) {
      double distance = this.distance(player);
      long eta = Math.round(distance / 27.0);

      String what = switch (this.state) {
         case PREPARE -> "getting ready";
         case TAKEOFF -> "taking off";
         case CLIMB -> "climbing";
         case CRUISE -> this.climbing ? "climbing" : "gliding";
         case DESCEND -> this.spiralling ? "circling down" : "gliding down";
         case LAND -> "landing";
         case SERVICE -> "pit stop";
      };
      ModuleChat.actionBar(
         this,
         ModuleChat.body()
            .text(
               String.format(
                  Locale.ROOT,
                  "%,d blocks, ~%d:%02d, %d rockets, elytra %d, %s",
                  Math.round(distance),
                  eta / 60L,
                  eta % 60L,
                  count(player, ROCKET),
                  Math.max(0, durability(chest(player))),
                  what
               )
            )
      );
   }

   private double distance(ClientPlayerEntity player) {
      return Math.hypot(player.getX() - this.destX, player.getZ() - this.destZ);
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   static double[] parseTarget(String raw) {
      if (raw == null) {
         return null;
      }

      String text = raw.toLowerCase(Locale.ROOT).replaceAll("[xyz]\\s*[:=]", " ").replaceAll("[,;/|()\\[\\]]", " ").trim();
      if (text.isEmpty()) {
         return null;
      }

      String[] parts = text.split("\\s+");
      if (parts.length >= 2 && parts.length <= 3) {
         double[] values = new double[parts.length];

         for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            double scale = 1.0;
            if (part.endsWith("k")) {
               scale = 1000.0;
               part = part.substring(0, part.length() - 1);
            }

            try {
               values[i] = Double.parseDouble(part) * scale;
            } catch (NumberFormatException e) {
               return null;
            }

            if (!Double.isFinite(values[i]) || Math.abs(values[i]) > 3.0E7) {
               return null;
            }
         }

         return parts.length == 2 ? new double[]{values[0], values[1]} : new double[]{values[0], values[2]};
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   private enum Job {
      ARRIVE,
      SERVICE,
      STOP;
   }

   @Environment(EnvType.CLIENT)
   private enum State {
      PREPARE,
      TAKEOFF,
      CLIMB,
      CRUISE,
      DESCEND,
      LAND,
      SERVICE;
   }

   @Environment(EnvType.CLIENT)
   private enum Task {
      NONE,
      EAT,
      SWAP,
      MEND,
      BUY_ROCKETS,
      BUY_XP;
   }
}
