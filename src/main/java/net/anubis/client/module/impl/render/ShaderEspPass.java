package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.tick.TickManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class ShaderEspPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.of("anubis", "pipeline/glow_esp"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.of("anubis", "core/glow_esp"))
      .withSampler("SilhouetteSampler")
      .withSampler("HaloSampler")
      .withUniform("GlowEspConfig", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
      .withColorWrite(true, false)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withVertexFormat(VertexFormats.EMPTY, DrawMode.TRIANGLES)
      .build();
   private static final Supplier<String> PASS_LABEL = () -> "Anubis glow esp";
   private static final Supplier<String> CONFIG_LABEL = () -> "Anubis glow esp config";
   private static final int CONFIG_SIZE = 32;
   private static final CameraRenderState CAMERA = new CameraRenderState();
   private static final MatrixStack POSE = new MatrixStack();
   private static final Map<Entity, EntityRenderState> KEPT = new IdentityHashMap<>();
   private static ShaderEspPass.Rig rig;
   private static RawProjectionMatrix projectionBuffer;
   private static SimpleFramebuffer mask;
   private static AuraBlur blur;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static long keptFrame = Long.MIN_VALUE;
   private static boolean invalidShaderLogged;

   private ShaderEspPass() {
   }

   static void keep(Entity entity, EntityRenderState state, long frame) {
      if (keptFrame != frame) {
         KEPT.clear();
         keptFrame = frame;
      }

      KEPT.put(entity, state);
   }

   static void dropKept() {
      KEPT.clear();
      keptFrame = Long.MIN_VALUE;
   }

   static boolean paint(Camera camera, Matrix4f viewRotation, Matrix4f projection, Entity[] entities, int[] tints, int count, long frame) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Framebuffer main = mc.getFramebuffer();
      if (count > 0
         && mc.world != null
         && mc.isFinishedLoading()
         && !mc.gameRenderer.isRenderingPanorama()
         && main.getColorAttachment() != null
         && main.textureWidth > 0
         && main.textureHeight > 0) {
         GpuDevice device = RenderSystem.getDevice();
         if (!shaderReady(device)) {
            return false;
         }

         if (mask == null) {
            allocate(main.textureWidth, main.textureHeight);
         } else if (mask.textureWidth != main.textureWidth || mask.textureHeight != main.textureHeight) {
            mask.resize(main.textureWidth, main.textureHeight);
         }

         if (rig == null) {
            rig = new ShaderEspPass.Rig(mc);
         }

         if (projectionBuffer == null) {
            projectionBuffer = new RawProjectionMatrix("anubis glow esp");
         }

         device.createCommandEncoder().clearColorTexture(mask.getColorAttachment(), 0);
         submit(mc, camera, entities, tints, count, frame);
         flush(viewRotation, projection);
         return true;
      } else {
         return false;
      }
   }

   private static void submit(MinecraftClient mc, Camera camera, Entity[] entities, int[] tints, int count, long frame) {
      EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();
      RenderTickCounter delta = mc.getRenderTickCounter();
      TickManager ticks = mc.world.getTickManager();
      Vec3d eye = camera.getCameraPos();
      Entity viewer = camera.getFocusedEntity();
      boolean reuse = keptFrame == frame;
      CAMERA.initialized = camera.isReady();
      CAMERA.pos = eye;
      CAMERA.blockPos = camera.getBlockPos();
      CAMERA.entityPos = viewer == null ? eye : viewer.getLerpedPos(delta.getTickProgress(true));
      CAMERA.orientation.set(camera.getRotation());

      for (int i = 0; i < count; i++) {
         Entity entity = entities[i];
         if (entity != null && !entity.isRemoved()) {
            EntityRenderState state = reuse ? KEPT.get(entity) : null;
            if (state == null) {
               state = dispatcher.getAndUpdateRenderState(entity, delta.getTickProgress(!ticks.shouldSkipTick(entity)));
            }

            silhouetteOnly(state);
            state.outlineColor = tints[i] | 0xFF000000;
            dispatcher.render(state, CAMERA, state.x - eye.x, state.y - eye.y, state.z - eye.z, POSE, rig.storage);
         }
      }
   }

   private static void silhouetteOnly(EntityRenderState state) {
      state.displayName = null;
      state.leashDatas = null;
      state.onFire = false;
      state.shadowPieces.clear();
      if (state instanceof PlayerEntityRenderState avatar) {
         avatar.playerName = null;
      }
   }

   private static void flush(Matrix4f viewRotation, Matrix4f projection) {
      GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      RenderSystem.backupProjectionMatrix();
      RenderSystem.setProjectionMatrix(projectionBuffer.set(projection), ProjectionType.PERSPECTIVE);
      modelView.pushMatrix();
      modelView.mul(viewRotation);
      RenderSystem.outputColorTextureOverride = mask.getColorAttachmentView();
      RenderSystem.outputDepthTextureOverride = null;

      try {
         rig.features.render();
         rig.outlines.draw();
      } finally {
         RenderSystem.outputColorTextureOverride = previousColor;
         RenderSystem.outputDepthTextureOverride = previousDepth;
         modelView.popMatrix();
         RenderSystem.restoreProjectionMatrix();
         rig.storage.clear();
      }

      rig.storage.onNextFrame();
   }

   static void compose(float strokePx, float aura, float reachPx, float fill, float phase, int sheenArgb, float sheen) {
      Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      GpuTextureView halo = blur.spread(encoder, mask.getColorAttachmentView(), mask.textureWidth, mask.textureHeight, Math.max(reachPx, strokePx + 2.0F));
      configData.putFloat(0, strokePx);
      configData.putFloat(4, aura);
      configData.putFloat(8, fill);
      configData.putFloat(12, phase);
      configData.putFloat(16, (sheenArgb >> 16 & 0xFF) / 255.0F);
      configData.putFloat(20, (sheenArgb >> 8 & 0xFF) / 255.0F);
      configData.putFloat(24, (sheenArgb & 0xFF) / 255.0F);
      configData.putFloat(28, sheen);
      encoder.writeToBuffer(configSlice, configData);

      try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorAttachmentView(), OptionalInt.empty())) {
         pass.setPipeline(PIPELINE);
         pass.setUniform("GlowEspConfig", configSlice);
         pass.bindTexture("SilhouetteSampler", mask.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
         pass.bindTexture("HaloSampler", halo, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
         pass.draw(0, 3);
      }
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(PIPELINE).isValid() && AuraBlur.compiled(device)) {
         invalidShaderLogged = false;
         return true;
      }

      if (!invalidShaderLogged) {
         invalidShaderLogged = true;
         LOGGER.warn("Glow ESP shaders did not compile; entities are left without outlines");
      }

      return false;
   }

   private static void allocate(int width, int height) {
      mask = new SimpleFramebuffer("Anubis glow esp mask", width, height, false);
      blur = new AuraBlur("Anubis glow esp aura");
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 32L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(32);
   }

   static void release() {
      dropKept();
      if (rig != null) {
         rig.close();
         rig = null;
      }

      if (mask != null) {
         mask.delete();
         mask = null;
      }

      if (blur != null) {
         blur.release();
         blur = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      if (projectionBuffer != null) {
         projectionBuffer.close();
         projectionBuffer = null;
      }

      configSlice = null;
      configData = null;
   }

   @Environment(EnvType.CLIENT)
   private static final class Drain extends Immediate {
      private final Map<RenderLayer, VertexConsumer> sinks = new IdentityHashMap<>();

      Drain() {
         super(new BufferAllocator(64), new LinkedHashMap<>());
      }

      void close() {
         this.allocator.close();
      }

      @Override
      public VertexConsumer getBuffer(RenderLayer layer) {
         return this.sinks.computeIfAbsent(layer, type -> new ShaderEspPass.Nowhere());
      }

      @Override
      public void drawCurrentLayer() {
      }

      @Override
      public void draw() {
      }

      @Override
      public void draw(RenderLayer layer) {
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Nowhere implements VertexConsumer {
      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         return this;
      }

      @Override
      public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
      }

      @Override
      public void quad(Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
      }

      @Override
      public void quad(Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay) {
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      @Override
      public VertexConsumer color(int argb) {
         return this;
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer light(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      @Override
      public VertexConsumer lineWidth(float width) {
         return this;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Rig {
      final OrderedRenderCommandQueueImpl storage = new OrderedRenderCommandQueueImpl();
      final ShaderEspPass.Drain drain = new ShaderEspPass.Drain();
      final OutlineVertexConsumerProvider outlines;
      final RenderDispatcher features;

      Rig(MinecraftClient mc) {
         this.outlines = mc.getBufferBuilders().getOutlineVertexConsumers();
         this.features = new RenderDispatcher(
            this.storage, mc.getBlockRenderManager(), this.drain, mc.getAtlasManager(), this.outlines, this.drain, mc.textRenderer
         );
      }

      void close() {
         this.features.close();
         this.drain.close();
      }
   }
}
