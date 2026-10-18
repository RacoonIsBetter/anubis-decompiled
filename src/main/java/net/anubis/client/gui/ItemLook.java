package net.anubis.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;

@Environment(EnvType.CLIENT)
public final class ItemLook extends ItemRenderState {
   public final List<Object> identity = new ArrayList<>();
   private final List<LayerRenderState> layers = new ArrayList<>();

   public void clear() {
      super.clear();
      this.identity.clear();
      this.layers.clear();
   }

   public LayerRenderState newLayer() {
      LayerRenderState layer = super.newLayer();
      this.layers.add(layer);
      return layer;
   }

   public void addModelKey(Object element) {
      if (!(element instanceof Glint)) {
         this.identity.add(element);
      }
   }

   public boolean resolve(ItemStack stack) {
      MinecraftClient mc = MinecraftClient.getInstance();

      try {
         mc.getItemModelManager().clearAndUpdate(this, stack, ItemDisplayContext.GUI, mc.world, mc.player, 0);
      } catch (RuntimeException badModel) {
         this.clear();
         return false;
      }

      for (LayerRenderState layer : this.layers) {
         layer.setGlint(Glint.NONE);
      }

      if (this.identity.isEmpty()) {
         this.identity.add(stack.getItem());
      }

      return true;
   }
}
