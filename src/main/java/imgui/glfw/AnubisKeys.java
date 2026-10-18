package imgui.glfw;

import imgui.AnubisAndroidNative;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AnubisKeys {
   private static final boolean ANDROID = AnubisAndroidNative.isAndroidRuntime();
   private static final int FIRST_KEY = 32;
   private static final int LAST_KEY = 348;
   private static final Map<Long, AnubisKeys.State> STATES = new HashMap<>();
   private static final AnubisKeys.KeyReader GLFW_READER = GLFW::glfwGetKey;

   private AnubisKeys() {
   }

   public static boolean isAndroid() {
      return ANDROID;
   }

   public static int getKey(long window, int key) {
      return getKey(window, key, ANDROID, GLFW_READER);
   }

   public static void record(long window, int key, int action) {
      record(window, key, action, ANDROID);
   }

   public static void clear(long window) {
      synchronized (STATES) {
         STATES.remove(window);
      }
   }

   static int getKey(long window, int key, boolean android, AnubisKeys.KeyReader reader) {
      if (!android) {
         return reader.get(window, key);
      }

      if (key >= 32 && key <= 348) {
         synchronized (STATES) {
            AnubisKeys.State state = STATES.get(window);
            if (state != null && state.unavailable[key]) {
               return state.down[key] ? 1 : 0;
            }
         }

         try {
            int action = reader.get(window, key);
            return action != 1 && action != 2 ? 0 : 1;
         } catch (IndexOutOfBoundsException missingSlot) {
            synchronized (STATES) {
               AnubisKeys.State state = state(window);
               state.unavailable[key] = true;
               return state.down[key] ? 1 : 0;
            }
         }
      } else {
         return 0;
      }
   }

   static void record(long window, int key, int action, boolean android) {
      if (android && key >= 32 && key <= 348) {
         if (action == 1 || action == 0 || action == 2) {
            synchronized (STATES) {
               state(window).down[key] = action != 0;
            }
         }
      }
   }

   private static AnubisKeys.State state(long window) {
      AnubisKeys.State value = STATES.get(window);
      if (value == null) {
         value = new AnubisKeys.State();
         STATES.put(window, value);
      }

      return value;
   }

   @Environment(EnvType.CLIENT)
   interface KeyReader {
      int get(long var1, int var3);
   }

   @Environment(EnvType.CLIENT)
   private static final class State {
      final boolean[] down = new boolean[349];
      final boolean[] unavailable = new boolean[349];
   }
}
