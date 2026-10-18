package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.BufferUtils;

@Environment(EnvType.CLIENT)
final class AuraBlur {
   private static final RenderPipeline PIPELINE = pipeline(false);
   private static final RenderPipeline MOBILE_PIPELINE = pipeline(true);
   private static final int CONFIG_SIZE = 16;
   private static final float TAPS = 6.0F;
   private final String name;
   private final Supplier<String> passLabel;
   private final boolean approximateHalo;
   private SimpleFramebuffer across;
   private SimpleFramebuffer down;
   private GpuBuffer acrossConfig;
   private GpuBuffer downConfig;
   private GpuBufferSlice acrossSlice;
   private GpuBufferSlice downSlice;
   private ByteBuffer configData;

   private static RenderPipeline pipeline(boolean mobile) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.of("anubis", mobile ? "pipeline/aura_spread_mobile" : "pipeline/aura_spread"))
         .withVertexShader("core/screenquad")
         .withFragmentShader(Identifier.of("anubis", "core/aura_spread"))
         .withSampler("InSampler")
         .withUniform("AuraSpread", UniformType.UNIFORM_BUFFER)
         .withoutBlend()
         .withDepthWrite(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withVertexFormat(VertexFormats.EMPTY, DrawMode.TRIANGLES);
      if (mobile) {
         builder.withShaderDefine("ANUBIS_MOBILE_AURA");
      }

      return builder.build();
   }

   private static RenderPipeline selectedPipeline(GpuDevice device, boolean approximateHalo) {
      return approximateHalo && MobilePerformance.active() && device.precompilePipeline(MOBILE_PIPELINE).isValid() ? MOBILE_PIPELINE : PIPELINE;
   }

   AuraBlur(String name) {
      this(name, true);
   }

   AuraBlur(String name, boolean approximateHalo) {
      this.name = name;
      this.passLabel = () -> name + " spread";
      this.approximateHalo = approximateHalo;
   }

   static boolean compiled(GpuDevice device) {
      return compiled(device, true);
   }

   static boolean compiled(GpuDevice device, boolean approximateHalo) {
      return device.precompilePipeline(selectedPipeline(device, approximateHalo)).isValid();
   }

   GpuTextureView spread(CommandEncoder encoder, GpuTextureView source, int width, int height, float reachPx) {
      int divisor = PerformanceModule.optimizationsEnabled() && reachPx >= 8.0F ? 4 : 2;
      int halfWidth = Math.max(1, (width + divisor - 1) / divisor);
      int halfHeight = Math.max(1, (height + divisor - 1) / divisor);
      if (this.across == null) {
         this.allocate(halfWidth, halfHeight);
      } else if (this.across.textureWidth != halfWidth || this.across.textureHeight != halfHeight) {
         this.across.resize(halfWidth, halfHeight);
         this.down.resize(halfWidth, halfHeight);
      }

      float step = Math.max(0.75F, reachPx / 6.0F);
      RenderPipeline selected = selectedPipeline(RenderSystem.getDevice(), this.approximateHalo);
      this.run(encoder, source, this.across, this.acrossSlice, step / width, 0.0F, selected);
      this.run(encoder, this.across.getColorAttachmentView(), this.down, this.downSlice, 0.0F, step / height, selected);
      return this.down.getColorAttachmentView();
   }

   private void run(CommandEncoder encoder, GpuTextureView from, SimpleFramebuffer into, GpuBufferSlice slice, float du, float dv, RenderPipeline selected) {
      this.configData.putFloat(0, du);
      this.configData.putFloat(4, dv);
      encoder.writeToBuffer(slice, this.configData);
      RenderPass pass = encoder.createRenderPass(this.passLabel, into.getColorAttachmentView(), OptionalInt.empty());

      try {
         pass.setPipeline(selected);
         pass.setUniform("AuraSpread", slice);
         pass.bindTexture("InSampler", from, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
         pass.draw(0, 3);
      } catch (Throwable var12) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }
         }

         throw var12;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private void allocate(int width, int height) {
      GpuDevice device = RenderSystem.getDevice();
      this.across = new SimpleFramebuffer(this.name + " across", width, height, false);
      this.down = new SimpleFramebuffer(this.name + " down", width, height, false);
      this.acrossConfig = device.createBuffer(() -> this.name + " across config", 136, 16L);
      this.downConfig = device.createBuffer(() -> this.name + " down config", 136, 16L);
      this.acrossSlice = this.acrossConfig.slice();
      this.downSlice = this.downConfig.slice();
      this.configData = BufferUtils.createByteBuffer(16);
   }

   void release() {
      if (this.across != null) {
         this.across.delete();
         this.across = null;
      }

      if (this.down != null) {
         this.down.delete();
         this.down = null;
      }

      if (this.acrossConfig != null) {
         this.acrossConfig.close();
         this.acrossConfig = null;
      }

      if (this.downConfig != null) {
         this.downConfig.close();
         this.downConfig = null;
      }

      this.acrossSlice = null;
      this.downSlice = null;
      this.configData = null;
   }
}
