package net.anubis.bootstrap;

import imgui.AnubisAndroidNative;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class AnubisNativeBootstrap implements PreLaunchEntrypoint {
   public void onPreLaunch() {
      AnubisAndroidNative.prepare();
   }
}
