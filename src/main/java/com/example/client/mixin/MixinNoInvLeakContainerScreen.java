package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.anubis.client.module.impl.misc.NoInvLeakModule;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
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

   @WrapOperation(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
   private ItemStack anubis$noInvLeakSlot(Slot slot, Operation<ItemStack> original, @Local(argsOnly = true) DrawContext guiGraphics) {
      ItemStack stack = (ItemStack)original.call(new Object[]{slot});
      if (!NoInvLeakModule.ownSlot(slot)) {
         return stack;
      }

      int x = NoInvLeakModule.screenX(guiGraphics, slot.x, slot.y, this.x + slot.x);
      int y = NoInvLeakModule.screenY(guiGraphics, slot.x, slot.y, this.y + slot.y);
      return NoInvLeakModule.container(stack, slot, x, y, slot == this.focusedSlot);
   }

   @WrapOperation(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;copyWithCount(I)Lnet/minecraft/item/ItemStack;"))
   private ItemStack anubis$noInvLeakDragPreview(ItemStack stack, int count, Operation<ItemStack> original) {
      return NoInvLeakModule.dragged((ItemStack)original.call(new Object[]{stack, count}));
   }

   @WrapOperation(
      method = "drawSlot",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getBackgroundSprite()Lnet/minecraft/util/Identifier;")
   )
   private Identifier anubis$noInvLeakSilhouette(Slot slot, Operation<Identifier> original) {
      Identifier icon = (Identifier)original.call(new Object[]{slot});
      return icon != null && NoInvLeakModule.overlaying() && NoInvLeakModule.ownSlot(slot) && slot.hasStack() ? null : icon;
   }

   @ModifyVariable(method = "drawItem", at = @At("HEAD"), argsOnly = true)
   private ItemStack anubis$noInvLeakCarried(ItemStack stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
      return NoInvLeakModule.carried(stack, x, y);
   }

   @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
   private void anubis$noInvLeakTooltip(DrawContext guiGraphics, int x, int y, CallbackInfo ci) {
      if (NoInvLeakModule.hidesTooltips() && NoInvLeakModule.ownSlot(this.focusedSlot)) {
         if (NoInvLeakModule.overlaying() && this.focusedSlot.hasStack() && this.getScreenHandler().getCursorStack().isEmpty()) {
            StreamOverlay.tooltip(this.focusedSlot.getStack(), x, y);
         }

         ci.cancel();
      }
   }
}
