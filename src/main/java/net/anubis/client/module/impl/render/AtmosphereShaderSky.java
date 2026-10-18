package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class AtmosphereShaderSky {
   static final String OFF = "Off";
   static final String SPACE = "Space";
   static final String NORTHERN_LIGHTS = "Northern Lights";
   static final String RINGED_PLANET = "Ringed Planet";
   static final String BLACK_HOLE = "Black Hole";
   private static final List<String> THEMES = List.of("Space", "Northern Lights", "Ringed Planet", "Black Hole");
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final RenderPipeline PIPELINE = skyPipeline(-1);
   private static final RenderPipeline[] MOBILE_PIPELINES = new RenderPipeline[]{skyPipeline(0), skyPipeline(1), skyPipeline(2), skyPipeline(3)};
   private static final RenderPipeline COMPOSITE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.of("anubis", "pipeline/sky_theme_composite"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.of("anubis", "core/sky_theme_composite"))
      .withSampler("InSampler")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(VertexFormats.EMPTY, DrawMode.TRIANGLES)
      .build();
   private static final int UNIFORM_SIZE = 112;
   private static final float CUBE = 10.0F;
   private static final int CUBE_INDICES = 36;
   private static final float MAX_STEP = 0.1F;
   private static final float TIME_WRAP = 4096.0F;
   private GpuBuffer cube;
   private GpuBuffer uniforms;
   private ByteBuffer uniformData;
   private SimpleFramebuffer mobileTarget;
   private boolean mobileTargetFailed;
   private boolean invalidLogged;
   private float time;
   private long lastNanos;

   private static RenderPipeline skyPipeline(int mobileMode) {
      boolean offscreen = mobileMode >= 0;
      Builder builder = RenderPipeline.builder(new Snippet[]{RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET})
         .withLocation(Identifier.of("anubis", offscreen ? "pipeline/sky_theme_mobile_" + mobileMode : "pipeline/sky_theme"))
         .withVertexShader(Identifier.of("anubis", "core/sky_theme"))
         .withFragmentShader(Identifier.of("anubis", "core/sky_theme"))
         .withUniform("AnubisSky", UniformType.UNIFORM_BUFFER)
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS);
      if (offscreen) {
         builder.withShaderDefine("ANUBIS_SKY_MODE", mobileMode);
      }

      return (offscreen ? builder.withoutBlend().withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) : builder.withBlend(BlendFunction.TRANSLUCENT)).build();
   }

   boolean render(
      String theme,
      float visibility,
      float brightness,
      float speed,
      float stars,
      float effect,
      float band,
      int low,
      int high,
      int bandColor,
      int zenith,
      int horizon
   ) {
      int mode = THEMES.indexOf(theme);
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : Math.min((float)(now - this.lastNanos) / 1.0E9F, 0.1F);
      this.lastNanos = now;
      this.time = (this.time + dt * speed) % 4096.0F;
      if (mode >= 0 && !(visibility <= 0.002F)) {
         GpuDevice device = RenderSystem.getDevice();
         boolean mobile = AtmosphereQuality.mobile();
         if (mobile && this.mobileTargetFailed) {
            return false;
         }

         RenderPipeline pipeline = mobile ? MOBILE_PIPELINES[mode] : PIPELINE;
         if (device.precompilePipeline(pipeline).isValid() && (!mobile || device.precompilePipeline(COMPOSITE).isValid())) {
            Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
            if (target.textureWidth > 0 && target.textureHeight > 0) {
               int width = mobile ? AtmosphereQuality.skyWidth(target.textureWidth, target.textureHeight) : target.textureWidth;
               int height = mobile ? AtmosphereQuality.skyHeight(target.textureWidth, target.textureHeight) : target.textureHeight;
               if (this.cube == null) {
                  this.cube = buildCube(device);
               }

               if (this.uniforms == null) {
                  this.uniforms = device.createBuffer(() -> "Anubis sky theme settings", 136, 112L);
                  this.uniformData = BufferUtils.createByteBuffer(112);
               }

               this.putVec(0, mode, visibility, this.time, brightness);
               this.putVec(16, stars, effect, band, (float)width / target.textureWidth);
               this.putColor(32, low);
               this.putColor(48, high);
               this.putColor(64, bandColor);
               this.putColor(80, zenith);
               this.putColor(96, horizon);
               CommandEncoder encoder = device.createCommandEncoder();
               encoder.writeToBuffer(this.uniforms.slice(), this.uniformData);
               GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                  .write(RenderSystem.getModelViewStack(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
               ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
               if (!mobile) {
                  RenderPass pass = encoder.createRenderPass(
                     () -> "Anubis sky theme", target.getColorAttachmentView(), OptionalInt.empty(), target.getDepthAttachmentView(), OptionalDouble.empty()
                  );

                  try {
                     this.drawSky(pass, pipeline, transforms, indices);
                  } catch (Throwable var32) {
                     if (pass != null) {
                        try {
                           pass.close();
                        } catch (Throwable var29) {
                           var32.addSuppressed(var29);
                        }
                     }

                     throw var32;
                  }

                  if (pass != null) {
                     pass.close();
                  }

                  return true;
               } else {
                  try {
                     if (this.mobileTarget == null) {
                        this.mobileTarget = new SimpleFramebuffer("Anubis mobile sky", width, height, false);
                     } else if (this.mobileTarget.textureWidth != width || this.mobileTarget.textureHeight != height) {
                        this.mobileTarget.resize(width, height);
                     }

                     RenderPass pass = encoder.createRenderPass(() -> "Anubis mobile sky", this.mobileTarget.getColorAttachmentView(), OptionalInt.of(0));

                     try {
                        this.drawSky(pass, pipeline, transforms, indices);
                     } catch (Throwable var34) {
                        if (pass != null) {
                           try {
                              pass.close();
                           } catch (Throwable var31) {
                              var34.addSuppressed(var31);
                           }
                        }

                        throw var34;
                     }

                     if (pass != null) {
                        pass.close();
                     }

                     pass = encoder.createRenderPass(() -> "Anubis mobile sky composite", target.getColorAttachmentView(), OptionalInt.empty());

                     try {
                        pass.setPipeline(COMPOSITE);
                        pass.bindTexture("InSampler", this.mobileTarget.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                        pass.draw(0, 3);
                     } catch (Throwable var33) {
                        if (pass != null) {
                           try {
                              pass.close();
                           } catch (Throwable var30) {
                              var33.addSuppressed(var30);
                           }
                        }

                        throw var33;
                     }

                     if (pass != null) {
                        pass.close();
                     }

                     return true;
                  } catch (RuntimeException failure) {
                     this.mobileTargetFailed = true;
                     this.releaseMobileTarget();
                     LOGGER.warn("[Anubis] Mobile sky target unavailable; using the gradient sky", failure);
                     return false;
                  }
               }
            } else {
               return false;
            }
         } else {
            if (!this.invalidLogged) {
               this.invalidLogged = true;
               LOGGER.warn("[Anubis] Atmosphere sky shader {} did not compile; using the gradient sky", pipeline.getFragmentShader());
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private void drawSky(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms, ShapeIndexBuffer indices) {
      pass.setPipeline(pipeline);
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", transforms);
      pass.setUniform("AnubisSky", this.uniforms.slice());
      pass.setVertexBuffer(0, this.cube);
      pass.setIndexBuffer(indices.getIndexBuffer(36), indices.getIndexType());
      pass.drawIndexed(0, 0, 36, 1);
   }

   void close() {
      this.releaseMobileTarget();
      if (this.cube != null) {
         this.cube.close();
      }

      if (this.uniforms != null) {
         this.uniforms.close();
      }

      this.cube = null;
      this.uniforms = null;
      this.uniformData = null;
      this.lastNanos = 0L;
      this.mobileTargetFailed = false;
      this.invalidLogged = false;
   }

   private void releaseMobileTarget() {
      if (this.mobileTarget != null) {
         this.mobileTarget.delete();
         this.mobileTarget = null;
      }
   }

   private void putVec(int offset, float x, float y, float z, float w) {
      this.uniformData.putFloat(offset, x);
      this.uniformData.putFloat(offset + 4, y);
      this.uniformData.putFloat(offset + 8, z);
      this.uniformData.putFloat(offset + 12, w);
   }

   private void putColor(int offset, int rgb) {
      this.putVec(offset, (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
   }

   private static GpuBuffer buildCube(GpuDevice device) {
      BufferAllocator bytes = new BufferAllocator(24 * VertexFormats.POSITION.getVertexSize());

      GpuBuffer var5;
      try {
         BufferBuilder builder = new BufferBuilder(bytes, DrawMode.QUADS, VertexFormats.POSITION);
         float s = 10.0F;
         face(builder, s, -s, -s, s, s, -s, s, s, s, s, -s, s);
         face(builder, -s, -s, s, -s, s, s, -s, s, -s, -s, -s, -s);
         face(builder, -s, s, -s, -s, s, s, s, s, s, s, s, -s);
         face(builder, -s, -s, s, -s, -s, -s, s, -s, -s, s, -s, s);
         face(builder, s, -s, s, s, s, s, -s, s, s, -s, -s, s);
         face(builder, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s);
         BuiltBuffer mesh = builder.end();

         try {
            var5 = device.createBuffer(() -> "Anubis sky theme cube", 32, mesh.getBuffer());
         } catch (Throwable var9) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var10) {
         try {
            bytes.close();
         } catch (Throwable var7) {
            var10.addSuppressed(var7);
         }

         throw var10;
      }

      bytes.close();
      return var5;
   }

   private static void face(
      BufferBuilder builder, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz
   ) {
      builder.vertex(ax, ay, az);
      builder.vertex(bx, by, bz);
      builder.vertex(cx, cy, cz);
      builder.vertex(dx, dy, dz);
   }
}
