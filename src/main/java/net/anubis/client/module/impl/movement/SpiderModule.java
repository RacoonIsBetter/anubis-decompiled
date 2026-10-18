package net.anubis.client.module.impl.movement;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class SpiderModule extends ClientModule {
   private static final double CLIMB_VELOCITY_THRESHOLD = 0.2;
   private final NumberSetting climbSpeed = this.addSetting("climb-speed", new NumberSetting("Climb Speed", 0.2F, 0.0F, 10.0F).step(0.1F));

   public SpiderModule() {
      super("spider", "Spider", "Allows you to climb walls like a spider.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   private void onPostTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && player.horizontalCollision) {
         Vec3d velocity = player.getVelocity();
         if (!(velocity.y >= 0.2)) {
            player.setVelocity(velocity.x, this.climbSpeed.get(), velocity.z);
         }
      }
   }
}
