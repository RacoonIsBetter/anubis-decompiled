package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public interface AccessorBlockOverlayMultiPlayerGameMode {
   @Accessor("currentBreakingProgress")
   float anubis$blockOverlayDestroyProgress();

   @Accessor("currentBreakingPos")
   BlockPos anubis$blockOverlayDestroyPos();
}
