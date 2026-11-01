package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class MixinNoInvLeakInventoryModel {
   @WrapOperation(
      method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/entity/LivingEntity;)Lnet/minecraft/client/render/entity/state/EntityRenderState;"
      )
   )
   private static EntityRenderState anubis$noInvLeakModel(LivingEntity entity, Operation<EntityRenderState> original) {
      if (!NoInvLeakModule.hidesModelOf(entity)) {
         return (EntityRenderState)original.call(new Object[]{entity});
      }

      NoInvLeakModule.bareModel(true);

      try {
         return (EntityRenderState)original.call(new Object[]{entity});
      } finally {
         NoInvLeakModule.bareModel(false);
      }
   }
}
