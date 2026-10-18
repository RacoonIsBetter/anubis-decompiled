package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import net.anubis.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(TranslationStorage.class)
public abstract class MixinTranslationGuardClientLanguage {
   @Inject(method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;", at = @At("HEAD"), require = 0)
   private static void anubis$beginLoad(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<TranslationStorage> cir) {
      TranslationGuard.beginLoad();
   }

   @WrapOperation(
      method = "load(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;load(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
      require = 0
   )
   private static void anubis$tapLoad(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
      original.call(new Object[]{stream, TranslationGuard.tap(resource, output)});
   }

   @Inject(method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;", at = @At("RETURN"), require = 0)
   private static void anubis$endLoad(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<TranslationStorage> cir) {
      TranslationGuard.endLoad();
   }
}
