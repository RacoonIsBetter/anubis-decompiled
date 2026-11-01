package net.anubis.client.gui;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.toast.Toast.Visibility;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class ChallengeToast implements Toast {
   public static final String RAINBOW = "anubis:rainbow";
   private static final long RAINBOW_MILLIS = 2500L;
   private static final int BACKGROUND = -266921698;
   private static final int TITLE_COLOR = -986891;
   private static final int MESSAGE_COLOR = -5656902;
   private static final int COUNT_COLOR = -6643540;
   private static final int TIMER_TRACK = 654311423;
   private static final int TIMER_FILL = -1711276033;
   private static final int TEXT_X = 30;
   private static final int TEXT_PAD = 7;
   private static final int RIGHT_GAP = 3;
   private static final int MAX_WIDTH = 240;
   private static final int LINE_HEIGHT = 10;
   private static final int MAX_LINES = 5;
   private static final long DISPLAY_MILLIS = 5000L;
   private final Object token;
   private final ItemStack icon;
   private final boolean sound;
   private Text title;
   private Text message;
   private int width;
   private int height = 32;
   private List<OrderedText> lines = List.of();
   private int count = 1;
   private boolean changed;
   private boolean hiding;
   private long changedAt;
   private Visibility wanted = Visibility.HIDE;

   public ChallengeToast(ItemStack icon, Text title, Text message, boolean sound) {
      this(TYPE, icon, title, message, sound);
   }

   private ChallengeToast(Object token, ItemStack icon, Text title, Text message, boolean sound) {
      this.token = token;
      this.icon = icon.copy();
      this.title = title;
      this.message = message;
      this.sound = sound;
      this.fit();
   }

   public static void addOrUpdate(ItemStack icon, String title, Text message, boolean sound) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.isOnThread()) {
         mc.execute(() -> addOrUpdate(icon, title, message, sound));
      } else {
         ToastManager toasts = mc.getToastManager();
         ChallengeToast toast = toasts.getToast(ChallengeToast.class, title);
         if (toast == null) {
            toasts.add(new ChallengeToast(title, icon, Text.literal(title), message, sound));
         } else {
            toast.count++;
            toast.message = message;
            toast.changed = true;
            toast.fit();
         }
      }
   }

   private String countLabel() {
      return this.count > 1 ? " x" + this.count : "";
   }

   private void fit() {
      TextRenderer font = MinecraftClient.getInstance().textRenderer;
      int text = Math.max(font.getWidth(this.title) + font.getWidth(this.countLabel()), font.getWidth(this.message));
      this.width = MathHelper.clamp(30 + text + 7, 160, 240);
      List<OrderedText> split = this.message.getString().isEmpty() ? List.of() : font.wrapLines(this.message, this.width - 30 - 7);
      this.lines = split.size() > 5 ? List.copyOf(split.subList(0, 5)) : split;
      this.height = Math.max(32, 18 + this.lines.size() * 10 + 4);
   }

   @Override
   public Object getType() {
      return this.token;
   }

   @Override
   public SoundEvent getSoundEvent() {
      return this.sound ? SoundEvents.BLOCK_NOTE_BLOCK_BELL.value() : null;
   }

   @Override
   public Visibility getVisibility() {
      return this.wanted;
   }

   @Override
   public int getWidth() {
      return this.width;
   }

   @Override
   public int getHeight() {
      return this.height;
   }

   @Override
   public float getYPos(int topIndex) {
      float y = topIndex * 32.0F;
      float[] bar = HudRenderer.infoBarBox();
      if (bar == null) {
         return y;
      }

      Window window = MinecraftClient.getInstance().getWindow();
      float scale = window.getScaleFactor();
      float left = this.getXPos(window.getScaledWidth(), 1.0F);
      float right = left + this.width;
      float barLeft = bar[0] / scale;
      float barTop = bar[1] / scale;
      float barRight = bar[2] / scale;
      float barBottom = bar[3] / scale;
      return !(barTop > 32.0F) && !(right <= barLeft) && !(left >= barRight) ? y + barBottom + 2.0F : y;
   }

   protected long displayMillis() {
      return 5000L;
   }

   protected final int cardRight() {
      return this.width - 3;
   }

   protected final int cardBottom() {
      return this.height - 1;
   }

   @Override
   public void update(ToastManager manager, long time) {
      if (!this.hiding) {
         if (this.changed) {
            this.changedAt = time;
            this.changed = false;
         }

         this.hiding = time - this.changedAt >= this.displayMillis() * manager.getNotificationDisplayTimeMultiplier();
         this.wanted = this.hiding ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   @Override
   public void onFinishedRendering() {
      if (this.changed) {
         this.hiding = false;
         MinecraftClient.getInstance().getToastManager().add(this);
      }
   }

   @Override
   public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
      int right = this.width - 3;
      int bottom = this.height - 1;
      rounded(context, 0, 1, right, bottom, -266921698);
      this.drawTimer(context, startTime, right, bottom);
      boolean single = this.lines.isEmpty();
      this.drawTitle(context, textRenderer, single ? 12 : 7);

      for (int i = 0; i < this.lines.size(); i++) {
         context.drawText(textRenderer, rainbowed(this.lines.get(i)), 30, 18 + i * 10, -5656902, true);
      }

      this.renderIcon(context, this.icon);
   }

   protected void renderIcon(DrawContext graphics, ItemStack icon) {
      graphics.drawItemWithoutEntity(icon, 8, 8);
   }

   private void drawTimer(DrawContext graphics, long visibleFor, int right, int bottom) {
      double duration = this.displayMillis() * MinecraftClient.getInstance().getToastManager().getNotificationDisplayTimeMultiplier();
      float left = 1.0F - (float)MathHelper.clamp((visibleFor - this.changedAt) / duration, 0.0, 1.0);
      int x0 = 4;
      int x1 = right - 4;
      int y = bottom - 2;
      graphics.fill(x0, y, x1, y + 1, 654311423);
      graphics.fill(x0, y, x0 + Math.round((x1 - x0) * left), y + 1, -1711276033);
   }

   private static void rounded(DrawContext graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
      graphics.fill(x0, y0 + 2, x1, y1 - 2, color);
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
      graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
   }

   private void drawTitle(DrawContext graphics, TextRenderer font, int y) {
      String suffix = this.countLabel();
      int room = this.width - 30 - 7 - font.getWidth(suffix);
      OrderedText shown = this.title.asOrderedText();
      if (font.getWidth(this.title) > room) {
         Text ellipsis = Text.literal("…");
         StringVisitable cut = font.trimToWidth(this.title, room - font.getWidth(ellipsis));
         shown = Language.getInstance().reorder(StringVisitable.concat(cut, ellipsis));
      }

      graphics.drawText(font, rainbowed(shown), 30, y, -986891, true);
      if (!suffix.isEmpty()) {
         graphics.drawText(font, suffix, 30 + font.getWidth(shown), y, -6643540, true);
      }
   }

   private static OrderedText rainbowed(OrderedText text) {
      float turn = (float)(Util.getMeasuringTimeMs() % 2500L) / 2500.0F;
      return sink -> text.accept((index, style, codePoint) -> {
         if (!"anubis:rainbow".equals(style.getInsertion())) {
            return sink.accept(index, style, codePoint);
         }

         float hue = index * 0.045F - turn;
         int rgb = MathHelper.hsvToRgb(hue - (float)Math.floor(hue), 0.75F, 1.0F);
         return sink.accept(index, style.withInsertion(null).withColor(TextColor.fromRgb(rgb)), codePoint);
      });
   }
}
