// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package com.example.client.mixin;

import net.anubis.client.sound.SoundCapture;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RETURN capture follows vanilla thread handoff; HEAD only marks a main-thread dispatch scope. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class SoundDetectorPacketMixin {
    @Inject(method = {"onPlaySound", "onPlaySoundFromEntity", "onBlockEvent", "onWorldEvent", "onBlockBreakingProgress",
            "onEntityStatus", "onEntityDamage", "onExplosion", "onStopSound"}, at = @At("HEAD"))
    private void anubis$soundEnter(CallbackInfo ci) { SoundCapture.enterPacket(); }

    @Inject(method = "onPlaySound", at = @At("RETURN"))
    private void anubis$sound(PlaySoundS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.sound(packet); }
    @Inject(method = "onPlaySoundFromEntity", at = @At("RETURN"))
    private void anubis$entitySound(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.sound(packet); }
    @Inject(method = "onBlockEvent", at = @At("RETURN"))
    private void anubis$blockSound(BlockEventS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.block(packet); }
    @Inject(method = "onWorldEvent", at = @At("RETURN"))
    private void anubis$levelSound(WorldEventS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.level(packet); }
    @Inject(method = "onBlockBreakingProgress", at = @At("RETURN"))
    private void anubis$miningSound(BlockBreakingProgressS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.mining(packet); }
    @Inject(method = "onEntityStatus", at = @At("RETURN"))
    private void anubis$statusSound(EntityStatusS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.entityEvent(packet); }
    @Inject(method = "onEntityDamage", at = @At("RETURN"))
    private void anubis$damageSound(EntityDamageS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.damage(packet); }
    @Inject(method = "onExplosion", at = @At("RETURN"))
    private void anubis$explosionSound(ExplosionS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.explosion(packet); }
    @Inject(method = "onStopSound", at = @At("RETURN"))
    private void anubis$stopSound(StopSoundS2CPacket packet, CallbackInfo ci) { SoundCapture.leavePacket(); SoundCapture.stop(packet); }
    @Inject(method = "onUnloadChunk", at = @At("RETURN"))
    private void anubis$soundForget(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        SoundCapture.forgetChunk(packet.pos().x, packet.pos().z);
    }
    @Inject(method = "onChunkData", at = @At("RETURN"))
    private void anubis$soundChunk(ChunkDataS2CPacket packet, CallbackInfo ci) {
        SoundCapture.forgetChunk(packet.getChunkX(), packet.getChunkZ());
    }
}
