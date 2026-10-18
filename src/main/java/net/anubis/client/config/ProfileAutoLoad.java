package net.anubis.client.config;

import java.lang.ref.WeakReference;
import java.util.List;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ProfileAutoLoad {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Object OWNER = new Object();
   private static final long TOAST_WAIT_NANOS = 15000000000L;
   private static WeakReference<ClientConnection> handled = new WeakReference<>(null);
   private static String pendingTitle;
   private static String pendingMessage;
   private static long pendingSince;

   private ProfileAutoLoad() {
   }

   public static void install() {
      PacketAppliedEvent.TYPE.listen(OWNER, event -> {
         if (event.packet instanceof GameJoinS2CPacket) {
            onJoin();
         }
      });
      TickEvent.TYPE.listen(OWNER, event -> showPendingToast());
   }

   private static void onJoin() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.isInSingleplayer()) {
         ClientPlayNetworkHandler listener = mc.getNetworkHandler();
         ServerInfo server = listener == null ? null : listener.getServerInfo();
         if (server != null && server.address != null) {
            ClientConnection connection = listener.getConnection();
            if (connection != handled.get()) {
               handled = new WeakReference<>(connection);
               List<String> loaded = ConfigProfiles.getInstance().loadForServer(server.address);
               if (!loaded.isEmpty()) {
                  LOGGER.info("[Anubis] Joined {} - loaded profile {}", server.address, String.join(", ", loaded));
                  pendingTitle = loaded.size() == 1 ? "Profile loaded" : "Profiles loaded";
                  pendingMessage = String.join(", ", loaded);
                  pendingSince = System.nanoTime();
               }
            }
         }
      }
   }

   private static void showPendingToast() {
      if (pendingTitle != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         boolean expired = System.nanoTime() - pendingSince > 15000000000L;
         if (expired || mc.player != null && !(mc.currentScreen instanceof LevelLoadingScreen)) {
            String title = pendingTitle;
            String message = pendingMessage;
            pendingTitle = null;
            pendingMessage = null;
            if (mc.world != null) {
               ChallengeToast.addOrUpdate(new ItemStack(Items.WRITABLE_BOOK), title, Text.literal(message), false);
            }
         }
      }
   }
}
