package net.anubis.client.util.seedscan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the world seed put each overworld ore. A background thread runs Minecraft's own world
 * generator for the seed ({@link VanillaOreGenerator}) up to FULL chunks and reads the ores out,
 * so the positions are exact, caves and neighbour-chunk features included. It needs nothing from
 * the server, so it still works where anti-xray sends the underground as plain deepslate.
 */
@Environment(EnvType.CLIENT)
public final class SeedOres implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final Map<Block, SeedOres.Ore> TEMPLATE = new IdentityHashMap<>();

   private final long seed;
   private final Thread worker;
   private final ConcurrentLinkedQueue<Long> requests = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<SeedOres.Result> results = new ConcurrentLinkedQueue<>();
   private volatile boolean closed;
   private volatile String failure;

   static {
      ore(SeedOres.Type.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
      ore(SeedOres.Type.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
      ore(SeedOres.Type.GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
      ore(SeedOres.Type.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
      ore(SeedOres.Type.LAPIS, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
      ore(SeedOres.Type.IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
      ore(SeedOres.Type.COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
      ore(SeedOres.Type.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
   }

   private static void ore(SeedOres.Type type, Block stone, Block deepslate) {
      TEMPLATE.put(stone, new SeedOres.Ore(0L, type, false));
      TEMPLATE.put(deepslate, new SeedOres.Ore(0L, type, true));
   }

   private SeedOres(long seed) {
      this.seed = seed;
      this.worker = new Thread(this::work, "Anubis Seed Ores");
      this.worker.setDaemon(true);
      this.worker.setPriority(Thread.MIN_PRIORITY + 1);
      this.worker.start();
   }

   public static SeedOres start(long seed) {
      return new SeedOres(seed);
   }

   public long seed() {
      return this.seed;
   }

   /** The hashed seed the server sent at login, when it can be read. Servers may send a fake one. */
   public static OptionalLong hashedSeed(ClientWorld level) {
      return AdvancedScanner.hashedSeed(level);
   }

   public static boolean matches(long seed, long hashedSeed) {
      return AdvancedScanner.matchesHashedSeed(seed, hashedSeed);
   }

   /** Why the generator could not be built, or null. */
   public String failure() {
      return this.failure;
   }

   /** Chunks are generated in the order they are requested: request the nearest first. */
   public void request(long chunk) {
      this.requests.add(chunk);
      LockSupport.unpark(this.worker);
   }

   public SeedOres.Result poll() {
      return this.results.poll();
   }

   @Override
   public void close() {
      this.closed = true;
      this.requests.clear();
      LockSupport.unpark(this.worker);
   }

   private void work() {
      Path scratch = FabricLoader.getInstance().getGameDir().resolve(".cache").resolve("anubis-seed-esp");
      VanillaOreGenerator generator = null;

      try {
         while (!this.closed) {
            Long next = this.requests.poll();
            if (next == null) {
               LockSupport.parkNanos(50_000_000L);
               continue;
            }

            if (generator == null) {
               generator = new VanillaOreGenerator(this.seed, scratch);
            }

            List<SeedOres.Ore> ores = new ArrayList<>();

            for (VanillaOreGenerator.Found found : generator.predict(ChunkPos.getPackedX(next), ChunkPos.getPackedZ(next), state -> TEMPLATE.containsKey(state.getBlock()))) {
               SeedOres.Ore template = TEMPLATE.get(found.state().getBlock());
               ores.add(new SeedOres.Ore(found.pos(), template.type(), template.deepslate()));
            }

            this.results.add(new SeedOres.Result(next, List.copyOf(ores)));
            if (generator.shouldRecycle()) {
               generator.close();
               generator = null;
            }
         }
      } catch (Exception | LinkageError e) {
         this.failure = e.toString();
         LOGGER.warn("[Anubis] Seed Block ESP could not generate the world for this seed", e);
      } finally {
         if (generator != null) {
            generator.close();
         }
      }
   }

   /** One generated ore block and whether it is the deepslate variant. */
   @Environment(EnvType.CLIENT)
   public record Ore(long pos, SeedOres.Type type, boolean deepslate) {
   }

   @Environment(EnvType.CLIENT)
   public record Result(long chunk, List<SeedOres.Ore> ores) {
   }

   @Environment(EnvType.CLIENT)
   public enum Type {
      DIAMOND,
      EMERALD,
      GOLD,
      REDSTONE,
      LAPIS,
      IRON,
      COPPER,
      COAL
   }
}
