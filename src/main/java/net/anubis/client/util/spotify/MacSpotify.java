package net.anubis.client.util.spotify;

import java.io.IOException;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class MacSpotify extends ScriptedPlayer {
   private static final long TIMEOUT_MS = 2000L;
   private static final String APP_BINARY = "/Contents/MacOS/Spotify";
   private static final String NOT_RUNNING = "not running";
   private static final String STOPPED = "stopped";
   private static final char FIELD = '\u001f';
   private static final int FIELDS = 7;
   private static final String[] READ = new String[]{
      "osascript",
      "-e",
      "if application \"Spotify\" is not running then return \"not running\"",
      "-e",
      "tell application \"Spotify\"",
      "-e",
      "set s to (player state) as text",
      "-e",
      "if s is \"stopped\" then return \"stopped\"",
      "-e",
      "set t to current track",
      "-e",
      "set d to character id 31",
      "-e",
      "return s & d & (name of t) & d & (artist of t) & d & (album of t) & d & (duration of t) & d & (player position) & d & (artwork url of t)",
      "-e",
      "end tell"
   };

   @Override
   protected ScriptedPlayer.Reading read() throws IOException, InterruptedException {
      if (!appRunning()) {
         return null;
      }

      String reply = run(2000L, READ).strip();
      if (reply.equals("not running")) {
         return null;
      }

      if (reply.equals("stopped")) {
         return new ScriptedPlayer.Reading(MediaSession.PlaybackState.STOPPED, "", "", "", 0L, 0L, "");
      }

      String[] parts = reply.split(String.valueOf('\u001f'), -1);
      if (parts.length < 7) {
         throw new IOException("unexpected Spotify reply: " + reply);
      }

      MediaSession.PlaybackState state = parts[0].equals("playing") ? MediaSession.PlaybackState.PLAYING : MediaSession.PlaybackState.PAUSED;
      return new ScriptedPlayer.Reading(
         state,
         parts[1].strip(),
         parts[2].strip(),
         parts[3].strip(),
         Math.max(0L, (long)number(parts[4])),
         Math.max(0L, Math.round(number(parts[5]) * 1000.0)),
         parts[6].strip()
      );
   }

   @Override
   protected boolean send(ScriptedPlayer.Command command) throws IOException, InterruptedException {
      if (!appRunning()) {
         return false;
      }

      String verb = switch (command) {
         case PLAY_PAUSE -> "playpause";
         case NEXT -> "next track";
         case PREVIOUS -> "previous track";
      };
      run(2000L, "osascript", "-e", "if application \"Spotify\" is running then tell application \"Spotify\" to " + verb);
      return true;
   }

   private static boolean appRunning() {
      return ProcessHandle.allProcesses().anyMatch(process -> {
         Optional<String> command = process.info().command();
         return command.isPresent() && command.get().endsWith("/Contents/MacOS/Spotify");
      });
   }

   private static double number(String text) {
      try {
         return Double.parseDouble(text.strip().replace(',', '.'));
      } catch (NumberFormatException unreadable) {
         return 0.0;
      }
   }
}
