package net.anubis.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class TracerEngine {
   private static final float WIDTH = 1.5F;
   private static boolean active;
   private static Vec3d cameraPos = Vec3d.ZERO;
   private static Vec3d origin = Vec3d.ZERO;
   private static FoglessLines lines;
   private static final Matrix4f viewProjection = new Matrix4f();

   private TracerEngine() {
   }

   public static void beginFrame(Matrix4fc viewRotation, Matrix4fc projection) {
      active = false;
      lines = null;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) {
            Camera camera = mc.gameRenderer.getCamera();
            if (camera != null && camera.isReady()) {
               viewProjection.set(projection).mul(viewRotation);
               Matrix4f inverse = viewProjection.invert(new Matrix4f());
               Vector4f centre = inverse.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
               Vector4f eye = inverse.transform(new Vector4f(0.0F, 0.0F, -1.0F, 0.0F));
               cameraPos = camera.getCameraPos();
               origin = cameraPos.add(centre.x / centre.w, centre.y / centre.w, centre.z / centre.w);
               Vec3d eyePos = cameraPos.add(eye.x / eye.w, eye.y / eye.w, eye.z / eye.w);
               Vector3fc forward = new Vector3f().set((float)(origin.x - eyePos.x), (float)(origin.y - eyePos.y), (float)(origin.z - eyePos.z)).normalize();
               double pixel = 2.0 / (projection.m11() * Math.max(1, mc.getWindow().getFramebufferHeight()));
               lines = FoglessLines.forView(eyePos, forward, pixel);
               active = true;
            }
         }
      }
   }

   public static void draw(Vec3d target, int argb) {
      if (active) {
         lines.line(origin, target, argb, 1.5F);
      }
   }

   public static void stem(Vec3d feet, double height, int argb) {
      if (active) {
         lines.line(feet, feet.add(0.0, height, 0.0), argb, 1.5F);
      }
   }

   public static Vector4f clip(Vec3d world) {
      return !active
         ? null
         : viewProjection.transform(new Vector4f((float)(world.x - cameraPos.x), (float)(world.y - cameraPos.y), (float)(world.z - cameraPos.z), 1.0F));
   }

   public static boolean isInFront(Camera camera, Vec3d target) {
      Vec3d delta = target.subtract(camera.getCameraPos());
      Vector3fc fwd = camera.getHorizontalPlane();
      return delta.x * fwd.x() + delta.y * fwd.y() + delta.z * fwd.z() > 0.05;
   }
}
