package net.anubis.client.module.impl.donutmacros;

import java.util.function.Predicate;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AhPurchase {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private final ClientModule owner;
   private final MacroWorker worker = new MacroWorker("Anubis AH Purchase");
   private final SniperBot bot;
   private final ListenerHandle<PacketReceiveNettyEvent> chat;
   private volatile AhPurchase.Status status = AhPurchase.Status.RUNNING;

   private AhPurchase(ClientModule owner, SnipeTarget target, int amount, long refreshMs, long budget) {
      this.owner = owner;
      this.bot = SniperBot.quota(owner, this::stopped, this.worker, target, amount, refreshMs, budget);
      this.chat = PacketReceiveNettyEvent.TYPE.listen(this, Stage.PRE, EventPriority.NORMAL, event -> {
         if (this.status == AhPurchase.Status.RUNNING) {
            if (event.packet instanceof GameMessageS2CPacket packet && packet.content() != null) {
               if (packet.overlay()) {
                  this.bot.onNote(packet.content().getString());
               } else {
                  this.bot.onChat(packet.content().getString());
               }
            } else if (event.packet instanceof OverlayMessageS2CPacket bar && bar.text() != null) {
               this.bot.onNote(bar.text().getString());
            }
         }
      });
      this.worker.start(this.bot::run, failure -> {
         LOGGER.error("[Anubis] AH purchase stopped", failure);
         ModuleChat.send(owner, ModuleChat.body().danger("AH purchase stopped").text(": " + failure));
         this.stopped(this.bot);
      });
   }

   public static AhPurchase start(ClientModule owner, Item item, int amount, long maxPerListing, long refreshMs, long budget) {
      SnipeTarget target = SnipeTarget.matching(
         item,
         new ItemStack(item).getName().getString(),
         Product.searchText(item),
         stack -> stack.isOf(item) && !stack.contains(DataComponentTypes.BLOCK_STATE) && !stack.contains(DataComponentTypes.BLOCK_ENTITY_DATA),
         maxPerListing
      );
      return new AhPurchase(owner, target, amount, refreshMs, budget);
   }

   public static AhPurchase startAny(
      ClientModule owner, Item shown, String name, String search, Predicate<ItemStack> filter, int amount, long maxPerListing, long refreshMs, long budget
   ) {
      return new AhPurchase(owner, SnipeTarget.matching(shown, name, search, filter, maxPerListing), amount, refreshMs, budget);
   }

   public static double unitPrice(ItemStack listing) {
      double total = ServerText.askingPrice(listing);
      return total <= 0.0 ? Double.MAX_VALUE : total / Math.max(1, listing.getCount());
   }

   public static long parseMoney(String raw) {
      return ServerText.parseMoney(raw);
   }

   public static String money(double value) {
      return ServerText.shortMoney(value);
   }

   public AhPurchase.Status status() {
      return this.status;
   }

   public int boughtItems() {
      return this.bot.boughtItems();
   }

   public long spent() {
      return this.bot.spent();
   }

   public void cancel() {
      if (this.status == AhPurchase.Status.RUNNING) {
         this.finish(AhPurchase.Status.FAILED);
      }
   }

   private void stopped(SniperBot from) {
      this.finish(from.complete() ? AhPurchase.Status.DONE : AhPurchase.Status.FAILED);
   }

   private synchronized void finish(AhPurchase.Status result) {
      if (this.status == AhPurchase.Status.RUNNING) {
         this.status = result;
         this.chat.unregister();
         this.worker.stop();
         MinecraftClient.getInstance().execute(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            if (player != null) {
               if (player.currentScreenHandler != player.playerScreenHandler) {
                  player.closeHandledScreen();
               } else {
                  Dialogs.close(mc);
               }
            }
         });
         if (result == AhPurchase.Status.FAILED) {
            LOGGER.info("[Anubis] AH purchase for {} ended without finishing", this.owner.name());
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Status {
      RUNNING,
      DONE,
      FAILED;
   }
}
