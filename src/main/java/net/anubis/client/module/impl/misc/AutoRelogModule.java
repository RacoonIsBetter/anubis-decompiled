package net.anubis.client.module.impl.misc;

import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.LevelUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AutoRelogModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int REARM_HEIGHT = 4;
   private static final int REARM_TICKS = 40;
   private final NumberSetting yLevel = this.addSetting("y-level", new NumberSetting("Y Level", 0.0F, -60.0F, 16.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 3.0F, 1.0F, 30.0F).step(0.5F));
   private ClientWorld seenLevel;
   private ClientPlayerEntity seenPlayer;
   private int aboveTicks;
   private ServerInfo rejoin;
   private boolean disconnected;
   private int rejoinTicks;

   public AutoRelogModule() {
      super("auto-relog", "Auto Relog", "Relogs once when you go down to deepslate level.", ModuleCategory.DONUT);
   }

   @Override
   protected void onEnable() {
      this.forget();
      this.on(TickEvent.TYPE, event -> this.onTick());
   }

   @Override
   protected void onDisable() {
      this.forget();
   }

   private void forget() {
      this.seenLevel = null;
      this.seenPlayer = null;
      this.aboveTicks = 0;
      this.rejoin = null;
      this.disconnected = false;
   }

   private void onTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.rejoin != null) {
         this.waitToRejoin(mc);
      } else {
         ClientPlayerEntity player = mc.player;
         ClientWorld level = mc.world;
         if (player != null && level != null && !(mc.currentScreen instanceof LevelLoadingScreen)) {
            if (level != this.seenLevel || player != this.seenPlayer) {
               this.seenLevel = level;
               this.seenPlayer = player;
               this.aboveTicks = 0;
            }

            if (!LevelUtil.isOverworld(level)) {
               this.aboveTicks = 0;
            } else {
               double y = player.getY();
               float line = this.yLevel.get();
               if (y >= line + 4.0F) {
                  if (this.aboveTicks < 40) {
                     this.aboveTicks++;
                  }
               } else if (y < line) {
                  boolean armed = this.aboveTicks >= 40;
                  this.aboveTicks = 0;
                  if (armed) {
                     this.relog(mc, y);
                  }
               }
            }
         } else {
            this.aboveTicks = 0;
         }
      }
   }

   private void relog(MinecraftClient mc, double y) {
      ServerInfo server = mc.isInSingleplayer() ? null : mc.getCurrentServerEntry();
      if (server != null && !server.isRealm()) {
         LOGGER.info("[Anubis] Auto Relog: reached Y {}, relogging", (int)Math.floor(y));
         this.rejoin = server;
         this.disconnected = false;
         this.rejoinTicks = Math.round(this.delay.get() * 20.0F);
         mc.send(() -> {
            if (this.rejoin == server && mc.world != null) {
               mc.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
               this.disconnected = true;
            }
         });
      }
   }

   private void waitToRejoin(MinecraftClient mc) {
      if (this.disconnected) {
         if (mc.world == null && mc.currentScreen instanceof MultiplayerScreen) {
            if (--this.rejoinTicks <= 0) {
               ServerInfo server = this.rejoin;
               Screen serverList = mc.currentScreen;
               this.rejoin = null;
               mc.send(() -> {
                  if (mc.currentScreen == serverList && mc.world == null) {
                     ConnectScreen.connect(serverList, mc, ServerAddress.parse(server.address), server, false, null);
                  }
               });
            }
         } else {
            this.rejoin = null;
         }
      }
   }
}
