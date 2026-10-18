package net.anubis.client.module.impl.combat;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class JumpResetModule extends ClientModule {
   private static final double MIN_KNOCKBACK = 0.1;
   private boolean pending;

   public JumpResetModule() {
      super("jump-reset", "Jump Reset", "Jumps the tick knockback lands to take less of it.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.pending = false;
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof EntityVelocityUpdateS2CPacket motion) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && motion.getEntityId() == player.getId()) {
            Vec3d knockback = motion.getVelocity();
            if (knockback.horizontalLength() >= 0.1 && knockback.y > 0.0) {
               this.pending = true;
            }
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (this.pending) {
         this.pending = false;
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.currentScreen == null) {
            if (player.isOnGround() && player.isSprinting() && player.input.playerInput.forward()) {
               if (!player.isTouchingWater() && !player.isInLava() && !player.isClimbing() && !player.hasVehicle() && !player.isGliding()) {
                  InputControl.jump();
               }
            }
         }
      }
   }
}
