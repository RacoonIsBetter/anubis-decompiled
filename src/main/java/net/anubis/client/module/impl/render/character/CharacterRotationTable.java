package net.anubis.client.module.impl.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class CharacterRotationTable {
   final float[] values = new float[153];

   void update(float pitch, float yaw, float roll) {
      for (int k = 0; k <= 16; k++) {
         float t = k / 16.0F;
         float cx = (float)Math.cos(pitch * t);
         float sx = (float)Math.sin(pitch * t);
         float cy = (float)Math.cos(yaw * t);
         float sy = (float)Math.sin(yaw * t);
         float cz = (float)Math.cos(roll * t);
         float sz = (float)Math.sin(roll * t);
         int o = k * 9;
         this.values[o] = cz * cy;
         this.values[o + 1] = cz * sy * sx - sz * cx;
         this.values[o + 2] = cz * sy * cx + sz * sx;
         this.values[o + 3] = sz * cy;
         this.values[o + 4] = sz * sy * sx + cz * cx;
         this.values[o + 5] = sz * sy * cx - cz * sx;
         this.values[o + 6] = -sy;
         this.values[o + 7] = cy * sx;
         this.values[o + 8] = cy * cx;
      }
   }
}
