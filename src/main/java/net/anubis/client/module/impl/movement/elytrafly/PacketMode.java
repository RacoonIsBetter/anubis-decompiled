package net.anubis.client.module.impl.movement.elytrafly;

import net.anubis.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;

@Environment(EnvType.CLIENT)
public final class PacketMode extends ElytraFlightMode {
   public PacketMode(ElytraFlyModule elytraFly) {
      super(elytraFly);
   }

   @Override
   public void onDeactivate() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         PlayerAbilities abilities = player.getAbilities();
         abilities.flying = false;
         if (!abilities.creativeMode && !player.isSpectator()) {
            abilities.allowFlying = false;
         }
      }
   }

   @Override
   public void onTick() {
      super.onTick();
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
            && !(player.fallDistance <= 0.2)
            && !this.mc.options.sneakKey.isPressed()) {
            player.setVelocity(Vec3d.ZERO);
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
            player.networkHandler.sendPacket(new OnGroundOnly(true, player.horizontalCollision));
         }
      }
   }

   @Override
   public void onPacketSend(Packet<?> packet) {
      if (packet instanceof PlayerMoveC2SPacket) {
         ClientPlayerEntity player = this.mc.player;
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
      }
   }

   @Override
   public void onPlayerMove() {
      PlayerAbilities abilities = this.mc.player.getAbilities();
      abilities.flying = true;
      abilities.setFlySpeed(this.elytraFly.horizontalSpeed.get() / 20.0F);
   }
}
