package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(VehicleMoveC2SPacket.class)
public interface AccessorBoatFlyServerboundMoveVehiclePacket {
   @Mutable
   @Accessor("position")
   void anubis$setPosition(Vec3d var1);
}
