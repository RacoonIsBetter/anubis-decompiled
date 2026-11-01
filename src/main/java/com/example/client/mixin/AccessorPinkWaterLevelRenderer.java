package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public interface AccessorPinkWaterLevelRenderer {
   @Accessor("chunks")
   BuiltChunkStorage anubis$pinkWaterViewArea();
}
