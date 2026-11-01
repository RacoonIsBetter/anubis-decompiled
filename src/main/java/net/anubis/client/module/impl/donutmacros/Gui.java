package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@Environment(EnvType.CLIENT)
final class Gui {
   private static final int COMMAND_GAP_MS = 200;
   final MacroWorker worker;

   Gui(MacroWorker worker) {
      this.worker = worker;
   }

   static MinecraftClient mc() {
      return MinecraftClient.getInstance();
   }

   static ClientPlayerEntity player() {
      ClientPlayerEntity player = mc().player;
      if (player == null) {
         throw new IllegalStateException("Not in a world");
      } else {
         return player;
      }
   }

   String title() {
      return this.worker.call(() -> mc().currentScreen == null ? "" : ServerText.normalize(mc().currentScreen.getTitle()));
   }

   static boolean containerOpenNow() {
      ClientPlayerEntity player = mc().player;
      return player != null && player.currentScreenHandler != player.playerScreenHandler && mc().currentScreen instanceof HandledScreen;
   }

   static List<ItemStack> containerNow() {
      List<ItemStack> out = new ArrayList<>();
      ClientPlayerEntity player = mc().player;
      if (player != null && player.currentScreenHandler != player.playerScreenHandler) {
         for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory instanceof PlayerInventory) {
               break;
            }

            out.add(slot.getStack().copy());
         }

         return out;
      } else {
         return out;
      }
   }

   List<ItemStack> container() {
      return this.worker.call(Gui::containerNow);
   }

   static String fingerprintNow() {
      StringBuilder text = new StringBuilder();
      ClientPlayerEntity player = mc().player;
      if (player != null) {
         text.append(player.currentScreenHandler.syncId).append('#');
      }

      for (ItemStack stack : containerNow()) {
         text.append(Registries.ITEM.getId(stack.getItem())).append(':').append(stack.getCount()).append(':').append(stack.getName().getString()).append('|');
      }

      return text.toString();
   }

   String fingerprint() {
      return this.worker.call(Gui::fingerprintNow);
   }

   static ItemStack slotNow(int index) {
      List<ItemStack> stacks = containerNow();
      return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
   }

   static int findNow(Predicate<ItemStack> test, int limit) {
      List<ItemStack> stacks = containerNow();

      for (int i = 0; i < stacks.size() && i < limit; i++) {
         if (!stacks.get(i).isEmpty() && test.test(stacks.get(i))) {
            return i;
         }
      }

      return -1;
   }

   static String nameOf(ItemStack stack) {
      return ServerText.normalize(stack.getName());
   }

   void click(int slot, int button, SlotActionType type) {
      this.worker.run(() -> {
         ClientPlayerEntity player = player();
         ScreenHandler menu = player.currentScreenHandler;
         mc().interactionManager.clickSlot(menu.syncId, slot, button, type, player);
      });
   }

   void clickInventory(int slot, int button, SlotActionType type) {
      this.worker.run(() -> {
         ClientPlayerEntity player = player();
         mc().interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, button, type, player);
      });
   }

   void closeInventory() {
      this.worker.run(() -> {
         ClientPlayerEntity player = player();
         if (player.currentScreenHandler == player.playerScreenHandler) {
            player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(player.playerScreenHandler.syncId));
         }
      });
   }

   void command(String command) {
      this.worker.run(() -> player().networkHandler.sendChatCommand(command));
      this.worker.sleep(200L);
   }

   void closeAll() {
      for (int i = 0; i < 4; i++) {
         boolean open = this.worker.call(() -> {
            ClientPlayerEntity player = mc().player;
            if (player == null) {
               return false;
            } else if (player.currentScreenHandler != player.playerScreenHandler) {
               player.closeHandledScreen();
               return true;
            } else if (mc().currentScreen != null && !(mc().currentScreen instanceof DeathScreen)) {
               mc().currentScreen.close();
               return true;
            } else {
               return false;
            }
         });
         if (!open) {
            return;
         }

         this.worker.ticks(2);
      }
   }

   boolean waitChange(String before, long timeoutMs) {
      return this.worker.waitFor(() -> !fingerprintNow().equals(before), timeoutMs);
   }
}
