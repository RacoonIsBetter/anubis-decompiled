package net.anubis.client.util.playerbypass;

import java.util.BitSet;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.LightData;

@Environment(EnvType.CLIENT)
public final class LightDataClassifier {
   private static final int NIBBLES_PER_SECTION = 2048;

   private LightDataClassifier() {
   }

   public static LightSectionStatus classifyPayload(byte[] payload) {
      if (payload != null && payload.length == 2048) {
         boolean allZero = true;
         boolean allMax = true;

         for (byte b : payload) {
            if (b != 0) {
               allZero = false;
            }

            if (b != -1) {
               allMax = false;
            }

            if (!allZero && !allMax) {
               return LightSectionStatus.NORMAL;
            }
         }

         if (allZero) {
            return LightSectionStatus.ZEROED_PRESENT;
         } else {
            return allMax ? LightSectionStatus.UNIFORM_MAX : LightSectionStatus.NORMAL;
         }
      } else {
         return LightSectionStatus.UNKNOWN;
      }
   }

   public static void apply(LightData data, ChunkLightSnapshot snapshot) {
      applyTrack(data.getInitedSky(), data.getUninitedSky(), data.getSkyNibbles(), snapshot.sky);
   }

   static int sentNotDark(LightData data, int sectionCount, int bottomSectionY) {
      BitSet mask = data.getInitedSky();
      BitSet empty = data.getUninitedSky();
      List<byte[]> updates = data.getSkyNibbles();
      int bits = 0;
      int next = 0;

      for (int i = 0; i < sectionCount + 2; i++) {
         boolean sent = mask.get(i);
         byte[] payload = sent && next < updates.size() ? updates.get(next) : null;
         if (sent) {
            next++;
         }

         if (i >= 1 && i <= sectionCount && i < 32 && ChunkLightSnapshot.candidate(i, bottomSectionY)) {
            if (sent) {
               LightSectionStatus status = classifyPayload(payload);
               if (status == LightSectionStatus.NORMAL || status == LightSectionStatus.UNIFORM_MAX) {
                  bits |= 1 << i;
               }
            } else if (empty.get(i)) {
               bits |= 1 << i;
            }
         }
      }

      return bits;
   }

   static void applyTrack(BitSet mask, BitSet emptyMask, List<byte[]> updates, LightSectionStatus[] out) {
      int next = 0;

      for (int i = 0; i < out.length; i++) {
         if (mask.get(i)) {
            byte[] payload = next < updates.size() ? updates.get(next) : null;
            next++;
            out[i] = classifyPayload(payload);
         } else if (emptyMask.get(i)) {
            out[i] = LightSectionStatus.EMPTY_MASK;
         }
      }
   }
}
