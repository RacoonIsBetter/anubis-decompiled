package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
final class Relogger {
   private static final int WAIT_TICKS = 60;
   private static final int TRIES = 5;
   private ServerInfo server;
   private boolean out;
   private int wait;
   private int tries;

   boolean active() {
      return this.server != null;
   }

   boolean start(MinecraftClient mc) {
      ServerInfo data = mc.isInSingleplayer() ? null : mc.getCurrentServerEntry();
      if (data != null && !data.isRealm() && mc.world != null) {
         this.server = data;
         this.out = false;
         this.wait = 60;
         this.tries = 0;
         mc.send(() -> {
            if (this.server == data && mc.world != null) {
               mc.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
               this.out = true;
            }
         });
         return true;
      } else {
         return false;
      }
   }

   void tick(MinecraftClient mc) {
      if (this.server != null && this.out) {
         if (mc.world != null) {
            this.server = null;
         } else if (!(mc.currentScreen instanceof ConnectScreen) && --this.wait <= 0) {
            if (++this.tries > 5) {
               this.server = null;
            } else {
               ServerInfo data = this.server;
               Screen parent = mc.currentScreen instanceof MultiplayerScreen ? mc.currentScreen : new MultiplayerScreen(new TitleScreen());
               this.wait = 180;
               mc.send(() -> {
                  if (mc.world == null) {
                     ConnectScreen.connect(parent, mc, ServerAddress.parse(data.address), data, false, null);
                  }
               });
            }
         }
      }
   }
}
