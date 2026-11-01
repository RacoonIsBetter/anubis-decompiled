package net.anubis.client.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.AnubisGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.gui.ThemeImages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.Cursor;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Environment(EnvType.CLIENT)
public final class ImGuiManager {
   private static final ImGuiManager INSTANCE = new ImGuiManager();
   private static final String MODULE_FONT_RESOURCE = "/assets/anubis/fonts/geist-medium.ttf";
   private static final String MENU_FONT_RESOURCE = "/assets/anubis/fonts/rajdhani-bold.ttf";
   public static final float MENU_FONT_SIZE = 24.0F;
   public static final float MENU_TAG_FONT_SIZE = 15.0F;
   public static final float MENU_BODY_FONT_SIZE = 18.0F;
   public static final float MENU_NAME_FONT_SIZE = 20.0F;
   public static final float MENU_ITEM_FONT_SIZE = 32.0F;
   public static final float HUD_VALUE_FONT_SIZE = 18.0F;
   public static final float HUD_TEXT_FONT_SIZE = 17.0F;
   public static final float HUD_LABEL_FONT_SIZE = 14.0F;
   private static final String EXTENDED_LATIN_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789ÇĞİÖŞÜçğıöşüÂâÊêÎîÔôÛûÀÁÄÈÉËÌÍÏÒÓÖÙÚÜÝàáäèéëìíïòóöùúüýÿ−∞";
   private static final float REFERENCE_HEIGHT = 1080.0F;
   private final ImGuiImplGlfw glfw = new ImGuiImplGlfw() {
      public void keyCallback(long window, int keycode, int scancode, int action, int mods) {
         boolean tracked = keycode >= 0 && keycode < ImGuiManager.this.imguiKeysDown.length;
         boolean feed = action == 0 ? tracked && ImGuiManager.this.imguiKeysDown[keycode] : ImGuiManager.this.feedsKeyPress(keycode, mods);
         if (!feed) {
            if (ImGuiManager.this.minecraftKeys != null) {
               ImGuiManager.this.minecraftKeys.invoke(window, keycode, scancode, action, mods);
            }
         } else {
            if (tracked) {
               ImGuiManager.this.imguiKeysDown[keycode] = action != 0;
            }

            super.keyCallback(window, keycode, scancode, action, mods);
         }
      }

      public void monitorCallback(long monitor, int event) {
         super.monitorCallback(monitor, event);
         if (ImGuiManager.this.minecraftMonitors != null) {
            ImGuiManager.this.minecraftMonitors.invoke(monitor, event);
         }
      }

      public void cursorPosCallback(long window, double x, double y) {
         super.cursorPosCallback(window, x, y);
         ImGuiManager.feedScaledCursor(x, y);
      }

      protected void updateMouseData() {
         super.updateMouseData();
         long handle = MinecraftClient.getInstance().getWindow().getHandle();
         if (GLFW.glfwGetWindowAttrib(handle, 131073) != 0) {
            double[] cx = new double[1];
            double[] cy = new double[1];
            GLFW.glfwGetCursorPos(handle, cx, cy);
            ImGuiManager.feedScaledCursor(cx[0], cy[0]);
         }
      }
   };
   private final AnubisGl3 gl3 = new AnubisGl3();
   private final boolean[] imguiKeysDown = new boolean[349];
   private GLFWKeyCallback minecraftKeys;
   private GLFWMonitorCallback minecraftMonitors;
   private boolean initialized;
   private boolean idleSkipped;
   private Runnable activeRenderer;
   private Runnable hudRenderer;
   private BooleanSupplier hudHasWork = () -> true;
   private Runnable toastRenderer;
   private BooleanSupplier toastHasWork = () -> false;
   private Runnable overlayRenderer;
   private Runnable streamRenderer;
   private BooleanSupplier streamHasWork = () -> false;
   private BooleanSupplier overlayHasWork = () -> false;
   private Runnable alertRenderer;
   private BooleanSupplier alertHasWork = () -> false;
   private float uiScale = 1.0F;
   private float userScale = 1.0F;
   private UiFont uiFont = UiFont.GEIST;
   private UiFont hudFont = UiFont.GEIST;
   private int frameCount;
   private float frameDelta;
   private ImFont categoryFont;
   private ImFont moduleFont;
   private ImFont titleFont;
   private ImFont smallFont;
   private ImFont hudValueFont;
   private ImFont hudTextFont;
   private ImFont hudLabelFont;
   private ImFont menuFont;
   private ImFont menuTagFont;
   private ImFont menuBodyFont;
   private ImFont menuNameFont;
   private ImFont menuItemFont;
   private short[] glyphRanges;

   private static void feedScaledCursor(double x, double y) {
      float sx = pixelsPerPointX();
      float sy = pixelsPerPointY();
      if (sx != 1.0F || sy != 1.0F) {
         ImGui.getIO().addMousePosEvent((float)(x * sx), (float)(y * sy));
      }
   }

   private static float pixelsPerPointX() {
      Window window = MinecraftClient.getInstance().getWindow();
      return window.getWidth() > 0 ? (float)window.getFramebufferWidth() / window.getWidth() : 1.0F;
   }

   private static float pixelsPerPointY() {
      Window window = MinecraftClient.getInstance().getWindow();
      return window.getHeight() > 0 ? (float)window.getFramebufferHeight() / window.getHeight() : 1.0F;
   }

   private ImGuiManager() {
   }

   public static ImGuiManager getInstance() {
      return INSTANCE;
   }

   public ImFont categoryFont() {
      return this.categoryFont;
   }

   public ImFont moduleFont() {
      return this.moduleFont;
   }

   public ImFont titleFont() {
      return this.titleFont;
   }

   public ImFont smallFont() {
      return this.smallFont;
   }

   public ImFont hudValueFont() {
      return this.hudValueFont;
   }

   public ImFont hudTextFont() {
      return this.hudTextFont;
   }

   public ImFont hudLabelFont() {
      return this.hudLabelFont;
   }

   public ImFont menuFont() {
      return this.menuFont;
   }

   public ImFont menuTagFont() {
      return this.menuTagFont;
   }

   public ImFont menuBodyFont() {
      return this.menuBodyFont;
   }

   public ImFont menuNameFont() {
      return this.menuNameFont;
   }

   public ImFont menuItemFont() {
      return this.menuItemFont;
   }

   public float uiScale() {
      return this.uiScale;
   }

   public int frameCount() {
      return this.frameCount;
   }

   public float frameDelta() {
      return this.frameDelta;
   }

   public void activate(Runnable renderer) {
      this.activeRenderer = renderer;
   }

   public boolean typing() {
      return this.initialized && ImGui.getIO().getWantTextInput();
   }

   public void requestCursor(DrawContext graphics) {
      if (this.initialized) {
         Cursor cursor = switch (ImGui.getMouseCursor()) {
            case 1 -> StandardCursors.IBEAM;
            case 2 -> StandardCursors.RESIZE_ALL;
            case 3 -> StandardCursors.RESIZE_NS;
            case 4 -> StandardCursors.RESIZE_EW;
            default -> null;
            case 7 -> StandardCursors.POINTING_HAND;
            case 8 -> StandardCursors.NOT_ALLOWED;
         };
         if (cursor != null) {
            graphics.setCursor(cursor);
         }
      }
   }

   private boolean feedsKeyPress(int keycode, int mods) {
      return this.activeRenderer != null && (keycode != 258 || (mods & 2) == 0);
   }

   public void deactivate() {
      this.activeRenderer = null;
      if (this.initialized) {
         String ini = ImGui.getIO().getIniFilename();
         if (ini != null) {
            ImGui.saveIniSettingsToDisk(ini);
         }
      }
   }

   public void setHudRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.hudRenderer = renderer;
      this.hudHasWork = hasWork;
   }

   public void setToastRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.toastRenderer = renderer;
      this.toastHasWork = hasWork;
   }

   public void setStreamRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.streamRenderer = renderer;
      this.streamHasWork = hasWork;
   }

   public void setOverlayRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.overlayRenderer = renderer;
      this.overlayHasWork = hasWork;
   }

   public void setAlertRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.alertRenderer = renderer;
      this.alertHasWork = hasWork;
   }

   public void renderIfActive() {
      boolean hud = this.hudRenderer != null && this.hudHasWork.getAsBoolean();
      boolean toasts = this.toastRenderer != null && this.toastHasWork.getAsBoolean();
      boolean overlay = this.overlayRenderer != null && this.overlayHasWork.getAsBoolean();
      boolean alert = this.alertRenderer != null && this.alertHasWork.getAsBoolean();
      boolean stream = this.streamRenderer != null && this.streamHasWork.getAsBoolean();
      if (this.activeRenderer == null && !hud && !toasts && !overlay && !alert && !stream) {
         if (this.initialized) {
            ImGui.getIO().clearEventsQueue();
            this.idleSkipped = true;
         }
      } else {
         this.initIfNeeded();
         this.rebuildFontsIfNeeded();
         if (this.idleSkipped) {
            ImGuiIO io = ImGui.getIO();
            io.clearInputKeys();
            io.clearInputMouse();
            Arrays.fill(this.imguiKeysDown, false);
            this.idleSkipped = false;
         }

         MinecraftClient mc = MinecraftClient.getInstance();
         int w = mc.getWindow().getFramebufferWidth();
         int h = mc.getWindow().getFramebufferHeight();
         GL30.glBindFramebuffer(36160, 0);
         GL11.glViewport(0, 0, w, h);
         GlStateManager._viewport(0, 0, w, h);
         this.glfw.newFrame();
         this.frameDelta = Math.min(ImGui.getIO().getDeltaTime(), 0.1F);
         ImGui.getIO().setDeltaTime(this.frameDelta);
         this.updateDisplayMetrics();
         this.gl3.newFrame();
         ThemeImages.get().beginFrame();
         ImGui.newFrame();
         this.frameCount++;
         boolean ended = false;

         try {
            GuiPalette.refresh();
            if (this.streamRenderer != null) {
               this.streamRenderer.run();
            }

            if (this.hudRenderer != null) {
               this.hudRenderer.run();
            }

            if (this.activeRenderer != null) {
               this.activeRenderer.run();
            }

            if (this.toastRenderer != null) {
               this.toastRenderer.run();
            }

            if (this.alertRenderer != null) {
               this.alertRenderer.run();
            }

            if (this.overlayRenderer != null) {
               this.overlayRenderer.run();
            }

            ImGui.render();
            ended = true;
            this.gl3.renderDrawData(ImGui.getDrawData());
         } finally {
            try {
               if (!ended) {
                  ImGui.endFrame();
               }
            } finally {
               ThemeImages.get().endFrame();
            }
         }
      }
   }

   public void dispose() {
      if (this.initialized) {
         this.gl3.shutdown();
         this.glfw.shutdown();
         ImGui.destroyContext();
         this.categoryFont = null;
         this.moduleFont = null;
         this.titleFont = null;
         this.smallFont = null;
         this.hudValueFont = null;
         this.hudTextFont = null;
         this.hudLabelFont = null;
         this.menuFont = null;
         this.menuTagFont = null;
         this.menuBodyFont = null;
         this.menuNameFont = null;
         this.menuItemFont = null;
         this.initialized = false;
      }
   }

   private void initIfNeeded() {
      if (!this.initialized) {
         long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
         ImGui.createContext();
         ImGuiIO io = ImGui.getIO();
         io.setIniFilename(iniPath());
         io.addConfigFlags(1);
         io.addConfigFlags(32);
         ImGui.styleColorsDark();
         this.uiScale = this.computeScale(this.uiScale);
         this.uiFont = chosenUiFont();
         this.hudFont = chosenHudFont();
         this.loadFonts(io);
         this.minecraftKeys = GLFW.glfwSetKeyCallback(windowHandle, null);
         GLFW.glfwSetKeyCallback(windowHandle, this.minecraftKeys);
         this.minecraftMonitors = GLFW.glfwSetMonitorCallback(null);
         GLFW.glfwSetMonitorCallback(this.minecraftMonitors);
         this.glfw.init(windowHandle, true);
         this.gl3.init(null);
         this.initialized = true;
      }
   }

   private void rebuildFontsIfNeeded() {
      float scale = this.computeScale(this.uiScale);
      UiFont ui = chosenUiFont();
      UiFont hud = chosenHudFont();
      if (scale != this.uiScale || ui != this.uiFont || hud != this.hudFont) {
         this.uiScale = scale;
         this.uiFont = ui;
         this.hudFont = hud;
         ImGuiIO io = ImGui.getIO();
         io.getFonts().clear();
         this.loadFonts(io);
         this.gl3.destroyFontsTexture();
         this.gl3.createFontsTexture();
      }
   }

   private static UiFont chosenUiFont() {
      return UiFont.of(ThemeStore.get().font.get());
   }

   private static UiFont chosenHudFont() {
      return UiFont.of(HudTheme.get().font.get());
   }

   private float computeScale(float current) {
      int height = MinecraftClient.getInstance().getWindow().getFramebufferHeight();
      if (height < 200) {
         return current;
      }

      if (!this.initialized || !ImGui.getIO().getMouseDown(0)) {
         this.userScale = ThemeStore.get().uiScale.get();
      }

      float raw = Math.max(0.6F, Math.min(3.5F, height / 1080.0F * this.userScale));
      return Math.round(raw * 20.0F) / 20.0F;
   }

   private static String iniPath() {
      try {
         Path dir = FabricLoader.getInstance().getConfigDir().resolve("anubis");
         Files.createDirectories(dir);
         return dir.resolve("gui.ini").toString();
      } catch (IOException | RuntimeException e) {
         System.err.println("[Anubis] GUI layout will not be saved: " + e.getMessage());
         return null;
      }
   }

   private void updateDisplayMetrics() {
      MinecraftClient client = MinecraftClient.getInstance();
      int w = Math.max(1, client.getWindow().getFramebufferWidth());
      int h = Math.max(1, client.getWindow().getFramebufferHeight());
      ImGuiIO io = ImGui.getIO();
      io.setDisplaySize(w, h);
      io.setDisplayFramebufferScale(1.0F, 1.0F);
   }

   private void loadFonts(ImGuiIO io) {
      if (this.glyphRanges == null) {
         this.glyphRanges = buildGlyphRanges(io);
      }

      try {
         io.getFonts().setFreeTypeRenderer(true);
      } catch (Throwable t) {
         System.err.println("[Anubis] FreeType unavailable, using stb_truetype: " + t.getMessage());
      }

      if (!this.buildFonts(io, this.uiFont, this.hudFont)) {
         if (this.uiFont != UiFont.GEIST || this.hudFont != UiFont.GEIST) {
            System.err.println("[Anubis] Couldn't build " + this.uiFont.label + " / " + this.hudFont.label + " fonts, using Geist");
            io.getFonts().clear();
            if (this.buildFonts(io, UiFont.GEIST, UiFont.GEIST)) {
               return;
            }
         }

         throw new IllegalStateException("ImGui font atlas could not be built");
      }
   }

   private boolean buildFonts(ImGuiIO io, UiFont ui, UiFont hud) {
      float s = this.uiScale;
      this.moduleFont = this.loadFont(io, ui, false, 16.0F * s);
      this.categoryFont = this.loadFont(io, ui, true, 17.0F * s);
      this.titleFont = this.loadFont(io, ui, true, 24.0F * s);
      this.smallFont = this.loadFont(io, ui, false, 13.0F * s);
      this.hudValueFont = this.loadFont(io, hud, true, 18.0F * s);
      this.hudTextFont = this.loadFont(io, hud, true, 17.0F * s);
      this.hudLabelFont = this.loadFont(io, hud, false, 14.0F * s);
      this.menuFont = loadFont(io, "/assets/anubis/fonts/rajdhani-bold.ttf", 24.0F * s, this.glyphRanges);
      this.menuTagFont = loadFont(io, "/assets/anubis/fonts/rajdhani-bold.ttf", 15.0F * s, this.glyphRanges);
      this.menuBodyFont = loadFont(io, "/assets/anubis/fonts/geist-medium.ttf", 18.0F * s, this.glyphRanges);
      this.menuNameFont = loadFont(io, "/assets/anubis/fonts/rajdhani-bold.ttf", 20.0F * s, this.glyphRanges);
      this.menuItemFont = loadFont(io, "/assets/anubis/fonts/rajdhani-bold.ttf", 32.0F * s, this.glyphRanges);
      if (this.moduleFont == null && this.categoryFont == null) {
         io.getFonts().addFontDefault();
      } else if (this.moduleFont != null) {
         io.setFontDefault(this.moduleFont);
      }

      return io.getFonts().build();
   }

   private static short[] buildGlyphRanges(ImGuiIO io) {
      ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
      builder.addRanges(io.getFonts().getGlyphRangesDefault());
      builder.addRanges(io.getFonts().getGlyphRangesVietnamese());
      builder.addText("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789ÇĞİÖŞÜçğıöşüÂâÊêÎîÔôÛûÀÁÄÈÉËÌÍÏÒÓÖÙÚÜÝàáäèéëìíïòóöùúüýÿ−∞");
      return builder.buildRanges();
   }

   private ImFont loadFont(ImGuiIO io, UiFont family, boolean bold, float sizePixels) {
      float size = family.size(sizePixels);
      float offsetY = family.offsetY(size);
      ImFont font = loadFont(io, family.resource(bold), size, this.glyphRanges, family.flags(), offsetY, false);
      if (font == null) {
         return family == UiFont.GEIST ? null : this.loadFont(io, UiFont.GEIST, bold, sizePixels);
      }

      if (family != UiFont.GEIST) {
         loadFont(io, UiFont.GEIST.resource(bold), family.fallbackSize(size), this.glyphRanges, UiFont.GEIST.flags(), offsetY, true);
      }

      return font;
   }

   private static ImFont loadFont(ImGuiIO io, String resourcePath, float sizePixels, short[] ranges) {
      return loadFont(io, resourcePath, sizePixels, ranges, 8, 0.0F, false);
   }

   private static ImFont loadFont(ImGuiIO io, String resourcePath, float sizePixels, short[] ranges, int flags, float offsetY, boolean merge) {
      try (InputStream in = ImGuiManager.class.getResourceAsStream(resourcePath)) {
         if (in == null) {
            System.err.println("[Anubis] Font resource not found: " + resourcePath);
            return null;
         }

         byte[] data = in.readAllBytes();
         ImFontConfig config = new ImFontConfig();

         try {
            config.setOversampleH(3);
            config.setOversampleV(1);
            config.setPixelSnapH(true);
            config.setFontBuilderFlags(flags);
            config.setGlyphOffset(0.0F, offsetY);
            config.setMergeMode(merge);
            return io.getFonts().addFontFromMemoryTTF(data, Math.round(sizePixels), config, ranges);
         } finally {
            config.destroy();
         }
      } catch (IOException | RuntimeException e) {
         System.err.println("[Anubis] Failed to load font " + resourcePath + ": " + e.getMessage());
         return null;
      }
   }
}
