package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.MipmapStrategy;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class JumpCirclesModule extends ClientModule {
   public static volatile JumpCirclesModule active;
   public static final Identifier LOGO_TEXTURE = Identifier.of("anubis", "textures/misc/jump_circle_logo.png");
   public static final Identifier LOGO_GLOW_TEXTURE = Identifier.of("anubis", "textures/misc/jump_circle_logo_glow.png");
   private static final String COLOR_ACCENT = "Accent";
   private static final String COLOR_CUSTOM = "Custom";
   private static final int MAX_RINGS = 24;
   private static final int SEGMENTS = 64;
   private static final float[] COS = new float[65];
   private static final float[] SIN = new float[65];
   private static final double LIFT = 0.015;
   private static final double MAX_DISTANCE_SQR = 16384.0;
   private static final double JUMP_RISE = 0.2;
   private static final int RISE_TICKS = 6;
   private static final int SWEEP_TICKS = 100;
   private static final float CORE_WHITE = 0.45F;
   private static final float CORE_ALPHA = 0.95F;
   private static final float HALO_ALPHA = 0.55F;
   private static final float FADE_IN_NANOS = 8.0E7F;
   private static final float LOGO_FILL = 0.75F;
   private static final float LOGO_SPAN = 0.9277344F;
   private static final float LOGO_DROP = 0.0126953125F;
   private static final float LOGO_GLOW_SCALE = 1.5F;
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color", "Accent", "Accent", "Custom"));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Custom Color", -1853860));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 1.2F, 0.5F, 3.0F).step(0.1F));
   private final NumberSetting duration = this.addSetting("duration", new NumberSetting("Duration", 1.0F, 0.3F, 3.0F).step(0.1F));
   private final BooleanSetting logo = this.addSetting("logo", new BooleanSetting("Logo", true));
   private final BooleanSetting others = this.addSetting("others", new BooleanSetting("Others", false));
   private final double[] ringX = new double[24];
   private final double[] ringY = new double[24];
   private final double[] ringZ = new double[24];
   private final float[] ringFacingX = new float[24];
   private final float[] ringFacingZ = new float[24];
   private final long[] ringBorn = new long[24];
   private int head;
   private int count;
   private float poseX;
   private float poseY;
   private float poseZ;
   private float poseT;
   private float poseRadius;
   private float poseFade;
   private final Int2ObjectOpenHashMap<JumpCirclesModule.Lift> lifts = new Int2ObjectOpenHashMap();
   private int tick;
   private int levelKey;

   public JumpCirclesModule() {
      super("jump-circles", "Jump Circles", "Draws a fading Anubis ring where you jump.", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> this.colorMode.is("Custom"));
   }

   @Override
   protected void onEnable() {
      this.clear();
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      active = null;
      this.clear();
   }

   private void clear() {
      this.head = 0;
      this.count = 0;
      this.lifts.clear();
      this.levelKey = 0;
   }

   public static void onJump(LivingEntity entity) {
      JumpCirclesModule module = active;
      if (module != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (entity == mc.player && mc.world != null) {
            module.syncLevel(mc.world);
            module.spawn(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw());
         }
      }
   }

   private void spawn(double x, double y, double z, float yaw) {
      if (this.count == 24) {
         this.head = (this.head + 1) % 24;
         this.count--;
      }

      int slot = (this.head + this.count) % 24;
      this.ringX[slot] = x;
      this.ringY[slot] = y;
      this.ringZ[slot] = z;
      double heading = Math.toRadians(yaw);
      this.ringFacingX[slot] = (float)(-Math.sin(heading));
      this.ringFacingZ[slot] = (float)Math.cos(heading);
      this.ringBorn[slot] = System.nanoTime();
      this.count++;
   }

   private void expire(long now) {
      for (long life = this.durationNanos(); this.count > 0 && now - this.ringBorn[this.head] >= life; this.count--) {
         this.head = (this.head + 1) % 24;
      }
   }

   private long durationNanos() {
      return (long)(this.duration.get() * 1.0E9);
   }

   private void syncLevel(ClientWorld level) {
      int key = System.identityHashCode(level);
      if (key != this.levelKey) {
         this.levelKey = key;
         this.head = 0;
         this.count = 0;
         this.lifts.clear();
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level == null) {
         this.clear();
      } else {
         this.syncLevel(level);
         if (!this.others.get()) {
            if (!this.lifts.isEmpty()) {
               this.lifts.clear();
            }
         } else {
            this.tick++;
            List<AbstractClientPlayerEntity> players = level.getPlayers();

            for (int i = 0; i < players.size(); i++) {
               AbstractClientPlayerEntity player = players.get(i);
               if (player != mc.player) {
                  JumpCirclesModule.Lift lift = (JumpCirclesModule.Lift)this.lifts.get(player.getId());
                  if (lift == null) {
                     lift = new JumpCirclesModule.Lift();
                     lift.airTicks = 6;
                     lift.judged = true;
                     this.lifts.put(player.getId(), lift);
                  }

                  lift.seen = this.tick;
                  this.track(player, lift);
               }
            }

            if (this.tick % 100 == 0) {
               int now = this.tick;
               this.lifts.values().removeIf(liftx -> liftx.seen != now);
            }
         }
      }
   }

   private void track(AbstractClientPlayerEntity player, JumpCirclesModule.Lift lift) {
      if (player.isOnGround()) {
         lift.x = player.getX();
         lift.y = player.getY();
         lift.z = player.getZ();
         lift.yaw = player.getYaw();
         lift.airTicks = -1;
         lift.judged = false;
      } else if (!lift.judged) {
         lift.airTicks++;
         if (player.getY() - lift.y > 0.2) {
            lift.judged = true;
            if (canLeaveRing(player)) {
               this.spawn(lift.x, lift.y, lift.z, lift.yaw);
            }
         } else if (lift.airTicks >= 6) {
            lift.judged = true;
         }
      }
   }

   private static boolean canLeaveRing(AbstractClientPlayerEntity player) {
      return !player.hasVehicle()
         && !player.isGliding()
         && !player.isInFluid()
         && !player.isClimbing()
         && player.hurtTime == 0
         && !player.isSpectator()
         && !player.isInvisible();
   }

   public static void renderLevel(CameraRenderState camera) {
      JumpCirclesModule module = active;
      if (module != null && module.count > 0 && camera.initialized) {
         module.render(camera.pos);
      }
   }

   private void render(Vec3d camera) {
      long now = System.nanoTime();
      this.expire(now);
      if (this.count != 0) {
         int argb = this.colorMode.is("Custom") ? this.color.argb() : CosmeticAccent.argb();
         int rgb = argb & 16777215;
         float radiusMax = this.size.get();
         float life = (float)this.durationNanos();
         float core = 0.025F + 0.02F * radiusMax;
         int coreRgb = towardsWhite(rgb, 0.45F);
         Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
         if (this.logo.get()) {
            float alpha = (argb >>> 24) / 255.0F;
            VertexConsumer out = buffers.getBuffer(JumpCirclesModule.Logo.GLOW_TYPE);

            for (int n = 0; n < this.count; n++) {
               int slot = (this.head + n) % 24;
               if (this.pose(slot, now, life, radiusMax, camera)) {
                  this.logo(out, slot, this.poseRadius - core, 1.5F, withAlpha(rgb, alpha * 0.55F * this.poseFade));
               }
            }

            buffers.draw(JumpCirclesModule.Logo.GLOW_TYPE);
            out = buffers.getBuffer(JumpCirclesModule.Logo.TYPE);

            for (int n = 0; n < this.count; n++) {
               int slot = (this.head + n) % 24;
               if (this.pose(slot, now, life, radiusMax, camera)) {
                  this.logo(out, slot, this.poseRadius - core, 1.0F, withAlpha(coreRgb, alpha * 0.95F * this.poseFade));
               }
            }

            buffers.draw(JumpCirclesModule.Logo.TYPE);
         }

         VertexConsumer out = buffers.getBuffer(JumpCirclesModule.Ring.TYPE);

         for (int n = 0; n < this.count; n++) {
            int slot = (this.head + n) % 24;
            if (this.pose(slot, now, life, radiusMax, camera)) {
               float glow = (0.08F + 0.08F * radiusMax) * (1.0F - 0.35F * this.poseT);
               int clear = rgb;
               int halo = withAlpha(rgb, 0.55F * this.poseFade);
               int line = withAlpha(coreRgb, 0.95F * this.poseFade);
               float cx = this.poseX;
               float cy = this.poseY;
               float cz = this.poseZ;
               float radius = this.poseRadius;
               float in = radius - core;
               float outR = radius + core;
               band(out, cx, cy, cz, Math.max(0.0F, in - glow * 0.7F), clear, in, halo);
               band(out, cx, cy, cz, in, halo, radius, line);
               band(out, cx, cy, cz, radius, line, outR, halo);
               band(out, cx, cy, cz, outR, halo, outR + glow, clear);
            }
         }

         buffers.draw(JumpCirclesModule.Ring.TYPE);
      }
   }

   private boolean pose(int slot, long now, float life, float radiusMax, Vec3d camera) {
      float cx = (float)(this.ringX[slot] - camera.x);
      float cy = (float)(this.ringY[slot] + 0.015 - camera.y);
      float cz = (float)(this.ringZ[slot] - camera.z);
      if ((double)cx * cx + (double)cy * cy + (double)cz * cz > 16384.0) {
         return false;
      }

      long age = now - this.ringBorn[slot];
      float t = Math.min(1.0F, (float)age / life);
      float open = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
      float fade = (1.0F - t) * (1.0F - t) * Math.min(1.0F, (float)age / 8.0E7F);
      if (fade <= 0.004F) {
         return false;
      }

      this.poseX = cx;
      this.poseY = cy;
      this.poseZ = cz;
      this.poseT = t;
      this.poseRadius = radiusMax * (0.18F + 0.82F * open);
      this.poseFade = fade;
      return true;
   }

   private void logo(VertexConsumer out, int slot, float inner, float scale, int argb) {
      if (!(inner <= 0.0F)) {
         float half = 0.75F * inner / 0.9277344F;
         float fx = this.ringFacingX[slot];
         float fz = this.ringFacingZ[slot];
         float lead = 0.025390625F * half;
         float x = this.poseX + fx * lead;
         float y = this.poseY;
         float z = this.poseZ + fz * lead;
         half *= scale;
         float ax = (fx + fz) * half;
         float az = (fz - fx) * half;
         float bx = (fx - fz) * half;
         float bz = (fz + fx) * half;
         out.vertex(x + ax, y, z + az).texture(0.0F, 0.0F).color(argb);
         out.vertex(x + bx, y, z + bz).texture(1.0F, 0.0F).color(argb);
         out.vertex(x - ax, y, z - az).texture(1.0F, 1.0F).color(argb);
         out.vertex(x - bx, y, z - bz).texture(0.0F, 1.0F).color(argb);
      }
   }

   private static void band(VertexConsumer out, float x, float y, float z, float r0, int c0, float r1, int c1) {
      int segments = 64;

      for (int i = 0; i < segments; i++) {
         int from = i * 64 / segments;
         int to = (i + 1) * 64 / segments;
         float cosA = COS[from];
         float sinA = SIN[from];
         float cosB = COS[to];
         float sinB = SIN[to];
         out.vertex(x + cosA * r0, y, z + sinA * r0).color(c0);
         out.vertex(x + cosB * r0, y, z + sinB * r0).color(c0);
         out.vertex(x + cosB * r1, y, z + sinB * r1).color(c1);
         out.vertex(x + cosA * r1, y, z + sinA * r1).color(c1);
      }
   }

   private static int withAlpha(int rgb, float alpha) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F + 0.5F);
      return a << 24 | rgb;
   }

   private static int towardsWhite(int rgb, float amount) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      r += (int)((255 - r) * amount);
      g += (int)((255 - g) * amount);
      b += (int)((255 - b) * amount);
      return r << 16 | g << 8 | b;
   }

   static {
      for (int i = 0; i <= 64; i++) {
         double angle = (Math.PI * 2) * i / 64.0;
         COS[i] = (float)Math.cos(angle);
         SIN[i] = (float)Math.sin(angle);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Lift {
      double x;
      double y;
      double z;
      float yaw;
      int airTicks;
      boolean judged;
      int seen;
   }

   @Environment(EnvType.CLIENT)
   private static final class Logo {
      static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
         .withLocation(Identifier.of("anubis", "pipeline/jump_circle_logo"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withSampler("Sampler0")
         .withBlend(BlendFunction.TRANSLUCENT)
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .build();
      static final RenderLayer TYPE = RenderLayer.of(
         "anubis_jump_circle_logo",
         RenderSetup.builder(PIPELINE).texture("Sampler0", JumpCirclesModule.LOGO_TEXTURE).layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).build()
      );
      static final RenderLayer GLOW_TYPE = RenderLayer.of(
         "anubis_jump_circle_logo_glow",
         RenderSetup.builder(PIPELINE)
            .texture("Sampler0", JumpCirclesModule.LOGO_GLOW_TEXTURE)
            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .build()
      );

      static {
         MinecraftClient.getInstance()
            .getTextureManager()
            .registerTexture(JumpCirclesModule.LOGO_TEXTURE, new JumpCirclesModule.LogoTexture(JumpCirclesModule.LOGO_TEXTURE));
         MinecraftClient.getInstance()
            .getTextureManager()
            .registerTexture(JumpCirclesModule.LOGO_GLOW_TEXTURE, new JumpCirclesModule.LogoTexture(JumpCirclesModule.LOGO_GLOW_TEXTURE));
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class LogoTexture extends ResourceTexture {
      LogoTexture(Identifier id) {
         super(id);
      }

      @Override
      protected void load(NativeImage image) {
         int levels = 32 - Integer.numberOfLeadingZeros(Math.min(image.getWidth(), image.getHeight()));
         NativeImage[] mips = MipmapHelper.getMipmapLevelsImages(this.getId(), new NativeImage[]{image}, levels - 1, MipmapStrategy.MEAN, 0.0F);

         try {
            GpuDevice device = RenderSystem.getDevice();
            this.close();
            this.glTexture = device.createTexture(this.getId()::toString, 5, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, levels);
            this.glTextureView = device.createTextureView(this.glTexture);
            CommandEncoder encoder = device.createCommandEncoder();

            for (int level = 0; level < levels; level++) {
               NativeImage mip = mips[level];
               encoder.writeToTexture(this.glTexture, mip, level, 0, 0, 0, mip.getWidth(), mip.getHeight(), 0, 0);
            }
         } finally {
            for (int level = 1; level < mips.length; level++) {
               mips[level].close();
            }
         }

         this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR, true);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Ring {
      static final RenderLayer TYPE = RenderLayer.of(
         "anubis_jump_circles", RenderSetup.builder(RenderPipelines.DEBUG_QUADS).layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).build()
      );
   }
}
