package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class PeepEffects {
   private static final float SPOT_BASE = -7.0F;
   private static final float SPOT_LENGTH = 95.0F;
   private static final float SPOT_WIDTH = 30.0F;
   private static final float SPOT_SIDE = 0.085F;
   private static final int BLUE = 9074943;
   private static final int MAGENTA = 16736192;
   private static final int LILAC = 13995263;
   private static final int COOL = 8029951;
   static final int LIGHTNING_LANES = 2;
   static final int HEARTS = 20;
   private static final int METEOR_LANES = 15;
   private static final int RAIN_DROPS = 330;
   private static final int BEAMS = 8;
   private static final int SPARKS = 135;
   private static final int PETALS = 38;
   private static final int WISPS = 21;
   private static final int MOTES = 55;
   private static final int GLITTER = 84;
   private static final int SPOT_DUST = 44;
   private static final int SPIRITS = 12;
   private static final int FEATHERS = 22;
   private static final int METEOR_TRIES = 4;
   private static final int METEOR_SLOTS = 0;
   private static final int PETAL_SLOTS = 15;
   private static final int HEART_SLOTS = 53;
   private static final int WISP_SLOTS = 73;
   private static final int MOTE_SLOTS = 94;
   private static final int GLITTER_SLOTS = 149;
   private static final int SPIRIT_SLOTS = 233;
   private static final int FEATHER_SLOTS = 245;
   private static final float IN_VIEW = 0.8F;
   private static final float VIEW_SPREAD = 0.375F;
   private static final float CRY_ALONE_RAIN = 2.2F;
   private static final float RAIN_SLANT = 0.06F;
   private static final int BEAM_SAMPLES = 8;
   private static final float[] SCRATCH = new float[8];
   private static final float[] OTHER = new float[8];
   private static final float[] ARC = new float[9];
   private static final float[] AT = new float[2];
   private static final float METEOR_GAP = 5.0F;

   private PeepEffects() {
   }

   static void skyLight(PeepSky s, SkyPaint p, float cover, float time) {
      float bright = s.w(PeepSong.THE_BRIGHTSIDE) * cover;
      if (bright > 0.003F) {
         sunrise(s, p, bright, time);
      }

      float sun = s.w(PeepSong.SUNLIGHT_ON_YOUR_SKIN) * cover;
      if (sun > 0.003F) {
         sunlight(s, p, sun, time);
      }
   }

   private static void sunrise(PeepSky s, SkyPaint p, float w, float time) {
      float age = s.age(PeepSong.THE_BRIGHTSIDE);
      float rise = PeepSky.smootherstep(MathHelper.clamp(age / 14.0F, 0.0F, 1.0F));
      float height = MathHelper.lerp(rise, 0.28F, 0.7F) + 0.02F * MathHelper.sin(time * 0.25F);
      float horizon = 0.72F;
      float middle = horizon - 0.16F;
      int pink = PeepSky.tint(0.36F * w, 16743088);
      int none = PeepSky.tint(0.0F, 16743088);

      for (int i = 0; i < 64; i++) {
         float u0 = i / 64.0F;
         float u1 = (i + 1) / 64.0F;
         float underSun = PeepSky.smooth(0.06F, 0.0F, Math.abs(PeepSky.wrapDelta(u0 + 0.0078125F)));
         int gold = PeepSky.tint(0.28F * w * (1.0F - 0.5F * underSun), 16761963);
         p.gradient(true, u0, u1, horizon - height, middle, none, pink);
         p.gradient(true, u0, u1, middle, horizon + 0.04F, pink, gold);
      }

      float up = MathHelper.lerp(PeepSky.smootherstep(MathHelper.clamp(age / 18.0F, 0.0F, 1.0F)), -3.0F, 9.0F);
      SkyImage beam = s.art[7];

      for (int k = 0; k < 3; k++) {
         float tilt = (k - 1) * 0.55F + 0.08F * MathHelper.sin(time * 0.12F + k * 2.0F);
         float shine = 0.085F * (0.8F + 0.2F * MathHelper.sin(time * 0.3F + k * 1.9F)) * (1.0F - 0.3F * s.hazeAt(y(up)));
         beam(p, beam, 0.0F, up - 1.0F, tilt, k == 0 ? 36.0F : (k == 2 ? 44.0F : 40.0F), k == 0 ? 12.0F : (k == 2 ? 16.0F : 14.0F), 16763024, shine * w);
      }

      float sunY = y(up);
      float aim = clear(s, 0.0F, up);
      p.glow(0.0F, sunY, units(34.0F), 16752250, 0.23F * w);
      p.glow(0.0F, sunY, units(13.0F), 16763024, 0.2F * w * (0.4F + 0.6F * aim));
      p.glow(0.0F, sunY, units(3.6F), 16760468, 0.2F * w * (0.1F + 0.9F * aim) * core(s, sunY));
   }

   private static void sunlight(PeepSky s, SkyPaint p, float w, float time) {
      float sourceU = s.stageU + 0.05F;
      float sourceUp = 64.0F;
      p.glow(sourceU, y(50.0F), units(64.0F), 16736150, 0.13F * w);
      p.glow(sourceU, y(40.0F), units(70.0F), 16756848, 0.07F * w);
      p.glow(sourceU, y(sourceUp - 8.0F), units(34.0F), 16752762, 0.2F * w);
      p.glow(sourceU, y(sourceUp), units(14.0F), 16762976, 0.32F * w);
      p.glow(sourceU, y(sourceUp), units(5.0F), 16765562, 0.3F * w * core(s, y(sourceUp)));
      SkyImage cone = s.art[14];

      for (int k = 0; k < 5; k++) {
         float tilt = (float) Math.PI + (k - 2.0F) * 0.3F + 0.07F * MathHelper.sin(time * 0.17F + k * 1.3F);
         float length = 62.0F + 8.0F * PeepSky.hash01(k * 3 + 1);
         float width = 15.0F + 6.0F * PeepSky.hash01(k * 3 + 2);
         float breathe = 0.8F + 0.2F * MathHelper.sin(time * 0.31F + k * 1.7F);
         float shine = breathe * beamClear(s, sourceU, sourceUp + 2.0F, tilt, length, width) * w;
         beam(p, cone, sourceU, sourceUp + 2.0F, tilt, length, width, 16749704, 0.17F * shine);
         beam(p, cone, sourceU, sourceUp + 2.0F, tilt, length * 0.45F, width * 0.7F, 14721070, 0.62F * shine, false);
         beam(p, cone, sourceU, sourceUp + 2.0F, tilt, length * 0.45F, width * 0.6F, 16761418, 0.5F * shine);
      }
   }

   static void behind(PeepSky s, SkyPaint p, float time) {
      float a = s.alpha;
      float alone = s.w(PeepSong.CRY_ALONE) * a;
      if (alone > 0.003F) {
         lonely(s, p, alone, time);
      }

      float hell = s.w(PeepSong.HELLBOY) * a;
      if (hell > 0.003F) {
         float flicker = 0.85F + 0.15F * (0.5F + 0.5F * MathHelper.sin(time * (float) (Math.PI * 2) * 0.7F));
         horizonLine(p, 1.5F, 6.0F, 16724821, 0.17F * flicker * hell);
      }

      float chains = s.w(PeepSong.FOUR_GOLD_CHAINS) * a;
      if (chains > 0.003F) {
         horizonLine(p, 2.0F, 7.0F, 16765562, 0.22F * chains);
      }

      float stars = s.w(PeepSong.STAR_SHOPPING) * a;
      if (stars > 0.003F) {
         shootingStars(s, p, stars, time);
      }

      float beamer = s.w(PeepSong.BEAMER_BOY) * a;
      if (beamer > 0.003F) {
         concertBeams(s, p, beamer, time);
      }

      float spot = s.w(PeepSong.SPOTLIGHT) * a;
      if (spot > 0.003F) {
         spotlight(s, p, spot, time);
      }

      float awful = s.w(PeepSong.AWFUL_THINGS) * a;
      if (awful > 0.003F) {
         storm(s, p, awful, time);
      }

      float witch = s.w(PeepSong.WITCHBLADES) * a;
      if (witch > 0.003F) {
         mist(p, s.art[10], 5, time * 0.004F, -3.0F, 18.0F, 10108104, 0.2F * witch, true);
         mist(p, s.art[10], 4, -time * 0.0025F + 0.3F, 4.0F, 34.0F, 16736200, 0.38F * witch, true);
      }

      float ghost = s.w(PeepSong.GHOST_BOY) * a;
      if (ghost > 0.003F) {
         mist(p, s.art[10], 6, time * 0.0015F, -4.0F, 11.0F, 12356338, 0.28F * ghost, false);
         mist(p, s.art[10], 5, -time * 0.001F + 0.5F, 0.0F, 16.0F, 10516706, 0.1F * ghost, true);
         mist(p, s.art[10], 5, time * 0.0012F + 0.2F, -2.0F, 9.0F, 14715600, 0.14F * ghost, true);
      }
   }

   private static void lonely(PeepSky s, SkyPaint p, float w, float time) {
      mist(p, s.art[10], 6, time * 0.0012F, -2.0F, 8.0F, 2755106, 0.35F * w, false);
   }

   private static void shootingStars(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage streak = s.art[0];
      float[] at = SCRATCH;

      for (int pass = 0; pass < 2; pass++) {
         for (int lane = 0; lane < 15; lane++) {
            boolean hero = lane % 4 == 3;
            float period = 3.2F + 2.6F * PeepSky.hash01(lane * 3 + 1);
            float local = time / period + PeepSky.hash01(lane * 3 + 2);
            int n = MathHelper.floor(local);
            float since = (local - n) * period;
            float length = (1.1F + 0.6F * PeepSky.hash01(lane, n, 1)) * (hero ? 1.3F : 1.0F);
            if (!(since > length)) {
               float t = since / length;
               int slot = 0 + lane;
               int path = s.picked(slot, n);
               float anchor = s.anchor(slot, n);
               if (path < 0) {
                  path = 4;

                  for (int k = 0; k < 4 && path == 4; k++) {
                     if (meteorClear(s, lane, n * 4 + k, hero, anchor, length, since, time, at)) {
                        path = k;
                     }
                  }

                  s.pick(slot, n, path);
               }

               if (path < 4) {
                  int run = n * 4 + path;
                  meteor(lane, run, hero, anchor, 1.0F - (float)Math.pow(1.0F - t, 1.7), at);
                  float headU = at[0];
                  float headUp = at[1];
                  float dx = at[2];
                  float dy = at[3];
                  float travel = at[4];
                  float done = at[5];
                  float fade = PeepSky.smooth(0.0F, 0.1F, t)
                     * (1.0F - PeepSky.smooth(0.55F, 1.0F, t))
                     * PeepSky.smooth(3.0F, 10.0F, headUp)
                     * w
                     * clear(s, headU, headUp)
                     * PeepSky.smooth(0.5F, 1.0F, s.alpha);
                  if (!(fade <= 0.003F)) {
                     if (pass == 0) {
                        meteor(lane, run, hero, anchor, 1.0F, OTHER);
                        float cap = hero ? 34.0F : (s.lyricBox(OTHER[0], OTHER[1], 6.0F, 6.0F) > 0.05F ? 20.0F : 26.0F);
                        float tail = Math.min(travel * done, cap) * (0.6F + 0.4F * (1.0F - t));
                        float centreUp = headUp - dy * tail * 0.4F;
                        float centreU = headU - du(dx * tail * 0.4F, centreUp);
                        float angle = (float)Math.atan2(-dy, dx);
                        p.sprite(streak, centreU, y(centreUp), units(tail), units(4.5F), angle, PeepSky.tint(0.42F * fade, 16732080), true);
                        p.sprite(streak, centreU, y(centreUp), units(tail), units(1.2F), angle, PeepSky.tint(0.5F * fade, 16756960), true);
                     } else {
                        p.glow(headU, y(headUp), units(1.45F), 16756960, 0.38F * fade * core(s, y(headUp)));
                        p.glow(headU, y(headUp), units(7.0F), 16732080, 0.3F * fade);
                     }
                  }
               }
            }
         }
      }
   }

   private static void meteor(int lane, int run, boolean hero, float anchor, float done, float[] out) {
      float startU = anchor + 0.04F + (PeepSky.hash01(lane, run, 2) - 0.5F) * 0.36F;
      float startUp = 25.0F + 33.0F * PeepSky.hash01(lane, run, 3);
      float slope = (16.0F + 22.0F * PeepSky.hash01(lane, run, 5)) * (float) (Math.PI / 180.0);
      float travel = hero ? 40.0F + 15.0F * PeepSky.hash01(lane, run, 6) : 22.0F + 16.0F * PeepSky.hash01(lane, run, 6);
      float dx = -MathHelper.cos(slope);
      float dy = -MathHelper.sin(slope);
      out[1] = startUp + dy * travel * done;
      out[0] = startU + du(dx * travel * done, out[1]);
      out[2] = dx;
      out[3] = dy;
      out[4] = travel;
      out[5] = done;
   }

   private static boolean meteorClear(PeepSky s, int lane, int run, boolean hero, float anchor, float length, float since, float time, float[] scratch) {
      for (int j = 1; j <= 6; j++) {
         float t = 0.08F + 0.84F * (j - 1) / 5.0F;
         meteor(lane, run, hero, anchor, 1.0F - (float)Math.pow(1.0F - t, 1.7), scratch);
         float later = t * length - since;
         if (crowded(s, lane, time + later, scratch[0], scratch[1])) {
            return false;
         }

         if (s.lyricBox(scratch[0], scratch[1], 2.5F, 2.5F) > 0.02F) {
            return false;
         }

         if (!(scratch[1] < 3.0F)) {
            float ahead = 0.02F * later;
            if (s.clearOfTattoos(scratch[0] - ahead, scratch[1], 1.5F) < 0.9F) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean crowded(PeepSky s, int lane, float when, float u, float up) {
      for (int other = 0; other < 15; other++) {
         if (other != lane) {
            boolean hero = other % 4 == 3;
            float period = 3.2F + 2.6F * PeepSky.hash01(other * 3 + 1);
            float local = when / period + PeepSky.hash01(other * 3 + 2);
            int n = MathHelper.floor(local);
            float since = (local - n) * period;
            float length = (1.1F + 0.6F * PeepSky.hash01(other, n, 1)) * (hero ? 1.3F : 1.0F);
            if (!(since > length)) {
               int path = s.picked(0 + other, n);
               float anchor = s.anchored(0 + other, n);
               if (path >= 0 && path < 4 && !Float.isNaN(anchor)) {
                  meteor(other, n * 4 + path, hero, anchor, 1.0F - (float)Math.pow(1.0F - since / length, 1.7), OTHER);
                  float across = PeepSky.wrapDelta(OTHER[0] - u) * 360.0F * MathHelper.cos(up * (float) (Math.PI / 180.0));
                  float along = OTHER[1] - up;
                  if (across * across + along * along < 25.0F) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static void concertBeams(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage beam = s.art[7];
      SkyImage cone = s.art[14];

      for (int b = 0; b < 8; b++) {
         float baseU = (b + 0.5F) / 8.0F + 0.01F * MathHelper.sin(time * 0.05F + b);

         for (int j = 0; j < 2; j++) {
            float swing = MathHelper.sin(time * 0.28F * (1.0F + 0.12F * b) + b * 1.9F + j * 0.7F);
            float tilt = (j == 0 ? 1.0F : -1.0F) * (0.12F + 0.5F * swing);
            boolean pink = (b + j) % 2 == 0;
            float wide = pink ? 24.0F : 26.0F;
            float shine = (0.78F + 0.22F * MathHelper.sin(time * 1.1F + b * 2.0F + j)) * w * beamClear(s, baseU, -6.0F, tilt, 82.0F, wide);
            beam(p, cone, baseU, -6.0F, tilt, 70.0F, wide * 0.8F, pink ? 16736192 : 8029951, (pink ? 0.21F : 0.24F) * shine);
            beam(p, beam, baseU, -6.0F, tilt, 82.0F, wide, pink ? 16736192 : 9074943, 0.33F * shine);
            beam(p, beam, baseU, -6.0F, tilt, 80.0F, 3.0F, pink ? 16769268 : 14866175, 0.2F * shine);
         }
      }

      horizonLine(p, 1.0F, 7.0F, 9074943, 0.08F * w);

      for (int b = 0; b < 8; b++) {
         float baseU = (b + 0.5F) / 8.0F + 0.01F * MathHelper.sin(time * 0.05F + b);
         float flare = w * clear(s, baseU, 2.5F);
         p.glow(baseU, y(2.5F), units(9.0F), b % 2 == 0 ? 16736192 : 9074943, 0.3F * flare);
         p.glow(baseU, y(2.5F), units(3.9F), b % 2 == 0 ? 16770806 : 14866175, 0.36F * flare);
      }
   }

   private static void spotlight(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage cone = s.art[14];
      float tilt = spotTilt(time);
      float baseU = s.stageU + 0.085F;
      float shine = w * (0.3F + 0.7F * beamClear(s, baseU, -7.0F, tilt, 95.0F, 30.0F));
      beam(p, cone, baseU, -7.0F, tilt, 95.0F, 54.0F, 16732080, 0.13F * shine);
      beam(p, cone, baseU, -7.0F, tilt, 95.0F, 30.0F, 15746463, 0.45F * shine);
      beam(p, cone, baseU, -7.0F, tilt, 52.25F, 13.5F, 16751318, 0.2F * shine);
      arc(baseU, -7.0F, tilt);
      onArc(8.5F / Math.max(0.3F, MathHelper.cos(tilt)), 0.0F);
      float sourceU = AT[0];
      float sourceUp = AT[1];
      float source = w * (0.25F + 0.75F * clear(s, sourceU, sourceUp));
      p.glow(sourceU, y(sourceUp), units(13.0F), 16766704, 0.3F * source);
      p.glow(sourceU, y(sourceUp), units(5.8F), 16759008, 0.48F * source);

      for (int k = 0; k < 44; k++) {
         float rise = MathHelper.fractionalPart(PeepSky.hash01(k * 5 + 1) + time * 3.0F / 58.9F);
         float along = 95.0F * (0.08F + 0.62F * rise);
         float spread = coneHalf(along / 95.0F);
         float side = PeepSky.hash01(k * 5 + 2) - 0.5F;
         float across = side * spread + 0.5F * MathHelper.sin(time * 0.45F + k * 2.0F);
         onArc(along, across);
         float u = AT[0];
         float up = AT[1];
         float twinkle = 0.5F + 0.5F * (float)Math.pow(0.5F + 0.5F * MathHelper.sin(time * (0.7F + 0.5F * PeepSky.hash01(k * 5 + 3)) + k * 1.7F), 2.0);
         float ends = PeepSky.smooth(0.0F, 0.12F, rise) * (1.0F - PeepSky.smooth(0.8F, 1.0F, rise));
         float axis = (1.0F - 0.6F * Math.min(1.0F, Math.abs(side) * 2.0F)) * (1.0F - PeepSky.smooth(0.3F, 0.5F, Math.abs(side)));
         p.glow(u, y(up), units(0.4F + 0.4F * PeepSky.hash01(k * 5 + 4)), 16765164, 0.55F * twinkle * axis * ends * w * clear(s, u, up));
      }
   }

   static float spotTilt(float time) {
      return -0.05F + 0.4F * MathHelper.sin(time * 0.2F + 0.5F);
   }

   static float spotlightOn(PeepSky s, float u, float up) {
      arc(s.stageU + 0.085F, -7.0F, spotTilt(s.time));
      float along = alongArc(u, up);
      float across = acrossArc(u, up);
      if (!(along <= 0.0F) && !(along >= 95.0F)) {
         float t = along / 95.0F;
         float half = coneHalf(t);
         return (1.0F - PeepSky.smooth(half * 0.55F, half * 1.1F, across)) * (1.0F - PeepSky.smooth(0.75F, 1.0F, t));
      } else {
         return 0.0F;
      }
   }

   private static float coneHalf(float t) {
      return (0.05F + 0.84999996F * t) * 30.0F * 0.5F;
   }

   private static void storm(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage mist = s.art[10];
      mist(p, mist, 6, time * 0.002F, -2.0F, 24.0F, 1967638, 0.75F * w, false);
      mist(p, mist, 5, -time * 0.0013F + 0.4F, 10.0F, 34.0F, 2755104, 0.75F * w, false);
      mist(p, mist, 6, time * 0.002F + 0.37F, 2.0F, 20.0F, 5902140, 0.18F * w, true);
      mist(p, mist, 5, -time * 0.0016F + 0.15F, 16.0F, 52.0F, 9050714, 0.15F * w, true);
      float[] bolt = SCRATCH;

      for (int pass = 0; pass < 2; pass++) {
         for (int lane = 0; lane < 2; lane++) {
            float flash = lightning(lane, time, bolt) * w;
            if (!(flash <= 0.003F)) {
               float u = s.stageU + bolt[0];
               float tall = bolt[1];
               flash *= clear(s, u, tall * 0.5F);
               if (pass == 0) {
                  p.glow(u, y(tall * 0.7F), units(tall * 2.4F), 9050714, 0.2F * flash);
                  p.glow(u, y(tall * 0.55F), units(tall * 1.5F), 12720235, 0.26F * flash);
                  p.glow(u, y(tall * 0.45F), units(tall * 0.6F), 16732080, 0.16F * flash);
               } else {
                  p.sprite(
                     s.art[bolt[2] < 0.5F ? 11 : 12],
                     u,
                     y(tall * 0.5F - 0.5F),
                     units(tall * 0.5F),
                     units(tall + 1.0F),
                     bolt[3],
                     PeepSky.tint(0.6F * flash, 16777215),
                     true
                  );
               }
            }
         }
      }
   }

   static float lightning(int lane, float time, float[] out) {
      float period = 18.0F + 12.0F * PeepSky.hash01(lane * 7 + 1);
      float local = time / period + PeepSky.hash01(lane * 7 + 2);
      int n = MathHelper.floor(local);
      float since = (local - n) * period;
      if (since > 2.6F) {
         return 0.0F;
      }

      out[0] = (PeepSky.hash01(lane, n, 1) < 0.5F ? -1.0F : 1.0F) * (0.07F + 0.14F * PeepSky.hash01(lane, n, 5));
      out[1] = 16.0F + 12.0F * PeepSky.hash01(lane, n, 2);
      out[2] = PeepSky.hash01(lane, n, 3);
      out[3] = (PeepSky.hash01(lane, n, 4) - 0.5F) * 0.25F;
      return Math.min(1.0F, flicker(since) + 0.55F * flicker(since - 0.25F) + 0.3F * flicker(since - 0.65F));
   }

   private static float flicker(float t) {
      if (t < 0.0F) {
         return 0.0F;
      } else {
         return t < 0.2F ? PeepSky.smooth(0.0F, 0.2F, t) : (float)Math.exp(-(t - 0.2F) / 0.24F);
      }
   }

   static void front(PeepSky s, SkyPaint p, float time) {
      float a = s.alpha;
      float alone = s.w(PeepSong.CRY_ALONE);
      float rain = (s.w(PeepSong.CRYBABY) + 2.2F * alone) * a;
      if (rain > 0.003F) {
         rain(s, p, rain, 1.0F + 0.5F * alone, alone, time);
      }

      float petals = s.w(PeepSong.LIFE_IS_BEAUTIFUL) * a;
      if (petals > 0.003F) {
         petals(s, p, petals, time);
      }

      float hearts = s.w(PeepSong.SAVE_THAT_SHIT) * a;
      if (hearts > 0.003F) {
         hearts(s, p, hearts, time);
      }

      float feathers = s.w(PeepSong.FALLING_DOWN) * a;
      if (feathers > 0.003F) {
         feathers(s, p, feathers, time);
      }

      float sparks = s.w(PeepSong.HELLBOY) * a;
      if (sparks > 0.003F) {
         sparks(s, p, sparks, time);
      }

      float ghosts = s.w(PeepSong.GHOST_GIRL) * a;
      if (ghosts > 0.003F) {
         ghosts(s, p, ghosts, time);
      }

      float spirits = s.w(PeepSong.GHOST_BOY) * a;
      if (spirits > 0.003F) {
         spirits(s, p, spirits, time);
      }

      float motes = s.w(PeepSong.SUNLIGHT_ON_YOUR_SKIN) * a;
      if (motes > 0.003F) {
         motes(s, p, motes, time);
      }

      float glitter = s.w(PeepSong.FOUR_GOLD_CHAINS) * a;
      if (glitter > 0.003F) {
         glitter(s, p, glitter, time);
      }
   }

   private static void rain(PeepSky s, SkyPaint p, float amount, float boost, float alone, float time) {
      SkyImage drop = s.art[1];
      float share = amount / 2.2F;
      float strength = Math.min(1.0F, amount) * boost;
      int color = PeepSky.mix(16747216, 16736184, 0.5F * alone);
      float sin = MathHelper.sin(0.06F);
      float cos = MathHelper.cos(0.06F);
      float floor = MathHelper.lerp(alone, 0.28F, 0.22F);

      for (int pass = 0; pass < 2; pass++) {
         int stride = pass == 0 ? 1 : 3;

         for (int k = pass; k < 330; k += stride) {
            float keep = MathHelper.clamp((share - PeepSky.hash01(k * 5 + 1)) * 10.0F, 0.0F, 1.0F);
            if (!(keep <= 0.0F)) {
               float period = 2.2F + 1.2F * PeepSky.hash01(k * 5 + 2);
               float local = time / period + PeepSky.hash01(k * 5 + 3);
               int n = MathHelper.floor(local);
               float t = local - n;
               float near = PeepSky.hash01(k * 5 + 4);
               float up = MathHelper.lerp(t, 78.0F, -2.0F);
               float u = PeepSky.hash01(k, n, 7) + du(-(78.0F - up) * 0.06F, up);
               float fade = PeepSky.smooth(0.0F, 0.08F, t) * PeepSky.smooth(2.0F, 10.0F, up) * keep * clear(s, u, up);
               if (!(fade <= 0.003F)) {
                  fade *= floor + (1.0F - floor) * PeepSky.smooth(0.25F, 1.0F, s.clearOfTattoos(u, up, 0.4F));
                  boolean hero = near > 0.85F - 0.08F * alone;
                  float length = hero ? MathHelper.lerp(alone, 3.0F + 3.0F * near, 7.5F) : 3.0F + 3.0F * near;
                  if (hero) {
                     fade *= 1.0F + 0.3F * alone;
                  }

                  if (pass == 0) {
                     p.sprite(
                        drop, u, y(up), units(0.35F + 0.2F * near), units(length), 0.06F, PeepSky.tint((0.4F + 0.38F * near) * fade * strength, color), true
                     );
                  } else {
                     float headUp = up - 0.35F * length * cos;
                     p.glow(u + du(-0.35F * length * sin, headUp), y(headUp), units(0.7F), 16754397, 0.22F * fade * strength);
                  }
               }
            }
         }
      }
   }

   private static void petals(PeepSky s, SkyPaint p, float w, float time) {
      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 38; k++) {
            float h = PeepSky.hash01(k * 7 + 1);
            float period = 16.0F + 8.0F * h;
            float local = time / period + PeepSky.hash01(k * 7 + 2);
            int n = MathHelper.floor(local);
            float t = local - n;
            float near = PeepSky.hash01(k * 7 + 3);
            float up = MathHelper.lerp(t, 76.0F, 2.0F);
            float u = around(s, 15 + k, n, PeepSky.hash01(k, n, 9)) + du(3.5F * MathHelper.sin(time * 0.55F * (0.8F + 0.4F * h) + k * 1.3F), up);
            float tall = 2.2F + 1.6F * near;
            float fade = PeepSky.smooth(0.0F, 0.07F, t) * PeepSky.smooth(3.0F, 11.0F, up) * w * clearAround(s, u, up) * s.clearOfTattoos(u, up, tall * 0.4F);
            if (!(fade <= 0.003F)) {
               if (pass == 0) {
                  p.glow(u, y(up), units(tall * 1.1F), 16732080, 0.1F * fade * PeepSky.smooth(6.0F, 13.0F, up));
               } else {
                  float turn = MathHelper.cos(time * (0.7F + 0.5F * h) + k);
                  float flip = 0.42F + 0.58F * Math.abs(turn);
                  float spin = h * (float) (Math.PI * 2) + time * (0.22F + 0.3F * near) * (k % 2 == 0 ? 1.0F : -1.0F) + 0.6F * MathHelper.sin(time * 0.7F + k);
                  int shade = PeepSky.mix(turn < 0.0F ? 16765668 : 16770800, 15916262, 0.4F * (1.0F - near));
                  shade = PeepSky.mix(shade, 16740264, 1.0F - fade);
                  float seen = PeepSky.smooth(0.35F, 0.75F, fade);
                  SkyImage petal = s.art[k % 3 == 0 ? 15 : 2];
                  float w0 = units(tall * 0.8F * flip);
                  float h0 = units(tall);
                  p.sprite(petal, u, y(up), w0, h0, spin, PeepSky.tint(0.9F * seen, shade), false);
                  p.sprite(petal, u, y(up), w0, h0, spin, PeepSky.tint(0.1F * seen, 16777215), true);
               }
            }
         }
      }
   }

   static void heart(int k, float time, float[] out) {
      float h = PeepSky.hash01(k * 11 + 1);
      float period = 10.0F + 5.0F * h;
      float local = time / period + PeepSky.hash01(k * 11 + 2);
      int n = MathHelper.floor(local);
      float t = local - n;
      boolean cracks = PeepSky.hash01(k, n, 5) < 0.18F;
      out[1] = MathHelper.lerp(t, 76.0F, 3.0F);
      out[0] = spread(PeepSky.hash01(k, n, 13)) + du(2.4F * MathHelper.sin(time * 0.6F + k * 1.7F), out[1]);
      out[2] = t * period;
      out[3] = cracks ? out[2] - (0.26F + 0.3F * PeepSky.hash01(k, n, 6)) * period : Float.NaN;
      out[4] = (2.8F + 1.8F * PeepSky.hash01(k * 11 + 3)) * (cracks ? 1.35F : 1.0F);
      out[5] = t;
      out[6] = n;
   }

   private static void hearts(PeepSky s, SkyPaint p, float w, float time) {
      float[] at = SCRATCH;

      for (int pass = 0; pass < 2; pass++) {
         SkyImage half = s.art[pass == 0 ? 3 : 4];

         for (int k = 0; k < 20; k++) {
            heart(k, time, at);
            float u = s.anchor(53 + k, (int)at[6]) + at[0];
            float up = at[1];
            float after = at[3];
            float tall = at[4];
            float wide = tall * 128.0F / 120.0F;
            float t = at[5];
            wide /= 1.0F
               + 0.35F * PeepSky.smooth(15.0F, 40.0F, Math.abs(PeepSky.wrapDelta(u - s.aimU)) * 360.0F * MathHelper.cos(up * (float) (Math.PI / 180.0)));
            boolean broken = !Float.isNaN(after) && after >= 0.0F;
            if (broken || pass != 1) {
               float side = broken ? (pass == 0 ? -1.0F : 1.0F) : 0.0F;
               float spin = 0.35F * MathHelper.sin(time * 0.5F + k);
               float fade = PeepSky.smooth(0.0F, 0.07F, t) * PeepSky.smooth(1.0F, 8.0F, up) * w;
               float open = 0.0F;
               float apart = 0.0F;
               if (!Float.isNaN(after) && after > -0.4F) {
                  if (after < 0.0F) {
                     u += du(PeepSky.smooth(-0.4F, 0.0F, after) * 0.15F * MathHelper.sin(time * 37.0F + k), up);
                  } else {
                     float split = PeepSky.smootherstep(Math.min(1.0F, after / 1.8F));
                     open = 0.55F * split;
                     apart = (0.04F + 0.3F * split) * wide;
                     up -= 2.5F * split * split;
                     fade *= 1.0F - PeepSky.smooth(0.8F, 2.2F, after);
                  }
               }

               float keep = clearAround(s, u, up) * s.clearOfTattoos(u, up, tall * 0.45F);
               fade *= keep * keep;
               if (!(fade <= 0.003F)) {
                  float angle = spin + side * open;
                  float reach = 0.33F * tall;
                  float tipUp = up - reach;
                  float across = reach * MathHelper.sin(angle) + side * apart;
                  float down = -reach * MathHelper.cos(angle);
                  float centreUp = tipUp - down;
                  float centreU = u + du(across, centreUp);
                  if (pass == 0) {
                     p.glow(u, y(up), units(tall * 0.95F), 16732080, 0.12F * fade);
                  }

                  p.sprite(
                     broken ? half : s.art[13],
                     centreU,
                     y(centreUp),
                     units(wide),
                     units(tall),
                     angle,
                     PeepSky.tint(fade * PeepSky.smooth(0.12F, 0.45F, fade), k % 3 == 0 ? 16747208 : 16759014),
                     true
                  );
               }
            }
         }
      }

      SkyImage twinkle = s.art[8];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 20; k++) {
            heart(k, time, at);
            float after = at[3];
            if (!Float.isNaN(after) && !(after < 0.0F) && !(after > 0.8F)) {
               float u = s.anchor(53 + k, (int)at[6]) + at[0];
               float glint = PeepSky.smooth(0.0F, 0.12F, after) * (1.0F - PeepSky.smooth(0.2F, 0.8F, after)) * w * clearAround(s, u, at[1]);
               if (!(glint <= 0.003F)) {
                  if (pass == 0) {
                     p.glow(u, y(at[1]), units(at[4] * 1.2F), 16732080, 0.3F * glint);
                  } else {
                     p.sprite(twinkle, u, y(at[1]), units(at[4] * 2.6F), units(at[4] * 2.6F), 0.3F, PeepSky.tint(0.8F * glint, 16766704), true);
                  }
               }
            }
         }
      }
   }

   private static void feathers(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage feather = s.art[5];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 22; k++) {
            float h = PeepSky.hash01(k * 13 + 1);
            float period = 30.0F + 10.0F * h;
            float local = time / period + PeepSky.hash01(k * 13 + 2);
            int n = MathHelper.floor(local);
            float t = local - n;
            float near = PeepSky.hash01(k * 13 + 3);
            float swing = time * 0.75F * (0.85F + 0.3F * h) + k * 2.0F;
            float up = MathHelper.lerp(t, 78.0F, 4.0F) - 0.8F * MathHelper.abs(MathHelper.sin(swing));
            float u = around(s, 245 + k, n, PeepSky.hash01(k, n, 17)) + du(4.5F * MathHelper.sin(swing), up);
            float tall = (4.0F + 2.0F * near) * 1.1F;
            float fade = PeepSky.smooth(0.0F, 0.08F, t) * PeepSky.smooth(2.0F, 10.0F, up) * w * clearAround(s, u, up) * s.clearOfTattoos(u, up, tall * 0.25F);
            if (!(fade <= 0.003F)) {
               if (pass == 0) {
                  p.glow(u, y(up), units(tall * 0.5F), 16732080, 0.22F * fade);
               } else {
                  float spin = 0.5F * MathHelper.cos(swing) + (PeepSky.hash01(k * 13 + 4) - 0.5F) * 0.6F;
                  int color = PeepSky.mix(16756446, 14696602, 1.0F - fade);
                  p.sprite(feather, u, y(up), units(tall * 0.28846154F), units(tall), spin, PeepSky.tint(fade, color), true);
               }
            }
         }
      }
   }

   private static void sparks(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage streak = s.art[0];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 135; k++) {
            float h = PeepSky.hash01(k * 17 + 1);
            float period = 3.5F + 2.5F * h;
            float local = time / period + PeepSky.hash01(k * 17 + 2);
            int n = MathHelper.floor(local);
            float t = local - n;
            float near = PeepSky.hash01(k * 17 + 3);
            float top = 6.0F + 18.0F * PeepSky.hash01(k, n, 4);
            float up = -1.0F + (top + 1.0F) * (1.0F - (float)Math.pow(1.0F - t, 2.2));
            float swayPhase = time * (1.1F + 0.6F * h) + k;
            float u = PeepSky.hash01(k, n, 19) + du(1.8F * MathHelper.sin(swayPhase) * t, up);
            float flicker = 0.65F + 0.35F * (0.5F + 0.5F * MathHelper.sin(time * (13.0F + 12.0F * h) + k * 2.3F));
            float fade = PeepSky.smooth(0.03F, 0.12F, t) * (1.0F - PeepSky.smooth(0.3F, 0.85F, t)) * flicker * w * clearAround(s, u, up);
            if (!(fade <= 0.003F)) {
               float size = 0.4F + 0.5F * near;
               int color = PeepSky.mix(PeepSky.mix(16751206, 16727150, PeepSky.smooth(0.1F, 0.5F, t)), 12720235, PeepSky.smooth(0.5F, 1.0F, t));
               float lean = 1.8F * MathHelper.cos(swayPhase) * (1.1F + 0.6F * h) * t / Math.max(1.0F, top);
               if (pass == 0) {
                  float trail = 3.5F + 2.5F * near;
                  p.sprite(
                     streak, u, y(up - trail * 0.4F), units(trail), units(size * 0.7F), (float) (-Math.PI / 2) + lean, PeepSky.tint(0.45F * fade, color), true
                  );
               } else {
                  p.glow(u, y(up), units(size * 1.2F), color, 0.58F * fade);
                  p.glow(u, y(up), units(size * 0.7F), 16763024, 0.4F * fade * (1.0F - PeepSky.smooth(0.15F, 0.45F, t)));
                  p.sprite(
                     s.art[1],
                     u,
                     y(up - size * 0.45F),
                     units(size * 0.5F),
                     units(size * 1.8F),
                     (float) Math.PI + lean,
                     PeepSky.tint(0.75F * fade, 16762010),
                     true
                  );
               }
            }
         }
      }
   }

   private static void ghosts(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage wisp = s.art[6];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 21; k++) {
            float h = PeepSky.hash01(k * 19 + 1);
            float period = 10.0F + 4.0F * h;
            float local = time / period + PeepSky.hash01(k * 19 + 2);
            int n = MathHelper.floor(local);
            float t = local - n;
            float near = PeepSky.hash01(k * 19 + 3);
            float headUp = MathHelper.lerp((float)Math.pow(t, 0.9), -4.0F, 58.0F);
            float sway = time * 0.45F + k * 1.9F;
            float grow = 0.85F + 0.35F * t;
            float tall = (8.0F + 5.0F * near) * grow;
            float wide = tall * 96.0F / 256.0F * 1.8F;
            float centreUp = headUp - tall * 0.3F;
            float u = around(s, 73 + k, n, PeepSky.hash01(k, n, 23)) + du(3.6F * MathHelper.sin(sway), centreUp);
            float fade = PeepSky.smooth(0.0F, 0.12F, t)
               * (1.0F - PeepSky.smooth(0.55F, 1.0F, t))
               * PeepSky.smooth(1.0F, 12.0F, headUp)
               * w
               * clearAround(s, u, headUp)
               * clearAround(s, u, centreUp);
            if (!(fade <= 0.003F)) {
               if (pass == 0) {
                  p.sprite(wisp, u, y(centreUp), units(wide), units(tall), 0.32F * MathHelper.cos(sway), PeepSky.tint(0.42F * fade, 16750300), true);
               } else {
                  float lean = 0.32F * MathHelper.cos(sway);
                  float reach = tall * 0.3F;
                  float glowUp = centreUp + reach * MathHelper.cos(lean);
                  p.glow(u + du(reach * MathHelper.sin(lean), glowUp), y(glowUp), units(2.0F), 16759020, 0.24F * fade);
               }
            }
         }
      }
   }

   private static void spirits(PeepSky s, SkyPaint p, float w, float time) {
      for (int k = 0; k < 12; k++) {
         float h = PeepSky.hash01(k * 31 + 1);
         float period = 11.0F + 6.0F * h;
         float local = time / period + PeepSky.hash01(k * 31 + 2);
         int n = MathHelper.floor(local);
         float t = local - n;
         float up = MathHelper.lerp((float)Math.pow(t, 0.85), 1.0F, 26.0F + 10.0F * PeepSky.hash01(k, n, 3));
         float sway = time * (0.4F + 0.2F * h) + k * 2.3F;
         float u = around(s, 233 + k, n, PeepSky.hash01(k, n, 2)) + du(2.2F * MathHelper.sin(sway), up);
         float flicker = 0.8F + 0.2F * MathHelper.sin(time * (1.3F + 0.6F * h) + k);
         float fade = PeepSky.smooth(0.0F, 0.15F, t) * (1.0F - PeepSky.smooth(0.5F, 1.0F, t)) * flicker * w * clearAround(s, u, up);
         if (!(fade <= 0.003F)) {
            float size = 0.7F + 0.5F * PeepSky.hash01(k * 31 + 4);
            p.glow(u, y(up), units(size * 6.0F), 10258144, 0.12F * fade);
            p.glow(u, y(up), units(size * 2.0F), 14208255, 0.3F * fade);
            p.glow(u, y(up), units(size * 0.7F), 16184063, 0.6F * fade * core(s, y(up)));
         }
      }
   }

   private static void motes(PeepSky s, SkyPaint p, float w, float time) {
      float beat = 1.0F + 0.5F * s.pulse;

      for (int k = 0; k < 55; k++) {
         float h = PeepSky.hash01(k * 23 + 1);
         float period = 9.0F + 6.0F * h;
         float local = time / period + PeepSky.hash01(k * 23 + 2);
         int n = MathHelper.floor(local);
         float t = local - n;
         float up = 4.0F + 56.0F * PeepSky.hash01(k, n, 3) + 3.0F * t + 1.5F * MathHelper.sin(time * 0.15F + k * 1.3F);
         float u = around(s, 94 + k, n, PeepSky.hash01(k, n, 2)) + du(1.2F * MathHelper.sin(time * 0.11F + k), up);
         float twinkle = 0.55F + 0.45F * MathHelper.sin(time * (0.8F + 0.5F * h) + k);
         float fade = twinkle * PeepSky.smooth(0.0F, 0.2F, t) * (1.0F - PeepSky.smooth(0.75F, 1.0F, t)) * w * clearAround(s, u, up) * beat;
         if (!(fade <= 0.003F)) {
            float size = (0.6F + 0.72F * PeepSky.hash01(k * 23 + 4)) * (k % 2 == 0 ? 1.3F : 1.0F);
            int color = k % 2 == 0 ? 16762714 : (k % 4 == 1 ? 16752324 : 16765050);
            p.glow(u, y(up), units(size * 4.0F), color, 0.2F * fade);
            p.glow(u, y(up), units(size * 1.4F), color, 0.35F * fade);
            p.glow(u, y(up), units(size * 0.6F), color, 0.42F * fade * core(s, y(up)));
         }
      }
   }

   private static void glitter(PeepSky s, SkyPaint p, float w, float time) {
      SkyImage glint = s.art[16];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 84; k++) {
            float h = PeepSky.hash01(k * 29 + 1);
            float phase = (time * (0.9F + 0.9F * h) + k * 2.7F) / (float) (Math.PI * 2);
            int n = MathHelper.floor(phase);
            float t = phase - n;
            if (!(t >= 0.5F)) {
               float sparkle = (float)Math.pow(MathHelper.sin((float) Math.PI * (float)Math.pow(t * 2.0F, 0.6)), 2.0);
               float up = 5.0F + 60.0F * PeepSky.hash01(k, n, 3) + 1.5F * MathHelper.sin(time * 0.2F + k);
               float u = around(s, 149 + k, n, PeepSky.hash01(k, n, 2)) + du(0.6F * MathHelper.sin(time * 0.1F + k), up);
               float fade = PeepSky.smooth(0.05F, 0.3F, sparkle) * w * clearAround(s, u, up);
               if (!(fade <= 0.003F)) {
                  float size = (1.3F + 1.2F * PeepSky.hash01(k * 29 + 4)) * (0.25F + 0.75F * sparkle);
                  boolean pink = k % 4 == 3;
                  int color = pink ? 16747213 : (k % 2 == 0 ? 16769952 : 16771504);
                  float low = (1.0F - 0.35F * PeepSky.smooth(14.0F, 4.0F, up)) * core(s, y(up));
                  if (pass == 0) {
                     p.glow(u, y(up), units(size * 0.9F), pink ? 16732080 : 16758858, 0.28F * fade * sparkle);
                  } else {
                     p.sprite(
                        glint,
                        u,
                        y(up),
                        units(size),
                        units(size),
                        0.4F * MathHelper.sin(time * 0.3F + k),
                        PeepSky.tint((pink ? 0.65F : 0.8F) * fade * low, color),
                        true
                     );
                  }
               }
            }
         }
      }
   }

   static void orbits(PeepSky s, SkyPaint p, float time, float w) {
      SkyImage arc = s.art[9];
      float cap = units(18.0F);

      for (int k = 0; k < s.shownTattoos; k++) {
         float size = Math.min(cap, (s.tw[k] + s.th[k]) * 0.5F);
         float a = s.tGlow[k] * (0.6F + 0.4F * s.tGlow[k]) * PeepSky.smooth(0.3F, 0.5F, s.tGlow[k]) * w;
         if (!(a <= 0.003F)) {
            float inner = time * 0.55F + k * 1.3F;
            float outer = time * 0.33F + k * 2.1F + 2.0F;
            p.sprite(arc, s.tu[k], s.ty[k], size * 1.35F, size * 1.35F, inner, PeepSky.tint(0.7F * a, 13995263), true);
            p.sprite(arc, s.tu[k], s.ty[k], size * 1.6F, size * 1.6F, outer, PeepSky.tint(0.5F * a, 16748512), true);
            arcHead(s, p, k, size * 1.35F, inner, 0.5F * a);
            arcHead(s, p, k, size * 1.6F, outer, 0.35F * a);
         }
      }
   }

   private static void arcHead(PeepSky s, SkyPaint p, int k, float size, float spin, float alpha) {
      float radius = size * 0.421875F;
      float up = (0.72F - s.ty[k]) * (float) (Math.PI / 3) * (180.0F / (float)Math.PI);
      float u = s.tu[k] + du(radius * MathHelper.cos(spin) * 60.0F, up);
      float headY = s.ty[k] + radius * MathHelper.sin(spin);
      p.glow(u, headY, units(3.0F), 16732080, 0.3F * alpha);
      p.glow(u, headY, units(1.8F), 16748512, 0.3F * alpha);
      p.glow(u, headY, units(0.8F), 16766704, alpha);
   }

   static void goldSparkles(PeepSky s, SkyPaint p, float time, float w) {
      SkyImage twinkle = s.art[8];

      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < s.shownTattoos; k++) {
            float a = PeepSky.smooth(0.15F, 0.6F, s.tGlow[k]) * w;
            if (!(a <= 0.003F)) {
               for (int j = 0; j < 3; j++) {
                  float angle = j * 2.1F + k + time * 0.15F;
                  float sparkle = (float)Math.pow(Math.max(0.0F, MathHelper.sin(time * (1.1F + 0.3F * j) + k * 1.9F + j * 2.2F)), 4.0);
                  if (!(sparkle <= 0.03F)) {
                     float u = s.tu[k]
                        + MathHelper.cos(angle) * s.tw[k] * 0.5F * 60.0F / (360.0F * Math.max(0.2F, MathHelper.cos((0.72F - s.ty[k]) * (float) (Math.PI / 3))));
                     float y = s.ty[k] + MathHelper.sin(angle) * s.th[k] * 0.55F;
                     float size = Math.min(s.th[k] * 0.6F, units(7.0F)) * (0.2F + 0.8F * sparkle);
                     float shine = a * PeepSky.smooth(0.03F, 0.25F, sparkle);
                     if (pass == 0) {
                        p.glow(u, y, size * 0.35F, 16758858, 0.25F * shine * sparkle);
                     } else {
                        p.sprite(twinkle, u, y, size, size, time * 0.4F + j, PeepSky.tint(shine, j == 1 ? 16774354 : 16770720), true);
                     }
                  }
               }
            }
         }
      }
   }

   static float spread(float h) {
      return h < 0.8F ? (h / 0.8F - 0.5F) * 0.375F : (h - 0.8F) / 0.19999999F;
   }

   private static float around(PeepSky s, int slot, int run, float h) {
      return s.anchor(slot, run) + spread(h);
   }

   private static void horizonLine(SkyPaint p, float peak, float top, int rgb, float alpha) {
      if (!(alpha <= 0.003F)) {
         int glow = PeepSky.tint(alpha, rgb);
         int none = PeepSky.tint(0.0F, rgb);
         float high = y(top);
         float middle = y(peak);
         float below = y(-1.0F);

         for (int i = 0; i < 48; i++) {
            float u0 = i / 48.0F;
            float u1 = (i + 1) / 48.0F;
            p.gradient(true, u0, u1, high, middle, none, glow);
            p.gradient(true, u0, u1, middle, below, glow, none);
         }
      }
   }

   private static void mist(SkyPaint p, SkyImage mist, int repeats, float drift, float lowUp, float highUp, int rgb, float alpha, boolean additive) {
      if (!(alpha <= 0.003F)) {
         float top = y(highUp);
         float bottom = y(lowUp);
         float offset = PeepSky.wrap(drift);
         int color = PeepSky.tint(alpha, rgb);

         for (int i = 0; i < repeats; i++) {
            float u0 = (i + offset) / repeats;
            p.band(mist, u0, u0 + 1.0F / repeats, top, bottom, color, additive, 24);
         }
      }
   }

   private static void beam(SkyPaint p, SkyImage beam, float baseU, float baseUp, float tilt, float length, float width, int rgb, float alpha) {
      beam(p, beam, baseU, baseUp, tilt, length, width, rgb, alpha, true);
   }

   private static void beam(SkyPaint p, SkyImage beam, float baseU, float baseUp, float tilt, float length, float width, int rgb, float alpha, boolean additive) {
      if (!(alpha <= 0.003F)) {
         arc(baseU, baseUp, tilt);
         float spin = onArc(length * 0.5F, 0.0F);
         p.sprite(beam, AT[0], y(AT[1]), units(width), units(length), spin, PeepSky.tint(alpha, rgb), additive);
      }
   }

   private static void arc(float baseU, float baseUp, float tilt) {
      float a = baseU * (float) (Math.PI * 2);
      float e = baseUp * (float) (Math.PI / 180.0);
      float ca = MathHelper.cos(a);
      float sa = MathHelper.sin(a);
      float ce = MathHelper.cos(e);
      float se = MathHelper.sin(e);
      float ct = MathHelper.cos(tilt);
      float st = MathHelper.sin(tilt);
      ARC[0] = ce * ca;
      ARC[1] = ce * sa;
      ARC[2] = se;
      ARC[3] = -ct * se * ca - st * sa;
      ARC[4] = -ct * se * sa + st * ca;
      ARC[5] = ct * ce;
   }

   private static float onArc(float along, float across) {
      float d = along * (float) (Math.PI / 180.0);
      float cd = MathHelper.cos(d);
      float sd = MathHelper.sin(d);
      float px = cd * ARC[0] + sd * ARC[3];
      float py = cd * ARC[1] + sd * ARC[4];
      float pz = cd * ARC[2] + sd * ARC[5];
      float tx = cd * ARC[3] - sd * ARC[0];
      float ty = cd * ARC[4] - sd * ARC[1];
      float tz = cd * ARC[5] - sd * ARC[2];
      if (across != 0.0F) {
         float c = across * (float) (Math.PI / 180.0);
         float cc = MathHelper.cos(c);
         float sc = MathHelper.sin(c);
         float nx = ty * pz - tz * py;
         float ny = tz * px - tx * pz;
         float nz = tx * py - ty * px;
         px = cc * px + sc * nx;
         py = cc * py + sc * ny;
         pz = cc * pz + sc * nz;
      }

      ARC[6] = px;
      ARC[7] = py;
      ARC[8] = pz;
      float a = (float)Math.atan2(py, px);
      float e = (float)Math.asin(MathHelper.clamp(pz, -1.0F, 1.0F));
      AT[0] = PeepSky.wrap(a / (float) (Math.PI * 2));
      AT[1] = e * (180.0F / (float)Math.PI);
      float ca = MathHelper.cos(a);
      float sa = MathHelper.sin(a);
      float se = MathHelper.sin(e);
      float ce = MathHelper.cos(e);
      float up = -tx * se * ca - ty * se * sa + tz * ce;
      float right = -tx * sa + ty * ca;
      return (float)Math.atan2(right, up);
   }

   private static float alongArc(float u, float up) {
      float a = u * (float) (Math.PI * 2);
      float e = up * (float) (Math.PI / 180.0);
      float ce = MathHelper.cos(e);
      float x = ce * MathHelper.cos(a);
      float y = ce * MathHelper.sin(a);
      float z = MathHelper.sin(e);
      return (float)Math.atan2(x * ARC[3] + y * ARC[4] + z * ARC[5], x * ARC[0] + y * ARC[1] + z * ARC[2]) * (180.0F / (float)Math.PI);
   }

   private static float acrossArc(float u, float up) {
      float a = u * (float) (Math.PI * 2);
      float e = up * (float) (Math.PI / 180.0);
      float ce = MathHelper.cos(e);
      float x = ce * MathHelper.cos(a);
      float y = ce * MathHelper.sin(a);
      float z = MathHelper.sin(e);
      float nx = ARC[4] * ARC[2] - ARC[5] * ARC[1];
      float ny = ARC[5] * ARC[0] - ARC[3] * ARC[2];
      float nz = ARC[3] * ARC[1] - ARC[4] * ARC[0];
      return (float)Math.asin(MathHelper.clamp(Math.abs(x * nx + y * ny + z * nz), 0.0F, 1.0F)) * (180.0F / (float)Math.PI);
   }

   private static float beamClear(PeepSky s, float baseU, float baseUp, float tilt, float length, float width) {
      arc(baseU, baseUp, tilt);
      float along = MathHelper.clamp(alongArc(s.aimU, s.viewUp), 0.0F, length);
      onArc(along, 0.0F);
      float a = s.aimU * (float) (Math.PI * 2);
      float e = s.viewUp * (float) (Math.PI / 180.0);
      float ce = MathHelper.cos(e);
      float dot = ce * MathHelper.cos(a) * ARC[6] + ce * MathHelper.sin(a) * ARC[7] + MathHelper.sin(e) * ARC[8];
      float gap = (float)Math.acos(MathHelper.clamp(dot, -1.0F, 1.0F)) * (180.0F / (float)Math.PI) - width * (0.025F + 0.42F * along / length);
      float text = 0.0F;

      for (int i = 1; i <= 8; i++) {
         float at = length * i / 9.0F;
         float half = width * (0.025F + 0.42F * at / length) * 0.5F;
         onArc(at, 0.0F);
         text = Math.max(text, s.lyricBox(AT[0], AT[1], half, half));
      }

      return (0.35F + 0.65F * PeepSky.smooth(3.0F, 12.0F, gap)) * (1.0F - 0.45F * text);
   }

   private static float clear(PeepSky s, float u, float up) {
      return s.clear(u, up);
   }

   private static float core(PeepSky s, float y) {
      return 1.0F - 0.4F * s.hazeAt(y);
   }

   private static float clearAround(PeepSky s, float u, float up) {
      return s.clear(u, up);
   }

   private static float y(float up) {
      return PeepSky.elevationToY(up);
   }

   private static float units(float degrees) {
      return degrees * (float) (Math.PI / 180.0) / (float) (Math.PI / 3);
   }

   private static float du(float degrees, float up) {
      return degrees / (360.0F * Math.max(0.2F, MathHelper.cos(up * (float) (Math.PI / 180.0))));
   }
}
