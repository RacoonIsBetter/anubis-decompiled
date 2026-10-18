package net.anubis.client.module.impl.render;

import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class OverlaySegments {
   private List<double[]> source;
   private boolean varied;
   private int count;
   private double[] points = new double[0];
   private int[] colors = new int[0];

   void update(List<double[]> edges, boolean varies) {
      if (this.source != edges || this.varied != varies) {
         this.count = 0;

         for (double[] edge : edges) {
            this.count = this.count + subdivisions(edge, varies);
         }

         if (this.colors.length < this.count) {
            int capacity = Math.max(this.count, this.colors.length * 2);
            this.points = Arrays.copyOf(this.points, capacity * 9);
            this.colors = Arrays.copyOf(this.colors, capacity);
         }

         int offset = 0;

         for (double[] edge : edges) {
            int n = subdivisions(edge, varies);

            for (int i = 0; i < n; i++) {
               double t0 = (double)i / n;
               double t1 = (double)(i + 1) / n;
               double tm = (t0 + t1) * 0.5;

               for (int axis = 0; axis < 3; axis++) {
                  double start = edge[axis];
                  double end = edge[axis + 3];
                  this.points[offset + axis] = start + t0 * (end - start);
                  this.points[offset + axis + 3] = start + t1 * (end - start);
                  this.points[offset + axis + 6] = start + tm * (end - start);
               }

               offset += 9;
            }
         }

         this.source = edges;
         this.varied = varies;
      }
   }

   private static int subdivisions(double[] edge, boolean varies) {
      if (!varies) {
         return 1;
      }

      double x = edge[3] - edge[0];
      double y = edge[4] - edge[1];
      double z = edge[5] - edge[2];
      double length = Math.sqrt(x * x + y * y + z * z);
      return Math.max(1, Math.min(16, (int)Math.ceil(length / 0.125)));
   }

   int count() {
      return this.count;
   }

   double[] points() {
      return this.points;
   }

   int[] colors() {
      return this.colors;
   }

   void invalidate() {
      this.source = null;
   }

   void clear() {
      this.source = null;
      this.count = 0;
      this.points = new double[0];
      this.colors = new int[0];
   }
}
