package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Set;
import java.util.Map.Entry;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketAppliedEvent;
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
import net.anubis.client.util.LightLeakScanner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class BlockEntityDebugModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int SETTINGS_VERSION = 1;
   private static final int SUMMARY_TICKS = 1200;
   private static final float FILL_ALPHA = 0.8F;
   private static final int MAX_CHAT_PER_TICK = 5;
   private static final int MAX_NBT_CHARS = 200;
   private static final Set<String> NEW_IN_TABLE = Set.of("minecraft:jukebox", "minecraft:piston", "minecraft:sticky_piston");
   private final ModeSetting sDetection = this.addSetting("detection", new ModeSetting("Detection", "All", "All", "Below Zero", "Below -20", "Below -30"));
   private final BooleanSetting sBlockFilter = this.addSetting("block-filter", new BooleanSetting("Block Filter", false));
   private final BooleanSetting sChat = this.addSetting("chat", new BooleanSetting("Chat Notifications", false));
   private final BooleanSetting sShowNbt = this.addSetting("show-nbt", new BooleanSetting("Show NBT", true));
   private final BooleanSetting sHints = this.addSetting("hidden-hints", new BooleanSetting("Hidden Block Hints", true));
   private final BooleanSetting sLightLeaks = this.addSetting("light-leaks", new BooleanSetting("Light Leaks", true));
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
   private final LightLeakScanner lightScanner = new LightLeakScanner();
   private int ticks;
   private String lastSummary = "";
   private int seenGeneration = -1;
   private Set<String> scannedIds = Set.of();

   public BlockEntityDebugModule() {
      super(
         "block-entity-debug",
         "Block Entity Debug",
         "Renders every leaked block entity as ESP (always-on Netty capture), plus blocks the server hides but still gives away through chest/crafter events, sounds and light.",
         ModuleCategory.BASE_RAID
      );
      this.sShowNbt.visibleWhen(this.sChat::get);
      this.sLightLeaks.visibleWhen(this.sHints::get);
      this.sHints.onChange(this::resync);
      this.sLightLeaks.onChange(this::restartLightScan);
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
      this.restartLightScan();
   }

   @Override
   protected void onDisable() {
      BlockEntityLeakTracker.recordAdditions(false);
      this.lightScanner.clear();
      this.layer.clear();
      this.spawners.clear();
   }

   private void restartLightScan() {
      this.lightScanner.clear();
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.isEnabled() && this.lightLeaksOn() && mc.world != null && mc.player != null) {
         this.lightScanner.queueLoaded(mc.world, mc.player.getBlockPos(), mc.options.getClampedViewDistance());
      }
   }

   private boolean lightLeaksOn() {
      return this.sHints.get() && this.sLightLeaks.get();
   }

   private void resync() {
      if (this.isEnabled()) {
         this.layer.clear();
         this.spawners.clear();
         this.scannedIds = this.sBlockColors.enabledIds();
         this.seenGeneration = BlockEntityLeakTracker.generation();

         while (BlockEntityLeakTracker.pollAdded() != null) {
         }

         for (Entry<BlockPos, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            this.accept(entry.getKey(), entry.getValue());
         }
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (this.lightLeaksOn()) {
         this.lightScanner.onPacketApplied(event.packet);
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.lightLeaksOn() && mc.world != null) {
         this.lightScanner.tick(mc.world);
      }

      if (++this.ticks % 1200 == 0) {
         this.logSources();
      }

      if (BlockEntityLeakTracker.generation() != this.seenGeneration) {
         this.resync();
      } else {
         int announced = 0;
         int more = 0;

         BlockPos pos;
         while ((pos = BlockEntityLeakTracker.pollAdded()) != null) {
            BlockEntityLeakTracker.Entry entry = BlockEntityLeakTracker.view().get(pos);
            if (entry != null && this.accept(pos, entry) && this.sChat.get()) {
               if (announced < 5) {
                  this.announce(pos, entry);
                  announced++;
               } else {
                  more++;
               }
            }
         }

         if (more > 0) {
            ModuleChat.send(this, ModuleChat.body().text("+").value(more).text(" more"));
         }
      }
   }

   private void logSources() {
      String summary = "chunk="
         + BlockEntityLeakTracker.count(BlockEntityLeakTracker.Source.CHUNK)
         + " data="
         + BlockEntityLeakTracker.count(BlockEntityLeakTracker.Source.DATA)
         + " event="
         + BlockEntityLeakTracker.count(BlockEntityLeakTracker.Source.EVENT)
         + " sound="
         + BlockEntityLeakTracker.count(BlockEntityLeakTracker.Source.SOUND)
         + " light="
         + BlockEntityLeakTracker.count(BlockEntityLeakTracker.Source.LIGHT);
      if (!summary.equals(this.lastSummary)) {
         this.lastSummary = summary;
         LOGGER.info("[Anubis] Block Entity Debug sources: {}", summary);
      }
   }

   private boolean accept(BlockPos pos, BlockEntityLeakTracker.Entry entry) {
      if (entry.real() || this.sHints.get() && (entry.source() != BlockEntityLeakTracker.Source.LIGHT || this.sLightLeaks.get())) {
         Block block = entry.block();
         if (!this.passesDetection(pos)) {
            return false;
         }

         if (this.sBlockFilter.get() && block != Blocks.AIR && !this.sBlockColors.isEnabled(block)) {
            return false;
         }

         this.layer.put(pos.asLong(), block);
         if (block == Blocks.SPAWNER) {
            this.spawners.add(pos.asLong());
         }

         return true;
      } else {
         return false;
      }
   }

   private void announce(BlockPos pos, BlockEntityLeakTracker.Entry entry) {
      NbtCompound tag = entry.tag();
      ModuleChat.Body body = ModuleChat.body().name(entry.block() == Blocks.AIR ? "Hidden block" : entry.block().getName().getString());
      if (!entry.real()) {
         body.text(" (from " + entry.source().name().toLowerCase() + ")");
      }

      String mob = tag == null
         ? null
         : tag.getCompound("SpawnData").flatMap(data -> data.getCompound("entity")).flatMap(entity -> entity.getString("id")).orElse(null);
      if (mob != null) {
         body.text(" (").value(mob.replace("minecraft:", "")).text(")");
      }

      body.text(" at ").coords(pos.getX(), pos.getY(), pos.getZ());
      if (this.sShowNbt.get() && tag != null) {
         String snbt = tag.toString();
         if (snbt.length() > 200) {
            snbt = snbt.substring(0, 200) + "...";
         }

         Text full = NbtHelper.toPrettyPrintedText(tag);
         body.append(Text.literal(" " + snbt).styled(style -> style.withHoverEvent(new ShowText(full))));
      }

      ModuleChat.send(this, body);
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
         if (BlockEntityLeakTracker.containsReal(packet.getPos())) {
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
            LongIterator var8 = this.spawners.iterator();

            while (var8.hasNext()) {
               long packed = (Long)var8.next();
               BlockPos pos = BlockPos.fromLong(packed);
               if (!(eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > rangeSq)) {
                  GizmoDrawing.box(new Box(pos.getX() + 0.3, pos.getY() + 1.0, pos.getZ() + 0.3, pos.getX() + 0.7, pos.getY() + 257.0, pos.getZ() + 0.7), beam)
                     .ignoreOcclusion();
               }
            }
         }
      }
   }
}
