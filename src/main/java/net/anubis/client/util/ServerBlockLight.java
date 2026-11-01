package net.anubis.client.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.BitSet;
import java.util.Iterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.world.chunk.light.LightingProvider;

@Environment(EnvType.CLIENT)
public final class ServerBlockLight {
   public static final int UNKNOWN = -1;
   public static final int TOP_SECTION = 3;
   public static final int TOP_Y = 63;
   private static final byte[] DARK = new byte[0];
   private final Long2ObjectOpenHashMap<ServerBlockLight.Column> columns = new Long2ObjectOpenHashMap();

   public void onChunk(ClientWorld level, int chunkX, int chunkZ, LightData data) {
      this.columns.put(ChunkPos.toLong(chunkX, chunkZ), apply(level, null, data));
   }

   public void onLight(ClientWorld level, int chunkX, int chunkZ, LightData data) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      this.columns.put(key, apply(level, (ServerBlockLight.Column)this.columns.get(key), data));
   }

   public void onBlockChanged(BlockPos pos) {
      if (pos.getY() <= 63) {
         ServerBlockLight.Column column = (ServerBlockLight.Column)this.columns.get(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
         if (column != null) {
            if (column.touched == null) {
               column.touched = new LongOpenHashSet();
            }

            column.touched.add(pos.asLong());
         }
      }
   }

   public ServerBlockLight.Column column(ClientWorld level, int chunkX, int chunkZ) {
      long key = ChunkPos.toLong(chunkX, chunkZ);
      ServerBlockLight.Column column = (ServerBlockLight.Column)this.columns.get(key);
      if (column == null && LevelUtil.isLoaded(level, chunkX, chunkZ)) {
         column = fromClient(level, chunkX, chunkZ);
         this.columns.put(key, column);
      }

      return column;
   }

   public void forgetUnloaded(ClientWorld level) {
      this.columns.keySet().removeIf(key -> !LevelUtil.isLoaded(level, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)));
   }

   public void clear() {
      this.columns.clear();
   }

   private static ServerBlockLight.Column apply(ClientWorld level, ServerBlockLight.Column previous, LightData data) {
      LightingProvider engine = level.getLightingProvider();
      int minSection = engine.getBottomY();
      int count = engine.getHeight();
      byte[][] sections = previous != null && previous.minSection == minSection && previous.sections.length == count
         ? (byte[][])previous.sections.clone()
         : new byte[count][];
      BitSet sent = data.getInitedBlock();
      BitSet cleared = data.getUninitedBlock();
      Iterator<byte[]> updates = data.getBlockNibbles().iterator();

      for (int i = 0; i < count; i++) {
         boolean kept = minSection + i <= 3;
         if (sent.get(i)) {
            byte[] bytes = updates.hasNext() ? updates.next() : null;
            sections[i] = kept && bytes != null && bytes.length == 2048 ? (byte[])bytes.clone() : null;
         } else if (cleared.get(i)) {
            sections[i] = kept ? DARK : null;
         }
      }

      return new ServerBlockLight.Column(minSection, sections);
   }

   private static ServerBlockLight.Column fromClient(ClientWorld level, int chunkX, int chunkZ) {
      LightingProvider engine = level.getLightingProvider();
      ChunkLightingView listener = engine.get(LightType.BLOCK);
      int minSection = engine.getBottomY();
      byte[][] sections = new byte[engine.getHeight()][];

      for (int i = 0; i < sections.length && minSection + i <= 3; i++) {
         ChunkNibbleArray layer = listener.getLightSection(ChunkSectionPos.from(chunkX, minSection + i, chunkZ));
         if (layer != null) {
            sections[i] = layer.isUninitialized() ? DARK : (byte[])layer.asByteArray().clone();
         }
      }

      return new ServerBlockLight.Column(minSection, sections);
   }

   @Environment(EnvType.CLIENT)
   public static final class Column {
      private final int minSection;
      private final byte[][] sections;
      private LongOpenHashSet touched;

      private Column(int minSection, byte[][] sections) {
         this.minSection = minSection;
         this.sections = sections;
      }

      public int get(int x, int y, int z) {
         int index = (y >> 4) - this.minSection;
         if (index >= 0 && index < this.sections.length) {
            byte[] data = this.sections[index];
            if (data == null) {
               return -1;
            }

            if (this.touched != null && this.touched.contains(BlockPos.asLong(x, y, z))) {
               return -1;
            }

            if (data == ServerBlockLight.DARK) {
               return 0;
            }

            int cell = (y & 15) << 8 | (z & 15) << 4 | x & 15;
            return data[cell >> 1] >> ((cell & 1) << 2) & 15;
         } else {
            return -1;
         }
      }
   }
}
