package net.anubis.client.basefinder;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

/**
 * A ranked chunk ready to draw. {@code bearing} is the direction to the chunk relative to where
 * the player is looking, in degrees (0 = straight ahead, positive = to the right).
 */
@Environment(EnvType.CLIENT)
public record BaseFinderEntry(long chunk, int blockX, int blockZ, float probability, List<ChunkReport.Reason> reasons, Vec3d anchor, double distance, float bearing, int argb) {
   public String topReason() {
      return this.reasons.isEmpty() ? "" : this.reasons.get(0).text();
   }
}
