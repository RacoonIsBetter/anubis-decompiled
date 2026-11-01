package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class ColorGradingPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.of("anubis", "pipeline/color_grading"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.of("anubis", "core/color_grading"))
      .withSampler("InSampler")
      .withUniform("ColorGradingConfig", UniformType.UNIFORM_BUFFER)
      .withoutBlend()
      .withColorWrite(true, false)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withVertexFormat(VertexFormats.EMPTY, DrawMode.TRIANGLES)
      .build();
   private static final Supplier<String> PASS_LABEL = () -> "Anubis color grading";
   private static final Supplier<String> CONFIG_LABEL = () -> "Anubis color grading config";
   private static final int CONFIG_SIZE = 32;
   private static SimpleFramebuffer copy;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static boolean invalidShaderLogged;

   private ColorGradingPass() {
   }

   static void apply(boolean worldRendered, float vibrance, float contrast, float exposure, float warmth, float vignette) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Framebuffer main = mc.getFramebuffer();
      GpuTexture mainColor = main.getColorAttachment();
      if (worldRendered
         && mc.world != null
         && mc.isFinishedLoading()
         && !mc.gameRenderer.isRenderingPanorama()
         && mainColor != null
         && main.textureWidth > 0
         && main.textureHeight > 0) {
         if (copy == null) {
            allocate(main.textureWidth, main.textureHeight);
         } else if (copy.textureWidth != main.textureWidth || copy.textureHeight != main.textureHeight) {
            copy.resize(main.textureWidth, main.textureHeight);
         }

         GpuDevice device = RenderSystem.getDevice();
         if (shaderReady(device)) {
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(mainColor, copy.getColorAttachment(), 0, 0, 0, 0, 0, main.textureWidth, main.textureHeight);
            configData.putFloat(0, vibrance);
            configData.putFloat(4, contrast);
            configData.putFloat(8, exposure);
            configData.putFloat(12, warmth);
            configData.putFloat(16, vignette);
            encoder.writeToBuffer(configSlice, configData);

            try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorAttachmentView(), OptionalInt.empty())) {
               pass.setPipeline(PIPELINE);
               pass.setUniform("ColorGradingConfig", configSlice);
               pass.bindTexture("InSampler", copy.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
               pass.draw(0, 3);
            }
         }
      }
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(PIPELINE).isValid()) {
         invalidShaderLogged = false;
         return true;
      }

      if (!invalidShaderLogged) {
         invalidShaderLogged = true;
         LOGGER.warn("Color Grading shader {} did not compile; the picture is left as it is", PIPELINE.getFragmentShader());
      }

      return false;
   }

   private static void allocate(int width, int height) {
      copy = new SimpleFramebuffer("Anubis color grading copy", width, height, false);
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 32L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(32);
   }

   static void release() {
      if (copy != null) {
         copy.delete();
         copy = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      configSlice = null;
      configData = null;
   }
}
