package net.anubis.client.easyplace.hopper;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;

@Environment(EnvType.CLIENT)
public final class HopperPlacementHandler {
   public boolean isHopper(BlockState state) {
      return state.getBlock() instanceof HopperBlock;
   }

   public boolean faceCanProduceExpectedFacing(BlockState expected, Direction clickedFace) {
      if (this.isHopper(expected) && expected.contains(Properties.HOPPER_FACING)) {
         Direction wanted = expected.get(Properties.HOPPER_FACING);
         Direction produced = clickedFace.getOpposite();
         if (produced.getAxis() == Axis.Y) {
            produced = Direction.DOWN;
         }

         return produced == wanted;
      } else {
         return true;
      }
   }

   public int facingScore(BlockState expected, Direction clickedFace) {
      return this.faceCanProduceExpectedFacing(expected, clickedFace) ? 1000 : -1000;
   }
}
