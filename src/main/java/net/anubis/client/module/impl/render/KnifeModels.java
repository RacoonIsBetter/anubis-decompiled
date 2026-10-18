package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class KnifeModels {
   static final int KARAMBIT_DETAIL = 2;
   static final float KARAMBIT_RING_X = -16.0F;
   static final float KARAMBIT_RING_Y = -1.0F;
   static final float KARAMBIT_BLADE_LENGTH = 21.0F;
   static final float[] KARAMBIT_TIP = karambitTip();
   private static final float KATANA_BOW = 0.0016F;
   static final float KATANA_BOW_FROM = 3.0F;
   static final float KATANA_BOW_RADIUS = 312.5F;

   private KnifeModels() {
   }

   private static float grain(int x, int y, int z) {
      int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
      h ^= h >>> 13;
      h *= 1540483477;
      h ^= h >>> 15;
      return 0.92F + 0.08F * ((h & 1023) / 1023.0F);
   }

   static KnifeMesh butterflyBlade() {
      KnifeMesh.Voxels v = new KnifeMesh.Voxels();

      for (int x = -3; x <= 21; x++) {
         int top = x <= 12 ? 2 : Math.round(2.0F - (x - 12) * 3.0F / 9.0F);
         int bottom = x <= 14 ? -2 : Math.round(-2.0F + (float)Math.pow((x - 14) / 7.0F, 2.0) * 1.0F);

         for (int y = bottom; y <= top; y++) {
            int ink;
            if (x < 0) {
               ink = 5;
            } else if (x <= 1) {
               ink = 1;
            } else if (y == bottom) {
               ink = 2;
            } else if (y == top) {
               ink = x >= 13 ? 2 : 1;
            } else {
               ink = 0;
            }

            v.set(x, y, 0, ink, grain(x, y, 0));
         }
      }

      return v.mesh(0.0F, 22.0F);
   }

   static KnifeMesh butterflyHandle(int side) {
      KnifeMesh.Voxels v = new KnifeMesh.Voxels();
      int inner = side > 0 ? 1 : -1;
      int outer = side > 0 ? 2 : -2;
      int cap = side > 0 ? 3 : -3;

      for (int x = -27; x <= 0; x++) {
         for (int y = -2; y <= 2; y++) {
            boolean corner = (x == 0 || x == -27) && Math.abs(y) == 2;
            boolean hole = Math.abs(y) <= 1 && (x <= -6 && x >= -8 || x <= -12 && x >= -14 || x <= -18 && x >= -20);
            if (!corner && !hole) {
               v.set(x, y, inner, 4, grain(x, y, inner));
               int ink = 3;
               if ((x == -10 || x == -16) && Math.abs(y) <= 1) {
                  ink = 6;
               }

               if (y == 0 && (x == -2 || x == -25)) {
                  ink = 5;
               }

               v.set(x, y, outer, ink, grain(x, y, outer));
            }
         }
      }

      v.set(-2, 0, cap, 5, 1.0F);
      v.set(-25, 0, cap, 5, 1.0F);
      if (side < 0) {
         v.set(-28, 1, inner, 5, 1.0F);
         v.set(-28, 2, inner, 5, 1.0F);
         v.set(-29, 2, inner, 5, 1.0F);
      }

      return v.mesh(-29.0F, 0.0F);
   }

   private static float[] karambitCurve(float s) {
      float x = 5.5F;
      float y = 0.5F;
      float step = 0.05F;

      for (float t = 0.0F; t < s; t += step) {
         float heading = -(0.04F * t + 0.0026F * t * t);
         x += (float)Math.cos(heading) * step;
         y += (float)Math.sin(heading) * step;
      }

      float heading = -(0.04F * s + 0.0026F * s * s);
      return new float[]{x, y, (float)Math.cos(heading), (float)Math.sin(heading)};
   }

   private static float[] karambitTip() {
      float[] c = karambitCurve(21.0F);
      return new float[]{c[0] * 2.0F, c[1] * 2.0F};
   }

   static KnifeMesh karambit() {
      KnifeMesh.Voxels v = new KnifeMesh.Voxels();
      int k = 2;
      float ringX = -16.0F / k;
      float ringY = -1.0F / k;

      for (int x = Math.round((ringX + 2.4F) * k); x < 6 * k; x++) {
         float px = (x + 0.5F) / k;
         float middle = 0.5F - (float)Math.pow((5.0F - px) / 11.0F, 2.0);

         for (int y = -6 * k; y <= 4 * k; y++) {
            float across = (y + 0.5F) / k - middle;
            if (!(Math.abs(across) > 2.45F)) {
               v.set(x, y, 0, 5, grain(x, y, 0));
               boolean rivet = Math.hypot(px + 3.0F, across) < 0.7F || Math.hypot(px - 2.5F, across) < 0.7F;
               boolean inlay = Math.abs(across) < 0.5F && px > -1.5F && px < 1.5F;
               boolean rim = Math.abs(across) > 1.95F;

               for (int z : new int[]{-1, 1}) {
                  int ink = rivet ? 5 : (inlay ? 6 : (rim ? 4 : 3));
                  v.set(x, y, z, ink, grain(x, y, z));
               }

               if (rivet) {
                  v.set(x, y, -2, 5, 1.0F);
                  v.set(x, y, 2, 5, 1.0F);
               }
            }
         }
      }

      for (int x = Math.round((ringX - 3.4F) * k); x <= Math.round((ringX + 3.4F) * k); x++) {
         for (int y = Math.round((ringY - 3.4F) * k); y <= Math.round((ringY + 3.4F) * k); y++) {
            float dx = (x + 0.5F) / k - ringX;
            float dy = (y + 0.5F) / k - ringY;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (!(d < 2.0F) && !(d > 3.0F) && !v.filled(x, y, 0)) {
               float shade = !(d > 2.75F) && !(d < 2.25F) ? 1.0F : 0.86F;

               for (int z = -1; z <= 1; z++) {
                  v.set(x, y, z, 5, shade * grain(x, y, z));
               }
            }
         }
      }

      int samples = 420;
      float[][] curve = new float[samples + 1][];

      for (int i = 0; i <= samples; i++) {
         curve[i] = karambitCurve(21.0F * i / samples);
      }

      for (int x = 5 * k; x <= 31 * k; x++) {
         for (int y = -24 * k; y <= 6 * k; y++) {
            float px = (x + 0.5F) / k;
            float py = (y + 0.5F) / k;
            int best = 0;
            float bestDistance = Float.MAX_VALUE;

            for (int i = 0; i <= samples; i++) {
               float dx = px - curve[i][0];
               float dy = py - curve[i][1];
               float d = dx * dx + dy * dy;
               if (d < bestDistance) {
                  bestDistance = d;
                  best = i;
               }
            }

            float[] c = curve[best];
            float dx = px - c[0];
            float dy = py - c[1];
            float along = dx * c[2] + dy * c[3];
            float across = -dx * c[3] + dy * c[2];
            if ((best != 0 || !(along < -0.5F)) && (best != samples || !(along > 0.3F))) {
               float left = 1.0F - (float)best / samples;
               float spine = 2.9F * (float)Math.pow(left, 0.6) + 0.15F;
               float edge = 2.9F * (float)Math.pow(left, 1.1) + 0.1F;
               if (!(across > spine) && !(across < -edge) && !v.filled(x, y, 0)) {
                  int ink = across < -edge + 0.55F ? 2 : (across > spine - 0.55F ? 1 : 0);
                  v.set(x, y, 0, ink, grain(x, y, 0));
               }
            }
         }
      }

      return v.mesh(5.0F * k, 27.0F * k);
   }

   static KnifeMesh katanaScabbard() {
      KnifeMesh.Voxels v = new KnifeMesh.Voxels();

      for (int x = 1; x <= 51; x++) {
         int bow = katanaBow(Math.min(x, 48));
         boolean mouth = x <= 2;
         int low = mouth ? bow - 4 : bow - 3;
         int high = mouth ? bow + 3 : bow + 2;
         int thick = mouth ? 2 : 1;

         for (int y = low; y <= high; y++) {
            for (int z = -thick; z <= thick; z++) {
               boolean outside = y == low || y == high || Math.abs(z) == thick;
               int ink;
               if (mouth || x >= 50) {
                  ink = 8;
               } else if (x >= 12 && x <= 14 && outside) {
                  ink = (x + y + z) % 2 == 0 ? 7 : 6;
               } else {
                  ink = 4;
               }

               v.set(x, y, z, ink, ink == 4 ? 0.9F + 0.1F * grain(x, y, z) : grain(x, y, z));
            }
         }
      }

      int knob = katanaBow(16);
      v.set(16, knob + 3, 0, 8, 1.0F);
      v.set(17, knob + 3, 0, 8, 1.0F);
      return v.mesh(1.0F, 51.0F);
   }

   static float katanaCentre(float x) {
      return 0.0016F * (x - 3.0F) * (x - 3.0F);
   }

   static int katanaBow(int x) {
      return Math.round(0.0016F * (x - 3.0F) * (x - 3.0F));
   }

   static KnifeMesh katana() {
      KnifeMesh.Voxels v = new KnifeMesh.Voxels();

      for (int x = -17; x <= -1; x++) {
         for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
               int ink;
               if (x == -1) {
                  ink = 8;
               } else if (x == -17) {
                  ink = y == 0 && z == 0 ? 8 : 5;
               } else {
                  boolean face = Math.abs(y) == 1 ^ Math.abs(z) == 1;
                  boolean window = face && ((x + 20) % 4 == 1 || (x + 20) % 4 == 2);
                  ink = window ? 2 : 7;
               }

               v.set(x, y, z, ink, ink == 2 ? 0.82F : grain(x, y, z));
            }
         }
      }

      for (int y = -4; y <= 4; y++) {
         for (int z = -4; z <= 4; z++) {
            float d = (float)Math.sqrt(y * y + z * z);
            if (!(d > 3.9F)) {
               v.set(0, y, z, d >= 3.0F ? 8 : 5, grain(0, y, z));
            }
         }
      }

      for (int y = -1; y <= 1; y++) {
         for (int z = -1; z <= 1; z++) {
            v.set(1, y, z, 8, grain(1, y, z));
         }

         v.set(2, y, 0, 8, grain(2, y, 0));
      }

      for (int x = 3; x <= 48; x++) {
         int bow = katanaBow(x);
         int lowest = x >= 48 ? bow + 1 : (x >= 46 ? bow : (x >= 44 ? bow - 1 : bow - 2));
         int highest = x >= 48 ? bow + 1 : bow + 1;

         for (int y = lowest; y <= highest; y++) {
            int ink;
            if (y == lowest) {
               ink = 2;
            } else if (y == highest) {
               ink = 1;
            } else if (y == lowest + 1) {
               ink = x > 5 && Math.sin(x * 0.85) + Math.sin(x * 0.37) * 0.6 > 0.35 ? 2 : 0;
            } else {
               ink = 0;
            }

            v.set(x, y, 0, ink, grain(x, y, 0));
         }
      }

      return v.mesh(3.0F, 49.0F);
   }
}
