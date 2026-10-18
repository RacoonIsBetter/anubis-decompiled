package net.anubis.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import net.anubis.client.gui.EgyptDraw;
import net.anubis.client.gui.ThemeImages;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.util.NameFilter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class MenuFrame {
   public static final String HEAD = "/assets/anubis/textures/gui/head.png";
   private ImDrawList bg;
   private ImDrawList fg;
   private float width;
   private float height;
   private float scale;
   private float time;
   private float dt;
   private float mouseX;
   private float mouseY;
   private boolean clicked;
   private String tooltip;
   private float tooltipX;
   private float tooltipY;

   public void begin(ImDrawList bg, ImDrawList fg, float width, float height, float scale, float time, float dt, float mouseX, float mouseY, boolean clicked) {
      this.bg = bg;
      this.fg = fg;
      this.width = width;
      this.height = height;
      this.scale = scale;
      this.time = time;
      this.dt = dt;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.clicked = clicked;
      this.tooltip = null;
   }

   public ImDrawList bg() {
      return this.bg;
   }

   public ImDrawList fg() {
      return this.fg;
   }

   public float width() {
      return this.width;
   }

   public float height() {
      return this.height;
   }

   public float scale() {
      return this.scale;
   }

   public float px(float pixels1080) {
      return pixels1080 * this.scale;
   }

   public float time() {
      return this.time;
   }

   public float dt() {
      return this.dt;
   }

   public float mouseX() {
      return this.mouseX;
   }

   public float mouseY() {
      return this.mouseY;
   }

   public boolean clicked() {
      return this.clicked;
   }

   public float intro(float delay) {
      return EgyptDraw.intro(this.time, delay);
   }

   public boolean inside(float x, float y, float w, float h) {
      return this.mouseX >= x && this.mouseX <= x + w && this.mouseY >= y && this.mouseY <= y + h;
   }

   public void tooltip(String text, float centerX, float y) {
      this.tooltip = text;
      this.tooltipX = centerX;
      this.tooltipY = y;
   }

   public String pendingTooltip() {
      return this.tooltip;
   }

   public float tooltipX() {
      return this.tooltipX;
   }

   public float tooltipY() {
      return this.tooltipY;
   }

   public static int col(int argb, float alpha) {
      return EgyptDraw.col(argb, alpha);
   }

   public static int col(int argb) {
      return EgyptDraw.col(argb, 1.0F);
   }

   public static int mix(int from, int to, float k) {
      return EgyptDraw.mix(from, to, k);
   }

   public ImFont menuFont() {
      return ImGuiManager.getInstance().menuFont();
   }

   public ImFont bodyFont() {
      return ImGuiManager.getInstance().menuBodyFont();
   }

   public ImFont tagFont() {
      return ImGuiManager.getInstance().menuTagFont();
   }

   public ImFont nameFont() {
      return ImGuiManager.getInstance().menuNameFont();
   }

   public ImFont itemFont() {
      return ImGuiManager.getInstance().menuItemFont();
   }

   public ImFont listFont() {
      return ImGuiManager.getInstance().moduleFont();
   }

   public ImFont detailFont() {
      return ImGuiManager.getInstance().hudLabelFont();
   }

   public void text(ImDrawList dl, ImFont font, float x, float y, int argb, float alpha, String text) {
      EgyptDraw.text(dl, font, x, y, text, col(argb, alpha));
   }

   public float textWidth(ImFont font, String text) {
      return EgyptDraw.textWidth(font, text);
   }

   public float textHeight(ImFont font) {
      return font != null ? font.getFontSize() : this.px(15.0F);
   }

   public float spacedWidth(ImFont font, String text, float spacing) {
      return EgyptDraw.spacedWidth(font, text, spacing);
   }

   public void spacedText(ImDrawList dl, ImFont font, float x, float y, float spacing, int argb, float alpha, String text) {
      for (int i = 0; i < text.length(); i++) {
         String glyph = String.valueOf(text.charAt(i));
         EgyptDraw.text(dl, font, x, y, glyph, col(argb, alpha));
         x += this.textWidth(font, glyph) + spacing;
      }
   }

   public ThemeImages.Texture head() {
      return MenuTextures.original("/assets/anubis/textures/gui/head.png");
   }

   public ThemeImages.Texture headMask() {
      return MenuTextures.mask("/assets/anubis/textures/gui/head.png");
   }

   public ThemeImages.Texture headBlur(int radius) {
      return MenuTextures.blurred("/assets/anubis/textures/gui/head.png", radius);
   }

   public void image(ImDrawList dl, ThemeImages.Texture texture, float x0, float y0, float x1, float y1, int argb, float alpha) {
      if (texture != null) {
         dl.addImage(texture.id(), x0, y0, x1, y1, 0.0F, 0.0F, 1.0F, 1.0F, col(argb, alpha));
      }
   }

   public String displayName() {
      return NameFilter.apply(MinecraftClient.getInstance().getSession().getUsername());
   }
}
