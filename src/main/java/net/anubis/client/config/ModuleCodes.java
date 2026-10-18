package net.anubis.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleCodes {
   private static final String PREFIX = "AnubisModule:";
   private static final int FORMAT = 2;

   private ModuleCodes() {
   }

   public static String copy(ClientModule module) {
      JsonObject settings = new JsonObject();

      for (Setting setting : module.settings()) {
         if (!setting.isPrivate() && !setting.isDefault()) {
            settings.add(setting.id(), setting.compact(setting.serialize()));
         }
      }

      JsonObject root = new JsonObject();
      root.addProperty("format", 2);
      root.addProperty("module", module.id());
      if (module.settingsVersion() > 0) {
         root.addProperty("settingsVersion", module.settingsVersion());
      }

      root.add("settings", settings);
      return ProfileCodes.encode(root, "AnubisModule:");
   }

   public static void paste(ClientModule module, String text) throws ProfileException {
      if (!module.inConfigs()) {
         throw new ProfileException(module.name() + " can't be set by configs");
      }

      if (!ProfileCodes.contains(text, "AnubisModule:") && ProfileCodes.contains(text, "AnubisProfile:")) {
         throw new ProfileException("That's a whole profile, paste it in the Config tab");
      }

      JsonObject root = ProfileCodes.decode(text, "AnubisModule:", "No module config on the clipboard");
      int format = 0;
      if (root.has("format") && root.get("format").isJsonPrimitive()) {
         try {
            format = root.get("format").getAsInt();
         } catch (RuntimeException var10) {
         }
      }

      if (format < 2) {
         throw new ProfileException("Made by an old Anubis - old configs no longer work");
      }

      String id = root.has("module") && root.get("module").isJsonPrimitive() ? root.get("module").getAsString() : "";
      if (!module.id().equals(id)) {
         ClientModule owner = id.isEmpty() ? null : ModuleRegistry.resolve(id);
         throw new ProfileException("That config is for " + (owner != null ? owner.name() : "another module"));
      }

      JsonObject settings = root.has("settings") && root.get("settings").isJsonObject() ? root.getAsJsonObject("settings") : new JsonObject();

      for (Setting setting : module.settings()) {
         if (!setting.isPrivate()) {
            JsonElement value = settings.get(setting.id());
            if (value != null) {
               setting.deserialize(value);
            } else {
               setting.resetToDefault();
            }
         }
      }

      int current = module.settingsVersion();
      int saved = 0;
      if (root.has("settingsVersion") && root.get("settingsVersion").isJsonPrimitive()) {
         try {
            saved = root.get("settingsVersion").getAsInt();
         } catch (RuntimeException var9) {
         }
      }

      if (current > 0 && saved < current) {
         module.migrateSettings(saved);
      }
   }
}
