package com.example.client.mixin;

import net.anubis.client.util.playerbypass2.SimulationSignals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SimulationDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinPlayerBypass2ClientPacketListener {
   private static final String ON_MAIN_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

   @Inject(
      method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Login(GameJoinS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onLogin(packet.simulationDistance(), packet.viewDistance(), packet.commonPlayerSpawnInfo().dimension());
   }

   @Inject(
      method = "onPlayerRespawn(Lnet/minecraft/network/packet/s2c/play/PlayerRespawnS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Respawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onRespawn(packet.commonPlayerSpawnInfo().dimension());
   }

   @Inject(
      method = "onPlayerPositionLook(Lnet/minecraft/network/packet/s2c/play/PlayerPositionLookS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Teleport(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onTeleport();
   }

   @Inject(
      method = "onSimulationDistance(Lnet/minecraft/network/packet/s2c/play/SimulationDistanceS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Simulation(SimulationDistanceS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onSimulationDistance(packet.simulationDistance());
   }

   @Inject(
      method = "onChunkLoadDistance(Lnet/minecraft/network/packet/s2c/play/ChunkLoadDistanceS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2View(ChunkLoadDistanceS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onViewDistance(packet.getDistance());
   }

   @Inject(method = "onChunkData(Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;)V", at = @At("TAIL"))
   private void anubis$pb2Chunk(ChunkDataS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onChunk(packet.getChunkX(), packet.getChunkZ());
   }

   @Inject(
      method = "onEntity(Lnet/minecraft/network/packet/s2c/play/EntityS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Move(EntityS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onMove(packet);
   }

   @Inject(
      method = "onEntityPositionSync(Lnet/minecraft/network/packet/s2c/play/EntityPositionSyncS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2PositionSync(EntityPositionSyncS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onPositionSync(packet);
   }

   @Inject(
      method = "onEntitySetHeadYaw(Lnet/minecraft/network/packet/s2c/play/EntitySetHeadYawS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Head(EntitySetHeadYawS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onHeadRotation(packet);
   }

   @Inject(
      method = "onEntityTrackerUpdate(Lnet/minecraft/network/packet/s2c/play/EntityTrackerUpdateS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2EntityData(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onEntityData(packet);
   }

   @Inject(
      method = "onEntityStatus(Lnet/minecraft/network/packet/s2c/play/EntityStatusS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2EntityEvent(EntityStatusS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onEntityEvent(packet);
   }

   @Inject(
      method = "onEntitySpawn(Lnet/minecraft/network/packet/s2c/play/EntitySpawnS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2AddEntity(EntitySpawnS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onAddEntity(packet);
   }

   @Inject(
      method = "onEntitiesDestroy(Lnet/minecraft/network/packet/s2c/play/EntitiesDestroyS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2RemoveEntities(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onRemoveEntities(packet.getEntityIds());
   }

   @Inject(
      method = "onBlockUpdate(Lnet/minecraft/network/packet/s2c/play/BlockUpdateS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onBlockUpdate(packet);
   }

   @Inject(
      method = "onChunkDeltaUpdate(Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Section(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onSectionUpdate(packet);
   }

   @Inject(
      method = "onItemPickupAnimation(Lnet/minecraft/network/packet/s2c/play/ItemPickupAnimationS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2TakeItem(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onTakeItem(packet);
   }

   @Inject(
      method = "onEntityDamage(Lnet/minecraft/network/packet/s2c/play/EntityDamageS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Damage(EntityDamageS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onDamage(packet);
   }

   @Inject(
      method = "onEntityEquipmentUpdate(Lnet/minecraft/network/packet/s2c/play/EntityEquipmentUpdateS2CPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )
   )
   private void anubis$pb2Equipment(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
      SimulationSignals.onEquipment(packet);
   }
}
