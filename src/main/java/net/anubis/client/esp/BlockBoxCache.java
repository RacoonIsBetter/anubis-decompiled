package net.anubis.client.esp;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.function.LongFunction;
import net.anubis.client.util.BlockBoxHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

@Environment(EnvType.CLIENT)
public final class BlockBoxCache {
   private final int capacity;
   private final LongFunction<Box> factory;
   private final Long2ObjectLinkedOpenHashMap<BlockBoxCache.Entry> boxes = new Long2ObjectLinkedOpenHashMap();
   private float size = Float.NaN;
   private long frame;

   BlockBoxCache(int capacity) {
      this(capacity, null);
   }

   public BlockBoxCache(int capacity, LongFunction<Box> factory) {
      this.capacity = Math.max(1, capacity);
      this.factory = factory;
   }

   public void beginFrame(float size) {
      if (Float.floatToIntBits(this.size) != Float.floatToIntBits(size)) {
         this.boxes.clear();
         this.size = size;
      }

      this.frame++;
   }

   public Box box(long packed) {
      BlockBoxCache.Entry hit = (BlockBoxCache.Entry)this.boxes.getAndMoveToLast(packed);
      if (hit != null) {
         hit.frame = this.frame;
         return hit.box;
      }

      Box made = this.factory == null ? BlockBoxHelper.scaledBlockBox(BlockPos.fromLong(packed), this.size) : this.factory.apply(packed);
      if (this.boxes.size() == this.capacity) {
         if (((BlockBoxCache.Entry)this.boxes.get(this.boxes.firstLongKey())).frame == this.frame) {
            return made;
         }

         this.boxes.removeFirst();
      }

      this.boxes.put(packed, new BlockBoxCache.Entry(made, this.frame));
      return made;
   }

   int size() {
      return this.boxes.size();
   }

   @Environment(EnvType.CLIENT)
   private static final class Entry {
      final Box box;
      long frame;

      Entry(Box box, long frame) {
         this.box = box;
         this.frame = frame;
      }
   }
}
