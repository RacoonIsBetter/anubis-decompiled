package com.example.client.mixin;

import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(ChatHudLine.class)
public abstract class MixinNameProtectGuiMessage {
   @ModifyArg(
      method = "breakLines(Lnet/minecraft/client/font/TextRenderer;I)Ljava/util/List;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/ChatMessages;breakRenderedChatMessageLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/client/font/TextRenderer;)Ljava/util/List;"),
      index = 0
   )
   private StringVisitable anubis$nameProtectChatLine(StringVisitable text) {
      return NameProtectModule.protectChatLine(text);
   }
}
