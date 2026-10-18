package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBlock.class)
public interface AccessorElytraFlyBlockBehaviour {
   @Accessor("collidable")
   boolean anubis$elytraFlyHasCollision();
}
