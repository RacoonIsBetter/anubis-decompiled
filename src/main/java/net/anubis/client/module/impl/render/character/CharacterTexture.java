package net.anubis.client.module.impl.render.character;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.AbstractTexture;

@Environment(EnvType.CLIENT)
final class CharacterTexture extends AbstractTexture {
   CharacterTexture(String label, int width, int height, int[][] mips) {
      GpuDevice device = RenderSystem.getDevice();
      this.glTexture = device.createTexture(() -> label, 5, TextureFormat.RGBA8, width, height, 1, mips.length);
      this.glTextureView = device.createTextureView(this.glTexture);
      CommandEncoder encoder = device.createCommandEncoder();
      int w = width;
      int h = height;

      for (int level = 0; level < mips.length; level++) {
         int[] pixels = mips[level];
         NativeImage image = new NativeImage(w, h, false);

         try {
            for (int y = 0; y < h; y++) {
               for (int x = 0; x < w; x++) {
                  image.setColorArgb(x, y, pixels[y * w + x]);
               }
            }

            encoder.writeToTexture(this.glTexture, image, level, 0, 0, 0, w, h, 0, 0);
         } catch (Throwable var15) {
            try {
               image.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }

            throw var15;
         }

         image.close();
         w = Math.max(1, w / 2);
         h = Math.max(1, h / 2);
      }

      this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR, true);
   }
}
