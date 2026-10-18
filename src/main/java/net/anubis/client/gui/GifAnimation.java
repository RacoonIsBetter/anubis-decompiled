package net.anubis.client.gui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class GifAnimation {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int MAX_SIDE = 1024;
   private static final int MAX_FRAMES = 300;
   private static final long MAX_PIXELS = 33554432L;
   private static final int OPTIMIZED_SIDE = 768;
   private static final long OPTIMIZED_PIXELS = 8388608L;
   private static final int POSTER_SIDE = 256;
   private static final int QUEUE_FRAMES = 8;
   private static final long UPLOAD_BUDGET_NANOS = 2000000L;
   private static long frameUploadNanos;
   private static final ExecutorService POSTERS = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Anubis GIF posters");
      thread.setDaemon(true);
      return thread;
   });
   private final String name;
   private final boolean poster;
   private final boolean optimized;
   private final BlockingQueue<GifDecoder.Frame> queue = new ArrayBlockingQueue<>(8);
   private final List<ThemeImages.Texture> textures = new ArrayList<>();
   private final int[] ends;
   private volatile boolean decoded;
   private volatile boolean cancelled;
   private volatile int decodedFramePixels;
   private long startNanos = -1L;

   private GifAnimation(String name, boolean poster, boolean optimized) {
      this.name = name;
      this.poster = poster;
      this.optimized = optimized;
      this.ends = new int[poster ? 1 : 300];
   }

   static GifAnimation load(Path path, boolean poster, boolean optimized) {
      GifAnimation animation = new GifAnimation(path.getFileName().toString(), poster, optimized);
      if (poster) {
         POSTERS.execute(() -> animation.decode(path));
      } else {
         Thread.ofPlatform().daemon().name("Anubis GIF decoder").start(() -> animation.decode(path));
      }

      return animation;
   }

   static void beginFrame(boolean optimized) {
      frameUploadNanos = 0L;
   }

   long estimatedBytes() {
      long bytes = (long)this.queue.size() * this.decodedFramePixels * 4L;

      for (ThemeImages.Texture texture : this.textures) {
         bytes += (long)texture.width() * texture.height() * 16L / 3L;
      }

      return bytes;
   }

   ThemeImages.Texture current() {
      boolean complete = this.decoded;
      this.upload();
      int count = this.textures.size();
      if (count == 0) {
         return null;
      }

      if (this.startNanos < 0L) {
         if (!complete || !this.queue.isEmpty()) {
            return this.textures.get(0);
         }

         this.startNanos = System.nanoTime();
      }

      long time = (System.nanoTime() - this.startNanos) / 1000000L % this.ends[count - 1];
      int lo = 0;
      int hi = count - 1;

      while (lo < hi) {
         int mid = lo + hi >>> 1;
         if (this.ends[mid] > time) {
            hi = mid;
         } else {
            lo = mid + 1;
         }
      }

      return this.textures.get(lo);
   }

   boolean failed() {
      return this.decoded && this.queue.isEmpty() && this.textures.isEmpty();
   }

   void free() {
      this.cancelled = true;
      this.queue.clear();

      for (ThemeImages.Texture texture : this.textures) {
         GL11.glDeleteTextures(texture.id());
      }

      this.textures.clear();
   }

   private void decode(Path path) {
      try {
         if (!this.cancelled) {
            GifDecoder gif = GifDecoder.open(Files.readAllBytes(path));
            int w = gif.width();
            int h = gif.height();
            int frames = Math.min(gif.frameCount(), this.ends.length);
            int maxSide = this.poster ? 256 : (this.optimized ? 768 : 1024);
            long maxPixels = this.optimized ? 8388608L : 33554432L;
            double scale = Math.min(1.0, (double)maxSide / Math.max(w, h));
            if (!this.poster) {
               scale = Math.min(scale, Math.sqrt(maxPixels / ((double)frames * w * h)));
            }

            int outW = Math.max(1, (int)Math.floor(w * scale));
            int outH = Math.max(1, (int)Math.floor(h * scale));
            this.decodedFramePixels = outW * outH;
            if (!this.poster && frames < gif.frameCount()) {
               LOGGER.info("[Anubis] {} has {} frames; only the first {} will play", new Object[]{this.name, gif.frameCount(), frames});
            }

            if (!this.poster && outW < w) {
               LOGGER.info("[Anubis] {} ({}x{}, {} frames) plays at {}x{} to keep its memory use down", new Object[]{this.name, w, h, frames, outW, outH});
            }

            gif.decode(outW, outH, frames, this::offer);
            return;
         }
      } catch (IOException | RuntimeException e) {
         LOGGER.warn("[Anubis] Failed to load {}", this.name, e);
         return;
      } finally {
         this.decoded = true;
      }
   }

   private boolean offer(GifDecoder.Frame frame) {
      int[] pixels = frame.argb();

      for (int i = 0; i < pixels.length; i++) {
         int p = pixels[i];
         pixels[i] = p & -16711936 | p >>> 16 & 0xFF | (p & 0xFF) << 16;
      }

      try {
         while (!this.cancelled) {
            if (this.queue.offer(frame, 50L, TimeUnit.MILLISECONDS)) {
               return true;
            }
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      return false;
   }

   private void upload() {
      if (!this.optimized || frameUploadNanos < 2000000L) {
         long start = System.nanoTime();

         GifDecoder.Frame frame;
         try {
            while (this.textures.size() < this.ends.length && (frame = this.queue.poll()) != null) {
               ByteBuffer rgba = MemoryUtil.memAlloc(frame.argb().length * 4);

               try {
                  rgba.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(frame.argb());
                  this.textures.add(ThemeImages.fromPixels(rgba, frame.width(), frame.height(), false));
               } finally {
                  MemoryUtil.memFree(rgba);
               }

               int count = this.textures.size();
               this.ends[count - 1] = (count > 1 ? this.ends[count - 2] : 0) + frame.delayMs();
               long elapsed = System.nanoTime() - start;
               if (elapsed + (this.optimized ? frameUploadNanos : 0L) >= 2000000L) {
                  break;
               }
            }
         } finally {
            if (this.optimized) {
               frameUploadNanos = frameUploadNanos + (System.nanoTime() - start);
            }
         }
      }
   }
}
