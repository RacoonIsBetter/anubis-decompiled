package net.anubis.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

@Environment(EnvType.CLIENT)
final class GameIcons {
   private static final Map<Identifier, ThemeImages.Texture> TEXTURES = new HashMap<>();
   private static final Set<Identifier> MISSING = new HashSet<>();

   private GameIcons() {
   }

   static ThemeImages.Texture effect(RegistryEntry<StatusEffect> effect) {
      Identifier id = effect.getKey().<Identifier>map(RegistryKey::getValue).orElse(null);
      return id == null ? null : texture(Identifier.of(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png"));
   }

   static ThemeImages.Texture block(String name) {
      return texture(Identifier.ofVanilla("textures/block/" + name + ".png"));
   }

   private static ThemeImages.Texture texture(Identifier path) {
      ThemeImages.Texture texture = TEXTURES.get(path);
      if (texture == null && !MISSING.contains(path)) {
         try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(path)) {
            texture = ThemeImages.decode(in.readAllBytes(), path.toString(), true);
         } catch (IOException | RuntimeException e) {
            texture = null;
         }

         if (texture == null) {
            MISSING.add(path);
         } else {
            TEXTURES.put(path, texture);
         }

         return texture;
      } else {
         return texture;
      }
   }
}
