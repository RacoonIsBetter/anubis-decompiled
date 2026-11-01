package net.anubis.client.util.spotify;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
abstract class ScriptedPlayer implements PlayerSource {
   private static final long ART_TIMEOUT_MS = 3000L;
   private static final long ART_RETRY_MS = 10000L;
   private static final int ART_MAX_BYTES = 4194304;
   private volatile boolean closed;
   private volatile boolean unsupported;
   private volatile String lastError;
   private HttpClient http;
   private ExecutorService httpExecutor;
   private String trackKey;
   private long estimateMs;
   private long estimateAt;
   private boolean wasPlaying;
   private String artUrl = "";
   private long artRetryAt;
   private byte[] art;
   private int artVersion;

   protected abstract ScriptedPlayer.Reading read() throws IOException, InterruptedException;

   protected abstract boolean send(ScriptedPlayer.Command var1) throws IOException, InterruptedException;

   @Override
   public MediaSession.Snapshot poll() {
      if (!this.closed && !this.unsupported) {
         try {
            ScriptedPlayer.Reading reading = this.read();
            this.lastError = null;
            if (reading == null) {
               this.forgetTrack();
               return null;
            }

            return this.snapshot(reading);
         } catch (ScriptedPlayer.MissingTool missing) {
            this.unsupported = true;
            this.lastError = missing.getMessage();
         } catch (IOException failure) {
            this.lastError = failure.getMessage();
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         } catch (RuntimeException failure) {
            this.lastError = failure.toString();
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public boolean togglePlayPause() {
      return this.command(ScriptedPlayer.Command.PLAY_PAUSE);
   }

   @Override
   public boolean skipNext() {
      return this.command(ScriptedPlayer.Command.NEXT);
   }

   @Override
   public boolean skipPrevious() {
      return this.command(ScriptedPlayer.Command.PREVIOUS);
   }

   @Override
   public String lastError() {
      return this.lastError;
   }

   @Override
   public boolean isUnsupported() {
      return this.unsupported;
   }

   @Override
   public void close() {
      this.closed = true;
      HttpClient client = this.http;
      ExecutorService executor = this.httpExecutor;
      this.http = null;
      this.httpExecutor = null;
      if (client != null) {
         client.shutdownNow();
      }

      if (executor != null) {
         executor.shutdownNow();
      }
   }

   private boolean command(ScriptedPlayer.Command command) {
      if (!this.closed && !this.unsupported) {
         try {
            boolean accepted = this.send(command);
            this.lastError = null;
            return accepted;
         } catch (ScriptedPlayer.MissingTool missing) {
            this.unsupported = true;
            this.lastError = missing.getMessage();
         } catch (IOException failure) {
            this.lastError = failure.getMessage();
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         } catch (RuntimeException failure) {
            this.lastError = failure.toString();
         }

         return false;
      } else {
         return false;
      }
   }

   private MediaSession.Snapshot snapshot(ScriptedPlayer.Reading reading) {
      long now = System.currentTimeMillis();
      String key = reading.title() + "\n" + reading.artist() + "\n" + reading.album();
      boolean newTrack = !key.equals(this.trackKey);
      this.trackKey = key;
      boolean playing = reading.state() == MediaSession.PlaybackState.PLAYING;
      if (newTrack) {
         this.estimateMs = Math.max(0L, reading.positionMs());
      } else if (this.wasPlaying) {
         this.estimateMs = this.estimateMs + Math.max(0L, now - this.estimateAt);
      }

      this.estimateAt = now;
      this.wasPlaying = playing;
      if (reading.positionMs() > 0L) {
         this.estimateMs = reading.positionMs();
      }

      long position = this.estimateMs;
      if (reading.durationMs() > 0L) {
         position = Math.min(position, reading.durationMs());
      }

      this.updateArt(reading.artUrl(), now);
      return new MediaSession.Snapshot(
         reading.title(),
         reading.artist(),
         reading.album(),
         reading.durationMs(),
         position,
         now,
         reading.state(),
         1.0,
         this.art,
         this.art == null ? "" : "image",
         this.artVersion
      );
   }

   private void forgetTrack() {
      this.trackKey = null;
      this.wasPlaying = false;
      this.estimateMs = 0L;
   }

   private void updateArt(String url, long now) {
      String wanted = url == null ? "" : url.strip();
      if (!wanted.equals(this.artUrl) || this.art == null && !wanted.isEmpty() && now >= this.artRetryAt) {
         this.artUrl = wanted;
         byte[] bytes = wanted.isEmpty() ? null : this.download(wanted);
         if (bytes == null && !wanted.isEmpty()) {
            this.artRetryAt = now + 10000L;
         }

         if (bytes != null || this.art != null) {
            this.art = bytes;
            this.artVersion++;
         }
      }
   }

   private byte[] download(String url) {
      URI uri;
      try {
         uri = URI.create(url);
      } catch (IllegalArgumentException malformed) {
         return null;
      }

      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!scheme.equals("https") && !scheme.equals("http")) {
         return null;
      }

      try {
         HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(3000L)).GET().build();
         HttpResponse<byte[]> response = this.http().send(request, BodyHandlers.ofByteArray());
         byte[] body = response.body();
         return response.statusCode() == 200 && body != null && body.length != 0 && body.length <= 4194304 ? body : null;
      } catch (IOException failure) {
         this.lastError = "album art: " + failure.getMessage();
         return null;
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
         return null;
      }
   }

   private HttpClient http() {
      if (this.http == null) {
         this.httpExecutor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "Anubis-Spotify-Art");
            thread.setDaemon(true);
            return thread;
         });
         this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(3000L)).followRedirects(Redirect.NORMAL).executor(this.httpExecutor).build();
      }

      return this.http;
   }

   static String run(long timeoutMs, String... command) throws IOException, InterruptedException {
      Process process;
      try {
         process = new ProcessBuilder(command).start();
      } catch (IOException missing) {
         throw new ScriptedPlayer.MissingTool(command[0] + " is not available");
      }

      try {
         process.getOutputStream().close();
         if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IOException(command[0] + " did not answer within " + timeoutMs + " ms");
         } else {
            String out = readAll(process.getInputStream());
            if (process.exitValue() != 0) {
               String error = readAll(process.getErrorStream()).strip();
               throw new ScriptedPlayer.CommandFailed(error.isEmpty() ? command[0] + " exited with " + process.exitValue() : error);
            } else {
               return out;
            }
         }
      } finally {
         if (process.isAlive()) {
            process.destroyForcibly();
         }

         process.getInputStream().close();
         process.getErrorStream().close();
      }
   }

   private static String readAll(InputStream stream) throws IOException {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
   }

   @Environment(EnvType.CLIENT)
   enum Command {
      PLAY_PAUSE,
      NEXT,
      PREVIOUS;
   }

   @Environment(EnvType.CLIENT)
   static class CommandFailed extends IOException {
      CommandFailed(String message) {
         super(message);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class MissingTool extends IOException {
      MissingTool(String message) {
         super(message);
      }
   }

   @Environment(EnvType.CLIENT)
   record Reading(MediaSession.PlaybackState state, String title, String artist, String album, long durationMs, long positionMs, String artUrl) {
   }
}
