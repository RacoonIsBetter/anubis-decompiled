package net.anubis.client.module.impl.render;

import com.example.client.mixin.AccessorBlockOverlayMultiPlayerGameMode;
import com.example.client.mixin.AccessorGameRenderer;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.entity.Entity;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.hit.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class BlockOverlayModule extends ClientModule {
   private static final String BOTH = "Both";
   private static final String OUTLINE = "Outline";
   private static final String FILL = "Fill";
   private static final String THEME = "Theme";
   private static final String GRADIENT = "Gradient";
   private static final String SOLID = "Solid";
   private static final String RAINBOW = "Rainbow";
   private static final float THEME_LIGHTEN = 0.55F;
   private static final float FADE_IN = 0.12F;
   private static final float FADE_OUT = 0.2F;
   private static final double SNAP_DISTANCE = 6.0;
   private static final float FLOW_SPEED = 0.35F;
   private static final double SEGMENT = 0.125;
   private static final double FILL_INFLATE = 0.002;
   private static final double LINE_LIFT = 0.004;
   private static final double GLOW_LIFT = 0.002;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Both", "Both", "Outline", "Fill"));
   private final ModeSetting colorMode = this.addSetting("color-mode", new ModeSetting("Color Mode", "Theme", "Theme", "Gradient", "Solid", "Rainbow"));
   private final ColorSetting color = this.addSetting("color", new ColorSetting("Color", -871878));
   private final ColorSetting secondColor = this.addSetting("second-color", new ColorSetting("Second Color", -6480));
   private final BooleanSetting flow = this.addSetting("flow", new BooleanSetting("Flow", true));
   private final NumberSetting fillOpacity = this.addSetting("fill-opacity", new NumberSetting("Fill Opacity", 20.0F, 2.0F, 60.0F).step(1.0F));
   private final NumberSetting width = this.addSetting("width", new NumberSetting("Width", 2.0F, 1.0F, 5.0F).step(0.5F));
   private final BooleanSetting glow = this.addSetting("glow", new BooleanSetting("Glow", true));
   private final BooleanSetting smooth = this.addSetting("smooth", new BooleanSetting("Smooth", true));
   private final NumberSetting smoothSpeed = this.addSetting("smooth-speed", new NumberSetting("Smooth Speed", 16.0F, 4.0F, 40.0F).step(1.0F));
   private final BooleanSetting miningProgress = this.addSetting("mining-progress", new BooleanSetting("Mining Progress", true));
   private final BooleanSetting faceOnly = this.addSetting("face-only", new BooleanSetting("Face Only", false));
   private final BooleanSetting seeThrough = this.addSetting("see-through", new BooleanSetting("See Through", false));
   private ClientWorld level;
   private BlockPos pos;
   private BlockState state;
   private BlockPos partner;
   private BlockState partnerState;
   private final List<double[]> edges = new ArrayList<>();
   private final OverlaySegments mobileSegments = new OverlaySegments();
   private List<double[]> mobileFaceEdges = List.of();
   private Box mobileFaceBox;
   private Direction mobileFace;
   private List<Box> boxes = List.of();
   private Box localBounds;
   private Box goal;
   private Box box;
   private Direction face;
   private Box faceBox;
   private float presence;
   private float progress;
   private long lastFrame;
   private Vec3d eye;
   private double time;
   private float fade;
   private double ax;
   private double bx;
   private double ay;
   private double by;
   private double az;
   private double bz;

   public BlockOverlayModule() {
      super("block-overlay", "Block Overlay", "A smooth, animated outline and fill on the block you look at.", ModuleCategory.VISUALS);
      this.color.visibleWhen(() -> !this.colorMode.is("Theme"));
      this.secondColor.visibleWhen(() -> this.colorMode.is("Gradient"));
      this.flow.visibleWhen(() -> !this.colorMode.is("Solid"));
      this.fillOpacity.visibleWhen(() -> !this.style.is("Outline"));
      this.width.visibleWhen(() -> !this.style.is("Fill"));
      this.glow.visibleWhen(() -> !this.style.is("Fill"));
      this.smoothSpeed.visibleWhen(this.smooth::get);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         if (mc.world != this.level) {
            this.reset();
            this.level = mc.world;
         }

         long now = System.nanoTime();
         float dt = this.lastFrame == 0L ? 0.0F : Math.min((float)(now - this.lastFrame) / 1.0E9F, 0.1F);
         this.lastFrame = now;
         BlockHitResult hit = target(mc);
         if (hit != null && this.adopt(mc, hit)) {
            if (this.box != null && !(this.presence < 0.02F) && this.smooth.get() && !(this.box.getCenter().distanceTo(this.goal.getCenter()) > 6.0)) {
               this.box = lerp(this.box, this.goal, 1.0F - (float)Math.exp(-this.smoothSpeed.get() * dt));
            } else {
               this.box = this.goal;
            }

            this.presence = Math.min(1.0F, this.presence + dt / 0.12F);
         } else {
            hit = null;
            this.presence = Math.max(0.0F, this.presence - dt / 0.2F);
            if (this.presence == 0.0F) {
               this.reset();
               this.level = mc.world;
               return;
            }
         }

         if (this.box != null) {
            this.updateProgress(mc, dt, hit != null);
            this.eye = mc.gameRenderer.getCamera().getCameraPos();
            this.time = now % 3600000000000L / 1.0E9;
            this.draw();
         }
      } else {
         this.reset();
      }
   }

   private static BlockHitResult target(MinecraftClient mc) {
      if (!(mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == Type.BLOCK && !hit.isInsideBlock())) {
         return null;
      } else {
         if (!((AccessorGameRenderer)mc.gameRenderer).anubis$shouldRenderBlockOutline()) {
            return null;
         }

         BlockPos at = hit.getBlockPos();
         return !mc.world.getBlockState(at).isAir() && mc.world.getWorldBorder().contains(at) ? hit : null;
      }
   }

   private boolean adopt(MinecraftClient mc, BlockHitResult hit) {
      ClientWorld world = mc.world;
      BlockPos at = hit.getBlockPos();
      BlockPos other = partner(world, at, world.getBlockState(at));
      BlockPos ref = other != null && other.compareTo(at) < 0 ? other : at;
      BlockPos second = other == null ? null : (ref.equals(at) ? other : at);
      BlockState refState = world.getBlockState(ref);
      BlockState secondState = second == null ? null : world.getBlockState(second);
      if (!ref.equals(this.pos) || refState != this.state || secondState != this.partnerState) {
         Entity viewer = mc.getCameraEntity();
         ShapeContext context = viewer == null ? ShapeContext.absent() : ShapeContext.of(viewer);
         VoxelShape shape = refState.getOutlineShape(world, ref, context);
         if (second != null) {
            shape = VoxelShapes.union(shape, secondState.getOutlineShape(world, second, context).offset(second.subtract(ref)));
         }

         shape = shape.simplify();
         if (shape.isEmpty()) {
            return false;
         }

         if (!ref.equals(this.pos)) {
            this.progress = 0.0F;
         }

         this.pos = ref;
         this.state = refState;
         this.partner = second;
         this.partnerState = secondState;
         this.edges.clear();
         this.mobileSegments.invalidate();
         this.mobileFaceBox = null;
         shape.forEachEdge((x0, y0, z0, x1, y1, z1) -> this.edges.add(new double[]{x0, y0, z0, x1, y1, z1}));
         this.boxes = shape.getBoundingBoxes();
         this.localBounds = shape.getBoundingBox();
         this.goal = this.localBounds.offset(ref);
      }

      this.face = hit.getSide();
      this.faceBox = this.boxOnFace(hit.getPos().subtract(this.pos.getX(), this.pos.getY(), this.pos.getZ()));
      return true;
   }

   private static BlockPos partner(ClientWorld world, BlockPos at, BlockState state) {
      BlockPos other = null;
      if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
         other = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? at.up() : at.down();
      } else if (state.getBlock() instanceof BedBlock) {
         other = at.offset(BedBlock.getOppositePartDirection(state));
      } else if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
         other = ChestBlock.getPosInFrontOf(at, state);
      }

      return other != null && world.getBlockState(other).isOf(state.getBlock()) ? other : null;
   }

   private Box boxOnFace(Vec3d local) {
      double e = 0.001;

      for (Box b : this.boxes) {
         boolean onPlane = switch (this.face) {
            case EAST -> Math.abs(local.x - b.maxX) < e;
            case WEST -> Math.abs(local.x - b.minX) < e;
            case UP -> Math.abs(local.y - b.maxY) < e;
            case DOWN -> Math.abs(local.y - b.minY) < e;
            case SOUTH -> Math.abs(local.z - b.maxZ) < e;
            case NORTH -> Math.abs(local.z - b.minZ) < e;
            default -> throw new MatchException(null, null);
         };
         if (onPlane && b.expand(e).contains(local)) {
            return b;
         }
      }

      return this.localBounds;
   }

   private void updateProgress(MinecraftClient mc, float dt, boolean targeted) {
      float want = 0.0F;
      ClientPlayerInteractionManager gameMode = mc.interactionManager;
      if (targeted && this.miningProgress.get() && gameMode != null && gameMode.isBreakingBlock()) {
         AccessorBlockOverlayMultiPlayerGameMode mining = (AccessorBlockOverlayMultiPlayerGameMode)gameMode;
         BlockPos breaking = mining.anubis$blockOverlayDestroyPos();
         if (breaking != null && (breaking.equals(this.pos) || breaking.equals(this.partner))) {
            want = MathHelper.clamp(mining.anubis$blockOverlayDestroyProgress(), 0.0F, 1.0F);
         }
      }

      this.progress = this.progress + (want - this.progress) * (1.0F - (float)Math.exp(-18.0F * dt));
   }

   private void reset() {
      this.level = null;
      this.pos = null;
      this.state = null;
      this.partner = null;
      this.partnerState = null;
      this.edges.clear();
      this.mobileSegments.clear();
      this.mobileFaceEdges = List.of();
      this.mobileFaceBox = null;
      this.mobileFace = null;
      this.boxes = List.of();
      this.localBounds = null;
      this.goal = null;
      this.box = null;
      this.face = null;
      this.faceBox = null;
      this.presence = 0.0F;
      this.progress = 0.0F;
      this.lastFrame = 0L;
   }

   private void draw() {
      this.fade = this.presence * this.presence * (3.0F - 2.0F * this.presence);
      double pop = 0.94 + 0.06 * this.fade;
      double[] x = axis(
         this.box.minX, this.box.maxX, this.goal.minX, this.goal.maxX, this.pos.getX() - this.goal.minX, pop
      );
      double[] y = axis(
         this.box.minY, this.box.maxY, this.goal.minY, this.goal.maxY, this.pos.getY() - this.goal.minY, pop
      );
      double[] z = axis(
         this.box.minZ, this.box.maxZ, this.goal.minZ, this.goal.maxZ, this.pos.getZ() - this.goal.minZ, pop
      );
      this.ax = x[0];
      this.bx = x[1];
      this.ay = y[0];
      this.by = y[1];
      this.az = z[0];
      this.bz = z[1];
      boolean lines = !this.style.is("Fill");
      boolean fill = !this.style.is("Outline");
      float fillAlpha = this.fillOpacity.get() / 100.0F;
      if (fill) {
         if (this.faceOnly.get()) {
            this.faceFill(this.faceBox, this.face, fillAlpha, 0.002);
         } else {
            for (Box b : this.boxes) {
               this.boxFill(b, fillAlpha, 0.002);
            }
         }
      }

      if (this.progress > 0.01F) {
         this.progressFill(Math.min(0.45F, fillAlpha * 1.4F + 0.16F));
      }

      if (lines) {
         List<double[]> outlineEdges = this.edges;
         if (this.faceOnly.get()) {
            if (!MobilePerformance.active()) {
               outlineEdges = faceEdges(this.faceBox, this.face);
            } else {
               if (this.faceBox != this.mobileFaceBox || this.face != this.mobileFace) {
                  this.mobileFaceEdges = faceEdges(this.faceBox, this.face);
                  this.mobileFaceBox = this.faceBox;
                  this.mobileFace = this.face;
               }

               outlineEdges = this.mobileFaceEdges;
            }
         }

         this.outline(outlineEdges);
      }
   }

   private static double[] axis(double boxMin, double boxMax, double goalMin, double goalMax, double offset, double pop) {
      double goalSize = goalMax - goalMin;
      double scale = goalSize > 1.0E-6 ? (boxMax - boxMin) / goalSize : 1.0;
      double centre = (boxMin + boxMax) * 0.5;
      double a = centre * (1.0 - pop) + (boxMin + offset * scale) * pop;
      return new double[]{a, scale * pop};
   }

   private Vec3d world(double lx, double ly, double lz) {
      return new Vec3d(this.ax + this.bx * lx, this.ay + this.by * ly, this.az + this.bz * lz);
   }

   private void outline(List<double[]> list) {
      if (MobilePerformance.active()) {
         this.outlineMobile(list);
      } else {
         boolean varies = !this.colorMode.is("Solid");
         List<double[]> pieces = new ArrayList<>(list.size() * 4);
         List<Integer> colors = new ArrayList<>(list.size() * 4);

         for (double[] e : list) {
            double length = Math.sqrt(sq(e[3] - e[0]) + sq(e[4] - e[1]) + sq(e[5] - e[2]));
            int n = varies ? MathHelper.clamp((int)Math.ceil(length / 0.125), 1, 16) : 1;

            for (int i = 0; i < n; i++) {
               double t0 = (double)i / n;
               double t1 = (double)(i + 1) / n;
               double tm = (t0 + t1) * 0.5;
               pieces.add(
                  new double[]{
                     MathHelper.lerp(t0, e[0], e[3]),
                     MathHelper.lerp(t0, e[1], e[4]),
                     MathHelper.lerp(t0, e[2], e[5]),
                     MathHelper.lerp(t1, e[0], e[3]),
                     MathHelper.lerp(t1, e[1], e[4]),
                     MathHelper.lerp(t1, e[2], e[5])
                  }
               );
               colors.add(
                  this.colorAt(MathHelper.lerp(tm, e[0], e[3]), MathHelper.lerp(tm, e[1], e[4]), MathHelper.lerp(tm, e[2], e[5]))
               );
            }
         }

         float w = this.width.get();
         if (this.glow.get()) {
            for (int i = 0; i < pieces.size(); i++) {
               double[] p = pieces.get(i);
               int c = colors.get(i);
               this.line(p, ColorHelper.scaleAlpha(c, 0.34F * this.fade), w + 3.0F, 0.002);
               this.line(p, ColorHelper.scaleAlpha(c, 0.13F * this.fade), w + 8.0F, 0.002);
            }
         }

         for (int i = 0; i < pieces.size(); i++) {
            this.line(pieces.get(i), ColorHelper.scaleAlpha(colors.get(i), this.fade), w, 0.004);
         }
      }
   }

   private void outlineMobile(List<double[]> list) {
      this.mobileSegments.update(list, !this.colorMode.is("Solid"));
      double[] points = this.mobileSegments.points();
      int[] colors = this.mobileSegments.colors();
      int count = this.mobileSegments.count();
      int i = 0;

      for (int p = 0; i < count; p += 9) {
         colors[i] = this.colorAt(points[p + 6], points[p + 7], points[p + 8]);
         i++;
      }

      float w = this.width.get();
      if (this.glow.get()) {
         int ix = 0;

         for (int p = 0; ix < count; p += 9) {
            Vec3d from = this.lifted(points[p], points[p + 1], points[p + 2], 0.002);
            Vec3d to = this.lifted(points[p + 3], points[p + 4], points[p + 5], 0.002);
            this.drawLine(from, to, ColorHelper.scaleAlpha(colors[ix], 0.34F * this.fade), w + 3.0F);
            this.drawLine(from, to, ColorHelper.scaleAlpha(colors[ix], 0.13F * this.fade), w + 8.0F);
            ix++;
         }
      }

      int ix = 0;

      for (int p = 0; ix < count; p += 9) {
         this.drawLine(
            this.lifted(points[p], points[p + 1], points[p + 2], 0.004),
            this.lifted(points[p + 3], points[p + 4], points[p + 5], 0.004),
            ColorHelper.scaleAlpha(colors[ix], this.fade),
            w
         );
         ix++;
      }
   }

   private Vec3d lifted(double lx, double ly, double lz, double amount) {
      double x = this.ax + this.bx * lx;
      double y = this.ay + this.by * ly;
      double z = this.az + this.bz * lz;
      double dx = this.eye.x - x;
      double dy = this.eye.y - y;
      double dz = this.eye.z - z;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length < 1.0E-6) {
         return new Vec3d(x, y, z);
      }

      double scale = amount / length;
      return new Vec3d(x + dx * scale, y + dy * scale, z + dz * scale);
   }

   private void drawLine(Vec3d from, Vec3d to, int argb, float width) {
      VisibilityConfigurable drawn = GizmoDrawing.line(from, to, argb, width);
      if (this.seeThrough.get()) {
         drawn.ignoreOcclusion();
      }
   }

   private void line(double[] p, int argb, float w, double lift) {
      VisibilityConfigurable drawn = GizmoDrawing.line(this.lift(this.world(p[0], p[1], p[2]), lift), this.lift(this.world(p[3], p[4], p[5]), lift), argb, w);
      if (this.seeThrough.get()) {
         drawn.ignoreOcclusion();
      }
   }

   private Vec3d lift(Vec3d point, double by) {
      Vec3d toEye = this.eye.subtract(point);
      double length = toEye.length();
      return length < 1.0E-6 ? point : point.add(toEye.multiply(by / length));
   }

   private void boxFill(Box b, float alpha, double inflate) {
      for (Direction d : Direction.values()) {
         this.faceFill(b, d, alpha, inflate);
      }
   }

   private void faceFill(Box b, Direction d, float alpha, double inflate) {
      Vec3d min = this.world(b.minX, b.minY, b.minZ).subtract(inflate, inflate, inflate);
      Vec3d max = this.world(b.maxX, b.maxY, b.maxZ).add(inflate, inflate, inflate);
      Vec3d centre = faceCentre(b, d);
      int c = this.colorAt(centre.x, centre.y, centre.z);
      int argb = MathHelper.clamp(Math.round(alpha * this.fade * 255.0F), 0, 255) << 24 | c & 16777215;
      VisibilityConfigurable drawn = GizmoDrawing.face(min, max, d, DrawStyle.filled(argb));
      if (this.seeThrough.get()) {
         drawn.ignoreOcclusion();
      }
   }

   private void progressFill(float alpha) {
      double inflate = 0.004;
      if (this.faceOnly.get()) {
         Box b = this.faceBox;
         Box part = this.face.getAxis() == Axis.Y
            ? b.contract(b.getLengthX() * (1.0F - this.progress) * 0.5, 0.0, b.getLengthZ() * (1.0F - this.progress) * 0.5)
            : new Box(b.minX, b.minY, b.minZ, b.maxX, b.minY + b.getLengthY() * this.progress, b.maxZ);
         this.faceFill(part, this.face, alpha, inflate);
      } else {
         double top = this.localBounds.minY + this.localBounds.getLengthY() * this.progress;

         for (Box b : this.boxes) {
            if (!(b.minY >= top)) {
               this.boxFill(new Box(b.minX, b.minY, b.minZ, b.maxX, Math.min(b.maxY, top), b.maxZ), alpha, inflate);
            }
         }
      }
   }

   private int colorAt(double lx, double ly, double lz) {
      boolean themed = this.colorMode.is("Theme");
      int base = themed ? CosmeticAccent.argb() : this.color.argb();
      if (this.colorMode.is("Solid")) {
         return base;
      } else {
         double u = (
               norm(lx, this.localBounds.minX, this.localBounds.maxX)
                  + norm(ly, this.localBounds.minY, this.localBounds.maxY)
                  + norm(lz, this.localBounds.minZ, this.localBounds.maxZ)
            )
            / 3.0;
         double shift = this.flow.get() ? this.time * 0.35F : 0.0;
         if (this.colorMode.is("Rainbow")) {
            double hue = u * 0.5 - shift;
            return base & 0xFF000000 | MathHelper.hsvToRgb((float)(hue - Math.floor(hue)), 0.65F, 1.0F) & 16777215;
         } else {
            double k = this.flow.get() ? 0.5 - 0.5 * Math.cos((Math.PI * 2) * (u - shift)) : u;
            int second = themed ? ColorHelper.lerp(0.55F, base, -1) : this.secondColor.argb();
            return ColorHelper.lerp((float)k, base, second);
         }
      }
   }

   private static double norm(double v, double min, double max) {
      return max - min < 1.0E-6 ? 0.5 : (v - min) / (max - min);
   }

   private static Vec3d faceCentre(Box b, Direction d) {
      Vec3d c = b.getCenter();

      return switch (d) {
         case EAST -> new Vec3d(b.maxX, c.y, c.z);
         case WEST -> new Vec3d(b.minX, c.y, c.z);
         case UP -> new Vec3d(c.x, b.maxY, c.z);
         case DOWN -> new Vec3d(c.x, b.minY, c.z);
         case SOUTH -> new Vec3d(c.x, c.y, b.maxZ);
         case NORTH -> new Vec3d(c.x, c.y, b.minZ);
         default -> throw new MatchException(null, null);
      };
   }

   private static List<double[]> faceEdges(Box b, Direction d) {
      double[][] c = switch (d.getAxis()) {
         case X -> {
            double x = d == Direction.EAST ? b.maxX : b.minX;
            yield new double[][]{
               {x, b.minY, b.minZ}, {x, b.maxY, b.minZ}, {x, b.maxY, b.maxZ}, {x, b.minY, b.maxZ}
            };
         }
         case Y -> {
            double y = d == Direction.UP ? b.maxY : b.minY;
            yield new double[][]{
               {b.minX, y, b.minZ}, {b.maxX, y, b.minZ}, {b.maxX, y, b.maxZ}, {b.minX, y, b.maxZ}
            };
         }
         case Z -> {
            double z = d == Direction.SOUTH ? b.maxZ : b.minZ;
            yield new double[][]{
               {b.minX, b.minY, z}, {b.maxX, b.minY, z}, {b.maxX, b.maxY, z}, {b.minX, b.maxY, z}
            };
         }
         default -> throw new MatchException(null, null);
      };
      List<double[]> edges = new ArrayList<>(4);

      for (int i = 0; i < 4; i++) {
         double[] p = c[i];
         double[] q = c[(i + 1) % 4];
         edges.add(new double[]{p[0], p[1], p[2], q[0], q[1], q[2]});
      }

      return edges;
   }

   private static Box lerp(Box from, Box to, float k) {
      return new Box(
         MathHelper.lerp(k, from.minX, to.minX),
         MathHelper.lerp(k, from.minY, to.minY),
         MathHelper.lerp(k, from.minZ, to.minZ),
         MathHelper.lerp(k, from.maxX, to.maxX),
         MathHelper.lerp(k, from.maxY, to.maxY),
         MathHelper.lerp(k, from.maxZ, to.maxZ)
      );
   }

   private static double sq(double v) {
      return v * v;
   }
}
