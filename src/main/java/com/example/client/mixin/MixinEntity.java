package com.example.client.mixin;

import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.EspModule;
import net.anubis.client.module.impl.render.ItemEspModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class MixinEntity {
   @Inject(method = "isGlowing()Z", at = @At("HEAD"), cancellable = true)
   private void anubis$isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity)(Object)this;
      if (self.getEntityWorld().isClient()) {
         if (EspModule.shouldGlow(self) || ItemEspModule.shouldGlow(self)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(method = "getTeamColorValue()I", at = @At("HEAD"), cancellable = true)
   private void anubis$getTeamColor(CallbackInfoReturnable<Integer> cir) {
      Entity self = (Entity)(Object)this;
      if (self.getEntityWorld().isClient()) {
         if (EspModule.shouldGlow(self)) {
            cir.setReturnValue(EspModule.glowColor(self));
         } else if (ItemEspModule.shouldGlow(self)) {
            cir.setReturnValue(ItemEspModule.glowColor(self));
         }
      }
   }

   @Inject(method = "isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z", at = @At("HEAD"), cancellable = true)
   private void anubis$freecamOwnBody(PlayerEntity viewer, CallbackInfoReturnable<Boolean> cir) {
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null && freecam.drawsSelf((Entity)(Object)this, viewer)) {
         cir.setReturnValue(false);
      }
   }
}
