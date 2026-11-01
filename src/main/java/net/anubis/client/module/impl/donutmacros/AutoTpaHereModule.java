package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.config.FriendStore;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.anubis.client.module.setting.StringListSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.CompletionChannel;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class AutoTpaHereModule extends ClientModule {
   private static final String ORDER_AZ = "A to Z";
   private static final String ORDER_ZA = "Z to A";
   private static final String ORDER_RANDOM = "Random";
   private static final String EXTRA_CHARS = "0123456789_";
   private static final String SUB_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_";
   private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9._]{1,32}");
   private static final Pattern FORMATTING = Pattern.compile("§.");
   private static final Pattern RATE_LIMIT = Pattern.compile("^you need to wait another ([\\d.]+) seconds? to execute a command");
   private static final Pattern NOT_ONLINE = Pattern.compile("(that|this) (user|player) is not online|player is not online");
   private static final Pattern SENT_REQUEST = Pattern.compile("^you sent (\\S+) (a )?teleport( here)? request");
   private static final int SUGGEST_CAP = 990;
   private static final long DETECT_TIMEOUT_MS = 5000L;
   private static final long LETTER_TIMEOUT_MS = 5000L;
   private static final long LETTER_TIMEOUT_LOCAL_MS = 3000L;
   private static final long PREFIX_TIMEOUT_MS = 4000L;
   private static final int SPLIT_MAX_INFLIGHT = 6;
   private static final long PROBE_WAIT_MS = 700L;
   private static final long PROBE_MIN_WAIT_MS = 100L;
   private static final long PROBE_STALE_MS = 4000L;
   private static final int PROBE_GIVE_UP = 25;
   private static final long REPLY_WINDOW_MS = 2500L;
   private static final long JOIN_REPEAT_MS = 3600000L;
   private static final long SWEEP_GAP_MS = 200L;
   private static final long SWEEP_MIN_ROUND_MS = 45000L;
   private static final int SWEEP_MAX_INFLIGHT = 4;
   private static final int JOIN_QUEUE_MAX = 300;
   private static final long RANDOM_RESCAN_MS = 180000L;
   private static final long RESPAWN_FIRST_MS = 700L;
   private static final long RESPAWN_RETRY_MS = 600L;
   private static final int RESPAWN_COMMAND_DELAY_TICKS = 20;
   private static final int EAT_MAX_TICKS = 40;
   private static final long PRUNE_EVERY_MS = 60000L;
   private static final int CHAT_QUEUE_MAX = 256;
   private static final long SELL_COMMAND_GAP_MS = 400L;
   private static final String[] SWORD_RANK = new String[]{"netherite", "diamond", "iron", "stone", "golden", "wooden"};
   private final ModeSetting command = this.addSetting("command", new ModeSetting("Command", "tpahere", "tpahere", "tpa"));
   private final ModeSetting order = this.addSetting("order", new ModeSetting("Order", "A to Z", "A to Z", "Z to A", "Random"));
   private final StringSetting letters = this.addSetting("letters", new StringSetting("Letters", "").hint("All letters, or e.g. abc or k-m"));
   private final StringSetting startLetter = this.addSetting("start-letter", new StringSetting("Start Letter", "").hint("First letter in the order"));
   private final BooleanSetting numbers = this.addSetting("numbers", new BooleanSetting("Include 0-9 and _", false));
   private final BooleanSetting bedrock = this.addSetting("bedrock", new BooleanSetting("Bedrock Players Only", false));
   private final BooleanSetting repeat = this.addSetting("repeat", new BooleanSetting("Repeat Forever", true));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (s)", 1.0F, 0.25F, 30.0F).step(0.25F));
   private final BooleanSetting skipOffline = this.addSetting("skip-offline", new BooleanSetting("Skip Offline Players", true));
   private final BooleanSetting joinersFirst = this.addSetting("joiners-first", new BooleanSetting("New Players First", false));
   private final StringListSetting neverRequest = this.addSetting("never-request", new StringListSetting("Never Request"));
   private final NumberSetting sendMinutes = this.addSetting("send-minutes", new NumberSetting("Send For (min, 0 = always)", 0.0F, 0.0F, 240.0F).step(1.0F));
   private final NumberSetting restMinutes = this.addSetting("rest-minutes", new NumberSetting("Then Rest (min)", 0.0F, 0.0F, 240.0F).step(1.0F));
   private final NumberSetting pauseRadius = this.addSetting("pause-radius", new NumberSetting("Pause Radius", 6.0F, 1.0F, 64.0F).step(0.5F));
   private final NumberSetting resumeAfter = this.addSetting("resume-after", new NumberSetting("Resume After (s)", 2.5F, 0.0F, 60.0F).step(0.5F));
   private final BooleanSetting attack = this.addSetting("attack", new BooleanSetting("Attack", true));
   private final NumberSetting attackRadius = this.addSetting("attack-radius", new NumberSetting("Attack Radius", 1.5F, 0.5F, 6.0F).step(0.1F));
   private final NumberSetting swordSlot = this.addSetting("sword-slot", new NumberSetting("Sword Slot (0 = auto)", 0.0F, 0.0F, 9.0F).step(1.0F));
   private final NumberSetting eatBelow = this.addSetting("eat-below", new NumberSetting("Eat Below (hearts, 0 = never)", 5.0F, 0.0F, 10.0F).step(0.5F));
   private final NumberSetting appleSlot = this.addSetting("apple-slot", new NumberSetting("Apple Slot (0 = auto)", 0.0F, 0.0F, 9.0F).step(1.0F));
   private final NumberSetting eatCooldown = this.addSetting("eat-cooldown", new NumberSetting("Eat Cooldown (s)", 2.5F, 0.5F, 30.0F).step(0.5F));
   private final BooleanSetting enchantedFirst = this.addSetting("enchanted-first", new BooleanSetting("Enchanted Apples First", false));
   private final BooleanSetting autoRespawn = this.addSetting("auto-respawn", new BooleanSetting("Auto Respawn", true));
   private final StringSetting respawnCommand = this.addSetting("respawn-command", new StringSetting("Respawn Command", "").hint("e.g. /home"));
   private final BooleanSetting autoSell = this.addSetting("auto-sell", new BooleanSetting("Auto Sell", true));
   private final StringListSetting neverSell = this.addSetting(
      "never-sell", new StringListSetting("Never Sell", "diamond_sword", "golden_apple", "totem_of_undying").itemPicker()
   );
   private final SellRoutine sell = new SellRoutine(this, this.neverSell, this::keepForBot, this::onSellCommand);
   private final TeleportConfirm teleportConfirm = new TeleportConfirm(this);
   private final CompletionRequests completions = new CompletionRequests();
   private final Queue<String> chatLines = new ConcurrentLinkedQueue<>();
   private boolean progressReset = true;
   private String letter = "";
   private final Set<String> sentThisLetter = new HashSet<>();
   private final Map<String, Long> msgAt = new HashMap<>();
   private final Map<String, Long> joinMsgAt = new HashMap<>();
   private final Set<String> splitLetters = new HashSet<>();
   private final ArrayDeque<String> retryQueue = new ArrayDeque<>();
   private AutoTpaHereModule.Phase phase = AutoTpaHereModule.Phase.DETECT;
   private int detectSlash = -1;
   private int detectBare = -1;
   private long detectUntil;
   private AutoTpaHereModule.Collector collector;
   private List<String> queue = List.of();
   private int queueIndex;
   private AutoTpaHereModule.Delivery delivery;
   private final Map<String, AutoTpaHereModule.Bucket> buckets = new HashMap<>();
   private String lastRandomLetter = "";
   private boolean sweepOn;
   private List<String> sweepLetters = List.of();
   private List<String> sweepList = List.of();
   private int sweepIndex;
   private final List<AutoTpaHereModule.Inflight> sweepInflight = new ArrayList<>();
   private long sweepLastFire;
   private int sweepRounds;
   private long sweepRoundAt;
   private final Map<String, Set<String>> prefixCache = new HashMap<>();
   private final ArrayDeque<String> joinQueue = new ArrayDeque<>();
   private final Set<String> joinQueued = new HashSet<>();
   private String selfName = "";
   private boolean onlineCheckOff;
   private int probeMisses;
   private long lastSendAt;
   private long rateHoldUntil;
   private String lastTarget = "";
   private long lastTargetAt;
   private boolean lastTargetCounted = true;
   private long lastPruneAt;
   private final List<AutoTpaHereModule.Near> nearList = new ArrayList<>();
   private long lastNearAt;
   private boolean wasHolding;
   private long cycleStartedAt;
   private boolean wasResting;
   private boolean eating;
   private int eatTicks;
   private boolean eatStarted;
   private float eatStartHealth;
   private long lastEatAt;
   private boolean warnedNoSword;
   private boolean warnedNoApple;
   private boolean wasDead;
   private long deadSince;
   private long respawnTriedAt;
   private int respawnCommandIn;
   private int totalSent;
   private int totalOffline;
   private int totalDisabled;
   private int totalJoiners;
   private int totalArrivals;
   private int totalHits;
   private int totalEaten;
   private int totalDeaths;

   public AutoTpaHereModule() {
      super(
         "auto-tpahere",
         "Tpahere Kill Bot",
         "Sends tpahere requests to online players one by one, pauses when someone arrives and fights them. Servers can detect this.",
         ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();
      this.letters.onChange(this::lettersChanged);
      this.startLetter.onChange(this::lettersChanged);
      this.order.onChange(this::lettersChanged);
      this.numbers.onChange(this::lettersChanged);
      this.bedrock.onChange(this::lettersChanged);
      this.startLetter.visibleWhen(() -> !this.order.is("Random"));
      this.restMinutes.visibleWhen(() -> this.sendMinutes.get() > 0.0F);
      this.attackRadius.visibleWhen(this.attack::get);
      this.swordSlot.visibleWhen(this.attack::get);
      Setting[] eatSettings = new Setting[]{this.appleSlot, this.eatCooldown, this.enchantedFirst};

      for (Setting setting : eatSettings) {
         setting.visibleWhen(() -> this.eatBelow.get() > 0.0F);
      }

      this.respawnCommand.visibleWhen(this.autoRespawn::get);
      this.neverSell.visibleWhen(this.autoSell::get);
      this.autoSell.onChange(() -> {
         if (!this.autoSell.get()) {
            this.sell.abort(MinecraftClient.getInstance());
         }
      });
      this.neverRequest.privateValue();
      this.respawnCommand.privateValue();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.resetRun();
      if (this.progressReset || !this.letterList().contains(this.letter)) {
         this.letter = this.firstLetter();
         this.sentThisLetter.clear();
         this.progressReset = false;
      }

      this.info(
         ModuleChat.body()
            .text("Started ")
            .value("/" + this.command.get())
            .text(", ")
            .value(this.orderLabel())
            .text(this.order.is("Random") ? "" : " from " + this.letter.toUpperCase(Locale.ROOT))
            .text(", every ")
            .value(formatSeconds(this.delayMs()))
            .text(this.attack.get() ? ", attacking" : "")
      );
   }

   @Override
   protected void onDisable() {
      this.sell.abort(MinecraftClient.getInstance());
      this.stopEating(MinecraftClient.getInstance());
      this.completions.clear();
      this.chatLines.clear();
      this.delivery = null;
      this.collector = null;
      if (this.totalSent > 0 || this.totalArrivals > 0) {
         this.info(ModuleChat.body().text(this.statsText()));
      }
   }

   private void lettersChanged() {
      this.progressReset = true;
      if (this.isEnabled()) {
         this.resetRun();
         this.letter = this.firstLetter();
         this.sentThisLetter.clear();
         this.progressReset = false;
      }
   }

   private void resetRun() {
      long now = System.currentTimeMillis();
      this.completions.clear();
      this.chatLines.clear();
      this.msgAt.clear();
      this.joinMsgAt.clear();
      this.splitLetters.clear();
      this.retryQueue.clear();
      this.phase = AutoTpaHereModule.Phase.DETECT;
      this.detectSlash = -1;
      this.detectBare = -1;
      this.collector = null;
      this.queue = List.of();
      this.queueIndex = 0;
      this.delivery = null;
      this.buckets.clear();
      this.lastRandomLetter = "";
      this.sweepOn = false;
      this.sweepInflight.clear();
      this.sweepList = List.of();
      this.sweepIndex = 0;
      this.sweepRounds = 0;
      this.prefixCache.clear();
      this.joinQueue.clear();
      this.joinQueued.clear();
      MinecraftClient mc = MinecraftClient.getInstance();
      this.selfName = mc.getSession() == null ? "" : normalizeIgn(mc.getSession().getUsername());
      this.onlineCheckOff = false;
      this.probeMisses = 0;
      this.lastSendAt = 0L;
      this.rateHoldUntil = 0L;
      this.lastTarget = "";
      this.lastTargetCounted = true;
      this.lastPruneAt = now;
      this.nearList.clear();
      this.lastNearAt = 0L;
      this.wasHolding = false;
      this.cycleStartedAt = now;
      this.wasResting = false;
      this.eating = false;
      this.lastEatAt = 0L;
      this.warnedNoSword = false;
      this.warnedNoApple = false;
      this.wasDead = false;
      this.respawnCommandIn = 0;
      this.sell.reset();
      this.teleportConfirm.reset();
      this.totalSent = this.totalOffline = this.totalDisabled = this.totalJoiners = 0;
      this.totalArrivals = this.totalHits = this.totalEaten = this.totalDeaths = 0;
   }

   @EventHandler
   public void onPacket(PacketReceiveNettyEvent event) {
      if (event.packet instanceof GameMessageS2CPacket chat && chat.content() != null && this.chatLines.size() < 256) {
         this.chatLines.offer(chat.content().getString());
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (player != null && mc.world != null && connection != null && mc.interactionManager != null) {
         this.teleportConfirm.tick(mc);
         long now = System.currentTimeMillis();
         this.drainChat(now);
         if (now - this.lastPruneAt > 60000L) {
            this.prune(now);
         }

         if (this.handleDeath(mc, player, now)) {
            this.sell.abort(mc);
            this.stall();
         } else {
            this.tickRespawnCommand(connection);
            this.scanNear(mc, player);
            if (this.sell.active() && (!this.nearList.isEmpty() || this.wantsToEat(player, now))) {
               this.sell.abort(mc);
            }

            if (!this.tickEating(mc, player, now) && !this.startEating(mc, player, now)) {
               this.fight(mc, player);
               this.pumpSweep(now);
               if (this.holding(now)) {
                  this.stall();
               } else if (this.autoSell.get() && this.sell.step(mc, player, connection, now, now >= this.rateHoldUntil && now - this.lastSendAt >= 400L)) {
                  this.stall();
               } else if (!this.resting(now) && now >= this.rateHoldUntil) {
                  this.step(connection, now);
               }
            } else {
               this.stall();
            }
         }
      } else {
         this.sell.abort(mc);
         this.stopEating(mc);
         this.stall();
      }
   }

   private void stall() {
      if (this.delivery != null) {
         this.delivery.stale = true;
      }
   }

   private void step(ClientPlayNetworkHandler connection, long now) {
      switch (this.phase) {
         case DETECT:
            this.stepDetect(now);
            break;
         case COLLECT:
            this.stepCollect(connection, now);
            break;
         case SEND:
            this.stepSend(connection, now);
            break;
         case PICK:
            this.stepPick(connection, now);
      }
   }

   private void stepDetect(long now) {
      String first = this.order.is("Random") ? "a" : this.letter;
      if (this.detectSlash < 0 && this.detectBare < 0) {
         this.detectSlash = this.completions.fire(this.partialFor(first), CompletionChannel.Form.SLASH);
         this.detectBare = this.completions.fire(this.partialFor(first), CompletionChannel.Form.BARE);
         this.detectUntil = now + 5000L;
      } else {
         if (this.completions.poll(this.detectSlash) != null) {
            this.completions.form(CompletionChannel.Form.SLASH);
         } else if (this.completions.poll(this.detectBare) != null) {
            this.completions.form(CompletionChannel.Form.BARE);
         } else if (now < this.detectUntil) {
            return;
         }

         this.completions.forget(this.detectSlash);
         this.completions.forget(this.detectBare);
         if (this.joinersFirst.get()) {
            this.startSweep();
         }

         if (this.order.is("Random")) {
            this.info(ModuleChat.body().text("Random order, letters ").value(String.join("", this.letterList()).toUpperCase(Locale.ROOT)));
            this.phase = AutoTpaHereModule.Phase.PICK;
         } else {
            this.beginLetter();
         }
      }
   }

   private void beginLetter() {
      this.collector = new AutoTpaHereModule.Collector(this.letter);
      this.phase = AutoTpaHereModule.Phase.COLLECT;
   }

   private void stepCollect(ClientPlayNetworkHandler connection, long now) {
      if (this.collector.step(connection, now)) {
         AutoTpaHereModule.Collector done = this.collector;
         this.collector = null;
         if (this.order.is("Random")) {
            AutoTpaHereModule.Bucket bucket = this.bucket(done.letter);
            bucket.scanned = true;
            bucket.at = now;
            bucket.names = done.names;
            bucket.unused = this.unusedFromNames(done.names);
            if (done.names.isEmpty()) {
               this.info(ModuleChat.body().text(done.timedOut ? "No answer for " : "No names for ").value(done.letter.toUpperCase(Locale.ROOT)));
            }

            this.phase = AutoTpaHereModule.Phase.PICK;
         } else {
            this.queue = this.unusedFromNames(done.names);
            this.queueIndex = 0;
            if (this.queue.isEmpty()) {
               this.info(
                  ModuleChat.body()
                     .text(done.timedOut ? "No answer for " : "No new names for ")
                     .value(done.letter.toUpperCase(Locale.ROOT))
                     .text(done.timedOut ? ", next letter" : "")
               );
            } else {
               this.info(
                  ModuleChat.body()
                     .text(isLetter(done.letter) ? "Letter " : "Prefix ")
                     .value(done.letter.toUpperCase(Locale.ROOT))
                     .text(": ")
                     .value(this.queue.size())
                     .text(this.queue.size() == 1 ? " player" : " players")
                     .text(done.split ? " (past the 1000 cap)" : "")
               );
            }

            this.phase = AutoTpaHereModule.Phase.SEND;
         }
      }
   }

   private void stepSend(ClientPlayNetworkHandler connection, long now) {
      if (this.delivery == null) {
         this.delivery = this.nextPriority();
      }

      while (this.delivery == null && this.queueIndex < this.queue.size()) {
         String name = this.queue.get(this.queueIndex++);
         String key = key(name);
         if (!this.sentThisLetter.contains(key) && !this.recentlyJoinMessaged(key, now) && !this.excluded(name)) {
            this.delivery = new AutoTpaHereModule.Delivery(name, false);
         }
      }

      if (this.delivery == null) {
         this.advanceLetter();
      } else {
         if (this.delivery.step(connection, now) != AutoTpaHereModule.Result.WAIT) {
            this.delivery = null;
         }
      }
   }

   private void stepPick(ClientPlayNetworkHandler connection, long now) {
      if (this.delivery == null) {
         this.delivery = this.nextPriority();
      }

      if (this.delivery == null) {
         List<String> pool = this.letterList();

         for (int tries = 0; tries < pool.size() + 4 && this.delivery == null; tries++) {
            String picked = this.pickRandomLetter(pool, now);
            if (picked == null) {
               if (!this.repeat.get()) {
                  this.finish("Finished the random pass.");
                  return;
               }

               this.reshuffle(pool);
               picked = this.pickRandomLetter(pool, now);
               if (picked == null) {
                  this.finish("No names found for these letters.");
                  return;
               }

               this.info(ModuleChat.body().text("Random pass done, starting over."));
            }

            AutoTpaHereModule.Bucket bucket = this.bucket(picked);
            if (!bucket.scanned || stale(bucket, now)) {
               this.collector = new AutoTpaHereModule.Collector(picked);
               this.phase = AutoTpaHereModule.Phase.COLLECT;
               return;
            }

            if (!bucket.unused.isEmpty()) {
               List<String> unused = bucket.unused;
               int index = ThreadLocalRandom.current().nextInt(unused.size());
               String name = unused.get(index);
               unused.set(index, unused.get(unused.size() - 1));
               unused.remove(unused.size() - 1);
               this.lastRandomLetter = picked;
               if (!this.excluded(name) && !this.sentThisLetter.contains(key(name))) {
                  this.delivery = new AutoTpaHereModule.Delivery(name, false);
               }
            }
         }

         if (this.delivery == null) {
            return;
         }
      }

      if (this.delivery.step(connection, now) != AutoTpaHereModule.Result.WAIT) {
         this.delivery = null;
      }
   }

   private String pickRandomLetter(List<String> pool, long now) {
      List<String> eligible = new ArrayList<>();

      for (String candidate : pool) {
         AutoTpaHereModule.Bucket bucket = this.buckets.get(candidate);
         if (bucket == null || !bucket.scanned || !bucket.unused.isEmpty() || this.repeat.get() && stale(bucket, now)) {
            eligible.add(candidate);
         }
      }

      if (eligible.size() > 1 && !this.lastRandomLetter.isEmpty()) {
         List<String> mixed = new ArrayList<>(eligible);
         mixed.remove(this.lastRandomLetter);
         if (!mixed.isEmpty()) {
            eligible = mixed;
         }
      }

      return eligible.isEmpty() ? null : eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
   }

   private void reshuffle(List<String> pool) {
      this.sentThisLetter.clear();

      for (String candidate : pool) {
         AutoTpaHereModule.Bucket bucket = this.buckets.get(candidate);
         if (bucket != null && bucket.scanned) {
            bucket.unused = this.unusedFromNames(bucket.names);
         }
      }
   }

   private AutoTpaHereModule.Bucket bucket(String key) {
      return this.buckets.computeIfAbsent(key, k -> new AutoTpaHereModule.Bucket());
   }

   private static boolean stale(AutoTpaHereModule.Bucket bucket, long now) {
      return now - bucket.at > 180000L;
   }

   private void advanceLetter() {
      List<String> list = this.letterList();
      int index = list.indexOf(this.letter);
      boolean wrapped = index >= 0 && index >= list.size() - 1;
      this.letter = index >= 0 && !wrapped ? list.get(index + 1) : list.get(0);
      this.sentThisLetter.clear();
      if (wrapped && !this.repeat.get()) {
         this.progressReset = true;
         this.finish("Finished " + this.orderLabel() + ".");
      } else {
         this.beginLetter();
      }
   }

   private void finish(String message) {
      this.info(ModuleChat.body().text(message));
      this.requestDisable();
   }

   private AutoTpaHereModule.Delivery nextPriority() {
      while (!this.retryQueue.isEmpty()) {
         String name = this.retryQueue.poll();
         if (!this.excluded(name)) {
            return new AutoTpaHereModule.Delivery(name, false);
         }
      }

      if (!this.sweepOn) {
         return null;
      }

      long now = System.currentTimeMillis();

      while (!this.joinQueue.isEmpty()) {
         String name = this.joinQueue.poll();
         this.joinQueued.remove(key(name));
         if (this.joinEligible(name, now)) {
            return new AutoTpaHereModule.Delivery(name, true);
         }
      }

      return null;
   }

   private void send(ClientPlayNetworkHandler connection, String name, boolean joiner, long now) {
      this.lastTarget = name;
      this.lastTargetAt = now;
      this.lastTargetCounted = false;
      connection.sendChatCommand(this.command.get() + " " + name);
      this.teleportConfirm.sent();
      this.lastSendAt = now;
      this.totalSent++;
      if (joiner) {
         this.totalJoiners++;
      }

      String key = key(name);
      this.msgAt.put(key, now);
      if (joiner) {
         this.joinMsgAt.put(key, now);
      }

      this.sentThisLetter.add(key);
   }

   private Boolean probeAnswer(List<String> answer, String name) {
      if (answer == null) {
         return null;
      }

      for (String raw : answer) {
         if (sameIgn(this.stripCommand(raw), name)) {
            return Boolean.TRUE;
         }
      }

      return Boolean.FALSE;
   }

   private void noteProbe(Boolean online) {
      if (!Boolean.FALSE.equals(online)) {
         this.probeMisses = 0;
      } else {
         if (++this.probeMisses >= 25 && !this.onlineCheckOff) {
            this.onlineCheckOff = true;
            this.info(ModuleChat.body().warn("Online check off").text(": 25 players in a row looked offline, the server may not confirm names."));
         }
      }
   }

   private List<String> localNames(ClientPlayNetworkHandler connection, String prefix) {
      List<String> out = new ArrayList<>();

      for (PlayerListEntry info : connection.getPlayerList()) {
         String name = info.getProfile() == null ? null : info.getProfile().name();
         if (name != null && validName(name) && this.matchesLetter(name, prefix)) {
            out.add(name);
         }
      }

      return out;
   }

   private void startSweep() {
      this.sweepLetters = this.letterList();
      this.sweepList = List.of();
      this.sweepIndex = 0;
      this.sweepRounds = 0;
      this.sweepInflight.clear();
      this.sweepOn = !this.sweepLetters.isEmpty();
   }

   private void pumpSweep(long now) {
      if (this.sweepOn) {
         this.sweepInflight.removeIf(request -> {
            List<String> answer = this.completions.poll(request.id());
            if (answer != null) {
               this.sweepResult(request.prefix(), answer, now);
            } else if (now - request.at() <= 4000L) {
               return false;
            }

            this.completions.forget(request.id());
            return true;
         });
         if (this.sweepInflight.size() < 4 && now - this.sweepLastFire >= 200L) {
            if (this.sweepIndex >= this.sweepList.size()) {
               if (this.sweepRounds > 0 && now - this.sweepRoundAt < 45000L) {
                  return;
               }

               List<String> list = new ArrayList<>();

               for (String l : this.sweepLetters) {
                  if (this.splitLetters.contains(l)) {
                     list.addAll(subPrefixes(l));
                  } else {
                     list.add(l);
                  }
               }

               this.sweepList = list;
               this.sweepIndex = 0;
               this.sweepRounds++;
               this.sweepRoundAt = now;
            }

            if (!this.sweepList.isEmpty()) {
               String prefix = this.sweepList.get(this.sweepIndex++);
               int id = this.completions.fire(this.partialFor(prefix));
               this.sweepLastFire = now;
               if (id >= 0) {
                  this.sweepInflight.add(new AutoTpaHereModule.Inflight(id, prefix, now));
               }
            }
         }
      }
   }

   private void sweepResult(String prefix, List<String> answer, long now) {
      String first = prefix.substring(0, 1);
      if (answer.size() >= 990) {
         if (prefix.length() == 1 && this.splitLetters.add(first)) {
            this.prefixCache.remove(prefix);
         }
      } else {
         Map<String, String> current = new HashMap<>();

         for (String name : this.namesForPrefix(answer, prefix)) {
            String clean = cleanName(this.stripCommand(name));
            if (validName(clean)) {
               current.put(key(clean), clean);
            }
         }

         Set<String> previous = this.prefixCache.get(prefix);
         if (previous == null || previous.isEmpty() || !current.isEmpty()) {
            this.prefixCache.put(prefix, new HashSet<>(current.keySet()));
            if (previous != null) {
               List<String> fresh = new ArrayList<>();

               for (Entry<String, String> entry : current.entrySet()) {
                  if (!previous.contains(entry.getKey())) {
                     fresh.add(entry.getValue());
                  }
               }

               if (fresh.size() <= 15 || !(fresh.size() > previous.size() * 0.3)) {
                  for (String name : fresh) {
                     this.queueJoiner(name, now);
                  }
               }
            }
         }
      }
   }

   private void queueJoiner(String name, long now) {
      String key = key(name);
      if (!key.isEmpty() && !this.joinQueued.contains(key) && this.joinEligible(name, now)) {
         if (this.joinQueue.size() >= 300) {
            this.joinQueued.remove(key(this.joinQueue.poll()));
         }

         this.joinQueue.add(name);
         this.joinQueued.add(key);
      }
   }

   private boolean joinEligible(String name, long now) {
      String key = key(name);
      if (!key.isEmpty() && !this.excluded(name) && !this.sentThisLetter.contains(key)) {
         Long at = this.msgAt.get(key);
         return at == null || now - at >= 3600000L;
      } else {
         return false;
      }
   }

   private boolean recentlyJoinMessaged(String key, long now) {
      Long at = this.joinMsgAt.get(key);
      return at != null && now - at < 3600000L;
   }

   private List<String> unusedFromNames(List<String> names) {
      long now = System.currentTimeMillis();
      List<String> unused = new ArrayList<>();

      for (String name : names) {
         String key = key(name);
         if (!key.isEmpty() && !this.excluded(name) && !this.sentThisLetter.contains(key) && !this.recentlyJoinMessaged(key, now)) {
            unused.add(name);
         }
      }

      return unused;
   }

   private void drainChat(long now) {
      String line;
      while ((line = this.chatLines.poll()) != null) {
         String low = FORMATTING.matcher(line).replaceAll("").trim().toLowerCase(Locale.ROOT);
         if (!low.isEmpty()) {
            if (low.contains("disabled tpahere")) {
               if (this.claimReply(now)) {
                  this.totalDisabled++;
               }
            } else if (NOT_ONLINE.matcher(low).find()) {
               if (this.claimReply(now)) {
                  this.totalOffline++;
               }
            } else {
               Matcher wait = RATE_LIMIT.matcher(low);
               if (wait.find()) {
                  try {
                     this.rateHoldUntil = now + (long)Math.ceil(Math.min(30.0, Double.parseDouble(wait.group(1))) * 1000.0) + 80L;
                  } catch (NumberFormatException var7) {
                  }

                  this.sell.commandRejected(this.rateHoldUntil);
                  if (this.claimReply(now)) {
                     this.totalSent = Math.max(0, this.totalSent - 1);
                     if (!this.retryQueue.contains(this.lastTarget)) {
                        this.retryQueue.add(this.lastTarget);
                     }
                  }
               } else {
                  Matcher sent = SENT_REQUEST.matcher(low);
                  if (sent.find() && sameIgn(this.lastTarget, sent.group(1))) {
                     this.lastTargetCounted = true;
                  }
               }
            }
         }
      }
   }

   private boolean claimReply(long now) {
      if (!this.lastTarget.isEmpty() && !this.lastTargetCounted && now - this.lastTargetAt <= 2500L) {
         this.lastTargetCounted = true;
         return true;
      } else {
         return false;
      }
   }

   private void prune(long now) {
      this.lastPruneAt = now;
      this.msgAt.values().removeIf(at -> now - at > 3600000L);
      this.joinMsgAt.values().removeIf(at -> now - at > 3600000L);
      this.completions.prune(now);
   }

   private boolean handleDeath(MinecraftClient mc, ClientPlayerEntity player, long now) {
      if (player.isDead()) {
         if (!this.wasDead) {
            this.wasDead = true;
            this.deadSince = now;
            this.respawnTriedAt = 0L;
            this.totalDeaths++;
            this.stopEating(mc);
            this.info(ModuleChat.body().danger("You died."));
         }

         if (this.autoRespawn.get() && now - this.deadSince >= 700L && now - this.respawnTriedAt >= 600L) {
            this.respawnTriedAt = now;
            player.requestRespawn();
         }

         return true;
      } else {
         if (this.wasDead) {
            this.wasDead = false;
            this.nearList.clear();
            this.lastNearAt = 0L;
            if (mc.currentScreen instanceof DeathScreen) {
               mc.setScreen(null);
            }

            this.info(ModuleChat.body().good("Respawned."));
            if (!this.respawnCommand.get().isBlank()) {
               this.respawnCommandIn = 20;
            }
         }

         return false;
      }
   }

   private void tickRespawnCommand(ClientPlayNetworkHandler connection) {
      if (this.respawnCommandIn > 0 && --this.respawnCommandIn <= 0) {
         String text = this.respawnCommand.get().trim();

         while (text.startsWith("/")) {
            text = text.substring(1);
         }

         if (!text.isEmpty()) {
            connection.sendChatCommand(text);
         }
      }
   }

   private void scanNear(MinecraftClient mc, ClientPlayerEntity player) {
      this.nearList.clear();
      double radius = this.pauseRadius.get();

      for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
         if (other != player && !other.getUuid().equals(player.getUuid())) {
            String name = other.getGameProfile().name();
            if (name != null && !this.isSelf(name) && !isFriend(name)) {
               double distance = player.getEntityPos().distanceTo(other.getEntityPos());
               if (distance <= radius) {
                  this.nearList.add(new AutoTpaHereModule.Near(other, name, distance));
               }
            }
         }
      }

      this.nearList.sort((a, b) -> Double.compare(a.distance(), b.distance()));
   }

   private boolean holding(long now) {
      if (!this.nearList.isEmpty()) {
         this.lastNearAt = now;
      }

      boolean holding = !this.nearList.isEmpty() || this.lastNearAt > 0L && now - this.lastNearAt < (long)(this.resumeAfter.get() * 1000.0F);
      if (holding && !this.wasHolding && !this.nearList.isEmpty()) {
         AutoTpaHereModule.Near first = this.nearList.get(0);
         this.totalArrivals++;
         this.info(
            ModuleChat.body()
               .name(first.name())
               .text(" is here (")
               .value(String.format(Locale.ROOT, "%.1f", first.distance()))
               .text(" blocks). Requests paused.")
         );
      } else if (!holding && this.wasHolding) {
         this.info(ModuleChat.body().text("Alone again, resuming."));
      }

      this.wasHolding = holding;
      return holding;
   }

   private boolean resting(long now) {
      long onMs = (long)(this.sendMinutes.get() * 60000.0F);
      long offMs = (long)(this.restMinutes.get() * 60000.0F);
      boolean resting = onMs > 0L && offMs > 0L && (now - this.cycleStartedAt) % (onMs + offMs) >= onMs;
      if (resting != this.wasResting) {
         this.wasResting = resting;
         this.info(
            resting ? ModuleChat.body().text("Resting for ").value(this.restMinutes.getInt() + " min") : ModuleChat.body().text("Rest over, sending again.")
         );
      }

      return resting;
   }

   private void fight(MinecraftClient mc, ClientPlayerEntity player) {
      if (this.attack.get() && !this.eating && canFight(mc, player)) {
         AbstractClientPlayerEntity target = null;

         for (AutoTpaHereModule.Near near : this.nearList) {
            if (near.distance() > this.attackRadius.get()) {
               break;
            }

            if (MeleeAttacks.isEnemy(player, near.player())) {
               target = near.player();
               break;
            }
         }

         if (target != null) {
            if (!this.equipSword(player)) {
               Vec3d eye = player.getEyePos();
               Box box = target.getBoundingBox();
               Rotation aim = RotationUtils.fromTo(eye, box.getCenter());
               if (MeleeAttacks.charge(player) < 1.0F) {
                  if (MeleeAttacks.ticksToFull(player) <= 1) {
                     ServerRotation.request(aim);
                  }
               } else {
                  Rotation look = ServerRotation.sent();
                  if (look == null) {
                     look = RotationUtils.entityRotation(player);
                  }

                  Vec3d at = hitPoint(player, eye, box, look);
                  if (at == null) {
                     ServerRotation.request(aim);
                  } else {
                     if (MeleeAttacks.attack(mc, new EntityHitResult(target, at))) {
                        MeleeAttacks.claimTick(mc);
                        this.totalHits++;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean canFight(MinecraftClient mc, ClientPlayerEntity player) {
      return mc.world != null
         && mc.interactionManager != null
         && !player.isSpectator()
         && player.currentScreenHandler == player.playerScreenHandler
         && !AutoEatModule.isEating();
   }

   private static Vec3d hitPoint(ClientPlayerEntity player, Vec3d eye, Box box, Rotation look) {
      double reach = player.getEntityInteractionRange();
      Vec3d direction = Vec3d.fromPolar(look.pitch(), look.yaw());
      Vec3d at = box.raycast(eye, eye.add(direction.multiply(reach))).orElse(box.contains(eye) ? eye : null);
      return at != null && eye.distanceTo(at) <= reach && MeleeAttacks.lineIsClear(player, eye, at) ? at : null;
   }

   private boolean equipSword(ClientPlayerEntity player) {
      PlayerInventory inventory = player.getInventory();
      int slot = this.findSword(inventory);
      if (slot < 0) {
         if (!this.warnedNoSword) {
            this.warnedNoSword = true;
            this.info(ModuleChat.body().warn("No sword in the hotbar").text(", attacking with the held item."));
         }

         return false;
      } else {
         if (inventory.getSelectedSlot() == slot) {
            return false;
         }

         inventory.setSelectedSlot(slot);
         return true;
      }
   }

   private int findSword(PlayerInventory inventory) {
      if (this.swordSlot.getInt() >= 1) {
         return this.swordSlot.getInt() - 1;
      }

      int best = -1;
      int bestRank = Integer.MAX_VALUE;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         String path = itemPath(inventory.getStack(slot));
         if (path.endsWith("_sword")) {
            int rank = SWORD_RANK.length;

            for (int i = 0; i < SWORD_RANK.length; i++) {
               if (path.startsWith(SWORD_RANK[i])) {
                  rank = i;
                  break;
               }
            }

            if (rank < bestRank) {
               bestRank = rank;
               best = slot;
            }
         }
      }

      return best;
   }

   private int findApple(PlayerInventory inventory) {
      int chosen = this.appleSlot.getInt() - 1;
      if (chosen >= 0 && chosen < PlayerInventory.getHotbarSize()) {
         ItemStack stack = inventory.getStack(chosen);
         if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            return chosen;
         }
      }

      int normal = -1;
      int enchanted = -1;

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) && enchanted < 0) {
            enchanted = slot;
         } else if (stack.isOf(Items.GOLDEN_APPLE) && normal < 0) {
            normal = slot;
         }
      }

      if (this.enchantedFirst.get()) {
         return enchanted >= 0 ? enchanted : normal;
      } else {
         return normal >= 0 ? normal : enchanted;
      }
   }

   private static String itemPath(ItemStack stack) {
      return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).getPath();
   }

   private boolean wantsToEat(ClientPlayerEntity player, long now) {
      float health = player.getHealth();
      return this.eatBelow.get() > 0.0F
         && health > 0.0F
         && health < this.eatBelow.get() * 2.0F
         && now - this.lastEatAt >= (long)(this.eatCooldown.get() * 1000.0F)
         && this.findApple(player.getInventory()) >= 0;
   }

   private boolean keepForBot(int index, ItemStack stack) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return this.attack.get() && player != null && index == this.findSword(player.getInventory())
         ? true
         : this.eatBelow.get() > 0.0F && (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
   }

   private void onSellCommand() {
      this.lastSendAt = System.currentTimeMillis();
      this.lastTargetCounted = true;
   }

   private boolean startEating(MinecraftClient mc, ClientPlayerEntity player, long now) {
      if (!this.eating && !(this.eatBelow.get() <= 0.0F) && mc.currentScreen == null) {
         float health = player.getHealth();
         if (!(health <= 0.0F) && !(health >= this.eatBelow.get() * 2.0F) && now - this.lastEatAt >= (long)(this.eatCooldown.get() * 1000.0F)) {
            this.lastEatAt = now;
            int slot = this.findApple(player.getInventory());
            if (slot < 0) {
               if (!this.warnedNoApple) {
                  this.warnedNoApple = true;
                  this.info(ModuleChat.body().warn("Low health but no golden apple in the hotbar."));
               }

               return false;
            } else {
               player.getInventory().setSelectedSlot(slot);
               this.eating = true;
               this.eatTicks = 0;
               this.eatStarted = false;
               this.eatStartHealth = health;
               mc.options.useKey.setPressed(true);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean tickEating(MinecraftClient mc, ClientPlayerEntity player, long now) {
      if (!this.eating) {
         return false;
      }

      this.eatTicks++;
      if (player.isUsingItem()) {
         this.eatStarted = true;
         if (this.eatTicks < 40) {
            mc.options.useKey.setPressed(true);
            return true;
         }
      } else if (!this.eatStarted && this.eatTicks < 40) {
         mc.options.useKey.setPressed(true);
         return true;
      }

      this.stopEating(mc);
      this.lastEatAt = now;
      if (this.eatStarted) {
         this.totalEaten++;
         this.info(
            ModuleChat.body()
               .text("Ate a golden apple (")
               .value(String.format(Locale.ROOT, "%.1f", this.eatStartHealth / 2.0F))
               .text(" -> ")
               .value(String.format(Locale.ROOT, "%.1f", player.getHealth() / 2.0F))
               .text(" hearts).")
         );
      }

      if (this.attack.get()) {
         this.equipSword(player);
      }

      return false;
   }

   private void stopEating(MinecraftClient mc) {
      if (this.eating) {
         this.eating = false;
         mc.options.useKey.setPressed(Crystals.isPhysicallyDown(mc, mc.options.useKey));
      }
   }

   private List<String> letterList() {
      List<String> parsed = parseLetters(this.letters.get());
      if (this.order.is("Z to A")) {
         Collections.reverse(parsed);
      }

      if (!this.numbers.get()) {
         return parsed;
      }

      List<String> out = new ArrayList<>();

      for (int i = 0; i < "0123456789_".length(); i++) {
         out.add(String.valueOf("0123456789_".charAt(i)));
      }

      out.addAll(parsed);
      return out;
   }

   static List<String> parseLetters(String raw) {
      String spec = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
      Set<String> out = new LinkedHashSet<>();

      for (String part : spec.split(",")) {
         if (part.length() == 3 && part.charAt(1) == '-' && isAz(part.charAt(0)) && isAz(part.charAt(2))) {
            char from = part.charAt(0);
            char to = part.charAt(2);
            int step = from <= to ? 1 : -1;

            for (char c = from; c != to + step; c = (char)(c + step)) {
               out.add(String.valueOf(c));
            }
         } else {
            for (int i = 0; i < part.length(); i++) {
               if (isAz(part.charAt(i))) {
                  out.add(String.valueOf(part.charAt(i)));
               }
            }
         }
      }

      if (out.isEmpty()) {
         for (char c = 'a'; c <= 'z'; c++) {
            out.add(String.valueOf(c));
         }
      }

      return new ArrayList<>(out);
   }

   private static boolean isAz(char c) {
      return c >= 'a' && c <= 'z';
   }

   private String firstLetter() {
      List<String> list = this.letterList();
      String wanted = this.startLetter.get().trim().toLowerCase(Locale.ROOT);
      if (!wanted.isEmpty()) {
         String first = wanted.substring(0, 1);
         if (list.contains(first)) {
            return first;
         }
      }

      return list.get(0);
   }

   private static List<String> subPrefixes(String letter) {
      List<String> out = new ArrayList<>("abcdefghijklmnopqrstuvwxyz0123456789_".length());

      for (int i = 0; i < "abcdefghijklmnopqrstuvwxyz0123456789_".length(); i++) {
         out.add(letter + "abcdefghijklmnopqrstuvwxyz0123456789_".charAt(i));
      }

      return out;
   }

   private String partialFor(String prefix) {
      return this.command.get() + " " + (this.bedrock.get() ? "." : "") + prefix;
   }

   private List<String> namesForPrefix(List<String> answer, String prefix) {
      String wanted = stripDots(prefix).toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();

      for (String raw : answer) {
         String name = cleanName(this.stripCommand(raw));
         if (validName(name) && stripDots(name).toLowerCase(Locale.ROOT).startsWith(wanted)) {
            out.add(name);
         }
      }

      return out;
   }

   private boolean matchesLetter(String name, String prefix) {
      String core = this.bedrock.get() ? stripDots(name) : name;
      return this.bedrock.get() && core.length() == name.length()
         ? false
         : !core.isEmpty() && core.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
   }

   private String stripCommand(String text) {
      String t = text == null ? "" : text.trim();
      String low = t.toLowerCase(Locale.ROOT);
      String cmd = this.command.get();

      for (String prefix : new String[]{"/" + cmd + " ", cmd + " ", "/tpahere ", "tpahere ", "/tpa ", "tpa "}) {
         if (low.startsWith(prefix)) {
            t = t.substring(prefix.length()).trim();
            break;
         }
      }

      int space = t.indexOf(32);
      if (space > 0) {
         t = t.substring(0, space);
      }

      return t.replaceAll("^[\"']|[\"']$", "");
   }

   private static String cleanName(String name) {
      return FORMATTING.matcher(name == null ? "" : name).replaceAll("").trim();
   }

   private static boolean validName(String name) {
      return name != null && VALID_NAME.matcher(name).matches() && !stripDots(name).isEmpty();
   }

   private static boolean isLetter(String prefix) {
      return !prefix.isEmpty() && isAz(prefix.charAt(0));
   }

   private static String stripDots(String name) {
      int i = 0;

      while (i < name.length() && name.charAt(i) == '.') {
         i++;
      }

      return name.substring(i);
   }

   private static String normalizeIgn(String name) {
      return name == null ? "" : stripDots(cleanName(name)).toLowerCase(Locale.ROOT);
   }

   private static String key(String name) {
      return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
   }

   private static boolean sameIgn(String a, String b) {
      String x = normalizeIgn(a);
      return !x.isEmpty() && x.equals(normalizeIgn(b));
   }

   private boolean isSelf(String name) {
      return !this.selfName.isEmpty() && this.selfName.equals(normalizeIgn(name));
   }

   private static boolean isFriend(String name) {
      FriendStore friends = FriendStore.getInstance();
      return friends.isFriend(name) || friends.isFriend(stripDots(name)) || friends.isFriend("." + stripDots(name));
   }

   private boolean blacklisted(String name) {
      String wanted = normalizeIgn(name);
      if (wanted.isEmpty()) {
         return false;
      }

      for (String entry : this.neverRequest.get()) {
         if (wanted.equals(normalizeIgn(entry))) {
            return true;
         }
      }

      return false;
   }

   private boolean excluded(String name) {
      return name == null || this.isSelf(name) || isFriend(name) || this.blacklisted(name);
   }

   private long delayMs() {
      return (long)(this.delay.get() * 1000.0F);
   }

   private String orderLabel() {
      List<String> parsed = parseLetters(this.letters.get());
      String head = this.numbers.get() ? "0-9_ " : "";
      if (this.order.is("Random")) {
         return "random " + head + (parsed.size() < 26 ? String.join("", parsed).toUpperCase(Locale.ROOT) : "A-Z");
      }

      if (parsed.size() < 26) {
         if (this.order.is("Z to A")) {
            Collections.reverse(parsed);
         }

         return head + String.join("", parsed).toUpperCase(Locale.ROOT);
      } else {
         return head + (this.order.is("Z to A") ? "Z to A" : "A to Z");
      }
   }

   private static String formatSeconds(long ms) {
      return ms % 1000L == 0L ? ms / 1000L + "s" : String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
   }

   private String statsText() {
      StringBuilder text = new StringBuilder("Sent ")
         .append(this.totalSent)
         .append(", offline ")
         .append(this.totalOffline)
         .append(", disabled ")
         .append(this.totalDisabled);
      if (this.totalJoiners > 0) {
         text.append(", new players ").append(this.totalJoiners);
      }

      if (this.totalArrivals > 0) {
         text.append(", arrivals ").append(this.totalArrivals);
      }

      if (this.totalHits > 0) {
         text.append(", hits ").append(this.totalHits);
      }

      if (this.totalEaten > 0) {
         text.append(", apples ").append(this.totalEaten);
      }

      if (this.totalDeaths > 0) {
         text.append(", deaths ").append(this.totalDeaths);
      }

      return text.append('.').toString();
   }

   private void info(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   @Environment(EnvType.CLIENT)
   private static final class Bucket {
      List<String> names = List.of();
      List<String> unused = new ArrayList<>();
      boolean scanned;
      long at;
   }

   @Environment(EnvType.CLIENT)
   private final class Collector {
      final String letter;
      boolean split;
      boolean timedOut;
      List<String> names = List.of();
      private boolean started;
      private int mainId = -1;
      private long mainUntil;
      private final List<String> raw = new ArrayList<>();
      private List<String> subs;
      private int subIndex;
      private final List<AutoTpaHereModule.Inflight> inflight = new ArrayList<>();

      Collector(String letter) {
         this.letter = letter;
      }

      boolean step(ClientPlayNetworkHandler connection, long now) {
         if (!this.started) {
            this.started = true;
            this.split = AutoTpaHereModule.this.splitLetters.contains(this.letter);
            if (!this.split) {
               this.mainId = AutoTpaHereModule.this.completions.fire(AutoTpaHereModule.this.partialFor(this.letter));
               this.mainUntil = now + (AutoTpaHereModule.this.localNames(connection, this.letter).isEmpty() ? 5000L : 3000L);
               if (this.mainId < 0) {
                  this.timedOut = true;
               }
            }
         }

         if (this.mainId >= 0) {
            List<String> answer = AutoTpaHereModule.this.completions.poll(this.mainId);
            if (answer == null && now < this.mainUntil) {
               return false;
            }

            AutoTpaHereModule.this.completions.forget(this.mainId);
            this.mainId = -1;
            if (answer == null) {
               this.timedOut = true;
            } else {
               this.raw.addAll(AutoTpaHereModule.this.namesForPrefix(answer, this.letter));
               if (answer.size() >= 990) {
                  this.split = true;
                  AutoTpaHereModule.this.splitLetters.add(this.letter);
               }
            }
         }

         if (this.split) {
            if (this.subs == null) {
               this.subs = AutoTpaHereModule.subPrefixes(this.letter);
            }

            if (!this.stepSplit(now)) {
               return false;
            }

            this.timedOut = this.timedOut && this.raw.isEmpty();
         }

         this.raw.addAll(AutoTpaHereModule.this.localNames(connection, this.letter));
         Set<String> seen = new HashSet<>();
         List<String> out = new ArrayList<>();

         for (String candidate : this.raw) {
            String name = AutoTpaHereModule.cleanName(AutoTpaHereModule.this.stripCommand(candidate));
            if (AutoTpaHereModule.validName(name) && AutoTpaHereModule.this.matchesLetter(name, this.letter) && seen.add(AutoTpaHereModule.key(name))) {
               out.add(name);
            }
         }

         this.names = out;
         return true;
      }

      private boolean stepSplit(long now) {
         while (this.subIndex < this.subs.size() && this.inflight.size() < 6) {
            String prefix = this.subs.get(this.subIndex++);
            int id = AutoTpaHereModule.this.completions.fire(AutoTpaHereModule.this.partialFor(prefix));
            if (id >= 0) {
               this.inflight.add(new AutoTpaHereModule.Inflight(id, prefix, now));
            }
         }

         this.inflight.removeIf(request -> {
            List<String> answer = AutoTpaHereModule.this.completions.poll(request.id());
            if (answer != null) {
               this.raw.addAll(AutoTpaHereModule.this.namesForPrefix(answer, request.prefix()));
            } else if (now - request.at() <= 4000L) {
               return false;
            }

            AutoTpaHereModule.this.completions.forget(request.id());
            return true;
         });
         return this.subIndex >= this.subs.size() && this.inflight.isEmpty();
      }
   }

   @Environment(EnvType.CLIENT)
   private final class Delivery {
      final String name;
      final boolean joiner;
      boolean stale;
      int probeId = -1;
      long probeAt;
      boolean finalWait;
      long finalUntil;

      Delivery(String name, boolean joiner) {
         this.name = name;
         this.joiner = joiner;
      }

      AutoTpaHereModule.Result step(ClientPlayNetworkHandler connection, long now) {
         if (this.stale) {
            this.stale = false;
            AutoTpaHereModule.this.completions.forget(this.probeId);
            this.probeId = -1;
            this.finalWait = false;
         }

         if (AutoTpaHereModule.this.excluded(this.name)) {
            return AutoTpaHereModule.Result.SKIPPED;
         }

         if (AutoTpaHereModule.this.skipOffline.get() && !AutoTpaHereModule.this.onlineCheckOff && this.probeId < 0 && !this.finalWait) {
            this.probeId = AutoTpaHereModule.this.completions.fire(AutoTpaHereModule.this.command.get() + " " + this.name);
            this.probeAt = now;
         }

         if (now < AutoTpaHereModule.this.lastSendAt + AutoTpaHereModule.this.delayMs()) {
            if (this.probeId >= 0 && Boolean.FALSE.equals(AutoTpaHereModule.this.probeAnswer(AutoTpaHereModule.this.completions.poll(this.probeId), this.name))
               )
             {
               AutoTpaHereModule.this.completions.forget(this.probeId);
               AutoTpaHereModule.this.totalOffline++;
               AutoTpaHereModule.this.noteProbe(false);
               return AutoTpaHereModule.Result.SKIPPED;
            } else {
               return AutoTpaHereModule.Result.WAIT;
            }
         } else {
            if (this.probeId >= 0) {
               if (!this.finalWait) {
                  this.finalWait = true;
                  if (now - this.probeAt > 4000L) {
                     AutoTpaHereModule.this.completions.forget(this.probeId);
                     this.probeId = AutoTpaHereModule.this.completions.fire(AutoTpaHereModule.this.command.get() + " " + this.name);
                     this.probeAt = now;
                     this.finalUntil = now + 700L;
                  } else {
                     this.finalUntil = now + Math.max(100L, 700L - (now - this.probeAt));
                  }
               }

               List<String> answer = AutoTpaHereModule.this.completions.poll(this.probeId);
               if (answer == null && now < this.finalUntil) {
                  return AutoTpaHereModule.Result.WAIT;
               }

               AutoTpaHereModule.this.completions.forget(this.probeId);
               this.probeId = -1;
               Boolean online = AutoTpaHereModule.this.probeAnswer(answer, this.name);
               AutoTpaHereModule.this.noteProbe(online);
               if (Boolean.FALSE.equals(online)) {
                  AutoTpaHereModule.this.totalOffline++;
                  return AutoTpaHereModule.Result.SKIPPED;
               }
            }

            AutoTpaHereModule.this.send(connection, this.name, this.joiner, now);
            return AutoTpaHereModule.Result.SENT;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Inflight(int id, String prefix, long at) {
   }

   @Environment(EnvType.CLIENT)
   private record Near(AbstractClientPlayerEntity player, String name, double distance) {
   }

   @Environment(EnvType.CLIENT)
   private enum Phase {
      DETECT,
      COLLECT,
      SEND,
      PICK;
   }

   @Environment(EnvType.CLIENT)
   private enum Result {
      WAIT,
      SENT,
      SKIPPED;
   }
}
