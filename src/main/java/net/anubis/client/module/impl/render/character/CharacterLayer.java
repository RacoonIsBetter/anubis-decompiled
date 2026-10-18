package net.anubis.client.module.impl.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Arm;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;

@Environment(EnvType.CLIENT)
public final class CharacterLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
   public CharacterLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent) {
      super(parent);
   }

   public void render(MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state, float yRot, float xRot) {
      WornCharacter worn = CharacterModule.worn(state);
      if (worn != null && (!state.invisible || !state.invisibleToPlayer)) {
         CharacterRenderer.submit(worn, (PlayerEntityModel)this.getContextModel(), poseStack, collector, light, state);
      }
   }

   public static boolean firstPersonHand(PlayerEntityModel model, MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, ModelPart arm) {
      return CharacterRenderer.firstPersonHand(model, poseStack, collector, light, arm);
   }

   public static boolean translateToHand(PlayerEntityModel model, PlayerEntityRenderState state, Arm arm, MatrixStack poseStack) {
      return CharacterRenderer.translateToHand(model, state, arm, poseStack);
   }
}
