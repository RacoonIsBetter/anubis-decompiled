package net.anubis.client.module.impl.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.KeyEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.anubis.client.sound.SoundDetectorConfig;
import net.anubis.client.sound.SoundDetectorController;
import net.anubis.client.sound.SoundDetectorSettings;
import net.anubis.client.sound.SoundIncident;
import net.anubis.client.sound.gui.SoundAlertEditorScreen;
import net.anubis.client.sound.gui.SoundAlertHud;
import net.anubis.client.sound.gui.SoundHistoryScreen;
import net.anubis.client.sound.gui.SoundRulesScreen;
import net.anubis.client.sound.gui.SoundSettingsJsonScreen;
import net.anubis.client.sound.gui.SoundSettingsScreen;
import net.anubis.client.util.FoglessLines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.lwjgl.glfw.GLFW;

/**
 * Donut Client's Sound Detector (https://github.com/Raindots/donut-client, MIT), ported one to one:
 * it reads every sound, block event, world event, mining, damage and explosion packet the server
 * sends, scores them into incidents (containers, workstations, building, switches, machinery, ...)
 * and shows warning cards, a chime, chat notices and world markers, with history, rules, expected
 * areas and per-server profiles. The settings below are the ones from Donut's main settings page;
 * "All Settings" opens every preference.
 */
@Environment(EnvType.CLIENT)
public final class DonutSoundDetectorModule extends ClientModule {
   private final SoundDetectorConfig config = SoundDetectorConfig.get();
   private final List<DonutSoundDetectorModule.Binding<?>> bindings = new ArrayList<>();
   private final KeySetting acknowledgeKey;
   private final KeySetting muteKey;
   private final KeySetting historyKey;
   private SoundDetectorController controller;

   private final ModeSetting preset = this.bind(
      this.addSetting("preset", new ModeSetting("Preset", SoundDetectorSettings.PRESETS.getFirst(), SoundDetectorSettings.PRESETS.toArray(String[]::new))),
      ModeSetting::get, ModeSetting::set, () -> this.cfg().preset, value -> this.cfg().applyPreset(value)
   );
   private final NumberSetting range = this.number("range", "Received-event Range", 0, 512, 1, () -> this.cfg().range, v -> this.cfg().range = v);
   private final NumberSetting warning = this.number("warning-threshold", "Warning Threshold", 1, 100, 1, () -> this.cfg().warningThreshold, v -> {
      this.cfg().warningThreshold = (int)Math.round(v);
      this.cfg().criticalThreshold = Math.max(this.cfg().criticalThreshold, this.cfg().warningThreshold);
   });
   private final NumberSetting critical = this.number("critical-threshold", "Critical Threshold", 1, 100, 1, () -> this.cfg().criticalThreshold, v -> this.cfg().criticalThreshold = (int)Math.round(v));
   private final BooleanSetting playersOnly = this.toggle("players-only", "Require Identified Player", () -> this.cfg().playersOnly, v -> this.cfg().playersOnly = v);
   private final BooleanSetting suppressSelf = this.toggle("suppress-self", "Filter Own Actions", () -> this.cfg().suppressSelf, v -> this.cfg().suppressSelf = v);
   private final BooleanSetting suppressProbableSelf = this.toggle("suppress-probable-self", "Filter Probable Own Actions", () -> this.cfg().suppressProbableSelf, v -> this.cfg().suppressProbableSelf = v);
   private final BooleanSetting ignoreNonPlayers = this.toggle("ignore-non-players", "Filter Known Mobs", () -> this.cfg().ignoreNonPlayers, v -> this.cfg().ignoreNonPlayers = v);
   private final BooleanSetting clientDerived = this.toggle("client-derived", "Include Client-derived Sounds", () -> this.cfg().includeClientDerived, v -> this.cfg().includeClientDerived = v);

   public DonutSoundDetectorModule() {
      super(
         "donut-sound-detector",
         "Donut Sound Detector",
         "Donut Client's Sound Detector: scores the sounds and block events the server sends into warnings about activity you can't see.",
         ModuleCategory.BASE_RAID
      );

      for (String category : SoundDetectorSettings.CATEGORIES) {
         this.toggle("source-" + category, "Source: " + SoundSettingsScreen.label(category),
            () -> this.cfg().categoryEnabled(category), v -> this.cfg().categories.put(category, v));
      }

      this.toggle("popup", "Warning Cards", () -> this.cfg().popup, v -> this.cfg().popup = v);
      this.toggle("chime", "Local Warning Chime", () -> this.cfg().chime, v -> this.cfg().chime = v);
      this.toggle("chat", "Chat Notices", () -> this.cfg().chat, v -> this.cfg().chat = v);
      this.toggle("world-markers", "World Markers", () -> this.cfg().worldMarkers, v -> this.cfg().worldMarkers = v);
      this.number("duration", "Card Duration (s)", 1, 60, 1, () -> this.cfg().durationSeconds, v -> this.cfg().durationSeconds = v);
      this.number("max-cards", "Visible Cards", 1, 5, 1, () -> this.cfg().maxCards, v -> this.cfg().maxCards = (int)Math.round(v));
      this.number("cooldown", "Source Cooldown (s)", 0.1F, 60, 0.1F, () -> this.cfg().cooldownSeconds, v -> this.cfg().cooldownSeconds = v);
      this.toggle("show-in-menus", "Show While In Menus", () -> this.cfg().showInMenus, v -> this.cfg().showInMenus = v);
      this.toggle("respect-hud", "Respect Global HUD Toggle", () -> this.cfg().respectHud, v -> this.cfg().respectHud = v);

      this.action("preview-warning", "Preview Warning / Move Alert", () -> this.open(parent -> new SoundAlertEditorScreen(parent, this.config, this.config::save, false)));
      this.action("preview-critical", "Preview Critical Alert", () -> this.open(parent -> new SoundAlertEditorScreen(parent, this.config, this.config::save, true)));
      this.number("scale", "Card Scale", 0.5F, 2, 0.05F, () -> this.cfg().scale, v -> this.cfg().scale = v);
      this.number("opacity", "Background Opacity", 0.05F, 1, 0.01F, () -> this.cfg().opacity, v -> this.cfg().opacity = v);
      this.color("warning-color", "Warning Accent", () -> this.cfg().warningColor, v -> this.cfg().warningColor = v);
      this.color("critical-color", "Critical Accent", () -> this.cfg().criticalColor, v -> this.cfg().criticalColor = v);
      this.color("top-color", "Card Top", () -> this.cfg().topColor, v -> this.cfg().topColor = v);
      this.color("bottom-color", "Card Bottom", () -> this.cfg().bottomColor, v -> this.cfg().bottomColor = v);
      this.number("volume", "Chime Volume", 0, 1, 0.01F, () -> this.cfg().volume, v -> this.cfg().volume = v);
      this.action("all-settings", "All Appearance, Audio And Behavior Controls", () -> this.open(parent -> new SoundSettingsScreen(parent, this.config, this.config::save)));

      this.action("rules", "Custom Rules / Expected Areas", () -> this.open(parent -> new SoundRulesScreen(parent, this.config, this.config::save)));
      this.action("history", "Activity History And Evidence", () -> this.open(parent -> new SoundHistoryScreen(parent, this.config)));
      this.action("mute", "Mute Notifications For 60 Seconds", () -> {
         if (this.controller() != null) {
            this.controller().mute(60);
            this.status("Notifications muted for 60 seconds. Observation continues.");
         }
      });
      this.action("save-profile", "Save Current Server Profile", () -> {
         if (this.controller() != null) {
            this.controller().saveProfile();
            this.config.save();
            this.status("Saved current server preferences.");
         }
      });
      this.action("load-profile", "Load Current Server Profile", () -> {
         if (this.controller() != null) {
            boolean loaded = this.controller().loadProfile();
            this.config.save();
            this.status(loaded ? "Loaded this server's saved preferences." : "No saved preferences for this server.");
         }
      });
      this.action("export-catalog", "Export Full Block / Sound Coverage", () -> {
         if (this.controller() != null) {
            try {
               this.status("Exported " + this.controller().exportCatalog().getFileName());
            } catch (Exception e) {
               this.status("Export failed: " + e.getMessage());
            }
         }
      });
      this.action("advanced", "Advanced Preferences / Import / Export", () -> this.open(parent -> new SoundSettingsJsonScreen(parent, this.config, this.config::save)));

      this.acknowledgeKey = this.addSetting("acknowledge-key", new KeySetting("Acknowledge Alert", -1));
      this.muteKey = this.addSetting("mute-key", new KeySetting("Mute 60s", -1));
      this.historyKey = this.addSetting("history-key", new KeySetting("Open History", -1));
      this.critical.visibleWhen(() -> this.warning.get() < 100.0F);
   }

   private SoundDetectorSettings cfg() {
      return this.config.soundDetector;
   }

   private SoundDetectorController controller() {
      if (this.controller == null) {
         this.controller = SoundDetectorController.get() != null ? SoundDetectorController.get() : new SoundDetectorController(this.config);
      }

      return this.controller;
   }

   @Override
   protected void onEnable() {
      this.cfg().enabled = true;
      this.sync();
      this.config.save();
      this.controller();
   }

   @Override
   protected void onDisable() {
      this.cfg().enabled = false;
      this.config.save();
      if (this.controller != null) {
         this.controller.disconnect();
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.sync();
      this.controller().tick(MinecraftClient.getInstance());
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      if (this.controller != null && event.graphics != null) {
         SoundAlertHud.render(event.graphics, this.config, this.controller.alerts());
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.controller == null) {
         return;
      }

      List<SoundIncident> markers = this.controller.markers();
      if (markers.isEmpty()) {
         return;
      }

      FoglessLines lines = FoglessLines.forFrame(event.partialTick);
      for (SoundIncident incident : markers) {
         int rgb = incident.priority() == SoundIncident.Priority.CRITICAL ? this.cfg().criticalColor : this.cfg().warningColor;
         int color = 0xFF000000 | rgb;
         Box box = new Box(incident.x() - .3, incident.y(), incident.z() - .3, incident.x() + .3, incident.y() + .6, incident.z() + .3);
         GizmoDrawing.box(box, DrawStyle.filled(ColorHelper.withAlpha(31, rgb))).ignoreOcclusion();
         int outline = ColorHelper.withAlpha(217, color);
         Vec3d[] corners = {
            new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.minX, box.minY, box.maxZ)
         };

         for (int i = 0; i < 4; i++) {
            Vec3d a = corners[i];
            Vec3d b = corners[(i + 1) % 4];
            lines.line(a, b, outline, 1.5F);
            lines.line(a.add(0.0, .6, 0.0), b.add(0.0, .6, 0.0), outline, 1.5F);
            lines.line(a, a.add(0.0, .6, 0.0), outline, 1.5F);
         }
      }
   }

   @EventHandler
   public void onKey(KeyEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (event.action != GLFW.GLFW_PRESS || mc.currentScreen != null || this.controller == null) {
         return;
      }

      if (event.key == this.acknowledgeKey.value()) {
         this.controller.acknowledge();
      } else if (event.key == this.muteKey.value()) {
         this.controller.mute(60);
      } else if (event.key == this.historyKey.value() && mc.player != null) {
         mc.setScreen(new SoundHistoryScreen(null, this.config));
      }
   }

   private void open(java.util.function.Function<Screen, Screen> screen) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.sync();
      this.controller();
      mc.execute(() -> mc.setScreen(screen.apply(mc.currentScreen)));
   }

   private void status(String message) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         mc.player.sendMessage(Text.literal("[Sound Detector] " + message), false);
      }
   }

   /** Two-way sync between these settings and the Donut preferences, which the screens also edit. */
   private void sync() {
      boolean changed = false;
      for (DonutSoundDetectorModule.Binding<?> binding : this.bindings) {
         changed |= binding.sync();
      }

      if (changed) {
         this.cfg().sanitize();
         this.config.save();
         for (DonutSoundDetectorModule.Binding<?> binding : this.bindings) {
            binding.pull();
         }
      }
   }

   private <S extends Setting, T> S bind(S setting, java.util.function.Function<S, T> uiGet, java.util.function.BiConsumer<S, T> uiSet, Supplier<T> get, Consumer<T> set) {
      this.bindings.add(new DonutSoundDetectorModule.Binding<>(setting, uiGet, uiSet, get, set));
      return setting;
   }

   private BooleanSetting toggle(String id, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
      return this.bind(this.addSetting(id, new BooleanSetting(name, get.get())), BooleanSetting::get, BooleanSetting::set, get, set);
   }

   private NumberSetting number(String id, String name, float min, float max, float step, Supplier<Number> get, Consumer<Double> set) {
      NumberSetting setting = this.addSetting(id, new NumberSetting(name, get.get().floatValue(), min, max).step(step));
      return this.bind(setting, NumberSetting::get, NumberSetting::set, () -> setting.snap(get.get().floatValue()), v -> set.accept((double)v));
   }

   private ColorSetting color(String id, String name, Supplier<Integer> get, Consumer<Integer> set) {
      return this.bind(
         this.addSetting(id, new ColorSetting(name, 0xFF000000 | get.get())),
         ColorSetting::rgb, (s, rgb) -> s.setArgb(0xFF000000 | rgb), () -> get.get() & 0xFFFFFF, v -> set.accept(v & 0xFFFFFF)
      );
   }

   private void action(String id, String name, Runnable action) {
      this.addSetting(id, new ActionSetting(name, action));
   }

   /**
    * One Anubis setting mirrored onto a Donut preference. A change on the Anubis side is written
    * into the preferences; otherwise a change in the preferences (from a screen, a preset, a profile
    * or imported JSON) is copied back into the Anubis setting.
    */
   private static final class Binding<T> {
      private final Supplier<T> ui;
      private final Consumer<T> uiSet;
      private final Supplier<T> cfg;
      private final Consumer<T> cfgSet;
      private T lastUi;
      private T lastCfg;

      <S extends Setting> Binding(S setting, java.util.function.Function<S, T> uiGet, java.util.function.BiConsumer<S, T> uiSet, Supplier<T> get, Consumer<T> set) {
         this.ui = () -> uiGet.apply(setting);
         this.uiSet = value -> uiSet.accept(setting, value);
         this.cfg = get;
         this.cfgSet = set;
         this.lastUi = this.ui.get();
         this.lastCfg = get.get();
      }

      boolean sync() {
         T now = this.ui.get();
         if (!Objects.equals(now, this.lastUi)) {
            this.cfgSet.accept(now);
            this.lastUi = now;
            this.lastCfg = this.cfg.get();
            return true;
         }

         this.pull();
         return false;
      }

      void pull() {
         T value = this.cfg.get();
         if (!Objects.equals(value, this.lastCfg)) {
            this.lastCfg = value;
            this.uiSet.accept(value);
            this.lastUi = this.ui.get();
         }
      }
   }
}
