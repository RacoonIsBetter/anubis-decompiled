package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
interface SkyPaint {
   float HORIZON = 0.72F;
   float RADIANS_PER_UNIT = (float) (Math.PI / 3);

   SkyImage upload(String var1, int var2, int var3, int[] var4);

   SkyImage upload(String var1, int var2, int var3, int[][] var4);

   void gradient(boolean var1, float var2, float var3, float var4, float var5, int var6, int var7);

   void band(SkyImage var1, float var2, float var3, float var4, float var5, int var6, boolean var7, int var8);

   void sprite(SkyImage var1, float var2, float var3, float var4, float var5, float var6, int var7, boolean var8);

   void glow(float var1, float var2, float var3, int var4, float var5);
}
