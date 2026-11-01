package net.anubis.client.easyplace.util;

import net.anubis.client.easyplace.schematic.TargetStatus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class BlockStateUtil {
   private BlockStateUtil() {
   }

   public static boolean statesMatch(BlockState expected, BlockState actual) {
      return StateRules.placed(actual, expected) || StateRules.slabHalf(actual, expected);
   }

   public static TargetStatus classify(BlockState expected, BlockState actual) {
      if (StateRules.done(actual, expected)) {
         return TargetStatus.CORRECT;
      } else if (StateRules.placed(actual, expected)) {
         return TargetStatus.ADJUST;
      } else {
         return !actual.isAir() && !actual.isReplaceable() && !StateRules.slabHalf(actual, expected) ? TargetStatus.WRONG : TargetStatus.MISSING;
      }
   }

   public static Item requiredItem(BlockState expected) {
      Item item = StateRules.plant(expected.getBlock()).asItem();
      return item == Items.AIR ? null : item;
   }

   public static boolean isPrimaryPlacementState(BlockState state) {
      return state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
         ? false
         : !state.contains(Properties.BED_PART) || state.get(Properties.BED_PART) != BedPart.HEAD;
   }

   public static boolean isSimpleFullBlock(BlockState state, World level, BlockPos pos) {
      return state.isFullCube(level, pos) && state.getBlock().asItem() != Items.AIR;
   }
}
