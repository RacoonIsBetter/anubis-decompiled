package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.BufferManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(BufferManager.class)
public class MixinGlDevice {
   @Redirect(method = "create(Lorg/lwjgl/opengl/GLCapabilities;Ljava/util/Set;Lnet/minecraft/client/gl/GpuDeviceInfo;)Lnet/minecraft/client/gl/BufferManager;", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gl/GlBackend;allowGlArbDirectAccess:Z", opcode = 178))
   private static boolean anubis$blockDsaForAmd() {
      String renderer = GL11.glGetString(7937);
      return renderer == null || !renderer.contains("AMD");
   }
}
