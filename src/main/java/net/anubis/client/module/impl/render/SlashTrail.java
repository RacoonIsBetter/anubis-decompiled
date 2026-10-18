package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack.Entry;

@Environment(EnvType.CLIENT)
final class SlashTrail {
   private static final int CAPACITY = 96;
   private static final long GAP_NANOS = 100000000L;
   private static final float SLOW = 0.6F;
   private static final float FAST = 2.8F;
   private static final float ALPHA = 0.6F;
   private static final float BASE_ALPHA = 0.12F;
   private final float[] points = new float[576];
   private final long[] times = new long[96];
   private int newest = -1;
   private int size;

   void add(long now, float[] sample, int from) {
      this.newest = (this.newest + 1) % 96;
      System.arraycopy(sample, from, this.points, this.newest * 6, 6);
      this.times[this.newest] = now;
      this.size = Math.min(this.size + 1, 96);
   }

   void clear() {
      this.size = 0;
      this.newest = -1;
   }

   boolean isEmpty(long now, long lifetime) {
      return this.size == 0 || now - this.times[this.newest] > lifetime;
   }

   void emit(Entry pose, VertexConsumer out, long now, long lifetime, int rgb, float strength) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;

      for (int k = 0; k + 1 < this.size; k++) {
         int newer = Math.floorMod(this.newest - k, 96);
         int older = Math.floorMod(this.newest - k - 1, 96);
         long ageNewer = now - this.times[newer];
         long ageOlder = now - this.times[older];
         if (ageOlder > lifetime) {
            break;
         }

         long dt = this.times[newer] - this.times[older];
         if (dt > 0L && dt <= 100000000L) {
            int n = newer * 6;
            int o = older * 6;
            float dx = this.points[n + 3] - this.points[o + 3];
            float dy = this.points[n + 4] - this.points[o + 4];
            float dz = this.points[n + 5] - this.points[o + 5];
            float speed = MathHelper.sqrt(dx * dx + dy * dy + dz * dz) / ((float)dt / 1.0E9F);
            float fast = MathHelper.clamp((speed - 0.6F) / 2.1999998F, 0.0F, 1.0F);
            if (!(fast <= 0.0F)) {
               float aNewer = fade(ageNewer, lifetime) * fast * strength;
               float aOlder = fade(ageOlder, lifetime) * fast * strength;
               float[] quad = new float[]{
                  this.points[o],
                  this.points[o + 1],
                  this.points[o + 2],
                  aOlder * 0.12F,
                  this.points[o + 3],
                  this.points[o + 4],
                  this.points[o + 5],
                  aOlder,
                  this.points[n + 3],
                  this.points[n + 4],
                  this.points[n + 5],
                  aNewer,
                  this.points[n],
                  this.points[n + 1],
                  this.points[n + 2],
                  aNewer * 0.12F
               };

               for (int v = 0; v < 4; v++) {
                  vertex(pose, out, quad, v, r, g, b);
               }

               for (int v = 3; v >= 0; v--) {
                  vertex(pose, out, quad, v, r, g, b);
               }
            }
         }
      }
   }

   private static float fade(long age, long lifetime) {
      float left = 1.0F - MathHelper.clamp((float)age / (float)lifetime, 0.0F, 1.0F);
      return left * left * 0.6F;
   }

   private static void vertex(Entry pose, VertexConsumer out, float[] quad, int v, int r, int g, int b) {
      int i = v * 4;
      out.vertex(pose, quad[i], quad[i + 1], quad[i + 2])
         .color(r, g, b, Math.round(MathHelper.clamp(quad[i + 3], 0.0F, 1.0F) * 255.0F))
         .texture(0.5F, 0.5F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(15728880)
         .normal(pose, 0.0F, 0.0F, 1.0F);
   }

   static void sparkle(Entry pose, VertexConsumer out, float x, float y, float z, float size, float turn, int rgb, float alpha) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      int core = Math.round(MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);

      for (int arm = 0; arm < 4; arm++) {
         float angle = turn + arm * (float) (Math.PI / 2);
         float cos = MathHelper.cos(angle);
         float sin = MathHelper.sin(angle);
         float length = size * (arm % 2 == 0 ? 1.0F : 0.7F);
         float width = size * 0.16F;
         float tx = x + cos * length;
         float ty = y + sin * length;
         float lx = x - sin * width;
         float ly = y + cos * width;
         float rx = x + sin * width;
         float ry = y - cos * width;
         star(pose, out, x, y, z, r, g, b, core);
         star(pose, out, lx, ly, z, r, g, b, core / 3);
         star(pose, out, tx, ty, z, r, g, b, 0);
         star(pose, out, rx, ry, z, r, g, b, core / 3);
         star(pose, out, rx, ry, z, r, g, b, core / 3);
         star(pose, out, tx, ty, z, r, g, b, 0);
         star(pose, out, lx, ly, z, r, g, b, core / 3);
         star(pose, out, x, y, z, r, g, b, core);
      }
   }

   private static void star(Entry pose, VertexConsumer out, float x, float y, float z, int r, int g, int b, int a) {
      out.vertex(pose, x, y, z)
         .color(r, g, b, a)
         .texture(0.5F, 0.5F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(15728880)
         .normal(pose, 0.0F, 0.0F, 1.0F);
   }
}
