package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class MixinSneakLocalPlayer {
   @ModifyExpressionValue(
      method = "tick()V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/input/Input;playerInput:Lnet/minecraft/util/PlayerInput;", opcode = 180),
      require = 3,
      allow = 3
   )
   private PlayerInput anubis$packetSneak(PlayerInput original) {
      SneakModule sneak = SneakModule.active;
      return sneak != null && !original.sneak() && sneak.doPacket()
         ? new PlayerInput(
            original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, original.sprint()
         )
         : original;
   }
}
