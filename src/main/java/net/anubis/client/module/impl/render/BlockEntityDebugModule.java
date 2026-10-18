package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.anubis.client.esp.BlockBoxCache;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.esp.HorizontalCandidates;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BlockColorListSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.util.BlockEntityLeakTracker;
import net.anubis.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class BlockEntityDebugModule extends ClientModule {
   private static final int SETTINGS_VERSION = 1;
   private static final float FILL_ALPHA = 0.8F;
   private static final Set<String> NEW_IN_TABLE = Set.of("minecraft:jukebox", "minecraft:piston", "minecraft:sticky_piston");
   private final ModeSetting sDetection = this.addSetting("detection", new ModeSetting("Detection", "All", "All", "Below Zero", "Below -20", "Below -30"));
   private final BooleanSetting sBlockFilter = this.addSetting("block-filter", new BooleanSetting("Block Filter", false));
   private final EspStyle style = new EspStyle(this::addSetting, "Boxes", 0.8F, 256.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sCustomTracerColor = this.addSetting("custom-tracer-color", new BooleanSetting("Custom Tracer Color", false));
   private final ColorSetting sTracerColor = this.addSetting("tracer-color", new ColorSetting("Tracer Color", -1));
   private final BlockColorListSetting sBlockColors = this.addSetting(BlockColorListSetting.createForBlockEntityDebug());
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return BlockEntityDebugModule.this.sBlockColors.colorArgb((Block)kind);
      }

      @Override
      public int tracerColor(Object kind) {
         return BlockEntityDebugModule.this.tracerColorOf((Block)kind);
      }
   }, () -> MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getRegistryKey());
   private final LongOpenHashSet spawners = new LongOpenHashSet();
   private List<BlockEntityDebugModule.SpawnerBeam> mobileBeams;
   private HorizontalCandidates<BlockEntityDebugModule.SpawnerBeam> beamCandidates;
   private BlockBoxCache beamBoxes;
   private int seenGeneration = -1;
   private Set<String> scannedIds = Set.of();

   public BlockEntityDebugModule() {
      super("block-entity-debug", "Block Entity Debug", "Renders every leaked block entity as ESP (always-on Netty capture).", ModuleCategory.BASE_RAID);
      this.sCustomTracerColor.visibleWhen(this.sTracers::get);
      this.sTracerColor.visibleWhen(() -> this.sTracers.get() && this.sCustomTracerColor.get());
      this.sDetection.onChange(this::resync);
      this.sBlockFilter.onChange(this::resync);
      this.sBlockColors.onChange(this::onBlockColorsChanged);
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      this.style.shape.set("Boxes");
      this.style.boxSize.set(1.0F);
      this.style.fillAlpha.set(0.8F);
      this.sBlockColors.resetColors();
      int plain = BlockColorListSetting.rgbaToArgb(this.sBlockColors.defaultColor());
      boolean filtered = this.sBlockFilter.get();

      for (BlockColorListSetting.BlockEntry entry : this.sBlockColors.entries()) {
         if (BlockColorListSetting.rgbaToArgb(entry.color()) != plain && (!filtered || NEW_IN_TABLE.contains(entry.id()))) {
            this.sBlockColors.select(entry);
         }
      }
   }

   private int tracerColorOf(Block block) {
      int own = this.sBlockColors.isEnabled(block) ? this.sBlockColors.tracerColorArgb(block) : this.sBlockColors.colorArgb(block);
      if (own == 0) {
         return 0;
      } else {
         return this.sCustomTracerColor.get() ? this.sTracerColor.argb() : own;
      }
   }

   private void onBlockColorsChanged() {
      if (this.isEnabled() && this.sBlockFilter.get() && !this.sBlockColors.enabledIds().equals(this.scannedIds)) {
         this.resync();
      }
   }

   @Override
   protected void onEnable() {
      BlockEntityLeakTracker.recordAdditions(true);
      this.resync();
   }

   @Override
   protected void onDisable() {
      BlockEntityLeakTracker.recordAdditions(false);
      this.layer.clear();
      this.spawners.clear();
      this.mobileBeams = null;
      this.beamCandidates = null;
      this.beamBoxes = null;
   }

   private void resync() {
      if (this.isEnabled()) {
         this.layer.clear();
         this.spawners.clear();
         this.mobileBeams = null;
         this.beamCandidates = null;
         this.beamBoxes = null;
         this.scannedIds = this.sBlockColors.enabledIds();
         this.seenGeneration = BlockEntityLeakTracker.generation();

         while (BlockEntityLeakTracker.pollAdded() != null) {
         }

         for (Entry<BlockPos, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            this.accept(entry.getKey(), entry.getValue().block());
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (BlockEntityLeakTracker.generation() != this.seenGeneration) {
         this.resync();
      } else {
         BlockPos pos;
         while ((pos = BlockEntityLeakTracker.pollAdded()) != null) {
            BlockEntityLeakTracker.Entry entry = BlockEntityLeakTracker.view().get(pos);
            if (entry != null) {
               this.accept(pos, entry.block());
            }
         }
      }
   }

   private void accept(BlockPos pos, Block block) {
      if (this.passesDetection(pos)) {
         if (!this.sBlockFilter.get() || block == Blocks.AIR || this.sBlockColors.isEnabled(block)) {
            this.layer.put(pos.asLong(), block);
            if (block == Blocks.SPAWNER && this.spawners.add(pos.asLong())) {
               this.mobileBeams = null;
            }
         }
      }
   }

   private boolean passesDetection(BlockPos pos) {
      return switch (this.sDetection.get()) {
         case "Below Zero" -> pos.getY() < 0;
         case "Below -20" -> pos.getY() < -20;
         case "Below -30" -> pos.getY() < -30;
         default -> true;
      };
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof BlockUpdateS2CPacket packet) {
         if (BlockEntityLeakTracker.contains(packet.getPos())) {
            BlockState state = packet.getState();
            if (state.isAir() || state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE)) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.sTracers.get()) {
         this.layer.renderTracers(this.style);
      }

      if (!this.spawners.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            Vec3d eye = mc.player.getCameraPosVec(event.partialTick);
            double rangeSq = (double)this.style.range.get() * this.style.range.get();
            int color = this.sBlockColors.colorArgb(Blocks.SPAWNER);
            DrawStyle beam = DrawStyle.stroked(color, this.style.lineWidth.get());
            if (MobilePerformance.active()) {
               this.renderMobileBeams(eye, rangeSq, beam);
            } else {
               this.mobileBeams = null;
               this.beamCandidates = null;
               this.beamBoxes = null;
               LongIterator var8 = this.spawners.iterator();

               while (var8.hasNext()) {
                  long packed = (Long)var8.next();
                  BlockPos pos = BlockPos.fromLong(packed);
                  if (!(eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > rangeSq)) {
                     GizmoDrawing.box(
                           new Box(
                              pos.getX() + 0.3,
                              pos.getY() + 1.0,
                              pos.getZ() + 0.3,
                              pos.getX() + 0.7,
                              pos.getY() + 257.0,
                              pos.getZ() + 0.7
                           ),
                           beam
                        )
                        .ignoreOcclusion();
                  }
               }
            }
         }
      }
   }

   private void renderMobileBeams(Vec3d eye, double rangeSq, DrawStyle style) {
      if (this.mobileBeams == null) {
         this.mobileBeams = new ArrayList<>(this.spawners.size());
         LongIterator var5 = this.spawners.iterator();

         while (var5.hasNext()) {
            long packed = (Long)var5.next();
            this.mobileBeams.add(new BlockEntityDebugModule.SpawnerBeam(packed));
         }
      }

      if (this.beamCandidates == null) {
         this.beamCandidates = new HorizontalCandidates<>(
            beamx -> BlockPos.unpackLongX(beamx.position()) + 0.5, beamx -> BlockPos.unpackLongZ(beamx.position()) + 0.5
         );
      }

      if (this.beamBoxes == null) {
         this.beamBoxes = new BlockBoxCache(4096, BlockEntityDebugModule::beamBounds);
      }

      this.beamBoxes.beginFrame(1.0F);

      for (BlockEntityDebugModule.SpawnerBeam beam : this.beamCandidates.select(this.mobileBeams, eye.x, eye.z, Math.sqrt(rangeSq))) {
         long packed = beam.position();
         if (!(eye.squaredDistanceTo(BlockPos.unpackLongX(packed) + 0.5, BlockPos.unpackLongY(packed) + 1.0, BlockPos.unpackLongZ(packed) + 0.5) > rangeSq)) {
            GizmoDrawing.box(this.beamBoxes.box(packed), style).ignoreOcclusion();
         }
      }
   }

   private static Box beamBounds(long packed) {
      int x = BlockPos.unpackLongX(packed);
      int y = BlockPos.unpackLongY(packed);
      int z = BlockPos.unpackLongZ(packed);
      return new Box(x + 0.3, y + 1.0, z + 0.3, x + 0.7, y + 257.0, z + 0.7);
   }

   @Environment(EnvType.CLIENT)
   private record SpawnerBeam(long position) {
   }
}
