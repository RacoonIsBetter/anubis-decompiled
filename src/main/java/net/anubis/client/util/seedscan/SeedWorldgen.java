package net.anubis.client.util.seedscan;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.chunk.PalettesFactory;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.gen.carver.ConfiguredCarvers;
import net.minecraft.structure.processor.StructureProcessorLists;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.BuiltinBiomes;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.chunk.PaletteProvider;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.world.gen.noise.BuiltinNoiseParameters;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.ConfiguredFeatures;
import net.minecraft.world.gen.feature.PlacedFeatures;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.densityfunction.DensityFunctions;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.registry.RegistryBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.registry.DynamicRegistryManager.ImmutableImpl;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures;

@Environment(EnvType.CLIENT)
public final class SeedWorldgen {
   public static final int MIN_Y = -64;
   public static final int HEIGHT = 384;
   public static final int MAX_Y = 319;
   public final long seed;
   final NoiseChunkGenerator generator;
   final NoiseConfig randomState;
   final MultiNoiseBiomeSource biomeSource;
   final ChunkGeneratorSettings settings;
   private final List<OreSpec> specs;
   private final PalettesFactory containers;
   private final StructureAccessor noStructures;
   private final HeightLimitView height = HeightLimitView.create(-64, 384);
   private final long zoomSeed;
   private final Map<Long, GenerationSettings> carverBiomes = new ConcurrentHashMap<>();
   private final Map<Long, short[]> storedBiomes = new ConcurrentHashMap<>();
   private final List<RegistryEntry<Biome>> biomeIds;
   private final Map<RegistryEntry<Biome>, Integer> biomeIndex = new IdentityHashMap<>();
   private static final int MIN_QUART = -16;
   private static final int QUARTS_Y = 96;
   private static final int MAX_STORED_BIOMES = 4096;
   private static final int MAX_CARVER_BIOMES = 65536;
   private final BiomeAccess biomeManager;
   private static final DynamicRegistryManager CARVER_REGISTRIES = new ImmutableImpl(List.<Registry<?>>of(new SimpleRegistry<>(RegistryKeys.BIOME, Lifecycle.stable()).freeze()));
   private static final Set<RegistryKey<Biome>> TALL_TREES = Set.of(
      BiomeKeys.JUNGLE, BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.MANGROVE_SWAMP
   );

   private SeedWorldgen(long seed) {
      this.seed = seed;
      WrapperLookup lookup = new RegistryBuilder()
         .addRegistry(RegistryKeys.CONFIGURED_CARVER, ConfiguredCarvers::bootstrap)
         .addRegistry(RegistryKeys.PROCESSOR_LIST, StructureProcessorLists::bootstrap)
         .addRegistry(RegistryKeys.CONFIGURED_FEATURE, ConfiguredFeatures::bootstrap)
         .addRegistry(RegistryKeys.PLACED_FEATURE, PlacedFeatures::bootstrap)
         .addRegistry(RegistryKeys.BIOME, BuiltinBiomes::bootstrap)
         .addRegistry(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterLists::bootstrap)
         .addRegistry(RegistryKeys.NOISE_PARAMETERS, BuiltinNoiseParameters::bootstrap)
         .addRegistry(RegistryKeys.DENSITY_FUNCTION, DensityFunctions::bootstrap)
         .addRegistry(RegistryKeys.CHUNK_GENERATOR_SETTINGS, ChunkGeneratorSettings::bootstrap)
         .createWrapperLookup(DynamicRegistryManager.of(Registries.REGISTRIES));
      RegistryEntry<ChunkGeneratorSettings> settingsHolder = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD);
      this.settings = (ChunkGeneratorSettings)settingsHolder.value();
      this.randomState = NoiseConfig.create(this.settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
      RegistryEntry<MultiNoiseBiomeSourceParameterList> parameters = lookup.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
      this.biomeSource = MultiNoiseBiomeSource.create(parameters);
      this.generator = new NoiseChunkGenerator(this.biomeSource, settingsHolder);
      List<RegistryEntry<Biome>> biomes = List.copyOf(this.biomeSource.getBiomes());
      List<IndexedFeatures> steps = PlacedFeatureIndexer.collectIndexedFeatures(biomes, holder -> this.generator.getGenerationSettings(holder).getFeatures(), true);
      Map<PlacedFeature, String> names = new IdentityHashMap<>();
      lookup.getOrThrow(RegistryKeys.PLACED_FEATURE)
         .streamEntries()
         .forEach(reference -> names.put((PlacedFeature)reference.value(), reference.registryKey().getValue().getPath()));
      this.specs = OreSpec.derive(steps, biomes, this.generator, placed -> names.getOrDefault(placed, "?"), -64, 319);
      RegistryEntry<Biome> plains = lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS);
      List<RegistryEntry<Biome>> ids = new ArrayList<>(biomes);
      if (!ids.contains(plains)) {
         ids.add(plains);
      }

      this.biomeIds = List.copyOf(ids);

      for (int i = 0; i < this.biomeIds.size(); i++) {
         this.biomeIndex.put(this.biomeIds.get(i), i);
      }

      this.containers = new PalettesFactory(
         PaletteProvider.forBlockStates(Block.STATE_IDS), Blocks.AIR.getDefaultState(), null, PaletteProvider.forBiomes(listIds(ids)), plains, null
      );
      this.noStructures = new StructureAccessor(emptyLevel(this.containers, this.height), new GeneratorOptions(seed, false, false), null);
      this.zoomSeed = BiomeAccess.hashSeed(seed);
      this.biomeManager = new BiomeAccess(this::storedBiome, this.zoomSeed);
   }

   public static SeedWorldgen create(long seed) {
      return new SeedWorldgen(seed);
   }

   public List<OreSpec> specs() {
      return this.specs;
   }

   public int treeAllowance(int chunkX, int chunkZ) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            int x = (chunkX + dx << 4) + 8;
            int z = (chunkZ + dz << 4) + 8;

            for (int y = 60; y <= 130; y += 10) {
               if (this.biomeAt(x, y, z).getKey().filter(TALL_TREES::contains).isPresent()) {
                  return GateHeights.TALL_TREE_ALLOWANCE;
               }
            }
         }
      }

      return GateHeights.TREE_ALLOWANCE;
   }

   public RegistryEntry<Biome> biomeAt(int x, int y, int z) {
      return this.biomeManager.getBiome(new BlockPos(x, y, z));
   }

   RegistryEntry<Biome> storedBiome(int qx, int qy, int qz) {
      int clamped = Math.max(-16, Math.min(qy, 79));
      short[] quarts = this.storedBiomes(BiomeCoords.toChunk(qx), BiomeCoords.toChunk(qz));
      return this.biomeIds.get(quarts[((clamped - -16) * 4 + (qz & 3)) * 4 + (qx & 3)]);
   }

   private short[] storedBiomes(int chunkX, int chunkZ) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      short[] quarts = this.storedBiomes.get(key);
      if (quarts != null) {
         return quarts;
      }

      ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.NO_UPGRADE_DATA, this.height, this.containers, null);
      return this.fillBiomes(chunk);
   }

   private short[] fillBiomes(ProtoChunk chunk) {
      this.generator.populateBiomes(this.randomState, Blender.getNoBlending(), this.noStructures, chunk).join();
      chunk.setStatus(ChunkStatus.BIOMES);
      int x0 = BiomeCoords.fromBlock(chunk.getPos().getStartX());
      int z0 = BiomeCoords.fromBlock(chunk.getPos().getStartZ());
      short[] quarts = new short[1536];

      for (int qy = 0; qy < 96; qy++) {
         for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
               Integer id = this.biomeIndex.get(chunk.getBiomeForNoiseGen(x0 + qx, -16 + qy, z0 + qz));
               quarts[(qy * 4 + qz) * 4 + qx] = (short)(id == null ? 0 : id);
            }
         }
      }

      if (this.storedBiomes.size() > 4096) {
         this.storedBiomes.clear();
      }

      this.storedBiomes.put(chunk.getPos().toLong(), quarts);
      return quarts;
   }

   public ProtoChunk generateTerrain(int chunkX, int chunkZ) {
      ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.NO_UPGRADE_DATA, this.height, this.containers, null);
      this.fillBiomes(chunk);
      this.generator.populateNoise(Blender.getNoBlending(), this.randomState, this.noStructures, chunk).join();
      chunk.setStatus(ChunkStatus.NOISE);
      this.generator
         .buildSurface(chunk, new HeightContext(this.generator, chunk), this.randomState, this.noStructures, this.biomeManager, null, Blender.getNoBlending());
      chunk.setStatus(ChunkStatus.SURFACE);
      this.carve(chunk);
      chunk.setStatus(ChunkStatus.CARVERS);
      return chunk;
   }

   public TerrainChunk terrain(int chunkX, int chunkZ) {
      return TerrainChunk.of(this.generateTerrain(chunkX, chunkZ));
   }

   void carve(ProtoChunk chunk) {
      if (this.carverBiomes.size() > 65536) {
         this.carverBiomes.clear();
      }

      BiomeAccess carverBiomeManager = new BiomeAccess((x, y, z) -> this.biomeSource.getBiome(x, y, z, this.randomState.getMultiNoiseSampler()), this.zoomSeed);
      ChunkRandom random = new ChunkRandom(new CheckedRandom(RandomSeed.getSeed()));
      ChunkPos pos = chunk.getPos();
      ChunkNoiseSampler noise = chunk.getOrCreateChunkNoiseSampler(c -> {
         throw new IllegalStateException("carving before the noise fill");
      });
      AquiferSampler aquifer = noise.getAquiferSampler();
      CarverContext context = new CarverContext(this.generator, CARVER_REGISTRIES, chunk.getHeightLimitView(), noise, this.randomState, this.settings.surfaceRule());
      CarvingMask mask = chunk.getOrCreateCarvingMask();

      for (int dx = -8; dx <= 8; dx++) {
         for (int dz = -8; dz <= 8; dz++) {
            ChunkPos from = new ChunkPos(pos.x + dx, pos.z + dz);
            GenerationSettings biome = this.carverBiomes
               .computeIfAbsent(
                  from.toLong(),
                  k -> this.generator
                     .getGenerationSettings(
                        this.biomeSource
                           .getBiome(
                              BiomeCoords.fromBlock(from.getStartX()), 0, BiomeCoords.fromBlock(from.getStartZ()), this.randomState.getMultiNoiseSampler()
                           )
                     )
               );
            int index = 0;

            for (RegistryEntry<ConfiguredCarver<?>> holder : biome.getCarversForStep()) {
               random.setCarverSeed(this.seed + index, from.x, from.z);
               ConfiguredCarver<?> carver = holder.value();
               if (carver.shouldCarve(random)) {
                  carver.carve(context, chunk, carverBiomeManager::getBiome, random, aquifer, from, mask);
               }

               index++;
            }
         }
      }
   }

   private static IndexedIterable<RegistryEntry<Biome>> listIds(List<RegistryEntry<Biome>> list) {
      final Map<RegistryEntry<Biome>, Integer> ids = new IdentityHashMap<>();

      for (int i = 0; i < list.size(); i++) {
         ids.put(list.get(i), i);
      }

      return new IndexedIterable<RegistryEntry<Biome>>() {
         public int getRawId(RegistryEntry<Biome> biome) {
            Integer id = ids.get(biome);
            return id == null ? -1 : id;
         }

         public RegistryEntry<Biome> get(int id) {
            return id >= 0 && id < list.size() ? list.get(id) : null;
         }

         public int size() {
            return list.size();
         }

         public Iterator<RegistryEntry<Biome>> iterator() {
            return list.iterator();
         }
      };
   }

   private static WorldAccess emptyLevel(PalettesFactory containers, HeightLimitView height) {
      ProtoChunk empty = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.NO_UPGRADE_DATA, height, containers, null);
      InvocationHandler handler = (proxy, method, args) -> {
         if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
               case "equals" -> proxy == args[0];
               case "hashCode" -> System.identityHashCode(proxy);
               default -> "SeedWorldgen.emptyLevel";
            };
         }

         if (Chunk.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 4) {
            return empty;
         }

         if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
         }

         throw new UnsupportedOperationException("structure-free level: " + method);
      };
      return (WorldAccess)Proxy.newProxyInstance(WorldAccess.class.getClassLoader(), new Class[]{WorldAccess.class}, handler);
   }
}
