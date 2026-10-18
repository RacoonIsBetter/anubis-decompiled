package com.example.client.mixin;

import net.anubis.client.module.impl.misc.SpamModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MixinSpamMinecraft {
   @Shadow
   public ClientWorld world;

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
   private void anubis$spamGameLeft(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
      if (SpamModule.active != null && this.world != null) {
         SpamModule.onGameLeft();
      }
   }

   @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
   private void anubis$spamDisconnectedScreen(Screen screen, CallbackInfo ci) {
      if (SpamModule.active != null && this.world != null && screen instanceof DisconnectedScreen) {
         SpamModule.onDisconnectedScreen();
      }
   }
}
