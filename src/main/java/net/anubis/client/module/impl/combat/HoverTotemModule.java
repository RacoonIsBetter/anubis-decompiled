package net.anubis.client.module.impl.combat;

import com.example.client.mixin.AccessorAbstractContainerScreen;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class HoverTotemModule extends ClientModule {
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));

   public HoverTotemModule() {
      super("hovertotem", "Hover Totem", "Swaps a hovered totem from any open container into the offhand or selected hotbar slot.", ModuleCategory.COMBAT);
      this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.interactionManager != null && !player.isInCreativeMode()) {
         if (mc.currentScreen instanceof HandledScreen<?> screen && AutoInvTotemModule.clickSafe(player) && !AutoInvTotemModule.busy()) {
            ScreenHandler menu = screen.getScreenHandler();
            Slot hovered = ((AccessorAbstractContainerScreen)screen).anubis$getHoveredSlot();
            if (hovered != null && menu.getCursorStack().isEmpty() && hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
               if (hovered.inventory != player.getInventory() || hovered.getIndex() != 40) {
                  int swapButton;
                  if (!player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                     swapButton = 40;
                  } else {
                     if (!this.hotbarRefill.get()) {
                        return;
                     }

                     swapButton = this.hotbarSlot.getInt() - 1;
                     if (player.getInventory().getStack(swapButton).isOf(Items.TOTEM_OF_UNDYING)) {
                        return;
                     }
                  }

                  mc.interactionManager.clickSlot(menu.syncId, hovered.id, swapButton, SlotActionType.SWAP, player);
               }
            }
         }
      }
   }
}
