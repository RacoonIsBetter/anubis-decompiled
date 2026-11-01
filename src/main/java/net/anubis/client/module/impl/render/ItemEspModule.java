package net.anubis.client.module.impl.render;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ItemColorListSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class ItemEspModule extends ClientModule {
   private static ItemEspModule instance;
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 128.0F, 10.0F, 512.0F).step(1.0F));
   private final BooleanSetting sOutline = this.addSetting("outline", new BooleanSetting("Outline", true));
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final ItemColorListSetting sItemColors = this.addSetting(ItemColorListSetting.createDefault());

   public ItemEspModule() {
      super("item-esp", "Item ESP", "Highlights selected dropped items with model outlines and tracers.", ModuleCategory.RENDER);
      instance = this;
   }

   public static boolean shouldGlow(Entity entity) {
      ItemEspModule module = instance;
      return module != null && module.isEnabled() && module.sOutline.get() ? entity instanceof ItemEntity item && module.wants(item, 1.0F) : false;
   }

   public static int glowColor(Entity entity) {
      ItemEspModule module = instance;
      return module != null && entity instanceof ItemEntity item ? module.sItemColors.colorArgb(item.getStack().getItem()) & 16777215 : 16777215;
   }

   private boolean wants(ItemEntity item, float partialTick) {
      if (item.isRemoved()) {
         return false;
      }

      Item kind = item.getStack().getItem();
      if (!this.sItemColors.isEnabled(kind)) {
         return false;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return true;
      }

      float range = Math.max(1.0F, this.sRange.get());
      return item.getLerpedPos(partialTick).squaredDistanceTo(mc.player.getCameraPosVec(partialTick)) <= (double)range * range;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sTracers.get() && this.sItemColors.hasAnyEnabled()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
               if (entity instanceof ItemEntity item && this.wants(item, event.partialTick)) {
                  Vec3d pos = item.getLerpedPos(event.partialTick);
                  TracerEngine.draw(pos, this.sItemColors.colorArgb(item.getStack().getItem()));
               }
            }
         }
      }
   }
}
