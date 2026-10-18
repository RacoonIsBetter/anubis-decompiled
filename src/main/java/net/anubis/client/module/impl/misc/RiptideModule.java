package net.anubis.client.module.impl.misc;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class RiptideModule extends ClientModule {
   private int releasedTick = -100;

   public RiptideModule() {
      super("riptide", "Riptide", "Uses a riptide trident while you hold right-click.", ModuleCategory.DONUT);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null && mc.currentScreen == null) {
         if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) == 1) {
            if (GrimRiptide.clientAccepts()) {
               Hand hand = hand(player);
               if (hand != null) {
                  if (!player.isUsingItem()) {
                     if (player.age - this.releasedTick >= 9) {
                        mc.interactionManager.interactItem(player, hand);
                     }
                  } else if (GrimRiptide.isRiptide(player.getActiveItem())) {
                     if (player.getItemUseTime() >= chargeTicks()) {
                        if (player.age - this.releasedTick >= 9) {
                           mc.interactionManager.stopUsingItem(player);
                           this.releasedTick = player.age;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int chargeTicks() {
      return Math.max(9, TridentUtilModule.minChargeTicks(10));
   }

   private static Hand hand(ClientPlayerEntity player) {
      if (GrimRiptide.isRiptide(player.getMainHandStack())) {
         return Hand.MAIN_HAND;
      } else {
         return GrimRiptide.isRiptide(player.getOffHandStack()) ? Hand.OFF_HAND : null;
      }
   }
}
