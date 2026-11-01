package com.example.client.mixin;

import net.anubis.client.module.impl.misc.AntiGhostBlockModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinAntiGhostBlockMultiPlayerGameMode {
   @Shadow
   private int blockBreakingCooldown;

   @Shadow
   public abstract void cancelBlockBreaking();

   @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
   private void anubis$holdBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (AntiGhostBlockModule.holdBreak(pos)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
   private void anubis$waitBeforeStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (AntiGhostBlockModule.waiting(pos)) {
         this.cancelBlockBreaking();
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true, order = 1100)
   private void anubis$waitBeforeContinue(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (AntiGhostBlockModule.waiting(pos)) {
         if (this.blockBreakingCooldown > 0) {
            this.blockBreakingCooldown--;
         }

         this.cancelBlockBreaking();
         cir.setReturnValue(false);
      }
   }
}
