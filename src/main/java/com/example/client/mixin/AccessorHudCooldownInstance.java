package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/entity/player/ItemCooldownManager$Entry")
public interface AccessorHudCooldownInstance {
   @Accessor("startTick")
   int anubis$hudStartTime();

   @Accessor("endTick")
   int anubis$hudEndTime();
}
