package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.ItemEnchants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AhSellModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int SLOTS = 9;
   private static final String FIXED = "Fixed";
   private static final String AUTO = "Auto (beat cheapest)";
   private final ModeSetting count = this.addSetting("items", new ModeSetting("Items", "1", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
   private final ModeSetting pricing = this.addSetting("pricing", new ModeSetting("Pricing", "Fixed", "Fixed", "Auto (beat cheapest)"));
   private final StringSetting[] items = new StringSetting[9];
   private final StringSetting[] enchants = new StringSetting[9];
   private final StringSetting[] prices = new StringSetting[9];
   private final StringSetting undercut;
   private final NumberSetting priceCheck;
   private final NumberSetting relistGap;
   private final NumberSetting ignoreDrops;
   private final NumberSetting salesCheck;
   private final NumberSetting perListing;
   private final NumberSetting maxListings;
   private final NumberSetting listingDelay;
   private final MacroWorker worker = new MacroWorker("Anubis AH Sell");
   private volatile AhSellBot bot;

   public AhSellModule() {
      super(
         "ah-sell", "AH Sell", "Lists up to 9 items from your inventory on the Auction House at your price or the cheapest price.", ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();

      for (int i = 0; i < 9; i++) {
         int slot = i + 1;
         int index = i;
         this.items[i] = this.addSetting("item-" + slot, new StringSetting("Item " + slot, "").hint("e.g. diamond_block").itemPicker());
         this.enchants[i] = this.addSetting("enchants-" + slot, new StringSetting("Enchantments " + slot, "").enchantsFor(this.items[i]));
         this.prices[i] = this.addSetting("price-" + slot, new StringSetting("Price " + slot + " (each)", "").hint("e.g. 5k (Auto: lowest price)"));
         this.items[i].visibleWhen(() -> slot <= this.slotCount());
         this.enchants[i].visibleWhen(() -> slot <= this.slotCount() && ItemEnchants.supports(Product.resolveItem(this.items[index].get())));
         this.prices[i].visibleWhen(() -> slot <= this.slotCount());
      }

      this.undercut = this.addSetting("undercut", new StringSetting("Undercut By", "100").hint("e.g. 100"));
      this.priceCheck = this.addSetting("price-check", new NumberSetting("Price Check (s)", 120.0F, 10.0F, 600.0F).step(10.0F));
      this.relistGap = this.addSetting("relist-gap", new NumberSetting("Relist Higher At (x undercut)", 3.0F, 1.0F, 20.0F).step(0.5F));
      this.ignoreDrops = this.addSetting("ignore-drops", new NumberSetting("Ignore Drops Over (x undercut)", 20.0F, 1.0F, 100.0F).step(1.0F));
      this.salesCheck = this.addSetting("sales-check", new NumberSetting("Sales Check (s)", 20.0F, 5.0F, 300.0F).step(5.0F));
      this.perListing = this.addSetting("per-listing", new NumberSetting("Items Per Listing", 1.0F, 1.0F, 64.0F).step(1.0F));
      this.maxListings = this.addSetting("max-listings", new NumberSetting("Max Listings", 25.0F, 1.0F, 50.0F).step(1.0F));
      this.listingDelay = this.addSetting("listing-delay", new NumberSetting("Listing Delay (s)", 0.0F, 0.0F, 5.0F).step(0.25F));
      this.undercut.visibleWhen(this::auto);
      this.priceCheck.visibleWhen(this::auto);
      this.relistGap.visibleWhen(this::auto);
      this.ignoreDrops.visibleWhen(this::auto);
   }

   private int slotCount() {
      return Integer.parseInt(this.count.get());
   }

   private boolean auto() {
      return this.pricing.is("Auto (beat cheapest)");
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getSession() != null) {
         List<Product> products = new ArrayList<>();
         String problem = this.buildProducts(products);
         if (problem != null) {
            this.info(ModuleChat.body().warn(problem));
            this.requestDisable();
         } else {
            AuctionHouse.Config config = new AuctionHouse.Config(
               Math.max(0L, ServerText.parseMoney(this.undercut.get())),
               (long)(this.priceCheck.get() * 1000.0F),
               (long)(this.salesCheck.get() * 1000.0F),
               this.perListing.getInt(),
               this.maxListings.getInt(),
               (long)(this.listingDelay.get() * 1000.0F),
               this.relistGap.get(),
               this.ignoreDrops.get(),
               36,
               15000L
            );
            AhSellBot created = new AhSellBot(this, this.worker, products, !this.auto(), config, mc.getSession().getUsername());
            this.bot = created;
            this.worker.start(created::run, failure -> {
               LOGGER.error("[Anubis] AH Sell stopped", failure);
               this.info(ModuleChat.body().danger("Stopped").text(": " + failure));
               mc.execute(() -> {
                  if (this.bot == created) {
                     this.setEnabled(false);
                  }
               });
            });
         }
      } else {
         this.info(ModuleChat.body().warn("Join a server first."));
         this.requestDisable();
      }
   }

   @Override
   protected void onDisable() {
      AhSellBot stopped = this.bot;
      this.bot = null;
      this.worker.stop();
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (stopped != null && player != null) {
         if (player.currentScreenHandler != player.playerScreenHandler) {
            player.closeHandledScreen();
         } else {
            Dialogs.close(mc);
         }

         this.info(ModuleChat.body().text(stopped.summary()));
      }
   }

   @EventHandler
   public void onPacket(PacketReceiveNettyEvent event) {
      AhSellBot current = this.bot;
      if (current != null && event.packet instanceof GameMessageS2CPacket chat && chat.content() != null) {
         current.onChat(chat.content().getString());
      }
   }

   private String buildProducts(List<Product> out) {
      if (this.auto() && ServerText.parseMoney(this.undercut.get()) < 0L) {
         return "Undercut By must be a price like 100.";
      }

      Set<Item> seen = new HashSet<>();

      for (int i = 0; i < this.slotCount(); i++) {
         String raw = this.items[i].get().trim();
         if (!raw.isEmpty()) {
            Item item = Product.resolveItem(raw);
            if (item == null) {
               return "Item " + (i + 1) + ": unknown item '" + raw + "'.";
            }

            if (!seen.add(item)) {
               return "Item " + (i + 1) + ": " + raw + " is listed twice.";
            }

            long price = ServerText.parseMoney(this.prices[i].get());
            if (price <= 0L) {
               return "Item " + (i + 1) + ": set a price like 5k" + (this.auto() ? " (the lowest price Auto may use)." : ".");
            }

            List<Product.Enchant> wanted = ItemEnchants.supports(item) ? Product.parseEnchants(this.enchants[i].get()) : List.of();
            out.add(new Product(item, raw, Product.searchText(item), new ItemStack(item).getName().getString(), price, wanted, false, 1, true, 1L, 1L, 0L));
         }
      }

      return out.isEmpty() ? "Set at least one item." : null;
   }

   private void info(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }
}
