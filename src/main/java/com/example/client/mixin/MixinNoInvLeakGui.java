package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class MixinNoInvLeakGui {
   @Unique
   private static final Identifier ANUBIS$SELECTION = Identifier.ofVanilla("hud/hotbar_selection");
   @Shadow
   private int heldItemTooltipFade;
   @Shadow
   private ItemStack currentStack;

   @ModifyVariable(method = "renderHotbarItem", at = @At("HEAD"), argsOnly = true)
   private ItemStack anubis$noInvLeakHotbar(
      ItemStack stack,
      @Local(argsOnly = true) DrawContext guiGraphics,
      @Local(argsOnly = true, ordinal = 0) int x,
      @Local(argsOnly = true, ordinal = 1) int y,
      @Local(argsOnly = true, ordinal = 2) int seed
   ) {
      return NoInvLeakModule.hotbar(stack, NoInvLeakModule.screenX(guiGraphics, x, y, x), NoInvLeakModule.screenY(guiGraphics, x, y, y), seed);
   }

   @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
   private void anubis$noInvLeakItemName(DrawContext guiGraphics, CallbackInfo ci) {
      if (NoInvLeakModule.hidesItemName()) {
         if (NoInvLeakModule.overlaying() && this.heldItemTooltipFade > 0 && !this.currentStack.isEmpty()) {
            int alpha = Math.min(255, (int)(this.heldItemTooltipFade * 256.0F / 10.0F));
            int y = guiGraphics.getScaledWindowHeight() - 59;
            if (!MinecraftClient.getInstance().interactionManager.hasStatusBars()) {
               y += 14;
            }

            StreamOverlay.itemName(this.currentStack, alpha, y);
         }

         ci.cancel();
      }
   }

   @WrapWithCondition(
      method = "renderHotbar",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
      )
   )
   private boolean anubis$noInvLeakSelection(DrawContext guiGraphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
      if (NoInvLeakModule.hidesSelectedSlot() && ANUBIS$SELECTION.equals(sprite)) {
         if (NoInvLeakModule.overlaying()) {
            StreamOverlay.selection(x, y);
         }

         return false;
      } else {
         return true;
      }
   }
}
