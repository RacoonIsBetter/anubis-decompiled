// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package com.example.client.mixin;

import net.anubis.client.sound.SoundCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe logical dispatch before audio settings/channel allocation can discard it. */
@Mixin(ClientWorld.class)
abstract class SoundDetectorClientSoundMixin {
    @Inject(method = "playSoundFromEntityClient(Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V", at = @At("HEAD"))
    private void anubis$soundEntityEnter(Entity entity, SoundEvent sound, SoundCategory source, float volume, float pitch, CallbackInfo ci) {
        if (SoundCapture.active() && entity != null) SoundCapture.clientSound(sound, source, entity.getEntityPos(), entity, volume, pitch);
    }
    @Inject(method = "playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V", at = @At("HEAD"))
    private void anubis$soundSeedEntityEnter(Entity excluded, Entity emitter, RegistryEntry<SoundEvent> sound, SoundCategory source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (SoundCapture.active() && excluded == MinecraftClient.getInstance().player && emitter != null)
            SoundCapture.clientSound(sound.value(), source, emitter.getEntityPos(), emitter, volume, pitch);
    }
    @Inject(method = "playSoundClient(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V", at = @At("HEAD"))
    private void anubis$soundLocalPlayer(SoundEvent sound, SoundCategory source, float volume, float pitch, CallbackInfo ci) {
        if (SoundCapture.active() && MinecraftClient.getInstance().player != null) {
            var player = MinecraftClient.getInstance().player;
            SoundCapture.clientSound(sound, source, player.getEntityPos(), player, volume, pitch);
        }
    }
    @Inject(method = "playSound(Lnet/minecraft/entity/Entity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V", at = @At("HEAD"))
    private void anubis$soundPredicted(Entity excluded, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory source, float volume, float pitch, long seed, CallbackInfo ci) {
        // This overload plays locally only for the initiating local player. Keep that exact self context.
        if (SoundCapture.active() && excluded == MinecraftClient.getInstance().player)
            SoundCapture.clientSound(sound.value(), source, new Vec3d(x, y, z), excluded, volume, pitch);
    }
    @Inject(method = "playSoundClient(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V", at = @At("HEAD"))
    private void anubis$soundDispatch(double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch, boolean delay, CallbackInfo ci) {
        SoundCapture.clientSound(sound, source, new Vec3d(x, y, z), null, volume, pitch);
    }
}
