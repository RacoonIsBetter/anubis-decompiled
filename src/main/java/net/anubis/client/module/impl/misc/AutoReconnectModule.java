package net.anubis.client.module.impl.misc;

import java.util.Locale;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterTick;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

@Environment(EnvType.CLIENT)
public final class AutoReconnectModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 5.0F, 3.0F, 60.0F).step(1.0F));
   private ServerInfo lastServer;
   private Screen activeScreen;
   private ButtonWidget reconnectButton;
   private int ticksLeft;

   public AutoReconnectModule() {
      super("auto-reconnect", "Auto Reconnect", "Reconnects to the last server after an unexpected disconnect.", ModuleCategory.MISC);
      TickEvent.TYPE.listen(this, this::trackServer);
      ScreenEvents.AFTER_INIT.register(this::onScreenInit);
   }

   private void trackServer(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         ServerInfo server = mc.isInSingleplayer() ? null : mc.getCurrentServerEntry();
         this.lastServer = server != null && !server.isLocal() && !server.isRealm() ? server : null;
      }
   }

   private void onScreenInit(MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
      if (screen instanceof TitleScreen || screen instanceof MultiplayerScreen) {
         this.lastServer = null;
      } else if (screen instanceof DisconnectedScreen && this.lastServer != null) {
         ServerInfo server = this.lastServer;
         boolean auto = this.isEnabled();
         if (screen == this.activeScreen) {
            Screens.getButtons(screen).remove(this.reconnectButton);
         } else {
            this.activeScreen = screen;
            this.ticksLeft = Math.round(this.delay.get() * 20.0F);
         }

         ButtonWidget button = ButtonWidget.builder(reconnectLabel(auto, this.ticksLeft), btn -> {
            btn.active = false;
            this.reconnect(client, server);
         }).dimensions(scaledWidth - 150 - 10, scaledHeight - 20 - 10, 150, 20).build();
         this.reconnectButton = button;
         Screens.getButtons(screen).add(button);
         if (auto) {
            ScreenEvents.afterTick(screen).register((AfterTick)s -> {
               this.ticksLeft--;
               if (this.ticksLeft <= 0) {
                  button.active = false;
                  this.reconnect(client, server);
               } else {
                  button.setMessage(reconnectLabel(true, this.ticksLeft));
               }
            });
         }
      }
   }

   private void reconnect(MinecraftClient client, ServerInfo server) {
      ServerAddress address = ServerAddress.parse(server.address);
      ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, address, server, false, null);
   }

   private static Text reconnectLabel(boolean auto, int ticksLeft) {
      return auto ? Text.literal(String.format(Locale.ROOT, "Reconnect (%.1f)", ticksLeft / 20.0)) : Text.literal("Reconnect");
   }
}
