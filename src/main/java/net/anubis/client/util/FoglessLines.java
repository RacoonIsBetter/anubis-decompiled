package net.anubis.client.util;

import com.example.client.mixin.AccessorGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class FoglessLines {
   private static final double NEAR = 0.05;
   private final Vec3d eye;
   private final Vector3fc forward;
   private final double pixelPerDepth;

   private FoglessLines(Vec3d eye, Vector3fc forward, double pixelPerDepth) {
      this.eye = eye;
      this.forward = forward;
      this.pixelPerDepth = pixelPerDepth;
   }

   public static FoglessLines forFrame(float partialTick) {
      Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
      return new FoglessLines(camera.getCameraPos(), camera.getHorizontalPlane(), pixelScale(partialTick));
   }

   public static double pixelScale(float partialTick) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Camera camera = mc.gameRenderer.getCamera();
      float fov = ((AccessorGameRenderer)mc.gameRenderer).anubis$getFov(camera, partialTick, true);
      return 2.0 * Math.tan(Math.toRadians(fov) * 0.5) / Math.max(1, mc.getWindow().getFramebufferHeight());
   }

   public static FoglessLines forView(Vec3d eye, Vector3fc forward, double pixelPerDepth) {
      return new FoglessLines(eye, forward, pixelPerDepth);
   }

   public void line(Vec3d from, Vec3d to, int argb, float width) {
      this.line(from.x, from.y, from.z, to.x, to.y, to.z, argb, width);
   }

   public void line(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      double depthFrom = this.depth(fromX, fromY, fromZ);
      double depthTo = this.depth(toX, toY, toZ);
      if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
         if (depthFrom < 0.05) {
            double t = (0.05 - depthFrom) / (depthTo - depthFrom);
            fromX += (toX - fromX) * t;
            fromY += (toY - fromY) * t;
            fromZ += (toZ - fromZ) * t;
            depthFrom = 0.05;
         } else if (depthTo < 0.05) {
            double t = (0.05 - depthTo) / (depthFrom - depthTo);
            toX += (fromX - toX) * t;
            toY += (fromY - toY) * t;
            toZ += (fromZ - toZ) * t;
            depthTo = 0.05;
         }

         double ax = fromX - this.eye.x;
         double ay = fromY - this.eye.y;
         double az = fromZ - this.eye.z;
         double bx = toX - fromX;
         double by = toY - fromY;
         double bz = toZ - fromZ;
         double sx = ay * bz - az * by;
         double sy = az * bx - ax * bz;
         double sz = ax * by - ay * bx;
         double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(length < 1.0E-9)) {
            double scale = width * 0.5 * this.pixelPerDepth / length;
            double kf = scale * depthFrom;
            double kt = scale * depthTo;
            WorldQuads.layer(true)
               .quad(
                  fromX + sx * kf,
                  fromY + sy * kf,
                  fromZ + sz * kf,
                  toX + sx * kt,
                  toY + sy * kt,
                  toZ + sz * kt,
                  toX - sx * kt,
                  toY - sy * kt,
                  toZ - sz * kt,
                  fromX - sx * kf,
                  fromY - sy * kf,
                  fromZ - sz * kf,
                  argb
               );
         }
      }
   }

   public void box(Box box, int argb, float width) {
      double x0 = box.minX;
      double y0 = box.minY;
      double z0 = box.minZ;
      double x1 = box.maxX;
      double y1 = box.maxY;
      double z1 = box.maxZ;

      for (double y : new double[]{y0, y1}) {
         this.line(new Vec3d(x0, y, z0), new Vec3d(x1, y, z0), argb, width);
         this.line(new Vec3d(x1, y, z0), new Vec3d(x1, y, z1), argb, width);
         this.line(new Vec3d(x1, y, z1), new Vec3d(x0, y, z1), argb, width);
         this.line(new Vec3d(x0, y, z1), new Vec3d(x0, y, z0), argb, width);
      }

      this.line(new Vec3d(x0, y0, z0), new Vec3d(x0, y1, z0), argb, width);
      this.line(new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z0), argb, width);
      this.line(new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), argb, width);
      this.line(new Vec3d(x0, y0, z1), new Vec3d(x0, y1, z1), argb, width);
   }

   private double depth(double x, double y, double z) {
      return (x - this.eye.x) * this.forward.x() + (y - this.eye.y) * this.forward.y() + (z - this.eye.z) * this.forward.z();
   }
}
