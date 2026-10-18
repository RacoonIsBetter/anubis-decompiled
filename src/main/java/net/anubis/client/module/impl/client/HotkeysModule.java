package net.anubis.client.module.impl.client;

import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HotkeysModule extends ClientModule {
   public HotkeysModule() {
      super("hotkeys", "Hotkeys", "Lists the modules you have bound to a key, and their keys.", ModuleCategory.CLIENT);
   }

   public static HotkeysModule get() {
      return ModuleRegistry.get(HotkeysModule.class);
   }

   public static boolean shouldShowHud() {
      HotkeysModule module = get();
      return module != null && module.isEnabled();
   }
}
