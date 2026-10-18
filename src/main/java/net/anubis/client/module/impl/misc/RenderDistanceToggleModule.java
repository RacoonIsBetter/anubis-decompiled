package net.anubis.client.module.impl.misc;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class RenderDistanceToggleModule extends ClientModule {
   private static final int RESTORE_AFTER_TICKS = 10;
   private static final int REARM_MARGIN = 2;
   private final NumberSetting lowDistance = this.addSetting("low-distance", new NumberSetting("Low Distance", 2.0F, 2.0F, 32.0F).step(1.0F));
   private final NumberSetting resetY = this.addSetting("reset-y", new NumberSetting("Reset Y", -1.0F, -64.0F, 320.0F).step(1.0F));
   private boolean armed = true;
   private boolean restoring;
   private int timer;
   private int savedDistance = -1;

   public RenderDistanceToggleModule() {
      super("render-distance-toggle", "Render Distance Toggle", "Reloads chunks when you go below a Y level.", ModuleCategory.BASE_RAID);
   }

   @Override
   protected void onEnable() {
      this.armed = true;
      this.restoring = false;
      this.timer = 0;
      this.savedDistance = -1;
   }

   @Override
   protected void onDisable() {
      if (this.restoring) {
         this.restore(MinecraftClient.getInstance());
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         int y = mc.player.getBlockY();
         if (!this.restoring && y > this.resetY.getInt() + 2) {
            this.armed = true;
         }

         if (this.armed && y <= this.resetY.getInt()) {
            this.armed = false;
            this.restoring = true;
            this.timer = 0;
            this.savedDistance = (Integer)mc.options.getViewDistance().getValue();
            apply(mc, this.lowDistance.getInt());
         } else if (this.restoring && ++this.timer >= 10) {
            this.restore(mc);
         }
      } else {
         if (this.restoring) {
            this.restore(mc);
         }
      }
   }

   private void restore(MinecraftClient mc) {
      if (this.savedDistance >= 0) {
         apply(mc, this.savedDistance);
      }

      this.restoring = false;
   }

   private static void apply(MinecraftClient mc, int chunks) {
      mc.options.getViewDistance().setValue(chunks);
      mc.options.sendClientSettings();
   }
}
