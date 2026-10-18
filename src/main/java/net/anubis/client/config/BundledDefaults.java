package net.anubis.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class BundledDefaults {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String ROOT = "/assets/anubis/defaults/";
   private static JsonObject modules;
   private static JsonObject clickGui;
   private static List<JsonObject> presets;

   private BundledDefaults() {
   }

   public static synchronized void adopt(List<ClientModule> registered) {
      for (ClientModule module : registered) {
         JsonObject entry = entry(module);
         if (entry != null && module.inConfigs()) {
            try {
               JsonObject settings = object(entry, "settings");
               if (settings != null) {
                  for (Setting setting : module.settings()) {
                     JsonElement value = settings.get(setting.id());
                     if (value != null && !setting.isPrivate()) {
                        setting.adoptDefault(value);
                     }
                  }
               }

               if (entry.has("keybind")) {
                  int key = entry.get("keybind").getAsInt();
                  if (ModuleKeybinds.isBindable(key)) {
                     module.setKeybind(key);
                  }
               }

               if (entry.has("holdMode")) {
                  module.setHoldMode(entry.get("holdMode").getAsBoolean());
               }
            } catch (RuntimeException e) {
               LOGGER.warn("[Anubis] Failed to apply bundled defaults of '{}'", module.id(), e);
            }
         }
      }
   }

   public static synchronized boolean enabledByDefault(ClientModule module) {
      JsonObject entry = module.inConfigs() ? entry(module) : null;
      JsonElement enabled = entry == null ? null : entry.get("enabled");
      if (enabled != null && enabled.isJsonPrimitive()) {
         try {
            return enabled.getAsBoolean();
         } catch (RuntimeException e) {
            return module.enabledByDefault();
         }
      } else {
         return module.enabledByDefault();
      }
   }

   public static synchronized float[] panelSpot(String panel, float screenW, float screenH) {
      JsonObject layout = clickGui();
      JsonObject panels = object(layout, "panels");
      JsonElement screen = layout.get("screen");
      if (panels != null && screen != null && screen.isJsonArray() && panels.has(panel)) {
         try {
            JsonArray reference = screen.getAsJsonArray();
            JsonArray spot = panels.getAsJsonArray(panel);
            return new float[]{
               spot.get(0).getAsFloat() * screenW / reference.get(0).getAsFloat(), spot.get(1).getAsFloat() * screenH / reference.get(1).getAsFloat()
            };
         } catch (RuntimeException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static synchronized Set<String> collapsedPanels() {
      Set<String> collapsed = new LinkedHashSet<>();
      JsonElement list = clickGui().get("collapsed");
      if (list != null && list.isJsonArray()) {
         for (JsonElement element : list.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
               collapsed.add(element.getAsString());
            }
         }

         return collapsed;
      } else {
         return collapsed;
      }
   }

   public static synchronized List<JsonObject> presets() {
      if (presets == null) {
         List<JsonObject> list = new ArrayList<>();
         JsonElement array = read("presets.json").get("presets");
         if (array != null && array.isJsonArray()) {
            for (JsonElement element : array.getAsJsonArray()) {
               if (element.isJsonObject()) {
                  list.add(element.getAsJsonObject());
               }
            }
         }

         presets = List.copyOf(list);
      }

      List<JsonObject> copies = new ArrayList<>(presets.size());

      for (JsonObject preset : presets) {
         copies.add(preset.deepCopy());
      }

      return copies;
   }

   private static JsonObject entry(ClientModule module) {
      JsonObject all = object(modules(), "modules");
      return all == null ? null : object(all, module.id());
   }

   private static JsonObject modules() {
      if (modules == null) {
         modules = read("modules.json");
      }

      return modules;
   }

   private static JsonObject clickGui() {
      if (clickGui == null) {
         clickGui = read("clickgui.json");
      }

      return clickGui;
   }

   private static JsonObject object(JsonObject parent, String key) {
      JsonElement value = parent.get(key);
      return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
   }

   private static JsonObject read(String name) {
      try (InputStream in = BundledDefaults.class.getResourceAsStream("/assets/anubis/defaults/" + name)) {
         if (in == null) {
            return new JsonObject();
         }

         try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
         }
      } catch (Exception e) {
         LOGGER.warn("[Anubis] Failed to read bundled defaults {}", name, e);
         return new JsonObject();
      }
   }
}
