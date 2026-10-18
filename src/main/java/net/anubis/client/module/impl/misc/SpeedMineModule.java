package net.anubis.client.module.impl.misc;

import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;

@Environment(EnvType.CLIENT)
public final class SpeedMineModule extends ClientModule {
   private static volatile SpeedMineModule active;
   private final NumberSetting breakAt = this.addSetting("break-at", new NumberSetting("Break At", 0.7F, 0.3F, 1.0F).step(0.01F));
   private final BooleanSetting skipDelay = this.addSetting("skip-delay", new BooleanSetting("Skip Break Delay", true));
   private final BooleanSetting keepInstantDelay = this.addSetting("keep-instant-delay", new BooleanSetting("Keep Instant Delay", true));

   public SpeedMineModule() {
      super("speed-mine", "Donut Speed Mine", "Finishes breaking blocks before the full break time.", ModuleCategory.DONUT);
      this.keepInstantDelay.visibleWhen(this.skipDelay::get);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public static float scaleProgress(BlockState state, float progress) {
      SpeedMineModule module = active;
      return module != null && affects(state) ? progress / module.breakAt.get() : progress;
   }

   public static boolean dropsDelay(BlockState state, float progress) {
      SpeedMineModule module = active;
      return module != null && module.skipDelay.get() && affects(state) ? !module.keepInstantDelay.get() || progress < 1.0F : false;
   }

   private static boolean affects(BlockState state) {
      if (state.isAir() || state.isOf(Blocks.SPAWNER)) {
         return false;
      } else {
         return state.isOf(Blocks.GRAVEL) ? true : !state.isIn(BlockTags.SHOVEL_MINEABLE) && !state.isIn(BlockTags.AXE_MINEABLE);
      }
   }
}
