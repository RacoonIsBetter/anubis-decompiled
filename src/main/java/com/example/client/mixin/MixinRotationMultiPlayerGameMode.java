package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Supplier;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinRotationMultiPlayerGameMode {
   @Shadow
   @Final
   private MinecraftClient client;

   @WrapMethod(method = "interactItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;")
   private ActionResult anubis$useItemRotation(PlayerEntity player, Hand hand, Operation<ActionResult> original) {
      return this.anubis$asSent(player, () -> (ActionResult)original.call(new Object[]{player, hand}));
   }

   @WrapMethod(method = "stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V")
   private void anubis$releaseRotation(PlayerEntity player, Operation<Void> original) {
      this.anubis$asSent(player, () -> (Void)original.call(new Object[]{player}));
   }

   private <T> T anubis$asSent(PlayerEntity player, Supplier<T> call) {
      ClientPlayerEntity self = this.client.player;
      if (player == self && !ServerRotation.isSwapped()) {
         Rotation rotation = ServerRotation.commit(self);
         float yaw = self.getYaw();
         float pitch = self.getPitch();
         if (rotation.yaw() == yaw && rotation.pitch() == pitch) {
            return call.get();
         }

         self.setYaw(rotation.yaw());
         self.setPitch(rotation.pitch());

         try {
            return call.get();
         } finally {
            self.setYaw(yaw);
            self.setPitch(pitch);
         }
      } else {
         return call.get();
      }
   }

   @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true, order = 950)
   private void anubis$attackRotationGate(PlayerEntity player, Entity target, CallbackInfo ci) {
      ClientPlayerEntity self = this.client.player;
      if (player == self) {
         Box box = target.getBoundingBox();
         double reach = self.getEntityInteractionRange();
         AttackRangeComponent range = (AttackRangeComponent)self.getMainHandStack().get(DataComponentTypes.ATTACK_RANGE);
         if (range != null) {
            box = box.expand(range.hitboxMargin());
            reach = range.getEffectiveMaxRange(self);
         }

         if (ServerRotation.misses(self, box, reach)) {
            player.resetTicksSince();
            ci.cancel();
         }
      }
   }

   @Inject(method = "interactEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true, order = 950)
   private void anubis$interactRotationGate(PlayerEntity player, Entity target, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      ClientPlayerEntity self = this.client.player;
      if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.getEntityInteractionRange())) {
         cir.setReturnValue(ActionResult.PASS);
      }
   }

   @Inject(method = "interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true, order = 950)
   private void anubis$interactAtRotationGate(PlayerEntity player, Entity target, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      ClientPlayerEntity self = this.client.player;
      if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.getEntityInteractionRange())) {
         cir.setReturnValue(ActionResult.PASS);
      }
   }

   @Inject(method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true, order = 950)
   private void anubis$useItemOnRotationGate(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
      if (player == this.client.player && ServerRotation.missesBlock(player, new Box(hit.getBlockPos()), player.getBlockInteractionRange())) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true, order = 950)
   private void anubis$startDestroyRotationGate(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
      ClientPlayerEntity self = this.client.player;
      if (self != null && ServerRotation.missesBlock(self, new Box(pos), self.getBlockInteractionRange())) {
         cir.setReturnValue(false);
      }
   }
}
