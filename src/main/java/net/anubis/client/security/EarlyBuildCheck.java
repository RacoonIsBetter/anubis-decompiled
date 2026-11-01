package net.anubis.client.security;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

@Environment(EnvType.CLIENT)
public final class EarlyBuildCheck implements PreLaunchEntrypoint {
   public void onPreLaunch() {
      OfficialBuild.checkBeforeLaunch();
   }
}
