package net.anubis.client.module.impl.combat.totem;

import java.util.random.RandomGenerator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class OffhandRefill {
   private static final int MAX_ATTEMPTS = 3;
   private static final long MS = 1000000L;
   private final OffhandRefill.Driver driver;
   private final RandomGenerator random;
   private OffhandRefill.Phase phase = OffhandRefill.Phase.IDLE;
   private long due;
   private int source = -1;
   private double tempo = 1.0;
   private boolean occupiedSinceSwap;
   private OffhandRefill.Status status = OffhandRefill.Status.IDLE;
   private int attempts;
   private int sentContainer = -1;
   private int seenState = -1;
   private long startedAt;
   private long deadline;
   private long retryAt;
   private long retryPause;

   public OffhandRefill(OffhandRefill.Driver driver, RandomGenerator random) {
      this.driver = driver;
      this.random = random;
   }

   public void reset() {
      this.cancelSchedule();
      this.resetConfirmation();
      this.occupiedSinceSwap = false;
   }

   public boolean busy() {
      return this.phase != OffhandRefill.Phase.IDLE || this.status == OffhandRefill.Status.WAITING;
   }

   public void step(long now, int setting) {
      if (!this.driver.canInteract()) {
         this.reset();
      } else {
         this.expire(now);
         if (!this.driver.needsTotem()) {
            this.occupiedSinceSwap = true;
            this.cancelSchedule();
         } else {
            if (this.occupiedSinceSwap) {
               if (this.status != OffhandRefill.Status.EMPTY && this.status != OffhandRefill.Status.GAVE_UP) {
                  this.resetConfirmation();
               }

               this.occupiedSinceSwap = false;
            }

            if (!this.canAttempt(now)) {
               this.cancelSchedule();
            } else {
               int speed = Math.max(1, Math.min(10, setting));
               if (speed == 1) {
                  int instant = this.driver.findSource();
                  if (instant >= 0 && this.driver.validSource(instant)) {
                     this.equip(now, instant);
                  }
               } else if (now >= this.due) {
                  switch (this.phase) {
                     case IDLE:
                        if (this.driver.findSource() < 0) {
                           return;
                        }

                        this.tempo = 0.94 + this.random.nextDouble() * 0.12;
                        this.phase = OffhandRefill.Phase.NOTICE;
                        this.due = now + this.sample(10 + (speed - 1) * 8, 20 + (speed - 1) * 13);
                        break;
                     case NOTICE:
                        this.source = this.driver.findSource();
                        if (this.source < 0) {
                           this.cancelSchedule();
                           return;
                        }

                        this.phase = OffhandRefill.Phase.TARGET;
                        this.due = now + this.sample(6.0 + (speed - 1) * 2.5, 12.0 + (speed - 1) * 4.5);
                        break;
                     case TARGET:
                        if (!this.driver.validSource(this.source)) {
                           this.phase = OffhandRefill.Phase.NOTICE;
                           this.due = now + this.sample(6.0, 14.0);
                           return;
                        }

                        this.equip(now, this.source);
                  }
               }
            }
         }
      }
   }

   public void observe(int containerId, int stateId, OffhandRefill.Offhand offhand, long receivedAt) {
      if (this.attempts != 0 && receivedAt >= this.startedAt) {
         if (containerId == -1 || containerId == this.sentContainer) {
            if (stateId == -1 || stateId >= this.seenState) {
               if (this.status == OffhandRefill.Status.WAITING
                  || this.status == OffhandRefill.Status.UNCONFIRMED
                  || this.status == OffhandRefill.Status.EMPTY
                  || this.status == OffhandRefill.Status.GAVE_UP) {
                  if (stateId != -1) {
                     this.seenState = stateId;
                  }
                  this.status = switch (offhand) {
                     case EMPTY -> this.attempts >= 3 ? OffhandRefill.Status.GAVE_UP : OffhandRefill.Status.EMPTY;
                     case TOTEM -> OffhandRefill.Status.CONFIRMED;
                     case OTHER -> OffhandRefill.Status.OCCUPIED;
                  };
                  this.retryAt = receivedAt + this.retryPause;
               }
            }
         }
      }
   }

   private void equip(long now, int index) {
      if (this.driver.needsTotem() && this.driver.canInteract() && this.driver.validSource(index)) {
         this.begin(now, this.driver.containerId(), this.driver.stateId(), this.driver.latencyMillis());
         this.cancelSchedule();
         this.driver.swap(index);
         this.occupiedSinceSwap = !this.driver.needsTotem();
      } else {
         this.cancelSchedule();
      }
   }

   private long sample(double low, double high) {
      return Math.round((low + (high - low) * (this.random.nextDouble() + this.random.nextDouble()) / 2.0) * this.tempo * 1000000.0);
   }

   private void cancelSchedule() {
      this.phase = OffhandRefill.Phase.IDLE;
      this.source = -1;
      this.due = 0L;
   }

   private void resetConfirmation() {
      this.status = OffhandRefill.Status.IDLE;
      this.attempts = 0;
      this.sentContainer = -1;
      this.seenState = -1;
      this.startedAt = this.deadline = this.retryAt = this.retryPause = 0L;
   }

   private void begin(long now, int containerId, int stateId, int latencyMillis) {
      this.attempts++;
      this.sentContainer = containerId;
      this.seenState = stateId;
      this.startedAt = now;
      long latency = Math.max(0, latencyMillis);
      this.deadline = now + Math.min(5000L, Math.max(750L, latency * 3L + 250L)) * 1000000L;
      this.retryPause = Math.min(500L, Math.max(100L, latency)) * 1000000L;
      this.status = OffhandRefill.Status.WAITING;
   }

   private void expire(long now) {
      if (this.status == OffhandRefill.Status.WAITING && now >= this.deadline) {
         this.status = OffhandRefill.Status.UNCONFIRMED;
         this.retryAt = now + this.retryPause;
      }
   }

   private boolean canAttempt(long now) {
      this.expire(now);
      if (this.status == OffhandRefill.Status.IDLE) {
         return true;
      } else if (this.status != OffhandRefill.Status.EMPTY && this.status != OffhandRefill.Status.UNCONFIRMED) {
         return false;
      } else if (this.attempts >= 3) {
         this.status = OffhandRefill.Status.GAVE_UP;
         return false;
      } else {
         return now >= this.retryAt;
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Driver {
      boolean needsTotem();

      boolean canInteract();

      int findSource();

      boolean validSource(int var1);

      void swap(int var1);

      int containerId();

      int stateId();

      int latencyMillis();
   }

   @Environment(EnvType.CLIENT)
   public enum Offhand {
      EMPTY,
      TOTEM,
      OTHER;
   }

   @Environment(EnvType.CLIENT)
   private enum Phase {
      IDLE,
      NOTICE,
      TARGET;
   }

   @Environment(EnvType.CLIENT)
   private enum Status {
      IDLE,
      WAITING,
      CONFIRMED,
      EMPTY,
      OCCUPIED,
      UNCONFIRMED,
      GAVE_UP;
   }
}
