package net.anubis.client.easyplace.builder;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.anubis.client.easyplace.EasyPlace;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
final class ScaffoldStore {
   private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "EasyPlace scaffold list");
      thread.setDaemon(true);
      return thread;
   });
   private final Object lock = new Object();
   private int savedHash;
   private String savedWorld;

   private static Path file() {
      return MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("easyplace-builder-scaffold.json");
   }

   static String worldKey(MinecraftClient mc) {
      if (mc.world == null) {
         return null;
      }

      String place;
      if (mc.getServer() != null) {
         place = "local:" + mc.getServer().getSaveProperties().getLevelName();
      } else if (mc.getCurrentServerEntry() != null) {
         place = mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
      } else {
         place = "unknown";
      }

      return place + " " + mc.world.getRegistryKey();
   }

   Set<Long> load(String world) {
      Set<Long> out = new HashSet<>();
      synchronized (this.lock) {
         JsonObject root = read();
         JsonElement var6 = root.get(world);
         if (var6 instanceof JsonArray) {
            for (JsonElement element : (JsonArray)var6) {
               out.add(element.getAsLong());
            }
         }
      }

      this.savedWorld = world;
      this.savedHash = out.hashCode();
      return out;
   }

   void save(String world, Set<Long> scaffold) {
      if (world != null) {
         int hash = scaffold.hashCode();
         if (!world.equals(this.savedWorld) || hash != this.savedHash) {
            this.savedWorld = world;
            this.savedHash = hash;
            long[] copy = scaffold.stream().mapToLong(Long::longValue).toArray();
            WRITER.execute(() -> this.write(world, copy));
         }
      }
   }

   private void write(String world, long[] positions) {
      synchronized (this.lock) {
         JsonObject root = read();
         if (positions.length == 0) {
            root.remove(world);
         } else {
            JsonArray list = new JsonArray();

            for (long position : positions) {
               list.add(position);
            }

            root.add(world, list);
         }

         try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException e) {
            EasyPlace.LOG.warn("Could not save the builder's scaffold list", e);
         }
      }
   }

   private static JsonObject read() {
      Path file = file();
      if (!Files.isRegularFile(file)) {
         return new JsonObject();
      }

      try {
         JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
         return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
      } catch (IOException | RuntimeException e) {
         EasyPlace.LOG.warn("Could not read the builder's scaffold list", e);
         return new JsonObject();
      }
   }
}
