package net.anubis.client.module.impl.donutmacros;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

@Environment(EnvType.CLIENT)
final class AuctionHouse {
   private static final int INVENTORY_SLOTS = 36;
   private static final int AH_SCAN_SLOTS = 45;
   private static final int AH_ROOT_BUTTON = 51;
   private static final int CHEST_CONFIRM_SLOT = 15;
   private static final int INVENTORY_SETTLE_TICKS = 4;
   private static final int COLLECT_LIMIT = 50;
   private static final int VERIFY_LIMIT = 15;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long NAV_TIMEOUT_MS = 4000L;
   private static final long SELL_TIMEOUT_MS = 15000L;
   final ClientModule module;
   final MacroWorker worker;
   final Gui gui;
   final AuctionHouse.Config config;
   private final String ign;
   private final String ignKey;
   private volatile long tooManyAt;
   private volatile long alreadyBoughtAt;
   private volatile long listedAt;
   private volatile long errorAt;

   AuctionHouse(ClientModule module, MacroWorker worker, AuctionHouse.Config config, String ign) {
      this.module = module;
      this.worker = worker;
      this.gui = new Gui(worker);
      this.config = config;
      this.ign = ign;
      this.ignKey = ServerText.key(ign);
   }

   void say(ModuleChat.Body body) {
      ModuleChat.send(this.module, body);
   }

   boolean inWorld() {
      return this.worker.call(() -> Gui.mc().player != null && Gui.mc().getNetworkHandler() != null);
   }

   void ensurePrice(AuctionHouse.Stock stock) {
      if (stock.fixedPrice) {
         stock.price = stock.product.minPrice();
      } else {
         if (stock.price <= 0L || System.currentTimeMillis() - stock.lastPriceCheck >= this.config.priceCheckMs()) {
            this.updatePrice(stock);
         }
      }
   }

   private void updatePrice(AuctionHouse.Stock stock) {
      Product product = stock.product;
      double[] scan = this.scan(stock);
      stock.lastPriceCheck = System.currentTimeMillis();
      boolean confirmed = scan[0] > 0.0;
      boolean matched = scan[1] > 0.0;
      double cheapestUnit = scan[2];
      long undercut = this.config.undercut();
      String name = product.displayName();
      if (cheapestUnit > 0.0) {
         long cheapest = Math.round(cheapestUnit);
         long next = Math.max(cheapest - undercut, product.minPrice());
         if (stock.price > 0L && cheapest < stock.price) {
            long drop = stock.price - cheapest;
            double deep = undercut > 0L ? undercut * this.config.ignoreDrops() : stock.price / 2.0;
            if (drop > deep) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(": ")
                     .warn("ignoring a deep undercut")
                     .text(" to $")
                     .value(ServerText.shortMoney(cheapest))
                     .text(" (drop $")
                     .value(ServerText.shortMoney(drop))
                     .text(").")
               );
            } else if (next < stock.price) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(": ")
                     .warn("undercut")
                     .text(" to $")
                     .value(ServerText.shortMoney(cheapest))
                     .text(", relisting at $")
                     .value(ServerText.shortMoney(next))
                     .text(".")
               );
               this.relist(stock, next);
            }
         } else if (stock.price > 0L && next > stock.price + undercut * this.config.relistGap() && stock.inAh > 0) {
            this.say(
               ModuleChat.body()
                  .name(name)
                  .text(": price went up to $")
                  .value(ServerText.shortMoney(cheapest))
                  .text(", relisting at $")
                  .value(ServerText.shortMoney(next))
                  .text(".")
            );
            this.relist(stock, next);
         } else {
            if (stock.price <= 0L) {
               this.say(
                  ModuleChat.body()
                     .name(name)
                     .text(": cheapest is $")
                     .value(ServerText.shortMoney(cheapest))
                     .text(", selling at $")
                     .value(ServerText.shortMoney(next))
                     .text(" each.")
               );
            }

            stock.price = next;
         }
      } else if (stock.price <= 0L && confirmed && !matched) {
         stock.price = product.minPrice() * 2L;
         this.say(ModuleChat.body().name(name).text(": no other listings, selling at $").value(ServerText.shortMoney(stock.price)).text(" each."));
      } else if (stock.price <= 0L) {
         this.say(ModuleChat.body().name(name).text(": ").warn("could not read AH prices").text(", waiting."));
      }
   }

   private double[] scan(AuctionHouse.Stock stock) {
      Product product = stock.product;
      this.gui.closeAll();
      this.worker.sleep(200L);
      this.gui.command("ah " + product.search());
      boolean open = this.worker.waitFor(() -> this.isAhResultsNow() && this.hasContent(), 10000L);
      if (!open) {
         this.gui.closeAll();
         return new double[]{0.0, 0.0, -1.0};
      } else {
         this.worker.sleep(600L);
         if (!this.worker.call(this::isAhResultsNow)) {
            this.gui.closeAll();
            return new double[]{0.0, 0.0, -1.0};
         } else {
            double[] result = this.worker.call(() -> {
               List<ItemStack> stacks = Gui.containerNow();
               boolean matched = false;
               double cheapest = -1.0;

               for (int i = 0; i < stacks.size() && i < 45; i++) {
                  ItemStack stack = stacks.get(i);
                  if (product.matches(stack)) {
                     matched = true;
                     double total = ServerText.listingPrice(stack);
                     if (!(total <= 0.0)) {
                        double unit = total / Math.max(1, stack.getCount());
                        if (!this.ownListing(stock, stack, unit) && (cheapest < 0.0 || unit < cheapest)) {
                           cheapest = unit;
                        }
                     }
                  }
               }

               return new double[]{1.0, matched ? 1.0 : 0.0, cheapest};
            });
            this.gui.closeAll();
            this.worker.sleep(200L);
            return result;
         }
      }
   }

   private boolean ownListing(AuctionHouse.Stock stock, ItemStack stack, double unit) {
      for (String line : ServerText.lore(stack)) {
         if (!this.ignKey.isEmpty() && ServerText.key(line).contains(this.ignKey)) {
            return true;
         }
      }

      return stock.inAh > 0 && stock.price > 0L && Math.abs(unit - stock.price) < 1.0;
   }

   private boolean hasContent() {
      List<ItemStack> stacks = Gui.containerNow();
      if (stacks.size() > 49 && !stacks.get(49).isEmpty()) {
         return true;
      }

      for (int i = 0; i <= 10 && i < stacks.size(); i++) {
         if (!stacks.get(i).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean isAhResultsNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.contains("order")) {
            return false;
         } else {
            return !title.contains("auction") && !title.contains("market") && !title.equals("ah") && !title.contains(" ah ") ? isChest(Gui.slotNow(51)) : true;
         }
      }
   }

   private boolean isAhRootNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         return ServerText.normalize(Gui.mc().currentScreen.getTitle()).contains("order") ? false : isChest(Gui.slotNow(51));
      }
   }

   private static boolean isChest(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      String path = Registries.ITEM.getId(stack.getItem()).getPath();
      return path.equals("chest") || path.equals("trapped_chest");
   }

   private boolean openYourItems() {
      this.gui.closeAll();
      this.worker.sleep(200L);
      this.gui.command("ah " + this.ign);
      if (!this.worker.waitFor(this::isAhRootNow, 10000L)) {
         return false;
      }

      for (int attempt = 0; attempt < 2; attempt++) {
         String before = this.gui.fingerprint();
         this.gui.click(51, 0, SlotActionType.PICKUP);
         if (this.gui.waitChange(before, 4000L)) {
            this.worker.sleep(200L);
            return this.worker.call(Gui::containerOpenNow);
         }
      }

      return false;
   }

   boolean refreshListings(List<AuctionHouse.Stock> stocks) {
      if (!this.openYourItems()) {
         this.gui.closeAll();
         return false;
      }

      int[] counts = this.worker.call(() -> {
         int[] found = new int[stocks.size()];

         for (ItemStack stack : Gui.containerNow()) {
            for (int ix = 0; ix < stocks.size(); ix++) {
               if (stocks.get(ix).product.matches(stack)) {
                  found[ix]++;
                  break;
               }
            }
         }

         return found;
      });
      this.gui.closeAll();
      this.worker.sleep(200L);

      for (int i = 0; i < stocks.size(); i++) {
         AuctionHouse.Stock stock = stocks.get(i);
         int gone = stock.inAh - counts[i];
         if (gone > 0 && stock.chatSales.getAndSet(0) == 0) {
            stock.sold.addAndGet(gone);
            stock.earned.addAndGet(gone * Math.max(0L, stock.price) * this.config.perListing());
            this.say(
               ModuleChat.body().good("Sold " + gone + "x").text(" ").name(stock.product.displayName()).text(" (total ").value(stock.sold.get()).text(").")
            );
         }

         stock.chatSales.set(0);
         stock.inAh = counts[i];
      }

      return true;
   }

   private int collect(AuctionHouse.Stock stock) {
      if (!this.openYourItems()) {
         this.say(ModuleChat.body().warn("Could not open Your Items."));
         this.gui.closeAll();
         return 0;
      }

      int collected = this.collectPass(stock.product, 50);
      if (collected > 0) {
         this.gui.closeAll();
         this.worker.sleep(500L);
         if (this.openYourItems()) {
            collected += this.collectPass(stock.product, 15);
         }
      }

      this.gui.closeAll();
      this.worker.sleep(200L);
      if (collected > 0) {
         this.say(
            ModuleChat.body()
               .text("Took back ")
               .value(collected)
               .text(collected == 1 ? " listing of " : " listings of ")
               .name(stock.product.displayName())
               .text(".")
         );
      }

      return collected;
   }

   private int collectPass(Product product, int limit) {
      int collected = 0;
      Set<Integer> skipped = new HashSet<>();

      while (collected < limit && this.worker.call(Gui::containerOpenNow)) {
         int slot = this.worker.call(() -> {
            List<ItemStack> stacks = Gui.containerNow();

            for (int i = 0; i < stacks.size(); i++) {
               if (!skipped.contains(i) && product.matches(stacks.get(i))) {
                  return i;
               }
            }

            return -1;
         });
         if (slot < 0) {
            break;
         }

         long mark = System.currentTimeMillis();
         this.gui.click(slot, 0, SlotActionType.PICKUP);
         this.worker.ticks(3);
         if (this.alreadyBoughtAt >= mark) {
            skipped.add(slot);
         } else {
            collected++;
         }
      }

      return collected;
   }

   private void relist(AuctionHouse.Stock stock, long next) {
      this.refreshListings(List.of(stock));
      stock.price = next;
      this.collect(stock);
      stock.inAh = 0;
   }

   int listSome(AuctionHouse.Stock stock, int wanted, int[] totalInAh) {
      if (wanted <= 0) {
         return 0;
      }

      this.gui.closeAll();
      this.gui.closeInventory();
      int previous = this.worker.call(() -> Gui.player().getInventory().getSelectedSlot());
      int done = 0;
      int failures = 0;

      try {
         for (int i = 0; i < wanted; i++) {
            this.ensureAlive();
            int hotbar = this.stage(stock.product);
            if (hotbar < 0) {
               this.say(ModuleChat.body().warn("Could not prepare a hotbar slot").text(" (inventory full?)."));
               break;
            }

            AuctionHouse.Listing result = this.listFrom(stock, hotbar);
            if (result == AuctionHouse.Listing.FULL) {
               totalInAh[0] = Math.max(totalInAh[0], this.config.maxListings());
               this.say(ModuleChat.body().warn("The AH says you have too many listings."));
               break;
            }

            if (result == AuctionHouse.Listing.FAILED) {
               if (++failures >= 2) {
                  break;
               }
            } else {
               done++;
               stock.inAh++;
               totalInAh[0]++;
               stock.listed.incrementAndGet();
               if (this.config.listingDelayMs() > 0L && i < wanted - 1) {
                  this.worker.sleep(this.config.listingDelayMs());
               }
            }
         }
      } finally {
         this.worker.run(() -> {
            ClientPlayerEntity player = Gui.mc().player;
            if (player != null) {
               player.getInventory().setSelectedSlot(previous);
            }
         });
      }

      if (done > 0) {
         this.say(
            ModuleChat.body()
               .text("Listed ")
               .value(done + "x")
               .text(" ")
               .name(stock.product.displayName())
               .text(" at $")
               .value(ServerText.shortMoney(stock.price * this.config.perListing()))
               .text(" (in AH: ")
               .value(totalInAh[0])
               .text(").")
         );
      }

      return done;
   }

   private AuctionHouse.Listing listFrom(AuctionHouse.Stock stock, int hotbar) {
      Product product = stock.product;
      this.worker.run(() -> Gui.player().getInventory().setSelectedSlot(hotbar));
      this.worker.ticks(2);
      int before = this.worker.call(() -> Gui.player().getInventory().getStack(hotbar).getCount());
      long mark = System.currentTimeMillis();
      this.gui.command("ah sell " + ServerText.exactMoney(stock.price * this.config.perListing()));
      long end = mark + 15000L;
      boolean[] confirmed = new boolean[]{false};
      Object[] pressedDialog = new Object[]{null};
      int[] pressedChest = new int[]{-1};

      while (System.currentTimeMillis() < end) {
         if (this.tooManyAt >= mark) {
            this.gui.closeAll();
            return AuctionHouse.Listing.FULL;
         }

         if (this.listedAt >= mark) {
            this.worker.sleep(150L);
            this.gui.closeAll();
            return AuctionHouse.Listing.LISTED;
         }

         if (this.errorAt >= mark) {
            this.gui.closeAll();
            return AuctionHouse.Listing.FAILED;
         }

         long elapsed = System.currentTimeMillis() - mark;
         int state = this.worker
            .call(
               () -> {
                  Dialogs.View view = Dialogs.current(Gui.mc());
                  if (view != null) {
                     if (view.dialog() != pressedDialog[0]
                        && (view.titled("sure") || view.titled("confirm") || view.titled("sell"))
                        && !view.titled("buy")
                        && !view.titled("purchase")) {
                        Dialogs.Button yes = view.labelled("yes", "confirm");
                        if (yes != null && Dialogs.press(Gui.mc(), view, yes, Map.of())) {
                           pressedDialog[0] = view.dialog();
                           confirmed[0] = true;
                           return 1;
                        }
                     }

                     return 0;
                  } else if (Gui.containerOpenNow()) {
                     ClientPlayerEntity player = Gui.player();
                     int sync = player.currentScreenHandler.syncId;
                     ItemStack button = Gui.slotNow(15);
                     String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
                     boolean otherMenu = title.contains("buy") || title.contains("purchase") || title.contains("(page") || title.contains("order");
                     if (sync != pressedChest[0] && !otherMenu && !button.isEmpty() && Registries.ITEM.getId(button.getItem()).getPath().contains("lime")) {
                        Gui.mc().interactionManager.clickSlot(sync, 15, 0, SlotActionType.PICKUP, player);
                        pressedChest[0] = sync;
                        confirmed[0] = true;
                        return 1;
                     } else {
                        return 0;
                     }
                  } else {
                     ItemStack held = Gui.player().getInventory().getStack(hotbar);
                     boolean gone = !product.matches(held) || held.getCount() < before;
                     return !gone || !confirmed[0] && elapsed <= 2000L ? 0 : 2;
                  }
               }
            );
         if (state == 2) {
            this.gui.closeAll();
            return AuctionHouse.Listing.LISTED;
         }

         this.worker.sleep(state == 1 ? 250L : 50L);
      }

      this.gui.closeAll();
      this.say(ModuleChat.body().warn("Listing timed out").text("."));
      return AuctionHouse.Listing.FAILED;
   }

   private int stage(Product product) {
      int n = this.config.perListing();
      boolean clicked = false;

      try {
         for (int attempt = 0; attempt < 12; attempt++) {
            int[] plan = this.worker.call(() -> planStage(product, n));
            if (plan[0] == 0) {
               return plan[1];
            }

            if (plan[0] < 0) {
               return -1;
            }

            for (int i = 2; i < plan.length; i += 2) {
               this.gui.clickInventory(plan[i] >> 1, plan[i] & 1, plan[i + 1] == 1 ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP);
               clicked = true;
               this.worker.ticks(1);
            }
         }

         return -1;
      } finally {
         if (clicked) {
            this.gui.closeInventory();
            this.worker.ticks(4);
         }
      }
   }

   private static int[] planStage(Product product, int n) {
      ClientPlayerEntity player = Gui.player();
      PlayerInventory inventory = player.getInventory();
      ItemStack carried = player.playerScreenHandler.getCursorStack();
      if (!carried.isEmpty()) {
         int empty = emptyMain(inventory);
         return empty < 0 ? new int[]{-1} : new int[]{1, 0, click(menuSlot(empty), 0), 0};
      }

      for (int h = 0; h < 9; h++) {
         ItemStack stack = inventory.getStack(h);
         if (product.matches(stack) && stack.getCount() == n) {
            return new int[]{0, h};
         }
      }

      int target = -1;

      for (int h = 0; h < 9; h++) {
         ItemStack stack = inventory.getStack(h);
         if (product.matches(stack) && stack.getCount() < n) {
            target = h;
            break;
         }
      }

      if (target < 0) {
         for (int h = 0; h < 9; h++) {
            if (inventory.getStack(h).isEmpty()) {
               target = h;
               break;
            }
         }
      }

      if (target < 0) {
         int victim = -1;

         for (int h = 8; h >= 0; h--) {
            if (!product.matches(inventory.getStack(h))) {
               victim = h;
               break;
            }
         }

         if (victim < 0) {
            victim = 8;
         }

         return emptyMain(inventory) < 0 ? new int[]{-1} : new int[]{1, 0, click(menuSlot(victim), 0), 1};
      } else {
         int have = product.matches(inventory.getStack(target)) ? inventory.getStack(target).getCount() : 0;
         int source = -1;
         int best = 0;

         for (int index = 0; index < 36; index++) {
            if (index != target) {
               ItemStack stack = inventory.getStack(index);
               if (product.matches(stack) && stack.getCount() > best) {
                  best = stack.getCount();
                  source = index;
               }
            }
         }

         if (source < 0) {
            return new int[]{-1};
         }

         int need = n - have;
         int[] plan = new int[2 + 2 * (need + 2)];
         int at = 2;
         plan[0] = 1;
         plan[at++] = click(menuSlot(source), 0);
         plan[at++] = 0;
         if (best <= need) {
            plan[at++] = click(menuSlot(target), 0);
            plan[at++] = 0;
         } else {
            for (int i = 0; i < need; i++) {
               plan[at++] = click(menuSlot(target), 1);
               plan[at++] = 0;
            }

            plan[at++] = click(menuSlot(source), 0);
            plan[at++] = 0;
         }

         return Arrays.copyOf(plan, at);
      }
   }

   private static int click(int slot, int button) {
      return slot << 1 | button;
   }

   private static int menuSlot(int inventoryIndex) {
      return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
   }

   private static int emptyMain(PlayerInventory inventory) {
      for (int index = 9; index < 36; index++) {
         if (inventory.getStack(index).isEmpty()) {
            return index;
         }
      }

      return -1;
   }

   int countInventory(Product product) {
      return this.worker.call(() -> {
         ClientPlayerEntity player = Gui.mc().player;
         if (player == null) {
            return 0;
         }

         int total = 0;

         for (int index = 0; index < 36; index++) {
            ItemStack stack = player.getInventory().getStack(index);
            if (product.matches(stack)) {
               total += stack.getCount();
            }
         }

         return total;
      });
   }

   boolean inventoryCapReached(Product product) {
      return this.worker.call(() -> {
         PlayerInventory inventory = Gui.player().getInventory();
         int used = 0;
         int empty = 0;

         for (int index = 0; index < 36; index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isEmpty()) {
               empty++;
            } else if (stack.isOf(product.item())) {
               used++;
            }
         }

         return used >= this.config.collectSlots() || empty <= 0;
      });
   }

   void ensureAlive() {
      if (this.worker.call(() -> Gui.mc().player != null && Gui.mc().player.isDead())) {
         this.say(ModuleChat.body().danger("You died").text(", respawning."));
         long end = System.currentTimeMillis() + 30000L;
         long next = System.currentTimeMillis() + 700L;

         while (System.currentTimeMillis() < end) {
            boolean dead = this.worker.call(() -> Gui.mc().player != null && Gui.mc().player.isDead());
            if (!dead) {
               break;
            }

            if (System.currentTimeMillis() >= next) {
               this.worker.run(() -> {
                  ClientPlayerEntity player = Gui.mc().player;
                  if (player != null) {
                     player.requestRespawn();
                  }
               });
               next = System.currentTimeMillis() + 1500L;
            }

            this.worker.sleep(100L);
         }

         this.worker.run(() -> {
            if (Gui.mc().currentScreen instanceof DeathScreen) {
               Gui.mc().setScreen(null);
            }
         });
         throw new AuctionHouse.Problem("Respawned");
      }
   }

   void waitSalesCheck() {
      long until = System.currentTimeMillis() + this.config.salesCheckMs();

      while (System.currentTimeMillis() < until) {
         this.worker.sleep(Math.min(1000L, until - System.currentTimeMillis()));
      }
   }

   String onChat(String line, List<AuctionHouse.Stock> stocks) {
      long now = System.currentTimeMillis();
      if (line.contains("too many listed items")) {
         this.tooManyAt = now;
      }

      if (line.contains("already bought")) {
         this.alreadyBoughtAt = now;
      }

      if (line.startsWith("you listed")) {
         this.listedAt = now;
      }

      if (line.contains("you cannot do this") || line.contains("you need to wait another") || line.contains("don't have enough")) {
         this.errorAt = now;
      }

      int bought = line.indexOf(" bought your ");
      if (bought <= 0) {
         return null;
      }

      String rest = line.substring(bought + " bought your ".length());
      int forAt = rest.lastIndexOf(" for ");
      String itemPart = forAt > 0 ? rest.substring(0, forAt) : rest;
      AuctionHouse.Stock stock = null;

      for (AuctionHouse.Stock candidate : stocks) {
         if (candidate.product.nameMatches(itemPart, false)) {
            stock = candidate;
            break;
         }
      }

      if (stock == null) {
         for (AuctionHouse.Stock candidate : stocks) {
            if (candidate.product.nameMatches(itemPart, true)) {
               stock = candidate;
               break;
            }
         }
      }

      if (stock == null) {
         return null;
      }

      List<Double> money = forAt > 0 ? ServerText.moneyAll(rest.substring(forAt)) : List.of();
      long amount = money.isEmpty() ? 0L : Math.round(money.get(0));
      stock.sold.incrementAndGet();
      stock.chatSales.incrementAndGet();
      stock.earned.addAndGet(amount);
      this.say(
         ModuleChat.body()
            .good("Sold")
            .text(" ")
            .name(stock.product.displayName())
            .text(amount > 0L ? " for $" + ServerText.shortMoney(amount) : "")
            .text(" (total ")
            .value(stock.sold.get())
            .text(", ~$")
            .value(ServerText.shortMoney(stock.earned.get()))
            .text(").")
      );
      return stock.product.displayName();
   }

   static String summary(List<AuctionHouse.Stock> stocks) {
      int listed = 0;
      int sold = 0;
      long earned = 0L;

      for (AuctionHouse.Stock stock : stocks) {
         listed += stock.listed.get();
         sold += stock.sold.get();
         earned += stock.earned.get();
      }

      return "Listed " + listed + ", sold " + sold + ", earned ~$" + ServerText.shortMoney(earned) + ".";
   }

   @Environment(EnvType.CLIENT)
   record Config(
      long undercut,
      long priceCheckMs,
      long salesCheckMs,
      int perListing,
      int maxListings,
      long listingDelayMs,
      double relistGap,
      double ignoreDrops,
      int collectSlots,
      long orderCheckMs
   ) {
   }

   @Environment(EnvType.CLIENT)
   private enum Listing {
      LISTED,
      FULL,
      FAILED;
   }

   @Environment(EnvType.CLIENT)
   static final class Problem extends RuntimeException {
      Problem(String message) {
         super(message, null, false, false);
      }
   }

   @Environment(EnvType.CLIENT)
   static final class Stock {
      final Product product;
      final boolean fixedPrice;
      long price = -1L;
      long lastPriceCheck;
      int inAh;
      final AtomicInteger listed = new AtomicInteger();
      final AtomicInteger sold = new AtomicInteger();
      final AtomicInteger chatSales = new AtomicInteger();
      final AtomicLong earned = new AtomicLong();

      Stock(Product product, boolean fixedPrice) {
         this.product = product;
         this.fixedPrice = fixedPrice;
         if (fixedPrice) {
            this.price = product.minPrice();
         }
      }
   }
}
