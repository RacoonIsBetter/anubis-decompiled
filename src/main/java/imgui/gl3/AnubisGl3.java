package imgui.gl3;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.type.ImInt;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

@Environment(EnvType.CLIENT)
public class AnubisGl3 extends ImGuiImplGl3 {
   public boolean createFontsTexture() {
      ImFontAtlas atlas = ImGui.getIO().getFonts();
      ImInt width = new ImInt();
      ImInt height = new ImInt();
      ByteBuffer pixels = atlas.getTexDataAsRGBA32(width, height);
      int maximum = GL11.glGetInteger(3379);
      long byteCount = (long)width.get() * height.get() * 4L;
      if (width.get() > 0
         && height.get() > 0
         && width.get() <= maximum
         && height.get() <= maximum
         && pixels != null
         && byteCount <= pixels.capacity()
         && byteCount <= 2147483647L) {
         ByteBuffer upload = pixels.duplicate();
         upload.position(0);
         upload.limit((int)byteCount);
         int oldActive = GL11.glGetInteger(34016);
         GL13.glActiveTexture(33984);
         int oldTexture = GL11.glGetInteger(32873);
         int oldUnpackBuffer = GL11.glGetInteger(35055);
         int oldAlignment = GL11.glGetInteger(3317);
         int oldRowLength = GL11.glGetInteger(3314);
         int oldSkipRows = GL11.glGetInteger(3315);
         int oldSkipPixels = GL11.glGetInteger(3316);
         int oldFont = this.data.fontTexture;
         int texture = 0;
         boolean success = false;

         try {
            reportExistingErrors();
            GL15.glBindBuffer(35052, 0);
            GL11.glPixelStorei(3317, 1);
            GL11.glPixelStorei(3314, 0);
            GL11.glPixelStorei(3315, 0);
            GL11.glPixelStorei(3316, 0);
            texture = GL11.glGenTextures();
            GL11.glBindTexture(3553, texture);
            GL11.glTexParameteri(3553, 10241, 9729);
            GL11.glTexParameteri(3553, 10240, 9729);
            GL11.glTexParameteri(3553, 10242, 33071);
            GL11.glTexParameteri(3553, 10243, 33071);
            GL11.glTexParameteri(3553, 33084, 0);
            GL11.glTexParameteri(3553, 33085, 0);
            GL11.glTexImage2D(3553, 0, 32856, width.get(), height.get(), 0, 6408, 5121, upload);
            int error = GL11.glGetError();
            if (texture == 0 || error != 0) {
               throw new IllegalStateException(
                  "[Anubis ImGui] Font upload failed: "
                     + width.get()
                     + "x"
                     + height.get()
                     + ", GL error=0x"
                     + Integer.toHexString(error)
                     + ", renderer="
                     + GL11.glGetString(7937)
               );
            }

            this.data.fontTexture = texture;
            atlas.setTexID(texture);
            success = true;
            System.err
               .printf(
                  Locale.ROOT,
                  "[Anubis ImGui] Font atlas uploaded: %dx%d RGBA8, max=%d, previousUnpackBuffer=%d, GL=%s, renderer=%s%n",
                  width.get(),
                  height.get(),
                  maximum,
                  oldUnpackBuffer,
                  GL11.glGetString(7938),
                  GL11.glGetString(7937)
               );
         } finally {
            if (!success && texture != 0) {
               GL11.glDeleteTextures(texture);
            }

            int restoredTexture = success && oldFont != 0 && oldTexture == oldFont ? texture : oldTexture;
            GL11.glBindTexture(3553, restoredTexture);
            GL15.glBindBuffer(35052, oldUnpackBuffer);
            GL11.glPixelStorei(3317, oldAlignment);
            GL11.glPixelStorei(3314, oldRowLength);
            GL11.glPixelStorei(3315, oldSkipRows);
            GL11.glPixelStorei(3316, oldSkipPixels);
            GL13.glActiveTexture(oldActive);
            if (success && oldFont != 0 && oldFont != texture) {
               GL11.glDeleteTextures(oldFont);
            }
         }

         return true;
      } else {
         throw new IllegalStateException("[Anubis ImGui] Invalid font atlas " + width.get() + "x" + height.get() + "; GL_MAX_TEXTURE_SIZE=" + maximum);
      }
   }

   private static void reportExistingErrors() {
      for (int i = 0; i < 16; i++) {
         int error = GL11.glGetError();
         if (error == 0) {
            return;
         }

         System.err.println("[Anubis ImGui] GL error predating font upload: 0x" + Integer.toHexString(error));
      }
   }
}
