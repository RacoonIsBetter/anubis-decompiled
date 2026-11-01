package net.anubis.client.easyplace.placement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class Clicks {
   private Clicks() {
   }

   public static boolean usesClick(World level, BlockPos pos, boolean builder) {
      if (level.getBlockEntity(pos) instanceof NamedScreenHandlerFactory) {
         return true;
      }

      if (!builder) {
         return false;
      }

      Block block = level.getBlockState(pos).getBlock();
      return level.getBlockEntity(pos) != null
         || block instanceof DoorBlock
         || block instanceof TrapdoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof AbstractRedstoneGateBlock
         || block instanceof NoteBlock
         || block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof BellBlock
         || block instanceof DaylightDetectorBlock;
   }
}
