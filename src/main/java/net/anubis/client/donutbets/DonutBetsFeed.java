package net.anubis.client.donutbets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent.OpenUrl;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class DonutBetsFeed {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final URI FEED = URI.create("https://donutbets.net/api/client/feed");
   private static final String SITE = "donutbets.net";
   static final int MAX_BYTES = 65536;
   private static final int MAX_EVENTS = 20;
   private static final long POLL_MILLIS = 55000L;
   private static final long POLL_JITTER_MILLIS = 10000L;
   private static final long FIRST_POLL_MILLIS = 15000L;
   private static final long READ_DEADLINE_SECONDS = 12L;
   private static final int HURT_QUIET_TICKS = 200;
   private static final int ID_MAX = 80;
   private static final int TITLE_MAX = 100;
   private static final int TEXT_MAX = 300;
   private static final int SEEN_MAX = 300;
   private static final String TOP_CENTER = "Top Center";
   private static final String TOP_LEFT = "Top Left";
   private static final String TOP_RIGHT = "Top Right";
   private static final long SOUND_GAP_MILLIS = 10000L;
   private static final String FANFARE = "Fanfare";
   private static final String LEVEL_UP = "Level Up";
   private static final String BELL = "Bell";
   private static final String AMETHYST = "Amethyst";
   private static final String SILENT = "Off";
   private static final String CARD_AT = "Top Center";
   private static final String SOUND = "Level Up";
   private static final int PER_HOUR = 3;
   private static final String NAME = "DonutBets";
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NEVER).build();
   private final Gson gson = new Gson();
   private volatile Thread worker;
   private volatile long nextPoll;
   private String etag;
   private final Deque<DonutBetsFeed.Event> queue = new ArrayDeque<>();
   private final Set<String> seen = new LinkedHashSet<>();
   private final Deque<Long> shownAt = new ArrayDeque<>();
   private int ticksSinceHurt = 200;
   private long lastSoundAt;
   private static final DonutBetsFeed INSTANCE = new DonutBetsFeed();

   private DonutBetsFeed() {
   }

   public static void start() {
      INSTANCE.begin();
   }

   static DonutBetsFeed get() {
      return INSTANCE;
   }

   private synchronized void begin() {
      if (this.worker == null) {
         this.loadSeen();
         this.nextPoll = System.currentTimeMillis() + 15000L;
         LOGGER.info(
            "[DonutBets] Reading DonutBets event texts from {} about once a minute. Text only: nothing about the player is sent, nothing is downloaded or run. Part of the client, always on.",
            FEED
         );
         Thread thread = new Thread(this::run, "Anubis DonutBets event feed (text only)");
         thread.setDaemon(true);
         this.worker = thread;
         thread.start();
         TickEvent.TYPE.listen(this, Stage.POST, EventPriority.NORMAL, event -> this.tick());
      }
   }

   private void run() {
      while (this.worker == Thread.currentThread()) {
         if (System.currentTimeMillis() >= this.nextPoll) {
            this.poll();
            this.nextPoll = System.currentTimeMillis() + 55000L + ThreadLocalRandom.current().nextLong(10000L);
         }

         try {
            Thread.sleep(1000L);
         } catch (InterruptedException e) {
            return;
         }
      }
   }

   private void poll() {
      try {
         Builder request = HttpRequest.newBuilder(FEED)
            .timeout(Duration.ofSeconds(8L))
            .header("Accept", "application/json")
            .header("User-Agent", "Anubis-Client/" + version() + " (DonutBets event feed, text only)")
            .GET();
         if (this.etag != null) {
            request.header("If-None-Match", this.etag);
         }

         HttpResponse<InputStream> response = this.http.send(request.build(), BodyHandlers.ofInputStream());

         try (InputStream body = response.body()) {
            CompletableFuture.runAsync(() -> closeQuietly(body), CompletableFuture.delayedExecutor(12L, TimeUnit.SECONDS));
            if (response.statusCode() == 304) {
               return;
            }

            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (response.statusCode() == 200 && type.contains("json")) {
               byte[] bytes = body.readNBytes(65537);
               if (bytes.length > 65536) {
                  LOGGER.debug("[DonutBets] feed is bigger than {} bytes, not read", 65536);
                  return;
               }

               this.etag = response.headers().firstValue("ETag").filter(DonutBetsFeed::plainHeader).orElse(null);
               this.accept(new String(bytes, StandardCharsets.UTF_8));
               return;
            }

            LOGGER.debug("[DonutBets] feed answered {} {}", response.statusCode(), type);
         }

         return;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (Exception e) {
         LOGGER.debug("[DonutBets] feed did not answer: {}", e.toString());
      }
   }

   void accept(String body) {
      List<DonutBetsFeed.Event> fresh;
      try {
         fresh = parse(body, Instant.now());
      } catch (RuntimeException | StackOverflowError e) {
         LOGGER.debug("[DonutBets] feed is not valid JSON: {}", e.toString());
         return;
      }

      synchronized (this.queue) {
         for (DonutBetsFeed.Event event : fresh) {
            if (this.queue.size() >= 20) {
               break;
            }

            if (!this.seen.contains(event.id()) && !this.queue.stream().anyMatch(waiting -> waiting.id().equals(event.id()))) {
               this.queue.add(event);
            }
         }
      }
   }

   static List<DonutBetsFeed.Event> parse(String body, Instant now) {
      List<DonutBetsFeed.Event> out = new ArrayList<>();
      JsonElement root = JsonParser.parseString(body);
      if (root.isJsonObject() && root.getAsJsonObject().has("events") && root.getAsJsonObject().get("events").isJsonArray()) {
         for (JsonElement element : root.getAsJsonObject().getAsJsonArray("events")) {
            if (out.size() >= 20) {
               break;
            }

            if (element.isJsonObject()) {
               JsonObject event = element.getAsJsonObject();
               String id = clean(text(event, "id"), 80);
               String title = clean(text(event, "title"), 100);
               String text = clean(text(event, "text"), 300);
               if (!id.isEmpty() && (!title.isEmpty() || !text.isEmpty())) {
                  Instant starts = time(event, "startsAt");
                  Instant ends = time(event, "endsAt");
                  if ((starts == null || !now.isBefore(starts)) && (ends == null || now.isBefore(ends))) {
                     out.add(new DonutBetsFeed.Event(id, title, text, safeLink(text(event, "url"))));
                  }
               }
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         this.ticksSinceHurt = mc.player.hurtTime > 0 ? 0 : this.ticksSinceHurt + 1;
         if (mc.currentScreen == null && this.ticksSinceHurt >= 200) {
            long now = System.currentTimeMillis();

            while (!this.shownAt.isEmpty() && now - this.shownAt.peekFirst() > 3600000L) {
               this.shownAt.pollFirst();
            }

            if (this.shownAt.size() < 3) {
               DonutBetsFeed.Event event;
               synchronized (this.queue) {
                  event = this.queue.pollFirst();
               }

               if (event != null) {
                  this.shownAt.addLast(now);
                  this.seen.add(event.id());

                  while (this.seen.size() > 300) {
                     this.seen.remove(this.seen.iterator().next());
                  }

                  this.saveSeen();
                  this.show(mc, event, now);
               }
            }
         }
      }
   }

   private void show(MinecraftClient mc, DonutBetsFeed.Event event, long now) {
      ModuleChat.Body body = ModuleChat.body();
      if (!event.title().isEmpty()) {
         body.append(Text.empty().withColor(16777215).append(styled(event.title(), false)));
      }

      if (!event.text().isEmpty()) {
         if (!event.title().isEmpty()) {
            body.text(": ");
         }

         body.append(styled(event.text(), false));
      }

      String link = safeLink(event.url());
      if (link != null) {
         body.text(" ")
            .append(
               Text.literal("[open]")
                  .styled(
                     style -> style.withColor(Formatting.GOLD)
                        .withUnderline(true)
                        .withClickEvent(new OpenUrl(URI.create(link)))
                        .withHoverEvent(new ShowText(Text.literal(link)))
                  )
            );
      }

      ModuleChat.sendAs("DonutBets", body);
      Text title = event.title().isEmpty() ? Text.literal("DonutBets") : styled(event.title(), true);
      Text message = event.text().isEmpty() ? Text.literal("donutbets.net") : styled(event.text(), true);
      boolean quiet = now - this.lastSoundAt < 10000L;
      if (!quiet) {
         this.lastSoundAt = now;
      }

      mc.getToastManager().add(new DonutBetsFeed.BetsToast(title, message, quiet ? "Off" : "Level Up", "Top Center"));
      LOGGER.info("[DonutBets] showed event {}", event.id());
   }

   static String safeLink(String url) {
      if (url != null && !url.isBlank() && url.length() <= 300) {
         try {
            URI uri = new URI(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1) {
               return null;
            } else {
               return !host.equals("donutbets.net") && !host.endsWith(".donutbets.net") ? null : uri.toASCIIString();
            }
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   static MutableText styled(String text, boolean liveRainbow) {
      MutableText out = Text.empty();
      StringBuilder run = new StringBuilder();
      Style style = Style.EMPTY;
      boolean rainbow = false;
      int i = 0;

      while (i < text.length()) {
         char c = text.charAt(i);
         if ((c == '&' || c == 167) && i + 1 < text.length()) {
            char next = Character.toLowerCase(text.charAt(i + 1));
            if (next == '&') {
               run.append('&');
               i += 2;
               continue;
            }

            if (text.regionMatches(true, i + 1, "rainbow", 0, 7)) {
               flush(out, run, style, rainbow, liveRainbow);
               style = Style.EMPTY;
               rainbow = true;
               i += 8;
               continue;
            }

            if (next == '#' && i + 8 <= text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
               flush(out, run, style, rainbow, liveRainbow);
               style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
               rainbow = false;
               i += 8;
               continue;
            }

            Formatting format = Formatting.byCode(next);
            if (format != null) {
               flush(out, run, style, rainbow, liveRainbow);
               if (format == Formatting.RESET) {
                  style = Style.EMPTY;
                  rainbow = false;
               } else if (format.isColor()) {
                  style = Style.EMPTY.withColor(format);
                  rainbow = false;
               } else {
                  style = style.withFormatting(format);
               }

               i += 2;
               continue;
            }
         }

         run.append(c);
         i++;
      }

      flush(out, run, style, rainbow, liveRainbow);
      return out;
   }

   private static void flush(MutableText out, StringBuilder run, Style style, boolean rainbow, boolean liveRainbow) {
      if (!run.isEmpty()) {
         String text = run.toString();
         run.setLength(0);
         if (!rainbow) {
            out.append(Text.literal(text).setStyle(style));
         } else if (liveRainbow) {
            out.append(Text.literal(text).setStyle(style.withInsertion("anubis:rainbow")));
         } else {
            int count = text.codePointCount(0, text.length());
            int at = 0;
            int i = 0;

            while (i < text.length()) {
               int c = text.codePointAt(i);
               i += Character.charCount(c);
               float hue = (float)(at++) / Math.max(count, 7) * 0.85F;
               out.append(Text.literal(Character.toString(c)).setStyle(style.withColor(TextColor.fromRgb(MathHelper.hsvToRgb(hue, 0.75F, 1.0F)))));
            }
         }
      }
   }

   static String clean(String text, int max) {
      if (text == null) {
         return "";
      }

      StringBuilder out = new StringBuilder();
      int i = 0;

      while (i < text.length()) {
         int c = text.codePointAt(i);
         i += Character.charCount(c);
         if (c == 167) {
            c = 38;
         }

         int type = Character.getType(c);
         if (!Character.isISOControl(c) && type != 16 && type != 13 && type != 14 && type != 18 && type != 19 && type != 0) {
            if (out.length() + Character.charCount(c) > max) {
               break;
            }

            out.appendCodePoint(c);
         }
      }

      return out.toString().trim();
   }

   private static boolean plainHeader(String value) {
      if (!value.isEmpty() && value.length() <= 200) {
         for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < ' ' || c > '~') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static void closeQuietly(InputStream stream) {
      try {
         stream.close();
      } catch (IOException var2) {
      }
   }

   private static String text(JsonObject object, String key) {
      try {
         return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static Instant time(JsonObject object, String key) {
      String value = text(object, key);
      if (value != null && !value.isBlank()) {
         try {
            return Instant.parse(value.trim());
         } catch (DateTimeParseException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String version() {
      return FabricLoader.getInstance().getModContainer("anubisplus").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
   }

   private static Path seenFile() {
      return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("donutbets_seen.json");
   }

   private void loadSeen() {
      this.seen.clear();

      try {
         Path file = seenFile();
         if (!Files.exists(file) || Files.size(file) > 65536L) {
            return;
         }

         String[] ids = (String[])this.gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), String[].class);
         if (ids == null) {
            return;
         }

         for (String id : ids) {
            String clean = clean(id, 80);
            if (!clean.isEmpty() && this.seen.size() < 300) {
               this.seen.add(clean);
            }
         }
      } catch (IOException | RuntimeException e) {
         LOGGER.debug("[DonutBets] could not read the shown events: {}", e.toString());
      }
   }

   private void saveSeen() {
      try {
         Path file = seenFile();
         Files.createDirectories(file.getParent());
         Files.writeString(file, this.gson.toJson(this.seen.toArray(new String[0])), StandardCharsets.UTF_8);
      } catch (IOException e) {
         LOGGER.debug("[DonutBets] could not save the shown events: {}", e.toString());
      }
   }

   @Environment(EnvType.CLIENT)
   static final class BetsToast extends ChallengeToast {
      private static final long SHOWN_MILLIS = 30000L;
      private static final float RAINBOW_MILLIS = 3000.0F;
      private static final int BACK_SHIFT = 6;
      private static final float BACK_LEAN = (float)Math.toRadians(22.0);
      private static final float BACK_SCALE = 0.85F;
      private static final ItemStack BACK = new ItemStack(Items.DIAMOND);
      private final String position;
      private final String sound;

      BetsToast(Text title, Text message, String sound, String position) {
         super(glinting(new ItemStack(Items.DIAMOND)), title, message, !"Off".equals(sound));
         this.sound = sound;
         this.position = position;
      }

      @Override
      public SoundEvent getSoundEvent() {
         return switch (this.sound) {
            case "Fanfare" -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case "Bell" -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case "Amethyst" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case "Off" -> null;
            default -> SoundEvents.ENTITY_PLAYER_LEVELUP;
         };
      }

      private static ItemStack glinting(ItemStack stack) {
         stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
         return stack;
      }

      @Override
      public float getXPos(int scaledWindowWidth, float visibleWidthPortion) {
         if ("Top Left".equals(this.position)) {
            return -this.getWidth() * (1.0F - visibleWidthPortion);
         } else {
            return "Top Right".equals(this.position) ? super.getXPos(scaledWindowWidth, visibleWidthPortion) : (scaledWindowWidth - this.getWidth()) / 2.0F;
         }
      }

      @Override
      protected void renderIcon(DrawContext graphics, ItemStack icon) {
         leaning(graphics, 2, -BACK_LEAN);
         leaning(graphics, 14, BACK_LEAN);
         graphics.drawItemWithoutEntity(icon, 8, 8);
      }

      private static void leaning(DrawContext graphics, int x, float angle) {
         graphics.getMatrices().pushMatrix();
         graphics.getMatrices().translate(x + 8, 16.0F);
         graphics.getMatrices().rotate(angle);
         graphics.getMatrices().scale(0.85F, 0.85F);
         graphics.getMatrices().translate(-8.0F, -8.0F);
         graphics.drawItemWithoutEntity(BACK, 0, 0);
         graphics.getMatrices().popMatrix();
      }

      @Override
      protected long displayMillis() {
         return 30000L;
      }

      @Override
      public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
         super.draw(context, textRenderer, startTime);
         frame(context, this.cardRight(), this.cardBottom(), (float)(Util.getMeasuringTimeMs() % 3000L) / 3000.0F);
      }

      private static void frame(DrawContext graphics, int right, int bottom, float turn) {
         int top = 1;
         int across = right - 4;
         int down = bottom - top - 4;
         float perimeter = 2.0F * (across + down) + 8.0F;
         float at = 0.0F;

         for (int x = 2; x < right - 2; x++) {
            dot(graphics, x, top, at++ / perimeter + turn);
         }

         dot(graphics, right - 2, top + 1, at++ / perimeter + turn);

         for (int y = top + 2; y < bottom - 2; y++) {
            dot(graphics, right - 1, y, at++ / perimeter + turn);
         }

         dot(graphics, right - 2, bottom - 2, at++ / perimeter + turn);

         for (int x = right - 3; x >= 2; x--) {
            dot(graphics, x, bottom - 1, at++ / perimeter + turn);
         }

         dot(graphics, 1, bottom - 2, at++ / perimeter + turn);

         for (int y = bottom - 3; y >= top + 2; y--) {
            dot(graphics, 0, y, at++ / perimeter + turn);
         }

         dot(graphics, 1, top + 1, at++ / perimeter + turn);
      }

      private static void dot(DrawContext graphics, int x, int y, float hue) {
         graphics.fill(x, y, x + 1, y + 1, 0xFF000000 | MathHelper.hsvToRgb(hue - (float)Math.floor(hue), 0.8F, 1.0F));
      }
   }

   @Environment(EnvType.CLIENT)
   record Event(String id, String title, String text, String url) {
   }
}
