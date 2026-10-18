package net.anubis.client.util;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.systems.ProjectionType;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.gizmo.GizmoDrawerImpl;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoCollectorImpl;
import net.minecraft.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import net.minecraft.world.debug.gizmo.GizmoCollectorImpl.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public final class ShaderOverlay {
   private static final MethodHandle SHADER_PACK_IN_USE = irisProbe();
   private static final GizmoCollectorImpl COLLECTOR = new GizmoCollectorImpl();
   private static RawProjectionMatrix projectionBuffer;

   private ShaderOverlay() {
   }

   private static MethodHandle irisProbe() {
      if (!FabricLoader.getInstance().isModLoaded("iris")) {
         return null;
      }

      try {
         Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
         Object instance = api.getMethod("getInstance").invoke(null);
         return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return null;
      }
   }

   public static boolean shaderPackInUse() {
      if (SHADER_PACK_IN_USE == null) {
         return false;
      }

      try {
         return (boolean)SHADER_PACK_IN_USE.invokeExact();
      } catch (Throwable t) {
         return false;
      }
   }

   public static CollectorScope capture() {
      if (!shaderPackInUse()) {
         return null;
      }

      if (pending()) {
         COLLECTOR.extractGizmos();
      }

      return GizmoDrawing.using(COLLECTOR);
   }

   public static boolean pending() {
      return !COLLECTOR.getGizmos().isEmpty();
   }

   public static void render(Camera camera, Matrix4f viewRotation, Matrix4f projection, GpuBufferSlice fog, GpuBufferSlice onTopFog) {
      if (pending()) {
         GizmoDrawerImpl standard = new GizmoDrawerImpl();
         GizmoDrawerImpl onTop = new GizmoDrawerImpl();
         long now = Util.getMeasuringTimeMs();

         for (Entry instance : COLLECTOR.extractGizmos()) {
            instance.getGizmo().draw(instance.ignoresOcclusion() ? onTop : standard, instance.getOpacity(now));
         }

         if (!standard.isEmpty() || !onTop.isEmpty()) {
            CameraRenderState state = new CameraRenderState();
            state.initialized = camera.isReady();
            state.pos = camera.getCameraPos();
            state.blockPos = camera.getBlockPos();
            state.orientation = new Quaternionf(camera.getRotation());
            MinecraftClient mc = MinecraftClient.getInstance();
            if (projectionBuffer == null) {
               projectionBuffer = new RawProjectionMatrix("anubis overlay");
            }

            Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
            MatrixStack poseStack = new MatrixStack();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            GpuBufferSlice previousFog = RenderSystem.getShaderFog();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projectionBuffer.set(projection), ProjectionType.PERSPECTIVE);
            modelView.pushMatrix();
            modelView.mul(viewRotation);

            try {
               if (!standard.isEmpty()) {
                  RenderSystem.setShaderFog(fog);
                  standard.draw(poseStack, buffers, state, viewRotation);
                  buffers.draw();
               }

               if (!onTop.isEmpty()) {
                  RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mc.getFramebuffer().getDepthAttachment(), 1.0);
                  RenderSystem.setShaderFog(onTopFog);
                  onTop.draw(poseStack, buffers, state, viewRotation);
                  buffers.draw();
               }
            } finally {
               modelView.popMatrix();
               RenderSystem.restoreProjectionMatrix();
               RenderSystem.setShaderFog(previousFog);
            }
         }
      }
   }
}
