package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

@Environment(EnvType.CLIENT)
final class KnifeMaterial {
   static final int BLADE = 0;
   static final int BLADE_DARK = 1;
   static final int EDGE = 2;
   static final int HANDLE = 3;
   static final int HANDLE_DARK = 4;
   static final int METAL = 5;
   static final int ACCENT = 6;
   static final int WRAP = 7;
   static final int GOLD = 8;
   private static final KnifeMaterial DIAMOND = new KnifeMaterial(7340016, 4187087, 1941139, 13828087, 1780531, 989213, 12109772, 4187087, 1391168, 15253850);
   private static final KnifeMaterial NETHERITE = new KnifeMaterial(16742973, 6445663, 3813942, 11773356, 1709592, 920333, 7234665, 13715758, 3806228, 13213770);
   private static final KnifeMaterial IRON = new KnifeMaterial(16777215, 14277081, 10197915, 16777215, 4862754, 2825490, 9342606, 13158600, 2829099, 14267466);
   private static final KnifeMaterial GOLDEN = new KnifeMaterial(
      16769126, 16242762, 13015574, 16775096, 5905950, 3674128, 15188812, 16770688, 7216927, 15912795
   );
   private static final KnifeMaterial COPPER = new KnifeMaterial(16755319, 15043163, 11096108, 16763302, 3096378, 1844771, 6274975, 6274975, 2375738, 14263130);
   private static final KnifeMaterial STONE = new KnifeMaterial(14540253, 10263708, 6710886, 13158600, 4862754, 2825490, 8026746, 11053224, 3811868, 12558416);
   private static final KnifeMaterial WOOD = new KnifeMaterial(15782560, 12290902, 8279854, 14529402, 5914146, 3679763, 9071172, 13081442, 4861976, 13213770);
   final int[] palette;
   final int glow;

   private KnifeMaterial(int glow, int... palette) {
      this.glow = glow;
      this.palette = new int[palette.length];

      for (int i = 0; i < palette.length; i++) {
         this.palette[i] = 0xFF000000 | palette[i];
      }
   }

   static KnifeMaterial of(ItemStack stack) {
      return named(Registries.ITEM.getId(stack.getItem()).getPath());
   }

   static KnifeMaterial named(String path) {
      if (path.startsWith("netherite")) {
         return NETHERITE;
      } else if (path.startsWith("diamond")) {
         return DIAMOND;
      } else if (path.startsWith("golden")) {
         return GOLDEN;
      } else if (path.startsWith("copper")) {
         return COPPER;
      } else if (path.startsWith("stone")) {
         return STONE;
      } else {
         return path.startsWith("wooden") ? WOOD : IRON;
      }
   }

   static boolean blade(int material) {
      return material == 0 || material == 1 || material == 2;
   }

   static boolean metallic(int material) {
      return material == 0 || material == 1 || material == 2 || material == 5 || material == 8;
   }
}
