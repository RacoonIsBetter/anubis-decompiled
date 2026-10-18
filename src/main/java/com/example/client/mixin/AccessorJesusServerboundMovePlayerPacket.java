package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(PlayerMoveC2SPacket.class)
public interface AccessorJesusServerboundMovePlayerPacket {
   @Mutable
   @Accessor("onGround")
   void anubis$jesusSetOnGround(boolean var1);
}
