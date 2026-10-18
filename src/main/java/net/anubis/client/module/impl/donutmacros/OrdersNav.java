package net.anubis.client.module.impl.donutmacros;

import java.util.function.Consumer;
import net.anubis.client.chat.ModuleChat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

@Environment(EnvType.CLIENT)
final class OrdersNav {
   static final int YOUR_ORDERS_BUTTON = 51;
   static final int ORDER_SLOTS = 45;
   private static final long OPEN_TIMEOUT_MS = 10000L;

   private OrdersNav() {
   }

   static boolean openYourOrders(MacroWorker worker, Gui gui, Consumer<ModuleChat.Body> say) {
      gui.closeAll();
      worker.sleep(150L);
      gui.command("orders");
      if (!worker.waitFor(OrdersNav::isOrdersRootNow, 10000L)) {
         say.accept(ModuleChat.body().warn("Could not open /orders."));
         gui.closeAll();
         return false;
      } else {
         String before = gui.fingerprint();
         gui.click(51, 0, SlotActionType.PICKUP);
         if (worker.waitFor(OrdersNav::isYourOrdersNow, 2500L)) {
            return true;
         } else if (gui.waitChange(before, 1000L)) {
            worker.sleep(150L);
            return true;
         } else {
            say.accept(ModuleChat.body().warn("Could not open Your Orders."));
            gui.closeAll();
            return false;
         }
      }
   }

   static boolean isOrdersRootNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      } else {
         String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
         if (title.startsWith("orders (page") || title.equals("orders")) {
            return true;
         } else if (title.contains("your orders") || title.contains("auction")) {
            return false;
         } else {
            return title.contains("orders") ? true : Gui.nameOf(Gui.slotNow(51)).contains("your order");
         }
      }
   }

   static boolean isYourOrdersNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      }

      String title = ServerText.normalize(Gui.mc().currentScreen.getTitle());
      return title.contains("your orders") || Gui.findNow(OrdersNav::isNewOrderPane, 45) >= 0;
   }

   static boolean isNewOrderPane(ItemStack stack) {
      return stack.isOf(Items.GRAY_STAINED_GLASS_PANE) && Gui.nameOf(stack).contains("new order");
   }

   static boolean isPane(ItemStack stack) {
      return stack.isEmpty() || Registries.ITEM.getId(stack.getItem()).getPath().contains("stained_glass_pane");
   }
}
