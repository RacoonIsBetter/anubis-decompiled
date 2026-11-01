package net.anubis.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.imgui.UiFont;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ThemeStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final ThemeStore INSTANCE = new ThemeStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 4;
   private static final String THEME_KEY = "anubisTheme";
   private static final String DEFAULT_BANNER = "donutbets-banner.png";
   private static final String BANNER_RESOURCE = "/assets/anubis/textures/gui/donutbets_banner.png";
   public static final List<ThemeStore.Preset> PRESETS = List.of(
      new ThemeStore.Preset("Default", -13338378, -14736856, -15263458),
      new ThemeStore.Preset("Midnight", -7643914, -15001050, -15461603),
      new ThemeStore.Preset("Rose", -770210, -14410720, -15002344),
      new ThemeStore.Preset("Mint", -15681151, -15064288, -15459560),
      new ThemeStore.Preset("Amber", -680437, -14409445, -15001324),
      new ThemeStore.Preset("Mono", -1710101, -14671840, -15263977)
   );
   public final ColorSetting accent = new ColorSetting("Accent", -13338378);
   public final NumberSetting rounding = new NumberSetting("Corner radius", 16.0F, 0.0F, 16.0F).step(1.0F);
   public final NumberSetting backgroundDim = new NumberSetting("Background dim", 0.55F, 0.0F, 0.8F).step(0.05F);
   public final BooleanSetting menuBlur = new BooleanSetting("Menu blur", true);
   public final BooleanSetting moduleDividers = new BooleanSetting("Module dividers", true);
   public final NumberSetting uiScale = new NumberSetting("UI scale", 1.0F, 0.75F, 1.5F).step(0.05F);
   public final ModeSetting font = new ModeSetting("Font", UiFont.GEIST.label, UiFont.labels());
   public final NumberSetting animationSpeed = new NumberSetting("Animation speed", 1.3F, 0.0F, 2.0F).step(0.1F);
   public final BooleanSetting animateLines = new BooleanSetting("Animate patterns", true);
   public final BooleanSetting customMainMenu = new BooleanSetting("Custom menus", true);
   public final BooleanSetting communityPopup = new BooleanSetting("Community popup", true);
   public final Surface panels = new Surface("panels", "Panels", false, -14736856, 1.0F, 0.45F, "Flow");
   public final Surface cards = new Surface("cards", "Cards", true, -15263458, 1.0F, 0.85F);
   public final Surface tabBar = new Surface("tabbar", "Tab bar", false, -15263458, 1.0F, 0.5F, "Flow");
   private final List<Surface> surfaces = List.of(this.panels, this.cards, this.tabBar);
   private final List<Setting> shared;
   private final List<Setting> settings;
   private final JsonObject defaults;
   private boolean loaded;
   private boolean readOnly;

   private ThemeStore() {
      List<Setting> all = new ArrayList<>(
         List.of(
            this.accent, this.rounding, this.backgroundDim, this.menuBlur, this.moduleDividers, this.uiScale, this.font, this.animationSpeed, this.animateLines
         )
      );

      for (Surface surface : this.surfaces) {
         all.addAll(surface.settings());
      }

      this.shared = List.copyOf(all);
      all.add(this.customMainMenu);
      all.add(this.communityPopup);
      this.settings = List.copyOf(all);
      useBanner(this.cards);
      this.defaults = snapshot(this.settings, true);
      this.customMainMenu.onChange(() -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null && mc.getWindow() != null) {
            mc.updateWindowTitle();
         }
      });
   }

   public static ThemeStore get() {
      INSTANCE.ensureLoaded();
      return INSTANCE;
   }

   public List<Surface> surfaces() {
      return this.surfaces;
   }

   public void applyPreset(ThemeStore.Preset preset) {
      this.accent.setArgb(preset.accent());
      this.panels.color.setArgb(preset.panel());
      this.cards.color.setArgb(preset.card());
      this.tabBar.color.setArgb(preset.card());
   }

   public boolean matchesPreset(ThemeStore.Preset preset) {
      return this.accent.rgb() == (preset.accent() & 16777215)
         && this.panels.color.rgb() == (preset.panel() & 16777215)
         && this.cards.color.rgb() == (preset.card() & 16777215);
   }

   public void resetToDefaults() {
      apply(this.shared, this.defaults, true);
      this.installBanner();
   }

   public static Path backgroundsDir() {
      return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("backgrounds");
   }

   public String exportString() {
      JsonObject root = new JsonObject();
      root.add("anubisTheme", snapshot(this.shared, false));
      return root.toString();
   }

   public boolean importString(String text) {
      if (text != null && !text.isBlank()) {
         try {
            JsonElement parsed = JsonParser.parseString(text.trim());
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("anubisTheme")) {
               JsonElement values = parsed.getAsJsonObject().get("anubisTheme");
               if (!values.isJsonObject()) {
                  return false;
               }

               apply(this.shared, values.getAsJsonObject(), false);
               return true;
            } else {
               return false;
            }
         } catch (RuntimeException e) {
            return false;
         }
      } else {
         return false;
      }
   }

   public JsonObject profileSnapshot() {
      return snapshot(this.shared, true);
   }

   public void applyProfile(JsonObject values) {
      for (Setting setting : this.shared) {
         JsonElement value = values.get(setting.id());
         if (value == null && !setting.isPrivate()) {
            value = this.defaults.get(setting.id());
         }

         if (value != null) {
            setting.deserialize(value);
         }
      }
   }

   public JsonObject shareable(JsonObject values) {
      JsonObject kept = new JsonObject();

      for (Setting setting : this.shared) {
         JsonElement value = values.get(setting.id());
         if (value != null && !setting.isPrivate() && !Setting.sameValue(value, this.defaults.get(setting.id()))) {
            kept.add(setting.id(), value.deepCopy());
         }
      }

      return kept;
   }

   public synchronized void load() {
      if (!this.loaded) {
         this.loaded = true;
         this.readFile();
         this.installBanner();
      }
   }

   private void readFile() {
      Path path = this.configPath();
      if (Files.isRegularFile(path)) {
         try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version <= 4) {
               if (root.has("values") && root.get("values").isJsonObject()) {
                  apply(this.settings, root.getAsJsonObject("values"), true);
               }

               if (version < 3) {
                  this.adoptNewDefaults();
               }

               if (version < 4) {
                  this.adoptBanner();
               }
            } else {
               this.readOnly = true;
               LOGGER.warn("[Anubis] Theme version {} is newer than supported {}; skipping load", version, 4);
            }
         } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Anubis] Failed to load theme; preserving the file", e);
            this.readOnly = true;
         }
      }
   }

   private void adoptNewDefaults() {
      if (this.panels.background.is("None")) {
         this.panels.background.set("Flow");
      }

      if (Math.abs(this.panels.opacity.get() - 0.92F) < 0.001F) {
         this.panels.opacity.set(1.0F);
      }

      for (Surface surface : List.of(this.cards, this.tabBar)) {
         if (Math.abs(surface.opacity.get() - 0.97F) < 0.001F) {
            surface.opacity.set(1.0F);
         }
      }
   }

   private void adoptBanner() {
      if (this.cards.background.is("None") && this.cards.image.get().isBlank()) {
         useBanner(this.cards);
      }
   }

   private static void useBanner(Surface surface) {
      surface.background.set("Image");
      surface.image.set("donutbets-banner.png");
      surface.coverage.set("Header");
      surface.headerHeight.set(157.0F);
      surface.imageOpacity.set(1.0F);
      surface.imageScale.set(1.1F);
      surface.imageX.set(0.6F);
      surface.imageY.set(0.55F);
   }

   private void installBanner() {
      if ("donutbets-banner.png".equals(this.cards.image.get())) {
         Path target = backgroundsDir().resolve("donutbets-banner.png");
         if (!Files.isRegularFile(target)) {
            try (InputStream in = ThemeStore.class.getResourceAsStream("/assets/anubis/textures/gui/donutbets_banner.png")) {
               if (in != null) {
                  Files.createDirectories(target.getParent());
                  Files.copy(in, target);
               }
            } catch (IOException e) {
               LOGGER.warn("[Anubis] Could not install the default card banner", e);
            }
         }
      }
   }

   public synchronized void save() {
      if (!this.readOnly && this.loaded) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 4);
         root.add("values", snapshot(this.settings, true));
         Path path = this.configPath();

         try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp)) {
               GSON.toJson(root, writer);
            }

            try {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException e) {
            LOGGER.warn("[Anubis] Failed to save theme", e);
         }
      }
   }

   private static JsonObject snapshot(List<Setting> from, boolean withPrivate) {
      JsonObject values = new JsonObject();

      for (Setting setting : from) {
         if (withPrivate || !setting.isPrivate()) {
            values.add(setting.id(), setting.serialize());
         }
      }

      return values;
   }

   private static void apply(List<Setting> from, JsonObject values, boolean withPrivate) {
      for (Setting setting : from) {
         if ((withPrivate || !setting.isPrivate()) && values.has(setting.id())) {
            setting.deserialize(values.get(setting.id()));
         }
      }
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         this.load();
      }
   }

   private Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("theme.json");
   }

   @Environment(EnvType.CLIENT)
   public record Preset(String name, int accent, int panel, int card) {
   }
}
