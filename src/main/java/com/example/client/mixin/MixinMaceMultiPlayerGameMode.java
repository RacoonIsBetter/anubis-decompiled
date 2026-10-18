package com.example.client.mixin;

import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.MaceModule;
import net.anubis.client.module.impl.combat.ShieldBreakerModule;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinMaceMultiPlayerGameMode {
   @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V"))
   private void anubis$weaponBeforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (player == self && !MeleeAttacks.actedThisTick(self)) {
         ShieldBreakerModule shieldBreaker = ModuleRegistry.get(ShieldBreakerModule.class);
         if (shieldBreaker != null && shieldBreaker.isEnabled()) {
            shieldBreaker.beforeAttack(self, target);
         }

         MaceModule mace = ModuleRegistry.get(MaceModule.class);
         if (mace != null && mace.isEnabled()) {
            mace.beforeAttack(self, target);
         }
      }
   }
}
