package net.anubis.client.module.impl.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import imgui.ImDrawList;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.gui.HudPanel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.OpenUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class StaffLiveModule extends ClientModule implements HudOverlayProvider {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
   private static final Pattern CHANNEL = Pattern.compile("^(yt|tt):@?([A-Za-z0-9._-]+)$", 2);
   private static final String CANONICAL = "<link rel=\"canonical\" href=\"";
   private static final String LIVE_NOW = "\"isLiveNow\":true";
   private static final String META_TITLE = "<meta name=\"title\" content=\"";
   private static final String START = "\"startTimestamp\":\"";
   private static final int MAX_TITLE_CHARS = 48;
   private final StringListSetting channels = this.addSetting(
      "channels", new StringListSetting("Channels", "yt:wizoxlol", "yt:pastagamer08", "tt:pastagamer08", "tt:theauzzi", "tt:u_vv7_", "yt:DrDonutt")
   );
   private final NumberSetting interval = this.addSetting("check-every", new NumberSetting("Check Every (s)", 60.0F, 15.0F, 600.0F).step(5.0F));
   private final NumberSetting scale = this.addSetting("scale", new NumberSetting("Scale", 1.0F, 0.5F, 3.0F).step(0.05F));
   private final NumberSetting opacity = this.addSetting("opacity", HudPanel.opacitySetting());
   private final HudPanel panel = new HudPanel(
      this.addSetting("pos-x", HudPanel.coordinate("X")), this.addSetting("pos-y", HudPanel.coordinate("Y")), this.scale, this.opacity
   );
   private final HttpClient http = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10L)).build();
   private final Map<String, Boolean> live = new ConcurrentHashMap<>();
   private final Map<String, StaffLiveModule.Live> shown = new ConcurrentHashMap<>();
   private volatile Thread worker;

   public StaffLiveModule() {
      super(
         "staff-live",
         "Staff Live",
         "Tells you when DonutSMP staff go live on YouTube or TikTok: a chat line with a watch link, a chime, and a panel with the stream title while they are live. Channels are yt:handle or tt:handle.",
         ModuleCategory.DONUT
      );
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.live.clear();
      this.shown.clear();
      Thread thread = new Thread(this::run, "Anubis Staff Live");
      thread.setDaemon(true);
      this.worker = thread;
      thread.start();
   }

   @Override
   protected void onDisable() {
      Thread thread = this.worker;
      this.worker = null;
      if (thread != null) {
         thread.interrupt();
      }

      this.shown.clear();
   }

   private void run() {
      long next = 0L;

      while (this.worker == Thread.currentThread()) {
         if (System.currentTimeMillis() >= next) {
            Set<String> keys = new HashSet<>();

            for (StaffLiveModule.Channel channel : this.channels()) {
               if (this.worker != Thread.currentThread()) {
                  return;
               }

               keys.add(channel.key());
               this.check(channel);
            }

            this.live.keySet().retainAll(keys);
            this.shown.keySet().retainAll(keys);
            next = System.currentTimeMillis() + (long)(this.interval.get() * 1000.0F);
         }

         try {
            Thread.sleep(1000L);
         } catch (InterruptedException e) {
            return;
         }
      }
   }

   private List<StaffLiveModule.Channel> channels() {
      List<StaffLiveModule.Channel> out = new ArrayList<>();

      for (String entry : this.channels.get()) {
         Matcher m = CHANNEL.matcher(entry.trim());
         if (m.find()) {
            out.add(new StaffLiveModule.Channel(m.group(1).equalsIgnoreCase("yt"), m.group(2)));
         }
      }

      return out;
   }

   private void check(StaffLiveModule.Channel channel) {
      StaffLiveModule.Stream stream;
      try {
         stream = channel.youtube() ? this.youtube(channel.handle()) : this.tiktok(channel.handle());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return;
      } catch (Exception e) {
         LOGGER.debug("[Staff Live] check of {} failed: {}", channel.key(), e.toString());
         return;
      }

      if (stream != null) {
         Boolean was = this.live.put(channel.key(), stream.live());
         if (stream.live() && !Boolean.TRUE.equals(was)) {
            this.announce(channel, stream);
         }

         if (!stream.live()) {
            this.shown.remove(channel.key());
         }
      }
   }

   private StaffLiveModule.Stream youtube(String handle) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.youtube.com/@" + handle + "/live"))
         .timeout(Duration.ofSeconds(20L))
         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
         .header("Accept-Language", "en-US,en;q=0.9")
         .header("Accept-Encoding", "gzip")
         .header("Cookie", "SOCS=CAI; CONSENT=YES+")
         .build();
      HttpResponse<InputStream> response = this.http.send(request, BodyHandlers.ofInputStream());

      try (InputStream raw = response.body()) {
         if (response.statusCode() != 200) {
            return null;
         }

         boolean gzip = response.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
         Reader in = new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8);
         char[] buffer = new char[16384];
         StringBuilder window = new StringBuilder();
         String watch = null;
         String title = null;
         boolean liveNow = false;

         int n;
         while ((n = in.read(buffer)) > 0) {
            window.append(buffer, 0, n);
            if (watch == null) {
               String href = after(window, "<link rel=\"canonical\" href=\"");
               if (href != null) {
                  if (!href.contains("/watch?v=")) {
                     return StaffLiveModule.Stream.OFFLINE;
                  }

                  watch = href;
               }
            }

            if (watch != null && title == null) {
               title = after(window, "<meta name=\"title\" content=\"");
            }

            if (watch != null && !liveNow) {
               liveNow = window.indexOf("\"isLiveNow\":true") >= 0;
            }

            if (liveNow) {
               String start = after(window, "\"startTimestamp\":\"");
               if (start != null || window.length() > 4096) {
                  return new StaffLiveModule.Stream(true, clean(title), watch, null, parseStart(start));
               }
            } else if (window.length() > 4096) {
               window.delete(0, window.length() - 512);
            }
         }

         return liveNow ? new StaffLiveModule.Stream(true, clean(title), watch, null, 0L) : StaffLiveModule.Stream.OFFLINE;
      }
   }

   private StaffLiveModule.Stream tiktok(String user) throws Exception {
      String url = "https://www.tiktok.com/api-live/user/room/?aid=1988&app_language=en&app_name=tiktok_web&sourceType=54&uniqueId="
         + URLEncoder.encode(user, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(20L))
         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
         .header("Accept", "application/json")
         .build();
      HttpResponse<String> response = this.http.send(request, BodyHandlers.ofString());
      if (response.statusCode() != 200) {
         return null;
      } else {
         JsonElement body = JsonParser.parseString(response.body());
         if (!body.isJsonObject()) {
            return null;
         } else {
            JsonObject data = object(body.getAsJsonObject(), "data");
            JsonObject room = object(data, "liveRoom");
            if (room != null && room.has("status")) {
               boolean on = room.get("status").getAsInt() == 2;
               JsonObject owner = object(data, "user");
               String nick = owner != null && owner.has("nickname") ? owner.get("nickname").getAsString() : null;
               String title = room.has("title") ? room.get("title").getAsString() : null;
               long started = room.has("startTime") ? room.get("startTime").getAsLong() * 1000L : 0L;
               return new StaffLiveModule.Stream(on, clean(title), "https://www.tiktok.com/@" + user + "/live", nick, started);
            } else {
               return null;
            }
         }
      }
   }

   private void announce(StaffLiveModule.Channel channel, StaffLiveModule.Stream stream) {
      String url = stream.link() != null ? stream.link() : "https://www.youtube.com/@" + channel.handle() + "/live";
      String who = stream.nick() != null && !stream.nick().equalsIgnoreCase(channel.handle())
         ? channel.handle() + " (" + stream.nick() + ")"
         : channel.handle();
      String platform = channel.youtube() ? "YouTube" : "TikTok";
      long now = System.currentTimeMillis();
      long since = stream.started() > 0L && stream.started() <= now ? stream.started() : now;
      this.shown.putIfAbsent(channel.key(), new StaffLiveModule.Live(who, platform, stream.title(), since, now));
      LOGGER.info("[Staff Live] {} is live on {}", who, platform);
      MinecraftClient mc = MinecraftClient.getInstance();
      mc.execute(() -> {
         if (this.isEnabled() && mc.player != null) {
            ModuleChat.Body body = ModuleChat.body().name(who).text(" is ").danger("LIVE").text(" on " + platform);
            if (stream.title() != null && !stream.title().isBlank()) {
               body.text(": " + stream.title());
            }

            body.text(" ").append(Text.literal("[watch]").styled(style -> style.withClickEvent(new OpenUrl(URI.create(url))).withUnderline(true)));
            ModuleChat.send(this, body);
            if (this.notificationsOn()) {
               mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.25F));
            }
         }
      });
   }

   private List<StaffLiveModule.Live> rows() {
      return !this.isEnabled() ? List.of() : this.shown.values().stream().sorted(Comparator.comparingLong(StaffLiveModule.Live::shownAt)).toList();
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && MinecraftClient.getInstance().player != null && !this.shown.isEmpty();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      List<StaffLiveModule.Live> live = this.rows();
      if (!live.isEmpty()) {
         long now = System.currentTimeMillis();
         List<HudPanel.Line> lines = new ArrayList<>();

         for (StaffLiveModule.Live row : live) {
            lines.add(new HudPanel.Line(0, row.who(), -1, row.platform() + "  ·  " + ago(now - row.since()), -47814));
            if (row.title() != null && !row.title().isBlank()) {
               lines.add(new HudPanel.Line(shorten(row.title()), -6052949));
            }
         }

         this.panel.card(dl, "LIVE", String.valueOf(live.size()), -47814, lines, "staff_list", screenW, screenH, 0.5F, 10.0F);
      }
   }

   private static String shorten(String title) {
      return title.length() <= 48 ? title : title.substring(0, 47).stripTrailing() + "…";
   }

   private static String ago(long ms) {
      long m = ms / 60000L;
      return m < 1L ? "now" : (m < 60L ? m + "m" : m / 60L + "h " + m % 60L + "m");
   }

   private static String after(CharSequence s, String marker) {
      String text = s.toString();
      int at = text.indexOf(marker);
      int end = at < 0 ? -1 : text.indexOf(34, at + marker.length());
      return end < 0 ? null : text.substring(at + marker.length(), end);
   }

   private static String clean(String s) {
      if (s == null) {
         return null;
      }

      s = s.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
      StringBuilder out = new StringBuilder(s.length());
      s.codePoints().filter(cp -> cp >= 32 && cp < 8192 || cp >= 8208 && cp < 9728).forEach(out::appendCodePoint);
      return out.toString().replaceAll("\\s+", " ").trim();
   }

   private static long parseStart(String iso) {
      try {
         return iso == null ? 0L : OffsetDateTime.parse(iso).toInstant().toEpochMilli();
      } catch (RuntimeException e) {
         return 0L;
      }
   }

   private static JsonObject object(JsonObject parent, String key) {
      if (parent == null) {
         return null;
      }

      JsonElement element = parent.get(key);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }

   @Environment(EnvType.CLIENT)
   private record Channel(boolean youtube, String handle) {
      String key() {
         return (this.youtube ? "yt:" : "tt:") + this.handle.toLowerCase(Locale.ROOT);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Live(String who, String platform, String title, long since, long shownAt) {
   }

   @Environment(EnvType.CLIENT)
   private record Stream(boolean live, String title, String link, String nick, long started) {
      static final StaffLiveModule.Stream OFFLINE = new StaffLiveModule.Stream(false, null, null, null, 0L);
   }
}
