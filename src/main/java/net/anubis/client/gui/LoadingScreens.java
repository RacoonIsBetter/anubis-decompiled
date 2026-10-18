package net.anubis.client.gui;

import com.example.client.mixin.AccessorLevelLoadingScreen;
import com.example.client.mixin.AccessorLoadingOverlay;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import java.util.Locale;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;

@Environment(EnvType.CLIENT)
public final class LoadingScreens {
   private static final String HEAD_RESOURCE = "/assets/anubis/textures/gui/head.png";
   private static final int BACKGROUND_RGB = 722950;
   private static final float FADE_OUT_SECONDS = 0.45F;
   private static final long ORIGIN = System.nanoTime();
   private static long lastActiveAt = -1L;
   private static float lastProgress;
   private static float lastAlpha;
   private static String lastStatus = "";
   private static float shownFill;

   private LoadingScreens() {
   }

   public static int backgroundColor(int argb) {
      return !AnubisTitleScreen.enabled() ? argb : argb & 0xFF000000 | 722950;
   }

   public static boolean hasWork() {
      if (!AnubisTitleScreen.enabled()) {
         return false;
      }

      if (lastActiveAt >= 0L) {
         return true;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.getOverlay() instanceof SplashOverlay
         || mc.currentScreen instanceof LevelLoadingScreen
         || mc.currentScreen instanceof MessageScreen
         || mc.currentScreen instanceof ProgressScreen;
   }

   public static void render() {
      if (AnubisTitleScreen.enabled()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         long now = System.nanoTime();
         float alpha;
         float progress;
         String status;
         if (mc.getOverlay() instanceof SplashOverlay overlay) {
            AccessorLoadingOverlay state = (AccessorLoadingOverlay)overlay;
            alpha = reloadAlpha(state);
            progress = state.anubis$getProgress();
            status = "Loading";
         } else {
            Screen screen = mc.currentScreen;
            if (screen instanceof LevelLoadingScreen loading) {
               progress = ((AccessorLevelLoadingScreen)loading).anubis$getProgress();
               status = "Entering world";
            } else {
               if (!(screen instanceof MessageScreen) && !(screen instanceof ProgressScreen)) {
                  drawFadingOut(now);
                  return;
               }

               progress = -1.0F;
               String title = screen.getTitle().getString();
               status = title.isBlank() ? "Loading" : title;
            }

            alpha = 1.0F;
         }

         lastActiveAt = now;
         lastProgress = progress;
         lastStatus = status;
         lastAlpha = alpha;
         draw(alpha, progress, status, now);
      }
   }

   private static void drawFadingOut(long now) {
      if (lastActiveAt >= 0L) {
         float elapsed = seconds(now - lastActiveAt);
         if (elapsed >= 0.45F) {
            lastActiveAt = -1L;
         } else {
            draw(lastAlpha * (1.0F - elapsed / 0.45F), lastProgress < 0.0F ? -1.0F : 1.0F, lastStatus, now);
         }
      }
   }

   private static float reloadAlpha(AccessorLoadingOverlay state) {
      long millis = Util.getMeasuringTimeMs();
      long fadeOutStart = state.anubis$getFadeOutStart();
      long fadeInStart = state.anubis$getFadeInStart();
      float fadeOut = fadeOutStart > -1L ? (float)(millis - fadeOutStart) / 1000.0F : -1.0F;
      float fadeIn = fadeInStart > -1L ? (float)(millis - fadeInStart) / 500.0F : -1.0F;
      if (fadeOut >= 1.0F) {
         return 1.0F - clamp01(fadeOut - 1.0F);
      } else {
         return state.anubis$isFadeIn() ? clamp01(fadeIn) : 1.0F;
      }
   }

   private static void draw(float a, float progress, String status, long now) {
      if (!(a <= 0.001F)) {
         ImGuiIO io = ImGui.getIO();
         float sw = io.getDisplaySizeX();
         float sh = io.getDisplaySizeY();
         float s = ImGuiManager.getInstance().uiScale();
         float t = seconds(now - ORIGIN);
         float dt = Math.max(0.001F, io.getDeltaTime());
         ImDrawList dl = ImGui.getForegroundDrawList();
         dl.addRectFilledMultiColor(
            0.0F, 0.0F, sw, sh, EgyptDraw.col(-15857145, a), EgyptDraw.col(-15857145, a), EgyptDraw.col(-16514302, a), EgyptDraw.col(-16514302, a)
         );
         float headSize = Math.min(sh * 0.3F, 290.0F * s);
         float sunR = headSize * 0.62F;
         float cx = sw * 0.5F;
         float cy = sh * 0.42F;
         EgyptDraw.sunDisk(dl, cx, cy, sunR, t * 0.12F, s, a);
         float target = progress >= 0.0F ? clamp01(progress) : (float)(t * 0.45 % 1.0);
         if (!(progress < 0.0F) && !(target < shownFill)) {
            shownFill = shownFill + (target - shownFill) * Math.min(1.0F, dt * 6.0F);
         } else {
            shownFill = target;
         }

         ThemeImages.Texture head = EgyptDraw.texture("/assets/anubis/textures/gui/head.png");
         float x0 = cx - headSize * 0.5F;
         float y0 = cy - headSize * 0.5F;
         float x1 = x0 + headSize;
         float y1 = y0 + headSize;
         if (head != null) {
            EgyptDraw.glow(dl, cx, y1 - headSize * shownFill, headSize * 0.45F, -871878, 0.12F * a);
            dl.addImage(head.id(), x0, y0, x1, y1, 0.0F, 0.0F, 1.0F, 1.0F, EgyptDraw.col(-9743568, 0.35F * a));
            float clipTop = y1 - headSize * shownFill;
            dl.pushClipRect(x0, clipTop, x1, y1, true);
            dl.addImage(head.id(), x0, y0, x1, y1, 0.0F, 0.0F, 1.0F, 1.0F, EgyptDraw.col(-1, a));
            dl.popClipRect();
         }

         ImFont big = ImGuiManager.getInstance().menuFont();
         ImFont small = ImGuiManager.getInstance().menuTagFont();
         float nameY = cy + sunR * 1.34F + 14.0F * s;
         EgyptDraw.drawSpaced(dl, big, cx, nameY, "ANUBIS CLIENT", 5.0F * s, -871878, a, true);
         float bigSize = big != null ? big.getFontSize() : 24.0F * s;
         float barW = 260.0F * s;
         float barY = nameY + bigSize + 16.0F * s;
         float line = Math.max(1.0F, 1.5F * s);
         float bx = cx - barW * 0.5F;
         dl.addLine(bx, barY, bx + barW, barY, EgyptDraw.col(-871878, 0.18F * a), line);
         float end = progress >= 0.0F ? bx + barW * shownFill : bx + barW * (0.5F + 0.5F * (float)Math.sin(t * 2.2F));
         float start = progress >= 0.0F ? bx : Math.max(bx, end - barW * 0.25F);
         dl.addLine(start, barY, end, barY, EgyptDraw.col(-11654, a), line);
         float d = 3.5F * s;
         dl.addQuadFilled(end, barY - d, end + d, barY, end, barY + d, end - d, barY, EgyptDraw.col(-11654, a));
         String line2 = progress >= 0.0F ? status + "  ·  " + Math.round(clamp01(progress) * 100.0F) + "%" : status;
         EgyptDraw.drawSpaced(dl, small, cx, barY + 12.0F * s, line2.toUpperCase(Locale.ROOT), 2.0F * s, -7372954, a, false);
      }
   }

   private static float seconds(long nanos) {
      return (float)nanos / 1.0E9F;
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }
}
