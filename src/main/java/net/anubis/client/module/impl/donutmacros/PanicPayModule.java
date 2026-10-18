package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PanicPayModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Pattern VALID_NAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");
   private static final Pattern RATE_LIMIT = Pattern.compile("you need to wait another ([\\d.]+) seconds?");
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final int MAX_STRIKES = 2;
   private static final int MAX_SELL_ROUNDS = 6;
   private static final int[] ARMOR_MENU_SLOTS = new int[]{5, 6, 7, 8};
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final long COMMAND_GAP_MS = 1000L;
   private static final long OPEN_TIMEOUT_MS = 8000L;
   private static final long REPLY_TIMEOUT_MS = 6000L;
   private final StringSetting payTo = this.addSetting("pay-to", new StringSetting("Pay To", "").hint("Player name"));
   private final BooleanSetting sellArmor = this.addSetting("sell-armor", new BooleanSetting("Sell Armor", true));
   private final BooleanSetting sellOffhand = this.addSetting("sell-offhand", new BooleanSetting("Sell Offhand", true));
   private final MacroWorker worker = new MacroWorker("Anubis Panic Pay");
   private volatile boolean running;
   private volatile long paidAt;
   private volatile long poorAt;
   private volatile long errorAt;
   private volatile String errorText = "";
   private volatile long holdUntil;
   private volatile long balanceAt;
   private volatile double balance;
   private volatile String balanceText = "";
   private long lastCommandAt;

   public PanicPayModule() {
      super("panic-pay", "Panic Pay", "Sells your whole inventory and armor with /sell, then pays all your money to one player.", ModuleCategory.DONUT_MACROS);
      this.usesNotifications();
      this.payTo.sessionOnly();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   public boolean inConfigs() {
      return false;
   }

   @Override
   protected void onEnable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      String target = this.payTo.get().trim();
      if (mc.player == null || mc.getNetworkHandler() == null) {
         this.say(ModuleChat.body().warn("Join a server first."));
         this.requestDisable();
      } else if (!VALID_NAME.matcher(target).matches()) {
         this.say(ModuleChat.body().warn("Set Pay To to a player name first."));
         this.requestDisable();
      } else if (target.equalsIgnoreCase(mc.getSession().getUsername())) {
         this.say(ModuleChat.body().warn("Pay To is your own name."));
         this.requestDisable();
      } else {
         boolean armor = this.sellArmor.get();
         boolean offhand = this.sellOffhand.get();
         this.running = true;
         this.lastCommandAt = 0L;
         this.say(
            ModuleChat.body().danger("Panic Pay started").text(": selling everything, then paying ").name(target).text(". Press the key again to cancel.")
         );
         this.worker.start(() -> {
            try {
               this.run(new Gui(this.worker), target, armor, offhand);
            } catch (AuctionHouse.Problem problem) {
               this.say(ModuleChat.body().warn(problem.getMessage()).text(", stopped."));
            } finally {
               this.finish(mc);
            }
         }, failure -> {
            LOGGER.error("[Anubis] Panic Pay stopped", failure);
            this.say(ModuleChat.body().danger("Stopped").text(": " + failure));
            this.finish(mc);
         });
      }
   }

   private void finish(MinecraftClient mc) {
      mc.execute(() -> {
         if (this.running && this.isEnabled()) {
            this.setEnabled(false);
         }
      });
   }

   @Override
   protected void onDisable() {
      boolean wasRunning = this.running;
      this.running = false;
      this.worker.stop();
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (wasRunning && player != null && player.currentScreenHandler != player.playerScreenHandler) {
         player.closeHandledScreen();
      }
   }

   @EventHandler
   public void onPacket(PacketReceiveNettyEvent event) {
      if (this.running && event.packet instanceof GameMessageS2CPacket chat && chat.content() != null) {
         String raw = chat.content().getString();
         String line = ServerText.normalize(raw);
         long now = System.currentTimeMillis();
         Matcher wait = RATE_LIMIT.matcher(line);
         if (wait.find()) {
            try {
               this.holdUntil = now + (long)Math.ceil(Double.parseDouble(wait.group(1)) * 1000.0) + 100L;
            } catch (NumberFormatException var11) {
            }
         } else {
            if (line.startsWith("you paid ")) {
               this.paidAt = now;
            } else if (line.contains("enough money")) {
               this.poorAt = now;
            } else if (!line.startsWith("you have $") && (!line.startsWith("balance") || !line.contains("$"))) {
               if (line.contains("not online")
                  || line.contains("not found")
                  || line.contains("cannot pay")
                  || line.contains("can't pay")
                  || line.contains("not a valid")
                  || line.contains("invalid")
                  || line.contains("doesn't exist")
                  || line.contains("does not exist")) {
                  this.errorText = raw;
                  this.errorAt = now;
               }
            } else {
               String shown = line.substring(line.indexOf(36));
               List<Double> values = ServerText.moneyAll(shown);
               String token = ServerText.moneyToken(shown);
               if (!values.isEmpty() && token != null) {
                  this.balance = values.get(0);
                  this.balanceText = token;
                  this.balanceAt = now;
               }
            }
         }
      }
   }

   private void run(Gui gui, String target, boolean armor, boolean offhand) {
      gui.closeAll();
      Map<Item, Integer> strikes = new HashMap<>();
      int sold = this.sellRounds(gui, strikes);
      if ((armor || offhand) && this.takeOff(gui, armor, offhand)) {
         sold += this.sellRounds(gui, strikes);
      }

      this.say(ModuleChat.body().text("Sold ").value(sold).text(sold == 1 ? " item" : " items").text(", paying ").name(target).text("."));
      this.payBalance(gui, target);
   }

   private int sellRounds(Gui gui, Map<Item, Integer> strikes) {
      int total = 0;

      for (int round = 0; round < 6; round++) {
         Map<Item, Integer> before = this.worker.call(() -> inventoryCounts(strikes));
         if (before.isEmpty()) {
            break;
         }

         this.openSell(gui);
         int moved = this.worker
            .call(
               () -> {
                  ClientPlayerEntity player = Gui.player();
                  List<Integer> slots = new ArrayList<>();

                  for (Slot slot : player.currentScreenHandler.slots) {
                     if (slot.inventory instanceof PlayerInventory
                        && slot.hasStack()
                        && slot.getIndex() < 36
                        && strikes.getOrDefault(slot.getStack().getItem(), 0) < 2) {
                        slots.add(slot.id);
                     }
                  }

                  for (int index : slots) {
                     Gui.mc().interactionManager.clickSlot(player.currentScreenHandler.syncId, index, 0, SlotActionType.QUICK_MOVE, player);
                  }

                  return slots.size();
               }
            );
         this.worker.ticks(2);
         if (moved > 0 && this.worker.call(Gui::containerOpenNow)) {
            gui.click(53, 0, SlotActionType.PICKUP);
         }

         this.worker.sleep(700L);
         gui.closeAll();
         this.worker.sleep(400L);
         Map<Item, Integer> after = this.worker.call(() -> inventoryCounts(strikes));

         for (Entry<Item, Integer> entry : before.entrySet()) {
            int left = after.getOrDefault(entry.getKey(), 0);
            if (left < entry.getValue()) {
               total += entry.getValue() - left;
            } else {
               int strike = strikes.merge(entry.getKey(), 1, Integer::sum);
               if (strike == 2) {
                  this.say(
                     ModuleChat.body()
                        .warn("Could not sell ")
                        .name(new ItemStack((ItemConvertible)entry.getKey()).getName().getString())
                        .text(", skipping it.")
                  );
               }
            }
         }
      }

      return total;
   }

   private static Map<Item, Integer> inventoryCounts(Map<Item, Integer> strikes) {
      Map<Item, Integer> counts = new HashMap<>();
      PlayerInventory inventory = Gui.player().getInventory();

      for (int index = 0; index < 36; index++) {
         ItemStack stack = inventory.getStack(index);
         if (!stack.isEmpty() && strikes.getOrDefault(stack.getItem(), 0) < 2) {
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
         }
      }

      return counts;
   }

   private void openSell(Gui gui) {
      for (int attempt = 0; attempt < 3; attempt++) {
         this.command(gui, "sell");
         if (this.worker.waitFor(PanicPayModule::sellMenuOpen, 8000L)) {
            this.worker.sleep(150L);
            return;
         }

         gui.closeAll();
      }

      throw new AuctionHouse.Problem("The sell menu did not open");
   }

   private static boolean sellMenuOpen() {
      ClientPlayerEntity player = Gui.mc().player;
      if (player != null && player.currentScreenHandler != player.playerScreenHandler && Gui.mc().currentScreen instanceof HandledScreen) {
         int container = 0;

         for (Slot slot : player.currentScreenHandler.slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
               container++;
            }
         }

         return container > 53;
      } else {
         return false;
      }
   }

   private boolean takeOff(Gui gui, boolean armor, boolean offhand) {
      List<Integer> slots = this.worker.call(() -> {
         ClientPlayerEntity player = Gui.player();
         List<Integer> out = new ArrayList<>();
         if (armor) {
            for (int slotx : ARMOR_MENU_SLOTS) {
               if (player.playerScreenHandler.getSlot(slotx).hasStack()) {
                  out.add(slotx);
               }
            }
         }

         if (offhand && player.playerScreenHandler.getSlot(45).hasStack()) {
            out.add(45);
         }

         return out;
      });
      if (slots.isEmpty()) {
         return false;
      }

      gui.closeAll();

      for (int slot : slots) {
         gui.clickInventory(slot, 0, SlotActionType.QUICK_MOVE);
         this.worker.ticks(1);
      }

      gui.closeInventory();
      this.worker.ticks(4);
      return true;
   }

   private void payBalance(Gui gui, String target) {
      String amount = this.readBalance(gui);
      if (this.balance < 1.0) {
         this.say(ModuleChat.body().warn("You have no money to pay."));
      } else {
         for (int attempt = 0; attempt < 3; attempt++) {
            PanicPayModule.Payment result = this.pay(gui, target, amount);
            if (result == PanicPayModule.Payment.PAID) {
               this.say(ModuleChat.body().good("Paid $" + amount).text(" to ").name(target).text("."));
               return;
            }

            if (result == PanicPayModule.Payment.POOR) {
               throw new AuctionHouse.Problem("/pay " + target + " " + amount + " was refused: not enough money");
            }

            if (result == PanicPayModule.Payment.FAILED) {
               throw new AuctionHouse.Problem("/pay failed: " + this.errorText);
            }
         }

         throw new AuctionHouse.Problem("No answer to /pay " + target + " " + amount);
      }
   }

   private String readBalance(Gui gui) {
      for (int attempt = 0; attempt < 3; attempt++) {
         long mark = System.currentTimeMillis();
         this.command(gui, "bal");
         if (this.worker.waitFor(() -> this.balanceAt >= mark, 6000L)) {
            return this.balanceText;
         }
      }

      throw new AuctionHouse.Problem("Could not read your balance from /bal");
   }

   private PanicPayModule.Payment pay(Gui gui, String target, String amount) {
      long mark = System.currentTimeMillis();
      this.command(gui, "pay " + target + " " + amount);
      Object[] pressed = new Object[]{null};
      long end = mark + 6000L;

      while (System.currentTimeMillis() < end) {
         if (this.paidAt >= mark) {
            return PanicPayModule.Payment.PAID;
         }

         if (this.poorAt >= mark) {
            return PanicPayModule.Payment.POOR;
         }

         if (this.errorAt >= mark) {
            return PanicPayModule.Payment.FAILED;
         }

         if (this.holdUntil > mark) {
            return PanicPayModule.Payment.RETRY;
         }

         this.worker.run(() -> {
            Dialogs.View view = Dialogs.current(Gui.mc());
            if (view != null && view.dialog() != pressed[0]) {
               Dialogs.Button confirm = view.labelled("confirm", "yes", "pay");
               if (confirm != null && Dialogs.press(Gui.mc(), view, confirm, Map.of())) {
                  pressed[0] = view.dialog();
               }
            }
         });
         this.worker.sleep(50L);
      }

      return PanicPayModule.Payment.RETRY;
   }

   private void command(Gui gui, String command) {
      long wait = Math.max(this.holdUntil, this.lastCommandAt + 1000L) - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }

      gui.command(command);
      this.lastCommandAt = System.currentTimeMillis();
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }

   @Environment(EnvType.CLIENT)
   private enum Payment {
      PAID,
      POOR,
      RETRY,
      FAILED;
   }
}
