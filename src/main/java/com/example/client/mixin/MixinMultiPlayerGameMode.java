package com.example.client.mixin;

import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.WTapRecodeModule;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinMultiPlayerGameMode {
   @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V"))
   private void anubis$beforeAttack(PlayerEntity player, Entity entity, CallbackInfo ci) {
      WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
      if (wTap != null && wTap.isEnabled()) {
         wTap.beforeAttack(player, entity);
      }
   }

   @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
   private void anubis$afterAttack(PlayerEntity player, Entity entity, CallbackInfo ci) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }

      WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
      if (wTap != null && wTap.isEnabled()) {
         wTap.afterAttack();
      }
   }

   @Inject(method = "attackWithPiercingWeapon(Lnet/minecraft/component/type/PiercingWeaponComponent;)V", at = @At("RETURN"))
   private void anubis$afterStab(PiercingWeaponComponent weapon, CallbackInfo ci) {
      MeleeAttacks.noteAction(MinecraftClient.getInstance().player);
      WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
      if (wTap != null && wTap.isEnabled()) {
         wTap.afterStab();
      }
   }

   @Inject(method = "interactItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
   private void anubis$afterUseItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }
   }

   @Inject(method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
   private void anubis$afterUseItemOn(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }
   }

   @Inject(method = "interactEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
   private void anubis$afterInteract(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }
   }

   @Inject(method = "interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
   private void anubis$afterInteractAt(PlayerEntity player, Entity entity, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }
   }

   @Inject(method = "stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("RETURN"))
   private void anubis$afterRelease(PlayerEntity player, CallbackInfo ci) {
      if (player == MinecraftClient.getInstance().player) {
         MeleeAttacks.noteAction(player);
      }
   }
}
