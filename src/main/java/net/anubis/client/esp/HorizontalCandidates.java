package net.anubis.client.esp;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HorizontalCandidates<T> {
   private final ToDoubleFunction<T> x;
   private final ToDoubleFunction<T> z;
   private final Long2ObjectOpenHashMap<IntArrayList> regions = new Long2ObjectOpenHashMap();
   private final IntArrayList selected = new IntArrayList();
   private final ArrayList<T> candidates = new ArrayList<>();
   private List<T> indexed;
   private int cameraX;
   private int cameraZ;
   private double range;
   private boolean ready;

   public HorizontalCandidates(ToDoubleFunction<T> x, ToDoubleFunction<T> z) {
      this.x = x;
      this.z = z;
   }

   public List<T> select(List<T> source, double eyeX, double eyeZ, double radius) {
      if (source.size() >= 64 && Double.isFinite(eyeX) && Double.isFinite(eyeZ) && Double.isFinite(radius) && !(radius < 0.0) && !(radius > 1000000.0)) {
         if (this.indexed != source) {
            if (this.indexed != null && source.size() < this.indexed.size() / 4) {
               this.clear();
            }

            this.regions.clear();

            for (int i = 0; i < source.size(); i++) {
               T point = source.get(i);
               ((IntArrayList)this.regions
                     .computeIfAbsent(key(region(this.x.applyAsDouble(point)), region(this.z.applyAsDouble(point))), ignored -> new IntArrayList()))
                  .add(i);
            }

            this.indexed = source;
            this.ready = false;
         }

         int cx = (int)Math.floor(eyeX / 16.0);
         int cz = (int)Math.floor(eyeZ / 16.0);
         if (this.ready && this.cameraX == cx && this.cameraZ == cz && this.range == radius) {
            return this.candidates;
         }

         this.selected.clear();
         this.candidates.clear();
         int minX = region(cx * 16.0 - radius);
         int maxX = region((cx + 1.0) * 16.0 + radius);
         int minZ = region(cz * 16.0 - radius);
         int maxZ = region((cz + 1.0) * 16.0 + radius);

         for (int rx = minX; rx <= maxX; rx++) {
            for (int rz = minZ; rz <= maxZ; rz++) {
               IntArrayList group = (IntArrayList)this.regions.get(key(rx, rz));
               if (group != null) {
                  this.selected.addAll(group);
               }
            }
         }

         IntArrays.quickSort(this.selected.elements(), 0, this.selected.size());

         for (int i = 0; i < this.selected.size(); i++) {
            this.candidates.add(source.get(this.selected.getInt(i)));
         }

         this.cameraX = cx;
         this.cameraZ = cz;
         this.range = radius;
         this.ready = true;
         return this.candidates;
      } else {
         if (this.indexed != null) {
            this.clear();
         }

         return source;
      }
   }

   public void clear() {
      this.indexed = null;
      this.regions.clear();
      this.regions.trim();
      this.selected.clear();
      this.selected.trim();
      this.candidates.clear();
      this.candidates.trimToSize();
      this.ready = false;
   }

   private static int region(double coordinate) {
      return (int)Math.floor(coordinate / 256.0);
   }

   private static long key(int x, int z) {
      return x & 4294967295L | (long)z << 32;
   }
}
