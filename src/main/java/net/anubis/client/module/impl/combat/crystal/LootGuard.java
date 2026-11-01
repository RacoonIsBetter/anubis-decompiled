package net.anubis.client.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class LootGuard {
   private static final double HALF_WIDTH = 8.0;
   private static final double HALF_HEIGHT = 4.0;
   private static final double WATCH_RANGE = 16.0;
   private static final double LOADED_RANGE = 32.0;
   private static final long OTHER_MS = 60000L;
   private static final long OWN_MS = 300000L;
   private static final long EMPTY_GRACE_MS = 3000L;
   private static final List<LootGuard.Spot> SPOTS = new ArrayList<>();
   private static final IntSet SEEN = new IntOpenHashSet();
   private static RegistryKey<World> dimension;
   private static ClientPlayerEntity lastPlayer;
   private static int lastTick = -1;
   private static boolean selfDead;

   private LootGuard() {
   }

   public static void update(ClientWorld level, ClientPlayerEntity player) {
      if (player != lastPlayer || player.age != lastTick) {
         lastPlayer = player;
         lastTick = player.age;
         dimension = level.getRegistryKey();
         long now = Util.getMeasuringTimeMs();
         if (player.isDead()) {
            if (!selfDead) {
               SPOTS.add(new LootGuard.Spot(dimension, player.getEntityPos(), now, now + 300000L));
            }

            selfDead = true;
         } else {
            selfDead = false;
         }

         SEEN.removeIf(id -> level.getEntityById(id) == null);

         for (PlayerEntity other : level.getPlayers()) {
            if (other != player && other.isDead() && SEEN.add(other.getId()) && other.distanceTo(player) <= 16.0) {
               SPOTS.add(new LootGuard.Spot(dimension, other.getEntityPos(), now, now + 60000L));
            }
         }

         SPOTS.removeIf(
            spot -> now > spot.until()
               || spot.dimension() == dimension
                  && now - spot.since() > 3000L
                  && spot.at().distanceTo(player.getEntityPos()) <= 32.0
                  && level.getNonSpectatingEntities(ItemEntity.class, spot.box()).isEmpty()
         );
      }
   }

   public static boolean near(Vec3d center) {
      for (LootGuard.Spot spot : SPOTS) {
         if (spot.dimension() == dimension && spot.box().contains(center)) {
            return true;
         }
      }

      return false;
   }

   @Environment(EnvType.CLIENT)
   private record Spot(RegistryKey<World> dimension, Vec3d at, long since, long until) {
      Box box() {
         return new Box(this.at.x - 8.0, this.at.y - 4.0, this.at.z - 8.0, this.at.x + 8.0, this.at.y + 4.0, this.at.z + 8.0);
      }
   }
}
