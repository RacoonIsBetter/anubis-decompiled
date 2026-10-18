package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.anubis.client.module.impl.render.TimeChangerModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.ClientWorld.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class MixinTimeChangerClientLevel {
   @WrapOperation(method = "setTime(JJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld$Properties;setTimeOfDay(J)V"))
   private void anubis$timeChangerServerDayTime(Properties levelData, long dayTime, Operation<Void> original) {
      TimeChangerModule timeChanger = TimeChangerModule.active;
      if (timeChanger == null) {
         original.call(new Object[]{levelData, dayTime});
      } else {
         ClientWorld level = (ClientWorld)(Object)this;
         long shown = levelData.getTimeOfDay();
         long forced = timeChanger.interceptServerDayTime(level, dayTime);
         original.call(new Object[]{levelData, forced});
         if (forced != shown) {
            level.getEnvironmentAttributes().tick();
         }
      }
   }
}
