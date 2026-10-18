package com.example.client.mixin;

import net.anubis.client.module.impl.misc.FakeStats;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Scoreboard.class)
public abstract class MixinFakeStatsScoreboard {
   @Inject(method = "setObjectiveSlot(Lnet/minecraft/scoreboard/ScoreboardDisplaySlot;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$fakeSidebar(ScoreboardDisplaySlot slot, ScoreboardObjective objective, CallbackInfo ci) {
      if (FakeStats.redirect((Scoreboard)(Object)this, slot, objective)) {
         ci.cancel();
      }
   }
}
