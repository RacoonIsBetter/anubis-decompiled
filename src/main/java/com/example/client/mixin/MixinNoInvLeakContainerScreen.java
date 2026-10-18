package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class MixinNoInvLeakContainerScreen {
   @Shadow
   protected Slot focusedSlot;
   @Shadow
   protected int x;
   @Shadow
   protected int y;

   @Shadow
   public abstract ScreenHandler getScreenHandler();

   @WrapOperation(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
   private ItemStack anubis$noInvLeakSlot(Slot slot, Operation<ItemStack> original) {
      ItemStack stack = (ItemStack)original.call(new Object[]{slot});
      return NoInvLeakModule.ownSlot(slot)
         ? NoInvLeakModule.container(stack, slot, this.x + slot.x, this.y + slot.y, slot == this.focusedSlot)
         : stack;
   }

   @ModifyVariable(method = "drawItem(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
   private ItemStack anubis$noInvLeakCarried(ItemStack stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
      return NoInvLeakModule.carried(stack, x, y);
   }

   @Inject(method = "drawMouseoverTooltip(Lnet/minecraft/client/gui/DrawContext;II)V", at = @At("HEAD"), cancellable = true)
   private void anubis$noInvLeakTooltip(DrawContext guiGraphics, int x, int y, CallbackInfo ci) {
      if (NoInvLeakModule.hidesTooltips() && NoInvLeakModule.ownSlot(this.focusedSlot)) {
         if (NoInvLeakModule.overlaying() && this.focusedSlot.hasStack() && this.getScreenHandler().getCursorStack().isEmpty()) {
            StreamOverlay.tooltip(this.focusedSlot.getStack(), x, y);
         }

         ci.cancel();
      }
   }
}
