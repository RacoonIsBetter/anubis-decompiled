package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class MixinNameProtectEntityRenderer {
   @ModifyExpressionValue(
      method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getDisplayName(Lnet/minecraft/entity/Entity;)Lnet/minecraft/text/Text;")
   )
   private Text anubis$nameProtectNameTag(Text original) {
      return NameProtectModule.protect(original);
   }
}
