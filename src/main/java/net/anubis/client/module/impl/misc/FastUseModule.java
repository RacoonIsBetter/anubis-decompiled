package net.anubis.client.module.impl.misc;

import com.example.client.mixin.AccessorCombatMinecraft;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class FastUseModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 3.0F).step(1.0F));
   private final BooleanSetting blocks = this.addSetting("blocks", new BooleanSetting("Blocks", true));
   private final BooleanSetting items = this.addSetting("items", new BooleanSetting("Items", true));

   public FastUseModule() {
      super("fast-use", "Fast Use", "Places blocks and uses items faster while use is held.", ModuleCategory.MISC);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.currentScreen == null && mc.options.useKey.isPressed()) {
         if (!player.isUsingItem() && this.covers(player)) {
            AccessorCombatMinecraft access = (AccessorCombatMinecraft)mc;
            int wait = access.anubis$rightClickDelay();
            if (!ServerRotation.pending() && !MeleeAttacks.actedThisTick(player)) {
               if (wait > this.delay.getInt() + 1) {
                  access.anubis$setRightClickDelay(this.delay.getInt() + 1);
               }
            } else {
               access.anubis$setRightClickDelay(Math.max(wait, 2));
               KeyBinding use = mc.options.useKey;

               while (use.wasPressed()) {
               }
            }
         }
      }
   }

   private boolean covers(ClientPlayerEntity player) {
      ItemStack held = player.getMainHandStack();
      if (held.isEmpty()) {
         held = player.getOffHandStack();
      }

      if (held.isEmpty()) {
         return false;
      } else {
         return held.getItem() instanceof BlockItem ? this.blocks.get() : this.items.get();
      }
   }
}
