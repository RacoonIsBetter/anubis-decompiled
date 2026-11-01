package net.anubis.client.module.impl.render;

import java.awt.Color;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.glyph.GlyphCanvas;
import net.anubis.client.util.glyph.GlyphShapes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;

@Environment(EnvType.CLIENT)
public final class FloatingRelicsModule extends ClientModule {
   private static final String COMET = "Comet";
   private static final String FIREBALL = "Fireball";
   private static final String RAINBOW = "Rainbow";
   private static final String NEON = "Neon";
   private static final String ELECTRIC = "Electric";
   private static final String SAKURA = "Sakura";
   private static final String VOID = "Void";
   private static final String AURORA = "Aurora";
   private static final int MAX_RELICS = 32;
   private static final int MAX_HISTORY = 62;
   private static final int SMOOTHING = 3;
   private static final int MAX_POINTS = 188;
   private static final float GRAVITY = 0.016F;
   private static final float BUOYANCY = 0.045F;
   private static final float AIR_DRAG = 0.982F;
   private static final float GROUND_GRIP = 0.86F;
   private static final float ORBIT_PULL = 0.012F;
   private static final float SWIRL = 0.0025F;
   private static final float FOLLOW = 0.035F;
   private static final float WANDER = 0.003F;
   private static final float MAX_SPEED = 0.9F;
   private static final int APPEAR_TICKS = 12;
   private static final int STAGGER_TICKS = 3;
   private static final float[] GLOW_SCALES = new float[]{2.6F, 2.0F, 1.55F, 1.2F, 0.85F, 0.5F};
   private static final float[] GLOW_ALPHAS = new float[]{0.035F, 0.05F, 0.08F, 0.13F, 0.3F, 0.95F};
   private static final float[] PETAL = petal();
   private static final float[] ROUND = round(24);
   private static final float[] SOFT = round(16);
   private static final float SOFT_BELOW = 0.1F;
   private static final long EPOCH = System.nanoTime();
   private final ModeSetting style = this.addSetting(
      "style", new ModeSetting("Style", "Comet", "Comet", "Fireball", "Rainbow", "Neon", "Electric", "Sakura", "Void", "Aurora")
   );
   private final NumberSetting trailLength = this.addSetting("trail-length", new NumberSetting("Trail Length", 1.0F, 0.2F, 3.0F).step(0.1F));
   private final NumberSetting count = this.addSetting("count", new NumberSetting("Count", 6.0F, 1.0F, 32.0F).step(1.0F));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 0.3F, 0.15F, 0.75F).step(0.05F));
   private final NumberSetting spread = this.addSetting("spread", new NumberSetting("Spread", 2.6F, 1.2F, 6.0F).step(0.1F));
   private final NumberSetting bounce = this.addSetting("bounce", new NumberSetting("Bounce", 0.6F, 0.1F, 0.95F).step(0.05F));
   private final NumberSetting drift = this.addSetting("drift", new NumberSetting("Drift", 0.5F, 0.0F, 2.5F).step(0.1F));
   private final BooleanSetting blocks = this.addSetting("block-bounce", new BooleanSetting("Block Bounce", true));
   private final FloatingRelicsModule.Relic[] relics = new FloatingRelicsModule.Relic[32];
   private final GlyphCanvas canvas = new GlyphCanvas();
   private final Random random = Random.create();
   private final Mutable cursor = new Mutable();
   private final double[] points = new double[564];
   private final double[] raw = new double[186];
   private final double[] near = new double[3];
   private final double[] far = new double[3];
   private int live;
   private int age;
   private ClientWorld boundLevel;

   public FloatingRelicsModule() {
      super("floating-relics", "Floating Relics", "Glowing orbs float around you and leave a trail as they move.", ModuleCategory.VISUALS);

      for (int i = 0; i < 32; i++) {
         this.relics[i] = new FloatingRelicsModule.Relic();
      }
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      this.on(Render3DEvent.TYPE, this::onRender3D);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.live = 0;
      this.age = 0;
      this.boundLevel = null;
   }

   private void syncLevel(ClientWorld level) {
      if (level != this.boundLevel) {
         this.boundLevel = level;
         this.live = 0;
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         this.syncLevel(level);
         this.age++;
         int want = Math.min(32, this.count.getInt());

         for (int i = this.live; i < want; i++) {
            this.wake(this.relics[i], (i - this.live) * 3);
         }

         this.live = want;
         double px = player.getX();
         double py = player.getY();
         double pz = player.getZ();
         float height = player.getHeight();
         float moveX = (float)(px - player.lastX);
         float moveZ = (float)(pz - player.lastZ);
         float restitution = this.bounce.get();

         for (int i = 0; i < this.live; i++) {
            this.step(this.relics[i], level, px, py, pz, height, moveX, moveZ, restitution);
         }

         for (int i = 0; i < this.live; i++) {
            for (int j = i + 1; j < this.live; j++) {
               this.knock(this.relics[i], this.relics[j], restitution);
            }
         }

         for (int i = 0; i < this.live; i++) {
            if (this.relics[i].placed) {
               this.relics[i].remember();
            }
         }
      } else {
         this.reset();
      }
   }

   private void wake(FloatingRelicsModule.Relic r, int delay) {
      r.placed = false;
      r.wakeAt = this.age + delay;
      r.orbit = 0.55F + 0.45F * this.random.nextFloat();
      r.lift = this.random.nextFloat();
      r.grain = 0.82F + 0.3F * this.random.nextFloat();
      r.phase = this.random.nextFloat() * (float) (Math.PI * 2);
   }

   private void place(FloatingRelicsModule.Relic r, double px, double py, double pz) {
      float angle = this.random.nextFloat() * (float) (Math.PI * 2);
      double radius = this.spread.get() * r.orbit;
      r.x = px + MathHelper.cos(angle) * radius;
      r.y = py + 0.15;
      r.z = pz + MathHelper.sin(angle) * radius;
      r.lastX = r.x;
      r.lastY = r.y;
      r.lastZ = r.z;
      r.vx = -MathHelper.sin(angle) * 0.03F;
      r.vy = 0.14F + 0.08F * this.random.nextFloat();
      r.vz = MathHelper.cos(angle) * 0.03F;
      r.placed = true;
      r.placedAt = this.age;
      r.head = 0;
      r.filled = 0;
   }

   private void step(
      FloatingRelicsModule.Relic r, ClientWorld level, double px, double py, double pz, float height, float moveX, float moveZ, float restitution
   ) {
      if (!r.placed) {
         if (this.age < r.wakeAt) {
            return;
         }

         this.place(r, px, py, pz);
      }

      r.lastX = r.x;
      r.lastY = r.y;
      r.lastZ = r.z;
      double dx = r.x - px;
      double dz = r.z - pz;
      double flat = Math.sqrt(dx * dx + dz * dz);
      double hover = py + 0.35 + r.lift * (height + 0.2) + 0.16 * MathHelper.sin((this.age + r.phase * 16.0F) * 0.045F);
      double leash = this.spread.get() * 3.0 + 8.0;
      if (!(flat > leash) && !(Math.abs(r.y - hover) > leash)) {
         float nx = flat > 1.0E-4 ? (float)(dx / flat) : 1.0F;
         float nz = flat > 1.0E-4 ? (float)(dz / flat) : 0.0F;
         float pull = (float)(this.spread.get() * r.orbit - flat) * 0.012F;
         float swirl = this.drift.get() * 0.0025F;
         r.vx += nx * pull - nz * swirl + moveX * 0.035F;
         r.vz += nz * pull + nx * swirl + moveZ * 0.035F;
         float wander = (this.age + r.phase * 20.0F) * 0.06F;
         r.vx = r.vx + MathHelper.sin(wander) * 0.003F;
         r.vz = r.vz + MathHelper.cos(wander * 1.31F) * 0.003F;
         r.vy -= 0.016F;
         double sink = hover - r.y;
         if (sink > 0.0) {
            r.vy = r.vy + (float)Math.min(sink, 1.5) * 0.045F;
         }

         r.vx *= 0.982F;
         r.vy *= 0.982F;
         r.vz *= 0.982F;
         float speed = r.vx * r.vx + r.vy * r.vy + r.vz * r.vz;
         if (speed > 0.80999994F) {
            float k = 0.9F / (float)Math.sqrt(speed);
            r.vx *= k;
            r.vy *= k;
            r.vz *= k;
         }

         boolean collide = this.blocks.get();
         float half = this.size.get() * r.grain * 0.5F;
         double next = r.x + r.vx;
         if (collide && this.solid(level, next + Math.copySign(half, r.vx), r.y, r.z)) {
            r.vx = -r.vx * restitution;
         } else {
            r.x = next;
         }

         next = r.y + r.vy;
         if (collide && this.solid(level, r.x, next + Math.copySign(half, r.vy), r.z)) {
            if (r.vy < 0.0F) {
               r.vx *= 0.86F;
               r.vz *= 0.86F;
            }

            r.vy = -r.vy * restitution;
         } else {
            r.y = next;
         }

         next = r.z + r.vz;
         if (collide && this.solid(level, r.x, r.y, next + Math.copySign(half, r.vz))) {
            r.vz = -r.vz * restitution;
         } else {
            r.z = next;
         }

         if (collide && this.solid(level, r.x, r.y, r.z)) {
            r.y += 0.2;
            r.lastY = r.y;
            r.vy = Math.max(r.vy, 0.08F);
         }
      } else {
         this.place(r, px, py, pz);
      }
   }

   private void knock(FloatingRelicsModule.Relic a, FloatingRelicsModule.Relic b, float restitution) {
      if (a.placed && b.placed) {
         double dx = b.x - a.x;
         double dy = b.y - a.y;
         double dz = b.z - a.z;
         double reach = this.size.get() * (a.grain + b.grain) * 0.5 * 0.95;
         double d2 = dx * dx + dy * dy + dz * dz;
         if (!(d2 >= reach * reach) && !(d2 < 1.0E-8)) {
            double d = Math.sqrt(d2);
            float nx = (float)(dx / d);
            float ny = (float)(dy / d);
            float nz = (float)(dz / d);
            double push = (reach - d) * 0.5;
            a.x -= nx * push;
            a.y -= ny * push;
            a.z -= nz * push;
            b.x += nx * push;
            b.y += ny * push;
            b.z += nz * push;
            float closing = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny + (a.vz - b.vz) * nz;
            if (!(closing <= 0.0F)) {
               float impulse = closing * (1.0F + restitution) * 0.5F;
               a.vx -= impulse * nx;
               a.vy -= impulse * ny;
               a.vz -= impulse * nz;
               b.vx += impulse * nx;
               b.vy += impulse * ny;
               b.vz += impulse * nz;
            }
         }
      }
   }

   private boolean solid(ClientWorld level, double x, double y, double z) {
      this.cursor.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
      BlockState state = level.getBlockState(this.cursor);
      if (state.isAir()) {
         return false;
      }

      VoxelShape shape = state.getCollisionShape(level, this.cursor);
      if (shape.isEmpty()) {
         return false;
      }

      double lx = x - this.cursor.getX();
      double ly = y - this.cursor.getY();
      double lz = z - this.cursor.getZ();
      return lx >= shape.getMin(Axis.X)
         && lx <= shape.getMax(Axis.X)
         && ly >= shape.getMin(Axis.Y)
         && ly <= shape.getMax(Axis.Y)
         && lz >= shape.getMin(Axis.Z)
         && lz <= shape.getMax(Axis.Z);
   }

   private void onRender3D(Render3DEvent event) {
      if (this.live != 0) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null && mc.player != null) {
            this.canvas.begin(mc.gameRenderer.getCamera());
            float pt = event.partialTick;
            float time = (float)(System.nanoTime() - EPOCH) / 1.0E9F;
            int samples = Math.max(2, Math.min(60, Math.round(this.trailLength.get() * 20.0F)));
            String look = this.style.get();

            for (int i = 0; i < this.live; i++) {
               FloatingRelicsModule.Relic r = this.relics[i];
               if (r.placed) {
                  float since = this.age - r.placedAt + pt;
                  float grow = appear(since / 12.0F);
                  float extent = this.size.get() * 0.5F * r.grain * grow;
                  if (!(extent < 0.004F)) {
                     int n = this.trail(r, pt, samples);
                     this.drawTrail(look, i, n, extent, time);
                     int h = (n - 1) * 3;
                     this.drawHead(look, this.points[h], this.points[h + 1], this.points[h + 2], extent, time, r.phase);
                  }
               }
            }

            this.canvas.submit(false);
         }
      }
   }

   private int trail(FloatingRelicsModule.Relic r, float pt, int samples) {
      int m = Math.min(r.filled, samples + 2);
      if (m < 2) {
         this.points[0] = MathHelper.lerp(pt, r.lastX, r.x);
         this.points[1] = MathHelper.lerp(pt, r.lastY, r.y);
         this.points[2] = MathHelper.lerp(pt, r.lastZ, r.z);
         return 1;
      }

      for (int k = 0; k < m; k++) {
         int slot = Math.floorMod(r.head - m + k, 62) * 3;
         this.raw[k * 3] = r.history[slot];
         this.raw[k * 3 + 1] = r.history[slot + 1];
         this.raw[k * 3 + 2] = r.history[slot + 2];
      }

      double head = m - 2 + pt;
      double tail = Math.max(0.0, head - samples);
      int n = Math.max(2, (int)Math.ceil((head - tail) * 3.0) + 1);

      for (int j = 0; j < n; j++) {
         double u = j == n - 1 ? head : tail + (head - tail) * j / (n - 1);
         int k = Math.min(m - 2, (int)u);
         double t = u - k;
         int k0 = Math.max(0, k - 1) * 3;
         int k1 = k * 3;
         int k2 = (k + 1) * 3;
         int k3 = Math.min(m - 1, k + 2) * 3;

         for (int axis = 0; axis < 3; axis++) {
            this.points[j * 3 + axis] = catmull(this.raw[k0 + axis], this.raw[k1 + axis], this.raw[k2 + axis], this.raw[k3 + axis], t);
         }
      }

      return n;
   }

   private static double catmull(double p0, double p1, double p2, double p3, double t) {
      double t2 = t * t;
      double t3 = t2 * t;
      return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3);
   }

   private void drawTrail(String look, int relic, int n, float extent, float time) {
      if (n >= 2) {
         float width = extent * 0.7F;

         for (int k = 1; k < n; k++) {
            float t = (float)k / (n - 1);
            float tb = (float)(k - 1) / (n - 1);
            int a = (k - 1) * 3;
            int b = k * 3;
            int back = n - 1 - k;
            double ax = this.points[a];
            double ay = this.points[a + 1];
            double az = this.points[a + 2];
            double bx = this.points[b];
            double by = this.points[b + 1];
            double bz = this.points[b + 2];
            switch (look) {
               case "Fireball":
                  if (back % 3 == 0) {
                     int colx = lerpRgb(13114880, 16760896, t);
                     this.glowDisk(bx, by, bz, extent * (0.35F + 0.8F * t), colx, 0.55F * t * t);
                  }

                  if (back % 4 == 0) {
                     long seedx = relic * 7919L + back * 104729L + (long)(time * 6.0F);
                     double sx = bx + jitter(seedx, 1) * extent * 1.6;
                     double sy = by + jitter(seedx, 2) * extent + (1.0F - t) * extent * 2.4;
                     double sz = bz + jitter(seedx, 3) * extent * 1.6;
                     this.canvas.sprite(GlyphShapes.DISK, sx, sy, sz, extent * 0.09F, 0.0F, withAlpha(16765562, 0.9F * t));
                  }
                  break;
               case "Rainbow": {
                  int col = Color.HSBtoRGB(t * 0.85F - time * 0.25F, 0.85F, 1.0F) & 16777215;
                  this.strip(ax, ay, az, bx, by, bz, width * tb, width * t, withAlpha(col, 0.92F * t));
                  break;
               }
               case "Neon": {
                  int col = lerpRgb(16728018, 4190975, t);
                  this.strip(ax, ay, az, bx, by, bz, width * 1.5F * tb, width * 1.5F * t, withAlpha(col, 0.22F * t));
                  this.strip(ax, ay, az, bx, by, bz, width * 0.6F * tb, width * 0.6F * t, withAlpha(col, 0.8F * t));
                  this.strip(ax, ay, az, bx, by, bz, width * 0.18F, width * 0.18F, withAlpha(16777215, 0.9F * t));
                  break;
               }
               case "Electric":
                  long seed = relic * 7919L + back * 104729L + (long)(time * 14.0F);
                  jag(this.far, ax, ay, az, seed + 104729L, extent * 0.7 * (1.0F - tb * 0.5F));
                  jag(this.near, bx, by, bz, seed, extent * 0.7 * (1.0F - t * 0.5F));
                  this.strip(
                     this.far[0], this.far[1], this.far[2], this.near[0], this.near[1], this.near[2], width * 0.6F, width * 0.6F, withAlpha(6273023, 0.3F * t)
                  );
                  this.strip(
                     this.far[0],
                     this.far[1],
                     this.far[2],
                     this.near[0],
                     this.near[1],
                     this.near[2],
                     width * 0.16F,
                     width * 0.16F,
                     withAlpha(15136511, 0.95F * t)
                  );
                  break;
               case "Sakura":
                  if (back % 6 == 0) {
                     long seedx = relic * 7919L + back * 104729L;
                     double fall = (1.0F - t) * extent * 3.0;
                     bx += jitter(seedx, 1) * extent * (1.4F - t);
                     bz += jitter(seedx, 3) * extent * (1.4F - t);
                     float spin = time * 2.1F + back * 0.7F + relic;
                     this.canvas.sprite(PETAL, bx, by - fall, bz, extent * (0.45F + 0.45F * t), spin, withAlpha(lerpRgb(16770286, 16748472, t), 0.95F * t));
                  }
                  break;
               case "Void":
                  for (int strand = 0; strand < 2; strand++) {
                     helix(this.far, ax, ay, az, bx, by, bz, tb, time, strand, extent);
                     helix(this.near, bx, by, bz, bx + (bx - ax), by + (by - ay), bz + (bz - az), t, time, strand, extent);
                     int colx = strand == 0 ? 12472575 : 16729800;
                     this.strip(
                        this.far[0],
                        this.far[1],
                        this.far[2],
                        this.near[0],
                        this.near[1],
                        this.near[2],
                        width * 0.32F,
                        width * 0.32F,
                        withAlpha(colx, 0.85F * t)
                     );
                  }
                  break;
               case "Aurora": {
                  int col = lerpRgb(4194208, 11558911, 0.5F + 0.5F * MathHelper.sin(t * 6.0F + time * 1.5F));
                  float shimmer = 0.45F + 0.55F * Math.abs(MathHelper.sin(k * 0.9F + time * 3.0F));
                  double tall = extent * 3.2 * t * (0.7F + 0.3F * shimmer);
                  this.curtain(ax, ay, az, bx, by, bz, extent * 3.2 * tb * 0.5, tall * 0.5, 0.0, withAlpha(col, 0.5F * t * shimmer));
                  this.curtain(ax, ay, az, bx, by, bz, extent * 3.2 * tb, tall, extent * 3.2 * tb * 0.5, withAlpha(col, 0.16F * t * shimmer), tall * 0.5);
                  break;
               }
               default: {
                  int col = lerpRgb(4160511, 13625599, t);
                  this.strip(ax, ay, az, bx, by, bz, width * 1.9F * tb, width * 1.9F * t, withAlpha(col, 0.16F * t * t));
                  this.strip(ax, ay, az, bx, by, bz, width * tb, width * t, withAlpha(col, 0.75F * t * t));
               }
            }
         }
      }
   }

   private void drawHead(String look, double x, double y, double z, float extent, float time, float phase) {
      switch (look) {
         case "Fireball":
            this.glow(x, y, z, extent * 1.1F, 16742938, 0.9F);
            this.glowDisk(x, y, z, extent * 0.45F, 16773808, 1.0F);
            break;
         case "Rainbow":
         case "Neon":
            this.glow(x, y, z, extent * 0.9F, 16777215, 1.0F);
            break;
         case "Electric":
            this.glow(x, y, z, extent * 1.1F, 6273023, 0.9F);
            this.glowDisk(x, y, z, extent * 0.4F, 16777215, 1.0F);
            break;
         case "Sakura":
            this.glow(x, y, z, extent * 0.9F, 16757713, 0.8F);
            this.canvas.sprite(PETAL, x, y, z, extent * 0.85F, time * 1.6F + phase, withAlpha(16751812, 1.0F));
            break;
         case "Void":
            this.glow(x, y, z, extent * 1.2F, 10174463, 0.85F);
            this.canvas.sprite(GlyphShapes.HALO, x, y, z, extent * 0.62F, time * 2.0F, withAlpha(14456575, 0.95F));
            this.canvas.sprite(GlyphShapes.DISK, x, y, z, extent * 0.5F, 0.0F, withAlpha(656400, 1.0F));
            break;
         case "Aurora":
            this.glow(x, y, z, extent * 0.8F, 13697000, 1.0F);
            break;
         default:
            this.glow(x, y, z, extent * 1.2F, 9422079, 0.85F);
            this.glowDisk(x, y, z, extent * 0.38F, 16777215, 1.0F);
      }
   }

   private void glow(double x, double y, double z, float radius, int rgb, float alpha) {
      for (int layer = 0; layer < GLOW_SCALES.length; layer++) {
         int col = layer == GLOW_SCALES.length - 1 ? lerpRgb(rgb, 16777215, 0.6F) : rgb;
         float[] disk = GLOW_ALPHAS[layer] < 0.1F ? SOFT : ROUND;
         this.canvas.sprite(disk, x, y, z, radius * GLOW_SCALES[layer], 0.0F, withAlpha(col, alpha * GLOW_ALPHAS[layer]));
      }
   }

   private void glowDisk(double x, double y, double z, float radius, int rgb, float alpha) {
      this.canvas.sprite(SOFT, x, y, z, radius * 1.6F, 0.0F, withAlpha(rgb, alpha * 0.25F));
      this.canvas.sprite(ROUND, x, y, z, radius, 0.0F, withAlpha(rgb, alpha));
   }

   private void strip(double ax, double ay, double az, double bx, double by, double bz, float wa, float wb, int argb) {
      if (!(wa <= 0.0F) || !(wb <= 0.0F)) {
         double dx = bx - ax;
         double dy = by - ay;
         double dz = bz - az;
         double tx = this.canvas.camX() - (ax + bx) * 0.5;
         double ty = this.canvas.camY() - (ay + by) * 0.5;
         double tz = this.canvas.camZ() - (az + bz) * 0.5;
         double sx = dy * tz - dz * ty;
         double sy = dz * tx - dx * tz;
         double sz = dx * ty - dy * tx;
         double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(length < 1.0E-9)) {
            sx /= length;
            sy /= length;
            sz /= length;
            this.canvas
               .quad(
                  ax + sx * wa,
                  ay + sy * wa,
                  az + sz * wa,
                  bx + sx * wb,
                  by + sy * wb,
                  bz + sz * wb,
                  bx - sx * wb,
                  by - sy * wb,
                  bz - sz * wb,
                  ax - sx * wa,
                  ay - sy * wa,
                  az - sz * wa,
                  argb
               );
         }
      }
   }

   private void curtain(double ax, double ay, double az, double bx, double by, double bz, double topA, double topB, double base, int argb) {
      this.curtain(ax, ay, az, bx, by, bz, topA, topB, base, argb, base);
   }

   private void curtain(double ax, double ay, double az, double bx, double by, double bz, double topA, double topB, double baseA, int argb, double baseB) {
      if (!(topA <= baseA) || !(topB <= baseB)) {
         this.canvas.quad(ax, ay + baseA, az, bx, by + baseB, bz, bx, by + topB, bz, ax, ay + topA, az, argb);
         this.canvas.quad(ax, ay + topA, az, bx, by + topB, bz, bx, by + baseB, bz, ax, ay + baseA, az, argb);
      }
   }

   private static void jag(double[] out, double x, double y, double z, long seed, double reach) {
      out[0] = x + jitter(seed, 1) * reach;
      out[1] = y + jitter(seed, 2) * reach;
      out[2] = z + jitter(seed, 3) * reach;
   }

   private static void helix(double[] out, double ax, double ay, double az, double bx, double by, double bz, float t, float time, int strand, float extent) {
      double dx = bx - ax;
      double dy = by - ay;
      double dz = bz - az;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length < 1.0E-9) {
         out[0] = ax;
         out[1] = ay;
         out[2] = az;
      } else {
         dx /= length;
         dy /= length;
         dz /= length;
         double ux = -dz;
         double uy = 0.0;
         double uz = dx;
         double ul = Math.sqrt(ux * ux + uz * uz);
         if (ul < 1.0E-6) {
            ux = 1.0;
            uz = 0.0;
            ul = 1.0;
         }

         ux /= ul;
         uz /= ul;
         double vx = dy * uz - dz * uy;
         double vy = dz * ux - dx * uz;
         double vz = dx * uy - dy * ux;
         double angle = t * 9.0 + time * 4.0 + strand * Math.PI;
         double radius = extent * (0.9 - 0.5 * t);
         double c = Math.cos(angle) * radius;
         double s = Math.sin(angle) * radius;
         out[0] = ax + ux * c + vx * s;
         out[1] = ay + uy * c + vy * s;
         out[2] = az + uz * c + vz * s;
      }
   }

   private static double jitter(long seed, int axis) {
      long h = seed * -7046029254386353131L + axis * -4417276706812531889L;
      h ^= h >>> 31;
      h *= -4658895280553007687L;
      h ^= h >>> 29;
      return (h >>> 11) / 9.007199E15F * 2.0 - 1.0;
   }

   private static float[] round(int segments) {
      float[] out = new float[segments / 2 * 8];

      for (int k = 0; k < segments; k += 2) {
         int o = k / 2 * 8;

         for (int v = 0; v < 3; v++) {
            float angle = (k + v) * (float) (Math.PI * 2) / segments;
            out[o + 2 + v * 2] = MathHelper.cos(angle);
            out[o + 3 + v * 2] = MathHelper.sin(angle);
         }
      }

      return out;
   }

   private static float[] petal() {
      int n = 16;
      double[] px = new double[n];
      double[] py = new double[n];

      for (int k = 0; k < n; k++) {
         double t = (double)k / n * Math.PI * 2.0;
         double notch = 1.0 - 0.28 * Math.pow(Math.max(0.0, Math.cos(t - (Math.PI / 2))), 12.0);
         px[k] = Math.cos(t) * 0.62 * (0.75 + 0.25 * Math.sin(t)) * notch;
         py[k] = Math.sin(t) * notch;
      }

      float[] out = new float[n * 8];

      for (int k = 0; k < n; k++) {
         int j = (k + 1) % n;
         int o = k * 8;
         out[o] = 0.0F;
         out[o + 1] = 0.0F;
         out[o + 2] = (float)px[k];
         out[o + 3] = (float)py[k];
         out[o + 4] = (float)px[j];
         out[o + 5] = (float)py[j];
         out[o + 6] = (float)px[j];
         out[o + 7] = (float)py[j];
      }

      return out;
   }

   private static float appear(float t) {
      if (t >= 1.0F) {
         return 1.0F;
      }

      if (t <= 0.0F) {
         return 0.0F;
      }

      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv + 0.22F * MathHelper.sin(t * (float) Math.PI) * inv;
   }

   private static int lerpRgb(int a, int b, float t) {
      t = MathHelper.clamp(t, 0.0F, 1.0F);
      int r = (int)((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = (int)((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return r << 16 | g << 8 | bl;
   }

   private static int withAlpha(int rgb, float alpha) {
      int a = (int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F + 0.5F);
      return a << 24 | rgb & 16777215;
   }

   @Environment(EnvType.CLIENT)
   private static final class Relic {
      double x;
      double y;
      double z;
      double lastX;
      double lastY;
      double lastZ;
      float vx;
      float vy;
      float vz;
      float orbit;
      float lift;
      float grain;
      float phase;
      boolean placed;
      int wakeAt;
      int placedAt;
      final double[] history = new double[186];
      int head;
      int filled;

      void remember() {
         this.history[this.head * 3] = this.x;
         this.history[this.head * 3 + 1] = this.y;
         this.history[this.head * 3 + 2] = this.z;
         this.head = (this.head + 1) % 62;
         this.filled = Math.min(62, this.filled + 1);
      }
   }
}
