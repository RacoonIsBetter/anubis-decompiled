package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.entry.RegistryEntry;

@Environment(EnvType.CLIENT)
public final class GrimRiptide {
   public static final int MIN_TICKS = 9;

   private GrimRiptide() {
   }

   public static boolean clientAccepts() {
      MinecraftClient mc = MinecraftClient.getInstance();
      PlayerEntity player = mc.player;
      if (player != null && mc.world != null && mc.isOnThread()) {
         if (!player.isTouchingWaterOrRain() && !mc.world.isRaining()) {
            Box box = player.getBoundingBox().expand(0.1);
            BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

            for (BlockPos pos : BlockPos.iterate(min, max)) {
               BlockState state = mc.world.getBlockState(pos);
               if (state.getFluidState().isOf(Fluids.WATER)) {
                  return true;
               }

               if (state.getFluidState().isOf(Fluids.FLOWING_WATER)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isRiptide(ItemStack stack) {
      if (!stack.isOf(Items.TRIDENT)) {
         return false;
      }

      for (Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
         RegistryEntry<Enchantment> enchantment = (RegistryEntry<Enchantment>)entry.getKey();
         if (enchantment.matchesKey(Enchantments.RIPTIDE) && entry.getIntValue() > 0) {
            return true;
         }
      }

      return false;
   }
}
