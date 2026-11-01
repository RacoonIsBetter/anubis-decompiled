package net.anubis.client.module.impl.misc.noinvleak;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.sun.jna.Platform;
import imgui.ImDrawList;
import imgui.ImGui;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.gui.BorderlessFullscreen;
import net.anubis.client.gui.ItemIcons;
import net.anubis.client.gui.ItemLook;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Atlases;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class StreamOverlay {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int SLOTS = 41;
   private static final int CURSOR = 41;
   private static final int ENTRIES = 42;
   private static final long SLOT_BITS = 2199023255551L;
   private static final int CELLS_PER_SIDE = 7;
   private static final int CACHE_LIMIT = 4194304;
   private static final long READBACK_TIMEOUT = 1000000000L;
   private static final long BACKGROUND_INTERVAL = 33000000L;
   private static final int FALLBACK_BACKGROUND = -7631989;
   private static final int[] BLANK = new int[0];
   private static final Runnable NOTHING = () -> {};
   private static final ItemStack[] stack = new ItemStack[42];
   private static final int[] guiX = new int[42];
   private static final int[] guiY = new int[42];
   private static long mask;
   private static int hovered = -1;
   private static boolean selectionSeen;
   private static int selectionX;
   private static int selectionY;
   private static long fakeMask;
   private static int fakeScale;
   private static ImDrawList fakeList;
   private static final DigitFont.Sink FAKE_SINK = (x, y, w, h, argb) -> fakeList.addRectFilled(
      x, y, x + w, y + h, argb & -16711936 | argb << 16 & 0xFF0000 | argb >>> 16 & 0xFF
   );
   private static final ItemStack[] shownStack = new ItemStack[42];
   private static final int[] shownX = new int[42];
   private static final int[] shownY = new int[42];
   private static final int[] shownCount = new int[42];
   private static final int[] shownBar = new int[42];
   private static long shownMask;
   private static int shownScale;
   private static boolean shownOpaque;
   private static boolean shownSelection;
   private static int shownSelectionX;
   private static int shownSelectionY;
   private static int shownHover = -1;
   private static int shownWindowX;
   private static int shownWindowY;
   private static GpuTexture shownAtlas;
   private static int boxX;
   private static int boxY;
   private static boolean backgroundValid;
   private static ItemStack cursorStack;
   private static int cursorCount;
   private static int cursorBar;
   private static int cursorScale;
   private static int cursorX;
   private static int cursorY;
   private static final ItemLook look = new ItemLook();
   private static final Object2ObjectOpenHashMap<List<Object>, int[]> icons = new Object2ObjectOpenHashMap();
   private static final int[][] slotIcon = new int[42][];
   private static final int[] pendingSlot = new int[49];
   private static final List<Object>[] pendingKey = newKeys(49);
   private static int cachedBytes;
   private static final int[][] background = new int[41][];
   private static final int[][] backgroundNext = new int[41][];
   private static final int[] syncRegion = new int[4];
   private static long backgroundAt;
   private static GpuFence asyncFence;
   private static GpuBuffer asyncBuffer;
   private static long asyncMask;
   private static int asyncScale;
   private static final int[] asyncX = new int[41];
   private static final int[] asyncY = new int[41];
   private static final int[] asyncRegion = new int[4];
   private static final Canvas canvas = new Canvas();
   private static OverlayWindow slotWindow;
   private static OverlayWindow cursorWindow;
   private static GpuTexture scratch;
   private static GpuTextureView scratchView;
   private static GpuTexture scratchDepth;
   private static GpuTextureView scratchDepthView;
   private static GpuBuffer readback;
   private static int iconPx;
   private static volatile boolean unsupported;
   private static volatile long retryAt;
   private static int transientFailures;
   private static boolean toldUnsupported;
   private static boolean toldPaused;
   private static boolean toldFullscreen;
   private static boolean diagnosed;
   private static final long[] BACKOFF = new long[]{2000000000L, 10000000000L, 60000000000L};
   private static final long REPEAT_LOG_INTERVAL = 60000000000L;
   private static final Map<String, long[]> reported = new HashMap<>();

   private StreamOverlay() {
   }

   public static boolean usable() {
      if (Platform.isWindows() && !unsupported) {
         long at = retryAt;
         return at == 0L || System.nanoTime() - at >= 0L;
      } else {
         return false;
      }
   }

   public static void retry() {
      unsupported = false;
      retryAt = 0L;
      transientFailures = 0;
   }

   public static boolean recordsHotbar() {
      return hotbarVisible();
   }

   public static void hotbar(int slot, int x, int y, ItemStack item) {
      if (hotbarVisible()) {
         record(slot, x, y, item);
      }
   }

   public static void container(int slot, int x, int y, ItemStack item, boolean isHovered) {
      record(slot, x, y, item);
      if (isHovered) {
         hovered = slot;
      }
   }

   public static void carried(ItemStack item, int x, int y) {
      record(41, x, y, item);
   }

   public static void selection(int x, int y) {
      if (hotbarVisible()) {
         selectionSeen = true;
         selectionX = x;
         selectionY = y;
      }
   }

   public static void tooltip(ItemStack item, int mouseX, int mouseY) {
      TextPanels.tooltip(item, mouseX, mouseY);
   }

   public static void itemName(ItemStack item, int alpha, int y) {
      TextPanels.itemName(item, alpha, y);
   }

   private static boolean hotbarVisible() {
      Screen screen = MinecraftClient.getInstance().currentScreen;
      return screen == null || screen instanceof ChatScreen;
   }

   private static void record(int entry, int x, int y, ItemStack item) {
      if (entry >= 0 && entry < 42 && (entry != 41 || !item.isEmpty())) {
         stack[entry] = item;
         guiX[entry] = x;
         guiY[entry] = y;
         mask |= 1L << entry;
      }
   }

   public static boolean hasFakes() {
      return fakeMask != 0L;
   }

   public static void drawFakes() {
      long bits = fakeMask;
      int scale = fakeScale;
      if (bits != 0L && scale > 0) {
         try {
            fakeList = ImGui.getBackgroundDrawList();

            for (int slot = 0; slot < 41; slot++) {
               if ((bits >>> slot & 1L) != 0L) {
                  ItemStack fake = NoInvLeakModule.fakeFor(slot);
                  if (!fake.isEmpty()) {
                     int x = guiX[slot] * scale;
                     int y = guiY[slot] * scale;
                     ItemIcons.draw(fakeList, fake, x, y, 16.0F * scale, 1.0F);
                     DigitFont.decorations(FAKE_SINK, fake, x, y, scale);
                  }
               }
            }
         } catch (RuntimeException | LinkageError e) {
            fakeMask = 0L;
            fail(e, "drawing the stream's fake items");
         }
      }
   }

   public static void endFrame() {
      long frame = mask;
      mask = 0L;
      int hover = hovered;
      hovered = -1;
      boolean selection = selectionSeen && NoInvLeakModule.hidesSelectedSlot();
      selectionSeen = false;
      if (NoInvLeakModule.overlayWanted() && usable()) {
         try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Window win = mc.getWindow();
            if (win.hasZeroWidthOrHeight()) {
               fakeMask = 0L;
               hideAll();
            } else {
               int scale = win.getScaleFactor();
               int windowX = win.getX();
               int windowY = win.getY();
               boolean opaque = NoInvLeakModule.opaqueCells();
               frame = withoutEmpty(frame, opaque && NoInvLeakModule.fillsEmpty());
               GpuTexture atlas = itemAtlas(mc);
               if (atlas != shownAtlas) {
                  OverlayArt.reset();
                  resetIcons(16 * scale);
                  shownAtlas = atlas;
                  shownMask = 0L;
               }

               fakeMask = frame & 2199023255551L;
               fakeScale = scale;
               layers(mc, win, frame, scale, windowX, windowY, opaque, selection, hover);
               TextPanels.update(win, scale, windowX, windowY);
            }
         } catch (RuntimeException | LinkageError e) {
            fakeMask = 0L;
            fail(e, "drawing the overlay");
         }

         TextPanels.frameDone();
      } else {
         fakeMask = 0L;
         if (started()) {
            release();
         }

         TextPanels.frameDone();
      }
   }

   private static void fail(Throwable e, String phase) {
      boolean permanent = e instanceof OverlayWindow.Unsupported || e instanceof LinkageError;
      String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      long wait = 0L;
      if (permanent) {
         unsupported = true;
      } else {
         wait = BACKOFF[Math.min(transientFailures++, BACKOFF.length - 1)];
         retryAt = System.nanoTime() + wait;
      }

      String key = phase + "|" + e.getClass().getName() + "|" + reason;
      long now = System.nanoTime();
      long[] seen = reported.get(key);
      String outcome = permanent ? "turned off for this session" : "retrying in " + wait / 1000000000L + " s";
      if (seen == null) {
         reported.put(key, new long[]{now, 0L});
         LOGGER.warn(
            "[Anubis] No Inv Leak: Stream Only failed while {} ({}); {}. Windows build {}. Your items are shown as hidden to you as well until it works again.",
            new Object[]{phase, reason, outcome, OverlayWindow.windowsBuild(), e}
         );
      } else if (now - seen[0] >= 60000000000L) {
         LOGGER.warn(
            "[Anubis] No Inv Leak: Stream Only failed again while {} ({}), {} more time(s) since the last report; {}",
            new Object[]{phase, reason, seen[1] + 1L, outcome}
         );
         seen[0] = now;
         seen[1] = 0L;
      } else {
         seen[1]++;
      }

      if (permanent && !toldUnsupported) {
         toldUnsupported = true;
         NoInvLeakModule.streamNotice("Stream Only unavailable", ": " + reason + ". Items stay hidden for you too.");
      } else if (!permanent && !toldPaused) {
         toldPaused = true;
         NoInvLeakModule.streamNotice("Stream Only paused", ": " + reason + ". Retrying automatically.");
      }

      try {
         release();
      } catch (RuntimeException | LinkageError cleanup) {
         LOGGER.debug("[Anubis] No Inv Leak: overlay cleanup failed", cleanup);
      }
   }

   private static void diagnose(MinecraftClient mc, Window win) {
      if (!diagnosed) {
         diagnosed = true;
         transientFailures = 0;
         boolean fullscreen = win.isFullscreen();
         boolean borderless = fullscreen && ((BorderlessFullscreen.Switchable)(Object)win).anubis$isBorderless();
         String mode = !fullscreen ? "windowed" : (borderless ? "borderless fullscreen" : "exclusive fullscreen");

         String renderer;
         try {
            renderer = GL11.glGetString(7936) + " / " + GL11.glGetString(7937);
         } catch (RuntimeException | LinkageError unknown) {
            renderer = "unknown";
         }

         LOGGER.info(
            "[Anubis] No Inv Leak: Stream Only overlay active (Windows build {}, {}, window {}x{} at {},{}, framebuffer {}x{}, gui scale {}, {}, overlay {})",
            new Object[]{
               OverlayWindow.windowsBuild(),
               mode,
               win.getWidth(),
               win.getHeight(),
               win.getX(),
               win.getY(),
               mc.getFramebuffer().textureWidth,
               mc.getFramebuffer().textureHeight,
               win.getScaleFactor(),
               renderer,
               slotWindow != null ? slotWindow.describe() : "pending"
            }
         );
         if (fullscreen && !borderless && !toldFullscreen) {
            toldFullscreen = true;
            NoInvLeakModule.streamNotice("Stream Only", ": exclusive fullscreen can hide the overlay from you. Turn on Borderless Fullscreen.");
         }
      }
   }

   public static void stop() {
      diagnosed = false;
      if (RenderSystem.isOnRenderThread()) {
         release();
      } else {
         MinecraftClient.getInstance().execute(StreamOverlay::release);
      }
   }

   private static boolean started() {
      return slotWindow != null || cursorWindow != null || scratch != null || TextPanels.started();
   }

   private static void layers(MinecraftClient mc, Window win, long frame, int scale, int windowX, int windowY, boolean opaque, boolean selection, int hover) {
      long slotBits = frame & 2199023255551L;
      boolean slotsShown = slotBits != 0L || selection;
      if (!slotsShown && (frame >>> 41 & 1L) == 0L) {
         hideSlotsAndCursor();
      } else {
         int px = 16 * scale;
         if (px != iconPx || cachedBytes > 4194304) {
            resetIcons(px);
         }

         if (scratch == null) {
            allocate(px);
         }

         if (slotWindow == null && slotsShown) {
            slotWindow = new OverlayWindow(win.getHandle());
         }

         if (cursorWindow == null && (frame >>> 41 & 1L) != 0L) {
            cursorWindow = new OverlayWindow(win.getHandle());
         }

         boolean layout = slotBits != shownMask
            || scale != shownScale
            || opaque != shownOpaque
            || selection != shownSelection
            || selection && (selectionX != shownSelectionX || selectionY != shownSelectionY);
         boolean content = false;

         for (int i = 0; i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L) {
               if (guiX[i] != shownX[i] || guiY[i] != shownY[i]) {
                  layout = true;
               } else if (stack[i] != shownStack[i] || stack[i].getCount() != shownCount[i] || bar(stack[i]) != shownBar[i]) {
                  content = true;
               }
            }
         }

         if (layout) {
            backgroundValid = false;
            dropAsync();
         }

         int pending = resolveIcons(frame, px);
         boolean wantBackground = opaque && slotBits != 0L && !backgroundValid;
         boolean backgroundChanged = false;
         if (pending > 0 || wantBackground) {
            synchronousRead(slotBits, scale, wantBackground, pending);
            if (wantBackground) {
               backgroundChanged = true;
               shownHover = hover;
            }
         } else if (opaque && slotBits != 0L) {
            backgroundChanged = takeAsync(slotBits, scale);
         }

         if (!slotsShown) {
            if (slotWindow != null) {
               slotWindow.hide();
            }

            shownMask = 0L;
         } else if (layout || content || backgroundChanged || pending > 0) {
            composeSlots(slotBits, scale, opaque, selection, windowX, windowY);
         } else if (windowX != shownWindowX || windowY != shownWindowY) {
            slotWindow.move(windowX + boxX, windowY + boxY);
            shownWindowX = windowX;
            shownWindowY = windowY;
         }

         if (opaque
            && slotBits != 0L
            && asyncFence == null
            && backgroundValid
            && (hover != shownHover || hotbarVisible() && System.nanoTime() - backgroundAt >= 33000000L)) {
            requestAsync(slotBits, scale);
            shownHover = hover;
         }

         cursor(frame, scale, windowX, windowY);
      }
   }

   private static void hideSlotsAndCursor() {
      if (slotWindow != null) {
         slotWindow.hide();
      }

      if (cursorWindow != null) {
         cursorWindow.hide();
      }

      shownMask = 0L;
      cursorStack = null;
   }

   private static long withoutEmpty(long frame, boolean keepEmpty) {
      if (keepEmpty) {
         return frame;
      }

      long bits = frame;

      for (int i = 0; i < 41; i++) {
         if ((bits >>> i & 1L) != 0L && stack[i].isEmpty()) {
            bits &= ~(1L << i);
         }
      }

      return bits;
   }

   private static void hideAll() {
      hideSlotsAndCursor();
      TextPanels.hide();
   }

   private static int bar(ItemStack item) {
      return item.isItemBarVisible() ? item.getItemBarColor() << 5 | item.getItemBarStep() : -1;
   }

   private static int cellX(int cell, int px) {
      return cell % 7 * px;
   }

   private static int cellY(int cell, int px) {
      return cell / 7 * px;
   }

   private static int resolveIcons(long bits, int px) {
      int pending = 0;

      for (int i = 0; i < 42; i++) {
         if ((bits >>> i & 1L) != 0L) {
            ItemStack item = stack[i];
            if (item != shownStack[i]) {
               slotIcon[i] = null;
            }

            if (slotIcon[i] == null) {
               if (!item.isEmpty() && look.resolve(item)) {
                  int[] icon = (int[])icons.get(look.identity);
                  if (icon != null) {
                     slotIcon[i] = icon;
                  } else {
                     int cell = pending;

                     try {
                        ItemIcons.render(look, scratch, scratchView, scratchDepth, scratchDepthView, cellX(cell, px), cellY(cell, px), px);
                     } catch (RuntimeException e) {
                        slotIcon[i] = BLANK;
                        look.clear();
                        String key = "icon|" + item.getItem();
                        if (reported.putIfAbsent(key, new long[]{System.nanoTime(), 0L}) == null) {
                           LOGGER.warn("[Anubis] No Inv Leak: Stream Only could not draw the icon for {}; it stays blank", item.getItem(), e);
                        }
                        continue;
                     }

                     pending++;
                     pendingSlot[cell] = i;
                     pendingKey[cell] = Arrays.asList(look.identity.toArray());
                  }

                  look.clear();
               } else {
                  slotIcon[i] = BLANK;
               }
            }
         }
      }

      return pending;
   }

   private static void resetIcons(int px) {
      icons.clear();
      cachedBytes = 0;
      Arrays.fill(slotIcon, null);
      dropAsync();
      backgroundValid = false;
      if (px != iconPx || scratch == null) {
         closeTextures();
         Arrays.fill(background, null);
         Arrays.fill(backgroundNext, null);
      }
   }

   private static void allocate(int px) {
      int side = 7 * px;
      GpuDevice device = RenderSystem.getDevice();
      scratch = device.createTexture("Anubis overlay icons", 15, TextureFormat.RGBA8, side, side, 1, 1);
      scratchView = device.createTextureView(scratch);
      scratchDepth = device.createTexture("Anubis overlay icon depth", 8, TextureFormat.DEPTH32, side, side, 1, 1);
      scratchDepthView = device.createTextureView(scratchDepth);
      device.createCommandEncoder().clearColorAndDepthTextures(scratch, 0, scratchDepth, 1.0);
      iconPx = px;
   }

   private static GpuTexture itemAtlas(MinecraftClient mc) {
      try {
         return mc.getAtlasManager().getAtlasTexture(Atlases.ITEMS).getGlTexture();
      } catch (RuntimeException notReady) {
         return null;
      }
   }

   private static void synchronousRead(long slotBits, int scale, boolean wantBackground, int pending) {
      int px = 16 * scale;
      int side = 7 * px;
      long scratchBytes = pending > 0 ? (long)side * side * 4L : 0L;
      boolean framed = wantBackground && region(slotBits, scale, syncRegion);
      long frameBytes = framed ? (long)syncRegion[2] * syncRegion[3] * 4L : 0L;
      long needed = framed ? scratchBytes + frameTextureBytes() : scratchBytes;
      long used = scratchBytes + frameBytes;
      if (wantBackground) {
         backgroundValid = true;
      }

      if (needed == 0L) {
         if (wantBackground) {
            unpackBackground(null, 0, syncRegion, false, slotBits, scale);
         }
      } else {
         GpuDevice device = RenderSystem.getDevice();
         if (readback == null || readback.size() < needed) {
            if (readback != null) {
               readback.close();
            }

            readback = device.createBuffer(() -> "Anubis overlay readback", 9, needed);
         }

         CommandEncoder encoder = device.createCommandEncoder();
         packDefaults();

         try {
            if (pending > 0) {
               encoder.copyTextureToBuffer(scratch, readback, 0L, NOTHING, 0);
            }

            if (framed) {
               copyRegion(encoder, readback, scratchBytes, syncRegion);
            }
         } finally {
            packReset();
         }

         try (GpuFence view = encoder.createFence()) {
            GL11.glFlush();
            if (!view.awaitCompletion(1000000000L)) {
               throw new IllegalStateException("overlay read-back timed out");
            }
         }

         try (MappedView view = encoder.mapBuffer(readback.slice(0L, used), true, false)) {
            ByteBuffer data = view.data();

            for (int c = 0; c < pending; c++) {
               int[] icon = unpackIcon(data, cellX(c, px), cellY(c, px), side, px);
               icons.put(pendingKey[c], icon);
               pendingKey[c] = null;
               cachedBytes += px * px * 4;
               slotIcon[pendingSlot[c]] = icon;
            }

            if (wantBackground) {
               unpackBackground(data, (int)scratchBytes, syncRegion, framed, slotBits, scale);
               backgroundAt = System.nanoTime();
            }
         }
      }
   }

   private static void requestAsync(long slotBits, int scale) {
      backgroundAt = System.nanoTime();
      if (region(slotBits, scale, asyncRegion)) {
         long frameBytes = frameTextureBytes();
         GpuDevice device = RenderSystem.getDevice();
         if (asyncBuffer == null || asyncBuffer.size() < frameBytes) {
            if (asyncBuffer != null) {
               asyncBuffer.close();
            }

            asyncBuffer = device.createBuffer(() -> "Anubis overlay background", 9, frameBytes);
         }

         CommandEncoder encoder = device.createCommandEncoder();
         packDefaults();

         try {
            copyRegion(encoder, asyncBuffer, 0L, asyncRegion);
         } finally {
            packReset();
         }

         asyncFence = encoder.createFence();
         GL11.glFlush();
         asyncMask = slotBits;
         asyncScale = scale;

         for (int i = 0; i < 41; i++) {
            asyncX[i] = guiX[i];
            asyncY[i] = guiY[i];
         }
      }
   }

   private static boolean takeAsync(long slotBits, int scale) {
      if (asyncFence != null && asyncFence.awaitCompletion(0L)) {
         boolean usable = asyncMask == slotBits && asyncScale == scale;

         for (int i = 0; usable && i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L && (asyncX[i] != guiX[i] || asyncY[i] != guiY[i])) {
               usable = false;
            }
         }

         boolean changed = false;
         if (usable) {
            long bytes = (long)asyncRegion[2] * asyncRegion[3] * 4L;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            try (MappedView view = encoder.mapBuffer(asyncBuffer.slice(0L, bytes), true, false)) {
               changed = unpackBackground(view.data(), 0, asyncRegion, true, slotBits, scale);
            }
         }

         dropAsync();
         return changed;
      } else {
         return false;
      }
   }

   private static void dropAsync() {
      if (asyncFence != null) {
         asyncFence.close();
         asyncFence = null;
      }
   }

   private static long frameTextureBytes() {
      GpuTexture frame = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
      return (long)frame.getWidth(0) * frame.getHeight(0) * 4L;
   }

   private static void packDefaults() {
      GlStateManager._pixelStore(3333, 4);
      GlStateManager._pixelStore(3331, 0);
      GlStateManager._pixelStore(3332, 0);
   }

   private static void packReset() {
      GlStateManager._pixelStore(3330, 0);
   }

   private static boolean region(long slotBits, int scale, int[] out) {
      GpuTexture target = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
      int px = 16 * scale;
      int x0 = Integer.MAX_VALUE;
      int y0 = Integer.MAX_VALUE;
      int x1 = Integer.MIN_VALUE;
      int y1 = Integer.MIN_VALUE;

      for (int i = 0; i < 41; i++) {
         if ((slotBits >>> i & 1L) != 0L) {
            x0 = Math.min(x0, guiX[i] * scale);
            y0 = Math.min(y0, guiY[i] * scale);
            x1 = Math.max(x1, guiX[i] * scale + px);
            y1 = Math.max(y1, guiY[i] * scale + px);
         }
      }

      x0 = Math.max(x0, 0);
      y0 = Math.max(y0, 0);
      x1 = Math.min(x1, target.getWidth(0));
      y1 = Math.min(y1, target.getHeight(0));
      if (x1 > x0 && y1 > y0) {
         out[0] = x0;
         out[1] = y0;
         out[2] = x1 - x0;
         out[3] = y1 - y0;
         return true;
      } else {
         return false;
      }
   }

   private static void copyRegion(CommandEncoder encoder, GpuBuffer target, long offset, int[] region) {
      GpuTexture color = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
      encoder.copyTextureToBuffer(color, target, offset, NOTHING, 0, region[0], color.getHeight(0) - region[1] - region[3], region[2], region[3]);
   }

   private static int[] unpackIcon(ByteBuffer data, int cellX, int cellY, int side, int px) {
      int[] out = new int[px * px];

      for (int y = 0; y < px; y++) {
         int row = ((side - 1 - (cellY + y)) * side + cellX) * 4;

         for (int x = 0; x < px; x++) {
            int at = row + x * 4;
            int a = data.get(at + 3) & 255;
            out[y * px + x] = a << 24 | Math.min(a, data.get(at) & 255) << 16 | Math.min(a, data.get(at + 1) & 255) << 8 | Math.min(a, data.get(at + 2) & 255);
         }
      }

      return out;
   }

   private static boolean unpackBackground(ByteBuffer data, int offset, int[] region, boolean framed, long slotBits, int scale) {
      int px = 16 * scale;
      int rx = region[0];
      int ry = region[1];
      int rw = region[2];
      int rh = region[3];
      boolean changed = false;

      for (int slot = 0; slot < 41; slot++) {
         if ((slotBits >>> slot & 1L) != 0L) {
            int cx = guiX[slot] * scale - rx;
            int cy = guiY[slot] * scale - ry;
            if (framed && cx >= 0 && cy >= 0 && cx + px <= rw && cy + px <= rh) {
               int[] next = backgroundNext[slot];
               if (next == null || next.length != px * px) {
                  next = new int[px * px];
               }

               for (int y = 0; y < px; y++) {
                  int row = offset + ((rh - 1 - (cy + y)) * rw + cx) * 4;

                  for (int x = 0; x < px; x++) {
                     int at = row + x * 4;
                     next[y * px + x] = 0xFF000000 | (data.get(at) & 255) << 16 | (data.get(at + 1) & 255) << 8 | data.get(at + 2) & 255;
                  }
               }

               int[] current = background[slot];
               if (current != null && Arrays.equals(current, next)) {
                  backgroundNext[slot] = next;
               } else {
                  changed = true;
                  background[slot] = next;
                  backgroundNext[slot] = current;
               }
            } else {
               if (background[slot] != null) {
                  changed = true;
               }

               background[slot] = null;
            }
         }
      }

      return changed;
   }

   private static void composeSlots(long slotBits, int scale, boolean opaque, boolean selection, int windowX, int windowY) {
      int px = 16 * scale;
      OverlayArt.Image frame = selection ? OverlayArt.selection() : null;
      boolean drawSelection = frame != null;
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;

      for (int i = 0; i < 41; i++) {
         if ((slotBits >>> i & 1L) != 0L) {
            minX = Math.min(minX, guiX[i]);
            minY = Math.min(minY, guiY[i]);
            maxX = Math.max(maxX, guiX[i] + 17);
            maxY = Math.max(maxY, guiY[i] + 17);
         }
      }

      if (drawSelection) {
         minX = Math.min(minX, selectionX);
         minY = Math.min(minY, selectionY);
         maxX = Math.max(maxX, selectionX + frame.width());
         maxY = Math.max(maxY, selectionY + frame.height());
      }

      if (minX > maxX) {
         slotWindow.hide();
         shownMask = 0L;
      } else {
         int bx = minX * scale;
         int by = minY * scale;
         int bw = (maxX - minX) * scale;
         int bh = (maxY - minY) * scale;
         canvas.wrap(slotWindow.pixels(bw, bh), slotWindow.stride(), bw, bh);
         if (opaque) {
            for (int i = 0; i < 41; i++) {
               if ((slotBits >>> i & 1L) != 0L) {
                  int ox = guiX[i] * scale - bx;
                  int oy = guiY[i] * scale - by;
                  int[] behind = background[i];
                  if (behind != null) {
                     canvas.copy(behind, px, px, ox, oy);
                  } else {
                     canvas.rect(ox, oy, px, px, -7631989);
                  }
               }
            }
         }

         if (drawSelection) {
            canvas.blend(frame.pixels(), frame.width(), frame.height(), selectionX * scale - bx, selectionY * scale - by, scale);
         }

         for (int i = 0; i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L) {
               int ox = guiX[i] * scale - bx;
               int oy = guiY[i] * scale - by;
               int[] icon = slotIcon[i];
               if (icon != null && icon != BLANK) {
                  canvas.blend(icon, px, px, ox, oy, 1);
               }

               if (!stack[i].isEmpty()) {
                  DigitFont.decorations(canvas, stack[i], ox, oy, scale);
               }
            }
         }

         slotWindow.present(windowX + bx, windowY + by, bw, bh);
         diagnose(MinecraftClient.getInstance(), MinecraftClient.getInstance().getWindow());

         for (int i = 0; i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L) {
               shownStack[i] = stack[i];
               shownX[i] = guiX[i];
               shownY[i] = guiY[i];
               shownCount[i] = stack[i].getCount();
               shownBar[i] = bar(stack[i]);
            }
         }

         shownMask = slotBits;
         shownScale = scale;
         shownOpaque = opaque;
         shownSelection = selection;
         shownSelectionX = selectionX;
         shownSelectionY = selectionY;
         shownWindowX = windowX;
         shownWindowY = windowY;
         boxX = bx;
         boxY = by;
      }
   }

   private static void cursor(long frame, int scale, int windowX, int windowY) {
      if ((frame >>> 41 & 1L) == 0L) {
         if (cursorWindow != null) {
            cursorWindow.hide();
         }

         cursorStack = null;
      } else {
         ItemStack item = stack[41];
         int px = 16 * scale;
         int screenX = windowX + guiX[41] * scale;
         int screenY = windowY + guiY[41] * scale;
         boolean same = item == cursorStack && item.getCount() == cursorCount && bar(item) == cursorBar && scale == cursorScale;
         if (same) {
            if (screenX != cursorX || screenY != cursorY) {
               cursorWindow.move(screenX, screenY);
            }
         } else {
            int size = px + scale;
            canvas.wrap(cursorWindow.pixels(size, size), cursorWindow.stride(), size, size);
            int[] icon = slotIcon[41];
            if (icon != null && icon != BLANK) {
               canvas.blend(icon, px, px, 0, 0, 1);
            }

            DigitFont.decorations(canvas, item, 0, 0, scale);
            cursorWindow.present(screenX, screenY, size, size);
            cursorStack = item;
            cursorCount = item.getCount();
            cursorBar = bar(item);
            cursorScale = scale;
         }

         cursorX = screenX;
         cursorY = screenY;
         shownStack[41] = item;
      }
   }

   private static void closeTextures() {
      if (scratchView != null) {
         scratchView.close();
      }

      if (scratch != null) {
         scratch.close();
      }

      if (scratchDepthView != null) {
         scratchDepthView.close();
      }

      if (scratchDepth != null) {
         scratchDepth.close();
      }

      scratchView = null;
      scratch = null;
      scratchDepthView = null;
      scratchDepth = null;
      iconPx = 0;
   }

   private static void release() {
      dropAsync();
      if (slotWindow != null) {
         slotWindow.destroy();
         slotWindow = null;
      }

      if (cursorWindow != null) {
         cursorWindow.destroy();
         cursorWindow = null;
      }

      TextPanels.release();
      closeTextures();
      if (readback != null) {
         readback.close();
         readback = null;
      }

      if (asyncBuffer != null) {
         asyncBuffer.close();
         asyncBuffer = null;
      }

      icons.clear();
      cachedBytes = 0;
      Arrays.fill(slotIcon, null);
      Arrays.fill(pendingKey, null);
      Arrays.fill(background, null);
      Arrays.fill(backgroundNext, null);
      Arrays.fill(stack, null);
      Arrays.fill(shownStack, null);
      mask = 0L;
      fakeMask = 0L;
      shownMask = 0L;
      shownAtlas = null;
      shownHover = -1;
      cursorStack = null;
      backgroundValid = false;
   }

   private static List<Object>[] newKeys(int n) {
      return new List[n];
   }
}
