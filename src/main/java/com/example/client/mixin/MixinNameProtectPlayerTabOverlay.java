package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.StringVisitable;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(PlayerListHud.class)
public abstract class MixinNameProtectPlayerTabOverlay {
   @ModifyReturnValue(method = "getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;", at = @At("RETURN"))
   private Text anubis$nameProtectTabName(Text original) {
      return NameProtectModule.protect(original);
   }

   @ModifyArg(
      method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"),
      index = 0
   )
   private StringVisitable anubis$nameProtectTabHeader(StringVisitable header) {
      return NameProtectModule.protectText(header);
   }

   @ModifyArg(
      method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"),
      index = 0
   )
   private StringVisitable anubis$nameProtectTabFooter(StringVisitable footer) {
      return NameProtectModule.protectText(footer);
   }

   @WrapOperation(method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/PlayerListEntry;getSkinTextures()Lnet/minecraft/entity/player/SkinTextures;"))
   private SkinTextures anubis$nameProtectTabHead(PlayerListEntry info, Operation<SkinTextures> original) {
      SkinTextures real = (SkinTextures)original.call(new Object[]{info});
      return NameProtectModule.hidesName(info.getProfile().name()) ? NameProtectModule.hiddenHead(info.getProfile().id(), real) : real;
   }
}
