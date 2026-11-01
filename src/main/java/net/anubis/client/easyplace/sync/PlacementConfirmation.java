package net.anubis.client.easyplace.sync;

import net.anubis.client.easyplace.schematic.SchematicBlockState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public record PlacementConfirmation(BlockPos pos, SchematicBlockState expected, long sentTick, long deadlineTick, int retry, int sequence, BlockState predicted) {
}
