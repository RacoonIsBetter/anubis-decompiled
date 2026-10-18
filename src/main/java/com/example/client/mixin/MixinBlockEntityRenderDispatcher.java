package com.example.client.mixin;

import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BlockEntityRenderManager.class)
public abstract class MixinBlockEntityRenderDispatcher {
   @Inject(method = "getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;", at = @At("HEAD"), cancellable = true)
   private void anubis$xraySkipHiddenBlockEntity(BlockEntity blockEntity, float partialTick, CrumblingOverlayCommand overlay, CallbackInfoReturnable<BlockEntityRenderState> cir) {
      XrayModule.Snapshot snapshot = XrayModule.active;
      if (snapshot != null) {
         World level = blockEntity.getWorld();
         if (level != null) {
            BlockPos pos = blockEntity.getPos();
            BlockState state = blockEntity.getCachedState();
            if (snapshot.alphaFor(level, state, pos) <= 0.0F) {
               cir.setReturnValue(null);
            }
         }
      }
   }
}
