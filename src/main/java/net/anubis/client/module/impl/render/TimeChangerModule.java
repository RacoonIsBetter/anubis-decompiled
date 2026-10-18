package net.anubis.client.module.impl.render;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class TimeChangerModule extends ClientModule {
   public static volatile TimeChangerModule active;
   private final NumberSetting time = this.addSetting("time", new NumberSetting("Time", 0.0F, -20000.0F, 20000.0F).step(1.0F));
   private ClientWorld trackedLevel;
   private long serverDayTime;
   private long appliedDayTime;

   public TimeChangerModule() {
      super("time-changer", "Time Changer", "Makes you able to set a custom time.", ModuleCategory.RENDER);
   }

   @Override
   protected void onEnable() {
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         this.applyForcedTime(level);
      }
   }

   @Override
   protected void onDisable() {
      active = null;
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && level == this.trackedLevel) {
         writeDayTime(level, this.currentServerDayTime(level));
      }

      this.trackedLevel = null;
   }

   private void onPostTick(TickEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         this.trackedLevel = null;
      } else {
         this.applyForcedTime(level);
      }
   }

   private void applyForcedTime(ClientWorld level) {
      this.serverDayTime = level == this.trackedLevel ? this.currentServerDayTime(level) : level.getTimeOfDay();
      this.trackedLevel = level;
      this.appliedDayTime = this.forcedDayTime();
      writeDayTime(level, this.appliedDayTime);
   }

   private static void writeDayTime(ClientWorld level, long dayTime) {
      if (level.getTimeOfDay() != dayTime) {
         level.getLevelProperties().setTimeOfDay(dayTime);
         level.getEnvironmentAttributes().tick();
      }
   }

   private long currentServerDayTime(ClientWorld level) {
      return this.serverDayTime + (level.getTimeOfDay() - this.appliedDayTime);
   }

   public long interceptServerDayTime(ClientWorld level, long dayTime) {
      this.trackedLevel = level;
      this.serverDayTime = dayTime;
      this.appliedDayTime = this.forcedDayTime();
      return this.appliedDayTime;
   }

   private long forcedDayTime() {
      return (long)this.time.get();
   }
}
