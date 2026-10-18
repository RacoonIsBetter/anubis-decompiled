package net.anubis.client.module.impl.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo.TextureAsset;

@Environment(EnvType.CLIENT)
public final class SkinChangeModule extends ClientModule {
   private final StringSetting selfPlayerSetting = this.addSetting("self-player", new StringSetting("Self Player", ""));
   private final StringSetting othersPlayerSetting = this.addSetting("others-player", new StringSetting("Others Player", ""));
   private volatile SkinChangeModule.CustomSkin selfSkin;
   private volatile SkinChangeModule.CustomSkin othersSkin;
   private String loadedSelfName = "";
   private String loadedOthersName = "";
   private boolean selfLoading;
   private boolean othersLoading;
   private final SkinChangeModule.SwapCache selfSwaps = new SkinChangeModule.SwapCache();
   private final SkinChangeModule.SwapCache othersSwaps = new SkinChangeModule.SwapCache();

   public SkinChangeModule() {
      super("skin-change", "Skin Change", "Wear any player's skin client-side.", ModuleCategory.MISC);
   }

   @Override
   protected void onEnable() {
      this.reloadIfChanged();
      this.on(TickEvent.TYPE, e -> this.reloadIfChanged());
   }

   @Override
   protected void onDisable() {
      this.selfSkin = null;
      this.othersSkin = null;
      this.loadedSelfName = "";
      this.loadedOthersName = "";
      this.selfSwaps.clear();
      this.othersSwaps.clear();
   }

   private void reloadIfChanged() {
      String selfName = this.selfPlayerSetting.get().trim();
      String othersName = this.othersPlayerSetting.get().trim();
      if (!selfName.equals(this.loadedSelfName)) {
         this.selfSkin = null;
         if (selfName.isEmpty()) {
            this.loadedSelfName = "";
         } else if (!this.selfLoading) {
            this.loadedSelfName = selfName;
            this.selfLoading = true;
            this.loadFromUsername(selfName, true);
         }
      }

      if (!othersName.equals(this.loadedOthersName)) {
         this.othersSkin = null;
         if (othersName.isEmpty()) {
            this.loadedOthersName = "";
         } else if (!this.othersLoading) {
            this.loadedOthersName = othersName;
            this.othersLoading = true;
            this.loadFromUsername(othersName, false);
         }
      }
   }

   private void loadFromUsername(String username, boolean isSelf) {
      MinecraftClient mc = MinecraftClient.getInstance();
      CompletableFuture.runAsync(
         () -> {
            try {
               String uuid = fetchUUID(username);
               if (uuid == null) {
                  mc.execute(() -> this.finishLoad(username, isSelf, null));
                  return;
               }

               SkinChangeModule.SkinSource skin = fetchSkin(uuid);
               if (skin == null) {
                  mc.execute(() -> this.finishLoad(username, isSelf, null));
                  return;
               }

               Identifier id = isSelf ? Identifier.of("anubis", "skin/self") : Identifier.of("anubis", "skin/others");
               Path cacheDir = mc.runDirectory.toPath().resolve("cache/anubis_skins");

               try {
                  Files.createDirectories(cacheDir);
               } catch (IOException var9) {
               }

               Path cachePath = cacheDir.resolve((isSelf ? "self_" : "others_") + uuid + ".png");
               registerSkin(id, cachePath, skin.url())
                  .whenCompleteAsync(
                     (texture, error) -> this.finishLoad(username, isSelf, texture == null ? null : new SkinChangeModule.CustomSkin(texture, skin.model())), mc
                  );
            } catch (Exception e) {
               mc.execute(() -> this.finishLoad(username, isSelf, null));
            }
         }
      );
   }

   private void finishLoad(String username, boolean isSelf, SkinChangeModule.CustomSkin loaded) {
      if (isSelf) {
         this.selfLoading = false;
      } else {
         this.othersLoading = false;
      }

      if (this.isEnabled()) {
         String current = (isSelf ? this.selfPlayerSetting : this.othersPlayerSetting).get().trim();
         if (loaded != null && username.equals(current) && username.equals(isSelf ? this.loadedSelfName : this.loadedOthersName)) {
            if (isSelf) {
               this.selfSkin = loaded;
            } else {
               this.othersSkin = loaded;
            }
         }

         this.reloadIfChanged();
      }
   }

   private static CompletableFuture<TextureAsset> registerSkin(Identifier id, Path cachePath, String url) {
      MinecraftClient mc = MinecraftClient.getInstance();
      PlayerSkinTextureDownloader downloader = new PlayerSkinTextureDownloader(mc.getNetworkProxy(), mc.getTextureManager(), mc);
      return downloader.downloadAndRegisterTexture(id, cachePath, url, true);
   }

   private static String fetchUUID(String username) throws Exception {
      HttpURLConnection conn = openGet("https://api.mojang.com/users/profiles/minecraft/" + username);
      int status = conn.getResponseCode();
      if (status != 204 && status != 404) {
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               sb.append(line);
            }

            JsonObject obj = JsonParser.parseString(sb.toString()).getAsJsonObject();
            return obj.get("id").getAsString();
         }
      } else {
         return null;
      }
   }

   private static SkinChangeModule.SkinSource fetchSkin(String uuid) throws Exception {
      HttpURLConnection conn = openGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
      if (conn.getResponseCode() != 200) {
         return null;
      }

      String json;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
         StringBuilder sb = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            sb.append(line);
         }

         json = sb.toString();
      }

      JsonObject profile = JsonParser.parseString(json).getAsJsonObject();

      for (JsonElement elem : profile.getAsJsonArray("properties")) {
         JsonObject prop = elem.getAsJsonObject();
         if ("textures".equals(prop.get("name").getAsString())) {
            String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()));
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
            if (!textures.has("SKIN")) {
               return null;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            JsonObject metadata = skin.has("metadata") ? skin.getAsJsonObject("metadata") : null;
            String model = metadata != null && metadata.has("model") ? metadata.get("model").getAsString() : null;
            return new SkinChangeModule.SkinSource(skin.get("url").getAsString(), PlayerSkinType.byModelMetadata(model));
         }
      }

      return null;
   }

   private static HttpURLConnection openGet(String url) throws Exception {
      HttpURLConnection conn = (HttpURLConnection)URI.create(url).toURL().openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      conn.setRequestProperty("User-Agent", "Anubis/1.0");
      return conn;
   }

   public SkinTextures skinFor(int entityId, SkinTextures original) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return this.skinFor(mc.player != null && entityId == mc.player.getId(), original);
   }

   public SkinTextures skinFor(UUID profileId, SkinTextures original) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return this.skinFor(mc.player != null && mc.player.getUuid().equals(profileId), original);
   }

   private SkinTextures skinFor(boolean isSelf, SkinTextures original) {
      SkinChangeModule.CustomSkin custom = isSelf ? this.selfSkin : this.othersSkin;
      return custom == null ? original : (isSelf ? this.selfSwaps : this.othersSwaps).swap(original, custom);
   }

   @Environment(EnvType.CLIENT)
   private record CustomSkin(TextureAsset body, PlayerSkinType model) {
   }

   @Environment(EnvType.CLIENT)
   private record SkinSource(String url, PlayerSkinType model) {
   }

   @Environment(EnvType.CLIENT)
   private static final class SwapCache {
      private static final int MAX = 256;
      private final Map<SkinTextures, SkinTextures> swapped = new IdentityHashMap<>();
      private SkinChangeModule.CustomSkin wearing;

      SkinTextures swap(SkinTextures original, SkinChangeModule.CustomSkin custom) {
         if (custom != this.wearing || this.swapped.size() >= 256) {
            this.swapped.clear();
            this.wearing = custom;
         }

         SkinTextures skin = this.swapped.get(original);
         if (skin == null) {
            skin = new SkinTextures(custom.body(), original.cape(), original.elytra(), custom.model(), original.secure());
            this.swapped.put(original, skin);
         }

         return skin;
      }

      void clear() {
         this.swapped.clear();
         this.wearing = null;
      }
   }
}
