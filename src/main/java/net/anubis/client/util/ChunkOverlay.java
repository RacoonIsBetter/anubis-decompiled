package net.anubis.client.util;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;

@Environment(EnvType.CLIENT)
public final class ChunkOverlay {
   public static final double MARKER_Y = 63.0;
   private static final float EDGE_SHADE = 0.77F;
   private static final float LINE_WIDTH = 1.5F;
   private static final double CORNER_RADIUS = 2.0;
   private static final int ARC_SEGMENTS = 4;
   private static final float GROW_SECONDS = 0.3F;
   private static final float SHRINK_SECONDS = 0.25F;
   private static final float SETTLE_SECONDS = 0.25F;
   private static final float MAX_WAIT_SECONDS = 1.0F;
   private static final float RESET_GAP_SECONDS = 0.5F;
   private final Long2FloatOpenHashMap shown = new Long2FloatOpenHashMap();
   private final LongOpenHashSet pending = new LongOpenHashSet();
   private final LongOpenHashSet done = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongArrayList group = new LongArrayList();
   private long lastNanos;
   private long pendingSince;
   private long lastAdded;
   private static final double[] OUTLINE = new double[40];
   private static int points;

   public void draw(LongSet chunks, int fill, float partialTick) {
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      if (dt > 0.5F) {
         this.shown.clear();
         this.pending.clear();
      }

      this.collect(chunks, now);
      this.step(chunks, dt);
      if (!this.shown.isEmpty()) {
         int edge = ColorHelper.withAlpha(Math.min(255, ColorHelper.getAlpha(fill) * 3 / 2), ColorHelper.scaleRgb(fill, 0.77F));
         FoglessLines lines = FoglessLines.forFrame(partialTick);
         this.done.clear();
         LongIterator keys = this.shown.keySet().iterator();

         while (keys.hasNext()) {
            long start = keys.nextLong();
            if (this.done.add(start)) {
               this.drawGroup(lines, chunks, start, fill, edge);
            }
         }
      }
   }

   public void drawTiles(LongSet chunks, int fill, int edge, float partialTick) {
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      if (dt > 0.5F) {
         this.shown.clear();
      }

      LongIterator keys = chunks.iterator();

      while (keys.hasNext()) {
         this.shown.putIfAbsent(keys.nextLong(), 0.0F);
      }

      this.step(chunks, dt);
      if (!this.shown.isEmpty()) {
         FoglessLines lines = FoglessLines.forFrame(partialTick);
         ObjectIterator<Entry> entries = this.shown.long2FloatEntrySet().fastIterator();

         while (entries.hasNext()) {
            Entry entry = (Entry)entries.next();
            float size = easeOut(entry.getFloatValue());
            if (!(size <= 0.001F)) {
               long key = entry.getLongKey();
               int cx = ChunkPos.getPackedX(key);
               int cz = ChunkPos.getPackedZ(key);
               if (!this.settled(chunks, key)) {
                  double half = 8.0 * size;
                  double mx = (cx << 4) + 8.0;
                  double mz = (cz << 4) + 8.0;
                  tile(lines, mx - half, mz - half, mx + half, mz + half, 2.0 * size, true, true, true, true, fade(fill, size), fade(edge, size));
               } else {
                  double x0 = cx << 4;
                  double z0 = cz << 4;
                  double x1 = x0 + 16.0;
                  double z1 = z0 + 16.0;
                  boolean north = !this.settled(chunks, ChunkPos.toLong(cx, cz - 1));
                  boolean south = !this.settled(chunks, ChunkPos.toLong(cx, cz + 1));
                  boolean west = !this.settled(chunks, ChunkPos.toLong(cx - 1, cz));
                  boolean east = !this.settled(chunks, ChunkPos.toLong(cx + 1, cz));
                  tile(lines, x0, z0, x1, z1, 2.0, north, south, west, east, fill, edge);
               }
            }
         }
      }
   }

   private boolean settled(LongSet chunks, long key) {
      return this.shown.get(key) >= 1.0F && chunks.contains(key);
   }

   private void collect(LongSet chunks, long now) {
      LongIterator keys = chunks.iterator();

      while (keys.hasNext()) {
         long key = keys.nextLong();
         if (!this.shown.containsKey(key) && !this.pending.contains(key)) {
            if (this.pending.isEmpty()) {
               this.pendingSince = now;
            }

            this.pending.add(key);
            this.lastAdded = now;
         }
      }

      keys = this.pending.iterator();

      while (keys.hasNext()) {
         if (!chunks.contains(keys.nextLong())) {
            keys.remove();
         }
      }

      if (!this.pending.isEmpty()) {
         if (!((float)(now - this.lastAdded) / 1.0E9F < 0.25F) || !((float)(now - this.pendingSince) / 1.0E9F < 1.0F)) {
            keys = this.pending.iterator();

            while (keys.hasNext()) {
               this.shown.put(keys.nextLong(), 0.0F);
            }

            this.pending.clear();
         }
      }
   }

   private void step(LongSet chunks, float dt) {
      ObjectIterator<Entry> entries = this.shown.long2FloatEntrySet().fastIterator();

      while (entries.hasNext()) {
         Entry entry = (Entry)entries.next();
         if (chunks.contains(entry.getLongKey())) {
            entry.setValue(Math.min(1.0F, entry.getFloatValue() + dt / 0.3F));
         } else {
            float left = entry.getFloatValue() - dt / 0.25F;
            if (left <= 0.0F) {
               entries.remove();
            } else {
               entry.setValue(left);
            }
         }
      }
   }

   private void drawGroup(FoglessLines lines, LongSet chunks, long start, int fill, int edge) {
      float progress = this.shown.get(start);
      boolean present = chunks.contains(start);
      this.group.clear();
      this.queue.enqueue(start);
      double sumX = 0.0;
      double sumZ = 0.0;

      while (!this.queue.isEmpty()) {
         long key = this.queue.dequeueLong();
         this.group.add(key);
         int cx = ChunkPos.getPackedX(key);
         int cz = ChunkPos.getPackedZ(key);
         sumX += (cx << 4) + 8.0;
         sumZ += (cz << 4) + 8.0;
         this.visit(chunks, ChunkPos.toLong(cx, cz - 1), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx, cz + 1), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx - 1, cz), progress, present);
         this.visit(chunks, ChunkPos.toLong(cx + 1, cz), progress, present);
      }

      float size = easeOut(progress);
      if (!(size <= 0.001F)) {
         double mx = sumX / this.group.size();
         double mz = sumZ / this.group.size();
         int groupFill = size >= 1.0F ? fill : fade(fill, size);
         int groupEdge = size >= 1.0F ? edge : fade(edge, size);

         for (int i = 0; i < this.group.size(); i++) {
            long key = this.group.getLong(i);
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            double x0 = mx + ((cx << 4) - mx) * size;
            double z0 = mz + ((cz << 4) - mz) * size;
            double x1 = x0 + 16.0 * size;
            double z1 = z0 + 16.0 * size;
            boolean north = !this.joined(chunks, ChunkPos.toLong(cx, cz - 1), progress, present);
            boolean south = !this.joined(chunks, ChunkPos.toLong(cx, cz + 1), progress, present);
            boolean west = !this.joined(chunks, ChunkPos.toLong(cx - 1, cz), progress, present);
            boolean east = !this.joined(chunks, ChunkPos.toLong(cx + 1, cz), progress, present);
            tile(lines, x0, z0, x1, z1, 2.0 * size, north, south, west, east, groupFill, groupEdge);
         }
      }
   }

   private void visit(LongSet chunks, long key, float progress, boolean present) {
      if (this.joined(chunks, key, progress, present) && this.done.add(key)) {
         this.queue.enqueue(key);
      }
   }

   private boolean joined(LongSet chunks, long key, float progress, boolean present) {
      return this.shown.containsKey(key) && this.shown.get(key) == progress && chunks.contains(key) == present;
   }

   private static void tile(
      FoglessLines lines,
      double x0,
      double z0,
      double x1,
      double z1,
      double radius,
      boolean north,
      boolean south,
      boolean west,
      boolean east,
      int fill,
      int edge
   ) {
      double nw = north && west ? radius : 0.0;
      double ne = north && east ? radius : 0.0;
      double se = south && east ? radius : 0.0;
      double sw = south && west ? radius : 0.0;
      points = 0;
      corner(lines, x0 + nw, z0 + nw, nw, 180.0, edge);
      corner(lines, x1 - ne, z0 + ne, ne, 270.0, edge);
      corner(lines, x1 - se, z1 - se, se, 0.0, edge);
      corner(lines, x0 + sw, z1 - sw, sw, 90.0, edge);
      WorldQuads top = WorldQuads.layer(true);
      double y = 63.0;

      for (int i = 1; i + 1 < points; i++) {
         top.quad(
            OUTLINE[0],
            y,
            OUTLINE[1],
            OUTLINE[i * 2],
            y,
            OUTLINE[i * 2 + 1],
            OUTLINE[i * 2 + 2],
            y,
            OUTLINE[i * 2 + 3],
            OUTLINE[i * 2 + 2],
            y,
            OUTLINE[i * 2 + 3],
            fill
         );
      }

      if (north) {
         edge(lines, x0 + nw, z0, x1 - ne, z0, edge);
      }

      if (south) {
         edge(lines, x0 + sw, z1, x1 - se, z1, edge);
      }

      if (west) {
         edge(lines, x0, z0 + nw, x0, z1 - sw, edge);
      }

      if (east) {
         edge(lines, x1, z0 + ne, x1, z1 - se, edge);
      }
   }

   private static void corner(FoglessLines lines, double cx, double cz, double radius, double startDegrees, int edge) {
      if (radius <= 0.0) {
         point(cx, cz);
      } else {
         double previousX = 0.0;
         double previousZ = 0.0;

         for (int i = 0; i <= 4; i++) {
            double angle = Math.toRadians(startDegrees + 90.0 * i / 4.0);
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            point(x, z);
            if (i > 0) {
               lines.line(previousX, 63.0, previousZ, x, 63.0, z, edge, 1.5F);
            }

            previousX = x;
            previousZ = z;
         }
      }
   }

   private static void point(double x, double z) {
      OUTLINE[points * 2] = x;
      OUTLINE[points * 2 + 1] = z;
      points++;
   }

   private static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static int fade(int color, float amount) {
      return ColorHelper.withAlpha(Math.round(ColorHelper.getAlpha(color) * amount), color);
   }

   private static void edge(FoglessLines lines, double ax, double az, double bx, double bz, int color) {
      lines.line(ax, 63.0, az, bx, 63.0, bz, color, 1.5F);
   }
}
