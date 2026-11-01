package net.anubis.client.util.playerbypass;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AutoRtpLoop {
   public static final long MIN_DELAY_MS = 3000L;
   public static final long MIN_SCAN_MS = 2000L;
   public static final long MIN_TIMEOUT_MS = 3000L;
   public static final long COOLDOWN_MARGIN_MS = 1000L;
   public static final long MAX_COOLDOWN_MS = 900000L;
   public static final long MAX_WARMUP_MS = 60000L;
   public static final long REJECT_GRACE_MS = 2000L;
   public static final long SETTLE_MS = 1500L;
   public static final long MAX_SETTLE_MS = 8000L;
   public static final long LATE_LANDING_MS = 5000L;
   public static final int MAX_FAILS_IN_ROW = 5;
   private static final long NEVER = -2305843009213693952L;
   private AutoRtpLoop.Phase phase = AutoRtpLoop.Phase.OFF;
   private int sent;
   private int landings;
   private int failsInRow;
   private boolean rejected;
   private boolean tryLanded;
   private boolean tryRefunded;
   private long lastSendAt = -2305843009213693952L;
   private long cooldownUntil;
   private long nextSendAt;
   private long holdUntil;
   private long deadline;
   private long scanUntil;
   private long waitingSince;
   private long tryEndedAt = -2305843009213693952L;
   private long lastDataAt = -2305843009213693952L;

   public void start(long now, AutoRtpLoop.Config config) {
      this.phase = AutoRtpLoop.Phase.WAITING;
      this.sent = 0;
      this.landings = 0;
      this.failsInRow = 0;
      this.rejected = false;
      this.tryLanded = false;
      this.tryRefunded = false;
      this.nextSendAt = this.lastSendAt == -2305843009213693952L ? now : Math.max(now, this.lastSendAt + config.delayMs());
      this.holdUntil = 0L;
      this.waitingSince = now;
      this.tryEndedAt = -2305843009213693952L;
      this.lastDataAt = -2305843009213693952L;
   }

   public void stop() {
      this.phase = AutoRtpLoop.Phase.OFF;
   }

   public void forgetCooldown() {
      this.cooldownUntil = 0L;
   }

   public AutoRtpLoop.Step tick(long now, AutoRtpLoop.Config config) {
      switch (this.phase) {
         case WAITING:
            if (now < this.sendAt()) {
               return AutoRtpLoop.Step.NONE;
            } else {
               if (config.maxRtps() > 0 && this.sent >= config.maxRtps()) {
                  return AutoRtpLoop.Step.LIMIT;
               }

               this.sent++;
               this.lastSendAt = now;
               this.deadline = now + config.timeoutMs();
               this.rejected = false;
               this.tryLanded = false;
               this.tryRefunded = false;
               this.phase = AutoRtpLoop.Phase.TELEPORTING;
               return AutoRtpLoop.Step.SEND;
            }
         case TELEPORTING:
            if (now < this.deadline) {
               return AutoRtpLoop.Step.NONE;
            } else {
               if (this.rejected) {
                  this.sent = Math.max(0, this.sent - 1);
                  this.tryRefunded = true;
               } else {
                  if (this.failsInRow + 1 >= 5) {
                     this.failsInRow = 5;
                     return AutoRtpLoop.Step.GAVE_UP;
                  }

                  this.failsInRow++;
               }

               this.toWaiting(now, config);
               return AutoRtpLoop.Step.TIMED_OUT;
            }
         case SCANNING:
            if (now < this.scanUntil) {
               return AutoRtpLoop.Step.NONE;
            }

            this.toWaiting(now, config);
            return AutoRtpLoop.Step.SCAN_CLEAR;
         default:
            return AutoRtpLoop.Step.NONE;
      }
   }

   public void landed(long now, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.TELEPORTING) {
         this.phase = AutoRtpLoop.Phase.SCANNING;
         this.landings++;
         this.failsInRow = 0;
         this.rejected = false;
         this.tryLanded = true;
         this.scanUntil = now + config.scanMs();
      }
   }

   public void relanded(long now, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.SCANNING) {
         this.scanUntil = Math.max(this.scanUntil, now + config.scanMs());
      }
   }

   public boolean landedLate(long now, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.WAITING && this.tryEndedAt != -2305843009213693952L && now - this.tryEndedAt <= 5000L) {
         if (!this.tryLanded) {
            this.landings++;
            if (this.tryRefunded) {
               this.sent++;
            }
         }

         this.phase = AutoRtpLoop.Phase.SCANNING;
         this.failsInRow = 0;
         this.rejected = false;
         this.tryLanded = true;
         this.tryRefunded = false;
         this.tryEndedAt = -2305843009213693952L;
         this.scanUntil = now + config.scanMs();
         return true;
      } else {
         return false;
      }
   }

   public void cooldown(long now, long ms) {
      if (this.phase != AutoRtpLoop.Phase.OFF) {
         this.nextUseIn(now, ms);
         if (this.phase == AutoRtpLoop.Phase.TELEPORTING) {
            this.rejected = true;
            this.deadline = Math.min(this.deadline, now + 2000L);
         }
      }
   }

   public void nextUseIn(long now, long ms) {
      if (this.phase != AutoRtpLoop.Phase.OFF) {
         long wait = Math.min(900000L, Math.max(0L, ms)) + 1000L;
         this.cooldownUntil = Math.max(this.cooldownUntil, now + wait);
      }
   }

   public void warmup(long now, long ms, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.TELEPORTING && !this.rejected) {
         this.deadline = Math.max(this.deadline, now + Math.min(60000L, Math.max(0L, ms)) + config.timeoutMs());
      }
   }

   public void extend(long now, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.TELEPORTING) {
         this.deadline = Math.max(this.deadline, now + config.timeoutMs());
      }
   }

   public void cancelled(long now) {
      if (this.phase == AutoRtpLoop.Phase.TELEPORTING && !this.rejected) {
         this.deadline = Math.min(this.deadline, now);
      }
   }

   public void interrupt(long now, AutoRtpLoop.Config config) {
      if (this.phase == AutoRtpLoop.Phase.TELEPORTING || this.phase == AutoRtpLoop.Phase.SCANNING) {
         this.toWaiting(now, config);
      }
   }

   public void holdUntil(long until) {
      this.holdUntil = Math.max(this.holdUntil, until);
   }

   public void dataArrived(long now) {
      this.lastDataAt = now;
   }

   private void toWaiting(long now, AutoRtpLoop.Config config) {
      this.phase = AutoRtpLoop.Phase.WAITING;
      this.rejected = false;
      this.nextSendAt = Math.max(now, this.lastSendAt + config.delayMs());
      this.waitingSince = now;
      this.tryEndedAt = now;
   }

   private long readyAt() {
      long at = Math.max(this.nextSendAt, Math.max(this.holdUntil, this.cooldownUntil));
      return this.lastSendAt == -2305843009213693952L ? at : Math.max(at, this.lastSendAt + 3000L);
   }

   private long settleUntil() {
      return this.lastDataAt == -2305843009213693952L ? 0L : Math.min(this.lastDataAt + 1500L, this.waitingSince + 8000L);
   }

   private long sendAt() {
      return Math.max(this.readyAt(), this.settleUntil());
   }

   public AutoRtpLoop.Phase phase() {
      return this.phase;
   }

   public boolean running() {
      return this.phase != AutoRtpLoop.Phase.OFF;
   }

   public boolean awaitingTeleport() {
      return this.phase == AutoRtpLoop.Phase.TELEPORTING && !this.rejected;
   }

   public int sent() {
      return this.sent;
   }

   public int landings() {
      return this.landings;
   }

   public boolean sentWithin(long now, long ms) {
      return this.lastSendAt != -2305843009213693952L && now - this.lastSendAt <= ms;
   }

   public long nextSendIn(long now) {
      return Math.max(0L, this.sendAt() - now);
   }

   public boolean settling(long now) {
      return this.phase == AutoRtpLoop.Phase.WAITING && now >= this.readyAt() && now < this.settleUntil();
   }

   public long cooldownLeft(long now) {
      return Math.max(0L, this.cooldownUntil - now);
   }

   public long scanLeft(long now) {
      return this.phase == AutoRtpLoop.Phase.SCANNING ? Math.max(0L, this.scanUntil - now) : 0L;
   }

   @Environment(EnvType.CLIENT)
   public record Config(long scanMs, long delayMs, long timeoutMs, int maxRtps) {
      public Config {
         scanMs = Math.max(2000L, scanMs);
         delayMs = Math.max(3000L, delayMs);
         timeoutMs = Math.max(3000L, timeoutMs);
         maxRtps = Math.max(0, maxRtps);
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Phase {
      OFF,
      WAITING,
      TELEPORTING,
      SCANNING;
   }

   @Environment(EnvType.CLIENT)
   public enum Step {
      NONE,
      SEND,
      TIMED_OUT,
      SCAN_CLEAR,
      LIMIT,
      GAVE_UP;
   }
}
