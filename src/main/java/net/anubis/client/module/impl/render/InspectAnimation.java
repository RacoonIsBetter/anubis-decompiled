package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
final class InspectAnimation {
   static final String CLASSIC = "Classic";
   static final String TWIRL = "Twirl";
   static final int X = 0;
   static final int Y = 1;
   static final int Z = 2;
   static final int YAW = 3;
   static final int PITCH = 4;
   static final int ROLL = 5;
   static final int FLIP = 6;
   static final int SPIN = 7;
   static final int PART_A = 8;
   static final int PART_B = 9;
   static final int SHINE = 10;
   static final int SLIDE = 11;
   static final int DRAW = 12;
   static final int SHEATH = 13;
   static final int OFF_HAND = 14;
   static final int OFF_AT = 15;
   static final int CHANNELS = 16;
   static final float GRIP_X = 0.07F;
   static final float GRIP_Y = -0.05F;
   static final float GRIP_Z = -0.02F;
   static final float CENTER_X = 0.07F;
   static final float CENTER_Y = 0.2F;
   static final float CENTER_Z = 0.07F;
   static final float BLADE_Y = 0.94F;
   static final float BLADE_Z = 0.342F;
   static final float SECONDS = 3.2F;
   private static final float[][] CLASSIC_KEYS = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.14F, -0.24F, 0.17F, 0.1F, 60.0F, -10.0F, 50.0F, 0.0F, 0.0F},
      {0.3F, -0.25F, 0.18F, 0.11F, 70.0F, -5.0F, 46.0F, 0.0F, 0.0F},
      {0.4F, -0.24F, 0.21F, 0.11F, 66.0F, -6.0F, 48.0F, 180.0F, 0.0F},
      {0.46F, -0.24F, 0.2F, 0.11F, 64.0F, -7.0F, 49.0F, 184.0F, 0.0F},
      {0.56F, -0.25F, 0.19F, 0.11F, 70.0F, -5.0F, 46.0F, 360.0F, 0.0F},
      {0.62F, -0.25F, 0.18F, 0.11F, 70.0F, -5.0F, 46.0F, 360.0F, 0.0F},
      {0.8F, -0.23F, 0.19F, 0.1F, 56.0F, -10.0F, 52.0F, 366.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 360.0F, 0.0F}
   };
   private static final float[][] TWIRL_KEYS = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.12F, -0.2F, 0.15F, 0.08F, 75.0F, -6.0F, 20.0F, 0.0F, -20.0F},
      {0.22F, -0.21F, 0.2F, 0.09F, 78.0F, -6.0F, 22.0F, 0.0F, 0.0F},
      {0.34F, -0.22F, 0.3F, 0.1F, 80.0F, -4.0F, 24.0F, 0.0F, 180.0F},
      {0.46F, -0.21F, 0.2F, 0.09F, 78.0F, -6.0F, 22.0F, 0.0F, 360.0F},
      {0.7F, -0.24F, 0.17F, 0.1F, 68.0F, -8.0F, 42.0F, 0.0F, 360.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 360.0F}
   };
   private static final InspectAnimation.Clip CLASSIC_CLIP = new InspectAnimation.Clip(CLASSIC_KEYS, 3.2F, 0.3F, 0.62F);
   private static final InspectAnimation.Clip TWIRL_CLIP = new InspectAnimation.Clip(TWIRL_KEYS, 3.2F, 0.22F, 0.46F);

   private InspectAnimation() {
   }

   static InspectAnimation.Clip clip(String style) {
      return "Twirl".equals(style) ? TWIRL_CLIP : CLASSIC_CLIP;
   }

   static void place(MatrixStack pose, float[] at, float weight, int side, float centerX, float centerY, float centerZ) {
      float turn = (float) (Math.PI / 180.0) * weight;
      float yaw = MathHelper.wrapDegrees(at[3]) * turn * side;
      float pitch = MathHelper.wrapDegrees(at[4]) * turn;
      float roll = MathHelper.wrapDegrees(at[5]) * turn * side;
      float flip = MathHelper.wrapDegrees(at[6]) * turn * side;
      float spin = MathHelper.wrapDegrees(at[7]) * turn;
      pose.translate(at[0] * weight * side, at[1] * weight, at[2] * weight);
      float gripX = 0.07F * side;
      pose.multiply(new Quaternionf().rotateX(pitch).rotateZ(roll).rotateY(yaw), gripX, -0.05F, -0.02F);
      pose.multiply(new Quaternionf().rotateX(spin), centerX * side, centerY, centerZ);
      pose.multiply(new Quaternionf().rotateAxis(flip, 0.0F, 0.94F, 0.342F), gripX, -0.05F, -0.02F);
   }

   static float[] sample(float[][] keys, float t, float[] out) {
      t = MathHelper.clamp(t, 0.0F, 1.0F);
      int i = 0;

      while (i < keys.length - 2 && t > keys[i + 1][0]) {
         i++;
      }

      float[] from = keys[i];
      float[] to = keys[i + 1];
      float span = to[0] - from[0];
      float u = span <= 0.0F ? 1.0F : (t - from[0]) / span;

      for (int c = 0; c < 16; c++) {
         out[c] = hermite(value(from, c), value(to, c), slope(keys, i, c) * span, slope(keys, i + 1, c) * span, u);
      }

      return out;
   }

   static float[] sampleLoop(float[][] keys, float t, float[] out) {
      t -= (float)Math.floor(t);
      int n = keys.length;
      int i = 0;

      while (i < n - 2 && t > keys[i + 1][0]) {
         i++;
      }

      float[] from = keys[i];
      float[] to = keys[i + 1];
      float span = to[0] - from[0];
      float u = span <= 0.0F ? 1.0F : (t - from[0]) / span;

      for (int c = 0; c < 16; c++) {
         out[c] = hermite(value(from, c), value(to, c), loopSlope(keys, i, c) * span, loopSlope(keys, i + 1, c) * span, u);
      }

      return out;
   }

   private static float loopSlope(float[][] keys, int i, int c) {
      int n = keys.length;
      float drift = value(keys[n - 1], c) - value(keys[0], c);
      float prevT;
      float prevV;
      if (i == 0) {
         prevT = keys[n - 2][0] - 1.0F;
         prevV = value(keys[n - 2], c) - drift;
      } else {
         prevT = keys[i - 1][0];
         prevV = value(keys[i - 1], c);
      }

      float nextT;
      float nextV;
      if (i == n - 1) {
         nextT = keys[1][0] + 1.0F;
         nextV = value(keys[1], c) + drift;
      } else {
         nextT = keys[i + 1][0];
         nextV = value(keys[i + 1], c);
      }

      return (nextV - prevV) / (nextT - prevT);
   }

   private static float value(float[] key, int channel) {
      return channel + 1 < key.length ? key[channel + 1] : 0.0F;
   }

   private static float slope(float[][] keys, int i, int c) {
      if (i != 0 && i != keys.length - 1) {
         float[] prev = keys[i - 1];
         float[] next = keys[i + 1];
         return (value(next, c) - value(prev, c)) / (next[0] - prev[0]);
      } else {
         return 0.0F;
      }
   }

   private static float hermite(float p0, float p1, float m0, float m1, float u) {
      float u2 = u * u;
      float u3 = u2 * u;
      return (2.0F * u3 - 3.0F * u2 + 1.0F) * p0 + (u3 - 2.0F * u2 + u) * m0 + (-2.0F * u3 + 3.0F * u2) * p1 + (u3 - u2) * m1;
   }

   @Environment(EnvType.CLIENT)
   static final class Clip {
      final float[][] keys;
      final float seconds;
      final float loopEnter;
      final float loopExit;
      private final float[][] loop;

      Clip(float[][] keys, float seconds) {
         this(keys, seconds, 0.0F, 0.0F);
      }

      Clip(float[][] keys, float seconds, float loopEnter, float loopExit) {
         this.keys = keys;
         this.seconds = seconds;
         this.loopEnter = loopEnter;
         this.loopExit = loopExit;
         this.loop = loopExit > loopEnter ? cut(keys, loopEnter, loopExit) : null;
      }

      boolean loops() {
         return this.loop != null;
      }

      float[] sample(float t, float[] out) {
         return this.loop != null && t > this.loopEnter && t < this.loopExit
            ? InspectAnimation.sampleLoop(this.loop, (t - this.loopEnter) / (this.loopExit - this.loopEnter), out)
            : InspectAnimation.sample(this.keys, t, out);
      }

      private static float[][] cut(float[][] keys, float enter, float exit) {
         int from = -1;
         int to = -1;

         for (int i = 0; i < keys.length; i++) {
            if (Math.abs(keys[i][0] - enter) < 1.0E-4F) {
               from = i;
            }

            if (Math.abs(keys[i][0] - exit) < 1.0E-4F) {
               to = i;
            }
         }

         if (from >= 0 && to > from) {
            float[][] loop = new float[to - from + 1][];

            for (int i = from; i <= to; i++) {
               loop[i - from] = (float[])keys[i].clone();
               loop[i - from][0] = (keys[i][0] - enter) / (exit - enter);
            }

            return loop;
         } else {
            throw new IllegalArgumentException("a loop starts and ends on keys");
         }
      }
   }
}
