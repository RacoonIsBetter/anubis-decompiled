package net.anubis.client.module.impl.render;

import com.example.client.mixin.AccessorSpawnerNametagsBaseSpawner;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.gui.WorldText;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.MobilePerformance;
import net.anubis.client.util.StableTopK;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class SpawnerNametagsModule extends ClientModule implements HudOverlayProvider, ChunkScanner.Consumer {
   private static final int MAX_LABELS = 48;
   private static final int RING_SEGMENTS = 64;
   private static final float RING_WIDTH = 1.5F;
   private static final float RING_ALPHA_INSIDE = 0.8F;
   private static final float RING_ALPHA_OUTSIDE = 0.4F;
   private static final double LABEL_HEIGHT = 1.2;
   private static final double FADE_SHARE = 0.125;
   private static final String SEPARATOR = "  ·  ";
   private final StableTopK<SpawnerNametagsModule.Candidate> mobileCandidates = new StableTopK<>(
      Comparator.comparingDouble(SpawnerNametagsModule.Candidate::distance)
   );
   private final List<SpawnerNametagsModule.Shown> mobileShown = new ArrayList<>(48);
   private static final double[] RING_COS = new double[65];
   private static final double[] RING_SIN = new double[65];
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 64.0F, 8.0F, 256.0F).step(1.0F));
   private final BooleanSetting sRadius = this.addSetting("activation-radius", new BooleanSetting("Activation Radius", true));
   private final LongOpenHashSet spawners = new LongOpenHashSet();

   public SpawnerNametagsModule() {
      super("spawner-nametags", "Spawner Nametags", "Shows each spawner's mob and activation range over the block.", ModuleCategory.RENDER);
   }

   @Override
   protected void onEnable() {
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.spawners.clear();
      this.mobileShown.clear();
      this.mobileCandidates.reset(0);
   }

   public static String mobName(MobSpawnerBlockEntity spawner) {
      MobSpawnerEntry data = ((AccessorSpawnerNametagsBaseSpawner)spawner.getLogic()).anubis$spawnerNametagsNextSpawnData();
      if (data == null) {
         return null;
      }

      String id = data.entity().getString("id", "");
      return id.isEmpty() ? null : EntityType.get(id).map(type -> type.getName().getString()).orElse(null);
   }

   public static int activationRange(MobSpawnerBlockEntity spawner) {
      return ((AccessorSpawnerNametagsBaseSpawner)spawner.getLogic()).anubis$spawnerNametagsRequiredPlayerRange();
   }

   @Override
   public boolean wants(BlockState state) {
      return state.isOf(Blocks.SPAWNER);
   }

   @Override
   public void found(long pos, BlockState state) {
      this.spawners.add(pos);
   }

   @Override
   public void removed(long pos) {
      this.spawners.remove(pos);
   }

   @Override
   public void reset() {
      this.spawners.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      LongIterator it = this.spawners.iterator();

      while (it.hasNext()) {
         long pos = it.nextLong();
         if (ChunkPos.toLong(BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4) == chunkKey && !stillThere.test(pos)) {
            it.remove();
         }
      }
   }

   private List<SpawnerNametagsModule.Shown> inRange(MinecraftClient mc) {
      if (MobilePerformance.active()) {
         return this.mobileInRange(mc);
      }

      this.mobileShown.clear();
      List<SpawnerNametagsModule.Shown> shown = new ArrayList<>();
      if (!this.spawners.isEmpty() && mc.world != null && mc.player != null) {
         World level = mc.world;
         Vec3d player = mc.player.getEntityPos();
         double range = this.sRange.get();
         LongIterator it = this.spawners.iterator();

         while (it.hasNext()) {
            BlockPos pos = BlockPos.fromLong(it.nextLong());
            double distanceSq = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (!(distanceSq > range * range) && level.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
               int activation = activationRange(spawner);
               boolean inside = activation < 0 || distanceSq < (double)activation * activation;
               shown.add(new SpawnerNametagsModule.Shown(pos, Math.sqrt(distanceSq), mobName(spawner), activation, inside));
            }
         }

         shown.sort(Comparator.comparingDouble(SpawnerNametagsModule.Shown::distance));
         return shown.size() > 48 ? shown.subList(0, 48) : shown;
      } else {
         return shown;
      }
   }

   private List<SpawnerNametagsModule.Shown> mobileInRange(MinecraftClient mc) {
      this.mobileShown.clear();
      this.mobileCandidates.reset(48);
      if (!this.spawners.isEmpty() && mc.world != null && mc.player != null) {
         Vec3d player = mc.player.getEntityPos();
         double range = this.sRange.get();
         double rangeSq = range * range;

         try {
            LongIterator it = this.spawners.iterator();

            while (it.hasNext()) {
               long packed = it.nextLong();
               double distanceSq = player.squaredDistanceTo(
                  BlockPos.unpackLongX(packed) + 0.5, BlockPos.unpackLongY(packed) + 0.5, BlockPos.unpackLongZ(packed) + 0.5
               );
               if (!(distanceSq > rangeSq)) {
                  double distance = Math.sqrt(distanceSq);
                  SpawnerNametagsModule.Candidate worst = this.mobileCandidates.worst();
                  if (worst == null || Double.compare(distance, worst.distance()) < 0) {
                     BlockPos pos = BlockPos.fromLong(packed);
                     if (mc.world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
                        this.mobileCandidates.offer(new SpawnerNametagsModule.Candidate(pos, distance, distanceSq, spawner));
                     }
                  }
               }
            }

            List<SpawnerNametagsModule.Candidate> nearest = this.mobileCandidates.result();
            nearest.sort(Comparator.comparingDouble(SpawnerNametagsModule.Candidate::distance));

            for (SpawnerNametagsModule.Candidate candidate : nearest) {
               int activation = activationRange(candidate.spawner());
               boolean inside = activation < 0 || candidate.distanceSq() < (double)activation * activation;
               this.mobileShown.add(new SpawnerNametagsModule.Shown(candidate.pos(), candidate.distance(), mobName(candidate.spawner()), activation, inside));
            }

            return this.mobileShown;
         } finally {
            this.mobileCandidates.reset(0);
         }
      } else {
         return this.mobileShown;
      }
   }

   private float fade(double distance) {
      double range = this.sRange.get();
      return (float)Math.max(0.0, Math.min(1.0, (range - distance) / (range * 0.125)));
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && !this.spawners.isEmpty();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.options.hudHidden) {
         List<SpawnerNametagsModule.Shown> shown = this.inRange(mc);
         int accent = WorldText.hudAccent();

         for (int i = shown.size() - 1; i >= 0; i--) {
            SpawnerNametagsModule.Shown spawner = shown.get(i);
            float[] screen = labelAnchor(mc, spawner.pos(), screenW, screenH);
            if (screen != null) {
               String name = spawner.mob() == null ? "Spawner" : spawner.mob() + " Spawner";
               String distance = Math.round(spawner.distance()) + "m";
               String[] runs;
               int[] colors;
               if (this.sRadius.get() && spawner.range() > 0) {
                  runs = new String[]{distance, "  ·  ", spawner.range() + "m range"};
                  colors = new int[]{WorldText.LABEL, WorldText.LABEL, spawner.inside() ? accent : WorldText.LABEL};
               } else {
                  runs = new String[]{distance};
                  colors = new int[]{WorldText.LABEL};
               }

               WorldText.draw(dl, screen[0], screen[1], name, runs, colors, this.fade(spawner.distance()));
            }
         }
      }
   }

   public static float[] labelAnchor(MinecraftClient mc, BlockPos pos, float screenW, float screenH) {
      float[] anchor = WorldLabel.project(mc, new Vec3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5), screenW, screenH);
      if (anchor == null) {
         return null;
      }

      for (int corner = 0; corner < 4; corner++) {
         float[] top = WorldLabel.project(
            mc, new Vec3d(pos.getX() + (corner & 1), pos.getY() + 1.0, pos.getZ() + (corner >> 1)), screenW, screenH
         );
         if (top != null) {
            anchor[1] = Math.min(anchor[1], top[1]);
         }
      }

      return anchor;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sRadius.get()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         List<SpawnerNametagsModule.Shown> shown = this.inRange(mc);
         if (!shown.isEmpty()) {
            int accent = GuiPalette.abgr(WorldText.hudAccent()) & 16777215;

            for (SpawnerNametagsModule.Shown spawner : shown) {
               if (spawner.range() > 0) {
                  float alpha = (spawner.inside() ? 0.8F : 0.4F) * this.fade(spawner.distance());
                  int color = Math.round(alpha * 255.0F) << 24 | accent;
                  if (color >>> 24 >= 4) {
                     drawRing(Vec3d.ofCenter(spawner.pos()), spawner.range(), color);
                  }
               }
            }
         }
      }
   }

   private static void drawRing(Vec3d centre, double radius, int color) {
      Vec3d previous = null;
      boolean mobile = MobilePerformance.active();

      for (int i = 0; i <= 64; i++) {
         double angle = (Math.PI * 2) * i / 64.0;
         Vec3d point = centre.add((mobile ? RING_COS[i] : Math.cos(angle)) * radius, 0.0, (mobile ? RING_SIN[i] : Math.sin(angle)) * radius);
         if (previous != null) {
            GizmoDrawing.line(previous, point, color, 1.5F).ignoreOcclusion();
         }

         previous = point;
      }
   }

   static {
      for (int i = 0; i <= 64; i++) {
         double angle = (Math.PI * 2) * i / 64.0;
         RING_COS[i] = Math.cos(angle);
         RING_SIN[i] = Math.sin(angle);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Candidate(BlockPos pos, double distance, double distanceSq, MobSpawnerBlockEntity spawner) {
   }

   @Environment(EnvType.CLIENT)
   private record Shown(BlockPos pos, double distance, String mob, int range, boolean inside) {
   }
}
