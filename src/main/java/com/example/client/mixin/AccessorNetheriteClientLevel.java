package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public interface AccessorNetheriteClientLevel {
   @Accessor("pendingUpdateManager")
   PendingUpdateManager anubis$blockStatePredictionHandler();
}
