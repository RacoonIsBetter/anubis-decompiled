package net.anubis.client.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AddonManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   public static final String ENTRYPOINT = "anubis";
   private static final String CLIENT_PACKAGE = "net.anubis.client";
   private static List<AnubisAddon> addons;
   private static boolean configLoaded;

   private AddonManager() {
   }

   public static List<AnubisAddon> addons() {
      List<AnubisAddon> loaded = addons;
      return loaded != null ? loaded : List.of();
   }

   public static void init() {
      if (addons == null) {
         List<AnubisAddon> loaded = create();
         loaded.removeIf(addonx -> !run(addonx, "register its categories", AnubisAddon::onRegisterCategories));
         loaded.removeIf(addonx -> {
            if (run(addonx, "initialise", AnubisAddon::onInitialize)) {
               return false;
            }

            removeModules(addonx);
            return true;
         });

         for (AnubisAddon addon : loaded) {
            LOGGER.info(
               "[Anubis] Loaded addon {} v{} by {}",
               new Object[]{addon.name(), addon.version(), addon.authors().isEmpty() ? "unknown" : String.join(", ", addon.authors())}
            );
         }

         addons = List.copyOf(loaded);
      }
   }

   public static void configLoaded() {
      if (addons != null && !configLoaded) {
         configLoaded = true;
         List<AnubisAddon> loaded = new ArrayList<>(addons);
         loaded.removeIf(addon -> {
            if (run(addon, "start", AnubisAddon::onConfigLoaded)) {
               return false;
            }

            removeModules(addon);
            return true;
         });
         addons = List.copyOf(loaded);
      }
   }

   private static List<AnubisAddon> create() {
      List<AnubisAddon> created = new ArrayList<>();

      List<EntrypointContainer<AnubisAddon>> containers;
      try {
         containers = FabricLoader.getInstance().getEntrypointContainers("anubis", AnubisAddon.class);
      } catch (RuntimeException | LinkageError e) {
         LOGGER.error("[Anubis] Could not list addons; starting without them", e);
         return created;
      }

      for (EntrypointContainer<AnubisAddon> container : containers) {
         ModMetadata metadata = container.getProvider().getMetadata();

         AnubisAddon addon;
         String pkg;
         try {
            addon = (AnubisAddon)container.getEntrypoint();
            pkg = addon.getPackage();
         } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[Anubis] Addon {} ({}) could not be created and is skipped", new Object[]{metadata.getName(), metadata.getId(), e});
            continue;
         }

         addon.bind(metadata);
         String problem = packageProblem(addon, pkg, created);
         if (problem != null) {
            LOGGER.error("[Anubis] Addon {} is skipped: {}", addon.name(), problem);
         } else {
            addon.packageName = pkg;
            created.add(addon);
         }
      }

      return created;
   }

   private static String packageProblem(AnubisAddon addon, String pkg, List<AnubisAddon> created) {
      if (pkg != null && addon.getClass().getName().startsWith(pkg + ".")) {
         if (overlap(pkg, "net.anubis.client")) {
            return "its package " + pkg + " overlaps Anubis' own (net.anubis.client)";
         }

         for (AnubisAddon other : created) {
            if (overlap(pkg, other.packageName)) {
               return "its package "
                  + pkg
                  + " overlaps "
                  + other.name()
                  + "'s ("
                  + other.packageName
                  + "); an addon made from the template needs its package renamed";
            }
         }

         return null;
      } else {
         return "getPackage() is \"" + pkg + "\", which doesn't hold its class " + addon.getClass().getName();
      }
   }

   private static boolean overlap(String a, String b) {
      return (a + ".").startsWith(b + ".") || (b + ".").startsWith(a + ".");
   }

   private static boolean run(AnubisAddon addon, String step, Consumer<AnubisAddon> action) {
      try {
         action.accept(addon);
         return true;
      } catch (RuntimeException | LinkageError e) {
         LOGGER.error("[Anubis] Addon {} failed to {} and is skipped{}", new Object[]{addon.name(), step, reportHint(addon), e});
         return false;
      }
   }

   private static String reportHint(AnubisAddon addon) {
      try {
         AnubisAddon.GithubRepo repo = addon.getRepo();
         String where = repo != null ? repo.url() + "/issues" : addon.getWebsite();
         return where != null ? " - report it at " + where : "";
      } catch (RuntimeException | LinkageError e) {
         return "";
      }
   }

   private static void removeModules(AnubisAddon addon) {
      String prefix = addon.packageName + ".";

      for (ClientModule module : ModuleRegistry.all()) {
         if (module.getClass().getName().startsWith(prefix)) {
            ModuleConfigStore.getInstance().release(module);
            if (module.isEnabled()) {
               module.setEnabled(false);
            }

            ModuleRegistry.remove(module);
         }
      }
   }
}
