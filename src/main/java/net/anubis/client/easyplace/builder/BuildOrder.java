package net.anubis.client.easyplace.builder;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GlazedTerracottaBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.WallRedstoneTorchBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
final class BuildOrder {
   static final int STAGE_NORMAL = 0;
   static final int STAGE_PISTON = 1;
   static final int STAGE_OBSERVER = 2;
   static final int STAGE_POWER = 3;
   private static final int MAX_TRACK = 512;

   private BuildOrder() {
   }

   static boolean powerOrTnt(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.REDSTONE_BLOCK || block instanceof RedstoneTorchBlock || block instanceof DaylightDetectorBlock || block instanceof TntBlock;
   }

   static boolean late(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
      return stage(schematic, pos, expected, machineLast) > 0;
   }

   static int stage(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
      int own = ownStage(schematic, pos, expected, machineLast);
      if (own != 0) {
         return own;
      }

      BlockPos support = hangsOn(expected, pos);
      return support == null ? 0 : ownStage(schematic, support, schematic.getBlockState(support), machineLast);
   }

   private static int ownStage(WorldSchematic schematic, BlockPos pos, BlockState expected, boolean machineLast) {
      if (powerOrTnt(expected) || pushedIntoTnt(schematic, pos, expected) != null) {
         return 3;
      } else if (!machineLast) {
         return 0;
      } else {
         Block block = expected.getBlock();
         if (block instanceof PistonBlock) {
            return 1;
         } else {
            return block instanceof ObserverBlock ? 2 : 0;
         }
      }
   }

   static boolean pistonWouldFire(ClientWorld level, BlockPos pos) {
      return level.isReceivingRedstonePower(pos) || level.isReceivingRedstonePower(pos.up());
   }

   static BlockPos pushedIntoTnt(WorldSchematic schematic, BlockPos pos, BlockState expected) {
      if (!expected.isAir() && !(expected.getBlock() instanceof TntBlock)) {
         for (Direction way : Direction.values()) {
            BlockState behind = schematic.getBlockState(pos.offset(way.getOpposite()));
            if (behind.getBlock() instanceof PistonBlock && behind.get(Properties.FACING) == way) {
               BlockPos ahead = pos.offset(way);
               if (schematic.getBlockState(ahead).getBlock() instanceof TntBlock) {
                  return ahead;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static BlockPos railBefore(ClientWorld level, WorldSchematic schematic, BlockPos pos, BlockState expected, Predicate<BlockPos> gaveUp) {
      List<BlockPos> track = track(schematic, pos);
      int index = track.indexOf(pos);
      if (index <= 0) {
         return null;
      }

      BlockPos before = track.get(index - 1);
      return !gaveUp.test(before) && !(level.getBlockState(before).getBlock() instanceof AbstractRailBlock) ? before : null;
   }

   static boolean railShapeOff(BlockState actual, BlockState expected) {
      RailShape want = railShape(expected);
      RailShape got = railShape(actual);
      return want != null && got != null && want != got;
   }

   private static List<BlockPos> track(WorldSchematic schematic, BlockPos pos) {
      Set<BlockPos> seen = new HashSet<>();
      List<BlockPos> stack = new ArrayList<>(List.of(pos));
      BlockPos start = null;

      while (!stack.isEmpty() && seen.size() < 512) {
         BlockPos at = stack.remove(stack.size() - 1);
         if (seen.add(at)) {
            List<BlockPos> links = links(schematic, at);
            if (links.size() < 2 && (start == null || before(at, start))) {
               start = at;
            }

            stack.addAll(links);
         }
      }

      if (start == null) {
         for (BlockPos rail : seen) {
            if (start == null || before(rail, start)) {
               start = rail;
            }
         }
      }

      List<BlockPos> order = new ArrayList<>();
      Set<BlockPos> laid = new HashSet<>();
      BlockPos at = start;

      while (at != null && laid.add(at)) {
         order.add(at);
         BlockPos next = null;

         for (BlockPos link : links(schematic, at)) {
            if (!laid.contains(link) && (next == null || before(link, next))) {
               next = link;
            }
         }

         at = next;
      }

      return order;
   }

   private static boolean before(BlockPos a, BlockPos b) {
      if (a.getY() != b.getY()) {
         return a.getY() < b.getY();
      } else {
         return a.getX() != b.getX() ? a.getX() < b.getX() : a.getZ() < b.getZ();
      }
   }

   private static List<BlockPos> links(WorldSchematic schematic, BlockPos pos) {
      List<BlockPos> out = new ArrayList<>(2);
      RailShape shape = railShape(schematic.getBlockState(pos));
      if (shape == null) {
         return out;
      }

      for (BlockPos end : ends(pos, shape)) {
         BlockPos other = railAt(schematic, end);
         if (other != null) {
            RailShape otherShape = railShape(schematic.getBlockState(other));

            for (BlockPos back : ends(other, otherShape)) {
               if (pos.equals(railAt(schematic, back))) {
                  out.add(other);
                  break;
               }
            }
         }
      }

      return out;
   }

   private static BlockPos railAt(WorldSchematic schematic, BlockPos cell) {
      if (railShape(schematic.getBlockState(cell)) != null) {
         return cell;
      } else {
         return railShape(schematic.getBlockState(cell.down())) != null ? cell.down() : null;
      }
   }

   private static List<BlockPos> ends(BlockPos pos, RailShape shape) {
      return switch (shape) {
         case NORTH_SOUTH -> List.of(pos.north(), pos.south());
         case EAST_WEST -> List.of(pos.east(), pos.west());
         case ASCENDING_EAST -> List.of(pos.east().up(), pos.west());
         case ASCENDING_WEST -> List.of(pos.west().up(), pos.east());
         case ASCENDING_NORTH -> List.of(pos.north().up(), pos.south());
         case ASCENDING_SOUTH -> List.of(pos.south().up(), pos.north());
         case SOUTH_EAST -> List.of(pos.south(), pos.east());
         case SOUTH_WEST -> List.of(pos.south(), pos.west());
         case NORTH_WEST -> List.of(pos.north(), pos.west());
         case NORTH_EAST -> List.of(pos.north(), pos.east());
      };
   }

   private static RailShape railShape(BlockState state) {
      if (state.getBlock() instanceof AbstractRailBlock rail) {
         Property<RailShape> shape = rail.getShapeProperty();
         return state.contains(shape) ? state.get(shape) : null;
      } else {
         return null;
      }
   }

   static boolean lookOnly(BlockState state) {
      Block block = state.getBlock();
      return block instanceof StairsBlock
         || block instanceof AbstractFurnaceBlock
         || block instanceof BarrelBlock
         || block instanceof PillarBlock
         || block instanceof CarvedPumpkinBlock
         || block instanceof GlazedTerracottaBlock
         || block instanceof FenceGateBlock
         || block instanceof LoomBlock
         || block instanceof StonecutterBlock
         || block instanceof LecternBlock
         || block instanceof BeehiveBlock
         || block instanceof AnvilBlock;
   }

   static List<BlockState> turnedVariants(BlockState state) {
      List<BlockState> out = new ArrayList<>();

      for (Property<?> property : List.of(Properties.FACING, Properties.HORIZONTAL_FACING, Properties.AXIS, Properties.HORIZONTAL_AXIS)) {
         if (state.contains(property)) {
            for (Object value : property.getValues()) {
               BlockState turned = with(state, property, value);
               if (!turned.equals(state)) {
                  out.add(turned);
               }
            }
            break;
         }
      }

      return out;
   }

   private static BlockState with(BlockState state, Property property, Object value) {
      return state.with(property, (Comparable)value);
   }

   static BlockPos hangsOn(BlockState state, BlockPos pos) {
      Block block = state.getBlock();
      if (block instanceof WallTorchBlock || block instanceof WallRedstoneTorchBlock || block instanceof WallSignBlock || block instanceof LadderBlock) {
         return pos.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite());
      }

      if (block instanceof WallMountedBlock) {
         return switch ((BlockFace)state.get(Properties.BLOCK_FACE)) {
            case FLOOR -> pos.down();
            case CEILING -> pos.up();
            case WALL -> pos.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite());
         };
      } else {
         return !(block instanceof RedstoneWireBlock)
               && !(block instanceof AbstractRedstoneGateBlock)
               && !(block instanceof AbstractRailBlock)
               && !(block instanceof CarpetBlock)
               && !(block instanceof AbstractPressurePlateBlock)
               && !(block instanceof AbstractTorchBlock)
            ? null
            : pos.down();
      }
   }

   static List<BlockPos> supportCells(BlockState expected, BlockPos pos) {
      Direction look = StateRules.lookFor(expected);
      if (look == null) {
         look = StateRules.flatLookFor(expected);
      }

      if (look != null) {
         List<BlockPos> cells = new ArrayList<>(List.of(pos.offset(look), pos.down()));

         for (Direction side : Type.HORIZONTAL) {
            if (side != look) {
               cells.add(pos.offset(side));
            }
         }

         if (look != Direction.UP) {
            cells.add(pos.up());
         }

         return cells;
      } else {
         Block block = expected.getBlock();
         if (block instanceof HopperBlock) {
            Direction spout = expected.get(Properties.HOPPER_FACING);
            return spout == Direction.DOWN ? List.of(pos.down(), pos.up()) : List.of(pos.offset(spout));
         } else if (hangsOn(expected, pos) != null) {
            return List.of();
         } else if (expected.contains(Properties.AXIS)) {
            Axis axis = expected.get(Properties.AXIS);
            return List.of(pos.offset(Direction.from(axis, AxisDirection.NEGATIVE)), pos.offset(Direction.from(axis, AxisDirection.POSITIVE)));
         } else if (expected.contains(Properties.SLAB_TYPE)) {
            return expected.get(Properties.SLAB_TYPE) == SlabType.TOP ? List.of(pos.up()) : List.of(pos.down());
         } else if (expected.contains(Properties.BLOCK_HALF)) {
            return expected.get(Properties.BLOCK_HALF) == BlockHalf.TOP ? List.of(pos.up()) : List.of(pos.down());
         } else {
            return List.of(pos.down(), pos.north(), pos.south(), pos.east(), pos.west(), pos.up());
         }
      }
   }

   static boolean waits(ClientWorld level, WorldSchematic schematic, BlockPos pos, BlockState expected, Predicate<BlockPos> gaveUp, boolean machineLast) {
      Block block = expected.getBlock();
      BlockPos tnt = pushedIntoTnt(schematic, pos, expected);
      if (tnt != null && !(level.getBlockState(tnt).getBlock() instanceof TntBlock)) {
         return true;
      }

      if (block instanceof PistonBlock) {
         return pistonWouldFire(level, pos);
      }

      if (block instanceof AbstractRailBlock && railBefore(level, schematic, pos, expected, gaveUp) != null) {
         return true;
      }

      if (block instanceof ObserverBlock) {
         BlockPos watched = pos.offset(expected.get(Properties.FACING));
         BlockState wanted = schematic.getBlockState(watched);
         if (BlockStateUtil.requiredItem(wanted) == null || stage(schematic, watched, wanted, machineLast) > 2 || gaveUp.test(watched)) {
            return false;
         } else {
            return wanted.getBlock() instanceof ObserverBlock && watched.offset(wanted.get(Properties.FACING)).equals(pos)
               ? false
               : !StateRules.placed(level.getBlockState(watched), wanted);
         }
      } else if (block instanceof FallingBlock) {
         return FallingBlock.canFallThrough(level.getBlockState(pos.down()));
      } else if (!expected.contains(Properties.WATERLOGGED) && expected.getFluidState().isStill() && expected.getFluidState().isIn(FluidTags.WATER)) {
         return !level.getFluidState(pos).isStill() || !level.getFluidState(pos).isIn(FluidTags.WATER);
      } else {
         return block instanceof TntBlock ? level.isReceivingRedstonePower(pos) : false;
      }
   }
}
