package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.anubis.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Language.class)
public abstract class MixinTranslationGuardLanguage {
   @ModifyReturnValue(method = "getInstance()Lnet/minecraft/util/Language;", at = @At("RETURN"))
   private static Language anubis$guardLanguage(Language original) {
      return TranslationGuard.active() ? TranslationGuard.language(original) : original;
   }
}
