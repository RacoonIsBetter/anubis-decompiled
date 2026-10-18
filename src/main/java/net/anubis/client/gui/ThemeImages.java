package net.anubis.client.gui;

import imgui.AnubisAndroidNative;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.Surface;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ThemeImages {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final ThemeImages INSTANCE = new ThemeImages();
   private static final int MAX_SIZE = 8192;
   private static final long RESCAN_MS = 2000L;
   private static final int SWEEP_TICKS = 20;
   private static final int OPTIMIZED_SIDE = 2048;
   private static final int PREVIEW_SIDE = 384;
   private static final long CACHE_BYTES = 67108864L;
   private final Map<String, ThemeImages.Texture> textures = new HashMap<>();
   private final Map<String, ThemeImages.Texture> previews = new HashMap<>();
   private final Map<String, GifAnimation> animations = new HashMap<>();
   private final Map<String, GifAnimation> posters = new HashMap<>();
   private final Set<String> failed = new HashSet<>();
   private final List<Runnable> pendingFrees = new ArrayList<>();
   private final List<Runnable> profileListeners = new ArrayList<>();
   private final Map<String, Long> lastUsed = new HashMap<>();
   private long frame;
   private boolean optimized;
   private List<String> files = List.of();
   private long lastScan;
   private boolean listingChanged;
   private int ticks;
   private volatile Path pendingImport;
   private volatile boolean dialogOpen;

   private ThemeImages() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (++this.ticks % 20 == 0) {
            this.sweep();
         }
      });
   }

   public static ThemeImages get() {
      return INSTANCE;
   }

   public void beginFrame() {
      this.flushFrees();
      boolean next = PerformanceModule.optimizationsEnabled();
      if (this.optimized != next) {
         this.optimized = next;
         this.retireAll();
         this.profileListeners.forEach(Runnable::run);
         this.flushFrees();
         this.failed.clear();
      }

      this.frame++;
      GifAnimation.beginFrame(this.optimized);
   }

   public void endFrame() {
      if (this.optimized) {
         long bytes = this.cacheBytes();
         if (bytes > 67108864L) {
            List<Entry<String, Long>> oldest = new ArrayList<>(this.lastUsed.entrySet());
            oldest.sort(Comparator.comparingLong(Entry::getValue));

            for (Entry<String, Long> entry : oldest) {
               if (bytes <= 67108864L) {
                  break;
               }

               if (entry.getValue() != this.frame) {
                  bytes -= this.retire(entry.getKey());
               }
            }
         }
      }
   }

   public boolean optimizedImages() {
      return this.optimized;
   }

   public void onImageProfileChange(Runnable listener) {
      this.profileListeners.add(listener);
   }

   private void markUsed(String prefix, String fileName) {
      if (this.optimized) {
         this.lastUsed.put(prefix + fileName, this.frame);
      }
   }

   public void retireTexture(ThemeImages.Texture texture) {
      if (texture != null) {
         this.pendingFrees.add(() -> GL11.glDeleteTextures(texture.id()));
      }
   }

   private void flushFrees() {
      if (!this.pendingFrees.isEmpty()) {
         List<Runnable> frees = new ArrayList<>(this.pendingFrees);
         this.pendingFrees.clear();
         frees.forEach(Runnable::run);
      }
   }

   private void retireAll() {
      this.textures.values().forEach(this::retireTexture);
      this.previews.values().forEach(this::retireTexture);
      this.animations.values().forEach(a -> this.pendingFrees.add(a::free));
      this.posters.values().forEach(a -> this.pendingFrees.add(a::free));
      this.textures.clear();
      this.previews.clear();
      this.animations.clear();
      this.posters.clear();
      this.lastUsed.clear();
   }

   private static long textureBytes(ThemeImages.Texture texture) {
      return texture == null ? 0L : (long)texture.width() * texture.height() * 16L / 3L;
   }

   private long cacheBytes() {
      long total = 0L;

      for (ThemeImages.Texture texture : this.textures.values()) {
         total += textureBytes(texture);
      }

      for (ThemeImages.Texture texture : this.previews.values()) {
         total += textureBytes(texture);
      }

      for (GifAnimation animation : this.animations.values()) {
         total += animation.estimatedBytes();
      }

      for (GifAnimation animation : this.posters.values()) {
         total += animation.estimatedBytes();
      }

      return total;
   }

   private long retire(String key) {
      String name = key.substring(2);
      this.lastUsed.remove(key);
      if (!key.startsWith("t:") && !key.startsWith("p:")) {
         GifAnimation animation = (key.startsWith("a:") ? this.animations : this.posters).remove(name);
         if (animation == null) {
            return 0L;
         }

         long bytes = animation.estimatedBytes();
         this.pendingFrees.add(animation::free);
         return bytes;
      } else {
         ThemeImages.Texture texture = (key.startsWith("t:") ? this.textures : this.previews).remove(name);
         this.retireTexture(texture);
         return textureBytes(texture);
      }
   }

   public static boolean isImage(Path path) {
      String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
      return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || isGif(name);
   }

   public static boolean isGif(String fileName) {
      return fileName.toLowerCase(Locale.ROOT).endsWith(".gif");
   }

   public List<String> files() {
      long now = System.currentTimeMillis();
      if (now - this.lastScan > 2000L) {
         this.lastScan = now;
         List<String> scanned = scan();
         if (!scanned.equals(this.files)) {
            this.files = scanned;
            this.listingChanged = true;
         }
      }

      return this.files;
   }

   public ThemeImages.Texture texture(String fileName) {
      if (fileName == null || fileName.isBlank() || this.failed.contains(fileName)) {
         return null;
      }

      if (isGif(fileName)) {
         return this.frame(this.animations, fileName, false);
      }

      this.markUsed("t:", fileName);
      ThemeImages.Texture texture = this.textures.get(fileName);
      if (texture == null) {
         texture = upload(ThemeStore.backgroundsDir().resolve(fileName), this.optimized ? 2048 : 8192);
         if (texture == null) {
            this.failed.add(fileName);
         } else {
            this.textures.put(fileName, texture);
         }
      }

      return texture;
   }

   public ThemeImages.Texture preview(String fileName) {
      if (fileName == null || fileName.isBlank() || this.failed.contains(fileName)) {
         return null;
      }

      if (isGif(fileName)) {
         GifAnimation playing = this.animations.get(fileName);
         if (playing != null) {
            this.markUsed("a:", fileName);
         }

         ThemeImages.Texture frame = playing != null ? playing.current() : null;
         return frame != null ? frame : this.frame(this.posters, fileName, true);
      } else {
         if (!this.optimized) {
            return this.texture(fileName);
         }

         this.markUsed("p:", fileName);
         ThemeImages.Texture texture = this.previews.get(fileName);
         if (texture == null) {
            texture = upload(ThemeStore.backgroundsDir().resolve(fileName), 384);
            if (texture == null) {
               this.failed.add(fileName);
            } else {
               this.previews.put(fileName, texture);
            }
         }

         return texture;
      }
   }

   public boolean isBroken(String fileName) {
      return this.failed.contains(fileName);
   }

   public String importFile(Path source) {
      if (source != null && Files.isRegularFile(source) && isImage(source)) {
         try {
            Path dir = ThemeStore.backgroundsDir();
            Files.createDirectories(dir);
            if (dir.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize().getParent())) {
               return source.getFileName().toString();
            }

            String name = uniqueName(dir, source.getFileName().toString());
            Files.copy(source, dir.resolve(name));
            this.lastScan = 0L;
            return name;
         } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Anubis] Failed to import header image {}", source, e);
            return null;
         }
      } else {
         return null;
      }
   }

   public boolean delete(String fileName) {
      if (fileName != null && this.files.contains(fileName)) {
         try {
            Files.deleteIfExists(ThemeStore.backgroundsDir().resolve(fileName));
         } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Anubis] Failed to delete header image {}", fileName, e);
            return false;
         }

         ThemeImages.Texture texture = this.textures.remove(fileName);
         this.retireTexture(texture);
         this.retireTexture(this.previews.remove(fileName));
         GifAnimation playing = this.animations.remove(fileName);
         if (playing != null) {
            this.pendingFrees.add(playing::free);
         }

         GifAnimation poster = this.posters.remove(fileName);
         if (poster != null) {
            this.pendingFrees.add(poster::free);
         }

         for (String prefix : List.of("t:", "p:", "a:", "g:")) {
            this.lastUsed.remove(prefix + fileName);
         }

         this.failed.remove(fileName);
         List<Surface> surfaces = new ArrayList<>(ThemeStore.get().surfaces());
         surfaces.addAll(HudTheme.get().surfaces());

         for (Surface surface : surfaces) {
            if (fileName.equals(surface.image.get())) {
               surface.image.set("");
               if (surface.isImage()) {
                  surface.background.set("None");
               }
            }
         }

         this.lastScan = 0L;
         return true;
      } else {
         return false;
      }
   }

   public void browseAsync() {
      if (AnubisAndroidNative.isAndroidRuntime()) {
         AndroidSystemPicker.open();
      } else if (!this.dialogOpen) {
         this.dialogOpen = true;
         Thread thread = new Thread(() -> {
            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  PointerBuffer filters = stack.mallocPointer(4);
                  filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.gif")).flip();
                  String result = TinyFileDialogs.tinyfd_openFileDialog("Choose a header image", null, filters, "Images (png, jpg, gif)", false);
                  if (result != null) {
                     this.pendingImport = Path.of(result);
                  }
               } catch (Throwable var10) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                     }
                  }

                  throw var10;
               }

               if (stack != null) {
                  stack.close();
               }
            } catch (Throwable t) {
               LOGGER.warn("[Anubis] File dialog failed", t);
            } finally {
               this.dialogOpen = false;
            }
         }, "Anubis theme file dialog");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public Path takePendingImport() {
      if (AnubisAndroidNative.isAndroidRuntime()) {
         return AndroidSystemPicker.takeSelection();
      }

      Path path = this.pendingImport;
      this.pendingImport = null;
      return path;
   }

   public void openFolder() {
      this.browseAsync();
   }

   private ThemeImages.Texture frame(Map<String, GifAnimation> cache, String fileName, boolean poster) {
      this.markUsed(poster ? "g:" : "a:", fileName);
      GifAnimation animation = cache.computeIfAbsent(fileName, name -> GifAnimation.load(ThemeStore.backgroundsDir().resolve(name), poster, this.optimized));
      ThemeImages.Texture texture = animation.current();
      if (texture == null && animation.failed()) {
         cache.remove(fileName);
         this.lastUsed.remove((poster ? "g:" : "a:") + fileName);
         this.failed.add(fileName);
      }

      return texture;
   }

   private void sweep() {
      Set<String> shown = new HashSet<>();

      for (Surface surface : ThemeStore.get().surfaces()) {
         if (surface.isImage()) {
            shown.add(surface.image.get());
         }
      }

      for (Surface surface : HudTheme.get().surfaces()) {
         if (surface.isImage()) {
            shown.add(surface.image.get());
         }
      }

      this.freeAllBut(this.animations, shown, "a:");
      if (this.listingChanged) {
         this.listingChanged = false;
         Set<String> present = new HashSet<>(this.files);
         this.freeAllBut(this.animations, present, "a:");
         this.freeAllBut(this.posters, present, "g:");

         for (String name : new ArrayList<>(this.textures.keySet())) {
            if (!present.contains(name)) {
               this.retire("t:" + name);
            }
         }

         for (String name : new ArrayList<>(this.previews.keySet())) {
            if (!present.contains(name)) {
               this.retire("p:" + name);
            }
         }

         this.failed.retainAll(present);
      }
   }

   private void freeAllBut(Map<String, GifAnimation> cache, Set<String> keep, String prefix) {
      cache.entrySet().removeIf(entry -> {
         if (keep.contains(entry.getKey())) {
            return false;
         }

         this.pendingFrees.add(entry.getValue()::free);
         this.lastUsed.remove(prefix + entry.getKey());
         return true;
      });
   }

   private static List<String> scan() {
      Path dir = ThemeStore.backgroundsDir();
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      try (Stream<Path> stream = Files.list(dir)) {
         return stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(ThemeImages::isImage)
            .map(path -> path.getFileName().toString())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
      } catch (IOException e) {
         return List.of();
      }
   }

   private static String uniqueName(Path dir, String original) {
      String name = original.replaceAll("[^A-Za-z0-9._-]", "_");
      int dot = name.lastIndexOf(46);
      String base = dot > 0 ? name.substring(0, dot) : name;
      String ext = dot > 0 ? name.substring(dot) : "";
      String candidate = name;

      for (int i = 2; Files.exists(dir.resolve(candidate)); i++) {
         candidate = base + "-" + i + ext;
      }

      return candidate;
   }

   private static ThemeImages.Texture upload(Path path, int maxSide) {
      if (!Files.isRegularFile(path)) {
         return null;
      }

      try {
         return decode(Files.readAllBytes(path), path.getFileName().toString(), false, maxSide);
      } catch (IOException | RuntimeException e) {
         LOGGER.warn("[Anubis] Failed to load {}", path.getFileName(), e);
         return null;
      }
   }

   static ThemeImages.Texture decode(byte[] bytes, String name, boolean pixelated) {
      return decode(bytes, name, pixelated, 8192);
   }

   private static ThemeImages.Texture decode(byte[] bytes, String name, boolean pixelated, int maxSide) {
      ByteBuffer file = null;
      ByteBuffer pixels = null;

      try {
         MemoryStack stack = MemoryStack.stackPush();

         Object var29;
         label300: {
            ThemeImages.Texture var31;
            label301: {
               ThemeImages.Texture var14;
               label302: {
                  Object size;
                  try {
                     file = MemoryUtil.memAlloc(bytes.length);
                     file.put(bytes).flip();
                     IntBuffer w = stack.mallocInt(1);
                     IntBuffer h = stack.mallocInt(1);
                     IntBuffer channels = stack.mallocInt(1);
                     pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
                     if (pixels == null) {
                        LOGGER.warn("[Anubis] Can't decode {}: {}", name, STBImage.stbi_failure_reason());
                        var29 = null;
                        break label300;
                     }

                     int width = w.get(0);
                     int height = h.get(0);
                     if (width <= 8192 && height <= 8192) {
                        int[] sizex = ImageDownsampler.dimensions(width, height, maxSide);
                        if (sizex[0] == width && sizex[1] == height) {
                           var31 = fromPixels(pixels, width, height, pixelated);
                           break label301;
                        }

                        ByteBuffer reduced = MemoryUtil.memAlloc(sizex[0] * sizex[1] * 4);

                        try {
                           ImageDownsampler.rgba(pixels, width, height, reduced, sizex[0], sizex[1]);
                           var14 = fromPixels(reduced, sizex[0], sizex[1], pixelated);
                           break label302;
                        } finally {
                           MemoryUtil.memFree(reduced);
                        }
                     }

                     LOGGER.warn("[Anubis] {} is larger than {}px", name, 8192);
                     size = null;
                  } catch (Throwable var27) {
                     if (stack != null) {
                        try {
                           stack.close();
                        } catch (Throwable var25) {
                           var27.addSuppressed(var25);
                        }
                     }

                     throw var27;
                  }

                  if (stack != null) {
                     stack.close();
                  }

                  return (ThemeImages.Texture)size;
               }

               if (stack != null) {
                  stack.close();
               }

               return var14;
            }

            if (stack != null) {
               stack.close();
            }

            return var31;
         }

         if (stack != null) {
            stack.close();
         }

         return (ThemeImages.Texture)var29;
      } finally {
         if (pixels != null) {
            STBImage.stbi_image_free(pixels);
         }

         if (file != null) {
            MemoryUtil.memFree(file);
         }
      }
   }

   public static ThemeImages.Texture fromPixels(ByteBuffer rgba, int width, int height, boolean pixelated) {
      return new ThemeImages.Texture(createTexture(rgba, width, height, pixelated), width, height);
   }

   private static int createTexture(ByteBuffer pixels, int width, int height, boolean pixelated) {
      int prevTexture = GL11.glGetInteger(32873);
      int prevUnpackBuffer = GL11.glGetInteger(35055);
      int prevAlignment = GL11.glGetInteger(3317);
      int prevRowLength = GL11.glGetInteger(3314);
      int prevSkipPixels = GL11.glGetInteger(3316);
      int prevSkipRows = GL11.glGetInteger(3315);

      try {
         GL15.glBindBuffer(35052, 0);
         GL11.glPixelStorei(3317, 4);
         GL11.glPixelStorei(3314, 0);
         GL11.glPixelStorei(3316, 0);
         GL11.glPixelStorei(3315, 0);
         int id = GL11.glGenTextures();
         GL11.glBindTexture(3553, id);
         GL11.glTexParameteri(3553, 10241, pixelated ? 9728 : 9987);
         GL11.glTexParameteri(3553, 10240, pixelated ? 9728 : 9729);
         GL11.glTexParameteri(3553, 10242, 33071);
         GL11.glTexParameteri(3553, 10243, 33071);
         GL11.glTexImage2D(3553, 0, 32856, width, height, 0, 6408, 5121, pixels);
         if (!pixelated) {
            GL30.glGenerateMipmap(3553);
         }

         return id;
      } finally {
         GL11.glBindTexture(3553, prevTexture);
         GL15.glBindBuffer(35052, prevUnpackBuffer);
         GL11.glPixelStorei(3317, prevAlignment);
         GL11.glPixelStorei(3314, prevRowLength);
         GL11.glPixelStorei(3316, prevSkipPixels);
         GL11.glPixelStorei(3315, prevSkipRows);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Texture(int id, int width, int height) {
   }
}
