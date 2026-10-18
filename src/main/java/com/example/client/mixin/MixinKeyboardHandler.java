package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.anubis.client.AnubisClientMod;
import net.anubis.client.event.impl.KeyInputEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Keyboard.class)
public abstract class MixinKeyboardHandler {
   @WrapMethod(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V")
   private void anubis$openGuiFirst(long window, int action, KeyInput event, Operation<Void> original) {
      boolean guiKey = action != 0 && AnubisClientMod.isGuiKey(window, event);
      if (!guiKey || action != 1 || !AnubisClientMod.toggleGuiFromKey()) {
         original.call(new Object[]{window, action, event});
         if (guiKey) {
            AnubisClientMod.dropGuiClicks();
         }
      }
   }

   @Inject(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$onKeyPress(long window, int action, KeyInput event, CallbackInfo ci) {
      if (KeyInputEvent.dispatch(event.key(), event.scancode(), action, event.modifiers())) {
         ci.cancel();
      }
   }
}
