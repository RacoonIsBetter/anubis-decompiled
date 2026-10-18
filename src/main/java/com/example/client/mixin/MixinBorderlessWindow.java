package com.example.client.mixin;

import net.anubis.client.gui.BorderlessFullscreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.VideoMode;
import net.minecraft.client.util.MonitorTracker;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinBorderlessWindow implements BorderlessFullscreen.Switchable {
   @Shadow
   @Final
   private long handle;
   @Shadow
   @Final
   private MonitorTracker monitorTracker;
   @Shadow
   private boolean fullscreen;
   @Shadow
   private boolean currentFullscreen;
   @Shadow
   private int windowedX;
   @Shadow
   private int windowedY;
   @Shadow
   private int windowedWidth;
   @Shadow
   private int windowedHeight;
   @Shadow
   private int x;
   @Shadow
   private int y;
   @Shadow
   private int width;
   @Shadow
   private int height;
   @Unique
   private boolean anubis$borderless;

   @Inject(method = "updateWindowRegion()V", at = @At("HEAD"), cancellable = true)
   private void anubis$setMode(CallbackInfo ci) {
      if (this.fullscreen && BorderlessFullscreen.wanted()) {
         Monitor monitor = this.monitorTracker.getMonitor((Window)(Object)this);
         if (monitor != null) {
            boolean exclusive = GLFW.glfwGetWindowMonitor(this.handle) != 0L;
            if (!exclusive && !this.anubis$borderless) {
               this.windowedX = this.x;
               this.windowedY = this.y;
               this.windowedWidth = this.width;
               this.windowedHeight = this.height;
            }

            VideoMode mode = monitor.getCurrentVideoMode();
            this.x = monitor.getViewportX();
            this.y = monitor.getViewportY();
            this.width = mode.getWidth();
            this.height = mode.getHeight();
            GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
            GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height, -1);
            if (exclusive) {
               GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
            }

            this.anubis$borderless = true;
            ci.cancel();
         }
      } else {
         if (this.anubis$borderless) {
            this.anubis$leaveBorderless();
         }
      }
   }

   @Unique
   private void anubis$leaveBorderless() {
      this.anubis$borderless = false;
      GLFW.glfwSetWindowAttrib(this.handle, 131077, 1);
      this.x = this.windowedX;
      this.y = this.windowedY;
      this.width = this.windowedWidth;
      this.height = this.windowedHeight;
   }

   @Override
   public boolean anubis$isBorderless() {
      return this.anubis$borderless;
   }

   @Override
   public void anubis$reapplyMode() {
      this.currentFullscreen = !this.fullscreen;
   }
}
