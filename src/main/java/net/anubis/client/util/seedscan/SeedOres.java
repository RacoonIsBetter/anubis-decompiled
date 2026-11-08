package net.anubis.client.util.seedscan;

import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the world seed puts each overworld ore, worked out on a background thread with the same
 * ore simulation the Advanced Scanner uses. It needs nothing from the server, so it still works
 * where anti-xray sends the underground as plain deepslate.
 *
 * <p>Carver caves and structures are not modelled, so a few predicted ores sit in open caves;
 * the caller hides those once the real blocks are visible.
 */
@Environment(EnvType.CLIENT)
public final class SeedOres implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int TERRAIN_CACHE = 400;

   private final long seed;
   private final Thread worker;
   private final ConcurrentLinkedQueue<Long> requests = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<SeedOres.Result> results = new ConcurrentLinkedQueue<>();
   private final Long2ObjectLinkedOpenHashMap<TerrainChunk> terrains = new Long2ObjectLinkedOpenHashMap<>();
   private final Long2ObjectLinkedOpenHashMap<GateHeights> gates = new Long2ObjectLinkedOpenHashMap<>();
   private volatile boolean closed;
   private volatile String failure;
   private SeedWorldgen world;

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

   /** True or false once the server's hashed seed is known, empty when it can't be checked. */
   public static OptionalLong hashedSeed(ClientWorld level) {
      return AdvancedScanner.hashedSeed(level);
   }

   public static boolean matches(long seed, long hashedSeed) {
      return AdvancedScanner.matchesHashedSeed(seed, hashedSeed);
   }

   /** Why the worldgen could not be built, or null. */
   public String failure() {
      return this.failure;
   }

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
      LockSupport.unpark(this.worker);
   }

   private void work() {
      try {
         this.world = SeedWorldgen.create(this.seed);
      } catch (RuntimeException | LinkageError e) {
         this.failure = e.toString();
         LOGGER.warn("[Anubis] Seed Block ESP could not build the world generator", e);
         return;
      }

      while (!this.closed) {
         Long next = this.requests.poll();
         if (next == null) {
            LockSupport.parkNanos(50_000_000L);
            continue;
         }

         try {
            this.results.add(this.predict(next));
         } catch (RuntimeException e) {
            LOGGER.warn("[Anubis] Seed Block ESP failed on chunk {}", new ChunkPos(next), e);
            this.results.add(new SeedOres.Result(next, List.of()));
         }
      }
   }

   private SeedOres.Result predict(long chunk) {
      int chunkX = ChunkPos.getPackedX(chunk);
      int chunkZ = ChunkPos.getPackedZ(chunk);
      SeedOres.View view = new SeedOres.View();
      List<SeedOres.Ore> ores = new ArrayList<>();

      for (OreSpec spec : this.world.specs()) {
         SeedOres.Type type = SeedOres.Type.of(spec.id());
         if (type == null) {
            continue;
         }

         view.spec = spec;
         OreSim.Chain chain = OreSim.run(spec, chunkX, chunkZ, view);
         ObjectIterator<Entry> it = chain.writes.long2ByteEntrySet().fastIterator();

         while (it.hasNext()) {
            Entry entry = it.next();
            long pos = entry.getLongKey();
            // Ores only replace stone-like blocks; the simulation already checked the terrain.
            if (VBlock.isBase(this.terrain(BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4)
               .kind(BlockPos.unpackLongX(pos), BlockPos.unpackLongY(pos), BlockPos.unpackLongZ(pos)))) {
               ores.add(new SeedOres.Ore(pos, type, entry.getByteValue() == 1));
            }
         }
      }

      return new SeedOres.Result(chunk, List.copyOf(ores));
   }

   private TerrainChunk terrain(int chunkX, int chunkZ) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      TerrainChunk terrain = this.terrains.getAndMoveToLast(key);
      if (terrain == null) {
         terrain = this.world.terrain(chunkX, chunkZ);
         this.terrains.putAndMoveToLast(key, terrain);
         if (this.terrains.size() > TERRAIN_CACHE) {
            this.terrains.removeFirst();
         }
      }

      return terrain;
   }

   private GateHeights gate(int chunkX, int chunkZ) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      GateHeights gate = this.gates.getAndMoveToLast(key);
      if (gate == null) {
         gate = GateHeights.of(this.terrain(chunkX, chunkZ), null, 0, this.world.treeAllowance(chunkX, chunkZ));
         this.gates.putAndMoveToLast(key, gate);
         if (this.gates.size() > TERRAIN_CACHE) {
            this.gates.removeFirst();
         }
      }

      return gate;
   }

   /** The seed's terrain as the ore simulation sees it (no client data: nothing the server can hide). */
   @Environment(EnvType.CLIENT)
   private final class View implements OreSim.World {
      OreSpec spec;

      @Override
      public long seed() {
         return SeedOres.this.seed;
      }

      @Override
      public int gateLow(int x, int z) {
         return SeedOres.this.gate(x >> 4, z >> 4).low(x, z);
      }

      @Override
      public int gateHigh(int x, int z) {
         return SeedOres.this.gate(x >> 4, z >> 4).high(x, z);
      }

      @Override
      public byte base(int x, int y, int z) {
         return (byte)(y >= -64 && y <= 319 && VBlock.isBase(SeedOres.this.terrain(x >> 4, z >> 4).kind(x, y, z)) ? 1 : 0);
      }

      @Override
      public byte air(int x, int y, int z) {
         return (byte)(y < -64 || y > 319 || SeedOres.this.terrain(x >> 4, z >> 4).kind(x, y, z) == VBlock.AIR ? 1 : 0);
      }

      @Override
      public boolean inBiome(OreSpec spec, int x, int y, int z) {
         return spec.biomes() == null || spec.inBiome(SeedOres.this.world.biomeAt(x, y, z));
      }
   }

   /** One predicted ore block; {@code certain} is false where the simulation had to guess. */
   @Environment(EnvType.CLIENT)
   public record Ore(long pos, SeedOres.Type type, boolean certain) {
   }

   @Environment(EnvType.CLIENT)
   public record Result(long chunk, List<SeedOres.Ore> ores) {
   }

   @Environment(EnvType.CLIENT)
   public enum Type {
      DIAMOND("diamond"),
      EMERALD("emerald"),
      GOLD("gold"),
      REDSTONE("redstone"),
      LAPIS("lapis"),
      IRON("iron"),
      COPPER("copper"),
      COAL("coal");

      private final String key;

      Type(String key) {
         this.key = key;
      }

      /** Overworld ore features are named like {@code ore_diamond_large} or {@code ore_iron_upper}. */
      static SeedOres.Type of(String featureId) {
         String id = featureId.toLowerCase(Locale.ROOT);
         if (!id.contains("ore_")) {
            return null;
         }

         for (SeedOres.Type type : values()) {
            if (id.contains(type.key)) {
               return type;
            }
         }

         return null;
      }
   }
}
