package net.anubis.client.module.impl.render;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.basefinder.BaseEvidenceScanner;
import net.anubis.client.basefinder.BaseFinderEntry;
import net.anubis.client.basefinder.BaseFinderHud;
import net.anubis.client.basefinder.ChunkReport;
import net.anubis.client.basefinder.Signal;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.impl.misc.PlayerBypassModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.LevelUtil;
import net.anubis.client.util.PerformanceBudget;
import net.anubis.client.util.ServerBlockLight;
import net.anubis.client.util.TracerEngine;
import net.anubis.client.util.playerbypass2.SimulationSignals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * One module for base hunting: scores every loaded chunk for player activity (placed blocks,
 * rotated deepslate, hand-mined tunnels and shafts, grown amethyst and kelp, lined-up surface
 * builds, players hiding underground, and players nobody can see whose chunks keep ticking),
 * ranks them by probability, and explains each find.
 */
@Environment(EnvType.CLIENT)
public final class BaseFinderModule extends ClientModule implements HudOverlayProvider {
   private static final int RESCAN_DELAY_TICKS = 40;
   private static final int RANK_EVERY_TICKS = 10;
   private static final int UNDERGROUND_DEPTH = 8;
   private static final int MAX_REPORTS = 8192;
   private static final long NOTIFY_COOLDOWN_MS = 1500L;
   /** Chunk-signal score below which Player Bypass's sources are ignored (same as that module). */
   private static final float HIDDEN_MIN_SOURCE = 0.5F;
   /** Summed activity around a peak needed before it counts as a hidden player, and where it reaches full strength. */
   private static final float HIDDEN_MIN_ACTIVITY = 8.0F;
   private static final float HIDDEN_FULL_ACTIVITY = 24.0F;
   /** A pinned (exact) hidden player is not replaced by a vaguer field peak for this long. */
   private static final long PIN_HOLD_MS = 10000L;
   /** Block changes count as someone digging out of sight only this far below the surface... */
   private static final int HIDDEN_CHANGE_DEPTH = 4;
   /** ...and at least this far from you and every player you can see. */
   private static final double HIDDEN_CHANGE_CLEARANCE = 8.0;
   /** More changes than this in one chunk in one tick is an explosion or a command, not a player. */
   private static final int HIDDEN_CHANGE_BURST = 5;
   /** Changes needed in a chunk before it is flagged, and where the signal reaches full strength. */
   private static final int HIDDEN_CHANGE_MIN = 3;
   private static final float HIDDEN_CHANGE_FULL = 8.0F;
   /** A chest opened with no visible player this close was opened by someone you can't see. */
   private static final double HIDDEN_USE_CLEARANCE = 6.0;
   private static final int ENTITY_SCAN_TICKS = 20;
   /** Same-type animals in one chunk before it looks bred; natural herds are 2 to 4. */
   private static final int FARM_MIN_ANIMALS = 10;
   private static final float FARM_FULL_ANIMALS = 24.0F;

   private final NumberSetting threshold = this.addSetting("min-probability", new NumberSetting("Min Probability %", 40.0F, 10.0F, 95.0F).step(5.0F));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 512.0F, 64.0F, 2048.0F).step(16.0F));
   private final BooleanSetting scanBlocks = this.addSetting("placed-blocks", new BooleanSetting("Placed Blocks", true));
   /**
    * Off by default: it only works when the server leaks real light from hidden chunks (ChunkVeil's
    * default sanitize-light does not), and glow berries, trial chambers and lava pockets also light up.
    */
   private final BooleanSetting hiddenLights = this.addSetting("hidden-lights", new BooleanSetting("Hidden Lights (Light Leaks)", false));
   private final BooleanSetting scanTunnels = this.addSetting("tunnels", new BooleanSetting("Tunnels & Shafts", true));
   private final NumberSetting tunnelLength = this.addSetting("tunnel-length", new NumberSetting("Tunnel Length", 8.0F, 5.0F, 15.0F).step(1.0F));
   private final BooleanSetting scanGrowth = this.addSetting("growth", new BooleanSetting("Growth (Amethyst, Kelp)", true));
   private final BooleanSetting scanEntities = this.addSetting("entities", new BooleanSetting("Entities (Frames, Pets, Farms)", true));
   private final BooleanSetting scanPlayers = this.addSetting("live-players", new BooleanSetting("Live Players", true));
   private final BooleanSetting hiddenPlayers = this.addSetting("hidden-players", new BooleanSetting("Hidden Players", true));
   private final NumberSetting playerMemory = this.addSetting("player-memory", new NumberSetting("Player Memory (s)", 120.0F, 10.0F, 600.0F).step(10.0F));
   private final ModeSetting highlight = this.addSetting("highlight", new ModeSetting("Highlight", "Evidence Depth", "Evidence Depth", "Fixed Height", "Off"));
   private final NumberSetting highlightY = this.addSetting("highlight-height", new NumberSetting("Height", 64.0F, -64.0F, 320.0F).step(1.0F));
   private final ColorSetting lowColor = this.addSetting("low-color", new ColorSetting("Low Color", 0xFFFFC14D));
   private final ColorSetting highColor = this.addSetting("high-color", new ColorSetting("High Color", 0xFFFF4D6A));
   private final BooleanSetting dots = this.addSetting("reason-dots", new BooleanSetting("Reason Dots", true));
   private final BooleanSetting panel = this.addSetting("panel", new BooleanSetting("Panel", true));
   private final NumberSetting panelRows = this.addSetting("panel-rows", new NumberSetting("Panel Rows", 5.0F, 1.0F, 12.0F).step(1.0F));
   private final NumberSetting panelX = this.addSetting("panel-x", new NumberSetting("Panel X", 8.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting panelY = this.addSetting("panel-y", new NumberSetting("Panel Y", 8.0F, 0.0F, 2000.0F).step(1.0F));
   private final NumberSetting panelScale = this.addSetting("panel-scale", new NumberSetting("Scale", 1.0F, 0.6F, 2.0F).step(0.05F));
   private final BooleanSetting tracer = this.addSetting("tracer", new BooleanSetting("Tracer To Best", false));
   private final NumberSetting notifyAbove = this.addSetting("notify-above", new NumberSetting("Notify Above %", 60.0F, 10.0F, 100.0F).step(5.0F));

   private final BaseEvidenceScanner scanner = new BaseEvidenceScanner();
   private final BaseFinderHud hud = new BaseFinderHud();
   private final Long2ObjectOpenHashMap<ChunkReport> reports = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Sighting> sightings = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Hidden> hidden = new Long2ObjectOpenHashMap<>();
   private long pinSeen;
   private final LongArrayList pendingChanges = new LongArrayList();
   private final LongArrayList pendingBreakers = new LongArrayList();
   private final LongArrayList pendingOpens = new LongArrayList();
   /** Chunks whose underground the server is hiding (sent as plain deepslate or air). */
   private final LongOpenHashSet masked = new LongOpenHashSet();
   /** Block light exactly as the server sent it, before the client relights it around fake blocks. */
   private final ServerBlockLight serverLight = new ServerBlockLight();
   private Long2ObjectOpenHashMap<EntityEvidence> entityEvidence = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Digging> digging = new Long2ObjectOpenHashMap<>();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private final Long2IntOpenHashMap dirtyAt = new Long2IntOpenHashMap();
   private final LongOpenHashSet announced = new LongOpenHashSet();
   private volatile List<BaseFinderEntry> ranked = List.of();
   private ClientWorld lastLevel;
   private boolean scanning;
   private int ticks;
   private boolean rankDirty = true;
   private long lastNotifyAt;
   private int pendingNotices;
   private BaseFinderEntry pendingBest;

   public BaseFinderModule() {
      super("base-finder", "Base Finder", "Ranks loaded chunks by how likely they hide a base, and shows why.", ModuleCategory.BASE_RAID);
      BooleanSetting notifications = this.usesNotifications();
      this.notifyAbove.visibleWhen(notifications::get);
      this.tunnelLength.visibleWhen(this.scanTunnels::get);
      this.hiddenLights.visibleWhen(this.scanBlocks::get);
      this.playerMemory.visibleWhen(this.scanPlayers::get);
      this.hiddenPlayers.visibleWhen(this.scanPlayers::get);
      this.highlightY.visibleWhen(() -> this.highlight.is("Fixed Height"));
      this.panelRows.visibleWhen(this.panel::get);
      this.panelX.visibleWhen(this.panel::get);
      this.panelY.visibleWhen(this.panel::get);
   }

   @Override
   protected void onEnable() {
      this.startLevel(MinecraftClient.getInstance().world);
   }

   @Override
   protected void onDisable() {
      SimulationSignals.unsubscribe(this);
      this.startLevel(null);
   }

   private void startLevel(ClientWorld level) {
      this.reports.clear();
      this.sightings.clear();
      this.hidden.clear();
      this.pendingChanges.clear();
      this.pendingBreakers.clear();
      this.pendingOpens.clear();
      this.masked.clear();
      this.serverLight.clear();
      this.digging.clear();
      this.entityEvidence = new Long2ObjectOpenHashMap<>();
      this.queue.clear();
      this.queued.clear();
      this.dirtyAt.clear();
      this.announced.clear();
      this.ranked = List.of();
      this.rankDirty = true;
      this.pendingNotices = 0;
      this.pendingBest = null;
      this.lastLevel = level;
      this.scanning = level != null && level.getRegistryKey() != World.END;
      if (this.scanning) {
         this.queueLoadedChunks(level);
      }
   }

   private void queueLoadedChunks(ClientWorld level) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int radius = mc.options.getViewDistance().getValue() + 1;
         ChunkPos center = mc.player.getChunkPos();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (LevelUtil.isLoaded(level, x, z)) {
                  this.enqueue(ChunkPos.toLong(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.scanning && this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (event.packet instanceof ChunkDataS2CPacket chunk) {
         if (level != null) {
            this.serverLight.onChunk(level, chunk.getChunkX(), chunk.getChunkZ(), chunk.getLightData());
         }

         this.enqueue(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
      } else if (event.packet instanceof LightUpdateS2CPacket light && level != null) {
         this.serverLight.onLight(level, light.getChunkX(), light.getChunkZ(), light.getData());
         this.enqueue(ChunkPos.toLong(light.getChunkX(), light.getChunkZ()));
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      if (this.scanning && event.pos != null) {
         this.serverLight.onBlockChanged(event.pos);
         long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
         if (!this.dirtyAt.containsKey(key)) {
            this.dirtyAt.put(key, this.ticks);
         }

         if (this.watchingHidden() && this.outOfSightChange(event.pos, event.oldState, event.newState)) {
            this.pendingChanges.add(event.pos.asLong());
         }
      }
   }

   /** The server tells nearby players who is breaking a block; a breaker you have no entity for is hidden from you. */
   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      if (this.scanning && this.watchingHidden()) {
         if (event.packet instanceof BlockBreakingProgressS2CPacket progress) {
            this.pendingBreakers.add(progress.getPos().asLong());
            this.pendingBreakers.add(progress.getEntityId());
         } else if (event.packet instanceof BlockEventS2CPacket blockEvent && blockEvent.getType() == 1 && blockEvent.getData() > 0) {
            // Chest lids: the server tells everyone nearby when a chest, ender chest or shulker box is opened.
            Block block = blockEvent.getBlock();
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST || block instanceof ShulkerBoxBlock) {
               this.pendingOpens.add(blockEvent.getPos().asLong());
            }
         }
      }
   }

   private boolean watchingHidden() {
      return this.scanPlayers.get() && this.hiddenPlayers.get();
   }

   /** A block mined out, placed or used (door, trapdoor, gate, barrel, lever) underground with nobody you can see near it. */
   private boolean outOfSightChange(BlockPos pos, BlockState was, BlockState now) {
      if (was == null || now == null) {
         return false;
      }

      if (was.getBlock() == now.getBlock()) {
         if (!used(was, now)) {
            return false;
         }
      } else {
         boolean broke = now.isAir() && !was.isAir();
         boolean placed = was.isAir() && !now.isAir();
         if (!broke && !placed || natural(broke ? was : now, placed)) {
            return false;
         }
      }

      // While the server hides a chunk's underground, its "changes" are the server revealing real blocks.
      if (pos.getY() < 16 && this.masked.contains(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4))) {
         return false;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level == null || mc.player == null) {
         return false;
      }

      if (nearFireOrLava(level, pos)) {
         return false;
      }

      WorldChunk chunk = level.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
      if (chunk == null || pos.getY() > chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15) - HIDDEN_CHANGE_DEPTH) {
         return false;
      }

      // Under trees the heightmap is the canopy; only count places the sky does not reach.
      if (level.getLightLevel(LightType.SKY, pos) > 7) {
         return false;
      }

      Vec3d center = Vec3d.ofCenter(pos);
      double clearance = HIDDEN_CHANGE_CLEARANCE * HIDDEN_CHANGE_CLEARANCE;

      for (AbstractClientPlayerEntity player : level.getPlayers()) {
         if (player.squaredDistanceTo(center) < clearance) {
            return false;
         }
      }

      // Zombies and villagers open wooden doors; anything alive right next to the block may have done it.
      return level.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(2.0), e -> !(e instanceof PlayerEntity)).isEmpty();
   }

   /** Lava sets nearby wood, azaleas and moss on fire underground; burnt blocks vanish on their own. */
   private static boolean nearFireOrLava(ClientWorld level, BlockPos pos) {
      BlockPos.Mutable at = new BlockPos.Mutable();

      for (int dx = -3; dx <= 3; dx++) {
         for (int dy = -3; dy <= 3; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
               BlockState state = level.getBlockState(at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
               if (state.isIn(BlockTags.FIRE) || state.isOf(Blocks.LAVA)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /** Doors, trapdoors, gates and barrels opened or closed, or a lever flipped: only hands (or redstone) do that. */
   private static boolean used(BlockState was, BlockState now) {
      Block block = was.getBlock();
      if (block == Blocks.LEVER) {
         return was.get(Properties.POWERED) != now.get(Properties.POWERED);
      }

      boolean openable = block instanceof BarrelBlock || was.isIn(BlockTags.WOODEN_DOORS) || was.isIn(BlockTags.WOODEN_TRAPDOORS) || was.isIn(BlockTags.FENCE_GATES);
      return openable && was.contains(Properties.OPEN) && was.get(Properties.OPEN) != now.get(Properties.OPEN)
         && (!was.contains(Properties.POWERED) || was.get(Properties.POWERED) == now.get(Properties.POWERED));
   }

   /** Changes the world makes by itself: fluids, falling blocks, growth, decay, fire, mobs, pistons. */
   private static boolean natural(BlockState state, boolean placed) {
      Block block = state.getBlock();
      if (!state.getFluidState().isEmpty() || block instanceof FallingBlock || block instanceof InfestedBlock
         || state.isIn(BlockTags.ENDERMAN_HOLDABLE) || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.CROPS) || state.isIn(BlockTags.FIRE)
         || state.isIn(BlockTags.ICE) || state.isIn(BlockTags.SNOW) || state.isIn(BlockTags.CAVE_VINES) || state.isIn(BlockTags.FLOWERS)
         || state.isIn(BlockTags.REPLACEABLE) || state.isIn(BlockTags.SAPLINGS)) {
         return true;
      }

      if (block == Blocks.MOVING_PISTON || block == Blocks.PISTON_HEAD || block == Blocks.POINTED_DRIPSTONE || block == Blocks.SCULK
         || block == Blocks.SCULK_VEIN || block == Blocks.SCULK_SENSOR || block == Blocks.SCULK_SHRIEKER || block == Blocks.GLOW_LICHEN
         || block == Blocks.VINE || block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD
         || block == Blocks.AMETHYST_CLUSTER || block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM || block == Blocks.TURTLE_EGG
         || block == Blocks.SNIFFER_EGG || block == Blocks.FROGSPAWN || block == Blocks.BUBBLE_COLUMN) {
         return true;
      }

      // Lava meeting water makes these on its own.
      return placed && (block == Blocks.COBBLESTONE || block == Blocks.STONE || block == Blocks.OBSIDIAN || block == Blocks.BASALT);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.startLevel(level);
      }

      if (!this.scanning || mc.player == null) {
         return;
      }

      this.ticks++;
      this.flushDirty();
      this.scanQueued(level);
      if (this.scanPlayers.get()) {
         this.trackPlayers(mc, level);
      }

      this.trackHidden(mc, level);
      if (this.ticks % 200 == 0) {
         this.masked.removeIf(k -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)));
         this.serverLight.forgetUnloaded(level);
      }
      if (this.ticks % ENTITY_SCAN_TICKS == 0) {
         this.scanEntities(mc, level);
      }

      if (this.rankDirty || this.ticks % RANK_EVERY_TICKS == 0) {
         this.rank(mc, level);
         this.rankDirty = false;
      }

      this.flushNotices();
   }

   private void flushDirty() {
      if (!this.dirtyAt.isEmpty()) {
         var it = this.dirtyAt.long2IntEntrySet().fastIterator();

         while (it.hasNext()) {
            var entry = it.next();
            if (this.ticks - entry.getIntValue() >= RESCAN_DELAY_TICKS) {
               this.enqueue(entry.getLongKey());
               it.remove();
            }
         }
      }
   }

   private void scanQueued(ClientWorld level) {
      boolean optimized = PerformanceModule.optimizationsEnabled();
      long deadline = optimized ? PerformanceBudget.acquire(PerformanceModule.scanBudgetNanos()) : System.nanoTime() + PerformanceModule.scanBudgetNanos();
      long started = System.nanoTime();
      BaseEvidenceScanner.Options options = new BaseEvidenceScanner.Options(
         this.scanBlocks.get(), this.scanTunnels.get(), this.scanGrowth.get(), this.tunnelLength.getInt(), this.hiddenLights.get()
      );

      try {
         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               WorldChunk chunk = level.getChunkManager().getWorldChunk(cx, cz);
               if (chunk != null) {
                  ChunkReport report = this.scanner.scan(level, chunk, options, this.serverLight.column(level, cx, cz));
                  if (this.scanner.masked()) {
                     this.masked.add(key);
                  } else {
                     this.masked.remove(key);
                  }

                  if (report == null) {
                     this.reports.remove(key);
                  } else {
                     this.reports.put(key, report);
                  }

                  this.rankDirty = true;
               }
            }
         }

         if (this.reports.size() > MAX_REPORTS) {
            this.reports.keySet().removeIf(k -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)));
         }
      } finally {
         if (optimized) {
            PerformanceBudget.record(started);
         }
      }
   }

   private void trackPlayers(MinecraftClient mc, ClientWorld level) {
      long now = System.currentTimeMillis();

      for (AbstractClientPlayerEntity other : level.getPlayers()) {
         if (other == mc.player || other.isSpectator()) {
            continue;
         }

         BlockPos pos = other.getBlockPos();
         int cx = pos.getX() >> 4;
         int cz = pos.getZ() >> 4;
         if (!LevelUtil.isLoaded(level, cx, cz)) {
            continue;
         }

         WorldChunk chunk = level.getChunkManager().getWorldChunk(cx, cz);
         if (chunk != null) {
            int surface = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15);
            if (pos.getY() <= surface - UNDERGROUND_DEPTH) {
               long key = ChunkPos.toLong(cx, cz);
               Sighting was = this.sightings.put(key, new Sighting(other.getGameProfile().name(), now, pos.asLong()));
               if (was == null) {
                  this.rankDirty = true;
               }
            }
         }
      }

      long memory = (long)(this.playerMemory.get() * 1000.0F);
      if (this.sightings.values().removeIf(s -> now - s.seenAt() > memory)) {
         this.rankDirty = true;
      }
   }

   /**
    * Reads Player Bypass's inference (shared with that module when it is on too): chunks outside
    * your simulation distance that keep ticking point at a player loading them, and hidden
    * attackers or item pickups pin one exactly.
    */
   private void trackHidden(MinecraftClient mc, ClientWorld level) {
      if (!this.watchingHidden()) {
         SimulationSignals.unsubscribe(this);
         this.pendingChanges.clear();
         this.pendingBreakers.clear();
         this.pendingOpens.clear();
         this.digging.clear();
         this.dropHidden();
         return;
      }

      if (!SimulationSignals.subscribed(this)) {
         SimulationSignals.subscribe(this);
         this.pinSeen = SimulationSignals.pinTotal();
      }

      SimulationSignals.tick(this);
      long now = System.currentTimeMillis();
      this.collectBreakers(mc, level, now);
      this.collectOpens(level, now);
      this.collectChanges(now);
      // Player Bypass's ring inference only works in the overworld; the two checks above work everywhere.
      if (PlayerBypassModule.netherOrEnd(level)) {
         this.pinSeen = SimulationSignals.pinTotal();
      } else {
         SimulationSignals.pinsSince(this.pinSeen, pin -> {
            BlockPos pos = pin.pos();
            String detail = pin.detail();
            int at = detail.indexOf(" @ ");
            this.markHidden(pos, 1.0F, at < 0 ? detail : detail.substring(0, at), now);
         });
         this.pinSeen = SimulationSignals.pinTotal();
         if (this.ticks % RANK_EVERY_TICKS == 0) {
            this.inferHidden(mc.player, now);
         }
      }

      long memory = (long)(this.playerMemory.get() * 1000.0F);
      this.digging.values().removeIf(d -> now - d.lastAt() > memory);
      if (this.hidden.values().removeIf(h -> now - h.seenAt() > memory)) {
         this.rankDirty = true;
      }
   }

   private void markHidden(BlockPos pos, float strength, String detail, long now) {
      this.hidden.put(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4), new Hidden(strength, detail, now, pos.asLong()));
      this.rankDirty = true;
   }

   private void collectOpens(ClientWorld level, long now) {
      double clearance = HIDDEN_USE_CLEARANCE * HIDDEN_USE_CLEARANCE;

      for (int i = 0; i < this.pendingOpens.size(); i++) {
         BlockPos pos = BlockPos.fromLong(this.pendingOpens.getLong(i));
         Vec3d center = Vec3d.ofCenter(pos);
         boolean seen = false;

         for (AbstractClientPlayerEntity player : level.getPlayers()) {
            if (player.squaredDistanceTo(center) < clearance) {
               seen = true;
               break;
            }
         }

         if (!seen) {
            this.markHidden(pos, 1.0F, "opened " + Registries.BLOCK.getId(level.getBlockState(pos).getBlock()).getPath().replace('_', ' ') + " out of sight", now);
         }
      }

      this.pendingOpens.clear();
   }

   /** Block-breaking progress from an entity id you were never sent: a player mining out of your sight, at any depth. */
   private void collectBreakers(MinecraftClient mc, ClientWorld level, long now) {
      for (int i = 0; i + 1 < this.pendingBreakers.size(); i += 2) {
         int id = (int)this.pendingBreakers.getLong(i + 1);
         if (id != mc.player.getId() && level.getEntityById(id) == null) {
            this.markHidden(BlockPos.fromLong(this.pendingBreakers.getLong(i)), 1.0F, "mining out of sight", now);
         }
      }

      this.pendingBreakers.clear();
   }

   /** Underground blocks mined or placed with nobody visible nearby, grouped by chunk; bursts (explosions) are dropped. */
   private void collectChanges(long now) {
      if (this.pendingChanges.isEmpty()) {
         return;
      }

      Long2IntOpenHashMap perChunk = new Long2IntOpenHashMap();
      Long2ObjectOpenHashMap<BlockPos> latest = new Long2ObjectOpenHashMap<>();

      for (int i = 0; i < this.pendingChanges.size(); i++) {
         BlockPos pos = BlockPos.fromLong(this.pendingChanges.getLong(i));
         long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
         perChunk.addTo(key, 1);
         latest.put(key, pos);
      }

      this.pendingChanges.clear();
      long memory = (long)(this.playerMemory.get() * 1000.0F);
      var it = perChunk.long2IntEntrySet().fastIterator();

      while (it.hasNext()) {
         var entry = it.next();
         if (entry.getIntValue() > HIDDEN_CHANGE_BURST) {
            continue;
         }

         long key = entry.getLongKey();
         Digging was = this.digging.get(key);
         int count = (was == null || now - was.lastAt() > memory ? 0 : was.count()) + entry.getIntValue();
         this.digging.put(key, new Digging(count, now));
         if (count >= HIDDEN_CHANGE_MIN) {
            Hidden current = this.hidden.get(key);
            float strength = Math.min(1.0F, 0.4F + 0.6F * count / HIDDEN_CHANGE_FULL);
            if (current == null || current.strength() <= strength || now - current.seenAt() > PIN_HOLD_MS) {
               this.markHidden(latest.get(key), strength, count + " blocks dug, placed or used out of sight", now);
            }
         }
      }
   }

   /**
    * Entities only players leave behind: item frames, armor stands and paintings; tamed, named or
    * leashed mobs; and more same-type animals in one chunk than nature spawns (a breeding pen).
    */
   private void scanEntities(MinecraftClient mc, ClientWorld level) {
      Long2ObjectOpenHashMap<EntityEvidence> next = new Long2ObjectOpenHashMap<>();
      if (this.scanEntities.get()) {
         Long2ObjectOpenHashMap<Map<String, Integer>> herds = new Long2ObjectOpenHashMap<>();

         for (Entity entity : level.getEntities()) {
            if (entity instanceof PlayerEntity) {
               continue;
            }

            BlockPos pos = entity.getBlockPos();
            long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity || entity instanceof PaintingEntity) {
               next.computeIfAbsent(key, k -> new EntityEvidence()).placed(pos);
            } else if (this.pet(entity, mc)) {
               next.computeIfAbsent(key, k -> new EntityEvidence()).pet(entity, pos);
            }

            if (entity instanceof AnimalEntity && !(entity instanceof AxolotlEntity) && !(entity instanceof BeeEntity)) {
               herds.computeIfAbsent(key, k -> new HashMap<>()).merge(Registries.ENTITY_TYPE.getId(entity.getType()).getPath(), 1, Integer::sum);
            }
         }

         var it = herds.long2ObjectEntrySet().fastIterator();

         while (it.hasNext()) {
            var entry = it.next();

            for (var herd : entry.getValue().entrySet()) {
               if (herd.getValue() >= FARM_MIN_ANIMALS) {
                  next.computeIfAbsent(entry.getLongKey(), k -> new EntityEvidence()).farm(herd.getKey(), herd.getValue());
               }
            }
         }
      }

      if (!next.isEmpty() || !this.entityEvidence.isEmpty()) {
         this.rankDirty = true;
      }

      this.entityEvidence = next;
   }

   /** Tamed by someone else, name-tagged, or on a lead (wandering traders' llamas aside). */
   private boolean pet(Entity entity, MinecraftClient mc) {
      if (!(entity instanceof LivingEntity)) {
         return false;
      }

      if (entity instanceof Tameable tameable) {
         LazyEntityReference<?> owner = tameable.getOwnerReference();
         if (owner != null) {
            return !owner.getUuid().equals(mc.player.getUuid());
         }
      }

      return entity.hasCustomName() || entity instanceof Leashable leashable && leashable.isLeashed() && !(entity instanceof TraderLlamaEntity);
   }

   private void dropHidden() {
      if (!this.hidden.isEmpty()) {
         this.hidden.clear();
         this.rankDirty = true;
      }
   }

   /** The same field Player Bypass builds: each ticking chunk votes for every chunk within simulation distance of it. */
   private void inferHidden(ClientPlayerEntity self, long now) {
      long nanos = SimulationSignals.now();
      int sim = SimulationSignals.simulation();
      int pcx = self.getBlockPos().getX() >> 4;
      int pcz = self.getBlockPos().getZ() >> 4;
      int start = SimulationSignals.ringStart();
      int view = SimulationSignals.view();
      int side = 2 * sim + 1;
      float span = sim * 1.4143F + 1.0F;
      float[] weights = new float[side * side];

      for (int dx = -sim; dx <= sim; dx++) {
         for (int dz = -sim; dz <= sim; dz++) {
            weights[(dx + sim) * side + dz + sim] = 1.0F - 0.3F * (float)Math.sqrt(dx * dx + dz * dz) / span;
         }
      }

      Long2FloatOpenHashMap field = new Long2FloatOpenHashMap();
      Long2FloatOpenHashMap raw = new Long2FloatOpenHashMap();
      var sources = SimulationSignals.chunks().long2ObjectEntrySet().iterator();

      while (sources.hasNext()) {
         var entry = sources.next();
         long key = entry.getLongKey();
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         int d = SimulationSignals.distance(cx, cz, pcx, pcz);
         float score = entry.getValue().score(nanos);
         if (d >= start && d <= view && score >= HIDDEN_MIN_SOURCE) {
            for (int dx = -sim; dx <= sim; dx++) {
               for (int dz = -sim; dz <= sim; dz++) {
                  long area = ChunkPos.toLong(cx + dx, cz + dz);
                  field.addTo(area, score * weights[(dx + sim) * side + dz + sim]);
                  raw.addTo(area, score);
               }
            }
         }
      }

      var frozen = SimulationSignals.frozen().iterator();

      while (frozen.hasNext()) {
         long key = frozen.nextLong();
         field.remove(key);
         raw.remove(key);
      }

      var cells = field.long2FloatEntrySet().iterator();

      while (cells.hasNext()) {
         var cell = cells.next();
         long key = cell.getLongKey();
         float activity = raw.get(key);
         if (activity >= HIDDEN_MIN_ACTIVITY && isPeak(field, key, cell.getFloatValue(), sim)) {
            Hidden was = this.hidden.get(key);
            if (was == null || was.pos() == 0L || now - was.seenAt() > PIN_HOLD_MS) {
               float strength = Math.min(1.0F, activity / HIDDEN_FULL_ACTIVITY);
               this.hidden.put(key, new Hidden(strength, "ticking chunks, activity " + Math.round(activity), now, 0L));
               this.rankDirty |= was == null;
            }
         }
      }
   }

   private static boolean isPeak(Long2FloatOpenHashMap field, long key, float value, int reach) {
      int cx = ChunkPos.getPackedX(key);
      int cz = ChunkPos.getPackedZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if (dx != 0 || dz != 0) {
               long other = ChunkPos.toLong(cx + dx, cz + dz);
               float near = field.get(other);
               if (near > value || near == value && other < key) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private float hiddenStrength(Hidden hidden, long now) {
      if (hidden == null || !this.scanPlayers.get() || !this.hiddenPlayers.get()) {
         return 0.0F;
      }

      float memory = this.playerMemory.get() * 1000.0F;
      return hidden.strength() * Math.max(0.0F, 1.0F - (now - hidden.seenAt()) / memory * 0.5F);
   }

   private float playerStrength(Sighting sighting, long now) {
      if (sighting == null || !this.scanPlayers.get()) {
         return 0.0F;
      }

      float memory = this.playerMemory.get() * 1000.0F;
      return Math.max(0.0F, 1.0F - (now - sighting.seenAt()) / memory * 0.5F);
   }

   private void rank(MinecraftClient mc, ClientWorld level) {
      boolean[] groups = new boolean[Signal.Group.values().length];
      groups[Signal.Group.BLOCKS.ordinal()] = this.scanBlocks.get();
      groups[Signal.Group.TUNNELS.ordinal()] = this.scanTunnels.get();
      groups[Signal.Group.GROWTH.ordinal()] = this.scanGrowth.get();
      groups[Signal.Group.PLAYERS.ordinal()] = this.scanPlayers.get();
      groups[Signal.Group.ENTITIES.ordinal()] = this.scanEntities.get();
      float min = this.threshold.get() / 100.0F;
      double maxDistance = this.range.get();
      Vec3d eye = mc.player.getEyePos();
      float yaw = mc.player.getYaw();
      long now = System.currentTimeMillis();
      LongOpenHashSet keys = new LongOpenHashSet(this.reports.keySet());
      keys.addAll(this.sightings.keySet());
      keys.addAll(this.hidden.keySet());
      keys.addAll(this.entityEvidence.keySet());
      List<BaseFinderEntry> out = new ArrayList<>();
      var it = keys.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         double centerX = (cx << 4) + 8.0;
         double centerZ = (cz << 4) + 8.0;
         double distance = Math.hypot(centerX - eye.x, centerZ - eye.z);
         if (distance > maxDistance) {
            continue;
         }

         ChunkReport report = this.reports.get(key);
         Sighting sighting = this.sightings.get(key);
         Hidden hiddenHere = this.hidden.get(key);
         if (report == null) {
            report = new ChunkReport(key);
         }

         EntityEvidence entities = this.entityEvidence.get(key);
         ChunkReport.Live live = new ChunkReport.Live()
            .put(Signal.PLAYER, this.playerStrength(sighting, now), sighting == null ? null : sighting.name())
            .put(Signal.HIDDEN_PLAYER, this.hiddenStrength(hiddenHere, now), hiddenHere == null ? null : hiddenHere.detail());
         if (entities != null) {
            entities.into(live);
         }

         float p = report.probability(groups, live);
         if (p < min) {
            continue;
         }

         List<ChunkReport.Reason> reasons = report.reasons(groups, live);
         Signal top = reasons.get(0).signal();
         long anchor = report.anchor;
         if (live.get(Signal.PLAYER) > 0.0F && (anchor == 0L || top == Signal.PLAYER)) {
            anchor = sighting.pos();
         } else if (live.get(Signal.HIDDEN_PLAYER) > 0.0F && hiddenHere.pos() != 0L && (anchor == 0L || top == Signal.HIDDEN_PLAYER)) {
            anchor = hiddenHere.pos();
         } else if (entities != null && entities.anchor != 0L && (anchor == 0L || top.group == Signal.Group.ENTITIES)) {
            anchor = entities.anchor;
         }

         BlockPos at = anchor == 0L ? BlockPos.ofFloored(centerX, eye.y, centerZ) : BlockPos.fromLong(anchor);
         double targetYaw = Math.toDegrees(Math.atan2(-(centerX - eye.x), centerZ - eye.z));
         float bearing = MathHelper.wrapDegrees((float)targetYaw - yaw);
         float t = MathHelper.clamp((p - min) / Math.max(0.05F, 1.0F - min), 0.0F, 1.0F);
         int argb = ColorHelper.lerp(t, this.lowColor.argb(), this.highColor.argb()) | 0xFF000000;
         out.add(new BaseFinderEntry(key, (int)centerX, (int)centerZ, p, List.copyOf(reasons), Vec3d.ofCenter(at), distance, bearing, argb));
         if (p * 100.0F >= this.notifyAbove.get() && this.announced.add(key)) {
            this.pendingNotices++;
            if (this.pendingBest == null || p > this.pendingBest.probability()) {
               this.pendingBest = out.get(out.size() - 1);
            }
         }
      }

      out.sort(Comparator.comparingDouble(BaseFinderEntry::probability).reversed().thenComparingDouble(BaseFinderEntry::distance));
      this.ranked = List.copyOf(out);
   }

   private void flushNotices() {
      if (this.pendingNotices == 0) {
         return;
      }

      if (!this.notificationsOn()) {
         this.pendingNotices = 0;
         this.pendingBest = null;
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.lastNotifyAt >= NOTIFY_COOLDOWN_MS && this.pendingBest != null) {
         BaseFinderEntry best = this.pendingBest;
         ModuleChat.Body body = ModuleChat.body()
            .good(Math.round(best.probability() * 100.0F) + "%")
            .text(" chunk at ")
            .coords(best.blockX(), (int)best.anchor().y, best.blockZ())
            .text(": " + best.topReason());
         if (this.pendingNotices > 1) {
            body.text(" (+" + (this.pendingNotices - 1) + " more)");
         }

         ModuleChat.send(this, body);
         this.pendingNotices = 0;
         this.pendingBest = null;
         this.lastNotifyAt = now;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      List<BaseFinderEntry> entries = this.ranked;
      if (mc.world == null || entries.isEmpty()) {
         return;
      }

      if (!this.highlight.is("Off")) {
         FoglessLines lines = FoglessLines.forFrame(event.partialTick);
         boolean fixed = this.highlight.is("Fixed Height");

         for (BaseFinderEntry entry : entries) {
            double y = fixed ? this.highlightY.get() : Math.floor(entry.anchor().y) + 0.02;
            double x0 = (ChunkPos.getPackedX(entry.chunk()) << 4) + 0.5;
            double z0 = (ChunkPos.getPackedZ(entry.chunk()) << 4) + 0.5;
            double x1 = x0 + 15.0;
            double z1 = z0 + 15.0;
            int fillAlpha = Math.round(24.0F + 56.0F * entry.probability());
            Vec3d[] outline = new Vec3d[]{new Vec3d(x0, y, z0), new Vec3d(x1, y, z0), new Vec3d(x1, y, z1), new Vec3d(x0, y, z1)};
            GizmoDrawing.collect(new Tile(outline, ColorHelper.withAlpha(fillAlpha, entry.argb()))).ignoreOcclusion();
            int edge = ColorHelper.withAlpha(200, entry.argb());

            for (int i = 0; i < 4; i++) {
               lines.line(outline[i], outline[(i + 1) % 4], edge, 1.5F);
            }
         }
      }

      if (this.tracer.get()) {
         BaseFinderEntry best = entries.get(0);
         TracerEngine.draw(best.anchor(), best.argb());
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.scanning && (this.panel.get() || this.dots.get() && !this.ranked.isEmpty());
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      int hiddenBelow = this.masked.size();
      BaseFinderHud.Style style = new BaseFinderHud.Style(
         this.panel.get(), this.dots.get(), this.panelX.get(), this.panelY.get(), this.panelRows.getInt(), this.panelScale.get(),
         hiddenBelow == 0 ? null : "Server hides underground in " + hiddenBelow + (hiddenBelow == 1 ? " chunk" : " chunks")
      );
      this.hud.render(dl, screenW, screenH, this.ranked, style);
   }

   @Environment(EnvType.CLIENT)
   private record Sighting(String name, long seenAt, long pos) {
   }

   /** A player inferred but not seen; {@code pos} is 0 when only the chunk is known. */
   @Environment(EnvType.CLIENT)
   private record Hidden(float strength, String detail, long seenAt, long pos) {
   }

   @Environment(EnvType.CLIENT)
   private record Digging(int count, long lastAt) {
   }

   @Environment(EnvType.CLIENT)
   private static final class EntityEvidence {
      int placed;
      int pets;
      String petName;
      int farmed;
      String farmType;
      long anchor;

      void placed(BlockPos pos) {
         this.placed++;
         this.anchor = pos.asLong();
      }

      void pet(Entity entity, BlockPos pos) {
         if (this.pets++ == 0 || entity.hasCustomName()) {
            this.petName = entity.hasCustomName()
               ? entity.getCustomName().getString()
               : Registries.ENTITY_TYPE.getId(entity.getType()).getPath().replace('_', ' ');
         }

         if (this.placed == 0) {
            this.anchor = pos.asLong();
         }
      }

      void farm(String type, int count) {
         if (count > this.farmed) {
            this.farmed = count;
            this.farmType = type.replace('_', ' ');
         }
      }

      void into(ChunkReport.Live live) {
         if (this.placed > 0) {
            live.put(Signal.PLACED_ENTITIES, Signal.PLACED_ENTITIES.strengthFor(this.placed), "x" + this.placed);
         }

         if (this.pets > 0) {
            live.put(Signal.PETS, 1.0F, this.pets > 1 ? this.petName + " +" + (this.pets - 1) : this.petName);
         }

         if (this.farmed > 0) {
            float strength = 0.8F + 0.2F * (this.farmed - FARM_MIN_ANIMALS) / (FARM_FULL_ANIMALS - FARM_MIN_ANIMALS);
            live.put(Signal.ANIMAL_FARM, strength, this.farmed + " " + this.farmType);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Tile(Vec3d[] outline, int color) implements Gizmo {
      public void draw(GizmoDrawer primitives, float alpha) {
         primitives.addPolygon(this.outline, ColorHelper.withAlpha(Math.round(ColorHelper.getAlpha(this.color) * alpha), this.color));
      }
   }
}
