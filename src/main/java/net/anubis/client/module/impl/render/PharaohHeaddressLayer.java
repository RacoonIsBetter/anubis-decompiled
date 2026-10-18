package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class PharaohHeaddressLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
   private static final Identifier TEXTURE = Identifier.of("anubis", "textures/cosmetic/headdress_base.png");
   private static final float HEAD_CENTRE = -0.25F;

   public PharaohHeaddressLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent) {
      super(parent);
   }

   public void render(MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state, float yRot, float xRot) {
      HeadCosmeticModule module = HeadCosmeticModule.worn;
      if (module != null && !state.invisible && module.wears(state)) {
         boolean helmet = !state.equippedHeadStack.isEmpty() || !state.headItemRenderState.isEmpty() || state.wearingSkullType != null;
         boolean chestplate = !state.equippedChestStack.isEmpty() && !state.equippedChestStack.contains(DataComponentTypes.GLIDER);
         PharaohMesh head = module.headMesh(helmet);
         PharaohMesh body = module.bodyMesh(chestplate);
         PharaohMesh.Look look = module.look();
         float scale = module.scale();
         RenderLayer type = RenderLayers.entityCutoutNoCull(TEXTURE);
         PlayerEntityModel model = (PlayerEntityModel)this.getContextModel();
         poseStack.push();
         model.getRootPart().applyTransform(poseStack);
         poseStack.push();
         model.getHead().applyTransform(poseStack);
         if (scale != 1.0F) {
            poseStack.translate(0.0F, -0.25F, 0.0F);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0F, 0.25F, 0.0F);
         }

         collector.submitCustom(poseStack, type, (pose, out) -> head.emit(pose, out, light, look));
         poseStack.pop();
         if (!body.isEmpty()) {
            model.body.applyTransform(poseStack);
            collector.submitCustom(poseStack, type, (pose, out) -> body.emit(pose, out, light, look));
         }

         poseStack.pop();
      }
   }
}
