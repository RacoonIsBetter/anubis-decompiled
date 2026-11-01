package net.anubis.client.module.impl.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.anubis.client.module.impl.client.SpotifyHudModule;
import net.anubis.client.util.spotify.Lyrics;
import net.anubis.client.util.spotify.NowPlaying;
import net.anubis.client.util.spotify.SyncedLyrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SkyLyrics {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final SkyLyrics INSTANCE = new SkyLyrics();
   private static final String FONT = "/assets/anubis/fonts/manrope-bold.ttf";
   private static final long LEAD_MS = 200L;
   private static final float LINE_DEGREES = 7.0F;
   private static final float TITLE_DEGREES = 4.0F;
   private static final float TITLE_GAP_DEGREES = 0.6F;
   private static final float MAX_WIDTH_DEGREES = 140.0F;
   private static final float RISE_DEGREES = 1.6F;
   private static final float FADE_RATE = 2.6F;
   private static final float FOLLOW_RATE = 2.2F;
   private static final float LINE_PX = 96.0F;
   private static final float TITLE_PX = 64.0F;
   private static final float GLOW_ALPHA = 0.8F;
   private static final int PAD = 28;
   private static final int MAX_TEXTURE = 4096;
   private static final int BLUR = 10;
   private static final int SEGMENTS = 32;
   private static final int FALLBACK_GLOW = 9063935;
   private static final float EMOJI_SCALE = 1.0F;
   private static final float EMOJI_GAP_DEGREES = 0.8F;
   private static final long EMOJI_COOLDOWN_NS = 5000000000L;
   private static final float WORD_EMOJI_EM = 1.6F;
   private static final float BEHIND_SCALE = 2.8F;
   private static final float BEHIND_ALPHA = 0.32F;
   private static final float PEEP_IN_SECONDS = 5.0F;
   private static final float PEEP_OUT_SECONDS = 1.8F;
   private static final float PEEP_KEEP_SECONDS = 30.0F;
   private static final double PEEP_CLOCK_WRAP = 21600.0;
   private static final int PEEP_LINE = -24876;
   private static final int PEEP_TITLE = -34104;
   private static final int PEEP_GLOW = 16723349;
   private final ExecutorService raster = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Anubis Sky Lyrics");
      thread.setDaemon(true);
      return thread;
   });
   private final SkyLyrics.Slot line = new SkyLyrics.Slot(7.0F, 96.0F);
   private final SkyLyrics.Slot title = new SkyLyrics.Slot(4.0F, 64.0F);
   private SkyCanvas canvas;
   private Font font;
   private boolean fontMissing;
   private boolean failed;
   private final Map<SkyEmoji, SkyTexture> emojiTextures = new EnumMap<>(SkyEmoji.class);
   private final Set<SkyEmoji> uploadedEmojis = ConcurrentHashMap.newKeySet();
   private float viewU = Float.NaN;
   private long lastFrame;
   private long lastEmojiAt;
   private boolean emojiRight = true;
   private float emojiScale = 1.0F;
   private float glowAlpha = 0.8F;
   private int serial;
   private final PeepSky peep = new PeepSky();
   private float peepLevel;
   private float peepIdle;
   private double peepClock;
   private String peepTitle;
   private PeepSong peepSong;

   private SkyLyrics() {
   }

   public static void render() {
      INSTANCE.draw();
   }

   private void draw() {
      if (!this.failed) {
         long now = System.nanoTime();
         float dt = this.lastFrame == 0L ? 0.0F : Math.min((float)(now - this.lastFrame) / 1.0E9F, 0.1F);
         this.lastFrame = now;
         SpotifyHudModule module = SpotifyHudModule.active();
         boolean peepWanted = module != null && module.peepPlaying();
         this.peepLevel = peepWanted ? Math.min(1.0F, this.peepLevel + dt / 5.0F) : Math.max(0.0F, this.peepLevel - dt / 1.8F);
         boolean burning = peepWanted && module.showLyrics();
         boolean on = module != null && (module.skyLyrics() || burning);
         this.peepIdle = !peepWanted && !(this.peepLevel > 0.0F) ? this.peepIdle + dt : 0.0F;
         if (this.peepLevel <= 0.0F && !peepWanted) {
            this.peep.restart();
            this.peepClock = 0.0;
            if (this.peepIdle > 30.0F && this.peep.holding()) {
               this.peep.close();
            }
         }

         if (on || !this.line.idle() || !this.title.idle() || !(this.peepLevel <= 0.0F)) {
            try {
               if (this.canvas == null) {
                  this.canvas = new SkyCanvas();
               }

               Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
               float target = MathHelper.floorMod(camera.getYaw() / 360.0F + 0.25F, 1.0F);
               this.viewU = Float.isNaN(this.viewU) ? target : wrap(this.viewU + wrapDelta(target - this.viewU) * Math.min(1.0F, dt * 2.2F));
               if (this.peepLevel > 0.0F) {
                  NowPlaying song = module != null ? module.nowPlaying() : null;
                  if (peepWanted && !song.title().equals(this.peepTitle)) {
                     this.peepTitle = song.title();
                     this.peepSong = PeepSong.of(this.peepTitle);
                  }

                  this.peep.ready(this.canvas, this.raster);
                  this.peepLyrics(on ? module : null);
                  this.peep.update(dt, this.peepSong, this.peepLevel, this.viewU, target, -camera.getPitch(), burning ? linePulse(module, song) : 0.0F);
                  this.peepClock = (this.peepClock + dt) % 21600.0;
                  this.peep.draw(this.canvas, this.peepLevel, (float)this.peepClock);
                  this.canvas.flush(1.0F);
               }

               NowPlaying track = on ? module.nowPlaying() : null;
               boolean playing = track != null && track.playing();
               this.line
                  .want(on ? currentLine(module, track) : "", on && module.skyEmojis() ? module.skyEmojiStyle() : null, on ? module.skyEmojiSize() : 1.0F);
               this.title.want(on && playing && module.skyTitle() ? track.title().strip() : "", null, 1.0F);
               this.line.step(dt, this.viewU);
               this.title.step(dt, this.viewU);
               if (this.line.idle() && this.title.idle()) {
                  return;
               }

               boolean follow = !on || module.skyFollow();
               float size = on ? module.skySize() : 1.0F;
               float titleSize = on ? module.skyTitleSize() : 1.0F;
               this.emojiScale = on ? module.skyEmojiSize() : 1.0F;
               this.glowAlpha = on ? module.skyGlow() : 0.8F;
               float height = on ? module.skyHeight() : 28.0F;
               float opacity = on ? module.skyOpacity() : 0.9F;
               int lineColor = burning ? -24876 : (on ? module.skyTextColor() : -1);
               int titleColor = burning ? -34104 : (on ? module.skyTitleColor() : -1);
               AtmosphereModule atmosphere = on && !burning && module.skyGlowAtmosphere() ? AtmosphereModule.get() : null;
               int glow = burning ? 16723349 : (atmosphere != null ? atmosphere.lyricGlow() : (on ? module.skyGlowColor() : 9063935));
               if ((glow & 16777215) == 0 && atmosphere != null) {
                  glow = 9063935;
               }

               this.drawSlot(this.line, follow, height, size, lineColor, glow);
               this.drawSlot(this.title, follow, titleElevation(height, size, titleSize), titleSize, titleColor, glow);
               this.canvas.flush(opacity);
            } catch (RuntimeException e) {
               this.failed = true;
               if (this.canvas != null) {
                  this.canvas.clear();
               }

               this.line.close();
               this.title.close();
               this.peep.close();
               LOGGER.error("[Anubis] Sky lyrics stopped drawing", e);
            }
         }
      }
   }

   private static float titleElevation(float height, float size, float titleSize) {
      return height + (7.0F * size + 4.0F * titleSize) * 0.5F + 0.6F * Math.max(size, titleSize);
   }

   private void peepLyrics(SpotifyHudModule module) {
      if (module == null) {
         this.peep.lyrics(this.viewU, Float.NaN, 0.0F, 0.0F, this.viewU, Float.NaN, 0.0F, 0.0F);
      } else {
         float size = module.skySize();
         float titleSize = module.skyTitleSize();
         float height = module.skyHeight();
         SkyLyrics.Shown shownLine = this.line.current != null ? this.line.current : this.line.leaving;
         SkyLyrics.Shown shownTitle = this.title.current != null ? this.title.current : this.title.leaving;
         boolean follow = module.skyFollow();
         float u = !follow && shownLine != null ? shownLine.u : this.viewU;
         float titleU = !follow && shownTitle != null ? shownTitle.u : u;
         this.peep
            .lyrics(
               u,
               height,
               halfWidth(shownLine, 7.0F * size),
               7.0F * size * 0.5F,
               titleU,
               titleElevation(height, size, titleSize),
               halfWidth(shownTitle, 4.0F * titleSize),
               4.0F * titleSize * 0.5F
            );
      }
   }

   private static float halfWidth(SkyLyrics.Shown shown, float degrees) {
      return shown == null ? 0.0F : Math.min(degrees * shown.aspect, 140.0F) * 0.5F;
   }

   private void drawSlot(SkyLyrics.Slot slot, boolean follow, float elevation, float size, int color, int glow) {
      SkyLyrics.Shown leaving = slot.leaving;
      SkyLyrics.Shown current = slot.current;
      if (leaving != null) {
         this.drawShown(leaving, follow, elevation + (1.0F - leaving.alpha) * 1.6F * size, slot.degrees * size, color, glow);
      }

      if (current != null) {
         float settle = 1.0F - current.alpha;
         this.drawShown(current, follow, elevation - settle * settle * 1.6F * size, slot.degrees * size, color, glow);
      }
   }

   private void drawShown(SkyLyrics.Shown shown, boolean follow, float elevation, float degrees, int color, int glow) {
      float alpha = MathHelper.clamp(shown.alpha, 0.0F, 1.0F);
      if (!(alpha <= 0.003F)) {
         float tall = degrees;
         float wide = degrees * shown.aspect;
         if (wide > 140.0F) {
            tall *= 140.0F / wide;
            wide = 140.0F;
         }

         float e = elevation * (float) (Math.PI / 180.0);
         float centre = 0.72F - e / (float) (Math.PI / 3);
         float halfY = tall * (float) (Math.PI / 180.0) / (float) (Math.PI / 3) * 0.5F;
         float halfU = wide / (360.0F * Math.max(0.2F, MathHelper.cos(e))) * 0.5F;
         float u = follow ? this.viewU : shown.u;
         float top = centre - halfY;
         float bottom = centre + halfY;
         float pop = !(shown.alpha >= 1.0F) && shown == this.line.current ? backOut(alpha) : alpha;
         float time = (float)(System.nanoTime() % 3600000000000L) / 1.0E9F;
         if (shown.emoji != null && shown.style == SpotifyHudModule.SkyEmojiStyle.BEHIND) {
            float sizeH = tall * 2.8F * this.emojiScale * pop * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
            this.canvas
               .sprite(shown.emoji, u, centre, sizeH, sizeH, MathHelper.sin(time * 0.9F + shown.phase) * 0.05F, withAlpha(16777215, alpha * 0.32F), false);
         }

         if (this.glowAlpha > 0.0F) {
            this.canvas.band(shown.glow, u - halfU, u + halfU, top, bottom, withAlpha(glow, alpha * this.glowAlpha), true, 32);
         }

         this.canvas.band(shown.fill, u - halfU, u + halfU, top, bottom, withAlpha(color, alpha * (color >>> 24) / 255.0F), false, 32);
         if (shown.emoji != null && shown.style != SpotifyHudModule.SkyEmojiStyle.BEHIND) {
            if (shown.style == SpotifyHudModule.SkyEmojiStyle.REPLACE_WORD) {
               float bob = MathHelper.sin(time * 2.2F + shown.phase) * tall * 0.03F * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
               float sizeH = shown.emojiSize * (bottom - top) * pop;
               this.canvas
                  .sprite(
                     shown.emoji,
                     u - halfU + shown.emojiX * halfU * 2.0F,
                     top + shown.emojiY * (bottom - top) - bob,
                     sizeH,
                     sizeH,
                     MathHelper.sin(time * 1.7F + shown.phase) * 0.06F,
                     withAlpha(16777215, alpha),
                     false
                  );
            } else {
               float emojiTall = tall * 1.0F * this.emojiScale;
               float emojiDegrees = emojiTall * pop;
               float offsetU = (emojiTall * 0.5F + 0.8F) / (360.0F * Math.max(0.2F, MathHelper.cos(e)));
               float bob = MathHelper.sin(time * 2.2F + shown.phase) * tall * 0.06F;
               float y = 0.72F - (elevation + bob) * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
               float sizeH = emojiDegrees * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
               float spin = MathHelper.sin(time * 1.7F + shown.phase) * 0.12F;
               this.canvas.sprite(shown.emoji, u + (shown.right ? 1.0F : -1.0F) * (halfU + offsetU), y, sizeH, sizeH, spin, withAlpha(16777215, alpha), false);
            }
         }
      }
   }

   private SkyLyrics.Shown show(SkyLyrics.Raster made, float u) {
      int id = this.serial++;
      SkyTexture fill = SkyTexture.upload("sky_lyrics_" + id, made.width(), made.height(), made.fill());
      SkyTexture glow = SkyTexture.upload("sky_lyrics_glow_" + id, made.width(), made.height(), made.glow());
      SkyLyrics.Shown shown = new SkyLyrics.Shown(fill, glow, (float)made.width() / made.height(), u);
      SkyEmoji emoji = made.emoji();
      if (emoji == null) {
         return shown;
      }

      SkyTexture texture = this.emojiTextures.get(emoji);
      if (texture == null && made.emojiPixels() != null) {
         texture = SkyTexture.upload("sky_emoji_" + emoji.name().toLowerCase(Locale.ROOT), 256, 256, made.emojiPixels());
         this.emojiTextures.put(emoji, texture);
         this.uploadedEmojis.add(emoji);
      }

      if (texture == null) {
         return shown;
      }

      shown.emoji = texture;
      shown.style = made.style();
      shown.emojiX = made.emojiX();
      shown.emojiY = made.emojiY();
      shown.emojiSize = made.emojiSize();
      if (made.style() == SpotifyHudModule.SkyEmojiStyle.BESIDE) {
         shown.right = this.emojiRight;
         this.emojiRight = !this.emojiRight;
      }

      this.lastEmojiAt = System.nanoTime();
      return shown;
   }

   private static float backOut(float t) {
      float c = 1.70158F;
      float u = t - 1.0F;
      return 1.0F + (c + 1.0F) * u * u * u + c * u * u;
   }

   private static String currentLine(SpotifyHudModule module, NowPlaying track) {
      if (track != null && track.playing()) {
         Lyrics lyrics = module.lyricsFor(track);
         if (lyrics != null && lyrics.status() == Lyrics.Status.SYNCED) {
            SyncedLyrics synced = lyrics.synced();
            int index = synced.indexAt(track.positionAt(System.currentTimeMillis()) + 200L);
            if (index < 0) {
               return "";
            }

            StringBuilder text = new StringBuilder();

            for (int i = synced.firstWithTimeOf(index); i <= synced.lastWithTimeOf(index); i++) {
               String part = synced.line(i).text().strip();
               if (!part.isEmpty()) {
                  if (!text.isEmpty()) {
                     text.append("  ·  ");
                  }

                  text.append(part);
               }
            }

            return text.toString();
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   private static float linePulse(SpotifyHudModule module, NowPlaying track) {
      if (track != null && track.playing()) {
         Lyrics lyrics = module.lyricsFor(track);
         if (lyrics != null && lyrics.status() == Lyrics.Status.SYNCED) {
            SyncedLyrics synced = lyrics.synced();
            long at = track.positionAt(System.currentTimeMillis()) + 200L;
            int index = synced.indexAt(at);
            float beat = 0.0F;

            for (int back = 0; back < 2 && index >= 0; back++) {
               int first = synced.firstWithTimeOf(index);
               if (hasWords(synced, first, synced.lastWithTimeOf(index))) {
                  beat = Math.max(beat, PeepSky.heartbeat((float)(at - synced.line(first).timeMs()) / 1000.0F));
               }

               index = first - 1;
            }

            return beat;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   private static boolean hasWords(SyncedLyrics synced, int first, int last) {
      for (int i = first; i <= last; i++) {
         if (!synced.line(i).text().isBlank()) {
            return true;
         }
      }

      return false;
   }

   private SkyLyrics.Raster rasterize(int id, String text, float pixels, SpotifyHudModule.SkyEmojiStyle style, float emojiScale) {
      SkyEmoji.Match match = style != null ? SkyEmoji.match(text) : null;
      SkyEmoji emoji = match != null ? match.emoji() : null;
      int[] emojiPixels = emoji != null && !this.uploadedEmojis.contains(emoji) ? emoji.draw() : null;
      if (emojiPixels == null && emoji != null && !this.uploadedEmojis.contains(emoji)) {
         emoji = null;
      }

      boolean replace = emoji != null && style == SpotifyHudModule.SkyEmojiStyle.REPLACE_WORD;
      String before = replace ? text.substring(0, match.start()) : text;
      String after = replace ? text.substring(match.end()) : "";
      Font base = this.fontFor(text);
      BufferedImage probe = new BufferedImage(1, 1, 2);
      Graphics2D measure = probe.createGraphics();
      hints(measure);
      Font sized = base.deriveFont(pixels);
      FontMetrics metrics = measure.getFontMetrics(sized);
      float gap = replace ? pixels * 1.6F * emojiScale : 0.0F;
      int width = Math.max(1, metrics.stringWidth(before) + Math.round(gap) + metrics.stringWidth(after));
      if (width + 56 > 4096) {
         float shrink = 4040.0F / width;
         sized = base.deriveFont(pixels * shrink);
         metrics = measure.getFontMetrics(sized);
         gap *= shrink;
         width = Math.max(1, Math.min(4040, metrics.stringWidth(before) + Math.round(gap) + metrics.stringWidth(after)));
      }

      measure.dispose();
      int w = width + 56;
      int h = metrics.getAscent() + metrics.getDescent() + 56;
      int baseline = 28 + metrics.getAscent();
      int lead = metrics.stringWidth(before);
      BufferedImage image = new BufferedImage(w, h, 2);
      Graphics2D g = image.createGraphics();
      hints(g);
      g.setFont(sized);
      g.setColor(Color.WHITE);
      g.drawString(before, 28, baseline);
      if (!after.isEmpty()) {
         g.drawString(after, 28 + lead + Math.round(gap), baseline);
      }

      float capHeight = (float)sized.createGlyphVector(g.getFontRenderContext(), "H").getVisualBounds().getHeight();
      g.dispose();
      int[] fill = image.getRGB(0, 0, w, h, null, 0, w);
      int[] glow = glow(fill, w, h);
      float emojiX = replace ? (28 + lead + gap * 0.5F) / w : 0.0F;
      float emojiY = replace ? (baseline - capHeight * 0.5F) / h : 0.0F;
      return new SkyLyrics.Raster(id, w, h, fill, glow, emoji, emojiPixels, emoji != null ? style : null, emojiX, emojiY, replace ? gap / h : 0.0F);
   }

   private Font fontFor(String text) {
      if (this.font == null && !this.fontMissing) {
         try (InputStream in = SkyLyrics.class.getResourceAsStream("/assets/anubis/fonts/manrope-bold.ttf")) {
            if (in != null) {
               this.font = Font.createFont(0, in);
            } else {
               this.fontMissing = true;
            }
         } catch (Exception e) {
            this.fontMissing = true;
            LOGGER.warn("[Anubis] Sky lyrics font couldn't load; using the system font", e);
         }
      }

      return this.font != null && this.font.canDisplayUpTo(text) == -1 ? this.font : new Font("SansSerif", 1, 1);
   }

   private static void hints(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
   }

   private static int[] glow(int[] fill, int w, int h) {
      float[] alpha = new float[w * h];

      for (int i = 0; i < alpha.length; i++) {
         alpha[i] = (fill[i] >>> 24) / 255.0F;
      }

      float[] scratch = new float[alpha.length];

      for (int pass = 0; pass < 2; pass++) {
         blurRows(alpha, scratch, w, h);
         blurColumns(scratch, alpha, w, h);
      }

      int[] out = new int[alpha.length];

      for (int i = 0; i < out.length; i++) {
         out[i] = Math.min(255, Math.round(alpha[i] * 400.0F)) << 24 | 16777215;
      }

      return out;
   }

   private static void blurRows(float[] from, float[] to, int w, int h) {
      float scale = 0.04761905F;

      for (int y = 0; y < h; y++) {
         int row = y * w;
         float sum = 0.0F;

         for (int x = 0; x <= Math.min(10, w - 1); x++) {
            sum += from[row + x];
         }

         for (int x = 0; x < w; x++) {
            to[row + x] = sum * scale;
            int add = x + 10 + 1;
            int drop = x - 10;
            if (add < w) {
               sum += from[row + add];
            }

            if (drop >= 0) {
               sum -= from[row + drop];
            }
         }
      }
   }

   private static void blurColumns(float[] from, float[] to, int w, int h) {
      float scale = 0.04761905F;

      for (int x = 0; x < w; x++) {
         float sum = 0.0F;

         for (int y = 0; y <= Math.min(10, h - 1); y++) {
            sum += from[y * w + x];
         }

         for (int y = 0; y < h; y++) {
            to[y * w + x] = sum * scale;
            int add = y + 10 + 1;
            int drop = y - 10;
            if (add < h) {
               sum += from[add * w + x];
            }

            if (drop >= 0) {
               sum -= from[drop * w + x];
            }
         }
      }
   }

   private static int withAlpha(int rgb, float alpha) {
      return Math.round(MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 16777215;
   }

   private static float wrap(float u) {
      return u - (float)Math.floor(u);
   }

   private static float wrapDelta(float delta) {
      return delta - Math.round(delta);
   }

   @Environment(EnvType.CLIENT)
   private record Raster(
      int id,
      int width,
      int height,
      int[] fill,
      int[] glow,
      SkyEmoji emoji,
      int[] emojiPixels,
      SpotifyHudModule.SkyEmojiStyle style,
      float emojiX,
      float emojiY,
      float emojiSize
   ) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Shown {
      final SkyTexture fill;
      final SkyTexture glow;
      final float aspect;
      final float u;
      final float phase = (float)(Math.random() * Math.PI * 2.0);
      float alpha;
      SkyTexture emoji;
      SpotifyHudModule.SkyEmojiStyle style;
      boolean right;
      float emojiX;
      float emojiY;
      float emojiSize;

      Shown(SkyTexture fill, SkyTexture glow, float aspect, float u) {
         this.fill = fill;
         this.glow = glow;
         this.aspect = aspect;
         this.u = u;
      }

      void close() {
         this.fill.close();
         this.glow.close();
      }
   }

   @Environment(EnvType.CLIENT)
   private final class Slot {
      private final float degrees;
      private final float pixels;
      private String wanted = "";
      private SpotifyHudModule.SkyEmojiStyle wantedStyle;
      private volatile int request;
      private volatile SkyLyrics.Raster ready;
      private SkyLyrics.Shown current;
      private SkyLyrics.Shown leaving;

      Slot(float degrees, float pixels) {
         this.degrees = degrees;
         this.pixels = pixels;
      }

      void want(String text, SpotifyHudModule.SkyEmojiStyle style, float emojiScale) {
         if (!text.equals(this.wanted) || style != this.wantedStyle) {
            this.wanted = text;
            this.wantedStyle = style;
            int id = ++this.request;
            this.ready = null;
            if (!text.isEmpty()) {
               SpotifyHudModule.SkyEmojiStyle emojis = System.nanoTime() - SkyLyrics.this.lastEmojiAt >= 5000000000L ? style : null;
               SkyLyrics.this.raster.submit(() -> {
                  try {
                     SkyLyrics.Raster made = SkyLyrics.this.rasterize(id, text, this.pixels, emojis, emojiScale);
                     if (id == this.request) {
                        this.ready = made;
                     }
                  } catch (RuntimeException | Error e) {
                     SkyLyrics.LOGGER.warn("[Anubis] Sky lyrics couldn't draw a line", e);
                  }
               });
            }
         }
      }

      void step(float dt, float u) {
         SkyLyrics.Raster made = this.ready;
         if (made != null && made.id() == this.request) {
            this.ready = null;
            this.retire();
            this.current = SkyLyrics.this.show(made, u);
         } else if (this.wanted.isEmpty()) {
            this.retire();
         }

         if (this.current != null) {
            this.current.alpha = Math.min(1.0F, this.current.alpha + dt * 2.6F);
         }

         if (this.leaving != null) {
            this.leaving.alpha -= dt * 2.6F;
            if (this.leaving.alpha <= 0.0F) {
               this.leaving.close();
               this.leaving = null;
            }
         }
      }

      boolean idle() {
         return this.current == null && this.leaving == null && this.wanted.isEmpty();
      }

      private void retire() {
         if (this.current != null) {
            if (this.leaving != null) {
               this.leaving.close();
            }

            this.leaving = this.current;
            this.current = null;
         }
      }

      void close() {
         this.wanted = "";
         this.request++;
         this.ready = null;
         if (this.current != null) {
            this.current.close();
         }

         if (this.leaving != null) {
            this.leaving.close();
         }

         this.current = null;
         this.leaving = null;
      }
   }
}
