package net.anubis.client.easyplace.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.easyplace.EasyPlace;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class BaritoneCompat {
   private static final String[] CANDIDATES = new String[]{"baritone.api.BaritoneAPI", "baritone.BaritoneAPI"};
   private static final List<String> BUILD_SETTINGS = List.of("allowBreak", "allowPlace");
   private static Boolean available;
   private static String resolvedClass;
   private static Object cachedBaritone;
   private static boolean commanded;
   private static Map<String, String> restore = Map.of();

   private BaritoneCompat() {
   }

   public static boolean isAvailable() {
      if (available == null) {
         available = false;

         for (String name : CANDIDATES) {
            try {
               Class.forName(name);
               resolvedClass = name;
               available = true;
               break;
            } catch (ClassNotFoundException var5) {
            }
         }
      }

      return available;
   }

   public static boolean canWalk() {
      return isAvailable() || chatPrefix() != null;
   }

   public static void goTo(BlockPos feet) {
      if (isAvailable()) {
         setGoal("baritone.api.pathing.goals.GoalBlock", feet, -1);
      } else {
         command("goto " + feet.getX() + " " + feet.getY() + " " + feet.getZ());
      }
   }

   public static void goNear(BlockPos pos, int range) {
      if (!isAvailable()) {
         goTo(pos);
      } else {
         setGoal("baritone.api.pathing.goals.GoalNear", pos, range);
      }
   }

   public static void cancel() {
      if (!isAvailable()) {
         if (commanded) {
            command("stop");
         }

         commanded = false;
      } else {
         try {
            Object baritone = getBaritone();
            if (baritone == null) {
               return;
            }

            Method getPathing = findMethod(baritone, "getPathingBehavior");
            if (getPathing == null) {
               return;
            }

            Object pathingBehavior = getPathing.invoke(baritone);
            if (pathingBehavior == null) {
               return;
            }

            Method cancelMethod = findMethodByName(pathingBehavior, "cancelEverything");
            if (cancelMethod != null) {
               cancelMethod.invoke(pathingBehavior);
            }
         } catch (Exception var4) {
         }
      }
   }

   public static void lockForBuild() {
      if (restore.isEmpty()) {
         Map<String, String> saved = new HashMap<>();
         Map<String, String> file = readSettingsFile();

         for (String name : BUILD_SETTINGS) {
            saved.put(name, file.getOrDefault(name.toLowerCase(), "true"));
            setSetting(name, false);
         }

         restore = saved;
      }
   }

   public static void restoreSettings() {
      Map<String, String> saved = restore;
      restore = Map.of();
      saved.forEach((name, value) -> setSetting(name, Boolean.parseBoolean(value)));
   }

   private static void setSetting(String name, boolean value) {
      if (isAvailable()) {
         try {
            Class<?> apiClass = Class.forName(resolvedClass);
            Object settings = apiClass.getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField(name).get(settings);
            Field field = setting.getClass().getField("value");
            field.set(setting, value);
         } catch (Exception e) {
            log("setting " + name + " failed: " + e.getClass().getSimpleName());
         }
      } else {
         command("set " + name + " " + value);
      }
   }

   private static void setGoal(String goalClassName, BlockPos pos, int range) {
      try {
         Object baritone = getBaritone();
         if (baritone == null) {
            log("getBaritone returned null");
            return;
         }

         Class<?> goalClass = Class.forName(goalClassName);
         Object goal;
         if (range >= 0) {
            try {
               goal = goalClass.getConstructor(BlockPos.class, int.class).newInstance(pos, range);
            } catch (NoSuchMethodException e) {
               goal = goalClass.getConstructor(int.class, int.class, int.class, int.class).newInstance(pos.getX(), pos.getY(), pos.getZ(), range);
            }
         } else {
            try {
               goal = goalClass.getConstructor(BlockPos.class).newInstance(pos);
            } catch (NoSuchMethodException e) {
               goal = goalClass.getConstructor(int.class, int.class, int.class).newInstance(pos.getX(), pos.getY(), pos.getZ());
            }
         }

         Method getGoalProcess = findMethod(baritone, "getCustomGoalProcess");
         if (getGoalProcess == null) {
            log("getCustomGoalProcess method not found");
            return;
         }

         Object goalProcess = getGoalProcess.invoke(baritone);
         if (goalProcess == null) {
            log("getCustomGoalProcess returned null");
            return;
         }

         Method setGoal = findMethodByName(goalProcess, "setGoalAndPath");
         if (setGoal == null) {
            log("setGoalAndPath method not found");
            return;
         }

         setGoal.invoke(goalProcess, goal);
      } catch (Exception e) {
         log("goal failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
   }

   private static void command(String text) {
      String prefix = chatPrefix();
      MinecraftClient mc = MinecraftClient.getInstance();
      if (prefix != null && mc.getNetworkHandler() != null) {
         mc.getNetworkHandler().sendChatMessage(prefix + text);
         commanded = !text.equals("stop");
      }
   }

   private static String chatPrefix() {
      if (!FabricLoader.getInstance().isModLoaded("baritone")) {
         return null;
      }

      Map<String, String> settings = readSettingsFile();
      if ("false".equals(settings.get("prefixcontrol"))) {
         return null;
      }

      String prefix = settings.getOrDefault("prefix", "#");
      return !prefix.isBlank() && !prefix.startsWith("/") ? prefix : null;
   }

   private static Map<String, String> readSettingsFile() {
      Map<String, String> out = new HashMap<>();
      Path file = MinecraftClient.getInstance().runDirectory.toPath().resolve("baritone").resolve("settings.txt");

      try {
         if (!Files.isRegularFile(file)) {
            return out;
         }

         for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            int space = trimmed.indexOf(32);
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && space > 0) {
               out.put(trimmed.substring(0, space).toLowerCase(), trimmed.substring(space + 1).trim());
            }
         }
      } catch (Exception e) {
         log("could not read settings.txt: " + e.getClass().getSimpleName());
      }

      return out;
   }

   private static void log(String message) {
      EasyPlace.LOG.warn("[Baritone] {}", message);
   }

   private static Object getBaritone() throws Exception {
      if (cachedBaritone != null) {
         return cachedBaritone;
      }

      Class<?> apiClass = Class.forName(resolvedClass);
      Object provider = apiClass.getMethod("getProvider").invoke(null);
      if (provider == null) {
         return null;
      }

      Method getPrimary = findMethodByName(provider, "getPrimaryBaritone");
      if (getPrimary == null) {
         return null;
      }

      cachedBaritone = getPrimary.invoke(provider);
      return cachedBaritone;
   }

   private static Method findMethod(Object obj, String name) {
      try {
         return obj.getClass().getMethod(name);
      } catch (Exception e) {
         return null;
      }
   }

   private static Method findMethodByName(Object obj, String name) {
      for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
         for (Method m : c.getMethods()) {
            if (m.getName().equals(name)) {
               return m;
            }
         }
      }

      return null;
   }
}
