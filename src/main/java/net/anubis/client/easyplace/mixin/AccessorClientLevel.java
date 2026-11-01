package net.anubis.client.easyplace.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public interface AccessorClientLevel {
   @Accessor("pendingUpdateManager")
   PendingUpdateManager easyplace$predictions();
}
