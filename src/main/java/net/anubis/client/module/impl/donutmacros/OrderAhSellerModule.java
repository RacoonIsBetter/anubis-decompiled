package net.anubis.client.module.impl.donutmacros;

import java.util.List;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
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
public final class OrderAhSellerModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int PRODUCTS = 5;
   private static final String PRICE_BEAT = "Beat Top Order";
   private static final String PRICE_FIXED = "Fixed";
   private final ModeSetting productSlot = this.addSetting("product", new ModeSetting("Product", "1", "1", "2", "3", "4", "5"));
   private final StringSetting[] items = new StringSetting[5];
   private final StringSetting[] minPrices = new StringSetting[5];
   private final StringSetting[] enchants = new StringSetting[5];
   private final BooleanSetting[] restock = new BooleanSetting[5];
   private final StringSetting[] orderAmounts = new StringSetting[5];
   private final ModeSetting[] priceModes = new ModeSetting[5];
   private final StringSetting[] fixedPrices = new StringSetting[5];
   private final StringSetting[] bidIncrements = new StringSetting[5];
   private final StringSetting[] maxPrices = new StringSetting[5];
   private final StringSetting undercut;
   private final NumberSetting priceCheck;
   private final NumberSetting salesCheck;
   private final NumberSetting perListing;
   private final NumberSetting maxListings;
   private final NumberSetting listingDelay;
   private final NumberSetting relistGap;
   private final NumberSetting ignoreDrops;
   private final NumberSetting collectSlots;
   private final NumberSetting orderCheck;
   private final MacroWorker worker = new MacroWorker("Anubis Order AH Trader");
   private volatile OrderAhBot bot;

   public OrderAhSellerModule() {
      super(
         "order-ah-seller",
         "Order AH Trader",
         "Sells an item on the Auction House, undercuts other sellers and restocks through orders.",
         ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();

      for (int i = 0; i < 5; i++) {
         int slot = i + 1;
         String id = "product-" + slot + "-";
         this.items[i] = this.addSetting(
            id + "item", new StringSetting("Item", slot == 1 ? "barrel" : "").hint("e.g. barrel or diamond_chestplate").itemPicker()
         );
         this.minPrices[i] = this.addSetting(id + "min-price", new StringSetting("Min Price (each)", slot == 1 ? "3k" : "1k").hint("e.g. 3k"));
         this.enchants[i] = this.addSetting(id + "enchants", new StringSetting("Enchantments", "").enchantsFor(this.items[i]));
         this.restock[i] = this.addSetting(id + "restock", new BooleanSetting("Restock With Orders", false));
         this.orderAmounts[i] = this.addSetting(id + "order-amount", new StringSetting("Order Amount", "64").hint("e.g. 64"));
         this.priceModes[i] = this.addSetting(id + "order-price", new ModeSetting("Order Price", "Beat Top Order", "Beat Top Order", "Fixed"));
         this.fixedPrices[i] = this.addSetting(id + "fixed-price", new StringSetting("Fixed Order Price (each)", "100").hint("e.g. 150k"));
         this.bidIncrements[i] = this.addSetting(id + "bid-increment", new StringSetting("Beat By", "1").hint("e.g. 1 or 100"));
         this.maxPrices[i] = this.addSetting(id + "max-price", new StringSetting("Max Order Price (each)", "").hint("Empty = no limit"));
         int index = i;
         Setting[] all = new Setting[]{
            this.items[i],
            this.minPrices[i],
            this.enchants[i],
            this.restock[i],
            this.orderAmounts[i],
            this.priceModes[i],
            this.fixedPrices[i],
            this.bidIncrements[i],
            this.maxPrices[i]
         };

         for (Setting setting : all) {
            setting.visibleWhen(() -> this.selected() == index);
         }

         this.enchants[i].visibleWhen(() -> this.selected() == index && ItemEnchants.supports(Product.resolveItem(this.items[index].get())));
         this.orderAmounts[i].visibleWhen(() -> this.selected() == index && this.restock[index].get());
         this.priceModes[i].visibleWhen(() -> this.selected() == index && this.restock[index].get());
         this.fixedPrices[i].visibleWhen(() -> this.selected() == index && this.restock[index].get() && this.priceModes[index].is("Fixed"));
         this.bidIncrements[i].visibleWhen(() -> this.selected() == index && this.restock[index].get() && this.priceModes[index].is("Beat Top Order"));
         this.maxPrices[i].visibleWhen(() -> this.selected() == index && this.restock[index].get());
      }

      this.undercut = this.addSetting("undercut", new StringSetting("Undercut By", "100").hint("e.g. 100"));
      this.priceCheck = this.addSetting("price-check", new NumberSetting("Price Check (s)", 120.0F, 10.0F, 600.0F).step(10.0F));
      this.salesCheck = this.addSetting("sales-check", new NumberSetting("Sales Check (s)", 20.0F, 5.0F, 300.0F).step(5.0F));
      this.perListing = this.addSetting("per-listing", new NumberSetting("Items Per Listing", 1.0F, 1.0F, 64.0F).step(1.0F));
      this.maxListings = this.addSetting("max-listings", new NumberSetting("Max Listings", 25.0F, 1.0F, 50.0F).step(1.0F));
      this.listingDelay = this.addSetting("listing-delay", new NumberSetting("Listing Delay (s)", 0.0F, 0.0F, 5.0F).step(0.25F));
      this.relistGap = this.addSetting("relist-gap", new NumberSetting("Relist Higher At (x undercut)", 3.0F, 1.0F, 20.0F).step(0.5F));
      this.ignoreDrops = this.addSetting("ignore-drops", new NumberSetting("Ignore Drops Over (x undercut)", 20.0F, 1.0F, 100.0F).step(1.0F));
      this.collectSlots = this.addSetting("collect-slots", new NumberSetting("Collect Up To (slots)", 18.0F, 1.0F, 36.0F).step(1.0F));
      this.orderCheck = this.addSetting("order-check", new NumberSetting("Order Check (s)", 15.0F, 5.0F, 300.0F).step(5.0F));
      this.collectSlots.visibleWhen(this::anyRestock);
      this.orderCheck.visibleWhen(this::anyRestock);
   }

   private int selected() {
      return Integer.parseInt(this.productSlot.get()) - 1;
   }

   private boolean anyRestock() {
      return this.restock[this.selected()].get();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getSession() != null) {
         String problem = this.validate();
         if (problem != null) {
            this.info(ModuleChat.body().warn(problem));
            this.requestDisable();
         } else {
            OrderAhBot created = new OrderAhBot(this, this.worker, this.buildProduct(), this.buildConfig(), mc.getSession().getUsername());
            this.bot = created;
            this.worker.start(created::run, failure -> {
               LOGGER.error("[Anubis] Order AH Trader stopped", failure);
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
      OrderAhBot stopped = this.bot;
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
      OrderAhBot current = this.bot;
      if (current != null && event.packet instanceof GameMessageS2CPacket chat && chat.content() != null) {
         current.onChat(chat.content().getString());
      }
   }

   private String validate() {
      int i = this.selected();
      Item item = Product.resolveItem(this.items[i].get());
      if (item == null) {
         return "Product " + (i + 1) + ": unknown item '" + this.items[i].get() + "'.";
      } else if (ServerText.parseMoney(this.minPrices[i].get()) <= 0L) {
         return "Product " + (i + 1) + ": Min Price must be a price like 3k.";
      } else if (ServerText.parseMoney(this.undercut.get()) < 0L) {
         return "Undercut By must be a price like 100.";
      } else if (!this.restock[i].get()) {
         return null;
      } else if (parseAmount(this.orderAmounts[i].get()) <= 0) {
         return "Product " + (i + 1) + ": Order Amount must be a whole number.";
      } else if (this.priceModes[i].is("Fixed") && ServerText.parseMoney(this.fixedPrices[i].get()) <= 0L) {
         return "Product " + (i + 1) + ": Fixed Order Price must be a price like 150k.";
      } else if (this.priceModes[i].is("Beat Top Order") && ServerText.parseMoney(this.bidIncrements[i].get()) <= 0L) {
         return "Product " + (i + 1) + ": Beat By must be a price like 1.";
      } else {
         return !this.maxPrices[i].get().isBlank() && ServerText.parseMoney(this.maxPrices[i].get()) <= 0L
            ? "Product " + (i + 1) + ": Max Order Price must be empty or a price like 200k."
            : null;
      }
   }

   private Product buildProduct() {
      int i = this.selected();
      Item item = Product.resolveItem(this.items[i].get());
      long max = this.maxPrices[i].get().isBlank() ? 0L : ServerText.parseMoney(this.maxPrices[i].get());
      return new Product(
         item,
         this.items[i].get().trim(),
         Product.searchText(item),
         new ItemStack(item).getName().getString(),
         ServerText.parseMoney(this.minPrices[i].get()),
         ItemEnchants.supports(item) ? Product.parseEnchants(this.enchants[i].get()) : List.of(),
         this.restock[i].get(),
         parseAmount(this.orderAmounts[i].get()),
         this.priceModes[i].is("Beat Top Order"),
         Math.max(1L, ServerText.parseMoney(this.fixedPrices[i].get())),
         Math.max(1L, ServerText.parseMoney(this.bidIncrements[i].get())),
         Math.max(0L, max)
      );
   }

   private AuctionHouse.Config buildConfig() {
      return new AuctionHouse.Config(
         Math.max(0L, ServerText.parseMoney(this.undercut.get())),
         (long)(this.priceCheck.get() * 1000.0F),
         (long)(this.salesCheck.get() * 1000.0F),
         this.perListing.getInt(),
         this.maxListings.getInt(),
         (long)(this.listingDelay.get() * 1000.0F),
         this.relistGap.get(),
         this.ignoreDrops.get(),
         this.collectSlots.getInt(),
         (long)(this.orderCheck.get() * 1000.0F)
      );
   }

   private static int parseAmount(String raw) {
      try {
         int value = Integer.parseInt(raw.trim());
         return value > 0 ? value : -1;
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private void info(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }
}
