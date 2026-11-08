/*
 * Ported from Donut Client (https://github.com/NotRaindoots/donut-client,
 * src/client/java/dev/donut/client/blockesp/VanillaOreGenerator.java) to Yarn names.
 *
 * MIT License
 *
 * Copyright (c) 2026 Donut Client contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.anubis.client.util.seedscan;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.impl.resource.FabricDataResourceStoreHolder;
import net.minecraft.block.BlockState;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.LifecycledResourceManager;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ApiServices;
import net.minecraft.util.SystemDetails;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.MultiValueDebugSampleLogImpl;
import net.minecraft.util.profiler.log.DebugSampleLog;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.rule.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker-owned vanilla world used only for generation: it never starts a server loop, opens a
 * listener, connects players, ticks gameplay or touches the user's saved worlds. Chunks are
 * generated to FULL, because neighbouring chunks can still place ores after FEATURES.
 */
@Environment(EnvType.CLIENT)
public final class VanillaOreGenerator implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final int RECYCLE_AFTER = 128;

   private final Thread owner = Thread.currentThread();
   private final Path scratch;
   private final ResourcePackManager packs;
   private LevelStorage.Session storage;
   private SaveLoader stem;
   private VanillaOreGenerator.PredictionServer server;
   private int requests;
   private boolean closed;

   public VanillaOreGenerator(long seed, Path scratchParent) throws Exception {
      Files.createDirectories(scratchParent);
      this.scratch = Files.createTempDirectory(scratchParent, "generation-");
      this.packs = VanillaDataPackProvider.createClientManager();

      try {
         this.packs.scanPacks();
         this.packs.setEnabledProfiles(List.of("vanilla"));
         this.stem = loadVanilla(seed, this.packs);
         this.storage = LevelStorage.create(this.scratch).createSessionWithoutSymlinkCheck("prediction");
         this.server = new VanillaOreGenerator.PredictionServer(this.storage, this.packs, this.stem, this.scratch);
         this.server.prepare();
      } catch (Exception | Error error) {
         this.close();
         throw error;
      }
   }

   private static SaveLoader loadVanilla(long seed, ResourcePackManager packs) {
      LifecycledResourceManager resources = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, packs.createResourcePacks());

      try {
         CombinedDynamicRegistries<ServerDynamicRegistryType> layers = ServerDynamicRegistryType.createCombinedDynamicRegistries();
         // The connected world has already bound the static block/item/fluid tags: reuse those
         // lookups instead of loading tags again, which would overwrite the live world's tags.
         List<RegistryWrapper.Impl<?>> staticLookups = layers.getPrecedingRegistryManagers(ServerDynamicRegistryType.WORLDGEN).stream().toList();
         DynamicRegistryManager.Immutable worldgen = RegistryLoader.loadFromResource(resources, staticLookups, RegistryLoader.DYNAMIC_REGISTRIES);
         List<RegistryWrapper.Impl<?>> lookups = Stream.concat(staticLookups.stream(), worldgen.stream()).toList();
         DynamicRegistryManager.Immutable dimensions = RegistryLoader.loadFromResource(resources, lookups, RegistryLoader.DIMENSION_REGISTRIES);
         DimensionOptionsRegistryHolder.DimensionsConfig normal = WorldPresets.createDemoOptions(RegistryWrapper.WrapperLookup.of(lookups.stream()))
            .toConfig(dimensions.getOrThrow(RegistryKeys.DIMENSION));
         CombinedDynamicRegistries<ServerDynamicRegistryType> registries = layers.with(
            ServerDynamicRegistryType.WORLDGEN, worldgen, normal.toDynamicRegistryManager()
         );
         DataConfiguration configuration = DataConfiguration.SAFE_MODE;
         GameRules rules = new GameRules(configuration.enabledFeatures());
         LevelInfo settings = new LevelInfo("Seed Block ESP prediction", GameMode.SPECTATOR, false, Difficulty.PEACEFUL, false, rules, configuration);
         LevelProperties data = new LevelProperties(settings, new GeneratorOptions(seed, true, false), LevelProperties.SpecialProperty.NONE, Lifecycle.stable());
         // Skip the spawn search and preload: only requested chunks get generated.
         data.setInitialized(true);
         DataPackContents reloadable = DataPackContents.reload(
               resources,
               registries,
               List.of(),
               configuration.enabledFeatures(),
               CommandManager.RegistrationEnvironment.INTEGRATED,
               LeveledPermissionPredicate.ALL,
               Util.getMainWorkerExecutor(),
               Runnable::run
            )
            .join();
         // Deliberately no static tag update: that would overwrite the active world's tags.
         return new SaveLoader(resources, reloadable, registries, data);
      } catch (RuntimeException | Error error) {
         resources.close();
         throw error;
      }
   }

   /** Every block in the FULL-generated chunk of {@code dimension} that {@code wanted} accepts, with its exact state. */
   public List<VanillaOreGenerator.Found> predict(RegistryKey<World> dimension, int chunkX, int chunkZ, Predicate<BlockState> wanted) {
      this.checkOwner();
      if (this.closed) {
         throw new IllegalStateException("Generator is closed");
      }

      ServerWorld world = this.server.getWorld(dimension);
      if (world == null) {
         throw new IllegalArgumentException("No generator for " + dimension.getValue());
      }

      Chunk chunk = world.getChunk(chunkX, chunkZ);
      List<VanillaOreGenerator.Found> result = find(chunk, wanted);
      // Expire request tickets so vanilla releases chunks. No mobs, blocks or players are ticked.
      if (++this.requests % 8 == 0) {
         world.getChunkManager().tick(() -> true, false);

         while (world.getChunkManager().executeQueuedTasks()) {
         }
      }

      return result;
   }

   /** Section palettes skip every section that cannot contain a wanted block. */
   private static List<VanillaOreGenerator.Found> find(Chunk chunk, Predicate<BlockState> wanted) {
      List<VanillaOreGenerator.Found> result = new ArrayList<>();
      ChunkSection[] sections = chunk.getSectionArray();
      int baseX = chunk.getPos().getStartX();
      int baseZ = chunk.getPos().getStartZ();

      for (int index = 0; index < sections.length; index++) {
         ChunkSection section = sections[index];
         if (section.isEmpty() || !section.hasAny(wanted)) {
            continue;
         }

         int bottom = chunk.sectionIndexToCoord(index) * 16;

         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (wanted.test(state)) {
                     result.add(new VanillaOreGenerator.Found(BlockPos.asLong(baseX + x, bottom + y, baseZ + z), state));
                  }
               }
            }
         }
      }

      return List.copyOf(result);
   }

   /** Large sessions are recreated to bound their working data. */
   public boolean shouldRecycle() {
      return this.requests >= RECYCLE_AFTER;
   }

   private void checkOwner() {
      if (Thread.currentThread() != this.owner) {
         throw new IllegalStateException("World generation must stay on its owning worker");
      }
   }

   @Override
   public void close() {
      this.checkOwner();
      if (this.closed) {
         return;
      }

      this.closed = true;

      try {
         if (this.server != null) {
            this.server.closePrediction();
         }
      } catch (Exception error) {
         LOGGER.warn("[Anubis] Could not close the Seed Block ESP generation world", error);
      } finally {
         if (this.stem != null) {
            this.stem.close();
         }

         if (this.storage != null) {
            try {
               this.storage.close();
            } catch (IOException error) {
               LOGGER.warn("[Anubis] Could not release Seed Block ESP storage", error);
            }
         }

         // Only the unique temporary directory this instance created.
         try (Stream<Path> paths = Files.walk(this.scratch)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(path);
            }
         } catch (IOException error) {
            LOGGER.warn("[Anubis] Could not remove Seed Block ESP scratch directory {}", this.scratch, error);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public record Found(long pos, BlockState state) {
   }

   @Environment(EnvType.CLIENT)
   private static final class PredictionServer extends MinecraftServer {
      private final DebugSampleLog samples = new MultiValueDebugSampleLogImpl(1);
      private final DataPackContents dataResources;

      PredictionServer(LevelStorage.Session storage, ResourcePackManager packs, SaveLoader stem, Path scratch) {
         super(
            Thread.currentThread(),
            storage,
            packs,
            stem,
            Proxy.NO_PROXY,
            Schemas.getFixer(),
            ApiServices.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY), scratch.toFile()),
            new VanillaOreGenerator.SilentProgress()
         );
         this.dataResources = stem.dataPackContents();
         this.setPlayerManager(new PlayerManager(this, stem.combinedDynamicRegistries(), storage.createSaveHandler(), this.getManagementListener()) {
         });
         this.getPlayerManager().setViewDistance(2);
         this.getPlayerManager().setSimulationDistance(2);
      }

      void prepare() {
         this.createWorlds();
      }

      void closePrediction() throws IOException {
         // No tick loop was started. Drain generation work and close only this world's resources.
         if (this.getNetworkIo() != null) {
            this.getNetworkIo().stop();
         }

         for (ServerWorld level : this.getWorlds()) {
            level.getChunkManager().shutdown();

            while (level.getChunkManager().executeQueuedTasks()) {
            }

            level.close();
         }
      }

      @Override
      protected boolean setupServer() {
         return false;
      }

      @Override
      public LeveledPermissionPredicate getOpPermissionLevel() {
         return LeveledPermissionPredicate.ALL;
      }

      @Override
      public PermissionPredicate getFunctionPermissions() {
         return LeveledPermissionPredicate.ALL;
      }

      @Override
      public boolean shouldBroadcastRconToOps() {
         return false;
      }

      @Override
      protected DebugSampleLog getDebugSampleLog() {
         return this.samples;
      }

      @Override
      public boolean shouldPushTickTimeLog() {
         return false;
      }

      @Override
      public SystemDetails addExtraSystemDetails(SystemDetails details) {
         return details;
      }

      @Override
      public boolean isDedicated() {
         return false;
      }

      @Override
      public int getRateLimit() {
         return 0;
      }

      @Override
      public int getMaxPlayerCount() {
         return 0;
      }

      @Override
      public boolean isUsingNativeTransport() {
         return false;
      }

      @Override
      public boolean isRemote() {
         return false;
      }

      @Override
      public boolean shouldBroadcastConsoleToOps() {
         return false;
      }

      @Override
      public boolean isHost(PlayerConfigEntry player) {
         return false;
      }

      @Override
      public <T> T getOrThrow(DataResourceStore.Key<T> key) {
         return ((FabricDataResourceStoreHolder)(Object)this.dataResources).fabric$getDataResourceStore().getOrThrow(key);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class SilentProgress implements ChunkLoadProgress {
      @Override
      public void init(ChunkLoadProgress.Stage stage, int chunks) {
      }

      @Override
      public void progress(ChunkLoadProgress.Stage stage, int completed, int total) {
      }

      @Override
      public void finish(ChunkLoadProgress.Stage stage) {
      }

      @Override
      public void initSpawnPos(RegistryKey<World> dimension, ChunkPos pos) {
      }
   }
}
