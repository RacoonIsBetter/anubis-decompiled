package net.anubis.client.module.impl.misc;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.StaffGhost;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.util.NameFilter;
import net.anubis.client.util.staff.ShardTracker;
import net.anubis.client.util.staff.StaffAlarm;
import net.anubis.client.util.staff.StaffMatcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.scoreboard.Team;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

@Environment(EnvType.CLIENT)
public final class StaffListModule extends ClientModule {
   public static final List<String> DONUT_STAFF = List.of(
      "FluffyMaster07",
      "archivePedro",
      "Munkerlich",
      "Frenk_Btw",
      "Napooo_",
      "auzzitech",
      "CryptoDaveYT",
      "W1zox_",
      "ZEEF69",
      "showered",
      "CaptainMoose35",
      "BobIsFound",
      "NoahvdAa",
      "0GSummer",
      "LzouzMP5",
      "Pastagamer08",
      "u_vv",
      "Owen1212055",
      "Splaterd",
      "Fallerfly",
      "Bloodspulse",
      "_chaon",
      "Dough4",
      "evify"
   );
   private static final Set<String> STAFF_KEYS = staffKeys();
   private static final StaffMatcher RANKS = new StaffMatcher(
      "Ranks + Markers + Team",
      "owner, co-owner, manager, admin, administrator, developer, dev, sr-admin, sr-mod, senior-mod, moderator, mod, trial-mod, sr-helper, senior-helper, helper, trial-helper, support, staff",
      "U+2605 U+2606 U+2726 U+2727 U+272A U+272F U+2730 U+2B50",
      false,
      5,
      String.join(",", DONUT_STAFF),
      ""
   );
   private static final int SCAN_INTERVAL_TICKS = 10;
   private static final int SETTLE_TICKS = 60;
   private static final int VANISH_CONFIRM_SCANS = 2;
   private static final int GROUP_THRESHOLD = 3;
   private static final int ALERT_GATHER_TICKS = 5;
   private static final int SWITCH_ECHO_TICKS = 20;
   public static final long ALERT_HIGHLIGHT_MS = 4000L;
   private static final long ALERT_COOLDOWN_MS = 120000L;
   private static final long SERVER_MARK_MS = 600000L;
   private static final int MAX_ROWS = 8;
   private static final float ALARM_SECONDS = 3.0F;
   private static final long SHARED_ALARM_MS = 5000L;
   private static final long NEARBY_GRACE_MS = 10000L;
   private static final long SIGHTING_GRACE_MS = 10000L;
   private static final long WATCH_GRACE_MS = 10000L;
   private static final Pattern ACCOUNT_NAME = Pattern.compile("[.*]?[A-Za-z0-9_]{1,16}");
   private final BooleanSetting regionAlert = this.addSetting("server-alert", new BooleanSetting("Region Alert", true));
   private final Map<UUID, StaffListModule.Staff> staff = new LinkedHashMap<>();
   private List<StaffListModule.Staff> sorted = List.of();
   private boolean resort;
   private final Map<UUID, StaffListModule.Classified> classified = new HashMap<>();
   private final Map<UUID, StaffListModule.Sighting> sightings = new HashMap<>();
   private final Queue<StaffListModule.GameModeSwitch> switches = new ConcurrentLinkedQueue<>();
   private final Map<UUID, String> pendingAlerts = new LinkedHashMap<>();
   private final StaffAlarm alarm = new StaffAlarm();
   private ClientPlayNetworkHandler connection;
   private final ShardTracker shard = new ShardTracker();
   private int ticksToScan;
   private int settleTicks;
   private boolean primed;
   private int alertGather;
   private int tickCount;

   public StaffListModule() {
      super("staff-list", "Staff List", "Lists online DonutSMP staff.", ModuleCategory.DONUT);
      this.usesNotifications();
      this.regionAlert.onChange(() -> {
         if (!this.regionAlert.get()) {
            this.forgetShard();
         }
      });
   }

   @Override
   protected void onEnable() {
      this.forget();
      MinecraftClient mc = MinecraftClient.getInstance();
      this.connection = mc.getNetworkHandler();
      this.shard.clear();
      this.settleTicks = 0;
      this.ticksToScan = 0;
      this.primed = false;
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      this.on(PacketReceiveNettyEvent.TYPE, this::onPacketNetty);
   }

   @Override
   protected void onDisable() {
      this.forget();
      this.connection = null;
      this.shard.clear();
   }

   private void forget() {
      this.staff.clear();
      this.sorted = List.of();
      this.classified.clear();
      this.forgetShard();
   }

   private void forgetShard() {
      this.switches.clear();
      this.sightings.clear();
      this.pendingAlerts.clear();
      this.alarm.stop();
      this.resort = true;
   }

   public static boolean shouldShowHud() {
      StaffListModule module = instance();
      return module != null && module.isEnabled() && !module.sorted.isEmpty();
   }

   public static StaffListModule.HudList hudRows() {
      StaffListModule module = instance();
      if (module != null && module.isEnabled()) {
         List<StaffListModule.Staff> all = module.sorted;
         int shown = all.size() <= 8 ? all.size() : 7;
         long now = System.currentTimeMillis();
         List<StaffListModule.HudRow> rows = new ArrayList<>(shown);

         for (int i = 0; i < shown; i++) {
            StaffListModule.Staff s = all.get(i);
            StaffListModule.Sighting seen = module.sightings.get(s.id);
            boolean watching = seen != null && seen.watching(now);
            boolean nearby = seen != null && seen.nearby;
            float alert = seen == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, (float)(seen.highlightUntil - now) / 4000.0F));
            String shownName = NameFilter.apply(s.name);
            rows.add(
               new StaffListModule.HudRow(
                  shownName,
                  s.id,
                  s.presence,
                  watching,
                  watching ? Math.max(0L, now - seen.watchingSince) : -1L,
                  nearby,
                  nearby ? seen.distance : -1.0F,
                  alert,
                  !shownName.equals(s.name)
               )
            );
         }

         return new StaffListModule.HudList(rows, all.size() - shown);
      } else {
         return StaffListModule.HudList.EMPTY;
      }
   }

   public static String formatAge(long ms) {
      long total = Math.max(0L, ms) / 1000L;
      long hours = total / 3600L;
      long minutes = total / 60L % 60L;
      long seconds = total % 60L;
      StringBuilder out = new StringBuilder(8);
      if (hours > 0L) {
         out.append(hours).append(minutes < 10L ? ":0" : ":");
      }

      return out.append(minutes).append(seconds < 10L ? ":0" : ":").append(seconds).toString();
   }

   public static boolean isStaffName(String name) {
      return name != null && STAFF_KEYS.contains(name.toLowerCase(Locale.ROOT));
   }

   public static boolean isInYourRegion(UUID id) {
      StaffListModule module = instance();
      if (module != null && module.isEnabled() && id != null) {
         StaffListModule.Sighting seen = module.sightings.get(id);
         return seen != null && seen.marked(System.currentTimeMillis());
      } else {
         return false;
      }
   }

   public static void provenHere(UUID id, String name) {
      StaffListModule module = instance();
      if (module != null && module.isEnabled() && module.regionAlert.get() && id != null && name != null) {
         long now = System.currentTimeMillis();
         StaffListModule.Sighting seen = module.sightings.computeIfAbsent(id, key -> new StaffListModule.Sighting());
         if (!seen.marked(now)) {
            module.resort = true;
         }

         seen.prove(now);
         if (!seen.coolingDown(now)) {
            module.alert(id, seen, now, name);
         }
      }
   }

   public static boolean alarmedRecently(UUID id) {
      StaffListModule module = instance();
      if (module != null && module.isEnabled() && id != null) {
         StaffListModule.Sighting seen = module.sightings.get(id);
         return seen != null && seen.alertedAt != 0L && System.currentTimeMillis() - seen.alertedAt < 5000L;
      } else {
         return false;
      }
   }

   private static Set<String> staffKeys() {
      Set<String> keys = new HashSet<>();

      for (String name : DONUT_STAFF) {
         keys.add(name.toLowerCase(Locale.ROOT));
      }

      return Set.copyOf(keys);
   }

   private static StaffListModule instance() {
      return ModuleRegistry.get(StaffListModule.class);
   }

   private void onPacketNetty(PacketReceiveNettyEvent event) {
      if (this.regionAlert.get()) {
         if (event.packet instanceof PlayerListS2CPacket info) {
            this.queueSwitches(info);
         } else if (event.packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> packet : bundle.getPackets()) {
               if (packet instanceof PlayerListS2CPacket info) {
                  this.queueSwitches(info);
               }
            }
         }
      }
   }

   private void queueSwitches(PlayerListS2CPacket info) {
      EnumSet<Action> actions = info.getActions();
      if (actions.contains(Action.UPDATE_GAME_MODE) && !actions.contains(Action.ADD_PLAYER)) {
         for (net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry entry : info.getEntries()) {
            if (entry != null && entry.profileId() != null) {
               this.switches.offer(new StaffListModule.GameModeSwitch(entry.profileId(), entry.gameMode()));
            }
         }
      }
   }

   private void onTick(TickEvent event) {
      this.alarm.tick();
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler conn = mc.getNetworkHandler();
      if (conn != null && mc.player != null && mc.world != null) {
         boolean moved = this.shard.moved(mc);
         if (conn != this.connection) {
            this.connection = conn;
            this.forget();
            this.settleTicks = 60;
            this.primed = false;
         } else if (moved) {
            this.forgetShard();
         }

         if (this.settleTicks > 0) {
            this.settleTicks--;
         }

         this.tickCount++;
         long now = System.currentTimeMillis();
         if (this.regionAlert.get()) {
            this.drainSwitches(conn, mc.player.getUuid(), now);
            this.trackBodies(mc, now);
         }

         if (--this.ticksToScan <= 0) {
            this.ticksToScan = 10;
            StaffListModule.Changes changes = this.scan(mc, conn, now);
            if (this.primed) {
               this.announce(mc, changes);
            } else if (this.settleTicks == 0) {
               this.primed = true;
            }
         }

         if (!this.pendingAlerts.isEmpty() && --this.alertGather <= 0) {
            this.raise();
         }

         if (this.resort) {
            this.sortView(now);
         }
      } else {
         if (this.connection != null || !this.staff.isEmpty() || !this.classified.isEmpty() || !this.sightings.isEmpty()) {
            this.forget();
         }

         this.switches.clear();
         this.connection = null;
         this.shard.clear();
         this.primed = false;
      }
   }

   private StaffListModule.Changes scan(MinecraftClient mc, ClientPlayNetworkHandler conn, long now) {
      UUID self = mc.player.getUuid();
      Set<UUID> listed = new HashSet<>();

      for (PlayerListEntry info : conn.getListedPlayerListEntries()) {
         listed.add(info.getProfile().id());
      }

      StaffListModule.Changes changes = new StaffListModule.Changes();
      Set<UUID> inTab = new HashSet<>();

      for (PlayerListEntry info : conn.getPlayerList()) {
         GameProfile profile = info.getProfile();
         UUID id = profile.id();
         String name = profile.name();
         if (id != null && !id.equals(self) && isStaffName(name) && isAccount(id, name)) {
            inTab.add(id);
            StaffMatcher.Match rank = this.rank(info, id, name);
            StaffListModule.Presence presence = info.getGameMode() == GameMode.SPECTATOR
               ? StaffListModule.Presence.SPECTATOR
               : (listed.contains(id) ? StaffListModule.Presence.ONLINE : StaffListModule.Presence.VANISHED);
            StaffListModule.Staff tracked = this.staff.get(id);
            if (tracked == null) {
               tracked = new StaffListModule.Staff(id);
               this.staff.put(id, tracked);
               tracked.update(name, rank, presence);
               changes.joined.add(tracked);
            } else {
               StaffListModule.Presence before = tracked.presence;
               tracked.update(name, rank, presence);
               tracked.offTabScans = 0;
               if (before != presence) {
                  changes.changed.add(tracked);
               }
            }
         }
      }

      this.classified.keySet().retainAll(inTab);
      Iterator<StaffListModule.Staff> it = this.staff.values().iterator();

      while (it.hasNext()) {
         StaffListModule.Staff tracked = it.next();
         if (!inTab.contains(tracked.id)) {
            if (mc.world.getPlayerByUuid(tracked.id) != null) {
               if (++tracked.offTabScans >= 2 && tracked.presence != StaffListModule.Presence.VANISHED) {
                  tracked.presence = StaffListModule.Presence.VANISHED;
                  changes.changed.add(tracked);
               }
            } else {
               it.remove();
               changes.left.add(tracked);
            }
         }
      }

      this.pruneSightings(now);
      this.sortView(now);
      return changes;
   }

   private void sortView(long now) {
      List<StaffListModule.Staff> view = new ArrayList<>(this.staff.values());
      view.sort(
         Comparator.<StaffListModule.Staff>comparingInt(s -> this.group(s, now))
            .thenComparingInt(s -> s.seniority)
            .thenComparing(s -> s.name, String.CASE_INSENSITIVE_ORDER)
      );
      this.sorted = List.copyOf(view);
      this.resort = false;
   }

   private int group(StaffListModule.Staff s, long now) {
      StaffListModule.Sighting seen = this.sightings.get(s.id);
      if (seen != null && seen.nearby) {
         return 0;
      } else if (seen != null && seen.marked(now)) {
         return 1;
      } else {
         return s.presence != StaffListModule.Presence.ONLINE ? 2 : 3;
      }
   }

   private static boolean isAccount(UUID id, String name) {
      return name != null && id.version() != 2 && ACCOUNT_NAME.matcher(name).matches();
   }

   private StaffMatcher.Match rank(PlayerListEntry info, UUID id, String name) {
      Team team = info.getScoreboardTeam();
      Text display = info.getDisplayName();
      Text prefix = team == null ? null : team.getPrefix();
      Text suffix = team == null ? null : team.getSuffix();
      StaffListModule.Classified last = this.classified.get(id);
      if (last != null && last.isFor(name, display, team, prefix, suffix)) {
         return last.match();
      }

      StaffMatcher.Match match = RANKS.match(name, team == null ? null : team.getName(), plain(display), plain(prefix), plain(suffix));
      this.classified.put(id, new StaffListModule.Classified(name, display, team, prefix, suffix, match));
      return match;
   }

   private static String plain(Text component) {
      return component == null ? null : component.getString();
   }

   private void drainSwitches(ClientPlayNetworkHandler conn, UUID self, long now) {
      StaffListModule.GameModeSwitch change;
      while ((change = this.switches.poll()) != null) {
         UUID id = change.id();
         if (!id.equals(self)) {
            StaffListModule.Staff tracked = this.staff.get(id);
            String name;
            if (tracked != null) {
               name = tracked.name;
            } else {
               PlayerListEntry info = conn.getPlayerListEntry(id);
               name = info == null ? null : info.getProfile().name();
               if (!isStaffName(name) || !isAccount(id, name)) {
                  continue;
               }
            }

            StaffListModule.Sighting seen = this.sightings.computeIfAbsent(id, key -> new StaffListModule.Sighting());
            if (!seen.marked(now)) {
               this.resort = true;
            }

            seen.prove(now);
            if (!seen.coolingDown(now)) {
               GameMode mode = change.mode();
               seen.switchAlertTick = this.tickCount;
               seen.switchedToSpectator = mode == GameMode.SPECTATOR;
               this.alert(id, seen, now, name);
            }
         }
      }
   }

   private void trackBodies(MinecraftClient mc, long now) {
      ClientPlayerEntity self = mc.player;

      for (AbstractClientPlayerEntity body : mc.world.getPlayers()) {
         if (body != self) {
            StaffListModule.Staff tracked = this.staff.get(body.getUuid());
            if (tracked != null) {
               StaffListModule.Sighting seen = this.sightings.computeIfAbsent(tracked.id, key -> new StaffListModule.Sighting());
               seen.prove(now);
               seen.bodyTick = this.tickCount;
               seen.distance = self.distanceTo(body);
               if (!seen.nearby) {
                  seen.nearby = true;
                  this.resort = true;
                  boolean newAppearance = seen.bodyLostAt == 0L || now - seen.bodyLostAt > 10000L;
                  if (newAppearance) {
                     this.alert(tracked.id, seen, now, tracked.name);
                  }
               }
            }
         }
      }

      for (StaffListModule.Sighting seen : this.sightings.values()) {
         if (seen.nearby && seen.bodyTick != this.tickCount) {
            seen.nearby = false;
            seen.bodyLostAt = now;
            this.resort = true;
         }
      }
   }

   private void pruneSightings(long now) {
      Iterator<Entry<UUID, StaffListModule.Sighting>> it = this.sightings.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, StaffListModule.Sighting> entry = it.next();
         StaffListModule.Sighting seen = entry.getValue();
         if (this.staff.containsKey(entry.getKey())) {
            seen.untrackedSince = 0L;
         } else if (seen.untrackedSince == 0L) {
            seen.untrackedSince = now;
         } else if (now - seen.untrackedSince > 10000L) {
            it.remove();
            this.resort = true;
         }
      }
   }

   private void alert(UUID id, StaffListModule.Sighting seen, long now, String name) {
      seen.alertedAt = now;
      seen.highlightUntil = now + 4000L;
      if (this.pendingAlerts.isEmpty()) {
         this.alertGather = 5;
      }

      this.pendingAlerts.put(id, name);
   }

   private void raise() {
      Entry<UUID, String> first = this.pendingAlerts.entrySet().iterator().next();
      UUID id = first.getKey();
      String name = first.getValue();
      int others = this.pendingAlerts.size() - 1;
      this.pendingAlerts.clear();
      this.alarm.start("Guardian", 3.0F, 1.0F);
      StaffGhost.watching(id, name, others);
   }

   private void announce(MinecraftClient mc, StaffListModule.Changes changes) {
      changes.changed.removeIf(this::echoesSwitchAlert);
      if (!changes.isEmpty()) {
         List<ModuleChat.Body> lines = new ArrayList<>();
         describe(lines, changes.joined, ModuleChat.body().warn("joined").build(), (linex, s) -> {
            linex.warn("joined");
            String note = note(s);
            if (note != null) {
               linex.text(" ").danger(note);
            }
         });
         describe(lines, changes.changed, ModuleChat.body().warn("changed visibility").build(), (linex, s) -> {
            switch (s.presence) {
               case ONLINE:
                  linex.text("is ").good("visible again");
                  break;
               case SPECTATOR:
                  switchedTo(linex, GameMode.SPECTATOR.getId());
                  break;
               case VANISHED:
                  linex.danger("vanished").text(s.offTabScans > 0 ? " (off TAB, still nearby)" : " (hidden from TAB)");
            }
         });
         describe(lines, changes.left, ModuleChat.body().good("left").build(), (linex, s) -> {
            if (s.presence == StaffListModule.Presence.VANISHED) {
               linex.text("is ").warn("out of sight");
            } else {
               linex.good("left");
            }
         });

         for (ModuleChat.Body line : lines) {
            ModuleChat.send(this, line);
         }

         boolean urgent = !changes.joined.isEmpty() || changes.changed.stream().anyMatch(s -> s.presence != StaffListModule.Presence.ONLINE);
         if (urgent && mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.25F));
         }
      }
   }

   private boolean echoesSwitchAlert(StaffListModule.Staff s) {
      StaffListModule.Sighting seen = this.sightings.get(s.id);
      if (seen != null && seen.switchAlertTick >= 0 && this.tickCount - seen.switchAlertTick <= 20) {
         return s.presence == StaffListModule.Presence.SPECTATOR
            ? seen.switchedToSpectator
            : s.presence == StaffListModule.Presence.ONLINE && !seen.switchedToSpectator;
      } else {
         return false;
      }
   }

   private static void describe(
      List<ModuleChat.Body> lines, List<StaffListModule.Staff> group, Text groupVerb, BiConsumer<ModuleChat.Body, StaffListModule.Staff> verb
   ) {
      if (!group.isEmpty()) {
         if (group.size() > 3) {
            List<Text> labels = new ArrayList<>(group.size());

            for (StaffListModule.Staff s : group) {
               labels.add(label(s));
            }

            lines.add(list(ModuleChat.body().value(group.size()).text(" staff ").append(groupVerb).text(": "), labels));
         } else {
            for (StaffListModule.Staff s : group) {
               ModuleChat.Body line = ModuleChat.body().append(label(s)).text(" ");
               verb.accept(line, s);
               lines.add(line);
            }
         }
      }
   }

   private static Text label(StaffListModule.Staff s) {
      return label(s.name, s.rank);
   }

   private static Text label(String name, String rank) {
      return ModuleChat.body().name(NameFilter.apply(name)).text(" (" + rank + ")").build();
   }

   private static String note(StaffListModule.Staff s) {
      return switch (s.presence) {
         case ONLINE -> null;
         case SPECTATOR -> "spectating";
         case VANISHED -> "vanished";
      };
   }

   private static ModuleChat.Body list(ModuleChat.Body body, List<Text> parts) {
      for (int i = 0; i < parts.size(); i++) {
         if (i > 0) {
            body.text(", ");
         }

         body.append(parts.get(i));
      }

      return body;
   }

   static ModuleChat.Body switchedTo(ModuleChat.Body body, String mode) {
      if (mode == null) {
         return body.text("switched game mode");
      } else {
         body.text("switched to ");
         if (GameMode.SPECTATOR.getId().equals(mode)) {
            return body.danger(mode);
         } else {
            return GameMode.CREATIVE.getId().equals(mode) ? body.warn(mode) : body.name(mode);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Changes {
      final List<StaffListModule.Staff> joined = new ArrayList<>();
      final List<StaffListModule.Staff> changed = new ArrayList<>();
      final List<StaffListModule.Staff> left = new ArrayList<>();

      boolean isEmpty() {
         return this.joined.isEmpty() && this.changed.isEmpty() && this.left.isEmpty();
      }
   }

   @Environment(EnvType.CLIENT)
   private record Classified(String name, Text display, Team team, Text prefix, Text suffix, StaffMatcher.Match match) {
      boolean isFor(String name, Text display, Team team, Text prefix, Text suffix) {
         return this.display == display && this.team == team && this.prefix == prefix && this.suffix == suffix && this.name.equals(name);
      }
   }

   @Environment(EnvType.CLIENT)
   private record GameModeSwitch(UUID id, GameMode mode) {
   }

   @Environment(EnvType.CLIENT)
   public record HudList(List<StaffListModule.HudRow> rows, int more) {
      static final StaffListModule.HudList EMPTY = new StaffListModule.HudList(List.of(), 0);
   }

   @Environment(EnvType.CLIENT)
   public record HudRow(
      String name,
      UUID id,
      StaffListModule.Presence presence,
      boolean watching,
      long watchingForMs,
      boolean nearby,
      float distance,
      float alertLevel,
      boolean nameHidden
   ) {
      public String watchingFor() {
         return this.watching ? StaffListModule.formatAge(this.watchingForMs) : "";
      }

      public String distanceLabel() {
         return this.nearby ? Math.round(this.distance) + "m" : "";
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Presence {
      ONLINE,
      SPECTATOR,
      VANISHED;
   }

   @Environment(EnvType.CLIENT)
   private static final class Sighting {
      long provenAt;
      long watchingSince;
      long alertedAt;
      long highlightUntil;
      boolean nearby;
      float distance;
      long bodyLostAt;
      int bodyTick;
      int switchAlertTick = -1;
      boolean switchedToSpectator;
      long untrackedSince;

      boolean marked(long now) {
         return this.nearby || this.provenAt != 0L && now - this.provenAt < 600000L;
      }

      void prove(long now) {
         if (!this.watching(now)) {
            this.watchingSince = now;
         }

         this.provenAt = now;
      }

      boolean watching(long now) {
         return this.nearby || this.provenAt != 0L && now - this.provenAt < 10000L;
      }

      boolean coolingDown(long now) {
         return this.alertedAt != 0L && now - this.alertedAt < 120000L;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Staff {
      final UUID id;
      String name = "";
      String rank = "";
      int seniority;
      StaffListModule.Presence presence = StaffListModule.Presence.ONLINE;
      int offTabScans;

      Staff(UUID id) {
         this.id = id;
      }

      void update(String name, StaffMatcher.Match rank, StaffListModule.Presence presence) {
         this.name = name;
         this.rank = rank.rank();
         this.seniority = rank.seniority();
         this.presence = presence;
      }
   }
}
