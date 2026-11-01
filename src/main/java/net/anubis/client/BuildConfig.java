package net.anubis.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BuildConfig {
   public static final boolean DEBUG = false;
   // The official release has the SHA-256 of its signing certificate here
   // (c0f62a59d9785f4d2f026cdd71f4ac272676dbd149e32473c2624711f3470e79). Empty means an unsigned
   // local build, which OfficialBuild does not check; otherwise every build of this source would
   // be stopped as "modified".
   public static final String[] SIGN_CERT_PARTS = new String[0];

   private BuildConfig() {
   }
}
