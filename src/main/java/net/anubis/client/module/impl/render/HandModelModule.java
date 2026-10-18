package net.anubis.client.module.impl.render;

import com.example.client.mixin.AccessorHandPlayerModel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public final class HandModelModule extends ClientModule {
   public static volatile HandModelModule active;
   private static final String ALL_ITEMS = "All Items";
   private static final String WEAPONS_TOOLS = "Weapons & Tools";
   private final ModeSetting items = this.addSetting("items", new ModeSetting("Items", "All Items", "All Items", "Weapons & Tools"));
   private final BooleanSetting mainHand = this.addSetting("main-hand", new BooleanSetting("Main Hand", true));
   private final BooleanSetting offHand = this.addSetting("off-hand", new BooleanSetting("Off Hand", true));

   public HandModelModule() {
      super("3d-hand", "3D Hand", "Shows your own arm holding the item in first person: swords, tools, items and blocks.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   static boolean wants(boolean mainHand) {
      HandModelModule module = active;
      return module != null && (mainHand ? module.mainHand.get() : module.offHand.get());
   }

   static boolean covers(ItemStack stack, boolean mainHand) {
      HandModelModule module = active;
      return wants(mainHand) && module != null && (module.items.is("All Items") || weaponOrTool(stack));
   }

   private static boolean weaponOrTool(ItemStack stack) {
      return stack.isIn(ItemTags.SWORDS)
         || stack.isIn(ItemTags.AXES)
         || stack.isIn(ItemTags.PICKAXES)
         || stack.isIn(ItemTags.SHOVELS)
         || stack.isIn(ItemTags.HOES)
         || stack.isIn(ItemTags.SPEARS)
         || stack.isOf(Items.MACE)
         || stack.isOf(Items.FISHING_ROD)
         || stack.isOf(Items.SHEARS)
         || stack.isOf(Items.FLINT_AND_STEEL)
         || stack.isOf(Items.BRUSH)
         || stack.isOf(Items.CARROT_ON_A_STICK)
         || stack.isOf(Items.WARPED_FUNGUS_ON_A_STICK);
   }

   public static void shoulder(MatrixStack pose, boolean right, PlayerEntityRenderer<?> renderer) {
      FirstPersonArm.shoulder(pose, right, ((AccessorHandPlayerModel)renderer.getModel()).anubis$handSlim());
   }

   public static void renderArm(MatrixStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, OrderedRenderCommandQueue collector, int light, boolean mainHand) {
      if (covers(stack, mainHand)) {
         FirstPersonArm.Arm arm = FirstPersonArm.arm();
         if (arm != null) {
            FirstPersonArm.hold(pose, arm, stack, context, holder, collector, light, 0.0F, 0.0F, 0.0F);
         }
      }
   }
}
