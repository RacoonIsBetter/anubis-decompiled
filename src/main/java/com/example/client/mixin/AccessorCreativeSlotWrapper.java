package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/client/gui/screen/ingame/CreativeInventoryScreen$CreativeSlot")
public interface AccessorCreativeSlotWrapper {
   @Accessor("slot")
   Slot anubis$target();
}
