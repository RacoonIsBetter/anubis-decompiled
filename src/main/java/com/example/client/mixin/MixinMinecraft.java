package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.AnubisTitleScreen;
import net.anubis.client.module.impl.misc.AutoEatModule;
import net.anubis.client.util.PerformanceBudget;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MixinMinecraft {
   @Shadow
   public ClientWorld world;

   @Shadow
   public abstract void setScreen(Screen var1);

   @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), cancellable = true)
   private void anubis$replaceTitleScreen(Screen screen, CallbackInfo ci) {
      if (!(screen instanceof AnubisTitleScreen)) {
         boolean title = screen instanceof TitleScreen || screen == null && this.world == null;
         if (title && AnubisTitleScreen.enabled()) {
            ci.cancel();
            this.setScreen(new AnubisTitleScreen());
         }
      }
   }

   @Inject(method = "getWindowTitle()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private void anubis$windowTitle(CallbackInfoReturnable<String> cir) {
      if (AnubisTitleScreen.enabled()) {
         cir.setReturnValue("Minecraft " + SharedConstants.getGameVersion().name() + " - Anubis Client");
      }
   }

   @Inject(method = "tick()V", at = @At("HEAD"))
   private void anubis$onTickStart(CallbackInfo ci) {
      PerformanceBudget.beginTick();
      EventBus.INSTANCE.drainAsync();
      ServerRotation.startTick();
      TickEvent.dispatch();
   }

   @Inject(method = "tick()V", at = @At("RETURN"))
   private void anubis$onTickEnd(CallbackInfo ci) {
      TickEvent.dispatchPost();
   }

   @ModifyExpressionValue(
      method = "doItemUse()V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;")
   )
   private Type anubis$autoEatIgnoreCrosshair(Type type) {
      return AutoEatModule.isEating() ? Type.MISS : type;
   }
}
