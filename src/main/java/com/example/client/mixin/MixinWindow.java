package com.example.client.mixin;

import net.anubis.client.gui.WindowIcon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourcePack;
import net.minecraft.client.util.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinWindow {
   @Shadow
   public abstract long getHandle();

   @Inject(method = "setIcon(Lnet/minecraft/resource/ResourcePack;Lnet/minecraft/client/util/Icons;)V", at = @At("TAIL"))
   private void anubis$setIcon(ResourcePack resources, Icons iconSet, CallbackInfo ci) {
      WindowIcon.apply(this.getHandle());
   }
}
