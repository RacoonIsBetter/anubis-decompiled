package net.anubis.client.module.impl.combat;

import com.example.client.mixin.AccessorCombatMinecraft;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class FastXpModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 3.0F).step(1.0F));

   public FastXpModule() {
      super("fastxp", "FastXP", "Throws experience bottles faster while use is held.", ModuleCategory.COMBAT);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.currentScreen == null && mc.options.useKey.isPressed()) {
         if (!player.isUsingItem() && holdsXp(player)) {
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

   private static boolean holdsXp(ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      return main.isOf(Items.EXPERIENCE_BOTTLE)
         || player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE) && !(main.getItem() instanceof BlockItem);
   }
}
