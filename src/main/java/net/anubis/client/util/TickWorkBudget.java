package net.anubis.client.util;

import java.util.function.LongSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TickWorkBudget {
   private final LongSupplier clock;
   private long limit;
   private long used;

   public TickWorkBudget(LongSupplier clock) {
      this.clock = clock;
   }

   public void reset(long nanos) {
      this.limit = Math.max(0L, nanos);
      this.used = 0L;
   }

   public long acquire(long requestedNanos) {
      long now = this.clock.getAsLong();
      long remaining = Math.max(0L, this.limit - this.used);
      long slice = Math.min(Math.max(0L, requestedNanos), Math.min(remaining, this.limit / 3L));
      return now + slice;
   }

   public void record(long startedAt) {
      this.used = this.used + Math.max(0L, this.clock.getAsLong() - startedAt);
   }

   public long usedNanos() {
      return this.used;
   }
}
