package net.anubis.client.easyplace.schematic;

import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.RayTraceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerRange;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.anubis.client.easyplace.EasyPlace;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class SchematicManager {
   private static final int CACHE_REFRESH_TICKS = 5;
   private static final int NAV_REFRESH_TICKS = 20;
   private final MinecraftClient mc;
   private final Map<Long, SchematicTarget> cachedTargets = new LinkedHashMap<>();
   private final List<SchematicManager.ActiveBox> activeBoxes = new ArrayList<>();
   private long lastRefreshTick = Long.MIN_VALUE;
   private BlockPos lastCenter;
   private double lastRange = -1.0;
   private boolean lastAura;
   private boolean lastAllBlocks;
   private int lastLayerRangeHash;
   private SchematicTarget cachedNavTarget;
   private long lastNavRefreshTick = Long.MIN_VALUE;

   public SchematicManager(MinecraftClient mc) {
      this.mc = mc;
   }

   public void clear() {
      this.cachedTargets.clear();
      this.activeBoxes.clear();
      this.lastRefreshTick = Long.MIN_VALUE;
      this.lastNavRefreshTick = Long.MIN_VALUE;
      this.cachedNavTarget = null;
      this.lastCenter = null;
   }

   public void invalidate() {
      this.lastRefreshTick = Long.MIN_VALUE;
   }

   public boolean hasActiveSchematic() {
      return this.rebuildActiveBoxes();
   }

   public List<SchematicManager.ActiveBox> activeBoxes() {
      this.rebuildActiveBoxes();
      return List.copyOf(this.activeBoxes);
   }

   public WorldSchematic getSchematicWorld() {
      return SchematicWorldHandler.getSchematicWorld();
   }

   public Collection<SchematicTarget> getTargets(long tick, double range, boolean aura, boolean allBlocks) {
      if (this.mc.player != null && this.mc.world != null) {
         BlockPos center = this.mc.player.getBlockPos();
         boolean moved = this.lastCenter == null || center.getSquaredDistance(this.lastCenter) > 1.0;
         int layerHash = this.getLayerRangeHash();
         boolean settingsChanged = range != this.lastRange || aura != this.lastAura || allBlocks != this.lastAllBlocks || layerHash != this.lastLayerRangeHash;
         if (tick - this.lastRefreshTick >= 5L || moved || settingsChanged) {
            this.refresh(tick, center, range, aura, allBlocks);
         }

         return this.cachedTargets.values();
      } else {
         this.clear();
         return List.of();
      }
   }

   private void refresh(long tick, BlockPos center, double range, boolean aura, boolean allBlocks) {
      this.cachedTargets.clear();
      this.lastRefreshTick = tick;
      this.lastCenter = center.toImmutable();
      this.lastRange = range;
      this.lastAura = aura;
      this.lastAllBlocks = allBlocks;
      this.lastLayerRangeHash = this.getLayerRangeHash();
      if (this.rebuildActiveBoxes()) {
         WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
         if (schematicWorld != null) {
            if (!aura) {
               BlockPos traced = RayTraceUtils.getSchematicWorldTraceIfClosestNoFluids(this.mc.world, this.mc.player, range);
               if (traced != null) {
                  this.addTarget(traced, schematicWorld, allBlocks);
               }
            } else {
               Vec3d playerPos = new Vec3d(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
               int radius = (int)Math.ceil(range);
               double rangeSquared = range * range;
               Mutable mutable = new Mutable();

               for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                  for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                     for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                        mutable.set(x, y, z);
                        if (!(mutable.toCenterPos().squaredDistanceTo(playerPos) > rangeSquared)) {
                           this.addTarget(mutable, schematicWorld, allBlocks);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addTarget(BlockPos pos, WorldSchematic schematicWorld, boolean allBlocks) {
      if (this.insideActivePlacement(pos)) {
         if (this.isInRenderLayer(pos)) {
            BlockState expected = schematicWorld.getBlockState(pos);
            if (!expected.isAir() && BlockStateUtil.isPrimaryPlacementState(expected)) {
               if (allBlocks || BlockStateUtil.isSimpleFullBlock(expected, schematicWorld, pos)) {
                  BlockState actual = this.mc.world.getBlockState(pos);
                  TargetStatus status = BlockStateUtil.classify(expected, actual);
                  if (status != TargetStatus.CORRECT) {
                     BlockPos immutable = pos.toImmutable();
                     double distance = immutable.toCenterPos().squaredDistanceTo(this.mc.player.getEyePos());
                     this.cachedTargets.put(immutable.asLong(), new SchematicTarget(immutable, SchematicBlockState.of(expected), actual, status, distance));
                  }
               }
            }
         }
      }
   }

   private boolean rebuildActiveBoxes() {
      this.activeBoxes.clear();

      for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
         if (placement.isEnabled()) {
            UnmodifiableIterator var4 = placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values().iterator();

            while (var4.hasNext()) {
               Box box = (Box)var4.next();
               if (box.getPos1() != null && box.getPos2() != null) {
                  this.activeBoxes.add(SchematicManager.ActiveBox.of(box.getPos1(), box.getPos2()));
               }
            }
         }
      }

      return !this.activeBoxes.isEmpty();
   }

   private boolean insideActivePlacement(BlockPos pos) {
      for (SchematicManager.ActiveBox box : this.activeBoxes) {
         if (box.contains(pos)) {
            return true;
         }
      }

      return false;
   }

   public static boolean inRenderLayer(BlockPos pos) {
      try {
         LayerRange range = DataManager.getRenderLayerRange();
         return range == null || range.isPositionWithinRange(pos);
      } catch (Exception e) {
         return true;
      }
   }

   private boolean isInRenderLayer(BlockPos pos) {
      try {
         LayerRange range = DataManager.getRenderLayerRange();
         return range == null || range.isPositionWithinRange(pos);
      } catch (Exception e) {
         return true;
      }
   }

   private int getLayerRangeHash() {
      try {
         LayerRange range = DataManager.getRenderLayerRange();
         return range == null
            ? 0
            : Objects.hash(range.getLayerMode(), range.getLayerRangeMin(), range.getLayerRangeMax(), range.getLayerSingle(), range.getAxis());
      } catch (Exception e) {
         return 0;
      }
   }

   public SchematicTarget nearestMissingForNavigation(long tick, boolean allBlocks) {
      if (this.mc.player != null && this.mc.world != null) {
         if (tick - this.lastNavRefreshTick < 20L) {
            return this.cachedNavTarget;
         }

         this.lastNavRefreshTick = tick;
         this.cachedNavTarget = null;

         try {
            if (!this.rebuildActiveBoxes()) {
               return null;
            }

            WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
            if (schematicWorld == null) {
               return null;
            }

            Vec3d eye = this.mc.player.getEyePos();
            double bestDist = Double.MAX_VALUE;
            Mutable mutable = new Mutable();
            int scanned = 0;
            int nonAir = 0;
            int missing = 0;

            for (SchematicManager.ActiveBox box : this.activeBoxes) {
               for (int x = box.minX(); x <= box.maxX(); x++) {
                  for (int y = box.minY(); y <= box.maxY(); y++) {
                     for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        scanned++;
                        mutable.set(x, y, z);
                        BlockState expected = schematicWorld.getBlockState(mutable);
                        if (!expected.isAir() && BlockStateUtil.isPrimaryPlacementState(expected)) {
                           nonAir++;
                           if (allBlocks || BlockStateUtil.isSimpleFullBlock(expected, schematicWorld, mutable)) {
                              BlockState actual = this.mc.world.getBlockState(mutable);
                              if (BlockStateUtil.classify(expected, actual) == TargetStatus.MISSING) {
                                 missing++;
                                 BlockPos immutable = mutable.toImmutable();
                                 double dist = immutable.toCenterPos().squaredDistanceTo(eye);
                                 if (dist < bestDist) {
                                    bestDist = dist;
                                    this.cachedNavTarget = new SchematicTarget(immutable, SchematicBlockState.of(expected), actual, TargetStatus.MISSING, dist);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            EasyPlace.LOG
               .debug(
                  "NavScan: scanned={} nonAir={} missing={} result={}",
                  new Object[]{scanned, nonAir, missing, this.cachedNavTarget == null ? "null" : this.cachedNavTarget.pos().toShortString()}
               );
         } catch (Exception e) {
            EasyPlace.LOG.warn("NavScan error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
         }

         return this.cachedNavTarget;
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   public record ActiveBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      private static SchematicManager.ActiveBox of(BlockPos first, BlockPos second) {
         return new SchematicManager.ActiveBox(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ()),
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ())
         );
      }

      public boolean contains(BlockPos pos) {
         return pos.getX() >= this.minX
            && pos.getX() <= this.maxX
            && pos.getY() >= this.minY
            && pos.getY() <= this.maxY
            && pos.getZ() >= this.minZ
            && pos.getZ() <= this.maxZ;
      }
   }
}
