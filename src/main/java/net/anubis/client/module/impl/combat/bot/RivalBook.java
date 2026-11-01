package net.anubis.client.module.impl.combat.bot;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class RivalBook {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Gson GSON = new Gson();
   private static final int VERSION = 1;
   private static final int MAX_FILES = 64;
   private static final long MAX_BYTES = 16384L;
   private static final float WEIGHT = 0.25F;
   static final int KNOCK_UP = 0;
   static final int CRYSTALS = 1;
   static final int OPENERS = 2;
   static final int BUCKETS = 3;
   private static final float[] PRIOR = new float[]{0.45F, 0.55F};
   private static final float FADE = 0.97F;
   private final Map<UUID, RivalBook.Rival> loaded = new HashMap<>();
   private long lastSave;

   RivalBook.Rival of(UUID id) {
      return this.loaded.computeIfAbsent(id, RivalBook::read);
   }

   void save(boolean now) {
      long time = System.currentTimeMillis();
      if (now || time - this.lastSave >= 30000L) {
         this.lastSave = time;
         List<RivalBook.Rival> changed = new ArrayList<>();

         for (RivalBook.Rival rival : this.loaded.values()) {
            if (rival.dirty) {
               rival.dirty = false;
               rival.seen = time;
               changed.add(rival.copy());
            }
         }

         if (!changed.isEmpty()) {
            Util.getIoWorkerExecutor().execute(() -> {
               for (RivalBook.Rival rivalx : changed) {
                  write(rivalx);
               }

               prune();
            });
         }
      }
   }

   private static Path folder() {
      return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("bots").resolve("rivals");
   }

   private static RivalBook.Rival read(UUID id) {
      Path file = folder().resolve(id + ".json");

      try {
         if (Files.isRegularFile(file) && Files.size(file) <= 16384L) {
            RivalBook.Rival rival = (RivalBook.Rival)GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), RivalBook.Rival.class);
            if (rival != null && rival.version == 1) {
               rival.id = id.toString();
               if (rival.openerReward == null || rival.openerReward.length != 6) {
                  rival.openerReward = new float[6];
               }

               if (rival.openerTries == null || rival.openerTries.length != 6) {
                  rival.openerTries = new float[6];
               }

               return rival;
            }
         }
      } catch (IOException | JsonSyntaxException e) {
         LOGGER.warn("[Anubis] Could not read a crystal bot rival entry, starting it over", e);
      }

      RivalBook.Rival rival = new RivalBook.Rival();
      rival.id = id.toString();
      return rival;
   }

   private static void write(RivalBook.Rival rival) {
      Path folder = folder();
      Path file = folder.resolve(UUID.fromString(rival.id) + ".json");

      try {
         Files.createDirectories(folder);
         Path tmp = folder.resolve(file.getFileName() + ".tmp");
         Files.writeString(tmp, GSON.toJson(rival), StandardCharsets.UTF_8);

         try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomicFail) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException | IllegalArgumentException e) {
         LOGGER.warn("[Anubis] Could not save a crystal bot rival entry", e);
      }
   }

   private static void prune() {
      List<Path> files = new ArrayList<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder(), "*.json")) {
         for (Path file : stream) {
            files.add(file);
         }
      } catch (IOException e) {
         return;
      }

      if (files.size() > 64) {
         files.sort((a, b) -> Long.compare(modified(b), modified(a)));

         for (Path old : files.subList(64, files.size())) {
            try {
               Files.deleteIfExists(old);
            } catch (IOException var5) {
            }
         }
      }
   }

   private static long modified(Path file) {
      try {
         return Files.getLastModifiedTime(file).toMillis();
      } catch (IOException e) {
         return 0L;
      }
   }

   private static float average(float value, int samples, float sample) {
      return samples == 0 ? sample : value + (sample - value) * 0.25F;
   }

   @Environment(EnvType.CLIENT)
   static final class Rival {
      int version = 1;
      String id;
      long seen;
      int fights;
      float launchRate = 0.5F;
      int launches;
      float airAlong;
      float airAcross;
      int flights;
      float retotemTicks;
      int retotems;
      float holeTicks;
      int holes;
      float pearlLowRate;
      int lowSpells;
      float[] openerReward = new float[6];
      float[] openerTries = new float[6];
      transient boolean dirty;

      void fought() {
         this.fights++;
         this.dirty = true;
      }

      void launched(boolean lifted) {
         this.launchRate = RivalBook.average(this.launchRate, this.launches++, lifted ? 1.0F : 0.0F);
         this.dirty = true;
      }

      void flew(double along, double across) {
         this.airAlong = RivalBook.average(this.airAlong, this.flights, (float)along);
         this.airAcross = RivalBook.average(this.airAcross, this.flights++, (float)across);
         this.dirty = true;
      }

      boolean knowsFlight() {
         return this.flights >= 2;
      }

      void retotemed(int ticks) {
         this.retotemTicks = RivalBook.average(this.retotemTicks, this.retotems++, ticks);
         this.dirty = true;
      }

      void leftHole(int ticks) {
         this.holeTicks = RivalBook.average(this.holeTicks, this.holes++, ticks);
         this.dirty = true;
      }

      void lowSpell(boolean pearled) {
         this.pearlLowRate = RivalBook.average(this.pearlLowRate, this.lowSpells++, pearled ? 1.0F : 0.0F);
         this.dirty = true;
      }

      boolean shrugsOffLaunches() {
         return this.launches >= 4 && this.launchRate < 0.35F;
      }

      int retotemWindow(int fallback) {
         return this.retotems >= 2 ? Math.round(this.retotemTicks) : fallback;
      }

      boolean campsInHoles() {
         return this.holes >= 2 && this.holeTicks >= 100.0F;
      }

      int opener(double reach) {
         int bucket = bucket(reach);
         float all = 0.0F;

         for (int arm = 0; arm < 2; arm++) {
            all += this.openerTries[arm * 3 + bucket];
         }

         int best = 0;
         double bestScore = Double.NEGATIVE_INFINITY;

         for (int arm = 0; arm < 2; arm++) {
            float tries = this.openerTries[arm * 3 + bucket];
            if (tries < 0.5F) {
               return arm;
            }

            double mean = (this.openerReward[arm * 3 + bucket] + RivalBook.PRIOR[arm]) / (tries + 1.0F);
            double score = mean + 0.5 * Math.sqrt(Math.log(all + 1.0) / tries) + ThreadLocalRandom.current().nextDouble(0.02);
            if (score > bestScore) {
               bestScore = score;
               best = arm;
            }
         }

         return best;
      }

      void opened(int arm, double reach, float reward) {
         int at = arm * 3 + bucket(reach);

         for (int i = 0; i < this.openerTries.length; i++) {
            if (i % 3 == at % 3) {
               this.openerReward[i] = this.openerReward[i] * 0.97F;
               this.openerTries[i] = this.openerTries[i] * 0.97F;
            }
         }

         this.openerReward[at] = this.openerReward[at] + reward;
         this.openerTries[at]++;
         this.dirty = true;
      }

      private static int bucket(double reach) {
         return reach <= 2.6 ? 0 : (reach <= 3.0 ? 1 : 2);
      }

      boolean pearlsWhenLow() {
         return this.lowSpells >= 2 && this.pearlLowRate >= 0.5F;
      }

      private RivalBook.Rival copy() {
         return (RivalBook.Rival)RivalBook.GSON.fromJson(RivalBook.GSON.toJson(this), RivalBook.Rival.class);
      }
   }
}
