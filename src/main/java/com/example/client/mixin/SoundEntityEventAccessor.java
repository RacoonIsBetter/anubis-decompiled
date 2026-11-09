// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package com.example.client.mixin;

import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep unresolved IDs for diagnostics without assigning an arbitrary nearby entity. */
@Mixin(EntityStatusS2CPacket.class)
public interface SoundEntityEventAccessor {
    @Accessor("entityId") int anubis$soundEntityId();
}
