package net.anubis.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class TargetingUtils {
   private TargetingUtils() {
   }

   public static Vec3d closestPoint(Vec3d point, Box box) {
      return new Vec3d(
         MathHelper.clamp(point.x, box.minX, box.maxX),
         MathHelper.clamp(point.y, box.minY, box.maxY),
         MathHelper.clamp(point.z, box.minZ, box.maxZ)
      );
   }
}
