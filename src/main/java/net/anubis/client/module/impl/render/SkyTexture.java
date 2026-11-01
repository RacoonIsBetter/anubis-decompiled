package net.anubis.client.module.impl.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.ColorHelper;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
final class SkyTexture implements SkyImage {
   private final int width;
   private final int height;
   private boolean mipmapped;
   private GpuTexture texture;
   private GpuTextureView view;

   private SkyTexture(int width, int height, GpuTexture texture, GpuTextureView view) {
      this.width = width;
      this.height = height;
      this.texture = texture;
      this.view = view;
   }

   static SkyTexture upload(String name, int width, int height, int[][] levels) {
      if (levels.length <= 1) {
         return upload(name, width, height, levels.length == 0 ? new int[0] : levels[0]);
      }

      for (int level = 0; level < levels.length; level++) {
         if (levels[level].length < SkyMips.size(width, level) * SkyMips.size(height, level)) {
            throw new IllegalArgumentException("Sky texture " + name + " is missing pixels at mip level " + level);
         }
      }

      String label = "anubis:sky/" + name;
      GpuDevice device = RenderSystem.getDevice();
      GpuTexture texture = device.createTexture(() -> label, 5, TextureFormat.RGBA8, width, height, 1, levels.length);

      try {
         for (int level = 0; level < levels.length; level++) {
            int w = SkyMips.size(width, level);
            int h = SkyMips.size(height, level);
            int[] argb = levels[level];

            try (NativeImage image = new NativeImage(w, h, false)) {
               IntBuffer pixels = MemoryUtil.memIntBuffer(image.imageId(), w * h);

               for (int i = 0; i < w * h; i++) {
                  pixels.put(i, ColorHelper.toAbgr(argb[i]));
               }

               device.createCommandEncoder().writeToTexture(texture, image, level, 0, 0, 0, w, h, 0, 0);
            }
         }

         SkyTexture made = new SkyTexture(width, height, texture, device.createTextureView(texture));
         made.mipmapped = true;
         return made;
      } catch (RuntimeException e) {
         texture.close();
         throw e;
      }
   }

   static SkyTexture upload(String name, int width, int height, int[] argb) {
      if (argb.length < width * height) {
         throw new IllegalArgumentException("Sky texture " + name + " is missing pixels");
      }

      String label = "anubis:sky/" + name;
      GpuDevice device = RenderSystem.getDevice();

      try (NativeImage image = new NativeImage(width, height, false)) {
         IntBuffer pixels = MemoryUtil.memIntBuffer(image.imageId(), width * height);

         for (int i = 0; i < width * height; i++) {
            pixels.put(i, ColorHelper.toAbgr(argb[i]));
         }

         GpuTexture texture = device.createTexture(() -> label, 5, TextureFormat.RGBA8, width, height, 1, 1);

         try {
            device.createCommandEncoder().writeToTexture(texture, image);
            return new SkyTexture(width, height, texture, device.createTextureView(texture));
         } catch (RuntimeException e) {
            texture.close();
            throw e;
         }
      }
   }

   @Override
   public int width() {
      return this.width;
   }

   @Override
   public int height() {
      return this.height;
   }

   GpuTextureView view() {
      return this.view;
   }

   GpuSampler sampler() {
      return RenderSystem.getSamplerCache().get(FilterMode.LINEAR, this.mipmapped);
   }

   @Override
   public void close() {
      if (this.view != null) {
         this.view.close();
      }

      if (this.texture != null) {
         this.texture.close();
      }

      this.view = null;
      this.texture = null;
   }
}
