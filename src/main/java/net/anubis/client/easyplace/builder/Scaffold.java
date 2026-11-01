package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.TransparentBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;

@Environment(EnvType.CLIENT)
final class Scaffold {
   private Scaffold() {
   }

   static boolean item(ItemStack stack) {
      return glass(stack) || stack.isIn(ItemTags.LEAVES);
   }

   static boolean glass(ItemStack stack) {
      return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TransparentBlock;
   }

   static boolean block(BlockState state) {
      return state.isIn(BlockTags.LEAVES) || state.getBlock() instanceof TransparentBlock;
   }

   static int count(ClientPlayerEntity player) {
      int count = 0;
      PlayerInventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         if (item(inventory.getStack(i))) {
            count += inventory.getStack(i).getCount();
         }
      }

      if (item(player.getOffHandStack())) {
         count += player.getOffHandStack().getCount();
      }

      return count;
   }

   static boolean has(ClientPlayerEntity player) {
      return Hands.has(player, Scaffold::item);
   }

   static boolean hold(MinecraftClient mc, ClientPlayerEntity player) {
      if (Hands.has(player, stack -> stack.isOf(Items.GLASS))) {
         return Hands.hold(mc, player, stack -> stack.isOf(Items.GLASS));
      } else {
         return Hands.has(player, stack -> stack.isIn(ItemTags.LEAVES))
            ? Hands.hold(mc, player, stack -> stack.isIn(ItemTags.LEAVES))
            : Hands.hold(mc, player, Scaffold::glass);
      }
   }
}
