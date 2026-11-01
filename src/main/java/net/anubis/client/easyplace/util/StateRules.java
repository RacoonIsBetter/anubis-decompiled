package net.anubis.client.easyplace.util;

import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.MultifaceBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;

@Environment(EnvType.CLIENT)
public final class StateRules {
   private static final Set<Property<?>> PLACED = Set.of(
      Properties.FACING,
      Properties.HORIZONTAL_FACING,
      Properties.HOPPER_FACING,
      Properties.VERTICAL_DIRECTION,
      Properties.AXIS,
      Properties.HORIZONTAL_AXIS,
      Properties.BLOCK_HALF,
      Properties.DOUBLE_BLOCK_HALF,
      Properties.SLAB_TYPE,
      Properties.ROTATION,
      Properties.BLOCK_FACE,
      Properties.ORIENTATION,
      Properties.DOOR_HINGE,
      Properties.CHEST_TYPE,
      Properties.BED_PART,
      Properties.ATTACHMENT,
      Properties.HANGING
   );
   private static final Set<Property<?>> CLICKED = Set.of(Properties.DELAY, Properties.COMPARATOR_MODE, Properties.NOTE, Properties.OPEN, Properties.INVERTED);
   private static final Map<Block, Block> TIPS = Map.of(
      Blocks.KELP_PLANT,
      Blocks.KELP,
      Blocks.TWISTING_VINES_PLANT,
      Blocks.TWISTING_VINES,
      Blocks.WEEPING_VINES_PLANT,
      Blocks.WEEPING_VINES,
      Blocks.CAVE_VINES_PLANT,
      Blocks.CAVE_VINES
   );

   private StateRules() {
   }

   public static Block plant(Block block) {
      return TIPS.getOrDefault(block, block);
   }

   public static boolean placed(BlockState actual, BlockState expected) {
      if (plant(actual.getBlock()) != plant(expected.getBlock())) {
         return false;
      }

      for (Property<?> property : expected.getProperties()) {
         if (actual.contains(property)
            && (PLACED.contains(property) || faceProperty(expected, property))
            && !actual.get(property).equals(expected.get(property))) {
            return false;
         }
      }

      return true;
   }

   public static boolean done(BlockState actual, BlockState expected) {
      if (!placed(actual, expected)) {
         return false;
      }

      for (Property<?> property : expected.getProperties()) {
         if (clickable(expected, property) && actual.contains(property) && !actual.get(property).equals(expected.get(property))) {
            return false;
         }
      }

      return true;
   }

   public static boolean needsClicks(BlockState actual, BlockState expected) {
      return placed(actual, expected) && !done(actual, expected);
   }

   public static boolean wouldFit(BlockState wouldBe, BlockState desired, boolean skipChestType) {
      if (plant(wouldBe.getBlock()) != plant(desired.getBlock())) {
         return false;
      }

      for (Property<?> property : desired.getProperties()) {
         if (wouldBe.contains(property)
            && (!skipChestType || property != Properties.CHEST_TYPE)
            && (property != Properties.SLAB_TYPE || desired.get(Properties.SLAB_TYPE) != SlabType.DOUBLE)) {
            if (faceProperty(desired, property)) {
               if ((Boolean)wouldBe.get(property) && !(Boolean)desired.get(property)) {
                  return false;
               }
            } else if (PLACED.contains(property) && !wouldBe.get(property).equals(desired.get(property))) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean slabHalf(BlockState actual, BlockState expected) {
      return expected.getBlock() instanceof SlabBlock
         && actual.getBlock() == expected.getBlock()
         && expected.get(Properties.SLAB_TYPE) == SlabType.DOUBLE
         && actual.get(Properties.SLAB_TYPE) != SlabType.DOUBLE;
   }

   public static int itemsNeeded(BlockState actual, BlockState expected) {
      if (expected.getBlock() instanceof SlabBlock && expected.get(Properties.SLAB_TYPE) == SlabType.DOUBLE) {
         return slabHalf(actual, expected) ? 1 : 2;
      } else {
         return 1;
      }
   }

   public static boolean clickable(BlockState expected, Property<?> property) {
      if (!CLICKED.contains(property)) {
         return false;
      }

      if (property != Properties.OPEN) {
         return true;
      }

      Block block = expected.getBlock();
      return block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR
         ? block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
         : false;
   }

   public static Direction lookFor(BlockState expected) {
      Block block = expected.getBlock();
      if (block instanceof CrafterBlock) {
         return expected.get(Properties.ORIENTATION).getFacing().getOpposite();
      } else if (block instanceof ObserverBlock) {
         return expected.get(Properties.FACING);
      } else {
         return !(block instanceof PistonBlock) && !(block instanceof DispenserBlock) && !(block instanceof BarrelBlock)
            ? null
            : expected.get(Properties.FACING).getOpposite();
      }
   }

   public static Direction flatLookFor(BlockState expected) {
      if (!expected.contains(Properties.HORIZONTAL_FACING)) {
         return null;
      } else {
         Block block = expected.getBlock();
         Direction facing = expected.get(Properties.HORIZONTAL_FACING);
         if (block instanceof ChestBlock || block instanceof AbstractFurnaceBlock || block instanceof AbstractRedstoneGateBlock) {
            return facing.getOpposite();
         } else {
            return !(block instanceof StairsBlock) && !(block instanceof DoorBlock) && !(block instanceof FenceGateBlock) ? null : facing;
         }
      }
   }

   private static boolean faceProperty(BlockState state, Property<?> property) {
      Block block = state.getBlock();
      return (block instanceof MultifaceBlock || block instanceof VineBlock)
         && property instanceof BooleanProperty
         && ConnectingBlock.FACING_PROPERTIES.containsValue(property);
   }
}
