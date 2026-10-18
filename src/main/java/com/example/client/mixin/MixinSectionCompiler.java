package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.chunk.SectionBuilder.RenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SectionBuilder.class)
public abstract class MixinSectionCompiler {
   @WrapOperation(
      method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkRendererRegion;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;")
   )
   private BlockState anubis$xrayBlockState(ChunkRendererRegion region, BlockPos pos, Operation<BlockState> original) {
      BlockState state = (BlockState)original.call(new Object[]{region, pos});
      XrayModule.Snapshot snapshot = XrayModule.active;
      if (snapshot != null && !state.isAir()) {
         float[] alphaSlot = XrayModule.CURRENT_ALPHA.get();
         float alpha = snapshot.alphaFor(region, state, pos);
         alphaSlot[0] = alpha;
         return alpha <= 0.0F ? Blocks.AIR.getDefaultState() : state;
      } else {
         return state;
      }
   }

   @WrapOperation(
      method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BlockRenderLayers;getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/BlockRenderLayer;")
   )
   private BlockRenderLayer anubis$xrayBlockLayer(BlockState state, Operation<BlockRenderLayer> original) {
      if (XrayModule.active == null) {
         return (BlockRenderLayer)original.call(new Object[]{state});
      }

      float alpha = XrayModule.CURRENT_ALPHA.get()[0];
      return alpha > 0.0F && alpha < 1.0F ? BlockRenderLayer.TRANSLUCENT : (BlockRenderLayer)original.call(new Object[]{state});
   }

   @WrapOperation(
      method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BlockRenderLayers;getFluidLayer(Lnet/minecraft/fluid/FluidState;)Lnet/minecraft/client/render/BlockRenderLayer;")
   )
   private BlockRenderLayer anubis$xrayFluidLayer(FluidState fluidState, Operation<BlockRenderLayer> original) {
      if (XrayModule.active == null) {
         return (BlockRenderLayer)original.call(new Object[]{fluidState});
      }

      float alpha = XrayModule.CURRENT_ALPHA.get()[0];
      return alpha > 0.0F && alpha < 1.0F ? BlockRenderLayer.TRANSLUCENT : (BlockRenderLayer)original.call(new Object[]{fluidState});
   }

   @Inject(method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;", at = @At("RETURN"))
   private void anubis$xrayResetAlpha(
      ChunkSectionPos sectionPos, ChunkRendererRegion region, VertexSorter vertexSorting, BlockBufferAllocatorStorage bufferPack, CallbackInfoReturnable<RenderData> cir
   ) {
      XrayModule.CURRENT_ALPHA.get()[0] = 1.0F;
   }
}
