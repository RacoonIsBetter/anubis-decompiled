package net.anubis.client.gui;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.special.SimpleSpecialModelRenderer;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.PlayerSkinCache;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.component.DataComponentTypes;

import net.minecraft.client.render.item.model.special.SpecialModelRenderer.BakeContext;
import net.minecraft.block.SkullBlock.Type;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel.SkullModelState;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class GlintHeadRenderer implements SimpleSpecialModelRenderer {
   public static final Identifier ID = Identifier.of("anubis", "glint_head");
   private final SkullBlockEntityModel model;

   private GlintHeadRenderer(SkullBlockEntityModel model) {
      this.model = model;
   }

   public static ItemStack stack() {
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      head.set(DataComponentTypes.ITEM_MODEL, ID);
      head.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
      return head;
   }

   public void render(ItemDisplayContext context, MatrixStack pose, OrderedRenderCommandQueue collector, int light, int overlay, boolean foil, int outline) {
      SkullBlockEntityRenderer.render(null, 180.0F, 0.0F, pose, collector, light, this.model, PlayerSkinCache.DEFAULT_RENDER_LAYER, outline, null);
      if (foil) {
         pose.push();
         pose.translate(0.5F, 0.0F, 0.5F);
         pose.scale(-1.0F, -1.0F, 1.0F);
         SkullModelState state = new SkullModelState();
         state.yaw = 180.0F;
         collector.getBatchingQueue(1).submitModel(this.model, state, pose, RenderLayers.entityGlint(), light, OverlayTexture.DEFAULT_UV, 0, null);
         pose.pop();
      }
   }

   public void collectVertices(Consumer<Vector3fc> output) {
      MatrixStack pose = new MatrixStack();
      pose.translate(0.5F, 0.0F, 0.5F);
      pose.scale(-1.0F, -1.0F, 1.0F);
      this.model.getRootPart().collectVertices(pose, output);
   }

   @Environment(EnvType.CLIENT)
   public record Unbaked() implements net.minecraft.client.render.item.model.special.SpecialModelRenderer.Unbaked {
      public static final MapCodec<GlintHeadRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new GlintHeadRenderer.Unbaked());

      public MapCodec<GlintHeadRenderer.Unbaked> getCodec() {
         return MAP_CODEC;
      }

      public SpecialModelRenderer<?> bake(BakeContext context) {
         SkullBlockEntityModel model = SkullBlockEntityRenderer.getModels(context.entityModelSet(), Type.PLAYER);
         return model == null ? null : new GlintHeadRenderer(model);
      }
   }
}
