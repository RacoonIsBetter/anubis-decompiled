package net.anubis.client.module.impl.misc;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class WeatherNotifierModule extends ClientModule {
   private static final int STABLE_TICKS = 20;
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private ClientWorld level;
   private boolean raining;
   private int changedTicks;

   public WeatherNotifierModule() {
      super("weather-notifier", "Weather Notifier", "Shows an advancement-style toast when it starts raining.", ModuleCategory.MISC);
   }

   @Override
   protected void onEnable() {
      this.level = null;
      this.raining = false;
      this.changedTicks = 0;
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.level = null;
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld current = mc.world;
      if (current != this.level) {
         this.level = current;
         this.raining = false;
         this.changedTicks = 0;
      }

      if (current != null && !(mc.currentScreen instanceof LevelLoadingScreen)) {
         if (current.isRaining() == this.raining) {
            this.changedTicks = 0;
         } else if (++this.changedTicks >= 20) {
            this.raining = !this.raining;
            this.changedTicks = 0;
            if (this.raining) {
               mc.getToastManager().add(new WeatherNotifierModule.WeatherToast(this.sound.get()));
            }
         }
      } else {
         this.changedTicks = 0;
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class WeatherToast extends ChallengeToast {
      private static final Text TITLE = Text.literal("Weather Changed!");
      private static final Text MESSAGE = Text.literal("It started raining.");

      WeatherToast(boolean sound) {
         super(new ItemStack(Items.TRIDENT), TITLE, MESSAGE, sound);
      }
   }
}
