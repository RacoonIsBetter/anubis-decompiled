package net.anubis.client.util.playerbypass2;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class SimulationSignals {
   private static final int RECENT = 6;
   private static final long SECOND = 1000000000L;
   private static final long CHUNK_SETTLE = 3000000000L;
   private static final long FRESH_ENTITY = 2000000000L;
   private static final long WANDER_MEMORY = 60000000000L;
   private static final long TELEPORT_QUIET = 2000000000L;
   private static final long LIGHTNING_LIFE = 3000000000L;
   private static final long UNDERGROUND_HOLD = 3000000000L;
   private static final long FROZEN_AFTER = 40000000000L;
   private static final int MAX_PINS = 32;
   private static final double MIN_MOVE_SQR = 1.0E-4;
   private static final double TELEPORT_JUMP_SQR = 64.0;
   private static final double SAMPLE_JUMP_SQR = 900.0;
   private static final double TRACKING_INSET = 24.0;
   private static final double TRACKED_DECAY = 0.5;
   private static final float FORGET_SCORE = 0.05F;
   private static final float WEAK_CAP = 4.0F;
   private static final float STRONG_CAP = 10.0F;
   private static final int MIN_Y = 0;
   private static final int BLOCK_SPILL = 1;
   private static final Set<Block> GROWS_FROM_AIR = Set.of(
      Blocks.SUGAR_CANE,
      Blocks.CACTUS,
      Blocks.BAMBOO,
      Blocks.BAMBOO_SAPLING,
      Blocks.MELON,
      Blocks.PUMPKIN,
      Blocks.SNOW,
      Blocks.FIRE,
      Blocks.SOUL_FIRE,
      Blocks.BROWN_MUSHROOM,
      Blocks.RED_MUSHROOM,
      Blocks.CHORUS_PLANT,
      Blocks.CHORUS_FLOWER
   );
   private static final Set<Block> GONE_TO_AIR = Set.of(Blocks.SNOW, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.TURTLE_EGG, Blocks.SNIFFER_EGG, Blocks.FROGSPAWN);
   private static boolean active;
   private static int serverSim = -1;
   private static int serverView = -1;
   private static int simOverride;
   private static int margin;
   private static boolean ignoreVisiblePlayers = true;
   private static long memoryNanos = 45000000000L;
   private static final LinkedHashSet<Object> SUBSCRIBERS = new LinkedHashSet<>();
   private static final ArrayDeque<SimulationSignals.Pin> PIN_HISTORY = new ArrayDeque<>();
   private static long pinTotal;
   private static int ticks;
   private static int session;
   private static RegistryKey<World> dimension;
   private static long quietUntil;
   private static long undergroundUntil;
   private static double lastX;
   private static double lastZ;
   private static double sampleX;
   private static double sampleZ;
   private static boolean hasLast;
   private static boolean hasSample;
   private static long passedCenter = Long.MIN_VALUE;
   private static int passedRadius = -1;
   private static final Long2ObjectOpenHashMap<SimulationSignals.ChunkSignal> CHUNKS = new Long2ObjectOpenHashMap();
   private static final Int2ObjectOpenHashMap<SimulationSignals.Share> ENTITY_SHARES = new Int2ObjectOpenHashMap();
   private static final Long2ObjectOpenHashMap<SimulationSignals.Share> BLOCK_SHARES = new Long2ObjectOpenHashMap();
   private static final Long2LongOpenHashMap CHUNK_LOADED = new Long2LongOpenHashMap();
   private static final Int2LongOpenHashMap ENTITY_ADDED = new Int2LongOpenHashMap();
   private static final Int2LongOpenHashMap ENTITY_INSIDE = new Int2LongOpenHashMap();
   private static final Map<RegistryKey<World>, LongOpenHashSet> PASSED_BY_DIMENSION = new HashMap<>();
   private static LongOpenHashSet passed = new LongOpenHashSet();
   private static final Int2DoubleOpenHashMap TRACKED_RANGE = new Int2DoubleOpenHashMap();
   private static final Int2ObjectOpenHashMap<long[]> LIGHTNING_PENDING = new Int2ObjectOpenHashMap();
   private static final Int2LongOpenHashMap LAST_ACTIVE = new Int2LongOpenHashMap();
   private static final LongOpenHashSet FROZEN = new LongOpenHashSet();
   private static final ArrayDeque<SimulationSignals.Pin> PINS = new ArrayDeque<>();

   private SimulationSignals() {
   }

   /**
    * Starts collecting on behalf of {@code owner}. Several modules can subscribe at once; signals
    * are collected while at least one is subscribed and cleared when the last one leaves.
    */
   public static void subscribe(Object owner) {
      if (SUBSCRIBERS.add(owner) && !active) {
         clear();
         active = true;
      }
   }

   public static void unsubscribe(Object owner) {
      if (SUBSCRIBERS.remove(owner) && SUBSCRIBERS.isEmpty()) {
         active = false;
         clear();
      }
   }

   public static boolean subscribed(Object owner) {
      return SUBSCRIBERS.contains(owner);
   }

   /** Advances the shared state once per client tick: only the earliest subscriber drives it. */
   public static void tick(Object owner) {
      if (!SUBSCRIBERS.isEmpty() && SUBSCRIBERS.iterator().next() == owner) {
         tick();
      }
   }

   public static void configure(int simulationOverride, int ringMargin, boolean ignorePlayers, int memorySeconds) {
      simOverride = simulationOverride;
      margin = ringMargin;
      ignoreVisiblePlayers = ignorePlayers;
      memoryNanos = Math.max(1, memorySeconds) * 1000000000L;
   }

   public static void clear() {
      resetLevel();
      PASSED_BY_DIMENSION.clear();
      passed = new LongOpenHashSet();
      if (dimension != null) {
         PASSED_BY_DIMENSION.put(dimension, passed);
      }
   }

   private static void resetLevel() {
      CHUNKS.clear();
      ENTITY_SHARES.clear();
      BLOCK_SHARES.clear();
      ENTITY_ADDED.clear();
      ENTITY_INSIDE.clear();
      TRACKED_RANGE.clear();
      LIGHTNING_PENDING.clear();
      LAST_ACTIVE.clear();
      FROZEN.clear();
      PINS.clear();
      PIN_HISTORY.clear();
      hasLast = false;
      hasSample = false;
      passedCenter = Long.MIN_VALUE;
      passedRadius = -1;
   }

   public static int session() {
      return session;
   }

   public static int serverView() {
      return serverView;
   }

   public static int simulation() {
      int sim = simOverride > 0 ? simOverride : (serverSim > 0 ? serverSim : 5);
      return Math.max(1, Math.min(sim, view() - 3 - margin));
   }

   public static int view() {
      int option = MinecraftClient.getInstance().options.getClampedViewDistance();
      return serverView > 0 ? Math.min(serverView, option) : option;
   }

   public static int ringStart() {
      return simulation() + 2 + margin;
   }

   public static Long2ObjectMap<SimulationSignals.ChunkSignal> chunks() {
      return CHUNKS;
   }

   public static LongOpenHashSet frozen() {
      return FROZEN;
   }

   public static SimulationSignals.Pin pollPin() {
      return PINS.poll();
   }

   public static void dropPins() {
      PINS.clear();
   }

   /** Count of pins ever raised; pass it back to {@link #pinsSince} to read only newer ones. */
   public static long pinTotal() {
      return pinTotal;
   }

   /** Reads pins raised after {@code seen} without taking them from {@link #pollPin()}. */
   public static void pinsSince(long seen, Consumer<SimulationSignals.Pin> sink) {
      long fresh = Math.min(pinTotal - seen, PIN_HISTORY.size());
      int skip = PIN_HISTORY.size() - (int)Math.max(0L, fresh);

      for (SimulationSignals.Pin pin : PIN_HISTORY) {
         if (skip-- <= 0) {
            sink.accept(pin);
         }
      }
   }

   public static boolean holding() {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      return now() < undergroundUntil || self != null && self.getY() < 0.0;
   }

   public static long now() {
      return System.nanoTime();
   }

   static float decay(long elapsed) {
      return (float)Math.exp(-elapsed / memoryNanos);
   }

   public static void onLogin(int simulationDistance, int chunkRadius, RegistryKey<World> level) {
      serverSim = simulationDistance;
      serverView = chunkRadius;
      session++;
      dimension = level;
      CHUNK_LOADED.clear();
      clear();
      startQuiet();
   }

   public static void onRespawn(RegistryKey<World> level) {
      startQuiet();
      if (!level.equals(dimension)) {
         dimension = level;
         CHUNK_LOADED.clear();
         resetLevel();
         passed = PASSED_BY_DIMENSION.computeIfAbsent(level, key -> new LongOpenHashSet());
      }
   }

   public static void onTeleport() {
      startQuiet();
   }

   public static void onSimulationDistance(int distance) {
      serverSim = distance;
   }

   public static void onViewDistance(int distance) {
      serverView = distance;
   }

   public static void onChunk(int x, int z) {
      if (active) {
         CHUNK_LOADED.put(ChunkPos.toLong(x, z), now());
      }
   }

   public static void onMove(EntityS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            Entity entity = packet.getEntity(level);
            if (entity != null) {
               boolean moved = packet.isPositionChanged() && (packet.getDeltaX() != 0 || packet.getDeltaY() != 0 || packet.getDeltaZ() != 0);
               if (moved || packet.hasRotation()) {
                  LAST_ACTIVE.put(entity.getId(), now());
               }

               if (moved) {
                  entitySignal(entity, SimulationSignals.Kind.MOVE, 0);
               } else if (packet.hasRotation()) {
                  entitySignal(entity, SimulationSignals.Kind.HEAD, 0);
               }
            }
         }
      }
   }

   public static void onPositionSync(EntityPositionSyncS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            Entity entity = level.getEntityById(packet.id());
            if (entity instanceof LivingEntity) {
               if (!(entity.getEntityPos().squaredDistanceTo(packet.values().position()) < 1.0E-4)) {
                  LAST_ACTIVE.put(entity.getId(), now());
                  entitySignal(entity, SimulationSignals.Kind.MOVE, 0);
               }
            }
         }
      }
   }

   public static void onHeadRotation(EntitySetHeadYawS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            Entity entity = packet.getEntity(level);
            if (entity != null) {
               LAST_ACTIVE.put(entity.getId(), now());
               entitySignal(entity, SimulationSignals.Kind.HEAD, 0);
            }
         }
      }
   }

   public static void onEntityData(EntityTrackerUpdateS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            if (now() - ENTITY_ADDED.getOrDefault(packet.id(), -4611686018427387904L) >= 2000000000L) {
               Entity entity = level.getEntityById(packet.id());
               if (entity != null) {
                  LAST_ACTIVE.put(entity.getId(), now());
                  entitySignal(entity, SimulationSignals.Kind.STATE, 0);
               }
            }
         }
      }
   }

   public static void onEntityEvent(EntityStatusS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            Entity entity = packet.getEntity(level);
            if (entity != null) {
               LAST_ACTIVE.put(entity.getId(), now());
               entitySignal(entity, SimulationSignals.Kind.EVENT, 0);
            }
         }
      }
   }

   public static void onEquipment(EntityEquipmentUpdateS2CPacket packet) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            if (now() - ENTITY_ADDED.getOrDefault(packet.getEntityId(), -4611686018427387904L) >= 2000000000L) {
               Entity entity = level.getEntityById(packet.getEntityId());
               if (entity != null) {
                  LAST_ACTIVE.put(entity.getId(), now());
                  entitySignal(entity, SimulationSignals.Kind.EQUIP, 0);
               }
            }
         }
      }
   }

   public static void onDamage(EntityDamageS2CPacket packet) {
      if (active) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientWorld level = mc.world;
         ClientPlayerEntity self = mc.player;
         if (level != null && self != null) {
            RegistryEntry<DamageType> type = packet.sourceType();
            if (!type.matchesKey(DamageTypes.SWEET_BERRY_BUSH) && !type.matchesKey(DamageTypes.STALAGMITE) && !type.matchesKey(DamageTypes.FALLING_STALACTITE)) {
               Entity victim = level.getEntityById(packet.entityId());
               if (victim != null) {
                  int cause = packet.sourceCauseId();
                  if (type.matchesKey(DamageTypes.PLAYER_ATTACK) && cause >= 0 && cause != self.getId() && level.getEntityById(cause) == null) {
                     pin(victim.getBlockPos(), "Hidden Attacker hit " + name(victim.getType()));
                  }

                  LAST_ACTIVE.put(victim.getId(), now());
                  entitySignal(victim, SimulationSignals.Kind.HURT, 0);
               }
            }
         }
      }
   }

   public static void onTakeItem(ItemPickupAnimationS2CPacket packet) {
      if (active) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientWorld level = mc.world;
         ClientPlayerEntity self = mc.player;
         if (level != null && self != null) {
            int collector = packet.getCollectorEntityId();
            if (collector != self.getId()) {
               Entity item = level.getEntityById(packet.getEntityId());
               if (item != null) {
                  Entity taker = level.getEntityById(collector);
                  if (taker == null) {
                     String what = item instanceof ItemEntity stack ? Registries.ITEM.getId(stack.getStack().getItem()).getPath() : name(item.getType());
                     pin(item.getBlockPos(), "Hidden Pickup " + what);
                  } else if (!(taker instanceof PlayerEntity)) {
                     LAST_ACTIVE.put(taker.getId(), now());
                     entitySignal(taker, SimulationSignals.Kind.EVENT, 0);
                  }
               }
            }
         }
      }
   }

   public static void onAddEntity(EntitySpawnS2CPacket packet) {
      if (active) {
         long now = now();
         ENTITY_ADDED.put(packet.getEntityId(), now);
         LAST_ACTIVE.put(packet.getEntityId(), now);
         EntityType<?> type = packet.getEntityType();
         if (type != EntityType.PLAYER) {
            BlockPos pos = BlockPos.ofFloored(packet.getX(), packet.getY(), packet.getZ());
            if (type == EntityType.LIGHTNING_BOLT) {
               LIGHTNING_PENDING.put(packet.getEntityId(), new long[]{pos.asLong(), now});
            } else if (spawnable(type) && !isQuiet()) {
               if (wellInsideTracking(packet.getX(), packet.getZ(), type)) {
                  if (settled(pos)) {
                     int id = packet.getEntityId();
                     signal(pos, SimulationSignals.Kind.SPAWN, spill(type), () -> entityShare(id), () -> name(type));
                  }
               }
            }
         }
      }
   }

   public static void onRemoveEntities(IntList ids) {
      if (active) {
         ClientWorld level = MinecraftClient.getInstance().world;
         if (level != null) {
            long now = now();
            boolean quiet = isQuiet();

            for (int i = 0; i < ids.size(); i++) {
               int id = ids.getInt(i);
               long[] bolt = (long[])LIGHTNING_PENDING.remove(id);
               if (bolt != null) {
                  if (!quiet && now - bolt[1] < 3000000000L) {
                     signal(BlockPos.fromLong(bolt[0]), SimulationSignals.Kind.LIGHTNING, 0, () -> entityShare(id), () -> "lightning_bolt");
                  }
               } else if (!quiet) {
                  Entity entity = level.getEntityById(id);
                  if (entity != null
                     && !(entity instanceof PlayerEntity)
                     && now - ENTITY_ADDED.getOrDefault(id, -4611686018427387904L) >= 2000000000L
                     && spawnable(entity.getType())
                     && wellInsideTracking(entity.getX(), entity.getZ(), entity.getType())
                     && settled(entity.getBlockPos())) {
                     entitySignal(entity, SimulationSignals.Kind.REMOVE, spill(entity.getType()));
                  }
               }
            }
         }
      }
   }

   public static void onBlockUpdate(BlockUpdateS2CPacket packet) {
      if (active) {
         block(packet.getPos(), packet.getState());
      }
   }

   public static void onSectionUpdate(ChunkDeltaUpdateS2CPacket packet) {
      if (active) {
         packet.visitUpdates(SimulationSignals::block);
      }
   }

   private static void block(BlockPos pos, BlockState state) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         BlockState previous = level.getBlockState(pos);
         if (previous != state) {
            if (!ignored(previous) && !ignored(state)) {
               if (settled(pos)) {
                  SimulationSignals.Kind kind = ticked(previous, state) ? SimulationSignals.Kind.TICK : SimulationSignals.Kind.BLOCK;
                  signal(pos, kind, 1, () -> blockShare(pos), () -> describe(previous) + " -> " + describe(state));
               }
            }
         }
      }
   }

   static boolean ticked(BlockState previous, BlockState state) {
      Block from = previous.getBlock();
      Block to = state.getBlock();
      if (from == to) {
         return true;
      }

      if (!previous.getFluidState().isEmpty() || !state.getFluidState().isEmpty()) {
         return true;
      }

      if (previous.isIn(BlockTags.LEAVES) && state.isAir()) {
         return true;
      }

      if (previous.isAir() && state.isIn(BlockTags.LEAVES) && !state.get(Properties.PERSISTENT, true)) {
         return true;
      }

      if (!previous.isIn(BlockTags.SAPLINGS) || !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES)) {
         if (previous.isIn(BlockTags.CROPS) && state.isAir()) {
            return true;
         }

         if (from != Blocks.DIRT || to != Blocks.GRASS_BLOCK && to != Blocks.MYCELIUM) {
            if (from == Blocks.FARMLAND && to == Blocks.DIRT) {
               return true;
            } else if (from == Blocks.MELON_STEM && to == Blocks.ATTACHED_MELON_STEM) {
               return true;
            } else if (from == Blocks.PUMPKIN_STEM && to == Blocks.ATTACHED_PUMPKIN_STEM) {
               return true;
            } else if (previous.isAir() && GROWS_FROM_AIR.contains(to)) {
               return true;
            } else {
               return state.isAir() && GONE_TO_AIR.contains(from) ? true : Oxidizable.getIncreasedOxidationBlock(from).map(next -> next == to).orElse(false);
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private static boolean ignored(BlockState state) {
      return state.isOf(Blocks.POINTED_DRIPSTONE)
         || state.isOf(Blocks.CAVE_VINES)
         || state.isOf(Blocks.CAVE_VINES_PLANT)
         || state.isOf(Blocks.SWEET_BERRY_BUSH)
         || state.isOf(Blocks.BEEHIVE)
         || state.isOf(Blocks.BEE_NEST)
         || state.isOf(Blocks.VINE)
         || state.isOf(Blocks.WEEPING_VINES)
         || state.isOf(Blocks.WEEPING_VINES_PLANT)
         || state.isOf(Blocks.TWISTING_VINES)
         || state.isOf(Blocks.TWISTING_VINES_PLANT);
   }

   public static void tick() {
      if (active) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity self = mc.player;
         if (mc.world != null && self != null) {
            long now = now();
            if (self.getY() < 0.0) {
               undergroundUntil = now + 3000000000L;
            }

            double x = self.getX();
            double z = self.getZ();
            if (hasLast && square(x - lastX, z - lastZ) > 64.0) {
               startQuiet();
            }

            lastX = x;
            lastZ = z;
            hasLast = true;
            refreshPassed(self);
            if (++ticks % 10 == 0) {
               int pcx = self.getBlockPos().getX() >> 4;
               int pcz = self.getBlockPos().getZ() >> 4;
               int start = ringStart();
               boolean sample = !isQuiet() && (!hasSample || square(x - sampleX, z - sampleZ) <= 900.0);
               sampleX = x;
               sampleZ = z;
               hasSample = true;
               Int2DoubleOpenHashMap reach = new Int2DoubleOpenHashMap();
               int view = view();
               FROZEN.clear();

               for (Entity entity : mc.world.getEntities()) {
                  if (!(entity instanceof PlayerEntity)) {
                     long seen = LAST_ACTIVE.get(entity.getId());
                     if (seen == 0L) {
                        LAST_ACTIVE.put(entity.getId(), now);
                     } else if (now - seen > 40000000000L && witness(entity)) {
                        int ex = entity.getBlockPos().getX() >> 4;
                        int ez = entity.getBlockPos().getZ() >> 4;
                        int d = distance(ex, ez, pcx, pcz);
                        if (d >= start && d <= view && !nearVisiblePlayer(ex, ez, self, 0)) {
                           FROZEN.add(ChunkPos.toLong(ex, ez));
                        }
                     }

                     if (sample && spawnable(entity.getType()) && !entity.hasPassengers() && !entity.hasVehicle()) {
                        int key = entity.getType().getMaxTrackDistance();
                        double distance = Math.hypot(entity.getX() - x, entity.getZ() - z);
                        if (distance > reach.get(key)) {
                           reach.put(key, distance);
                        }
                     }

                     if (distance(entity.getBlockPos().getX() >> 4, entity.getBlockPos().getZ() >> 4, pcx, pcz) < start) {
                        ENTITY_INSIDE.put(entity.getId(), now);
                     }
                  }
               }

               if (sample) {
                  for (int key : TRACKED_RANGE.keySet().toIntArray()) {
                     TRACKED_RANGE.put(key, Math.max(reach.get(key), TRACKED_RANGE.get(key) - 0.5));
                  }

                  ObjectIterator var22 = reach.int2DoubleEntrySet().iterator();

                  while (var22.hasNext()) {
                     it.unimi.dsi.fastutil.ints.Int2DoubleMap.Entry entry = (it.unimi.dsi.fastutil.ints.Int2DoubleMap.Entry)var22.next();
                     if (entry.getDoubleValue() > TRACKED_RANGE.get(entry.getIntKey())) {
                        TRACKED_RANGE.put(entry.getIntKey(), entry.getDoubleValue());
                     }
                  }
               }

               if (ticks % 200 == 0) {
                  Iterator<Entry<SimulationSignals.ChunkSignal>> chunks = CHUNKS.long2ObjectEntrySet().iterator();

                  while (chunks.hasNext()) {
                     if (((SimulationSignals.ChunkSignal)chunks.next().getValue()).score(now) < 0.05F) {
                        chunks.remove();
                     }
                  }

                  ENTITY_SHARES.values().removeIf(share -> share.amount(now) < 0.05F);
                  BLOCK_SHARES.values().removeIf(share -> share.amount(now) < 0.05F);
                  int keep = view() + 4;
                  CHUNK_LOADED.long2LongEntrySet()
                     .removeIf(e -> distance(ChunkPos.getPackedX(e.getLongKey()), ChunkPos.getPackedZ(e.getLongKey()), pcx, pcz) > keep);
                  ENTITY_ADDED.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > 60000000000L);
                  ENTITY_INSIDE.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > 60000000000L);
                  LIGHTNING_PENDING.values().removeIf(bolt -> now - bolt[1] > 3000000000L);
                  LAST_ACTIVE.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
               }
            }
         }
      }
   }

   private static boolean witness(Entity entity) {
      return entity instanceof PathAwareEntity mob
         && !(mob instanceof ShulkerEntity)
         && !mob.isAiDisabled()
         && !mob.hasVehicle()
         && !mob.hasPassengers()
         && !mob.isSleeping();
   }

   private static void pin(BlockPos pos, String detail) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self != null && !holding() && pos.getY() >= 0) {
         if (PINS.size() >= 32) {
            PINS.poll();
         }

         SimulationSignals.Pin made = new SimulationSignals.Pin(pos.toImmutable(), detail + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
         PINS.add(made);
         if (PIN_HISTORY.size() >= 32) {
            PIN_HISTORY.poll();
         }

         PIN_HISTORY.add(made);
         pinTotal++;
      }
   }

   private static void refreshPassed(ClientPlayerEntity self) {
      int pcx = self.getBlockPos().getX() >> 4;
      int pcz = self.getBlockPos().getZ() >> 4;
      long center = ChunkPos.toLong(pcx, pcz);
      int radius = ringStart() - 1 + 1;
      if (center != passedCenter || radius != passedRadius) {
         passedCenter = center;
         passedRadius = radius;

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               long key = ChunkPos.toLong(pcx + dx, pcz + dz);
               if (passed.add(key)) {
                  CHUNKS.remove(key);
               }
            }
         }
      }
   }

   private static void entitySignal(Entity entity, SimulationSignals.Kind kind, int spill) {
      if (!(entity instanceof PlayerEntity)) {
         if (!(entity.getFirstPassenger() instanceof PlayerEntity)) {
            if (!(entity instanceof FallingBlockEntity falling && falling.getBlockState().isOf(Blocks.POINTED_DRIPSTONE))) {
               ClientPlayerEntity self = MinecraftClient.getInstance().player;
               if (self != null) {
                  if (entity instanceof Tameable ownable) {
                     LazyEntityReference<?> owner = ownable.getOwnerReference();
                     if (owner != null && self.getUuid().equals(owner.getUuid())) {
                        return;
                     }
                  }

                  if (now() - ENTITY_INSIDE.getOrDefault(entity.getId(), -4611686018427387904L) >= 60000000000L) {
                     signal(entity.getBlockPos(), kind, spill, () -> entityShare(entity.getId()), () -> name(entity.getType()));
                  }
               }
            }
         }
      }
   }

   private static SimulationSignals.Share entityShare(int id) {
      return (SimulationSignals.Share)ENTITY_SHARES.computeIfAbsent(id, key -> new SimulationSignals.Share());
   }

   private static SimulationSignals.Share blockShare(BlockPos pos) {
      return (SimulationSignals.Share)BLOCK_SHARES.computeIfAbsent(pos.asLong(), key -> new SimulationSignals.Share());
   }

   private static String name(EntityType<?> type) {
      return Registries.ENTITY_TYPE.getId(type).getPath();
   }

   private static String describe(BlockState state) {
      return state.toString().replace("Block{minecraft:", "").replace("}", "");
   }

   private static void signal(BlockPos pos, SimulationSignals.Kind kind, int spill, Supplier<SimulationSignals.Share> source, Supplier<String> what) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self != null) {
         if (pos.getY() >= 0 && !holding()) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            int pcx = self.getBlockPos().getX() >> 4;
            int pcz = self.getBlockPos().getZ() >> 4;
            int d = distance(cx, cz, pcx, pcz);
            if (d >= ringStart() + spill && d <= view()) {
               long key = ChunkPos.toLong(cx, cz);
               if (!passed.contains(key)) {
                  if (!ignoreVisiblePlayers || !nearVisiblePlayer(cx, cz, self, spill)) {
                     long now = now();
                     float weight = source.get().take(kind.weight, kind.cap, now);
                     if (!(weight <= 0.0F)) {
                        String detail = kind.title + " " + what.get() + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                        ((SimulationSignals.ChunkSignal)CHUNKS.computeIfAbsent(key, k -> new SimulationSignals.ChunkSignal())).add(kind, now, weight, detail);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean nearVisiblePlayer(int cx, int cz, ClientPlayerEntity self, int spill) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return false;
      }

      int reach = simulation() + 1 + spill;

      for (PlayerEntity player : level.getPlayers()) {
         if (player != self && distance(cx, cz, player.getBlockPos().getX() >> 4, player.getBlockPos().getZ() >> 4) <= reach) {
            return true;
         }
      }

      return false;
   }

   private static boolean settled(BlockPos pos) {
      long loaded = CHUNK_LOADED.getOrDefault(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4), Long.MIN_VALUE);
      return loaded == Long.MIN_VALUE || now() - loaded > 3000000000L;
   }

   private static boolean wellInsideTracking(double x, double z, EntityType<?> type) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self == null) {
         return false;
      }

      double range = Math.min(TRACKED_RANGE.get(type.getMaxTrackDistance()), view() * 16.0);
      return Math.hypot(x - self.getX(), z - self.getZ()) < range - 24.0;
   }

   private static boolean spawnable(EntityType<?> type) {
      return type.getSpawnGroup() == SpawnGroup.MONSTER || type == EntityType.ITEM || type == EntityType.EXPERIENCE_ORB;
   }

   private static int spill(EntityType<?> type) {
      return type != EntityType.ITEM && type != EntityType.EXPERIENCE_ORB ? 0 : 1;
   }

   private static void startQuiet() {
      quietUntil = now() + 2000000000L;
   }

   private static boolean isQuiet() {
      return now() < quietUntil;
   }

   private static double square(double dx, double dz) {
      return dx * dx + dz * dz;
   }

   public static int distance(int ax, int az, int bx, int bz) {
      return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
   }

   @Environment(EnvType.CLIENT)
   public static final class ChunkSignal {
      private float score;
      private long updated;
      private int kinds;
      private final int[] counts = new int[SimulationSignals.Kind.values().length];
      private final ArrayDeque<String> recent = new ArrayDeque<>();

      public float score(long now) {
         return this.score * SimulationSignals.decay(now - this.updated);
      }

      public boolean has(SimulationSignals.Kind kind) {
         return (this.kinds & 1 << kind.ordinal()) != 0;
      }

      public int count(SimulationSignals.Kind kind) {
         return this.counts[kind.ordinal()];
      }

      public List<String> recent() {
         return List.copyOf(this.recent);
      }

      void add(SimulationSignals.Kind kind, long now, float weight, String detail) {
         this.recent.addLast(detail);
         if (this.recent.size() > 6) {
            this.recent.removeFirst();
         }

         this.score = this.score(now) + weight;
         this.updated = now;
         this.kinds = this.kinds | 1 << kind.ordinal();
         this.counts[kind.ordinal()]++;
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Kind {
      MOVE("Mob Move", 2.5F, true),
      HEAD("Head Turn", 2.5F, true),
      SPAWN("Spawn / Drop", 2.0F, false),
      REMOVE("Despawn / Pickup", 1.5F, false),
      STATE("Entity State", 0.5F, false),
      EVENT("Entity Event", 5.0F, true),
      BLOCK("Block Change", 1.6F, false),
      TICK("Tick Change", 10.0F, true),
      LIGHTNING("Lightning", 10.0F, true),
      HURT("Hurt", 5.0F, true),
      EQUIP("Equipment", 5.0F, true);

      public final String title;
      final float weight;
      final float cap;

      Kind(String title, float weight, boolean ticked) {
         this.title = title;
         this.weight = weight;
         this.cap = ticked ? 10.0F : 4.0F;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Pin(BlockPos pos, String detail) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Share {
      private float amount;
      private long updated;

      float amount(long now) {
         return this.amount * SimulationSignals.decay(now - this.updated);
      }

      float take(float weight, float cap, long now) {
         float held = this.amount(now);
         float granted = Math.min(weight, Math.max(cap, weight) - held);
         if (granted <= 0.0F) {
            return 0.0F;
         }

         this.amount = held + granted;
         this.updated = now;
         return granted;
      }
   }
}
