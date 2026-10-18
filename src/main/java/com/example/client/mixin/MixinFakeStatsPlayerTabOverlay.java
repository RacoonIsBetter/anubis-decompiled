package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.anubis.client.module.impl.misc.FakeStats;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.StringVisitable;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(PlayerListHud.class)
public abstract class MixinFakeStatsPlayerTabOverlay {
   @ModifyReturnValue(method = "getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;", at = @At("RETURN"))
   private Text anubis$fakeStatsTabName(Text original, @Local(argsOnly = true) PlayerListEntry info) {
      return FakeStats.tabName(info.getProfile().id(), original);
   }

   @WrapOperation(
      method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/scoreboard/Scoreboard;getScore(Lnet/minecraft/scoreboard/ScoreHolder;Lnet/minecraft/scoreboard/ScoreboardObjective;)Lnet/minecraft/scoreboard/ReadableScoreboardScore;"
      )
   )
   private ReadableScoreboardScore anubis$fakeStatsTabScore(Scoreboard board, ScoreHolder holder, ScoreboardObjective objective, Operation<ReadableScoreboardScore> original) {
      return FakeStats.tabScore(holder, (ReadableScoreboardScore)original.call(new Object[]{board, holder, objective}));
   }

   @ModifyArg(
      method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"),
      index = 0
   )
   private StringVisitable anubis$fakeStatsTabHeader(StringVisitable header) {
      return FakeStats.tabText(header);
   }

   @ModifyArg(
      method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"),
      index = 0
   )
   private StringVisitable anubis$fakeStatsTabFooter(StringVisitable footer) {
      return FakeStats.tabText(footer);
   }
}
