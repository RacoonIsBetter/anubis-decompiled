package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.Map;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

@Environment(EnvType.CLIENT)
public final class StorageEspModule extends ClientModule implements ChunkScanner.Consumer {
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.25F, 256.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final StorageEspModule.Type chest = new StorageEspModule.Type(
      "Chest", true, -6530304, "chests", "chest-color", "chest-tracers", "chest-tracer-color"
   );
   private final StorageEspModule.Type enderChest = new StorageEspModule.Type(
      "Ender Chest", true, -9109249, "ender-chests", "ender-chest-color", "ender-chest-tracers", "ender-chest-tracer-color"
   );
   private final StorageEspModule.Type shulker = new StorageEspModule.Type(
      "Shulker Box", true, -7995234, "shulker-boxes", "shulker-box-color", "shulker-tracers", "shulker-tracer-color"
   );
   private final StorageEspModule.Type spawner = new StorageEspModule.Type(
      "Spawner", true, -7700826, "spawners", "spawner-color", "spawner-tracers", "spawner-tracer-color"
   );
   private final StorageEspModule.Type furnace = new StorageEspModule.Type(
      "Furnace", false, -10197916, "furnaces", "furnace-color", "furnace-tracers", "furnace-tracer-color"
   );
   private final StorageEspModule.Type barrel = new StorageEspModule.Type(
      "Barrel", true, -10204366, "barrels", "barrel-color", "barrel-tracers", "barrel-tracer-color"
   );
   private final StorageEspModule.Type enchanting = new StorageEspModule.Type(
      "Enchanting Table", true, -3670016, "enchanting-tables", "enchanting-table-color", "enchanting-tracers", "enchanting-tracer-color"
   );
   private final StorageEspModule.Type hopper = new StorageEspModule.Type(
      "Hopper", false, -11513776, "hoppers", "hopper-color", "hopper-tracers", "hopper-tracer-color"
   );
   private final StorageEspModule.Type[] types = new StorageEspModule.Type[]{
      this.chest, this.enderChest, this.shulker, this.spawner, this.furnace, this.barrel, this.enchanting, this.hopper
   };
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return ((StorageEspModule.Type)kind).color.argb();
      }

      @Override
      public int tracerColor(Object kind) {
         StorageEspModule.Type t = (StorageEspModule.Type)kind;
         return t.tracers.get() ? t.tracerColor.argb() : 0;
      }
   }, () -> MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getRegistryKey());
   private final Map<RegistryKey<World>, Long2ObjectOpenHashMap<StorageEspModule.Type>> detections = new HashMap<>();
   private RegistryKey<World> shownDimension;

   public StorageEspModule() {
      super("storage-esp", "Storage ESP Bypass", "Highlights storage blocks and spawners with per-type colours.", ModuleCategory.BASE_RAID);
   }

   private StorageEspModule.Type typeOf(Block block) {
      if (block instanceof ChestBlock) {
         return this.chest;
      } else if (block instanceof EnderChestBlock) {
         return this.enderChest;
      } else if (block instanceof ShulkerBoxBlock) {
         return this.shulker;
      } else if (block instanceof SpawnerBlock) {
         return this.spawner;
      } else if (block instanceof AbstractFurnaceBlock) {
         return this.furnace;
      } else if (block instanceof BarrelBlock) {
         return this.barrel;
      } else if (block instanceof EnchantingTableBlock) {
         return this.enchanting;
      } else {
         return block instanceof HopperBlock ? this.hopper : null;
      }
   }

   private StorageEspModule.Type typeOf(BlockEntityType<?> type) {
      if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST) {
         return this.chest;
      } else if (type == BlockEntityType.ENDER_CHEST) {
         return this.enderChest;
      } else if (type == BlockEntityType.SHULKER_BOX) {
         return this.shulker;
      } else if (type == BlockEntityType.MOB_SPAWNER) {
         return this.spawner;
      } else if (type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER) {
         return this.furnace;
      } else if (type == BlockEntityType.BARREL) {
         return this.barrel;
      } else if (type == BlockEntityType.ENCHANTING_TABLE) {
         return this.enchanting;
      } else {
         return type == BlockEntityType.HOPPER ? this.hopper : null;
      }
   }

   private StorageEspModule.Type typeOf(BlockEntityType<?> type, NbtCompound tag) {
      StorageEspModule.Type known = this.typeOf(type);
      if (known == null && tag != null) {
         Identifier id = tag.getString("id").map(Identifier::tryParse).orElse(null);
         return id == null ? null : this.typeOf(Registries.BLOCK_ENTITY_TYPE.get(id));
      } else {
         return known;
      }
   }

   private void rescan() {
      if (this.isEnabled()) {
         this.layer.clear();
         ClientWorld level = MinecraftClient.getInstance().world;
         this.shownDimension = level == null ? null : level.getRegistryKey();
         Long2ObjectOpenHashMap<StorageEspModule.Type> found = this.shownDimension == null ? null : this.detections.get(this.shownDimension);
         if (found != null) {
            ObjectIterator var3 = found.long2ObjectEntrySet().iterator();

            while (var3.hasNext()) {
               Entry<StorageEspModule.Type> entry = (Entry<StorageEspModule.Type>)var3.next();
               if (((StorageEspModule.Type)entry.getValue()).enabled.get()) {
                  this.layer.put(entry.getLongKey(), entry.getValue());
               }
            }
         }
      }
   }

   private boolean anyTracer() {
      if (!this.sTracers.get()) {
         return false;
      }

      for (StorageEspModule.Type type : this.types) {
         if (type.enabled.get() && type.tracers.get()) {
            return true;
         }
      }

      return false;
   }

   @Override
   protected void onEnable() {
      this.detections.clear();
      this.layer.clear();
      ChunkScanner.register(this);
      ClientWorld level = MinecraftClient.getInstance().world;
      this.shownDimension = level == null ? null : level.getRegistryKey();
      if (level != null && MinecraftClient.getInstance().player != null) {
         ChunkPos center = MinecraftClient.getInstance().player.getChunkPos();
         int radius = MinecraftClient.getInstance().options.getClampedViewDistance() + 1;

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               WorldChunk chunk = level.getChunkManager().getWorldChunk(x, z);
               if (chunk != null) {
                  this.scanBlockEntities(chunk);
               }
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.detections.clear();
      this.layer.clear();
      this.shownDimension = null;
   }

   private void found(BlockPos pos, StorageEspModule.Type type) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (type != null && level != null) {
         long key = pos.asLong();
         StorageEspModule.Type previous = (StorageEspModule.Type)this.detections
            .computeIfAbsent(level.getRegistryKey(), k -> new Long2ObjectOpenHashMap())
            .put(key, type);
         if (previous != type) {
            if (previous != null) {
               this.layer.remove(key);
            }

            if (type.enabled.get()) {
               this.layer.put(key, type);
            }
         }
      }
   }

   private void gone(BlockPos pos) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         Long2ObjectOpenHashMap<StorageEspModule.Type> found = this.detections.get(level.getRegistryKey());
         if (found != null && found.remove(pos.asLong()) != null) {
            this.layer.remove(pos.asLong());
         }
      }
   }

   @Override
   public boolean wants(BlockState state) {
      return this.typeOf(state.getBlock()) != null;
   }

   @Override
   public void found(long pos, BlockState state) {
      this.found(BlockPos.fromLong(pos), this.typeOf(state.getBlock()));
   }

   @Override
   public void removed(long pos) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && level.getBlockState(BlockPos.fromLong(pos)).isAir()) {
         this.gone(BlockPos.fromLong(pos));
      }
   }

   @Override
   public void reset() {
      this.detections.clear();
      this.layer.clear();
   }

   private void scanBlockEntities(WorldChunk chunk) {
      for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
         this.found(blockEntity.getPos(), this.typeOf(blockEntity.getType()));
      }
   }

   private void blockChanged(BlockPos pos, BlockState state) {
      StorageEspModule.Type type = this.typeOf(state.getBlock());
      if (type != null) {
         this.found(pos, type);
      } else if (state.isAir()) {
         this.gone(pos);
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         if (level.getRegistryKey() != this.shownDimension) {
            this.rescan();
         }

         if (event.packet instanceof ChunkDataS2CPacket packet) {
            packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ()).accept((pos, type, tag) -> this.found(pos, this.typeOf(type, tag)));
            WorldChunk chunk = level.getChunkManager().getWorldChunk(packet.getChunkX(), packet.getChunkZ());
            if (chunk != null) {
               this.scanBlockEntities(chunk);
            }
         } else if (event.packet instanceof BlockUpdateS2CPacket packet) {
            this.blockChanged(packet.getPos(), packet.getState());
         } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::blockChanged);
         } else if (event.packet instanceof GameJoinS2CPacket) {
            this.detections.clear();
            this.layer.clear();
         }
      }
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof BlockEntityUpdateS2CPacket packet) {
         BlockPos var5 = packet.getPos();
         StorageEspModule.Type type = this.typeOf(packet.getBlockEntityType(), packet.getNbt());
         if (type != null) {
            MinecraftClient.getInstance().execute(() -> {
               if (this.isEnabled()) {
                  this.found(var5, type);
               }
            });
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && level.getRegistryKey() != this.shownDimension) {
         this.rescan();
      }

      this.layer.render(event, this.style);
      if (this.anyTracer()) {
         this.layer.renderTracers(this.style);
      }
   }

   @Environment(EnvType.CLIENT)
   private final class Type {
      final BooleanSetting enabled;
      final BooleanSetting tracers;
      final ColorSetting color;
      final ColorSetting tracerColor;

      Type(String label, boolean on, int argb, String idOn, String idColor, String idTracers, String idTracerColor) {
         this.enabled = StorageEspModule.this.addSetting(idOn, new BooleanSetting(label, on));
         this.color = StorageEspModule.this.addSetting(idColor, new ColorSetting(label + " Color", argb));
         this.tracers = StorageEspModule.this.addSetting(idTracers, new BooleanSetting(label + " Tracers", false));
         this.tracerColor = StorageEspModule.this.addSetting(idTracerColor, new ColorSetting(label + " Tracer Color", argb));
         this.color.visibleWhen(this.enabled::get);
         this.tracers.visibleWhen(() -> StorageEspModule.this.sTracers.get() && this.enabled.get());
         this.tracerColor.visibleWhen(() -> StorageEspModule.this.sTracers.get() && this.enabled.get() && this.tracers.get());
         this.enabled.onChange(StorageEspModule.this::rescan);
      }
   }
}
