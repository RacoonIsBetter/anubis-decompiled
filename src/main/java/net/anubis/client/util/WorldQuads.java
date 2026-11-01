package net.anubis.client.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class WorldQuads {
   private static final int MAX_QUADS = 64000;
   private static final int MIN_ALPHA = 3;
   private static final RenderPipeline DEPTH_TESTED = pipeline("world_quads", DepthTestFunction.LEQUAL_DEPTH_TEST);
   private static final RenderPipeline ON_TOP = pipeline("world_quads_on_top", DepthTestFunction.NO_DEPTH_TEST);
   private static final WorldQuads TESTED = new WorldQuads(DEPTH_TESTED);
   private static final WorldQuads TOP = new WorldQuads(ON_TOP);
   private static double originX;
   private static double originY;
   private static double originZ;
   private static BufferAllocator bytes;
   private static RawProjectionMatrix projectionBuffer;
   private final RenderPipeline pipeline;
   private float[] corners = new float[3072];
   private int[] colors = new int[256];
   private int quads;

   private WorldQuads(RenderPipeline pipeline) {
      this.pipeline = pipeline;
   }

   private static RenderPipeline pipeline(String name, DepthTestFunction depth) {
      return RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
         .withLocation(Identifier.of("anubis", "pipeline/" + name))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withBlend(BlendFunction.TRANSLUCENT)
         .withDepthWrite(false)
         .withDepthTestFunction(depth)
         .withCull(false)
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
         .build();
   }

   public static WorldQuads layer(boolean onTop) {
      return onTop ? TOP : TESTED;
   }

   public static void beginFrame(Vec3d camera) {
      originX = camera.x;
      originY = camera.y;
      originZ = camera.z;
      TESTED.quads = 0;
      TOP.quads = 0;
   }

   public boolean quad(
      double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz, int argb
   ) {
      if (argb >>> 24 >= 3 && this.reserve(1)) {
         int p = this.quads * 12;
         float[] out = this.corners;
         out[p] = (float)(ax - originX);
         out[p + 1] = (float)(ay - originY);
         out[p + 2] = (float)(az - originZ);
         out[p + 3] = (float)(bx - originX);
         out[p + 4] = (float)(by - originY);
         out[p + 5] = (float)(bz - originZ);
         out[p + 6] = (float)(cx - originX);
         out[p + 7] = (float)(cy - originY);
         out[p + 8] = (float)(cz - originZ);
         out[p + 9] = (float)(dx - originX);
         out[p + 10] = (float)(dy - originY);
         out[p + 11] = (float)(dz - originZ);
         this.colors[this.quads++] = argb;
         return true;
      } else {
         return false;
      }
   }

   public void quads(double[] positions, int[] argb, int count) {
      if (count > 0) {
         int room = Math.min(count, 64000 - this.quads);
         if (room > 0 && this.reserve(room)) {
            float[] out = this.corners;
            int w = this.quads * 12;
            int kept = 0;

            for (int i = 0; i < room; i++) {
               int color = argb[i];
               if (color >>> 24 >= 3) {
                  int r = i * 12;

                  for (int k = 0; k < 12; k += 3) {
                     out[w + k] = (float)(positions[r + k] - originX);
                     out[w + k + 1] = (float)(positions[r + k + 1] - originY);
                     out[w + k + 2] = (float)(positions[r + k + 2] - originZ);
                  }

                  this.colors[this.quads + kept] = color;
                  kept++;
                  w += 12;
               }
            }

            this.quads += kept;
         }
      }
   }

   private boolean reserve(int more) {
      if (this.quads + more <= this.colors.length) {
         return true;
      }

      if (this.quads + more > 64000) {
         return false;
      }

      int grown = Math.min(64000, Math.max(this.colors.length * 2, this.quads + more));
      this.colors = Arrays.copyOf(this.colors, grown);
      this.corners = Arrays.copyOf(this.corners, grown * 12);
      return true;
   }

   public static void render(Matrix4f viewRotation, Matrix4f projection) {
      if (TESTED.quads != 0 || TOP.quads != 0) {
         if (projectionBuffer == null) {
            projectionBuffer = new RawProjectionMatrix("anubis world quads");
         }

         RenderSystem.backupProjectionMatrix();
         RenderSystem.setProjectionMatrix(projectionBuffer.set(projection), ProjectionType.PERSPECTIVE);

         try {
            TESTED.draw(viewRotation);
            TOP.draw(viewRotation);
         } finally {
            RenderSystem.restoreProjectionMatrix();
            TESTED.quads = 0;
            TOP.quads = 0;
         }
      }
   }

   private void draw(Matrix4f viewRotation) {
      if (this.quads != 0) {
         if (bytes == null) {
            bytes = new BufferAllocator(this.quads * 4 * VertexFormats.POSITION_COLOR.getVertexSize());
         }

         BufferBuilder builder = new BufferBuilder(bytes, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
         float[] in = this.corners;
         int i = 0;

         for (int p = 0; i < this.quads; p += 12) {
            int color = this.colors[i];
            builder.vertex(in[p], in[p + 1], in[p + 2]).color(color);
            builder.vertex(in[p + 3], in[p + 4], in[p + 5]).color(color);
            builder.vertex(in[p + 6], in[p + 7], in[p + 8]).color(color);
            builder.vertex(in[p + 9], in[p + 10], in[p + 11]).color(color);
            i++;
         }

         try (BuiltBuffer mesh = builder.endNullable()) {
            if (mesh == null) {
               return;
            }

            GpuBuffer vertices = VertexFormats.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.getBuffer());
            ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            int indexCount = mesh.getDrawParameters().indexCount();
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
               .write(viewRotation, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
            Framebuffer target = MinecraftClient.getInstance().getFramebuffer();

            try (RenderPass pass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                     () -> "Anubis world quads", target.getColorAttachmentView(), OptionalInt.empty(), target.getDepthAttachmentView(), OptionalDouble.empty()
                  )) {
               pass.setPipeline(this.pipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("DynamicTransforms", transforms);
               pass.setVertexBuffer(0, vertices);
               pass.setIndexBuffer(indices.getIndexBuffer(indexCount), indices.getIndexType());
               pass.drawIndexed(0, 0, indexCount, 1);
            }
         }
      }
   }
}
