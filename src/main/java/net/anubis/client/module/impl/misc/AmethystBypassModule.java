package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.AmethystLight;
import net.anubis.client.util.GeodeGlowScan;
import net.anubis.client.util.LevelUtil;
import net.anubis.client.util.PerformanceBudget;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

@Environment(EnvType.CLIENT)
public final class AmethystBypassModule extends ClientModule {
   private static final int COLOR = -3636481;
   private static final float LINE_WIDTH = 1.5F;
   private static final int RESCAN_TICKS = 20;
   private static final long BUDGET_NANOS = 2000000L;
   private static final String ANBS_SCAN = "ANBS+ Scan";
   private static final int MAX_GLOW_MARKS = 200;
   private static final int FULL_REFRESH_TICKS = 600;
   private final ModeSetting method = this.addSetting("method", new ModeSetting("Method", "Bypass", "Bypass", "Light", "ANBS+ Scan"));
   private final NumberSetting minCells = this.addSetting("min-cells", new NumberSetting("Min Cells", 12.0F, 1.0F, 100.0F).step(1.0F));
   private final BooleanSetting tracer = this.addSetting("tracer", new BooleanSetting("Tracer", false));
   private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
   private final LongOpenHashSet found = new LongOpenHashSet();
   private final LongOpenHashSet bypassScanned = new LongOpenHashSet();
   private final Long2ObjectOpenHashMap<BlockPos> bypassMarkers = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<long[]> chunkHits = new Long2ObjectOpenHashMap();
   private final LongOpenHashSet lightScanned = new LongOpenHashSet();
   private final LongOpenHashSet chunkFound = new LongOpenHashSet();
   private boolean hitsChanged;
   private int sinceRefresh;
   private final GeodeGlowScan glow = new GeodeGlowScan();
   private final LongArrayList glowCells = new LongArrayList();
   private long[] hits = new long[0];
   private int tick;

   public AmethystBypassModule() {
      super("amethyst-bypass", "Amethyst Bypass", "Shows amethyst geodes hidden by anti-xray.", ModuleCategory.BASE_RAID);
      this.method.onChange(this::restart);
      this.minCells.visibleWhen(() -> this.method.is("ANBS+ Scan"));
   }

   @Override
   protected void onEnable() {
      this.restart();
   }

   @Override
   protected void onDisable() {
      this.restart();
   }

   private void restart() {
      this.queue.clear();
      this.found.clear();
      this.bypassScanned.clear();
      this.bypassMarkers.clear();
      this.chunkHits.clear();
      this.lightScanned.clear();
      this.hitsChanged = false;
      this.sinceRefresh = 0;
      this.hits = new long[0];
      this.tick = 0;
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != null && mc.player != null) {
         boolean bypass = this.method.is("Bypass");
         if (this.queue.isEmpty()) {
            if (++this.tick < 20) {
               return;
            }

            this.tick = 0;
            ChunkPos center = mc.player.getChunkPos();
            int radius = mc.options.getViewDistance().getValue();
            if (bypass) {
               this.bypassScanned
                  .removeIf(keyx -> !inRange(keyx, center, radius) || !LevelUtil.isLoaded(level, ChunkPos.getPackedX(keyx), ChunkPos.getPackedZ(keyx)));
               this.bypassMarkers.keySet().removeIf(keyx -> !this.bypassScanned.contains(keyx));

               for (int x = center.x - radius; x <= center.x + radius; x++) {
                  for (int z = center.z - radius; z <= center.z + radius; z++) {
                     long key = ChunkPos.toLong(x, z);
                     if (LevelUtil.isLoaded(level, x, z) && !this.bypassScanned.contains(key)) {
                        this.queue.add(new ChunkPos(x, z));
                     }
                  }
               }
            } else {
               if (++this.sinceRefresh * 20 >= 600) {
                  this.sinceRefresh = 0;
                  this.lightScanned.clear();
               }

               this.hitsChanged = this.hitsChanged
                  | this.chunkHits
                     .keySet()
                     .removeIf(keyx -> !inRange(keyx, center, radius) || !LevelUtil.isLoaded(level, ChunkPos.getPackedX(keyx), ChunkPos.getPackedZ(keyx)));
               this.lightScanned.removeIf(keyx -> !inRange(keyx, center, radius));

               for (int x = center.x - radius; x <= center.x + radius; x++) {
                  for (int z = center.z - radius; z <= center.z + radius; z++) {
                     if (LevelUtil.isLoaded(level, x, z) && !this.lightScanned.contains(ChunkPos.toLong(x, z))) {
                        this.queue.add(new ChunkPos(x, z));
                     }
                  }
               }
            }
         }

         boolean optimized = PerformanceModule.optimizationsEnabled();
         long started = System.nanoTime();
         long deadline = optimized ? PerformanceBudget.acquire(2000000L) : started + 2000000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            ChunkPos pos = this.queue.poll();
            if (LevelUtil.isLoaded(level, pos.x, pos.z)) {
               WorldChunk chunk = level.getChunk(pos.x, pos.z);
               if (bypass) {
                  collectPaletteLeak(chunk, this.bypassMarkers);
                  this.bypassScanned.add(pos.toLong());
               } else {
                  this.chunkFound.clear();
                  if (this.method.is("ANBS+ Scan")) {
                     this.collectGlow(level, chunk, this.chunkFound);
                  } else {
                     AmethystLight.scanSources(level, chunk, (packed, light) -> this.chunkFound.add(packed));
                  }

                  long key = pos.toLong();
                  long[] previous = (long[])this.chunkHits.get(key);
                  long[] now = this.chunkFound.toLongArray();
                  if (previous != null && now.length < previous.length) {
                     this.lightScanned.add(key);
                  } else {
                     if (now.length == 0) {
                        if (this.chunkHits.remove(key) != null) {
                           this.hitsChanged = true;
                        }
                     } else if (previous == null || !Arrays.equals(previous, now)) {
                        this.chunkHits.put(key, now);
                        this.hitsChanged = true;
                     }

                     this.lightScanned.add(key);
                  }
               }
            }
         }

         if (optimized) {
            PerformanceBudget.record(started);
         }

         if (bypass) {
            if (this.queue.isEmpty()) {
               this.hits = markersToLongArray(this.bypassMarkers);
            }
         } else if (this.hitsChanged) {
            this.hitsChanged = false;
            this.found.clear();
            ObjectIterator var22 = this.chunkHits.values().iterator();

            while (var22.hasNext()) {
               long[] chunkMarks = (long[])var22.next();

               for (long mark : chunkMarks) {
                  this.found.add(mark);
               }
            }

            this.hits = this.found.toLongArray();
         }
      }
   }

   private void collectGlow(ClientWorld level, WorldChunk chunk, LongOpenHashSet out) {
      this.glowCells.clear();
      if (this.glow.scan(level, chunk, this.glowCells::add) >= this.minCells.getInt()) {
         int marks = Math.min(this.glowCells.size(), 200);

         for (int i = 0; i < marks; i++) {
            out.add(this.glowCells.getLong(i));
         }
      }
   }

   private static boolean inRange(long chunkKey, ChunkPos center, int radius) {
      int x = ChunkPos.getPackedX(chunkKey);
      int z = ChunkPos.getPackedZ(chunkKey);
      return Math.abs(x - center.x) <= radius && Math.abs(z - center.z) <= radius;
   }

   private static long[] markersToLongArray(Long2ObjectOpenHashMap<BlockPos> markers) {
      long[] out = new long[markers.size()];
      int i = 0;
      ObjectIterator var3 = markers.values().iterator();

      while (var3.hasNext()) {
         BlockPos marker = (BlockPos)var3.next();
         out[i++] = marker.asLong();
      }

      return out;
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (!this.method.is("Bypass")) {
         if (event.packet instanceof ChunkDataS2CPacket chunk) {
            this.dirtyAround(chunk.getChunkX(), chunk.getChunkZ());
         } else if (event.packet instanceof LightUpdateS2CPacket light) {
            this.dirtyAround(light.getChunkX(), light.getChunkZ());
         } else if (event.packet instanceof BlockUpdateS2CPacket update) {
            this.dirtyAround(update.getPos().getX() >> 4, update.getPos().getZ() >> 4);
         } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket sectionUpdate) {
            sectionUpdate.visitUpdates((pos, state) -> this.dirtyAround(pos.getX() >> 4, pos.getZ() >> 4));
         }
      } else {
         if (event.packet instanceof ChunkDataS2CPacket chunk) {
            this.dirtyBypassChunk(chunk.getChunkX(), chunk.getChunkZ());
         } else if (event.packet instanceof BlockUpdateS2CPacket update) {
            this.dirtyBypassChunk(update.getPos().getX() >> 4, update.getPos().getZ() >> 4);
         } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket sectionUpdate) {
            sectionUpdate.visitUpdates((pos, state) -> this.dirtyBypassChunk(pos.getX() >> 4, pos.getZ() >> 4));
         }
      }
   }

   private void dirtyAround(int chunkX, int chunkZ) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            this.lightScanned.remove(ChunkPos.toLong(chunkX + dx, chunkZ + dz));
         }
      }
   }

   private void dirtyBypassChunk(int chunkX, int chunkZ) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      if (this.bypassScanned.remove(key)) {
         this.bypassMarkers.remove(key);
      }
   }

   private static void collectPaletteLeak(WorldChunk chunk, Long2ObjectOpenHashMap<BlockPos> out) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = chunk.getBottomSectionCoord();
      ChunkPos pos = chunk.getPos();

      for (int i = 0; i < sections.length; i++) {
         ChunkSection section = sections[i];
         if (section != null) {
            PalettedContainer<BlockState> states = section.getBlockStateContainer();
            if (!states.hasAny(state -> false) && states.hasAny(AmethystBypassModule::isBud)) {
               int baseY = (minSectionY + i) * 16;
               BlockPos marker = pickMarker(chunk, section, baseY, pos);
               out.put(pos.toLong(), marker != null ? marker : new BlockPos(pos.getStartX() + 8, baseY + 8, pos.getStartZ() + 8));
               return;
            }
         }
      }
   }

   private static BlockPos pickMarker(WorldChunk chunk, ChunkSection section, int baseY, ChunkPos pos) {
      Mutable neighbour = new Mutable();
      BlockPos best = null;
      int bestScore = Integer.MAX_VALUE;

      for (int y = 0; y < 16; y++) {
         int worldY = baseY + y;

         for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
               if (section.getBlockState(x, y, z).isAir()) {
                  int worldX = pos.getStartX() + x;
                  int worldZ = pos.getStartZ() + z;
                  boolean touchesGeode = false;

                  for (Direction direction : Direction.values()) {
                     int nx = worldX + direction.getOffsetX();
                     int nz = worldZ + direction.getOffsetZ();
                     if (nx >> 4 == pos.x && nz >> 4 == pos.z) {
                        neighbour.set(nx, worldY + direction.getOffsetY(), nz);
                        Block block = chunk.getBlockState(neighbour).getBlock();
                        if (block == Blocks.AMETHYST_BLOCK || block == Blocks.BUDDING_AMETHYST) {
                           touchesGeode = true;
                           break;
                        }
                     }
                  }

                  if (touchesGeode) {
                     int score = scatter(worldX, worldY, worldZ);
                     if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(worldX, worldY, worldZ);
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   static int scatter(int x, int y, int z) {
      long h = x * -7046029254386353131L ^ y * -4417276706812531889L ^ z * 1609587929392839161L;
      h ^= h >>> 29;
      h *= -4658895280553007687L;
      h ^= h >>> 32;
      return Math.floorMod(h, 100);
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isGeode(BlockState state) {
      return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      long[] current = this.hits;
      if (level != null && current.length != 0) {
         DrawStyle style = DrawStyle.filledAndStroked(-3636481, 1.5F, -3636481);
         boolean drawTracers = this.tracer.get();

         for (long packed : current) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (LevelUtil.isLoaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
               Box box = new Box(pos);
               if (event.frustum == null || event.frustum.isVisible(box)) {
                  GizmoDrawing.box(box, style).ignoreOcclusion();
               }

               if (drawTracers) {
                  TracerEngine.draw(Vec3d.ofCenter(pos), -3636481);
               }
            }
         }
      }
   }
}
