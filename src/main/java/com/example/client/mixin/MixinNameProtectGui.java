package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class MixinNameProtectGui {
   @ModifyVariable(method = "setTitle(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
   private Text anubis$nameProtectTitle(Text title) {
      return NameProtectModule.protect(title);
   }

   @ModifyVariable(method = "setSubtitle(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
   private Text anubis$nameProtectSubtitle(Text subtitle) {
      return NameProtectModule.protect(subtitle);
   }

   @ModifyVariable(method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), argsOnly = true)
   private Text anubis$nameProtectActionBar(Text message) {
      return NameProtectModule.protect(message);
   }

   @ModifyExpressionValue(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))
   private Text anubis$nameProtectSidebarTitle(Text original) {
      return NameProtectModule.protect(original);
   }

   @ModifyExpressionValue(
      method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "FIELD", opcode = 180, ordinal = 0, target = "Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;name:Lnet/minecraft/text/Text;")
   )
   private Text anubis$nameProtectSidebarLineWidth(Text original) {
      return NameProtectModule.protect(original);
   }

   @ModifyExpressionValue(
      method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At(value = "FIELD", opcode = 180, ordinal = 1, target = "Lnet/minecraft/client/gui/hud/InGameHud$SidebarEntry;name:Lnet/minecraft/text/Text;")
   )
   private Text anubis$nameProtectSidebarLineDraw(Text original) {
      return NameProtectModule.protect(original);
   }
}
