package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.AntiGhostBlockModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class MixinAntiGhostBlockClientLevel {
   @WrapOperation(
      method = "handleBlockUpdate",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/PendingUpdateManager;hasPendingUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"
      )
   )
   private boolean anubis$serverAnswer(PendingUpdateManager handler, BlockPos pos, BlockState state, Operation<Boolean> original) {
      boolean predicted = (Boolean)original.call(new Object[]{handler, pos, state});
      return predicted && !AntiGhostBlockModule.serverAnswered((ClientWorld)(Object)this, pos);
   }

   @Inject(method = "handlePlayerActionResponse", at = @At("TAIL"))
   private void anubis$acknowledged(int sequence, CallbackInfo ci) {
      AntiGhostBlockModule.acknowledged((ClientWorld)(Object)this, sequence);
   }
}
