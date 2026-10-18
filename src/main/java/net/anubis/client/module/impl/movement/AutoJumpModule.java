package net.anubis.client.module.impl.movement;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class AutoJumpModule extends ClientModule {
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Jump", "Jump", "LowHop"));
   private final ModeSetting jumpIf = this.addSetting("jump-if", new ModeSetting("Jump If", "Always", "Sprinting", "Walking", "Always"));
   private final NumberSetting velocityHeight = this.addSetting("velocity-height", new NumberSetting("Velocity Height", 0.25F, 0.0F, 2.0F).step(0.05F));

   public AutoJumpModule() {
      super("auto-jump", "Auto Jump", "Automatically jumps.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
   }

   private void onPreTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (player.isOnGround() && !player.isSneaking() && this.shouldJump(player)) {
            if (this.mode.is("Jump")) {
               player.jump();
            } else {
               Vec3d velocity = player.getVelocity();
               player.setVelocity(velocity.x, this.velocityHeight.get(), velocity.z);
            }
         }
      }
   }

   private boolean shouldJump(ClientPlayerEntity player) {
      boolean moving = player.forwardSpeed != 0.0F || player.sidewaysSpeed != 0.0F;

      return switch (this.jumpIf.get()) {
         case "Sprinting" -> player.isSprinting() && moving;
         case "Walking" -> moving;
         default -> true;
      };
   }
}
