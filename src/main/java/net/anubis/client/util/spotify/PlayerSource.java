package net.anubis.client.util.spotify;

import com.sun.jna.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface PlayerSource {
   MediaSession.Snapshot poll();

   boolean togglePlayPause();

   boolean skipNext();

   boolean skipPrevious();

   String lastError();

   boolean isUnsupported();

   void close();

   static boolean supported() {
      return Platform.isWindows() || Platform.isMac() || Platform.isLinux();
   }

   static PlayerSource spotify() {
      if (Platform.isMac()) {
         return new MacSpotify();
      } else {
         return Platform.isLinux() ? new LinuxSpotify() : MediaSession.spotify();
      }
   }
}
