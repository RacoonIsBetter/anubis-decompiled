package net.anubis.client.module.impl.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class PeepSky {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   static final int PINK = 16732080;
   static final int HOT = 16766704;
   static final int DEEP = 12720235;
   private static final int BLUR = 9;
   private static final int SEGMENTS = 64;
   private static final int ROWS = 14;
   private static final int TATTOO_PAD = 26;
   private static final int TATTOO_MAX = 320;
   private static final float COVER_SHARE = 0.7F;
   private static final float TATTOO_ART = 0.8F;
   static final float SPIN_PER_SECOND = 0.02F;
   private static final String TATTOOS = "/assets/anubis/textures/peep/";
   private static final String[] KINDS = new String[]{"star", "anarchy", "cake", "heart", "wave", "rose", "mom", "horseshoe", "hellboy", "rip"};
   static final int STAR = 0;
   static final int RIP = 9;
   private static final float[] ELEVATION = new float[]{41.0F, 18.0F, 34.0F, 46.0F, 22.0F, 30.0F, 38.0F, 49.0F, 23.0F, 36.0F};
   private static final float[] SIZE = new float[]{6.0F, 10.0F, 8.0F, 6.0F, 7.0F, 9.0F, 5.0F, 6.0F, 6.0F, 12.0F};
   private static final int STARS = 140;
   private static final int BOKEH = 12;
   private static final float CROSSFADE_SECONDS = 2.0F;
   private static final float STAGE_FOLLOW = 0.5F;
   private static final float FALL_PER_SECOND = 0.014285714F;
   private static final float FALL_LOW = 12.0F;
   private static final float FALL_HIGH = 64.0F;
   private static final float STAR_LOW = -1.5F;
   private static final float STAR_HIGH = 86.0F;
   private static final float LYRIC_EASE = 3.0F;
   private static final float PARTICLE_KEEP_OUT = 1.0F;
   private static final float TEXT_GAP = 3.0F;
   private static final float TEXT_GAP_BELOW = 1.0F;
   private static final float TEXT_FADE = 5.0F;
   private static final float TEXT_FADE_BELOW = 1.5F;
   private static final float COLUMN_HALF = 14.0F;
   private static final float AIM_NEAR = 6.0F;
   private static final float AIM_FAR = 15.0F;
   private static final float HORIZON_HOLD = 0.04F;
   private static final float HORIZON_DEEP = 0.36F;
   private static final float HORIZON_FADE = 0.6F;
   private static final float HORIZON_DEEP_SHARE = 0.78F;
   private static final float BEAT_RISE = 0.14F;
   private static final int GOLD = 16769658;
   private static final int GHOST = 14208255;
   private static final float LIT_GLOW = 0.6F;
   private volatile PeepSky.Built built;
   private volatile int generation;
   private boolean building;
   private SkyImage[] tattoos;
   private SkyImage[] rims;
   private SkyImage[] golds;
   private SkyImage[] veils;
   private int[] kinds;
   SkyImage[] art;
   private final float[] weight = new float[PeepSong.ALL.length];
   private final float[] shown = new float[PeepSong.ALL.length];
   private final float[] songAge = new float[PeepSong.ALL.length];
   private PeepSong song;
   float time;
   float alpha;
   float viewU;
   float aimU;
   float viewUp;
   float stageU = Float.NaN;
   float pulse;
   int horizonRgb;
   float horizonShare;
   private float fall;
   private float fallFade;
   private boolean fresh = true;
   static final int ANCHORS = 512;
   private static final int GHOST_SLOTS = 512 - KINDS.length;
   private static final float HAUNT_SPEED = 0.42F;
   private static final float HAUNT_PHASE = 3.5F;
   private static final float[] HAUNT_AZIMUTH = new float[]{-40.0F, -40.0F, -56.0F, -56.0F, -33.0F, -33.0F, 33.0F, 33.0F, 40.0F, 40.0F, 56.0F};
   private static final boolean[] HAUNT_HIGH = new boolean[]{false, false, true, true, true, true, true, true, false, false, true};
   private final float[] anchorU = new float[512];
   private final int[] anchorRun = new int[512];
   private final byte[] pick = new byte[512];
   private final int[] pickRun = new int[512];
   private boolean lyricsShown;
   private float lyricU;
   private float titleU;
   private float lyricUp;
   private float lineHalfWide;
   private float lineHalfTall;
   private float titleUp;
   private float titleHalfWide;
   private float titleHalfTall;
   private float wantLineWide;
   private float wantTitleWide;
   private final PeepSky.Look look;
   private static final int MAX_SHOWN = KINDS.length * 2;
   final float[] tu;
   final float[] ty;
   final float[] tw;
   final float[] th;
   final float[] tGlow;
   final float[] tLit;
   private final float[] tSpin;
   private final float[] tAlpha;
   private final float[] tGhost;
   private final int[] tTex;
   int shownTattoos;
   private static final PeepSky.Palette BASE = new PeepSky.Palette(16732080, 12720235, 1836056, 525072, 3934768, 16766704, 16732080, 0.42F, 1.0F, 1.0F);
   private static final PeepSky.Palette[] PALETTES = new PeepSky.Palette[PeepSong.ALL.length];

   PeepSky() {
      Arrays.fill(this.anchorRun, Integer.MIN_VALUE);
      Arrays.fill(this.pickRun, Integer.MIN_VALUE);
      this.lyricUp = 28.0F;
      this.look = new PeepSky.Look();
      this.tu = new float[MAX_SHOWN];
      this.ty = new float[MAX_SHOWN];
      this.tw = new float[MAX_SHOWN];
      this.th = new float[MAX_SHOWN];
      this.tGlow = new float[MAX_SHOWN];
      this.tLit = new float[MAX_SHOWN];
      this.tSpin = new float[MAX_SHOWN];
      this.tAlpha = new float[MAX_SHOWN];
      this.tGhost = new float[MAX_SHOWN];
      this.tTex = new int[MAX_SHOWN];
   }

   private static PeepSky.Palette palette(PeepSong song) {
      return switch (song) {
         case STAR_SHOPPING -> new PeepSky.Palette(16732080, 12720235, 1574434, 394260, 3017786, 16766704, 16740296, 0.4F, 1.0F, 1.2F);
         case CRYBABY -> new PeepSky.Palette(15751854, 11016294, 1574426, 459791, 3412026, 16766704, 15755960, 0.38F, 0.92F, 0.9F);
         case CRY_ALONE -> new PeepSky.Palette(12073606, 7999568, 1114896, 262664, 2230304, 15760056, 12720235, 0.26F, 0.68F, 0.4F);
         case HELLBOY -> new PeepSky.Palette(16727406, 11539004, 1967116, 655878, 4720670, 16752312, 16724821, 0.52F, 1.0F, 0.85F);
         case BEAMER_BOY -> new PeepSky.Palette(14241744, 8002188, 1181220, 328468, 2363456, 15780095, 11554303, 0.34F, 0.9F, 0.9F);
         case SPOTLIGHT -> new PeepSky.Palette(12728968, 7999568, 1442831, 393739, 2492966, 15909598, 12720235, 0.32F, 0.85F, 0.8F);
         case THE_BRIGHTSIDE -> new PeepSky.Palette(16740277, 12728960, 4198974, 2886188, 15764620, 16769224, 16756848, 0.48F, 1.0F, 0.45F);
         case SUNLIGHT_ON_YOUR_SKIN -> new PeepSky.Palette(16751540, 13654656, 5247556, 3017264, 16746144, 16770760, 16760944, 0.4F, 1.0F, 0.6F);
         case LIFE_IS_BEAUTIFUL -> new PeepSky.Palette(16740250, 13117530, 2492440, 1049610, 5904440, 16765660, 16742550, 0.46F, 1.0F, 0.9F);
         case FALLING_DOWN -> new PeepSky.Palette(14711487, 9186424, 1509924, 525846, 2889272, 16046320, 15043792, 0.32F, 0.9F, 1.0F);
         case SAVE_THAT_SHIT -> new PeepSky.Palette(16732080, 13639802, 2229532, 656144, 4721718, 16766704, 16732080, 0.48F, 1.0F, 1.0F);
         case AWFUL_THINGS -> new PeepSky.Palette(9050714, 4852272, 786952, 196868, 2754592, 14701982, 12720235, 0.3F, 0.62F, 0.45F);
         case WITCHBLADES -> new PeepSky.Palette(13127167, 8002216, 1705520, 656408, 4593238, 15255807, 14835936, 0.44F, 0.95F, 0.85F);
         case FOUR_GOLD_CHAINS -> new PeepSky.Palette(16736170, 12720235, 2033430, 656392, 4854832, 16770736, 16732080, 0.5F, 1.0F, 1.0F);
         case GHOST_BOY -> new PeepSky.Palette(12352724, 7617154, 2362428, 1378338, 5121634, 14735600, 13671666, 0.42F, 0.85F, 0.7F);
         case GHOST_GIRL -> new PeepSky.Palette(15774948, 12093128, 4991604, 3415640, 14198482, 16773370, 16769268, 0.46F, 1.0F, 0.6F);
      };
   }

   void ready(SkyPaint paint, ExecutorService raster) {
      if (this.tattoos == null) {
         PeepSky.Built made = this.built;
         if (made != null && made.generation() != this.generation) {
            this.built = null;
            made = null;
         }

         if (made == null) {
            if (!this.building) {
               this.building = true;
               int ticket = this.generation;
               raster.submit(() -> {
                  if (ticket == this.generation) {
                     try {
                        PeepSky.Built done = build(ticket);
                        if (ticket == this.generation) {
                           this.built = done;
                        }
                     } catch (RuntimeException | Error e) {
                        LOGGER.warn("[Anubis] Sky easter egg textures couldn't be drawn", e);
                     }
                  }
               });
            }
         } else {
            SkyImage[] out = new SkyImage[made.tattoos().length];
            SkyImage[] edges = new SkyImage[out.length];
            SkyImage[] gilt = new SkyImage[out.length];
            SkyImage[] ghosts = new SkyImage[out.length];
            SkyImage[] fx = new SkyImage[made.art().length];

            try {
               for (int i = 0; i < out.length; i++) {
                  out[i] = paint.upload("peep_tattoo_" + i, made.widths()[i], made.heights()[i], made.tattoos()[i]);
                  edges[i] = paint.upload("peep_rim_" + i, made.widths()[i], made.heights()[i], made.rims()[i]);
                  gilt[i] = paint.upload("peep_gold_" + i, made.widths()[i], made.heights()[i], made.golds()[i]);
                  ghosts[i] = paint.upload("peep_veil_" + i, made.widths()[i], made.heights()[i], made.veils()[i]);
               }

               for (int i = 0; i < fx.length; i++) {
                  PeepArt.Art piece = made.art()[i];
                  fx[i] = paint.upload("peep_fx_" + i, piece.width(), piece.height(), made.artLevels()[i]);
               }
            } catch (RuntimeException | Error e) {
               closeAll(out);
               closeAll(edges);
               closeAll(gilt);
               closeAll(ghosts);
               closeAll(fx);
               throw e;
            }

            this.kinds = made.kinds();
            this.rims = edges;
            this.golds = gilt;
            this.veils = ghosts;
            this.art = fx;
            this.tattoos = out;
            this.built = null;
            this.building = false;
         }
      }
   }

   boolean holding() {
      return this.tattoos != null || this.building || this.built != null;
   }

   private static void closeAll(SkyImage[] textures) {
      if (textures != null) {
         for (SkyImage tex : textures) {
            if (tex != null) {
               tex.close();
            }
         }
      }
   }

   void close() {
      closeAll(this.tattoos);
      closeAll(this.rims);
      closeAll(this.golds);
      closeAll(this.veils);
      closeAll(this.art);
      this.tattoos = null;
      this.rims = null;
      this.golds = null;
      this.veils = null;
      this.art = null;
      this.generation++;
      this.built = null;
      this.building = false;
      this.restart();
   }

   void restart() {
      Arrays.fill(this.weight, 0.0F);
      Arrays.fill(this.shown, 0.0F);
      Arrays.fill(this.songAge, 0.0F);
      this.song = null;
      this.stageU = Float.NaN;
      this.fall = 0.0F;
      this.fallFade = 0.0F;
      this.fresh = true;
      Arrays.fill(this.anchorRun, Integer.MIN_VALUE);
      Arrays.fill(this.pickRun, Integer.MIN_VALUE);
   }

   float anchor(int slot, int run) {
      if (this.anchorRun[slot] != run) {
         this.anchorRun[slot] = run;
         this.anchorU[slot] = Float.isNaN(this.stageU) ? this.viewU : this.stageU;
      }

      return this.anchorU[slot];
   }

   float anchored(int slot, int run) {
      return this.anchorRun[slot] == run ? this.anchorU[slot] : Float.NaN;
   }

   int picked(int slot, int run) {
      return this.pickRun[slot] == run ? this.pick[slot] : -1;
   }

   void pick(int slot, int run, int choice) {
      this.pickRun[slot] = run;
      this.pick[slot] = (byte)choice;
   }

   void lyrics(float lineU, float lineUp, float lineHalfWide, float lineHalfTall, float titleU, float titleUp, float titleHalfWide, float titleHalfTall) {
      this.lyricsShown = !Float.isNaN(lineUp);
      this.lyricU = wrap(lineU);
      this.titleU = wrap(titleU);
      this.lyricUp = this.lyricsShown ? lineUp : 28.0F;
      this.lineHalfTall = lineHalfTall;
      this.titleUp = titleUp;
      this.titleHalfTall = titleHalfTall;
      this.wantLineWide = this.lyricsShown ? lineHalfWide : 0.0F;
      this.wantTitleWide = this.lyricsShown ? titleHalfWide : 0.0F;
   }

   void update(float dt, PeepSong next, float level, float viewU, float aimU, float viewUp, float pulse) {
      this.song = next;
      boolean hidden = level <= 0.0F || this.fresh;
      this.fresh = false;
      float step = dt / 2.0F;
      float sum = 0.0F;

      for (int i = 0; i < this.weight.length; i++) {
         boolean playing = this.song != null && this.song.ordinal() == i;
         float target = playing ? 1.0F : 0.0F;
         this.weight[i] = hidden ? target : (this.weight[i] < target ? Math.min(target, this.weight[i] + step) : Math.max(target, this.weight[i] - step));
         this.shown[i] = smootherstep(this.weight[i]);
         sum += this.shown[i];
         this.songAge[i] = playing ? Math.min(this.songAge[i] + dt, 100000.0F) : (this.weight[i] <= 0.0F ? 0.0F : this.songAge[i]);
      }

      if (sum > 1.0F) {
         for (int i = 0; i < this.shown.length; i++) {
            this.shown[i] = this.shown[i] / sum;
         }
      }

      this.viewU = wrap(viewU);
      this.aimU = wrap(aimU);
      this.viewUp = viewUp;
      if (!this.lyricsShown) {
         this.lyricU = this.viewU;
         this.titleU = this.viewU;
      }

      float ease = hidden ? 1.0F : Math.min(1.0F, dt * 3.0F);
      this.lineHalfWide = this.lineHalfWide + (this.wantLineWide - this.lineHalfWide) * ease;
      this.titleHalfWide = this.titleHalfWide + (this.wantTitleWide - this.titleHalfWide) * ease;
      this.pulse = pulse;
      this.stageU = !Float.isNaN(this.stageU) && !hidden ? wrap(this.stageU + wrapDelta(this.viewU - this.stageU) * Math.min(1.0F, dt * 0.5F)) : this.viewU;
      float falling = this.w(PeepSong.FALLING_DOWN);
      if (falling > 0.001F) {
         this.fall = MathHelper.fractionalPart(this.fall + dt * 0.014285714F * falling);
      }

      this.fallFade = this.song == PeepSong.FALLING_DOWN ? Math.min(1.0F, this.fallFade + step) : Math.max(0.0F, this.fallFade - step);
      if (this.fallFade <= 0.0F) {
         this.fall = 0.0F;
      }
   }

   float w(PeepSong song) {
      return this.shown[song.ordinal()];
   }

   float age(PeepSong song) {
      return this.songAge[song.ordinal()];
   }

   void draw(SkyPaint paint, float level, float time) {
      this.time = time;
      float cover = smootherstep(MathHelper.clamp(level / 0.7F, 0.0F, 1.0F));
      float marks = smootherstep(MathHelper.clamp((level - 0.7F) / 0.3F, 0.0F, 1.0F));
      if (cover <= 0.0F) {
         this.horizonShare = 0.0F;
         PeepWorld.horizon(this.horizonRgb, 0.0F);
      } else {
         this.alpha = marks;
         this.blendLook();
         float zenith = -0.78F;
         float ground = 1.27F;
         float edge = MathHelper.lerp(cover, ground, zenith - 0.05F);
         this.horizon(edge, cover, time);

         for (int row = 0; row < 14; row++) {
            float y0 = MathHelper.lerp(row / 14.0F, edge, ground);
            float y1 = MathHelper.lerp((row + 1) / 14.0F, edge, ground);
            int c0 = this.shade(y0 - edge, y0, 0.72F);
            int c1 = this.shade(y1 - edge, y1, 0.72F);

            for (int i = 0; i < 64; i++) {
               float u0 = i / 64.0F;
               float u1 = (i + 1) / 64.0F;
               paint.gradient(false, u0, u1, y0, y1, c0, c1);
            }
         }

         float rim = 0.09F;
         int lit = tint(0.85F, this.look.topColor);
         int clear = tint(0.0F, this.look.topColor);

         for (int i = 0; i < 64; i++) {
            float u0 = i / 64.0F;
            float u1 = (i + 1) / 64.0F;
            paint.gradient(true, u0, u1, edge - rim, edge, clear, lit);
            paint.gradient(true, u0, u1, edge, edge + rim * 2.2F, tint(0.55F, this.look.rimColor), clear);
         }

         boolean fx = this.art != null && marks > 0.0F;
         if (fx) {
            PeepEffects.skyLight(this, paint, cover, time);
         }

         this.horizonGlow(paint, edge, cover, time);
         if (this.pulse > 0.0F && this.lyricsShown) {
            paint.glow(this.lyricU, elevationToY(this.lyricUp + 1.0F), 0.4F, 16732080, 0.18F * this.pulse * marks);
         }

         float drift = this.fall;
         float driftFade = smootherstep(this.fallFade);
         float lonely = 1.0F - 0.5F * this.w(PeepSong.CRY_ALONE);

         for (int k = 0; k < 12; k++) {
            float u = MathHelper.fractionalPart(k * 0.2718F + time * (0.004F + 0.002F * (k % 3)) * (k % 2 == 0 ? 1.0F : -1.0F));
            float y = MathHelper.lerp(MathHelper.fractionalPart(k * 0.5713F + 0.21F), zenith + 0.45F, 0.67F);
            float fade = 1.0F;
            if (driftFade > 0.0F) {
               float home = (0.72F - y) * 60.0F;
               float at = MathHelper.fractionalPart((home - -1.5F) / 87.5F - drift);
               y = elevationToY(MathHelper.lerp(driftFade, home, -1.5F + at * 87.5F));
               fade = MathHelper.lerp(driftFade, 1.0F, smooth(0.0F, 0.08F, at) * (1.0F - smooth(0.7F, 0.85F, at)));
            }

            if (!(y < edge + 0.05F)) {
               float breathe = 0.6F + 0.4F * MathHelper.sin(time * (0.35F + 0.07F * k) + k * 1.3F);
               float radius = 0.07F + 0.05F * (k % 4);
               fade *= smooth(0.02F, 0.02F + radius, 0.72F - y);
               paint.glow(
                  u,
                  y,
                  radius,
                  k % 3 == 0 ? this.look.deepColor : this.look.topColor,
                  0.16F * breathe * cover * this.look.light * fade * lonely * (1.0F + 0.8F * this.pulse)
               );
            }
         }

         for (int k = 0; k < 140; k++) {
            float u = MathHelper.fractionalPart(k * 0.618034F);
            float y = MathHelper.lerp(MathHelper.fractionalPart(k * 0.41421F + 0.13F), zenith + 0.08F, 0.74F);
            float fade = 1.0F;
            if (driftFade > 0.0F) {
               float home = (0.72F - y) * 60.0F;
               float at = MathHelper.fractionalPart((home - -1.5F) / 87.5F - drift);
               y = elevationToY(MathHelper.lerp(driftFade, home, -1.5F + at * 87.5F));
               fade = MathHelper.lerp(driftFade, 1.0F, smooth(0.0F, 0.03F, at) * (1.0F - smooth(0.9F, 0.99F, at)));
            }

            if (!(y < edge + 0.01F)) {
               float twinkle = 0.35F + 0.65F * Math.max(0.0F, MathHelper.sin(time * (0.9F + k % 7 * 0.31F) + k * 2.17F));
               paint.glow(
                  u, y, 0.004F + 0.0035F * (k % 4), k % 5 == 0 ? 16766704 : 16773882, Math.min(1.0F, 0.8F * twinkle * this.look.starLight) * cover * fade
               );
            }
         }

         boolean inked = marks > 0.0F && this.tattoos != null;
         this.shownTattoos = 0;
         if (inked) {
            this.layoutTattoos(smooth(0.35F, 1.0F, marks) * marks, time);
         }

         if (fx) {
            PeepEffects.behind(this, paint, time);
         }

         if (inked) {
            this.drawTattoos(paint, time);
         }

         if (fx) {
            PeepEffects.front(this, paint, time);
         }
      }
   }

   private void horizon(float edge, float cover, float time) {
      float horizon = 0.72F;
      int sky = this.shade(horizon - edge, horizon, horizon);
      float glow = 0.0F;
      if (Math.max(edge, horizon - 0.42F) < horizon) {
         float breathe = 0.85F + 0.15F * MathHelper.sin(time * 0.6F);
         glow = MathHelper.clamp(this.look.glowStrength * cover * breathe * (1.0F + 0.45F * this.pulse), 0.0F, 1.0F);
      }

      int add = this.look.glowColor;
      int r = Math.min(255, Math.round((sky >> 16 & 0xFF) + (add >> 16 & 0xFF) * glow));
      int g = Math.min(255, Math.round((sky >> 8 & 0xFF) + (add >> 8 & 0xFF) * glow));
      int b = Math.min(255, Math.round((sky & 0xFF) + (add & 0xFF) * glow));
      this.horizonRgb = r << 16 | g << 8 | b;
      this.horizonShare = 0.97F * MathHelper.clamp((horizon - edge) / 0.1F, 0.0F, 1.0F);
      PeepWorld.horizon(this.horizonRgb, this.horizonShare);
   }

   private void horizonGlow(SkyPaint paint, float edge, float cover, float time) {
      float top = Math.max(edge, 0.30000004F);
      float horizon = 0.72F;
      float hold = 0.76000005F;
      float deep = 1.08F;
      float below = 1.32F;
      if (!(top >= horizon)) {
         float breathe = 0.85F + 0.15F * MathHelper.sin(time * 0.6F);
         float strength = this.look.glowStrength * cover * breathe * (1.0F + 0.45F * this.pulse);
         int glow = tint(strength, this.look.glowColor);
         int dim = tint(strength * 0.78F, this.look.glowColor);
         int none = tint(0.0F, this.look.glowColor);

         for (int i = 0; i < 64; i++) {
            float u0 = i / 64.0F;
            float u1 = (i + 1) / 64.0F;
            paint.gradient(true, u0, u1, top, horizon, none, glow);
            paint.gradient(true, u0, u1, horizon, hold, glow, glow);
            paint.gradient(true, u0, u1, hold, deep, glow, dim);
            paint.gradient(true, u0, u1, deep, below, dim, none);
         }
      }
   }

   float lyricBox(float u, float up, float halfWide, float halfTall) {
      if (!this.lyricsShown) {
         return 0.0F;
      }

      float squeeze = 360.0F * MathHelper.cos(up * (float) (Math.PI / 180.0));
      float line = this.lineHalfWide <= 0.05F
         ? 0.0F
         : smooth(0.0F, 3.0F, this.lineHalfWide)
            * box(Math.abs(wrapDelta(u - this.lyricU)) * squeeze, up - this.lyricUp, this.lineHalfWide + halfWide + 2.5F, this.lineHalfTall + halfTall + 2.0F);
      float title = this.titleHalfWide <= 0.05F
         ? 0.0F
         : smooth(0.0F, 3.0F, this.titleHalfWide)
            * box(Math.abs(wrapDelta(u - this.titleU)) * squeeze, up - this.titleUp, this.titleHalfWide + halfWide + 2.5F, this.titleHalfTall + halfTall + 2.0F);
      return Math.max(line, title);
   }

   float clear(float u, float up) {
      float keep = 1.0F - 1.0F * this.lyricBox(u, up, 0.8F, 0.8F);
      float e1 = up * (float) (Math.PI / 180.0);
      float e2 = this.viewUp * (float) (Math.PI / 180.0);
      float cos = MathHelper.sin(e1) * MathHelper.sin(e2) + MathHelper.cos(e1) * MathHelper.cos(e2) * MathHelper.cos((u - this.aimU) * (float) (Math.PI * 2));
      if (cos < 0.93F) {
         return keep;
      }

      float degrees = (float)Math.acos(Math.min(1.0F, cos)) * (180.0F / (float)Math.PI);
      return keep * smooth(9.0F, 20.0F, degrees);
   }

   private float aimClear(float u, float up, float halfWide, float halfTall) {
      float across = Math.abs(wrapDelta(u - this.aimU)) * 360.0F * MathHelper.cos((up + this.viewUp) * 0.5F * (float) (Math.PI / 180.0));
      float gapX = Math.max(0.0F, across - halfWide);
      float gapY = Math.max(0.0F, Math.abs(up - this.viewUp) - halfTall);
      return smooth(6.0F, 15.0F, (float)Math.sqrt(gapX * gapX + gapY * gapY));
   }

   private float keepOut(float u, float up, float halfTall, float halfWide) {
      return (1.0F - this.textRoom(u, up, halfWide, halfTall)) * this.aimClear(u, up, halfWide, halfTall);
   }

   private float textRoom(float u, float up, float halfWide, float halfTall) {
      if (!this.lyricsShown) {
         return 0.0F;
      }

      float squeeze = 360.0F * MathHelper.cos(up * (float) (Math.PI / 180.0));
      float room = 0.0F;
      if (this.lineHalfWide > 0.05F) {
         float across = Math.abs(wrapDelta(u - this.lyricU)) * squeeze;
         float there = smooth(0.0F, 3.0F, this.lineHalfWide);
         room = there * near(across, this.lineHalfWide + halfWide) * nearUp(up - this.lyricUp, this.lineHalfTall + halfTall);
         if (up > this.lyricUp) {
            room = Math.max(room, there * near(across, 14.0F + halfWide));
         }
      }

      if (this.titleHalfWide > 0.05F) {
         float across = Math.abs(wrapDelta(u - this.titleU)) * squeeze;
         float there = smooth(0.0F, 3.0F, this.titleHalfWide);
         float above = up > this.titleUp ? 1.0F : nearUp(up - this.titleUp, this.titleHalfTall + halfTall);
         room = Math.max(room, there * near(across, this.titleHalfWide + halfWide) * above);
      }

      return room;
   }

   private static float near(float d, float reach) {
      return 1.0F - smooth(reach + 3.0F, reach + 3.0F + 5.0F, Math.abs(d));
   }

   private static float nearUp(float dy, float reach) {
      return dy >= 0.0F ? near(dy, reach) : 1.0F - smooth(reach + 1.0F, reach + 1.0F + 1.5F, -dy);
   }

   private static float box(float x, float y, float halfX, float halfY) {
      return (1.0F - smooth(halfX * 0.85F, halfX, Math.abs(x))) * (1.0F - smooth(halfY * 0.8F, halfY, Math.abs(y)));
   }

   private static float artShare(int pixels) {
      return 1.0F - 52.0F / pixels;
   }

   int kindAt(int k) {
      return this.kinds[this.tTex[k]];
   }

   private void layoutTattoos(float alpha, float time) {
      int n = this.tattoos.length;
      float alone = this.w(PeepSong.CRY_ALONE);
      float ghost = this.w(PeepSong.GHOST_BOY);
      float shimmer = this.w(PeepSong.GHOST_GIRL);
      float spot = this.w(PeepSong.SPOTLIGHT);
      float light = MathHelper.lerp(0.5F, 1.0F, this.look.light);
      float beat = 1.0F + 0.1F * this.pulse;
      float drift = smootherstep(this.fallFade);
      int count = 0;

      for (int i = 0; i < n; i++) {
         SkyImage tex = this.tattoos[i];
         int slot = this.kinds[i] % ELEVATION.length;
         float ring = alpha * (1.0F - alone) * light;
         if (!(ring <= 0.003F)) {
            float breath = 0.85F + 0.15F * MathHelper.sin(time * 2.8F + i);
            float aspect = (float)tex.width() / tex.height();
            float u = MathHelper.fractionalPart((float)i / n + time * 0.02F);
            float up = ELEVATION[slot] + 2.2F * MathHelper.sin(time * 0.8F + i * 1.7F);
            float fade = 1.0F;
            if (drift > 0.0F) {
               float at = MathHelper.fractionalPart((up - 12.0F) / 52.0F - this.fall);
               up = MathHelper.lerp(drift, up, 12.0F + at * 52.0F);
               fade = MathHelper.lerp(drift, 1.0F, smooth(0.0F, 0.12F, at) * (1.0F - smooth(0.88F, 0.98F, at)));
            }

            if (ghost > 0.0F) {
               float turn = (time * 0.42F + i * 3.5F) / (float) (Math.PI * 2) + 0.25F;
               int run = MathHelper.floor(turn);
               int place = i % HAUNT_AZIMUTH.length;
               float hauntUp = (HAUNT_HIGH[place] ? 41.0F : 12.5F) + 6.0F * hash01(i, run, 1);
               float hauntU = this.anchor(GHOST_SLOTS + i, run)
                  + (HAUNT_AZIMUTH[place] + 8.0F * (hash01(i, run, 2) - 0.5F)) / (360.0F * MathHelper.cos(hauntUp * (float) (Math.PI / 180.0)));
               u = wrap(u + wrapDelta(hauntU - u) * ghost);
               up = MathHelper.lerp(ghost, up, hauntUp);
            }

            float tall = SIZE[slot] * (0.94F + 0.06F * MathHelper.sin(time * 2.0F + i)) * (1.0F + 0.15F * ghost);
            float keep = this.keepOut(u, up, tall * 0.5F * artShare(tex.height()), tall * aspect * 0.5F * artShare(tex.width()));
            fade *= keep * smooth(0.15F, 0.45F, keep);
            count = this.place(
               count, i, u, up, tall / 60.0F, aspect, 0.1F * MathHelper.sin(time * 0.7F + i * 2.1F), ring * fade * breath, time, shimmer, ghost, spot, beat
            );
         }
      }

      this.shownTattoos = count;
   }

   float clearOfTattoos(float u, float up, float half) {
      float keep = 1.0F;
      float squeeze = 360.0F * MathHelper.cos(up * (float) (Math.PI / 180.0));

      for (int k = 0; k < this.shownTattoos && keep > 0.0F; k++) {
         if (!(this.tGlow[k] <= 0.05F)) {
            float tUp = (0.72F - this.ty[k]) * (float) (Math.PI / 3) * (180.0F / (float)Math.PI);
            float across = Math.abs(wrapDelta(u - this.tu[k])) * squeeze;
            float halfW = this.tw[k] * 30.0F * 0.8F;
            float halfT = this.th[k] * 30.0F * 0.8F;
            float gapX = Math.max(0.0F, across - halfW - half);
            float gapY = Math.max(0.0F, Math.abs(up - tUp) - halfT - half);
            if (!(gapX > 1.5F) && !(gapY > 1.5F)) {
               keep *= 1.0F - Math.min(1.0F, this.tGlow[k]) * (1.0F - smooth(0.0F, 1.5F, (float)Math.sqrt(gapX * gapX + gapY * gapY)));
            }
         }
      }

      return keep;
   }

   private void drawTattoos(SkyPaint paint, float time) {
      int count = this.shownTattoos;
      float ghost = this.w(PeepSong.GHOST_BOY);
      float shimmer = this.w(PeepSong.GHOST_GIRL);
      float spot = this.w(PeepSong.SPOTLIGHT);
      float gold = this.w(PeepSong.FOUR_GOLD_CHAINS);
      float witch = this.w(PeepSong.WITCHBLADES);
      float beatGlow = 1.0F + 1.3F * this.pulse;
      int glowColor = mix(mix(mix(16732080, 16757575, gold * 0.4F), 14208255, ghost * 0.7F), 16763118, shimmer * 0.3F);

      for (int k = 0; k < count; k++) {
         paint.glow(
            this.tu[k],
            this.ty[k],
            Math.max(this.th[k], this.tw[k]) * 0.6F,
            glowColor,
            0.22F * this.tGlow[k] * beatGlow * (1.0F + 0.6F * this.tLit[k]) * (1.0F + 0.5F * ghost * this.tGhost[k])
         );
      }

      int faint = mix(mix(16732080, 16740552, shimmer), 13150463, ghost);
      float brief = Math.min(1.0F, shimmer + ghost);
      float neon = 1.0F - 0.7F * ghost;
      float pale = smooth(0.7F, 1.0F, this.look.light);

      for (int k = 0; k < count; k++) {
         int kind = this.kindAt(k);
         boolean star = kind == 0;
         float filled = this.filled(k, gold, ghost);
         float a = this.tAlpha[k] * (star ? 0.3F : neon * (1.0F - bodyBack(kind) * filled)) * (1.0F - brief * (1.0F - smooth(0.18F, 0.45F, this.tAlpha[k])));
         float white = smooth(0.2F, 0.8F, this.tAlpha[k])
            * pale
            * 0.7F
            * (1.0F - 0.5F * gold)
            * (1.0F - filled)
            * (1.0F - 0.7F * this.tLit[k])
            * (1.0F - 0.5F * shimmer);
         int color = star ? mix(16732080, 16740296, white) : mix(mix(faint, 16732080, filled), 16777215, white);
         if (ghost > 0.0F) {
            color = mix(color, mix(10121471, 14203135, this.tGhost[k]), ghost);
            a *= 1.0F - ghost * (1.0F - this.haunt(k));
         }

         if (gold > 0.0F) {
            a *= 1.0F - 0.35F * gold;
            color = mix(color, 12720235, 0.4F * gold);
         }

         paint.sprite(this.tattoos[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(a, color), true);
      }

      if (ghost > 0.003F) {
         for (int k = 0; k < count; k++) {
            float a = 0.72F
               * this.tAlpha[k]
               * ghost
               * smooth(0.1F, 0.35F, this.tAlpha[k])
               * (1.0F - 0.08F * smooth(0.8F, 1.0F, this.tGhost[k]))
               * this.haunt(k);
            if (!(a <= 0.003F)) {
               int color = mix(11697407, 13145855, this.tGhost[k]);
               paint.sprite(this.veils[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(a, color), true);
            }
         }
      }

      if (gold > 0.003F) {
         for (int k = 0; k < count; k++) {
            float g = gold * smooth(0.12F, 0.6F, this.tGlow[k]);
            if (!(g <= 0.003F)) {
               paint.sprite(this.golds[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(0.95F * g, 4859392), false);
               paint.sprite(this.golds[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(0.9F * g, 16769658), true);
            }
         }
      }

      for (int k = 0; k < count; k++) {
         float filled = this.filled(k, gold, ghost);
         if (!(filled <= 0.003F)) {
            float a = (this.kindAt(k) == 9 ? 0.92F : 0.8F) * Math.min(1.0F, filled * 1.35F) * this.tGlow[k];
            paint.sprite(this.golds[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(a, 16749268), true);
         }
      }

      int rimFaint = mix(16732080, 12099839, ghost);

      for (int k = 0; k < count; k++) {
         float s = spot * this.tLit[k] * 0.35F;
         float h = ghost * this.tGhost[k] * 0.26F;
         float o = this.kindAt(k) == 0 ? 0.6F * (1.0F - gold) * (1.0F - ghost) : 0.0F;
         float sum = s + h + o;
         if (!(sum <= 0.003F)) {
            int color = mix(rimFaint, average(16743115, s, 15916287, h, 16732080, o), smooth(0.2F, 0.8F, this.tGlow[k]));
            paint.sprite(
               this.rims[this.tTex[k]], this.tu[k], this.ty[k], this.tw[k], this.th[k], this.tSpin[k], tint(Math.min(1.0F, sum) * this.tGlow[k], color), true
            );
         }
      }

      if (witch > 0.0F) {
         PeepEffects.orbits(this, paint, time, witch);
      }

      if (gold > 0.0F) {
         PeepEffects.goldSparkles(this, paint, time, gold);
      }
   }

   private static float bodyBack(int kind) {
      return kind == 9 ? 0.45F : 0.25F;
   }

   private float haunt(int k) {
      boolean high = HAUNT_HIGH[this.tTex[k] % HAUNT_HIGH.length];
      return (high ? 1.0F : 0.8F) * (1.0F - 0.4F * this.hazeAt(this.ty[k]));
   }

   private float filled(int k, float gold, float ghost) {
      float sign = this.kindAt(k) == 9 ? 0.6F : 0.0F;
      return sign * (1.0F - gold) * (1.0F - ghost);
   }

   private int place(
      int k, int tex, float u, float up, float tall, float aspect, float spin, float alpha, float time, float shimmer, float ghost, float spot, float beat
   ) {
      if (k >= MAX_SHOWN) {
         return k;
      }

      if (shimmer > 0.0F) {
         u += shimmer
            * 0.35F
            * (MathHelper.sin(time * 2.3F + tex * 1.1F) + 0.5F * MathHelper.sin(time * 3.7F + tex * 2.9F))
            / (360.0F * Math.max(0.2F, MathHelper.cos(up * (float) (Math.PI / 180.0))));
         up += shimmer * 0.3F * (MathHelper.sin(time * 2.9F + tex * 1.7F) + 0.5F * MathHelper.sin(time * 4.3F + tex));
         alpha *= 1.0F - shimmer * 0.15F * (0.5F + 0.5F * MathHelper.sin(time * 3.1F + tex));
      }

      float haunt = 1.0F;
      if (ghost > 0.0F) {
         float wave = 0.5F + 0.5F * MathHelper.sin(time * 0.42F + tex * 3.5F);
         haunt = smootherstep(smooth(0.38F, 0.68F, wave));
         up += ghost * 1.5F * wave;
      }

      float lit = spot > 0.0F ? spot * PeepEffects.spotlightOn(this, u, up) : 0.0F;
      float grow = beat * (1.0F + 0.06F * lit);
      this.tTex[k] = tex;
      this.tu[k] = wrap(u);
      this.ty[k] = elevationToY(up);
      this.th[k] = tall * grow;
      this.tw[k] = tall * aspect * grow;
      this.tSpin[k] = spin;
      this.tGhost[k] = haunt;
      this.tLit[k] = lit;
      this.tAlpha[k] = Math.min(alpha * MathHelper.lerp(ghost, 1.0F, haunt * 0.85F), 1.0F - 0.3F * ghost);
      this.tGlow[k] = alpha * MathHelper.lerp(ghost, 1.0F, haunt);
      return k + 1;
   }

   private void blendLook() {
      float[] rgb = this.look.rgb;
      Arrays.fill(rgb, 0.0F);
      float rest = 1.0F;
      float strength = 0.0F;
      float light = 0.0F;
      float stars = 0.0F;

      for (PeepSong song : PeepSong.ALL) {
         float w = this.shown[song.ordinal()];
         if (!(w <= 0.0F)) {
            rest -= w;
            PeepSky.Palette p = PALETTES[song.ordinal()];
            add(rgb, p, w);
            strength += w * p.glowStrength();
            light += w * p.light();
            stars += w * p.starLight();
         }
      }

      rest = Math.max(0.0F, rest);
      add(rgb, BASE, rest);
      this.look.glowStrength = strength + rest * BASE.glowStrength();
      this.look.light = light + rest * BASE.light();
      this.look.starLight = stars + rest * BASE.starLight();
      this.look.topColor = packed(rgb, 0);
      this.look.deepColor = packed(rgb, 1);
      this.look.duskColor = packed(rgb, 2);
      this.look.nightColor = packed(rgb, 3);
      this.look.hazeColor = packed(rgb, 4);
      this.look.hazeLight = MathHelper.clamp((0.6F * luma(this.look.hazeColor) + this.look.glowStrength * luma(packed(rgb, 6))) / 255.0F, 0.0F, 1.0F);
      this.look.rimColor = packed(rgb, 5);
      this.look.glowColor = packed(rgb, 6);
   }

   private static void add(float[] rgb, PeepSky.Palette p, float w) {
      if (!(w <= 0.0F)) {
         add(rgb, 0, p.top(), w);
         add(rgb, 1, p.deep(), w);
         add(rgb, 2, p.dusk(), w);
         add(rgb, 3, p.night(), w);
         add(rgb, 4, p.haze(), w);
         add(rgb, 5, p.rim(), w);
         add(rgb, 6, p.glow(), w);
      }
   }

   private static void add(float[] rgb, int c, int color, float w) {
      rgb[c * 3] = rgb[c * 3] + w * (color >> 16 & 0xFF);
      rgb[c * 3 + 1] = rgb[c * 3 + 1] + w * (color >> 8 & 0xFF);
      rgb[c * 3 + 2] = rgb[c * 3 + 2] + w * (color & 0xFF);
   }

   private static int packed(float[] rgb, int c) {
      return MathHelper.clamp(Math.round(rgb[c * 3]), 0, 255) << 16
         | MathHelper.clamp(Math.round(rgb[c * 3 + 1]), 0, 255) << 8
         | MathHelper.clamp(Math.round(rgb[c * 3 + 2]), 0, 255);
   }

   float hazeAt(float y) {
      return MathHelper.clamp(1.0F - Math.max(0.0F, 0.72F - y) / 0.4F, 0.0F, 1.0F) * this.look.hazeLight;
   }

   private static float luma(int rgb) {
      return 0.2126F * (rgb >> 16 & 0xFF) + 0.7152F * (rgb >> 8 & 0xFF) + 0.0722F * (rgb & 0xFF);
   }

   private int shade(float depth, float y, float horizon) {
      float d = Math.max(0.0F, depth);
      int col = d < 0.05F
         ? mix(this.look.topColor, this.look.deepColor, d / 0.05F)
         : (
            d < 0.3F
               ? mix(this.look.deepColor, this.look.duskColor, (d - 0.05F) / 0.25F)
               : mix(this.look.duskColor, this.look.nightColor, Math.min(1.0F, (d - 0.3F) / 0.5F))
         );
      float haze = MathHelper.clamp(1.0F - Math.max(0.0F, horizon - y) / 0.4F, 0.0F, 1.0F);
      col = mix(col, this.look.hazeColor, haze * 0.6F);
      return tint(d < 0.02F ? 0.92F : 0.97F, col);
   }

   static float heartbeat(float seconds) {
      return Math.max(beat(seconds, 0.17F), 0.6F * beat(seconds - 0.3F, 0.42F));
   }

   private static float beat(float t, float decay) {
      if (t < 0.0F) {
         return 0.0F;
      } else {
         return t < 0.14F ? smooth(0.0F, 0.14F, t) : (float)Math.exp(-(t - 0.14F) / decay);
      }
   }

   private static int average(int a, float wa, int b, float wb, int c, float wc) {
      float sum = Math.max(wa + wb + wc, 1.0E-4F);
      int r = Math.round(((a >> 16 & 0xFF) * wa + (b >> 16 & 0xFF) * wb + (c >> 16 & 0xFF) * wc) / sum);
      int g = Math.round(((a >> 8 & 0xFF) * wa + (b >> 8 & 0xFF) * wb + (c >> 8 & 0xFF) * wc) / sum);
      int bl = Math.round(((a & 0xFF) * wa + (b & 0xFF) * wb + (c & 0xFF) * wc) / sum);
      return MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(bl, 0, 255);
   }

   static float elevationToY(float degrees) {
      return 0.72F - degrees * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
   }

   static float hash01(int seed) {
      int h = seed * -1640531535;
      h ^= h >>> 15;
      h *= -2048144777;
      h ^= h >>> 13;
      h *= -1028477379;
      h ^= h >>> 16;
      return (h >>> 8) * 5.9604645E-8F;
   }

   static float hash01(int a, int b, int c) {
      return hash01(a * 73856093 ^ b * 19349663 ^ c * 83492791);
   }

   static int tint(float alpha, int rgb) {
      return ColorHelper.withAlpha(MathHelper.clamp(alpha, 0.0F, 1.0F), rgb);
   }

   static int mix(int from, int to, float t) {
      return ColorHelper.lerp(MathHelper.clamp(t, 0.0F, 1.0F), from, to) & 16777215;
   }

   static float smootherstep(float t) {
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }

   static float smooth(float from, float to, float x) {
      float t = MathHelper.clamp((x - from) / (to - from), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   static float wrap(float u) {
      return u - (float)Math.floor(u);
   }

   static float wrapDelta(float delta) {
      return delta - Math.round(delta);
   }

   private static PeepSky.Built build(int generation) {
      List<int[][]> pixels = new ArrayList<>();
      List<int[][]> edges = new ArrayList<>();
      List<int[][]> gilt = new ArrayList<>();
      List<int[][]> ghosts = new ArrayList<>();
      List<Integer> widths = new ArrayList<>();
      List<Integer> heights = new ArrayList<>();
      List<Integer> kinds = new ArrayList<>();

      for (int kind = 0; kind < KINDS.length; kind++) {
         BufferedImage art = load(KINDS[kind]);
         if (art != null) {
            float scale = Math.min(3.0F, 320.0F / Math.max(art.getWidth(), art.getHeight()));
            int w = Math.round(art.getWidth() * scale) + 52;
            int h = Math.round(art.getHeight() * scale) + 52;
            BufferedImage canvas = new BufferedImage(w, h, 2);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(art, 26, 26, w - 52, h - 52, null);
            g.dispose();
            int[] mask = canvas.getRGB(0, 0, w, h, null, 0, w);
            pixels.add(SkyMips.chain(neon(mask, w, h), w, h));
            edges.add(SkyMips.chain(PeepArt.rim(mask, w, h), w, h));
            gilt.add(SkyMips.chain(PeepArt.gold(mask, w, h), w, h));
            ghosts.add(SkyMips.chain(PeepArt.veil(mask, w, h), w, h));
            widths.add(w);
            heights.add(h);
            kinds.add(kind);
         }
      }

      PeepArt.Art[] art = PeepArt.drawAll();
      int[][][] artLevels = new int[art.length][][];

      for (int i = 0; i < art.length; i++) {
         artLevels[i] = SkyMips.chain(art[i].argb(), art[i].width(), art[i].height());
      }

      return new PeepSky.Built(
         generation,
         pixels.toArray(new int[0][][]),
         edges.toArray(new int[0][][]),
         gilt.toArray(new int[0][][]),
         ghosts.toArray(new int[0][][]),
         widths.stream().mapToInt(Integer::intValue).toArray(),
         heights.stream().mapToInt(Integer::intValue).toArray(),
         kinds.stream().mapToInt(Integer::intValue).toArray(),
         art,
         artLevels
      );
   }

   private static BufferedImage load(String kind) {
      try (InputStream in = PeepSky.class.getResourceAsStream("/assets/anubis/textures/peep/" + kind + ".png")) {
         if (in == null) {
            LOGGER.warn("[Anubis] Sky easter egg tattoo {} is missing", kind);
            return null;
         } else {
            return ImageIO.read(in);
         }
      } catch (IOException e) {
         LOGGER.warn("[Anubis] Sky easter egg tattoo {} couldn't be read", kind, e);
         return null;
      }
   }

   private static int[] neon(int[] mask, int w, int h) {
      float[] alpha = new float[w * h];

      for (int i = 0; i < alpha.length; i++) {
         alpha[i] = (mask[i] >>> 24) / 255.0F;
      }

      float[] halo = blur(alpha, w, h, 9);
      float[] solid = new float[w * h];

      for (int i = 0; i < solid.length; i++) {
         solid[i] = smooth(0.6F, 0.9F, alpha[i]);
      }

      float[] deep = blur(solid, w, h, 7);
      float[] thin = blur(solid, w, h, 1);
      int[] out = new int[w * h];

      for (int i = 0; i < out.length; i++) {
         float core = Math.max(alpha[i], Math.min(1.0F, thin[i] * 1.6F) * (1.0F - smooth(0.2F, 0.4F, deep[i])));
         float glow = Math.min(1.0F, halo[i] * 2.6F);
         float hot = smooth(0.7F, 1.0F, core) * (1.0F - 0.75F * smooth(0.45F, 0.8F, deep[i]));
         out[i] = tint(Math.max(core, glow * 0.85F), mix(16732080, 16766704, hot));
      }

      return out;
   }

   static float[] blur(float[] src, int w, int h, int radius) {
      float[] a = (float[])src.clone();
      float[] b = new float[src.length];

      for (int pass = 0; pass < 2; pass++) {
         for (int y = 0; y < h; y++) {
            float sum = 0.0F;

            for (int x = -radius; x <= radius; x++) {
               sum += a[y * w + MathHelper.clamp(x, 0, w - 1)];
            }

            for (int x = 0; x < w; x++) {
               b[y * w + x] = sum / (radius * 2 + 1);
               sum += a[y * w + Math.min(w - 1, x + radius + 1)] - a[y * w + Math.max(0, x - radius)];
            }
         }

         for (int x = 0; x < w; x++) {
            float sum = 0.0F;

            for (int y = -radius; y <= radius; y++) {
               sum += b[MathHelper.clamp(y, 0, h - 1) * w + x];
            }

            for (int y = 0; y < h; y++) {
               a[y * w + x] = sum / (radius * 2 + 1);
               sum += b[Math.min(h - 1, y + radius + 1) * w + x] - b[Math.max(0, y - radius) * w + x];
            }
         }
      }

      return a;
   }

   static {
      for (PeepSong song : PeepSong.ALL) {
         PALETTES[song.ordinal()] = palette(song);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Built(
      int generation,
      int[][][] tattoos,
      int[][][] rims,
      int[][][] golds,
      int[][][] veils,
      int[] widths,
      int[] heights,
      int[] kinds,
      PeepArt.Art[] art,
      int[][][] artLevels
   ) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Look {
      final float[] rgb = new float[21];
      float glowStrength;
      float light;
      float starLight;
      float hazeLight;
      int topColor;
      int deepColor;
      int duskColor;
      int nightColor;
      int hazeColor;
      int rimColor;
      int glowColor;
   }

   @Environment(EnvType.CLIENT)
   private record Palette(int top, int deep, int dusk, int night, int haze, int rim, int glow, float glowStrength, float light, float starLight) {
   }
}
