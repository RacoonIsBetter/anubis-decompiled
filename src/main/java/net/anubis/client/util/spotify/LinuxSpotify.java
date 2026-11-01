package net.anubis.client.util.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LinuxSpotify extends ScriptedPlayer {
   private static final long TIMEOUT_MS = 2000L;
   private static final long LOOKUP_GAP_MS = 2000L;
   private static final String BUS_PREFIX = "org.mpris.MediaPlayer2.spotify";
   private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
   private static final String PLAYER = "org.mpris.MediaPlayer2.Player";
   private String busName = "org.mpris.MediaPlayer2.spotify";
   private long nextLookupAt;

   @Override
   protected ScriptedPlayer.Reading read() throws IOException, InterruptedException {
      String name = this.busName(false);
      if (name == null) {
         return null;
      }

      String reply;
      try {
         reply = run(
            2000L,
            "dbus-send",
            "--session",
            "--print-reply",
            "--dest=" + name,
            "/org/mpris/MediaPlayer2",
            "org.freedesktop.DBus.Properties.GetAll",
            "string:org.mpris.MediaPlayer2.Player"
         );
      } catch (ScriptedPlayer.CommandFailed failure) {
         if (!gone(failure)) {
            throw failure;
         }

         this.busName = null;
         return null;
      }

      if (LinuxSpotify.DbusReply.parse(reply) instanceof Map<?, ?> properties) {
         Map<?, ?> metadata = properties.get("Metadata") instanceof Map<?, ?> map ? map : Map.of();
         String status = text(properties.get("PlaybackStatus"));

         MediaSession.PlaybackState state = switch (status) {
            case "Playing" -> MediaSession.PlaybackState.PLAYING;
            case "Paused" -> MediaSession.PlaybackState.PAUSED;
            default -> MediaSession.PlaybackState.STOPPED;
         };
         return new ScriptedPlayer.Reading(
            state,
            text(metadata.get("xesam:title")),
            text(metadata.get("xesam:artist")),
            text(metadata.get("xesam:album")),
            micros(metadata.get("mpris:length")),
            micros(properties.get("Position")),
            text(metadata.get("mpris:artUrl"))
         );
      } else {
         throw new IOException("unexpected MPRIS reply");
      }
   }

   @Override
   protected boolean send(ScriptedPlayer.Command command) throws IOException, InterruptedException {
      String name = this.busName(true);
      if (name == null) {
         return false;
      }

      String method = switch (command) {
         case PLAY_PAUSE -> "PlayPause";
         case NEXT -> "Next";
         case PREVIOUS -> "Previous";
      };

      try {
         run(2000L, "dbus-send", "--session", "--print-reply", "--dest=" + name, "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player." + method);
         return true;
      } catch (ScriptedPlayer.CommandFailed failure) {
         if (!gone(failure)) {
            throw failure;
         }

         this.busName = null;
         return false;
      }
   }

   private String busName(boolean now) throws IOException, InterruptedException {
      if (this.busName != null) {
         return this.busName;
      }

      long time = System.currentTimeMillis();
      if (!now && time < this.nextLookupAt) {
         return null;
      }

      this.nextLookupAt = time + 2000L;
      String reply = run(
         2000L, "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames"
      );
      Object var6 = LinuxSpotify.DbusReply.parse(reply);
      if (var6 instanceof List) {
         for (Object entry : (List)var6) {
            if (entry instanceof String candidate && candidate.startsWith("org.mpris.MediaPlayer2.spotify")) {
               this.busName = candidate;
               return candidate;
            }
         }
      }

      return null;
   }

   private static boolean gone(ScriptedPlayer.CommandFailed failure) {
      String message = failure.getMessage();
      return message != null && (message.contains("ServiceUnknown") || message.contains("NameHasNoOwner"));
   }

   private static String text(Object value) {
      if (value instanceof String string) {
         return string.strip();
      } else if (value instanceof List<?> list) {
         List<String> parts = new ArrayList<>();

         for (Object entry : list) {
            String part = text(entry);
            if (!part.isEmpty()) {
               parts.add(part);
            }
         }

         return String.join(", ", parts);
      } else {
         return "";
      }
   }

   private static long micros(Object value) {
      return value instanceof Number number ? Math.max(0L, number.longValue() / 1000L) : 0L;
   }

   @Environment(EnvType.CLIENT)
   static final class DbusReply {
      private final String[] lines;
      private int at;

      private DbusReply(String[] lines) {
         this.lines = lines;
      }

      static Object parse(String output) {
         String[] lines = output.strip().split("\\R");
         LinuxSpotify.DbusReply reply = new LinuxSpotify.DbusReply(lines);
         if (lines.length > 0 && lines[0].startsWith("method return")) {
            reply.at = 1;
         }

         return reply.at < lines.length ? reply.value(reply.next()) : null;
      }

      private String next() {
         return this.at < this.lines.length ? this.lines[this.at++].strip() : "]";
      }

      private Object value(String line) {
         if (line.startsWith("variant")) {
            line = line.substring("variant".length()).strip();
         }

         if (line.equals("array [")) {
            return this.array();
         }

         if (!line.startsWith("string ") && !line.startsWith("object path ")) {
            int space = line.lastIndexOf(32);
            String type = space < 0 ? line : line.substring(0, space);
            String raw = space < 0 ? "" : line.substring(space + 1);

            try {
               return switch (type) {
                  case "int64", "uint64", "int32", "uint32", "int16", "uint16", "byte" -> Long.parseLong(raw);
                  case "double" -> Double.parseDouble(raw);
                  case "boolean" -> Boolean.parseBoolean(raw);
                  default -> line;
               };
            } catch (NumberFormatException unreadable) {
               return line;
            }
         } else {
            return unquote(line);
         }
      }

      private Object array() {
         List<Object> items = new ArrayList<>();
         Map<String, Object> entries = new LinkedHashMap<>();

         while (this.at < this.lines.length) {
            String line = this.next();
            if (line.equals("]")) {
               break;
            }

            if (line.equals("dict entry(")) {
               Object key = this.value(this.next());
               Object value = this.value(this.next());
               this.next();
               if (key instanceof String name) {
                  entries.put(name, value);
               }
            } else {
               items.add(this.value(line));
            }
         }

         return entries.isEmpty() ? items : entries;
      }

      private static String unquote(String line) {
         int first = line.indexOf(34);
         int last = line.lastIndexOf(34);
         return first >= 0 && last > first ? line.substring(first + 1, last).replace("\\\"", "\"") : "";
      }
   }
}
