package com.example.client.mixin;

import net.anubis.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractSignEditScreen.class)
public abstract class MixinTranslationGuardSignEditScreen {
   @Shadow
   private SignText text;
   @Shadow
   @Final
   private String[] messages;

   @Inject(method = "<init>(Lnet/minecraft/block/entity/SignBlockEntity;ZZLnet/minecraft/text/Text;)V", at = @At("TAIL"))
   private void anubis$guardMessages(SignBlockEntity sign, boolean front, boolean filtered, Text title, CallbackInfo ci) {
      for (int line = 0; line < this.messages.length; line++) {
         this.messages[line] = TranslationGuard.plain(this.text.getMessage(line, filtered));
      }
   }
}
