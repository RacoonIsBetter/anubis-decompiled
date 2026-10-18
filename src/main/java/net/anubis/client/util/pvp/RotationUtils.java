package net.anubis.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class RotationUtils {
   private RotationUtils() {
   }

   public static Rotation fromTo(Vec3d from, Vec3d to) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(MathHelper.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
      float pitch = (float)(-(MathHelper.atan2(dy, horizontal) * 180.0F / (float)Math.PI));
      return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F));
   }

   public static Rotation entityRotation(Entity entity) {
      return new Rotation(entity.getYaw(), entity.getPitch());
   }

   public static float yawDelta(float fromYaw, float toYaw) {
      return MathHelper.wrapDegrees(toYaw - fromYaw);
   }

   public static float pitchDelta(float fromPitch, float toPitch) {
      return toPitch - fromPitch;
   }

   public static double angleTo(Rotation from, Rotation to) {
      float yaw = yawDelta(from.yaw(), to.yaw());
      float pitch = pitchDelta(from.pitch(), to.pitch());
      return Math.hypot(yaw, pitch);
   }

   public static double mouseScale(MinecraftClient mc, ClientPlayerEntity player) {
      double speed = (Double)mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F;
      return speed * speed * speed * (mc.options.getPerspective().isFirstPerson() && player.isUsingSpyglass() ? 1.0 : 8.0);
   }
}
