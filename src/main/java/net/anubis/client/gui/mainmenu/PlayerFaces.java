package net.anubis.client.gui.mainmenu;

import com.mojang.blaze3d.textures.GpuTexture;
import imgui.ImDrawList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.anubis.client.account.Account;
import net.anubis.client.account.AccountType;
import net.anubis.client.gui.EgyptDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.client.texture.PlayerSkinCache.Entry;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
final class PlayerFaces {
   private static final float FACE_U = 0.125F;
   private static final float HAT_U = 0.625F;
   private static final float TOP_V = 0.125F;
   private static final float PART = 0.125F;
   private static final Map<UUID, Supplier<SkinTextures>> SKINS = new HashMap<>();
   private static final Set<GpuTexture> NEAREST = Collections.newSetFromMap(new WeakHashMap<>());

   private PlayerFaces() {
   }

   static void draw(ImDrawList dl, Account account, float x, float y, float size, float s, float a) {
      UUID id;
      try {
         id = UUID.fromString(account.uuid());
      } catch (IllegalArgumentException unknown) {
         placeholder(dl, x, y, size, s, a);
         return;
      }

      draw(dl, id, account.type() == AccountType.CRACKED, x, y, size, s, a);
   }

   static void draw(ImDrawList dl, UUID id, boolean offline, float x, float y, float size, float s, float a) {
      int texture = texture(skin(id, offline).body().texturePath());
      if (texture == 0) {
         placeholder(dl, x, y, size, s, a);
      } else {
         float r = Math.max(1.0F, Math.round(3.0F * s));
         int tint = EgyptDraw.col(-1, a);
         dl.addImageRounded(texture, x, y, x + size, y + size, 0.125F, 0.125F, 0.25F, 0.25F, tint, r, 240);
         dl.addImageRounded(texture, x, y, x + size, y + size, 0.625F, 0.125F, 0.75F, 0.25F, tint, r, 240);
      }
   }

   private static void placeholder(ImDrawList dl, float x, float y, float size, float s, float a) {
      dl.addRectFilled(x, y, x + size, y + size, EgyptDraw.col(-7372954, 0.3F * a), Math.round(3.0F * s));
   }

   private static SkinTextures skin(UUID id, boolean offline) {
      return offline ? DefaultSkinHelper.getSkinTextures(id) : SKINS.computeIfAbsent(id, key -> {
         Supplier<Entry> lookup = MinecraftClient.getInstance().getPlayerSkinCache().getSupplier(ProfileComponent.ofDynamic(key));
         return () -> lookup.get().getTextures();
      }).get();
   }

   private static int texture(Identifier path) {
      GpuTexture texture;
      try {
         texture = MinecraftClient.getInstance().getTextureManager().getTexture(path).getGlTexture();
      } catch (IllegalStateException notUploaded) {
         return 0;
      }

      if (texture instanceof GlTexture gl && !gl.isClosed()) {
         if (NEAREST.add(texture)) {
            int bound = GL11.glGetInteger(32873);
            GL11.glBindTexture(3553, gl.getGlId());
            GL11.glTexParameteri(3553, 10241, 9728);
            GL11.glTexParameteri(3553, 10240, 9728);
            GL11.glBindTexture(3553, bound);
         }

         return gl.getGlId();
      } else {
         return 0;
      }
   }
}
