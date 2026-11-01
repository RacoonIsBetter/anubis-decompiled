package net.anubis.client.gui;

import com.mojang.blaze3d.textures.GpuTexture;
import imgui.ImDrawList;
import imgui.ImFont;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.Surface;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.client.HudModule;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public final class HudPanel {
   public static final int LABEL = -6052949;
   public static final int VALUE = -1;
   public static final int WARN = -47814;
   public static final int GOOD = -13445301;
   public static final int CAUTION = -19648;
   private static final float GRIP = 14.0F;
   private static final float HEADER_H = 20.0F;
   private static final float ROW_H = 22.0F;
   private static final float PAD = 8.0F;
   private static final float FACE = 16.0F;
   private static final float GAP_FACE = 6.0F;
   private static final float GAP_COUNT = 5.0F;
   private static final float GAP_RIGHT = 14.0F;
   private static final float ROWS_RADIUS = 6.0F;
   private static final float SKIN_FACE_U = 0.125F;
   private static final float SKIN_HAT_U = 0.625F;
   private static final float SKIN_TOP_V = 0.125F;
   private static final float SKIN_PART = 0.125F;
   private static final Map<String, ProfileComponent> PROFILES = new HashMap<>();
   private static final Set<GpuTexture> NEAREST = Collections.newSetFromMap(new WeakHashMap<>());
   private final NumberSetting x;
   private final NumberSetting y;
   private final NumberSetting scale;
   private final NumberSetting opacity;
   private boolean dragging;
   private boolean resizing;
   private boolean wasDown;
   private float grabX;
   private float grabY;
   private float scaleAtGrab;
   private float widthAtGrab;
   private float alpha = 1.0F;

   public HudPanel(NumberSetting x, NumberSetting y, NumberSetting scale, NumberSetting opacity) {
      this.x = x;
      this.y = y;
      this.scale = scale;
      this.opacity = opacity;
      x.visibleWhen(() -> false);
      y.visibleWhen(() -> false);
   }

   public static NumberSetting coordinate(String name) {
      return new NumberSetting(name, -1.0F, -1.0F, 10000.0F).step(1.0F);
   }

   public static NumberSetting opacitySetting() {
      return new NumberSetting("Opacity", 100.0F, 10.0F, 100.0F).step(5.0F);
   }

   public float[] place(ImDrawList dl, float w, float h, float screenW, float screenH, float defaultX, float defaultY) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.alpha = Math.max(0.0F, Math.min(1.0F, this.opacity.get() / 100.0F));
      float px = this.x.get() < 0.0F ? defaultX : this.x.get();
      float py = this.y.get() < 0.0F ? defaultY : this.y.get();
      boolean chat = mc.currentScreen instanceof ChatScreen;
      boolean down = chat && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1;
      float mx = (float)(mc.mouse.getX() * screenW / Math.max(1, mc.getWindow().getWidth()));
      float my = (float)(mc.mouse.getY() * screenH / Math.max(1, mc.getWindow().getHeight()));
      if (down && !this.wasDown) {
         if (this.scale != null && mx >= px + w - 14.0F && mx <= px + w && my >= py + h - 14.0F && my <= py + h) {
            this.resizing = true;
            this.scaleAtGrab = this.scale.get();
            this.widthAtGrab = Math.max(1.0F, w);
            this.grabX = px + w - mx;
         } else if (mx >= px && mx <= px + w && my >= py && my <= py + h) {
            this.dragging = true;
            this.grabX = mx - px;
            this.grabY = my - py;
         }
      }

      if (this.resizing) {
         this.scale.set(Math.max(this.scale.min(), Math.min(this.scale.max(), this.scaleAtGrab * (mx + this.grabX - px) / this.widthAtGrab)));
      }

      if (this.dragging) {
         px = mx - this.grabX;
         py = my - this.grabY;
      }

      px = Math.max(0.0F, Math.min(px, screenW - w));
      py = Math.max(0.0F, Math.min(py, screenH - h));
      if (!down) {
         if (this.dragging) {
            this.x.set(Math.round(px));
            this.y.set(Math.round(py));
         }

         this.dragging = false;
         this.resizing = false;
      }

      this.wasDown = down;
      if (chat) {
         dl.addRect(
            px - 1.0F, py - 1.0F, px + w + 1.0F, py + h + 1.0F, GuiPalette.abgr(!this.dragging && !this.resizing ? 1627389951 : -1056964609), 8.0F, 0, 1.0F
         );
         if (this.scale != null) {
            for (int i = 1; i <= 3; i++) {
               float o = i * 4.0F;
               dl.addLine(px + w - o, py + h - 2.0F, px + w - 2.0F, py + h - o, GuiPalette.abgr(-788529153), 1.5F);
            }
         }
      }

      return new float[]{px, py};
   }

   public void card(
      ImDrawList dl,
      String title,
      String value,
      int valueColor,
      List<HudPanel.Line> lines,
      String surfaceId,
      float screenW,
      float screenH,
      float across,
      float defaultY
   ) {
      ImGuiManager m = ImGuiManager.getInstance();
      float s = (this.scale == null ? 1.0F : this.scale.get()) * m.uiScale();
      ImFont labelFont = m.hudLabelFont();
      ImFont textFont = m.hudTextFont();
      int labelPx = HudChrome.px(14.0F, s);
      int textPx = HudChrome.px(17.0F, s);
      float pad = 8.0F * s;
      float headerH = 20.0F * s;
      float rowH = 22.0F * s;
      float face = faceSize(s);
      float faceGap = 6.0F * s;
      boolean faces = false;

      for (HudPanel.Line line : lines) {
         faces |= line.face() != 0;
      }

      float textX = faces ? face + faceGap : 0.0F;
      float titleW = HudChrome.width(labelFont, labelPx, title);
      float inner = titleW + (value.isEmpty() ? 0.0F : 5.0F * s + HudChrome.width(textFont, textPx, value));

      for (HudPanel.Line line : lines) {
         float lineW = textX + HudChrome.width(textFont, textPx, line.text());
         if (!line.right().isEmpty()) {
            lineW += 14.0F * s + HudChrome.width(labelFont, labelPx, line.right());
         }

         inner = Math.max(inner, lineW);
      }

      float w = (float)Math.ceil(inner + pad * 2.0F);
      float h = Math.round(headerH + rowH * lines.size());
      float margin = 10.0F * s;
      float[] at = this.place(dl, w, h, screenW, screenH, margin + (screenW - w - margin * 2.0F) * across, defaultY);
      float x = Math.round(at[0]);
      float y = Math.round(at[1]);
      Surface surface = HudTheme.get().forElementId(surfaceId);
      float r = Math.min(HudTheme.get().rounding.get(), 6.0F) * s;
      HudModule hud = ModuleRegistry.get(HudModule.class);
      if (hud == null || hud.shadow()) {
         HudChrome.shadow(dl, x, y, w, h, r, this.alpha, s);
      }

      if (surface != null) {
         dl.addRectFilled(x, y, x + w, y + h, HudChrome.scaleAlpha(GuiPalette.fill(surface), this.alpha), r);
         SurfaceBackground.draw(dl, surface, x, y, w, h, r, this.alpha);
      } else {
         dl.addRectFilled(x, y, x + w, y + h, HudChrome.scaleAlpha(HudChrome.CHIP_FILL, this.alpha), r);
      }

      float labelOffset = HudChrome.baselineOffset(textFont, textPx, labelFont, labelPx);
      float headTop = y + (float)Math.floor((headerH - textPx) * 0.5F);
      HudChrome.text(dl, labelFont, labelPx, x + pad, headTop + labelOffset, this.abgr(-6052949), title, true);
      if (!value.isEmpty()) {
         HudChrome.text(dl, textFont, textPx, x + pad + titleW + 5.0F * s, headTop, this.abgr(valueColor), value, true);
      }

      float textInset = (float)Math.floor((rowH - textPx) * 0.5F);

      for (int i = 0; i < lines.size(); i++) {
         HudPanel.Line line = lines.get(i);
         float top = Math.round(y + headerH + rowH * i);
         float ty = top + textInset;
         if (line.face() != 0) {
            this.drawFace(dl, line.face(), Math.round(x + pad), Math.round(top + (rowH - face) * 0.5F), face, s);
         }

         HudChrome.text(dl, textFont, textPx, x + pad + textX, ty, this.abgr(line.color()), line.text(), true);
         if (!line.right().isEmpty()) {
            float rx = x + w - pad - HudChrome.width(labelFont, labelPx, line.right());
            HudChrome.text(dl, labelFont, labelPx, rx, ty + labelOffset, this.abgr(line.rightColor()), line.right(), true);
         }
      }
   }

   private static float faceSize(float s) {
      int size = Math.round(Math.round(16.0F * s) / 8.0F) * 8;
      if (size > 22.0F * s - 4.0F * s) {
         size -= 8;
      }

      return Math.max(8, size);
   }

   public void drawFace(ImDrawList dl, int texture, float fx, float fy, float size, float s) {
      float r = Math.max(1.0F, Math.round(2.0F * s));
      int tint = this.abgr(-1);
      dl.addImageRounded(texture, fx, fy, fx + size, fy + size, 0.125F, 0.125F, 0.25F, 0.25F, tint, r, 240);
      dl.addImageRounded(texture, fx, fy, fx + size, fy + size, 0.625F, 0.125F, 0.75F, 0.25F, tint, r, 240);
      dl.addRect(fx, fy, fx + size, fy + size, HudChrome.scaleAlpha(402653183, this.alpha), r, 0, 1.0F);
   }

   public static int face(String name) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(name);
      Identifier skin = info != null
         ? info.getSkinTextures().body().texturePath()
         : mc.getPlayerSkinCache()
            .get(PROFILES.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> ProfileComponent.ofDynamic(name)))
            .getTextures()
            .body()
            .texturePath();

      GpuTexture texture;
      try {
         texture = mc.getTextureManager().getTexture(skin).getGlTexture();
      } catch (IllegalStateException notUploaded) {
         return 0;
      }

      if (texture instanceof GlTexture gl && !gl.isClosed()) {
         if (NEAREST.add(texture)) {
            int bound = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, gl.getGlId());
            GL11.glTexParameteri(3553, 10241, 9728);
            GL11.glTexParameteri(3553, 10240, 9728);
            GL11.glBindTexture(3553, bound);
         }

         return gl.getGlId();
      } else {
         return 0;
      }
   }

   public int abgr(int argb) {
      int a = Math.round((argb >>> 24 & 0xFF) * this.alpha);
      return GuiPalette.abgr(a << 24 | argb & 16777215);
   }

   @Environment(EnvType.CLIENT)
   public record Line(int face, String text, int color, String right, int rightColor) {
      public Line(String text, int color) {
         this(0, text, color, "", -6052949);
      }
   }
}
