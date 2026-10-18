package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Slot.class)
public abstract class MixinNoInvLeakSlot {
   @ModifyReturnValue(method = "getBackgroundSprite()Lnet/minecraft/util/Identifier;", at = @At("RETURN"))
   private Identifier anubis$noInvLeakSilhouette(Identifier icon) {
      Slot self = (Slot)(Object)this;
      return icon != null && NoInvLeakModule.overlaying() && self.inventory instanceof PlayerInventory && self.hasStack() ? null : icon;
   }
}
