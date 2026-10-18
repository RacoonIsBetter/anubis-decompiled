package com.example.client.mixin;

import net.anubis.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinSneakKeyboardInput {
   @Inject(method = "tick()V", at = @At("TAIL"))
   private void anubis$forceSneak(CallbackInfo ci) {
      SneakModule sneak = SneakModule.active;
      if (sneak != null && sneak.doVanilla()) {
         Input input = (Input)(Object)this;
         PlayerInput keys = input.playerInput;
         if (!keys.sneak()) {
            input.playerInput = new PlayerInput(
               keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), true, keys.sprint()
            );
         }
      }
   }
}
