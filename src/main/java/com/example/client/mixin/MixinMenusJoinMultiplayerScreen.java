package com.example.client.mixin;

import net.anubis.client.gui.AnubisTitleScreen;
import net.anubis.client.gui.mainmenu.MultiplayerOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiplayerScreen.class)
public abstract class MixinMenusJoinMultiplayerScreen extends Screen {
   @Unique
   private MultiplayerOverlay anubis$overlay;

   protected MixinMenusJoinMultiplayerScreen(Text title) {
      super(title);
   }

   @Inject(method = "init()V", at = @At("TAIL"))
   private void anubis$addOverlay(CallbackInfo ci) {
      this.anubis$overlay = AnubisTitleScreen.enabled() ? new MultiplayerOverlay((MultiplayerScreen)(Object)this) : null;
      if (this.anubis$overlay != null) {
         this.anubis$overlay.show();
      }
   }

   @Inject(method = "refreshWidgetPositions()V", at = @At("TAIL"))
   private void anubis$showOverlay(CallbackInfo ci) {
      if (this.anubis$overlay != null) {
         this.anubis$overlay.show();
      }
   }

   @Inject(method = "removed()V", at = @At("TAIL"))
   private void anubis$hideOverlay(CallbackInfo ci) {
      if (this.anubis$overlay != null) {
         this.anubis$overlay.hide();
      }
   }

   @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true)
   private void anubis$overlayKeys(KeyInput event, CallbackInfoReturnable<Boolean> cir) {
      if (this.anubis$overlay != null && this.anubis$overlay.keyPressed(event.key())) {
         cir.setReturnValue(true);
      }
   }

   public boolean mouseClicked(Click event, boolean doubleClick) {
      return this.anubis$takesPointer() || super.mouseClicked(event, doubleClick);
   }

   public boolean mouseReleased(Click event) {
      return this.anubis$takesPointer() || super.mouseReleased(event);
   }

   public boolean mouseDragged(Click event, double dragX, double dragY) {
      return this.anubis$takesPointer() || super.mouseDragged(event, dragX, dragY);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return this.anubis$takesPointer() || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   @Unique
   private boolean anubis$takesPointer() {
      return this.anubis$overlay != null && this.anubis$overlay.takesPointer();
   }
}
