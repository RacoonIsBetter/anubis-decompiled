package net.anubis.client.module.impl.misc;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.apache.commons.lang3.RandomStringUtils;

@Environment(EnvType.CLIENT)
public final class SpamModule extends ClientModule {
   public static volatile SpamModule active;
   private static final int MAX_MESSAGE_LENGTH = 256;
   private final StringListSetting messages = this.addSetting("messages", new StringListSetting("Messages", "Anubis was here!"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 20.0F, 0.0F, 1200.0F).step(1.0F));
   private final BooleanSetting disableOnLeave = this.addSetting("disable-on-leave", new BooleanSetting("Disable On Leave", true));
   private final BooleanSetting disableOnDisconnect = this.addSetting("disable-on-disconnect", new BooleanSetting("Disable On Disconnect", true));
   private final BooleanSetting random = this.addSetting("randomise", new BooleanSetting("Randomise", false));
   private final BooleanSetting autoSplitMessages = this.addSetting("auto-split-messages", new BooleanSetting("Auto Split Messages", false));
   private final NumberSetting splitLength = this.addSetting("split-length", new NumberSetting("Split Length", 256.0F, 1.0F, 256.0F).step(1.0F));
   private final NumberSetting autoSplitDelay = this.addSetting("split-delay", new NumberSetting("Split Delay", 20.0F, 0.0F, 200.0F).step(1.0F));
   private final BooleanSetting bypass = this.addSetting("bypass", new BooleanSetting("Bypass", false));
   private final BooleanSetting uppercase = this.addSetting("include-uppercase-characters", new BooleanSetting("Include Uppercase Characters", true));
   private final NumberSetting length = this.addSetting("length", new NumberSetting("Length", 16.0F, 1.0F, 256.0F).step(1.0F));
   private final Random rng = new Random();
   private int messageI;
   private int timer;
   private int splitNum;
   private String text;
   private boolean resetPending;

   public SpamModule() {
      super("spam", "Spam", "Spams specified messages in chat.", ModuleCategory.MISC);
      this.splitLength.visibleWhen(this.autoSplitMessages::get);
      this.autoSplitDelay.visibleWhen(this.autoSplitMessages::get);
      this.uppercase.visibleWhen(this.bypass::get);
      this.length.visibleWhen(this.bypass::get);
   }

   @Override
   public boolean inConfigs() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.resetPending = true;
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public static void onGameLeft() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.isOnThread()) {
         mc.execute(SpamModule::onGameLeft);
      } else {
         SpamModule spam = active;
         if (spam != null) {
            spam.handleGameLeft();
         }
      }
   }

   public static void onDisconnectedScreen() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.isOnThread()) {
         mc.execute(SpamModule::onDisconnectedScreen);
      } else {
         SpamModule spam = active;
         if (spam != null && spam.isEnabled() && spam.disableOnDisconnect.get()) {
            spam.requestDisable();
         }
      }
   }

   private void handleGameLeft() {
      if (this.isEnabled()) {
         if (this.disableOnLeave.get()) {
            this.requestDisable();
         } else {
            this.resetPending = true;
         }
      }
   }

   private void onPostTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         if (this.resetPending) {
            this.resetPending = false;
            this.timer = this.delay.getInt();
            this.messageI = 0;
            this.splitNum = 0;
         }

         List<String> list = this.messages.get();
         if (!list.isEmpty()) {
            if (this.timer > 0) {
               this.timer--;
            } else {
               if (this.text == null) {
                  int i;
                  if (this.random.get()) {
                     i = this.rng.nextInt(list.size());
                  } else {
                     if (this.messageI >= list.size()) {
                        this.messageI = 0;
                     }

                     i = this.messageI++;
                  }

                  this.text = list.get(i);
                  if (this.bypass.get()) {
                     String suffix = RandomStringUtils.insecure().nextAlphabetic(this.length.getInt());
                     if (!this.uppercase.get()) {
                        suffix = suffix.toLowerCase(Locale.ROOT);
                     }

                     this.text = this.text + " " + suffix;
                  }
               }

               int chunk = this.splitLength.getInt();
               if (this.autoSplitMessages.get() && this.text.length() > chunk) {
                  int splits = (this.text.length() + chunk - 1) / chunk;
                  if (this.splitNum >= splits) {
                     this.splitNum = 0;
                  }

                  int start = this.splitNum * chunk;
                  int end = Math.min(start + chunk, this.text.length());
                  sendPlayerMsg(mc, player.networkHandler, this.text.substring(start, end));
                  this.splitNum = (this.splitNum + 1) % splits;
                  this.timer = this.autoSplitDelay.getInt();
                  if (this.splitNum == 0) {
                     this.timer = this.delay.getInt();
                     this.text = null;
                  }
               } else {
                  if (this.text.length() > 256) {
                     this.text = this.text.substring(0, 256);
                  }

                  sendPlayerMsg(mc, player.networkHandler, this.text);
                  this.timer = this.delay.getInt();
                  this.text = null;
               }
            }
         }
      }
   }

   private static void sendPlayerMsg(MinecraftClient mc, ClientPlayNetworkHandler connection, String message) {
      mc.inGameHud.getChatHud().addToMessageHistory(message);
      if (message.startsWith("/")) {
         connection.sendChatCommand(message.substring(1));
      } else {
         connection.sendChatMessage(message);
      }
   }
}
