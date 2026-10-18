package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.anubis.client.event.impl.PlayerMoveEvent;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class MixinLocalPlayer {
   @WrapMethod(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V")
   private void anubis$onMove(MovementType type, Vec3d movement, Operation<Void> original) {
      if (!PlayerMoveEvent.TYPE.hasListeners()) {
         original.call(new Object[]{type, movement});
      } else {
         PlayerMoveEvent.move(movement, changed -> original.call(new Object[]{type, changed}));
      }
   }

   @Inject(method = "tick()V", at = @At("HEAD"))
   private void anubis$walkServerRotation(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      if (MinecraftClient.getInstance().player == self) {
         ServerRotation.applyForMovement(self);
      }
   }

   @Inject(method = "tick()V", at = @At("RETURN"))
   private void anubis$finishServerRotation(CallbackInfo ci) {
      ServerRotation.finishTick((ClientPlayerEntity)(Object)this);
   }

   @Inject(method = "tickMovement()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = Shift.AFTER))
   private void anubis$freecamAfterInput(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "sendMovementPackets()V", at = @At("HEAD"))
   private void anubis$freecamBeforeSend(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "sendMovementPackets()V", at = @At("RETURN"))
   private void anubis$swapOutServerRotation(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      if (MinecraftClient.getInstance().player == self) {
         ServerRotation.afterSend(self);
      }
   }
}
