package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
final class SkyMesh {
   static final RenderPipeline GLOW = pipeline("sky_glow", BlendFunction.LIGHTNING);
   static final RenderPipeline PAINT = pipeline("sky_paint", null);
   private static final int VERTEX_SIZE = VertexFormats.POSITION_COLOR.getVertexSize();
   private final String label;
   private final RenderPipeline pipeline;
   private final int maxQuads;
   private BufferAllocator bytes;
   private GpuBuffer buffer;
   private int indexCount;
   private boolean built;

   SkyMesh(String label, RenderPipeline pipeline, int maxQuads) {
      this.label = label;
      this.pipeline = pipeline;
      this.maxQuads = maxQuads;
   }

   private static RenderPipeline pipeline(String name, BlendFunction blend) {
      Builder builder = RenderPipeline.builder(new Snippet[]{RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET})
         .withLocation(Identifier.of("anubis", "pipeline/" + name))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS);
      return (blend == null ? builder.withoutBlend() : builder.withBlend(blend)).build();
   }

   BufferBuilder begin() {
      if (this.bytes == null) {
         this.bytes = new BufferAllocator(this.maxQuads * 4 * VERTEX_SIZE);
      }

      return new BufferBuilder(this.bytes, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
   }

   void upload(BufferBuilder builder) {
      this.built = true;
      BuiltBuffer mesh = builder.endNullable();

      label47: {
         try {
            if (mesh == null) {
               this.indexCount = 0;
               break label47;
            }

            if (this.buffer == null) {
               this.buffer = RenderSystem.getDevice().createBuffer(() -> this.label, 40, this.maxQuads * 4L * VERTEX_SIZE);
            }

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(0L, mesh.getBuffer().remaining()), mesh.getBuffer());
            this.indexCount = mesh.getDrawParameters().indexCount();
         } catch (Throwable var6) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (mesh != null) {
            mesh.close();
         }

         return;
      }

      if (mesh != null) {
         mesh.close();
      }
   }

   boolean isBuilt() {
      return this.built;
   }

   void draw(MatrixStack pose, float alpha) {
      if (this.indexCount != 0 && !(alpha <= 0.002F)) {
         ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
         Matrix4fStack modelView = RenderSystem.getModelViewStack();
         modelView.pushMatrix();

         try {
            modelView.mul(pose.peek().getPositionMatrix());
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
               .write(modelView, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
            Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
            RenderPass pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> this.label, target.getColorAttachmentView(), OptionalInt.empty(), target.getDepthAttachmentView(), OptionalDouble.empty());

            try {
               pass.setPipeline(this.pipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("DynamicTransforms", transforms);
               pass.setVertexBuffer(0, this.buffer);
               pass.setIndexBuffer(indices.getIndexBuffer(this.indexCount), indices.getIndexType());
               pass.drawIndexed(0, 0, this.indexCount, 1);
            } catch (Throwable var15) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var14) {
                     var15.addSuppressed(var14);
                  }
               }

               throw var15;
            }

            if (pass != null) {
               pass.close();
            }
         } finally {
            modelView.popMatrix();
         }
      }
   }

   void close() {
      if (this.buffer != null) {
         this.buffer.close();
      }

      if (this.bytes != null) {
         this.bytes.close();
      }

      this.buffer = null;
      this.bytes = null;
      this.indexCount = 0;
      this.built = false;
   }

   static void vertex(BufferBuilder builder, float x, float y, float z, int argb) {
      builder.vertex(x, y, z).color(argb);
   }
}
