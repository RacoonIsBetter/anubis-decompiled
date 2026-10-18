package com.example.client.mixin;

import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.WorldEntityAddEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class MixinClientLevel {
   @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
   private void anubis$onEntityAdd(Entity entity, CallbackInfo ci) {
      WorldEntityAddEvent.dispatch(entity);
   }

   @Inject(method = "updateListeners(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V", at = @At("HEAD"))
   private void anubis$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
      BlockUpdateEvent.dispatch(pos, oldState, newState);
   }
}
