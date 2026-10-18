package com.example.client.mixin;

import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class MixinVisGraph {
   @Inject(method = "markClosed(Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$noOcclusion(BlockPos pos, CallbackInfo ci) {
      if (XrayModule.active != null || FreecamModule.active != null) {
         ci.cancel();
      }
   }
}
