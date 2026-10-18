package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class KnifeSkin {
   static final String NONE = "None";
   static final String BUTTERFLY = "Butterfly";
   static final String KARAMBIT = "Karambit";
   static final String KATANA = "Katana";
   static final int MAX_STREAKS = 2;
   private static final Vector3f OFF_FOREARM = new Vector3f(0.4F, -0.85F, 0.35F).normalize();
   private static final Vector3f OFF_AWAY = new Vector3f(-0.3F, -0.75F, 0.1F);
   private static final float WIPE_LIFT = 3.0F;
   private static final float WIPE_TAIL = 16.0F;
   private static final Vector3f SCABBARD_FALL = new Vector3f(-0.3F, -0.9F, 0.25F);
   private static final float SCABBARD_TURN = (float) (Math.PI * 2.0 / 9.0);
   private static final float[][] BUTTERFLY_INSPECT = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.08F, -0.07F, 0.09F, 0.05F, -8.0F, 8.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 8.0F},
      {0.14F, -0.09F, 0.12F, 0.06F, -12.0F, 8.0F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 12.0F},
      {0.19F, -0.09F, 0.12F, 0.06F, -10.0F, 10.0F, 2.0F, -18.0F, 0.0F, 90.0F, 180.0F, 0.0F, 12.0F},
      {0.24F, -0.09F, 0.13F, 0.06F, -12.0F, 8.0F, 10.0F, 0.0F, 0.0F, 180.0F, 360.0F, 0.0F, 12.0F},
      {0.29F, -0.09F, 0.12F, 0.06F, -10.0F, 10.0F, 2.0F, 18.0F, 0.0F, 270.0F, 540.0F, 0.0F, 12.0F},
      {0.34F, -0.09F, 0.12F, 0.06F, -12.0F, 8.0F, 14.0F, 0.0F, 0.0F, 360.0F, 720.0F, 0.0F, 12.0F},
      {0.39F, -0.09F, 0.12F, 0.06F, -10.0F, 10.0F, 2.0F, -18.0F, 0.0F, 450.0F, 900.0F, 0.0F, 12.0F},
      {0.44F, -0.09F, 0.13F, 0.06F, -12.0F, 8.0F, 10.0F, 0.0F, 0.0F, 540.0F, 1080.0F, 0.0F, 12.0F},
      {0.49F, -0.09F, 0.12F, 0.06F, -10.0F, 10.0F, 2.0F, 18.0F, 0.0F, 630.0F, 1260.0F, 0.0F, 12.0F},
      {0.54F, -0.09F, 0.12F, 0.06F, -12.0F, 8.0F, 14.0F, 0.0F, 0.0F, 720.0F, 1440.0F, 0.0F, 12.0F},
      {0.62F, -0.1F, 0.12F, 0.07F, -35.0F, 6.0F, 20.0F, 0.0F, 0.0F, 720.0F, 1440.0F, 0.0F, 0.0F},
      {0.76F, -0.1F, 0.13F, 0.07F, 25.0F, 6.0F, 16.0F, 0.0F, 0.0F, 720.0F, 1440.0F, 1.0F, 0.0F},
      {0.88F, -0.05F, 0.06F, 0.03F, 8.0F, 3.0F, 6.0F, 0.0F, 0.0F, 720.0F, 1440.0F, 1.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 720.0F, 1440.0F, 1.0F, 0.0F}
   };
   private static final float[][] BUTTERFLY_DEPLOY = new float[][]{
      {0.0F, 0.02F, -0.14F, 0.02F, 0.0F, -30.0F, -20.0F, 0.0F, 0.0F, 180.0F, 0.0F, 0.0F, 10.0F},
      {0.3F, 0.01F, -0.04F, 0.01F, 0.0F, -10.0F, -10.0F, 0.0F, 0.0F, 220.0F, 100.0F, 0.0F, 10.0F},
      {0.62F, 0.0F, 0.01F, 0.0F, 0.0F, 4.0F, 6.0F, 0.0F, 0.0F, 350.0F, 340.0F, 0.0F, 3.0F},
      {0.8F, 0.0F, 0.0F, 0.0F, 0.0F, -2.0F, -2.0F, 0.0F, 0.0F, 365.0F, 368.0F, 0.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 360.0F, 360.0F, 0.0F, 0.0F}
   };
   private static final float[][] KARAMBIT_INSPECT = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.1F, -0.12F, 0.14F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, -25.0F, 0.0F, 0.0F},
      {0.14F, -0.14F, 0.16F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
      {0.205F, -0.14F, 0.17F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, 180.0F, 0.0F, 0.0F},
      {0.27F, -0.14F, 0.16F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, 360.0F, 0.0F, 0.0F},
      {0.335F, -0.14F, 0.17F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, 540.0F, 0.0F, 0.0F},
      {0.4F, -0.14F, 0.16F, 0.06F, -10.0F, 14.0F, 10.0F, 0.0F, 0.0F, 720.0F, 0.0F, 0.0F},
      {0.48F, -0.13F, 0.16F, 0.06F, -10.0F, 12.0F, 10.0F, 0.0F, 0.0F, 750.0F, 0.0F, 0.0F},
      {0.56F, -0.1F, 0.12F, 0.06F, 20.0F, 10.0F, -70.0F, 0.0F, 0.0F, 720.0F, 0.0F, 0.0F},
      {0.72F, -0.1F, 0.13F, 0.06F, -10.0F, 10.0F, -60.0F, 0.0F, 0.0F, 720.0F, 0.0F, 1.0F},
      {0.86F, -0.06F, 0.08F, 0.05F, -6.0F, 4.0F, 8.0F, 0.0F, 0.0F, 360.0F, 0.0F, 1.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 360.0F, 0.0F, 1.0F}
   };
   private static final float[][] KARAMBIT_DEPLOY = new float[][]{
      {0.0F, 0.02F, -0.14F, 0.02F, 0.0F, -30.0F, -20.0F, 0.0F, 0.0F, -540.0F, 0.0F, 0.0F},
      {0.55F, 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 0.0F, 0.0F, -60.0F, 0.0F, 0.0F},
      {0.78F, 0.0F, 0.0F, 0.0F, 0.0F, -2.0F, -2.0F, 0.0F, 0.0F, 12.0F, 0.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}
   };
   private static final float[][] KATANA_INSPECT = new float[][]{
      {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.0F},
      {0.1F, -0.06F, 0.12F, 0.06F, 0.0F, 0.0F, 60.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6F, 4.0F},
      {0.15F, -0.07F, 0.14F, 0.08F, -2.0F, 0.0F, 60.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 4.0F},
      {0.2F, -0.07F, 0.14F, 0.08F, -2.0F, 0.0F, 60.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 12.0F},
      {0.36F, -0.07F, 0.14F, 0.08F, 0.0F, 0.0F, 58.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 44.0F},
      {0.4F, -0.07F, 0.14F, 0.08F, 0.0F, 0.0F, 58.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 53.0F},
      {0.44F, -0.06F, 0.13F, 0.06F, 2.0F, 1.0F, 54.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6F, 58.0F},
      {0.48F, -0.05F, 0.11F, 0.03F, 4.0F, 2.0F, 42.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {0.52F, -0.04F, 0.1F, 0.02F, 0.0F, 4.0F, 20.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {0.6F, -0.04F, 0.12F, 0.02F, 0.0F, 4.0F, -160.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {0.68F, -0.04F, 0.1F, 0.02F, 0.0F, 4.0F, -340.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {0.78F, 0.04F, -0.03F, 0.0F, 0.0F, -6.0F, -400.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {0.88F, 0.0F, 0.02F, 0.0F, 0.0F, 2.0F, -365.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -360.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 62.0F}
   };
   private static final float[][] KATANA_DEPLOY = new float[][]{
      {0.0F, 0.06F, 0.0F, 0.02F, -10.0F, 0.0F, 45.0F, 180.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
      {0.05F, 0.05F, 0.01F, 0.01F, -12.0F, 0.0F, 47.0F, 180.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 0.0F},
      {0.18F, 0.15F, 0.0F, 0.05F, -10.0F, 0.0F, 45.0F, 180.0F, 0.0F, 0.0F, 0.0F, 0.3F, 0.0F, 16.0F, 1.0F, 0.0F},
      {0.3F, 0.3F, -0.02F, 0.1F, -8.0F, 0.0F, 43.0F, 180.0F, 0.0F, 0.0F, 0.0F, 0.9F, 0.0F, 50.0F, 1.0F, 0.0F},
      {0.33F, 0.3F, 0.0F, 0.1F, -4.0F, 4.0F, 36.0F, 180.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 52.0F, 1.0F, 0.0F},
      {0.41F, 0.08F, 0.12F, 0.04F, 0.0F, 10.0F, -40.0F, 180.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 52.0F, 0.25F, 0.0F},
      {0.47F, -0.02F, 0.1F, 0.02F, 5.0F, 8.0F, -100.0F, 180.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 52.0F, 0.0F, 0.0F},
      {0.62F, -0.02F, 0.08F, 0.02F, 0.0F, 6.0F, -460.0F, 180.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 52.0F, 0.0F, 0.0F},
      {0.71F, 0.04F, -0.04F, 0.0F, 0.0F, -6.0F, -520.0F, 180.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 52.0F, 0.0F, 0.0F},
      {0.82F, 0.0F, 0.02F, 0.0F, 0.0F, 2.0F, -370.0F, 300.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 52.0F, 0.0F, 0.0F},
      {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -360.0F, 360.0F, 0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 52.0F, 0.0F, 0.0F}
   };
   private static KnifeSkin butterfly;
   private static KnifeSkin karambit;
   private static KnifeSkin katana;
   final String name;
   final InspectAnimation.Clip inspect;
   final InspectAnimation.Clip deploy;
   private final KnifeSkin.Part[] parts;
   private final KnifeSkin.Streak[] streaks;
   private final float voxel;
   private final float gripX;
   private final float gripY;
   private final Vector3f fist;
   private final Vector3f elbow = new Vector3f();
   private final Quaternionf rest;
   private KnifeMesh scabbard;
   private float saddleX;
   private float saddleY;
   private float[] letGo;
   private KnifeSkin.Centre centre;
   private float bladeFrom;
   private float bladeTo;

   private KnifeSkin(
      String name,
      KnifeSkin.Part[] parts,
      KnifeSkin.Streak[] streaks,
      float voxel,
      float gripX,
      float gripY,
      Vector3f fist,
      Vector3f blade,
      Vector3f spine,
      InspectAnimation.Clip inspect,
      InspectAnimation.Clip deploy
   ) {
      this.name = name;
      this.parts = parts;
      this.streaks = streaks;
      this.voxel = voxel;
      this.gripX = gripX;
      this.gripY = gripY;
      this.fist = fist;
      this.inspect = inspect;
      this.deploy = deploy;
      Vector3f x = new Vector3f(blade).normalize();
      Vector3f y = new Vector3f(spine).fma(-spine.dot(x), x).normalize();
      this.rest = new Quaternionf().setFromNormalized(new Matrix3f(x, y, new Vector3f(x).cross(y)));
      fist.fma(0.6F, FirstPersonArm.FOREARM, this.elbow);
   }

   static KnifeSkin of(String name) {
      return switch (name) {
         case "Butterfly" -> butterfly != null ? butterfly : (butterfly = butterfly());
         case "Karambit" -> karambit != null ? karambit : (karambit = karambit());
         case "Katana" -> katana != null ? katana : (katana = katana());
         default -> null;
      };
   }

   private static KnifeSkin butterfly() {
      float pivotX = -1.5F;
      float pivotY = 0.5F;
      KnifeSkin.Part[] parts = new KnifeSkin.Part[]{
         new KnifeSkin.Part(KnifeModels.butterflyHandle(1), -1, pivotX, pivotY),
         new KnifeSkin.Part(KnifeModels.butterflyBlade(), 8, pivotX, pivotY),
         new KnifeSkin.Part(KnifeModels.butterflyHandle(-1), 9, pivotX, pivotY)
      };
      KnifeSkin.Streak[] streaks = new KnifeSkin.Streak[]{
         new KnifeSkin.Streak(1, 2.5F, 0.5F, 21.5F, -0.5F, 1.0F), new KnifeSkin.Streak(2, -4.0F, 0.5F, -27.5F, 0.5F, 0.8F)
      };
      return new KnifeSkin(
         "Butterfly",
         parts,
         streaks,
         0.021F,
         -10.0F,
         0.5F,
         new Vector3f(-0.06F, 0.06F, 0.02F),
         new Vector3f(-0.15F, 1.0F, -0.1F),
         new Vector3f(1.0F, 0.15F, 0.3F),
         new InspectAnimation.Clip(BUTTERFLY_INSPECT, 3.8F, 0.14F, 0.54F),
         new InspectAnimation.Clip(BUTTERFLY_DEPLOY, 0.9F)
      );
   }

   private static KnifeSkin karambit() {
      float ringX = -16.0F;
      float ringY = -1.0F;
      KnifeSkin.Part[] parts = new KnifeSkin.Part[]{new KnifeSkin.Part(KnifeModels.karambit(), 8, ringX, ringY)};
      float[] tip = KnifeModels.KARAMBIT_TIP;
      int k = 2;
      KnifeSkin.Streak[] streaks = new KnifeSkin.Streak[]{new KnifeSkin.Streak(0, 6.0F * k, 0.5F * k, tip[0], tip[1], 1.0F)};
      return new KnifeSkin(
         "Karambit",
         parts,
         streaks,
         0.022F / k,
         ringX + 2.0F,
         ringY,
         new Vector3f(-0.06F, 0.1F, 0.02F),
         new Vector3f(-0.5F, -0.8F, -0.3F),
         new Vector3f(0.8F, -0.5F, 0.35F),
         new InspectAnimation.Clip(KARAMBIT_INSPECT, 3.2F, 0.14F, 0.4F),
         new InspectAnimation.Clip(KARAMBIT_DEPLOY, 0.8F)
      );
   }

   private static KnifeSkin katana() {
      KnifeSkin.Part[] parts = new KnifeSkin.Part[]{new KnifeSkin.Part(KnifeModels.katana(), -1, 0.0F, 0.0F)};
      KnifeSkin.Streak[] streaks = new KnifeSkin.Streak[]{
         new KnifeSkin.Streak(0, 6.0F, KnifeModels.katanaBow(6) + 0.5F, 48.5F, KnifeModels.katanaBow(48) + 1.5F, 1.0F)
      };
      KnifeSkin skin = new KnifeSkin(
         "Katana",
         parts,
         streaks,
         0.028F,
         -5.0F,
         0.5F,
         new Vector3f(-0.06F, 0.02F, 0.02F),
         new Vector3f(-0.6F, 0.8F, -0.2F),
         new Vector3f(0.6F, 0.45F, 0.1F),
         new InspectAnimation.Clip(KATANA_INSPECT, 4.4F, 0.52F, 0.68F),
         new InspectAnimation.Clip(KATANA_DEPLOY, 2.0F)
      );
      skin.scabbard = KnifeModels.katanaScabbard();
      skin.saddleX = 20.0F;
      skin.saddleY = 0.0F;
      skin.centre = x -> x < 3.0F ? 0.5F : KnifeModels.katanaCentre(x);
      skin.bladeFrom = 3.0F;
      skin.bladeTo = 49.0F;
      skin.letGo = skin.findLetGo();
      return skin;
   }

   private float[] findLetGo() {
      float[] at = new float[16];
      float[] held = new float[16];

      for (int i = 0; i <= 2000; i++) {
         this.deploy.sample(i / 2000.0F, at);
         if (at[13] < 1.0F) {
            break;
         }

         System.arraycopy(at, 0, held, 0, 16);
      }

      return held;
   }

   int streakCount() {
      return this.streaks.length;
   }

   float streakStrength(int streak) {
      return this.streaks[streak].strength;
   }

   void draw(MatrixStack pose, float[] at, float weight, boolean slim, float size, KnifeSkin.Sink sink, float[] trail) {
      Matrix4f knife = this.knifeMatrix(at, weight, size, new Matrix4f());
      Vector3f hand = new Vector3f(this.fist).add(at[0] * weight, at[1] * weight, at[2] * weight);
      FirstPersonArm.place(pose, hand, this.elbow, slim, posed -> sink.arm(posed, false));
      Matrix4f sheath = this.scabbard != null && weight >= 0.999F && at[13] > 0.01F ? this.scabbardMatrix(at, size) : null;
      Vector3f other = null;
      if (sheath != null) {
         other = sheath.transformPosition(this.saddleX, this.saddleY, 0.5F, new Vector3f());
      } else if (this.centre != null && at[14] * weight > 0.01F) {
         float t = MathHelper.clamp(at[14] * weight, 0.0F, 1.0F);
         float x = at[15];
         float y = this.centre.y(x) + (x >= this.bladeFrom ? 3.0F : 0.0F);
         other = knife.transformPosition(x, y, 0.5F, new Vector3f()).fma(1.0F - t * t * (3.0F - 2.0F * t), OFF_AWAY);
      }

      if (other != null) {
         pose.push();
         pose.scale(-1.0F, 1.0F, 1.0F);
         Vector3f mirrored = new Vector3f(-other.x, other.y, other.z);
         FirstPersonArm.place(pose, mirrored, new Vector3f(mirrored).fma(0.6F, OFF_FOREARM), slim, posed -> sink.arm(posed, true));
         pose.pop();
      }

      KnifeMesh.Shine wipe = this.wipe(at, weight);
      KnifeMesh.Shine shine = wipe;
      if (shine == null) {
         float sweep = shinePosition(at[10]);
         shine = new KnifeMesh.Shine(sweep, sweep > 0.0F ? MathHelper.sin((float) Math.PI * sweep) * weight : 0.0F, 0.0F);
      }

      pose.push();
      pose.multiplyPositionMatrix(knife);

      for (int i = 0; i < this.parts.length; i++) {
         KnifeSkin.Part part = this.parts[i];
         pose.push();
         if (part.channel >= 0) {
            float angle = MathHelper.wrapDegrees(at[part.channel]) * weight;
            if (angle != 0.0F) {
               pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle), part.pivotX, part.pivotY, 0.5F);
            }
         }

         sink.part(pose, part.mesh, shine);
         if (wipe != null) {
            sink.glow(pose, part.mesh, wipe);
         }

         if (trail != null) {
            this.streaks(i, pose.peek().getPositionMatrix(), trail);
         }

         pose.pop();
      }

      pose.pop();
      if (sheath != null) {
         pose.push();
         pose.multiplyPositionMatrix(sheath);
         sink.part(pose, this.scabbard, KnifeMesh.Shine.NONE);
         pose.pop();
      }
   }

   private void streaks(int part, Matrix4f m, float[] trail) {
      Vector3f p = new Vector3f();

      for (int s = 0; s < this.streaks.length; s++) {
         KnifeSkin.Streak streak = this.streaks[s];
         if (streak.part == part) {
            m.transformPosition(streak.baseX, streak.baseY, 0.5F, p);
            trail[s * 6] = p.x;
            trail[s * 6 + 1] = p.y;
            trail[s * 6 + 2] = p.z;
            m.transformPosition(streak.tipX, streak.tipY, 0.5F, p);
            trail[s * 6 + 3] = p.x;
            trail[s * 6 + 4] = p.y;
            trail[s * 6 + 5] = p.z;
         }
      }
   }

   private Matrix4f knifeMatrix(float[] at, float weight, float size, Matrix4f dest) {
      float turn = (float) (Math.PI / 180.0) * weight;
      float scale = this.voxel * size;
      dest.translation(this.fist.x + at[0] * weight, this.fist.y + at[1] * weight, this.fist.z + at[2] * weight)
         .rotate(
            new Quaternionf()
               .rotateY(MathHelper.wrapDegrees(at[3]) * turn)
               .rotateX(MathHelper.wrapDegrees(at[4]) * turn)
               .rotateZ(MathHelper.wrapDegrees(at[5]) * turn)
         )
         .rotate(this.rest);
      float flip = MathHelper.wrapDegrees(at[6]) * turn;
      if (flip != 0.0F) {
         dest.rotateX(flip);
      }

      return dest.scale(scale).translate(at[11] * weight - this.gripX, -this.gripY, -0.5F);
   }

   private Matrix4f scabbardMatrix(float[] at, float size) {
      boolean held = at[13] >= 1.0F;
      float[] state = held ? at : this.letGo;
      float radius = 312.5F;
      Matrix4f m = this.knifeMatrix(state, 1.0F, size, new Matrix4f())
         .rotateAround(new Quaternionf().rotationZ(state[12] / radius), 3.0F, radius, 0.5F)
         .translate(0.05F, 0.0F, 0.0F);
      if (!held) {
         float fall = 1.0F - Math.max(at[13], 0.0F);
         fall *= fall;
         Vector3f hold = m.transformPosition(this.saddleX, this.saddleY, 0.5F, new Vector3f());
         m.translateLocal(-hold.x, -hold.y, -hold.z)
            .rotateLocalZ((float) (Math.PI * 2.0 / 9.0) * fall)
            .translateLocal(hold.x + SCABBARD_FALL.x * fall, hold.y + SCABBARD_FALL.y * fall, hold.z + SCABBARD_FALL.z * fall);
      }

      return m;
   }

   private KnifeMesh.Shine wipe(float[] at, float weight) {
      float touch = this.centre != null ? MathHelper.clamp((at[14] * weight - 0.7F) / 0.3F, 0.0F, 1.0F) : 0.0F;
      if (!(touch <= 0.01F) && !(at[15] < this.bladeFrom)) {
         float along = (at[15] - this.bladeFrom) / (this.bladeTo - this.bladeFrom);
         return new KnifeMesh.Shine(along, touch * touch * (3.0F - 2.0F * touch), 16.0F);
      } else {
         return null;
      }
   }

   float sparkle(float[] at, float weight) {
      float flash = sparkle(at[10]);
      if (this.centre != null && at[15] >= this.bladeFrom) {
         float along = (at[15] - this.bladeFrom) / (this.bladeTo - this.bladeFrom);
         float leaving = MathHelper.clamp((along - 0.96F) / 0.06F, 0.0F, 1.0F) * MathHelper.clamp((1.3F - along) / 0.15F, 0.0F, 1.0F);
         flash = Math.max(flash, leaving * MathHelper.clamp(at[14] * 1.6F, 0.0F, 1.0F));
      }

      return flash * weight;
   }

   static float shinePosition(float channel) {
      if (channel <= 0.0F) {
         return 0.0F;
      }

      float f = channel - 2.0F * MathHelper.floor(channel / 2.0F);
      return f < 1.0F ? f : 0.0F;
   }

   static float sparkle(float channel) {
      float f = shinePosition(channel);
      return MathHelper.clamp((f - 0.72F) / 0.16F, 0.0F, 1.0F) * MathHelper.clamp((1.0F - f) / 0.07F, 0.0F, 1.0F);
   }

   @Environment(EnvType.CLIENT)
   private interface Centre {
      float y(float var1);
   }

   @Environment(EnvType.CLIENT)
   private record Part(KnifeMesh mesh, int channel, float pivotX, float pivotY) {
   }

   @Environment(EnvType.CLIENT)
   interface Sink {
      void part(MatrixStack var1, KnifeMesh var2, KnifeMesh.Shine var3);

      void glow(MatrixStack var1, KnifeMesh var2, KnifeMesh.Shine var3);

      void arm(MatrixStack var1, boolean var2);
   }

   @Environment(EnvType.CLIENT)
   record Streak(int part, float baseX, float baseY, float tipX, float tipY, float strength) {
   }
}
