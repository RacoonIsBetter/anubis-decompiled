package net.anubis.client.module.impl.render;

import net.anubis.client.config.ThemeStore;
import net.anubis.client.gui.ThemeTint;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.client.HudModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class CosmeticAccent {
   private CosmeticAccent() {
   }

   static int argb() {
      HudModule hud = ModuleRegistry.get(HudModule.class);
      int argb = hud != null && hud.customAccent() ? hud.accentArgb() : ThemeStore.get().accent.argb();
      return ThemeTint.accent(argb) | 0xFF000000;
   }
}
