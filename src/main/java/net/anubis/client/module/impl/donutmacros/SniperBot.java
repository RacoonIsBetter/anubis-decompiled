package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.chat.ModuleChat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
final class SniperBot {
   private static final Pattern RATE_LIMIT = Pattern.compile("you need to wait another ([\\d.]+) seconds?");
   private static final int LISTING_SLOTS = 45;
   private static final int DEFAULT_FILTER = 47;
   private static final int DEFAULT_REFRESH = 49;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long BUY_TIMEOUT_MS = 4000L;
   private static final long FAILED_SKIP_MS = 60000L;
   private static final long BOUGHT_SKIP_MS = 15000L;
   private static final long RECOVERY_MS = 2000L;
   private final AhSniperModule module;
   private final MacroWorker worker;
   private final Gui gui;
   private final SnipeTarget target;
   private final long refreshMs;
   private final int maxBuys;
   private final AuctionHouse ah;
   private final AuctionHouse.Stock stock;
   private final List<AuctionHouse.Stock> stocks;
   private final Map<String, Long> skip = new HashMap<>();
   private volatile long boughtAt;
   private volatile long goneAt;
   private volatile long poorAt;
   private volatile long ownAt;
   private volatile long holdUntil;
   private int bought;
   private int unsold;
   private long spent;

   SniperBot(AhSniperModule module, MacroWorker worker, SnipeTarget target, long refreshMs, int maxBuys, long sellPrice, String ign) {
      this.module = module;
      this.worker = worker;
      this.gui = new Gui(worker);
      this.target = target;
      this.refreshMs = refreshMs;
      this.maxBuys = maxBuys;
      if (sellPrice > 0L) {
         this.ah = new AuctionHouse(module, worker, new AuctionHouse.Config(0L, 60000L, 20000L, 1, 1000, 0L, 3.0, 20.0, 36, 15000L), ign);
         this.stock = new AuctionHouse.Stock(target.product(sellPrice), true);
         this.stocks = List.of(this.stock);
      } else {
         this.ah = null;
         this.stock = null;
         this.stocks = List.of();
      }
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this.module, body);
   }

   void run() {
      this.say(
         ModuleChat.body()
            .text("Sniping ")
            .name(this.target.describe())
            .text(" under $")
            .value(ServerText.shortMoney(this.target.maxPrice))
            .text(this.target.minMinutes > 0 ? " with " + formatMinutes(this.target.minMinutes) + "+ left" : "")
            .text(this.stock != null ? ", selling at $" + ServerText.shortMoney(this.stock.price) : "")
            .text(".")
      );

      while (true) {
         this.worker.check();

         try {
            if (!this.worker.call(() -> Gui.mc().player != null && Gui.mc().getNetworkHandler() != null)) {
               this.worker.sleep(2000L);
            } else {
               if (this.stock != null && this.unsold > 0) {
                  this.sellStock();
               }

               this.snipe();
            }
         } catch (MacroWorker.Cancelled cancelled) {
            throw cancelled;
         } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            this.say(ModuleChat.body().warn(reason).text(", trying again."));
            this.worker.check();
            this.gui.closeAll();
            this.worker.sleep(2000L);
         }
      }
   }

   private void snipe() {
      this.openAuction();
      this.ensureRecent();
      long nextRefresh = System.currentTimeMillis() + this.refreshMs;

      while (true) {
         this.worker.check();
         if (!this.worker.call(SniperBot::auctionOpenNow)) {
            return;
         }

         long mark = System.currentTimeMillis();
         SniperBot.Hit hit = this.worker.call(this::scanAndClick);
         if (hit != null) {
            if (this.handleBuy(hit, mark)) {
               return;
            }

            nextRefresh = System.currentTimeMillis() + this.refreshMs;
         } else {
            long now = System.currentTimeMillis();
            if (now >= nextRefresh) {
               this.worker.run(this::clickRefresh);
               nextRefresh = now + this.refreshMs;
            }

            this.worker.sleep(50L);
         }
      }
   }

   private void openAuction() {
      this.gui.closeAll();
      long wait = this.holdUntil - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }

      this.gui.command("ah " + this.target.search);
      if (!this.worker.waitFor(SniperBot::auctionOpenNow, 10000L)) {
         throw new AuctionHouse.Problem("The AH did not open");
      }

      this.worker.sleep(300L);
   }

   private static boolean auctionOpenNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      }

      String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
      return title.contains("order") ? false : title.contains("auction") || refreshSlot(Gui.containerNow()) >= 0;
   }

   private void ensureRecent() {
      for (int attempt = 0; attempt < 4; attempt++) {
         int[] state = this.worker.call(() -> {
            List<ItemStack> stacks = Gui.containerNow();
            int slot = filterSlot(stacks);
            return new int[]{slot, slot < 0 ? SniperBot.Sort.UNKNOWN.ordinal() : selectedSort(stacks.get(slot)).ordinal()};
         });
         if (state[0] < 0) {
            this.say(ModuleChat.body().warn("No Filter button found").text(", sniping with the current sorting."));
            return;
         }

         SniperBot.Sort sort = SniperBot.Sort.values()[state[1]];
         if (sort == SniperBot.Sort.RECENT) {
            return;
         }

         if (sort == SniperBot.Sort.UNKNOWN && attempt > 0) {
            this.say(ModuleChat.body().warn("Could not read the Filter").text(", make sure it is on Recently Listed."));
            return;
         }

         String before = this.gui.fingerprint();
         this.gui.click(state[0], 0, SlotActionType.PICKUP);
         this.gui.waitChange(before, 2000L);
         this.worker.sleep(250L);
      }
   }

   private static int filterSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && (stack.isOf(Items.HOPPER) || Gui.nameOf(stack).contains("filter"))) {
            return i;
         }
      }

      return stacks.size() > 47 && !stacks.get(47).isEmpty() ? 47 : -1;
   }

   private static int refreshSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && stack.isOf(Items.ANVIL)) {
            return i;
         }
      }

      return stacks.size() > 49 && stacks.get(49).isOf(Items.ANVIL) ? 49 : -1;
   }

   static SniperBot.Sort selectedSort(ItemStack filter) {
      List<SniperBot.Sort> options = new ArrayList<>();
      List<Integer> colors = new ArrayList<>();

      for (Text line : ((LoreComponent)filter.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)).lines()) {
         String text = ServerText.normalize(Dialogs.visibleText(line));
         SniperBot.Sort option = text.contains("recent")
            ? SniperBot.Sort.RECENT
            : (text.contains("lowest") ? SniperBot.Sort.LOWEST : (text.contains("highest") ? SniperBot.Sort.HIGHEST : SniperBot.Sort.UNKNOWN));
         if (option != SniperBot.Sort.UNKNOWN) {
            options.add(option);
            colors.add(color(line));
         }
      }

      if (options.size() < 2) {
         return SniperBot.Sort.UNKNOWN;
      }

      for (int i = 0; i < options.size(); i++) {
         boolean unique = true;
         boolean othersAgree = true;
         int other = -2;

         for (int j = 0; j < options.size(); j++) {
            if (j != i) {
               if (colors.get(j).equals(colors.get(i))) {
                  unique = false;
               }

               if (other == -2) {
                  other = colors.get(j);
               } else if (other != colors.get(j)) {
                  othersAgree = false;
               }
            }
         }

         if (unique && othersAgree) {
            return options.get(i);
         }
      }

      int best = -1;
      SniperBot.Sort pick = SniperBot.Sort.UNKNOWN;

      for (int i = 0; i < options.size(); i++) {
         int rgb = colors.get(i);
         int brightness = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
         if (brightness > best) {
            best = brightness;
            pick = options.get(i);
         } else if (brightness == best) {
            pick = SniperBot.Sort.UNKNOWN;
         }
      }

      return pick;
   }

   private static int color(Text line) {
      int[] value = new int[]{-1};
      line.visit((style, text) -> {
         if (value[0] < 0 && !text.isBlank() && !text.trim().equals("•") && !text.trim().equals("▪")) {
            TextColor color = style.getColor();
            value[0] = color == null ? 11141290 : color.getRgb();
            return Optional.empty();
         } else {
            return Optional.empty();
         }
      }, Style.EMPTY);
      return value[0];
   }

   private void clickRefresh() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && auctionOpenNow()) {
         int slot = refreshSlot(Gui.containerNow());
         if (slot >= 0) {
            Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, player);
         }
      }
   }

   private SniperBot.Hit scanAndClick() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && auctionOpenNow()) {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.contains("(page") && !title.contains("(page 1)")) {
            return null;
         }

         long now = System.currentTimeMillis();
         this.skip.values().removeIf(until -> until < now);
         List<ItemStack> stacks = Gui.containerNow();

         for (int i = 0; i < 45 && i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty() && this.target.matches(stack)) {
               double total = ServerText.listingPrice(stack);
               if (!(total <= 0.0)) {
                  long unit = Math.round(total / Math.max(1, stack.getCount()));
                  if (unit <= this.target.maxPrice) {
                     String fingerprint = fingerprint(stack, total);
                     if (!this.skip.containsKey(fingerprint)) {
                        Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, player);
                        return new SniperBot.Hit(i, unit, fingerprint, stack.getName().getString());
                     }
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String fingerprint(ItemStack stack, double total) {
      return Registries.ITEM.getId(stack.getItem())
         + "|"
         + stack.getName().getString()
         + "|"
         + Math.round(total)
         + "|"
         + stack.getDamage()
         + "|"
         + String.join("/", ServerText.lore(stack));
   }

   private boolean handleBuy(SniperBot.Hit hit, long mark) {
      Object[] pressed = new Object[]{null};
      int[] pressedChest = new int[]{-1};
      long end = System.currentTimeMillis() + 4000L;

      while (System.currentTimeMillis() < end) {
         if (this.boughtAt >= mark) {
            return this.bought(hit);
         }

         if (this.poorAt >= mark) {
            this.say(
               ModuleChat.body()
                  .danger("Not enough money")
                  .text(" for ")
                  .name(hit.name())
                  .text(" at $")
                  .value(ServerText.shortMoney(hit.price()))
                  .text(", stopping.")
            );
            this.module.stopFromBot(this);
            throw new MacroWorker.Cancelled();
         }

         if (this.goneAt >= mark || this.ownAt >= mark) {
            this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
            return false;
         }

         this.worker.run(() -> {
            Dialogs.View view = Dialogs.current(Gui.mc());
            if (view == null) {
               ClientPlayerEntity player = Gui.mc().player;
               if (player != null && Gui.containerOpenNow() && !auctionOpenNow()) {
                  int sync = player.currentScreenHandler.syncId;
                  if (sync != pressedChest[0]) {
                     List<ItemStack> stacks = Gui.containerNow();

                     for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = stacks.get(i);
                        if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().contains("lime")) {
                           Gui.mc().interactionManager.clickSlot(sync, i, 0, SlotActionType.PICKUP, player);
                           pressedChest[0] = sync;
                           return;
                        }
                     }
                  }
               }
            } else {
               if (view.dialog() != pressed[0] && (view.titled("buy") || view.titled("sure") || view.titled("purchase"))) {
                  Dialogs.Button yes = view.labelled("yes", "confirm", "buy");
                  if (yes != null && Dialogs.press(Gui.mc(), view, yes, Map.of())) {
                     pressed[0] = view.dialog();
                  }
               }
            }
         });
         this.worker.sleep(50L);
      }

      this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 60000L);
      return !this.worker.call(SniperBot::auctionOpenNow);
   }

   private boolean bought(SniperBot.Hit hit) {
      this.skip.put(hit.fingerprint(), System.currentTimeMillis() + 15000L);
      this.bought++;
      this.unsold++;
      this.spent = this.spent + hit.price();
      this.say(
         ModuleChat.body()
            .good("Sniped")
            .text(" ")
            .name(hit.name())
            .text(" for $")
            .value(ServerText.shortMoney(hit.price()))
            .text(" (")
            .value(this.bought)
            .text(this.bought == 1 ? " buy" : " buys")
            .text(", $")
            .value(ServerText.shortMoney(this.spent))
            .text(" spent).")
      );
      if (this.maxBuys > 0 && this.bought >= this.maxBuys) {
         if (this.stock != null) {
            this.worker.sleep(300L);
            this.sellStock();
         }

         this.say(ModuleChat.body().text("Reached ").value(this.maxBuys).text(this.maxBuys == 1 ? " buy" : " buys").text(", stopping."));
         this.module.stopFromBot(this);
         throw new MacroWorker.Cancelled();
      } else {
         if (this.stock == null) {
            return !this.worker.call(SniperBot::auctionOpenNow);
         }

         this.worker.sleep(300L);
         this.sellStock();
         return true;
      }
   }

   private void sellStock() {
      int count = Math.min(this.unsold, this.ah.countInventory(this.stock.product));
      if (count > 0) {
         this.unsold = this.unsold - this.ah.listSome(this.stock, Math.min(count, 9), new int[]{0});
      }
   }

   void onChat(String raw) {
      String line = ServerText.normalize(raw);
      if (!line.isEmpty()) {
         long now = System.currentTimeMillis();
         Matcher wait = RATE_LIMIT.matcher(line);
         if (wait.find()) {
            try {
               this.holdUntil = now + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
            } catch (NumberFormatException var7) {
            }
         }

         if (line.startsWith("you bought")) {
            this.boughtAt = now;
         }

         if (line.contains("already bought") || line.contains("no longer available") || line.contains("not available")) {
            this.goneAt = now;
         }

         if (line.contains("enough money")) {
            this.poorAt = now;
         }

         if (line.contains("your own")) {
            this.ownAt = now;
         }

         if (this.ah != null) {
            this.ah.onChat(line, this.stocks);
         }
      }
   }

   String summary() {
      return "Sniped "
         + this.bought
         + (this.bought == 1 ? " item" : " items")
         + " for $"
         + ServerText.shortMoney(this.spent)
         + (this.stock != null ? ". " + AuctionHouse.summary(this.stocks) : ".");
   }

   private static String formatMinutes(int minutes) {
      return minutes % 60 == 0 ? minutes / 60 + "h" : minutes / 60 + "h " + minutes % 60 + "m";
   }

   @Environment(EnvType.CLIENT)
   private record Hit(int slot, long price, String fingerprint, String name) {
   }

   @Environment(EnvType.CLIENT)
   enum Sort {
      UNKNOWN,
      LOWEST,
      HIGHEST,
      RECENT;
   }
}
