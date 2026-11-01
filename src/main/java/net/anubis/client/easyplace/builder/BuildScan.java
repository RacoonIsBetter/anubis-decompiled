package net.anubis.client.easyplace.builder;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.anubis.client.easyplace.schematic.SchematicManager;
import net.anubis.client.easyplace.schematic.TargetStatus;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
final class BuildScan {
   static final int NONE = Integer.MAX_VALUE;
   final List<BuildScan.Cell> missing = new ArrayList<>();
   final List<BuildScan.Cell> adjust = new ArrayList<>();
   final List<BuildScan.Liquid> liquids = new ArrayList<>();
   final Set<Long> waiting = new HashSet<>();
   final List<BlockPos> poweredTnt = new ArrayList<>();
   final List<BlockPos> tntForPlayer = new ArrayList<>();
   final List<BlockPos> afterTntForPlayer = new ArrayList<>();
   private final List<BuildScan.Cell> tntCells = new ArrayList<>();
   private boolean powerLeft;
   private final Set<Long> waterBelow = new HashSet<>();
   private final Map<Long, Integer> staged = new HashMap<>();
   private final List<BuildScan.Cell> lateCells = new ArrayList<>();
   private boolean machineLast;
   int late;
   int leversOff;
   final List<BlockPos> levers = new ArrayList<>();
   int railsOff;
   final List<BlockPos> crafters = new ArrayList<>();
   final Map<BlockPos, boolean[]> crafterSlots = new HashMap<>();
   int layer = Integer.MAX_VALUE;
   int wrong;
   int noItem;
   int unloaded;
   boolean wholeSchematic = true;
   private boolean placeTnt;

   static BuildScan run(
      ClientWorld level,
      WorldSchematic schematic,
      List<SchematicManager.ActiveBox> boxes,
      Predicate<BlockPos> deferred,
      Predicate<BlockPos> gaveUp,
      Predicate<BlockPos> waterPending,
      boolean lateNow,
      Set<Long> scaffold,
      Set<Long> craftersDone,
      boolean wantCrafters,
      boolean placeTnt,
      boolean machineLast
   ) {
      BuildScan scan = new BuildScan();
      scan.placeTnt = placeTnt;
      scan.machineLast = machineLast;
      if (boxes.isEmpty()) {
         return scan;
      }

      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;

      for (SchematicManager.ActiveBox box : boxes) {
         minY = Math.min(minY, box.minY());
         maxY = Math.max(maxY, box.maxY());
      }

      Mutable cursor = new Mutable();
      int y = minY;

      do {
         for (scan.wholeSchematic = true; y <= maxY; y++) {
            if (scan.layer != Integer.MAX_VALUE && y > scan.layer + 1) {
               scan.wholeSchematic = false;
               break;
            }

            for (SchematicManager.ActiveBox box : boxes) {
               if (y >= box.minY() && y <= box.maxY()) {
                  for (int x = box.minX(); x <= box.maxX(); x++) {
                     for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        cursor.set(x, y, z);
                        scan.visit(level, schematic, cursor, deferred, gaveUp, waterPending, lateNow, scaffold, craftersDone, wantCrafters);
                     }
                  }
               }
            }
         }

         if (scan.wholeSchematic) {
            break;
         }

         scan.keepApproachesOpen(level, schematic, gaveUp, deferred);
      } while (scan.layer == Integer.MAX_VALUE);

      scan.releaseStages(gaveUp);
      scan.releaseTnt(level, schematic, deferred, gaveUp, scaffold);
      scan.keepApproachesOpen(level, schematic, gaveUp, deferred);
      return scan;
   }

   private void releaseStages(Predicate<BlockPos> gaveUp) {
      int lowest = Integer.MAX_VALUE;

      for (Entry<Long, Integer> entry : this.staged.entrySet()) {
         if (!gaveUp.test(BlockPos.fromLong(entry.getKey()))) {
            lowest = Math.min(lowest, entry.getValue());
         }
      }

      for (Entry<Long, Integer> entry : this.staged.entrySet()) {
         if (entry.getValue() > lowest) {
            this.waiting.add(entry.getKey());
         }
      }
   }

   private void keepApproachesOpen(ClientWorld level, WorldSchematic schematic, Predicate<BlockPos> gaveUp, Predicate<BlockPos> deferred) {
      Set<Long> open = new HashSet<>();

      for (BuildScan.Cell cell : this.missing) {
         open.add(cell.pos().asLong());
      }

      Set<Long> held = new HashSet<>();

      for (BuildScan.Cell cell : this.lateCells) {
         open.add(cell.pos().asLong());
         held.add(cell.pos().asLong());
      }

      held.addAll(this.staged.keySet());
      List<BuildScan.Cell> all = new ArrayList<>(this.missing);
      all.addAll(this.lateCells);

      for (BuildScan.Cell cell : all) {
         if (!this.waiting.contains(cell.pos().asLong()) || held.contains(cell.pos().asLong())) {
            if (StateRules.lookFor(cell.expected()) == Direction.UP) {
               BlockPos low = lowStand(level, schematic, cell.pos(), open);
               if (low != null) {
                  if (open.contains(low.asLong())) {
                     this.waiting.add(low.asLong());
                  }

                  if (open.contains(low.up().asLong())) {
                     this.waiting.add(low.up().asLong());
                  }
               } else {
                  this.waiting.add(cell.pos().asLong());
               }
            } else if (!gaveUp.test(cell.pos())) {
               BlockPos approach = approach(cell.expected(), cell.pos());
               if (approach != null && open.contains(approach.asLong())) {
                  BlockPos back = approach(schematic.getBlockState(approach), approach);
                  if (!cell.pos().equals(back)) {
                     this.waiting.add(approach.asLong());
                  }
               }
            }
         }
      }

      this.layer = Integer.MAX_VALUE;

      for (BuildScan.Cell cell : this.missing) {
         if (!this.waiting.contains(cell.pos().asLong()) && !deferred.test(cell.pos())) {
            this.layer = Math.min(this.layer, cell.pos().getY());
         }
      }
   }

   private static BlockPos lowStand(ClientWorld level, WorldSchematic schematic, BlockPos pos, Set<Long> open) {
      BlockPos best = null;
      int bestScore = -1;

      for (Direction side : Type.HORIZONTAL) {
         BlockPos feet = pos.down().offset(side);
         BlockPos floor = feet.down();
         boolean floorNow = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
         BlockState floorPlanned = schematic.getBlockState(floor);
         if ((floorNow || !floorPlanned.isAir() && floorPlanned.getFluidState().isEmpty())
            && freeCell(level, schematic, feet, open)
            && freeCell(level, schematic, feet.up(), open)
            && StateRules.lookFor(schematic.getBlockState(feet.up())) != Direction.UP) {
            int score = (floorNow ? 2 : 0) + (open.contains(feet.asLong()) ? 0 : 1);
            if (score > bestScore) {
               bestScore = score;
               best = feet;
            }
         }
      }

      return best;
   }

   private static boolean freeCell(ClientWorld level, WorldSchematic schematic, BlockPos cell, Set<Long> open) {
      BlockState now = level.getBlockState(cell);
      if (now.isReplaceable() && now.getFluidState().isEmpty()) {
         BlockState planned = schematic.getBlockState(cell);
         return planned.getBlock() instanceof FluidBlock ? false : planned.isAir() || open.contains(cell.asLong());
      } else {
         return false;
      }
   }

   private static BlockPos approach(BlockState expected, BlockPos pos) {
      Direction look = StateRules.lookFor(expected);
      if (look == null) {
         look = StateRules.flatLookFor(expected);
      }

      if (look == null && expected.getBlock() instanceof HopperBlock) {
         Direction spout = expected.get(Properties.HOPPER_FACING);
         if (spout != Direction.DOWN) {
            look = spout;
         }
      }

      return look == null ? null : pos.offset(look.getOpposite());
   }

   static Map<Item, Integer> remainingItems(ClientWorld level, WorldSchematic schematic, List<SchematicManager.ActiveBox> boxes) {
      Map<Item, Integer> out = new HashMap<>();
      Mutable cursor = new Mutable();

      for (SchematicManager.ActiveBox box : boxes) {
         for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
               for (int z = box.minZ(); z <= box.maxZ(); z++) {
                  cursor.set(x, y, z);
                  BlockState expected = schematic.getBlockState(cursor);
                  if (!expected.isAir() && level.isChunkLoaded(cursor)) {
                     BlockState actual = level.getBlockState(cursor);
                     CellFiller.Kind liquid = needsLiquid(expected, actual);
                     if (liquid != null) {
                        out.merge(liquid.item, 1, Integer::sum);
                     }

                     if (BlockStateUtil.isPrimaryPlacementState(expected)) {
                        Item item = BlockStateUtil.requiredItem(expected);
                        if (item != null && BlockStateUtil.classify(expected, actual) == TargetStatus.MISSING) {
                           out.merge(item, StateRules.itemsNeeded(actual, expected), Integer::sum);
                        }
                     }
                  }
               }
            }
         }
      }

      return out;
   }

   private void releaseTnt(ClientWorld level, WorldSchematic schematic, Predicate<BlockPos> deferred, Predicate<BlockPos> gaveUp, Set<Long> scaffold) {
      if (!this.powerLeft && scaffold.isEmpty()) {
         for (BuildScan.Cell cell : this.tntCells) {
            if (BuildOrder.waits(level, schematic, cell.pos(), cell.expected(), gaveUp, this.machineLast)) {
               this.poweredTnt.add(cell.pos());
            } else {
               this.waiting.remove(cell.pos().asLong());
               if (!deferred.test(cell.pos()) && (this.layer == Integer.MAX_VALUE || cell.pos().getY() < this.layer)) {
                  this.layer = cell.pos().getY();
               }
            }
         }
      }
   }

   private void visit(
      ClientWorld level,
      WorldSchematic schematic,
      Mutable cursor,
      Predicate<BlockPos> deferred,
      Predicate<BlockPos> gaveUp,
      Predicate<BlockPos> waterPending,
      boolean lateNow,
      Set<Long> scaffold,
      Set<Long> craftersDone,
      boolean wantCrafters
   ) {
      BlockState expected = schematic.getBlockState(cursor);
      if (!expected.isAir()) {
         if (!level.isChunkLoaded(cursor)) {
            this.unloaded++;
         } else {
            BlockState actual = level.getBlockState(cursor);
            CellFiller.Kind liquid = needsLiquid(expected, actual);
            if (liquid != null) {
               BlockPos pos = cursor.toImmutable();
               boolean capped = liquid == CellFiller.Kind.LAVA_SOURCE && capped(schematic, pos);
               this.liquids.add(new BuildScan.Liquid(pos, liquid, capped));
               if ((liquid != CellFiller.Kind.LAVA_SOURCE || capped) && waterPending.test(pos)) {
                  this.waterBelow.add(pos.asLong());
               }
            }

            if (expected.getFluidState().isEmpty() || expected.getBlock().asItem() != Items.AIR) {
               if (BlockStateUtil.isPrimaryPlacementState(expected)) {
                  if (BlockStateUtil.requiredItem(expected) == null) {
                     this.noItem++;
                  } else {
                     TargetStatus status = BlockStateUtil.classify(expected, actual);
                     switch (status) {
                        case MISSING:
                           BlockPos pos = cursor.toImmutable();
                           boolean tnt = expected.getBlock() instanceof TntBlock;
                           boolean intoTnt = !tnt && BuildOrder.pushedIntoTnt(schematic, pos, expected) != null;
                           if (!this.placeTnt && (tnt || intoTnt)) {
                              (tnt ? this.tntForPlayer : this.afterTntForPlayer).add(pos);
                              this.waiting.add(pos.asLong());
                              return;
                           }

                           int stage = BuildOrder.stage(schematic, pos, expected, this.machineLast);
                           if (lateNow && !tnt && !intoTnt && stage > 0) {
                              this.powerLeft = true;
                              this.staged.put(pos.asLong(), stage);
                           }

                           if (lateNow && tnt) {
                              this.missing.add(new BuildScan.Cell(pos, expected, actual));
                              this.tntCells.add(new BuildScan.Cell(pos, expected, actual));
                              this.waiting.add(pos.asLong());
                              return;
                           }

                           if (!lateNow && stage > 0) {
                              this.late++;
                              this.lateCells.add(new BuildScan.Cell(pos, expected, actual));
                              this.waiting.add(pos.asLong());
                              return;
                           }

                           this.missing.add(new BuildScan.Cell(pos, expected, actual));
                           if (this.waterBelow.contains(pos.down().asLong()) || BuildOrder.waits(level, schematic, pos, expected, gaveUp, this.machineLast)) {
                              this.waiting.add(pos.asLong());
                              return;
                           }

                           if (this.layer == Integer.MAX_VALUE && !deferred.test(pos)) {
                              this.layer = pos.getY();
                           }
                           break;
                        case ADJUST:
                           this.adjust.add(new BuildScan.Cell(cursor.toImmutable(), expected, actual));
                           break;
                        case CORRECT:
                           if (expected.getBlock() instanceof LeverBlock && expected.get(Properties.POWERED) && !actual.get(Properties.POWERED)) {
                              this.leversOff++;
                              this.levers.add(cursor.toImmutable());
                           }

                           if (BuildOrder.railShapeOff(actual, expected)) {
                              this.railsOff++;
                           }

                           if (wantCrafters && expected.getBlock() instanceof CrafterBlock && !craftersDone.contains(cursor.asLong())) {
                              boolean[] disabled = disabledSlots(schematic, cursor);
                              if (disabled != null) {
                                 BlockPos crafterPos = cursor.toImmutable();
                                 this.crafters.add(crafterPos);
                                 this.crafterSlots.put(crafterPos, disabled);
                              }
                           }
                           break;
                        case WRONG:
                           if (!scaffold.contains(cursor.asLong())) {
                              this.wrong++;
                           }
                     }
                  }
               }
            }
         }
      }
   }

   static CellFiller.Kind needsLiquid(BlockState expected, BlockState actual) {
      FluidState fluid = expected.getFluidState();
      if (!fluid.isEmpty() && fluid.isStill()) {
         FluidState there = actual.getFluidState();
         if (fluid.isIn(FluidTags.LAVA)) {
            if (there.isStill() && there.isIn(FluidTags.LAVA)) {
               return null;
            } else {
               return !actual.isAir() && (!actual.isReplaceable() || !there.isEmpty() && !there.isIn(FluidTags.LAVA)) ? null : CellFiller.Kind.LAVA_SOURCE;
            }
         } else if (!fluid.isIn(FluidTags.WATER)) {
            return null;
         } else if (expected.contains(Properties.WATERLOGGED)) {
            return StateRules.placed(actual, expected) && !actual.get(Properties.WATERLOGGED) ? CellFiller.Kind.WATERLOG : null;
         } else if (there.isStill() && there.isIn(FluidTags.WATER)) {
            return null;
         } else {
            return !actual.isAir() && !actual.isReplaceable() ? null : CellFiller.Kind.WATER_SOURCE;
         }
      } else {
         return null;
      }
   }

   private static boolean capped(WorldSchematic schematic, BlockPos pos) {
      Mutable up = pos.mutableCopy();

      for (int i = 0; i < 64; i++) {
         up.move(Direction.UP);
         BlockState state = schematic.getBlockState(up);
         if (!state.getFluidState().isIn(FluidTags.LAVA)) {
            return !state.isAir() && state.getFluidState().isEmpty();
         }
      }

      return false;
   }

   private static boolean[] disabledSlots(WorldSchematic schematic, BlockPos pos) {
      if (!(schematic.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) {
         return null;
      } else {
         boolean[] var6 = new boolean[9];
         boolean any = false;

         for (int i = 0; i < 9; i++) {
            var6[i] = crafter.isSlotDisabled(i);
            any |= var6[i];
         }

         return any ? var6 : null;
      }
   }

   Map<Item, Integer> needs(int top) {
      Map<Item, Integer> out = new HashMap<>();

      for (BuildScan.Cell cell : this.missing) {
         if (cell.pos().getY() <= top) {
            Item item = BlockStateUtil.requiredItem(cell.expected());
            if (item != null) {
               out.merge(item, StateRules.itemsNeeded(cell.actual(), cell.expected()), Integer::sum);
            }
         }
      }

      return out;
   }

   @Environment(EnvType.CLIENT)
   record Cell(BlockPos pos, BlockState expected, BlockState actual) {
   }

   @Environment(EnvType.CLIENT)
   record Liquid(BlockPos pos, CellFiller.Kind kind, boolean capped) {
   }
}
