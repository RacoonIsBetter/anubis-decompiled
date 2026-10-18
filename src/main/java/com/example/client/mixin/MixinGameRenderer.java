package com.example.client.mixin;

import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinGameRenderer {
   @Inject(method = "swapBuffers(Lnet/minecraft/client/util/tracy/TracyFrameCapturer;)V", at = @At("HEAD"))
   private void anubis$beforeSwap(CallbackInfo ci) {
      StreamOverlay.endFrame();
      ImGuiManager.getInstance().renderIfActive();
   }
}
