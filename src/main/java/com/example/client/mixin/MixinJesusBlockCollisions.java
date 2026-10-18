package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.CollisionView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.BlockCollisionSpliterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(BlockCollisionSpliterator.class)
public abstract class MixinJesusBlockCollisions {
   @WrapOperation(
      method = "computeNext",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/block/ShapeContext;getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/CollisionView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"
      )
   )
   private VoxelShape anubis$jesusCollisionShape(ShapeContext context, BlockState state, CollisionView level, BlockPos pos, Operation<VoxelShape> original) {
      VoxelShape shape = (VoxelShape)original.call(new Object[]{context, state, level, pos});
      JesusModule jesus = JesusModule.active;
      return jesus == null ? shape : jesus.collisionShape(level, state, pos, shape);
   }
}
