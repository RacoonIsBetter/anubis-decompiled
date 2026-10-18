package net.anubis.client.util.playerbypass;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.function.LongPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class VisibleChunkCache {
   private final int radius;
   private final LongOpenHashSet bases = new LongOpenHashSet();
   private final LongOpenHashSet suspects = new LongOpenHashSet();
   private final LongOpenHashSet nextBases = new LongOpenHashSet();
   private final LongOpenHashSet nextSuspects = new LongOpenHashSet();
   private boolean valid;
   private long revision;
   private int centerX;
   private int centerZ;

   public VisibleChunkCache(int radius) {
      if (radius >= 0 && radius <= 512) {
         this.radius = radius;
      } else {
         throw new IllegalArgumentException("Invalid chunk radius");
      }
   }

   public boolean update(
      long revision, int centerX, int centerZ, Long2ObjectMap<ChunkLightSnapshot> snapshots, VisibleChunkCache.Flagged flagged, LongPredicate suspect
   ) {
      if (this.valid && this.revision == revision && this.centerX == centerX && this.centerZ == centerZ) {
         return false;
      }

      this.nextBases.clear();
      this.nextSuspects.clear();
      int side = this.radius * 2 + 1;
      if (snapshots.size() <= side * side) {
         ObjectIterator changed = snapshots.long2ObjectEntrySet().iterator();

         while (changed.hasNext()) {
            Entry<ChunkLightSnapshot> entry = (Entry<ChunkLightSnapshot>)changed.next();
            long key = entry.getLongKey();
            if (Math.abs((long)((int)key) - centerX) <= this.radius && Math.abs((long)((int)(key >>> 32)) - centerZ) <= this.radius) {
               this.accept(key, (ChunkLightSnapshot)entry.getValue(), flagged, suspect);
            }
         }
      } else {
         for (int dx = -this.radius; dx <= this.radius; dx++) {
            for (int dz = -this.radius; dz <= this.radius; dz++) {
               long key = centerX + dx & 4294967295L | (long)(centerZ + dz) << 32;
               ChunkLightSnapshot snapshot = (ChunkLightSnapshot)snapshots.get(key);
               if (snapshot != null) {
                  this.accept(key, snapshot, flagged, suspect);
               }
            }
         }
      }

      boolean changed = !this.valid || !this.bases.equals(this.nextBases) || !this.suspects.equals(this.nextSuspects);
      if (changed) {
         this.bases.clear();
         this.bases.addAll(this.nextBases);
         this.suspects.clear();
         this.suspects.addAll(this.nextSuspects);
      }

      this.revision = revision;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.valid = true;
      return changed;
   }

   private void accept(long key, ChunkLightSnapshot snapshot, VisibleChunkCache.Flagged flagged, LongPredicate suspect) {
      if (flagged.test(key, snapshot)) {
         (suspect.test(key) ? this.nextSuspects : this.nextBases).add(key);
      }
   }

   public LongOpenHashSet bases() {
      return this.bases;
   }

   public LongOpenHashSet suspects() {
      return this.suspects;
   }

   public void invalidate() {
      this.valid = false;
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Flagged {
      boolean test(long var1, ChunkLightSnapshot var3);
   }
}
