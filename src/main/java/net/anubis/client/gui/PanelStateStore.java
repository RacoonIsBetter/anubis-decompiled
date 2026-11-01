package net.anubis.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.anubis.client.config.BundledDefaults;
import net.anubis.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PanelStateStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final PanelStateStore INSTANCE = new PanelStateStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 1;
   private final Set<String> collapsed = new LinkedHashSet<>();
   private final Map<String, float[]> homes = new LinkedHashMap<>();
   private boolean loaded;
   private boolean readOnly;

   private PanelStateStore() {
   }

   public static PanelStateStore getInstance() {
      return INSTANCE;
   }

   public synchronized boolean isCollapsed(ModuleCategory category) {
      this.ensureLoaded();
      return this.collapsed.contains(category.name());
   }

   public synchronized void setCollapsed(ModuleCategory category, boolean fold) {
      this.ensureLoaded();
      boolean changed = fold ? this.collapsed.add(category.name()) : this.collapsed.remove(category.name());
      if (changed) {
         this.save();
      }
   }

   public synchronized Map<String, float[]> panelHomes() {
      this.ensureLoaded();
      Map<String, float[]> copy = new HashMap<>();
      this.homes.forEach((id, spot) -> copy.put(id, (float[])spot.clone()));
      return copy;
   }

   public synchronized void setPanelHomes(Map<String, float[]> next) {
      this.ensureLoaded();
      this.homes.clear();
      next.forEach((id, spot) -> this.homes.put(id, (float[])spot.clone()));
      this.save();
   }

   public synchronized void load() {
      if (!this.loaded) {
         this.loaded = true;
         this.collapsed.clear();
         this.homes.clear();
         Path path = this.configPath();
         if (!Files.isRegularFile(path)) {
            this.collapsed.addAll(BundledDefaults.collapsedPanels());
         } else {
            try (Reader reader = Files.newBufferedReader(path)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               int version = root.has("version") ? root.get("version").getAsInt() : 0;
               if (version <= 1) {
                  if (root.has("collapsed") && root.get("collapsed").isJsonArray()) {
                     for (JsonElement element : root.getAsJsonArray("collapsed")) {
                        if (element.isJsonPrimitive()) {
                           this.collapsed.add(element.getAsString());
                        }
                     }
                  }

                  if (root.has("panels") && root.get("panels").isJsonObject()) {
                     for (Entry<String, JsonElement> entry : root.getAsJsonObject("panels").entrySet()) {
                        if (entry.getValue().isJsonArray() && entry.getValue().getAsJsonArray().size() == 2) {
                           JsonArray xy = entry.getValue().getAsJsonArray();
                           float x = xy.get(0).getAsFloat();
                           float y = xy.get(1).getAsFloat();
                           if (Float.isFinite(x) && Float.isFinite(y)) {
                              this.homes.put(entry.getKey(), new float[]{x, y});
                           }
                        }
                     }
                  }
               } else {
                  this.readOnly = true;
                  LOGGER.warn("[Anubis] ClickGUI state version {} is newer than supported {}; skipping load", version, 1);
               }
            } catch (IOException | RuntimeException e) {
               LOGGER.warn("[Anubis] Failed to load the ClickGUI state; preserving the file", e);
               this.collapsed.clear();
               this.homes.clear();
               this.readOnly = true;
            }
         }
      }
   }

   public synchronized void save() {
      if (!this.readOnly) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 1);
         JsonArray list = new JsonArray();

         for (String id : this.collapsed) {
            list.add(id);
         }

         root.add("collapsed", list);
         JsonObject panels = new JsonObject();
         this.homes.forEach((id, spot) -> {
            JsonArray xy = new JsonArray();
            xy.add(spot[0]);
            xy.add(spot[1]);
            panels.add(id, xy);
         });
         root.add("panels", panels);
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
            LOGGER.warn("[Anubis] Failed to save the ClickGUI state", e);
         }
      }
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         this.load();
      }
   }

   private Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("clickgui.json");
   }
}
