package net.anubis.client.easyplace.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(BlockItem.class)
public interface InvokerBlockItem {
   @Invoker("getPlacementState")
   BlockState easyplace$getPlacementState(ItemPlacementContext var1);
}
