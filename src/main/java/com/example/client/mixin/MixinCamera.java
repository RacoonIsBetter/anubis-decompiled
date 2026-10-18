package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class MixinCamera {
   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPos(double var1, double var3, double var5);

   @WrapOperation(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
   private void anubis$freelookRotation(Camera camera, float yRot, float xRot, Operation<Void> original) {
      FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
      if (FreecamModule.active == null && freelook != null && freelook.isEnabled()) {
         original.call(new Object[]{camera, freelook.cameraYaw(), freelook.cameraPitch()});
      } else {
         original.call(new Object[]{camera, yRot, xRot});
      }
   }

   @WrapOperation(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
   private float anubis$freelookThroughBlocks(Camera camera, float distance, Operation<Float> original) {
      return FreelookModule.throughBlocks() ? distance : (Float)original.call(new Object[]{camera, distance});
   }

   @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("RETURN"))
   private void anubis$freecamCamera(World level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null) {
         this.setPos(freecam.getX(partialTick), freecam.getY(partialTick), freecam.getZ(partialTick));
         this.setRotation(freecam.getYaw(), freecam.getPitch());
      }
   }
}
