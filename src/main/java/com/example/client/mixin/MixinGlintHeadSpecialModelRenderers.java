package com.example.client.mixin;

import com.mojang.serialization.MapCodec;
import net.anubis.client.gui.GlintHeadRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer.Unbaked;
import net.minecraft.util.dynamic.Codecs.IdMapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SpecialModelTypes.class)
public abstract class MixinGlintHeadSpecialModelRenderers {
   @Shadow
   @Final
   private static IdMapper<Identifier, MapCodec<? extends Unbaked>> ID_MAPPER;

   @Inject(method = "bootstrap()V", at = @At("TAIL"))
   private static void anubis$registerGlintHead(CallbackInfo ci) {
      ID_MAPPER.put(GlintHeadRenderer.ID, GlintHeadRenderer.Unbaked.MAP_CODEC);
   }
}
