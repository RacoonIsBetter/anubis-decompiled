package net.anubis.client.easyplace.schematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public record SchematicTarget(BlockPos pos, SchematicBlockState expected, BlockState actual, TargetStatus status, double distanceSquared) {
   public SchematicTarget withActual(BlockState state, TargetStatus status) {
      return new SchematicTarget(this.pos, this.expected, state, status, this.distanceSquared);
   }
}
