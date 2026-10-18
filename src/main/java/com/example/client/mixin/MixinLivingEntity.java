package com.example.client.mixin;

import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.SlowSwingModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
   @Inject(method = "getHandSwingDuration()I", at = @At("RETURN"), cancellable = true)
   private void anubis$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
      SlowSwingModule module = ModuleRegistry.get(SlowSwingModule.class);
      if (module != null && module.isEnabled()) {
         if ((Object)this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(module.getModifiedSwingDuration((Integer)cir.getReturnValue()));
         }
      }
   }
}
