// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package com.example.client.mixin;

import net.anubis.client.sound.LocalActionTracker;
import net.anubis.client.sound.SoundCapture;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Successful local predictions contribute probable-self context only; no interactions are fabricated. */
@Mixin(ClientPlayerInteractionManager.class)
abstract class SoundDetectorLocalMixin {
    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void anubis$soundUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        if (!SoundCapture.active() || !ci.getReturnValue().isAccepted()) return;
        var level = MinecraftClient.getInstance().world;
        if (level == null || !level.isChunkLoaded(hit.getBlockPos())) return;
        var target = level.getBlockState(hit.getBlockPos());
        LocalActionTracker.record(hit.getBlockPos(), SoundCapture.interactionCategory(target.getBlock()));
        if (target.getBlock() instanceof ChestBlock && target.contains(ChestBlock.CHEST_TYPE) && target.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            LocalActionTracker.record(ChestBlock.getPosInFrontOf(hit.getBlockPos(), target), "containers");
        if (player.getStackInHand(hand).getItem() instanceof BlockItem) {
            LocalActionTracker.record(hit.getBlockPos(), "building");
            LocalActionTracker.record(hit.getBlockPos().offset(hit.getSide()), "building");
        }
    }
    @Inject(method = "attackBlock", at = @At("RETURN"))
    private void anubis$soundStartBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> ci) {
        if (Boolean.TRUE.equals(ci.getReturnValue())) LocalActionTracker.record(pos, "building");
    }
    @Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"))
    private void anubis$soundContinueBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> ci) {
        if (Boolean.TRUE.equals(ci.getReturnValue())) LocalActionTracker.record(pos, "building");
    }
    @Inject(method = "interactEntity", at = @At("RETURN"))
    private void anubis$soundInteract(PlayerEntity player, Entity target, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (SoundCapture.active() && ci.getReturnValue().isAccepted()) LocalActionTracker.record(target.getEntityPos(), "farming");
    }
    @Inject(method = "interactEntityAtLocation", at = @At("RETURN"))
    private void anubis$soundInteractAt(PlayerEntity player, Entity target, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (SoundCapture.active() && ci.getReturnValue().isAccepted()) LocalActionTracker.record(target.getEntityPos(), "farming");
    }
}
