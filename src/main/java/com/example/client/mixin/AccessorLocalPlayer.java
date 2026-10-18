package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public interface AccessorLocalPlayer {
   @Accessor("ticksSinceLastPositionPacketSent")
   void anubis$setPositionReminder(int var1);

   @Accessor("lastSprinting")
   boolean anubis$wasSprinting();

   @Accessor("lastPlayerInput")
   PlayerInput anubis$lastSentInput();
}
