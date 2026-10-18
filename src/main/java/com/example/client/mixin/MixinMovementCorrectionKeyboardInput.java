package com.example.client.mixin;

import net.anubis.client.util.pvp.InputControl;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinMovementCorrectionKeyboardInput {
   @Inject(method = "tick()V", at = @At("TAIL"))
   private void anubis$shapeInput(CallbackInfo ci) {
      Input input = (Input)(Object)this;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && player.input == input) {
         PlayerInput keys = input.playerInput;
         PlayerInput corrected = InputControl.shape(player, ServerRotation.correctInput(keys));
         if (corrected != keys) {
            input.playerInput = corrected;
            ((AccessorMovementClientInput)input)
               .anubis$setMoveVector(
                  new Vec2f(impulse(corrected.left(), corrected.right()), impulse(corrected.forward(), corrected.backward())).normalize()
               );
         }
      }
   }

   private static float impulse(boolean positive, boolean negative) {
      return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
   }
}
