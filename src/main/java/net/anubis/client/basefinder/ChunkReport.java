package net.anubis.client.basefinder;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The evidence found in one chunk. {@code strength[i]} is in [0, 1] for {@link Signal#VALUES}[i];
 * {@code count[i]} is what the tooltip shows (blocks, tunnels, or a percentage for growth signals).
 */
@Environment(EnvType.CLIENT)
public final class ChunkReport {
   public final long chunk;
   public final float[] strength = new float[Signal.VALUES.length];
   public final int[] count = new int[Signal.VALUES.length];
   /** Packed BlockPos of the strongest evidence, used for the reason dot. */
   public long anchor;
   private float anchorWeight = -1.0F;

   public ChunkReport(long chunk) {
      this.chunk = chunk;
   }

   void add(Signal signal, int amount, long pos) {
      int i = signal.ordinal();
      this.count[i] += amount;
      this.strength[i] = signal.strengthFor(this.count[i]);
      this.offerAnchor(signal, pos);
   }

   void set(Signal signal, float strength, int shown, long pos) {
      int i = signal.ordinal();
      this.strength[i] = Math.max(0.0F, Math.min(1.0F, strength));
      this.count[i] = shown;
      if (strength > 0.0F) {
         this.offerAnchor(signal, pos);
      }
   }

   void clear(Signal signal) {
      this.strength[signal.ordinal()] = 0.0F;
      this.count[signal.ordinal()] = 0;
   }

   private void offerAnchor(Signal signal, long pos) {
      if (signal.weight > this.anchorWeight) {
         this.anchorWeight = signal.weight;
         this.anchor = pos;
      }
   }

   public boolean isEmpty() {
      for (float s : this.strength) {
         if (s > 0.0F) {
            return false;
         }
      }

      return true;
   }

   /** Probability from the scanned blocks, combined with what is live in the chunk (players seen or inferred). */
   public float probability(boolean[] enabledGroups, Live live) {
      float miss = 1.0F;

      for (Signal signal : Signal.VALUES) {
         if (enabledGroups[signal.group.ordinal()]) {
            miss *= 1.0F - signal.weight * this.strength(signal, live);
         }
      }

      return 1.0F - miss;
   }

   /** Reasons sorted by how much they contributed, strongest first. */
   public List<Reason> reasons(boolean[] enabledGroups, Live live) {
      List<Reason> out = new ArrayList<>();

      for (Signal signal : Signal.VALUES) {
         if (enabledGroups[signal.group.ordinal()]) {
            float s = this.strength(signal, live);
            if (s > 0.0F) {
               out.add(new Reason(signal, signal.weight * s, describe(signal, this.count[signal.ordinal()], live)));
            }
         }
      }

      out.sort((a, b) -> Float.compare(b.contribution(), a.contribution()));
      return out;
   }

   private float strength(Signal signal, Live live) {
      return Math.max(live.strength[signal.ordinal()], this.strength[signal.ordinal()]);
   }

   private static String describe(Signal signal, int count, Live live) {
      String detail = live.detail[signal.ordinal()];
      if (detail != null && live.strength[signal.ordinal()] > 0.0F) {
         return detail.isEmpty() ? signal.label : signal.label + ": " + detail;
      }

      return switch (signal) {
         case PLAYER, HIDDEN_PLAYER, PETS, ANIMAL_FARM, PLACED_ENTITIES -> signal.label;
         case AMETHYST, KELP -> signal.label + " (" + count + "% grown)";
         case TUNNEL, SHAFT, DRIPSTONE -> signal.label + (count > 1 ? " x" + count : "");
         case SURFACE_BUILD -> signal.label + " (" + count + " blocks)";
         case ROOM, NETHER_PORTAL -> signal.label + (count > 1 ? " x" + count : "");
         default -> signal.label + " x" + count;
      };
   }

   /**
    * Evidence that is not in the blocks (players seen or inferred, entities): per signal, a strength
    * in [0, 1] and the text the tooltip shows after the label. The stronger of this and the scan wins.
    */
   @Environment(EnvType.CLIENT)
   public static final class Live {
      final float[] strength = new float[Signal.VALUES.length];
      final String[] detail = new String[Signal.VALUES.length];

      public Live put(Signal signal, float value, String text) {
         int i = signal.ordinal();
         if (value > this.strength[i]) {
            this.strength[i] = Math.min(1.0F, value);
            this.detail[i] = text;
         }

         return this;
      }

      public float get(Signal signal) {
         return this.strength[signal.ordinal()];
      }
   }

   @Environment(EnvType.CLIENT)
   public record Reason(Signal signal, float contribution, String text) {
   }
}
