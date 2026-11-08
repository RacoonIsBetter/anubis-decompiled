package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Relative-move packets only expose their entity through a world lookup; Hidden Player ESP needs the raw id. */
@Environment(EnvType.CLIENT)
@Mixin(EntityS2CPacket.class)
public interface AccessorEntityS2CPacket {
   @Accessor("id")
   int anubis$getId();
}
