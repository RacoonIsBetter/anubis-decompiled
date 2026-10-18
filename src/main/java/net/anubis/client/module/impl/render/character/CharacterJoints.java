package net.anubis.client.module.impl.render.character;

import net.anubis.client.module.impl.render.character.kit.Bone;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.SwingAnimationType;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose;

@Environment(EnvType.CLIENT)
final class CharacterJoints {
   static final float CROUCH_HIP = 0.5F;
   static final float SADDLE_KNEE = 1.35F;
   static final float SEAT_KNEE = 0.3F;
   private static final float CROUCH_KNEE = 0.62F;
   private static final float CROUCH_ELBOW = 0.2F;
   private static final float MAX_KNEE = 1.7F;
   private static final float MAX_ELBOW = 1.6F;

   private CharacterJoints() {
   }

   static float angle(PlayerEntityRenderState state, Bone bone, float swing) {
      float fade = !state.isGliding && !state.usingRiptide ? 1.0F - state.leaningPitch * 0.8F : 0.0F;
      if (fade <= 0.0F) {
         return 0.0F;
      } else if (bone.leg()) {
         return knee(state, bone) * fade;
      } else {
         return bone.arm() ? -elbow(state, bone, swing) * fade : 0.0F;
      }
   }

   private static float knee(PlayerEntityRenderState state, Bone bone) {
      if (state.hasVehicle) {
         return CharacterModule.seat(state);
      }

      float speed = Math.min(1.0F, state.limbSwingAmplitude / state.limbAmplitudeInverse);
      float phase = state.limbSwingAnimationProgress * 0.6662F + (bone == Bone.LEFT_LEG ? (float) Math.PI : 0.0F);
      float flex = speed * (0.1F + 0.85F * Math.max(0.0F, MathHelper.sin(phase)));
      if (state.isInSneakingPose) {
         flex += 0.62F;
      }

      return Math.min(1.7F, flex);
   }

   private static float elbow(PlayerEntityRenderState state, Bone bone, float swing) {
      boolean right = bone == Bone.RIGHT_ARM;
      ArmPose pose = right ? state.rightArmPose : state.leftArmPose;
      ArmPose other = right ? state.leftArmPose : state.rightArmPose;
      if (other.isTwoHanded()) {
         return 0.0F;
      }

      float speed = Math.min(1.0F, state.limbSwingAmplitude / state.limbAmplitudeInverse);

      float flex = switch (pose) {
         case EMPTY -> 0.16F + 0.45F * Math.max(0.0F, -swing) + 0.3F * speed;
         case ITEM -> 0.3F + 0.2F * speed;
         case BLOCK -> 0.55F;
         case BRUSH -> 0.4F;
         default -> 0.0F;
      };
      if (flex <= 0.0F) {
         return 0.0F;
      }

      if (state.isInSneakingPose) {
         flex += 0.2F;
      }

      Arm arm = right ? Arm.RIGHT : Arm.LEFT;
      if (state.handSwingProgress > 0.0F && state.preferredArm == arm && state.swingAnimationType == SwingAnimationType.WHACK) {
         flex += 0.6F * MathHelper.sin(state.handSwingProgress * (float) Math.PI);
      }

      return Math.min(1.6F, flex);
   }
}
