package net.anubis.client.easyplace.builder;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap.Type;

@Environment(EnvType.CLIENT)
final class ElytraFlight {
   static final int BOOST_TICKS = 45;
   static final int HORIZON = 60;
   private static final double CRASH = 1000000.0;
   private static final float[] PITCHES = new float[]{-62.0F, -46.0F, -33.0F, -23.0F, -15.0F, -9.0F, -4.0F, 1.0F, 6.0F, 12.0F, 19.0F, 27.0F, 36.0F};
   private static final float[] NEAR_YAWS = new float[]{0.0F, 12.0F, -12.0F};
   private static final float[] WIDE_YAWS = new float[]{30.0F, -30.0F, 55.0F, -55.0F, 90.0F, -90.0F, 140.0F, -140.0F};
   private static final float[] LAND_PITCHES = new float[]{-28.0F, -18.0F, -10.0F, -4.0F, 0.0F, 4.0F, 8.0F, 12.0F, 16.0F, 21.0F, 27.0F, 34.0F};
   private static final float[] LAND_YAWS = new float[]{0.0F, 18.0F, -18.0F, 36.0F, -36.0F, 60.0F, -60.0F, 90.0F, -90.0F};
   private static final double FOOT = 0.3;

   private ElytraFlight() {
   }

   static float bearing(double fromX, double fromZ, double toX, double toZ) {
      return (float)(MathHelper.atan2(toZ - fromZ, toX - fromX) * 180.0F / (float)Math.PI) - 90.0F;
   }

   static double cost(ElytraFlight.Glide start, ElytraFlight.Goal goal, ElytraFlight.Terrain terrain, float yaw, float pitch, boolean rocket, float maxTurn) {
      ElytraFlight.Glide glide = start.copy();
      if (rocket) {
         glide.boost = 45;
      }

      double before = Math.hypot(goal.x() - glide.x, goal.z() - glide.z);
      double shortfall = 0.0;
      double dive = 0.0;
      double below = 0.0;
      double altitude = 0.0;

      for (int t = 1; t <= 60; t++) {
         glide.step(yaw, pitch, maxTurn);
         double clear = glide.y - terrain.under(glide.x, glide.z);
         if (clear < 0.05) {
            return 1000000.0 + (60 - t) * 1000.0;
         }

         double need = Math.max(goal.minClear(), Math.max(glide.speed() * 5.0, -glide.vy * 25.0));
         if (clear < need) {
            double gap = need - clear;
            shortfall += gap * gap / 2.0 + 2.0 * gap;
         }

         if (glide.vy < -0.5) {
            dive += (-glide.vy - 0.5) * 25.0;
            if (clear < 8.0) {
               dive += (-glide.vy - 0.5) * 30.0;
            }
         }

         if (glide.y < goal.floor()) {
            below += (goal.floor() - glide.y) * 4.0;
         }

         altitude += Math.abs(glide.y - goal.altitude()) * (t < 20 ? 0.4 : 1.0);
      }

      double cost = shortfall / 60.0 * 10.0 + dive / 60.0 * 10.0 + below / 60.0 + altitude / 60.0 * 0.4;
      double off = glide.y - goal.altitude();
      cost += off < 0.0 ? -off * 2.2 : off * (goal.landing() ? 1.6 : 0.6);
      double after = Math.hypot(goal.x() - glide.x, goal.z() - glide.z);
      cost -= before - after;
      if (rocket) {
         cost += goal.rocketCost();
      }

      double speed = glide.flatSpeed();
      if (speed < 0.55) {
         cost += (0.55 - speed) * 60.0;
      }

      if (goal.landing() && after < 120.0 && speed > goal.maxSpeed()) {
         cost += (speed - goal.maxSpeed()) * 40.0;
      }

      return cost;
   }

   static boolean clear(ElytraFlight.Glide start, ElytraFlight.Goal goal, ElytraFlight.Terrain terrain, float yaw, float pitch, boolean rocket, float maxTurn) {
      ElytraFlight.Glide glide = start.copy();
      if (rocket) {
         glide.boost = 45;
      }

      for (int t = 1; t <= 60; t++) {
         glide.step(yaw, pitch, maxTurn);
         if (glide.y - terrain.under(glide.x, glide.z) < goal.minClear()) {
            return false;
         }
      }

      return glide.flatSpeed() > 0.4;
   }

   static ElytraFlight.Plan search(ElytraFlight.Glide now, ElytraFlight.Goal goal, ElytraFlight.Terrain terrain, boolean boosting, float maxTurn) {
      float bearing = bearing(now.x, now.z, goal.x(), goal.z());
      ElytraFlight.Plan best = pass(now, goal, terrain, bearing, NEAR_YAWS, boosting, maxTurn, false, null);
      if (best.safe()) {
         return best;
      }

      best = pass(now, goal, terrain, bearing, NEAR_YAWS, boosting, maxTurn, true, best);
      return best.safe() ? best : pass(now, goal, terrain, bearing, WIDE_YAWS, boosting, maxTurn, true, best);
   }

   private static ElytraFlight.Plan pass(
      ElytraFlight.Glide now,
      ElytraFlight.Goal goal,
      ElytraFlight.Terrain terrain,
      float bearing,
      float[] yaws,
      boolean boosting,
      float maxTurn,
      boolean anyPitchRocket,
      ElytraFlight.Plan best
   ) {
      for (float offset : yaws) {
         float yaw = MathHelper.wrapDegrees(bearing + offset);

         for (float pitch : PITCHES) {
            for (int r = 0; r < 2; r++) {
               boolean rocket = r == 1;
               if (!rocket || goal.rockets() && !boosting && (anyPitchRocket || !(pitch > -30.0F))) {
                  double cost = cost(now, goal, terrain, yaw, pitch, rocket, maxTurn) + Math.abs(offset) * 0.08;
                  if (best == null || cost < best.cost()) {
                     best = new ElytraFlight.Plan(yaw, pitch, rocket, cost, cost < 400.0);
                  }
               }
            }
         }
      }

      return best;
   }

   static ElytraFlight.Plan touchdown(ElytraFlight.Glide now, BlockPos spot, ElytraFlight.Terrain terrain, float maxTurn) {
      float bearing = bearing(now.x, now.z, spot.getX() + 0.5, spot.getZ() + 0.5);
      ElytraFlight.Plan best = null;

      for (float offset : LAND_YAWS) {
         float yaw = MathHelper.wrapDegrees(bearing + offset);

         for (float pitch : LAND_PITCHES) {
            double cost = landing(now, spot, terrain, yaw, pitch, maxTurn) + Math.abs(offset) * 0.15;
            if (best == null || cost < best.cost()) {
               best = new ElytraFlight.Plan(yaw, pitch, false, cost, cost < 900.0);
            }
         }
      }

      return best;
   }

   private static double landing(ElytraFlight.Glide start, BlockPos spot, ElytraFlight.Terrain terrain, float yaw, float pitch, float maxTurn) {
      ElytraFlight.Glide glide = start.copy();
      double ground = terrain.under(glide.x, glide.z);

      for (int t = 1; t <= 200; t++) {
         double lastGround = ground;
         glide.step(yaw, pitch, maxTurn);
         ground = terrain.under(glide.x, glide.z);
         if (!(glide.y > ground)) {
            if (ground - lastGround > 0.6 && glide.flatSpeed() > 0.3) {
               return 1000000.0;
            }

            double cost = Math.hypot(glide.x - (spot.getX() + 0.5), glide.z - (spot.getZ() + 0.5));
            if (glide.vy < -0.5) {
               cost += 200.0;
            }

            if (glide.flatSpeed() > 1.0) {
               cost += (glide.flatSpeed() - 1.0) * 20.0;
            }

            if (!goodFloor(terrain.level(), BlockPos.ofFloored(glide.x, ground - 1.0, glide.z))) {
               cost += 900.0;
            }

            return cost;
         }
      }

      return 1000000.0;
   }

   static boolean goodFloor(ClientWorld level, BlockPos floor) {
      if (floor.getY() < level.getBottomY()) {
         return false;
      }

      BlockState state = level.getBlockState(floor);
      if (!state.getFluidState().isEmpty() || state.isIn(BlockTags.LEAVES) || hurts(state)) {
         return false;
      }

      if (!state.isOf(Blocks.SLIME_BLOCK) && !state.isOf(Blocks.HONEY_BLOCK) && !state.isOf(Blocks.POWDER_SNOW)) {
         VoxelShape shape = state.getCollisionShape(level, floor);
         if (shape.isEmpty()) {
            return false;
         } else {
            Box box = shape.getBoundingBox();
            if (!(box.maxY < 0.45) && !(box.maxY > 1.01) && !(box.getLengthX() < 0.9) && !(box.getLengthZ() < 0.9)) {
               BlockPos above = floor.up();
               BlockState over = level.getBlockState(above);
               return over.getCollisionShape(level, above).isEmpty() && over.getFluidState().isEmpty() && !hurts(over);
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean hurts(BlockState state) {
      return state.isOf(Blocks.MAGMA_BLOCK)
         || state.isIn(BlockTags.CAMPFIRES)
         || state.isOf(Blocks.CACTUS)
         || state.isOf(Blocks.SWEET_BERRY_BUSH)
         || state.isIn(BlockTags.FIRE)
         || state.isOf(Blocks.POINTED_DRIPSTONE)
         || state.isOf(Blocks.WITHER_ROSE)
         || state.isOf(Blocks.COBWEB)
         || state.isOf(Blocks.LAVA);
   }

   static BlockPos landingSpot(ElytraFlight.Terrain terrain, int centerX, int centerZ, int radius, boolean strict, Predicate<BlockPos> banned) {
      ClientWorld level = terrain.level();

      for (int r = 0; r <= radius; r += 2) {
         for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int x = centerX + dx;
                  int z = centerZ + dz;
                  if (terrain.known(x - 1, z - 1) && terrain.known(x + 1, z + 1)) {
                     int top = level.getTopY(Type.MOTION_BLOCKING, x, z);
                     BlockPos spot = new BlockPos(x, top, z);
                     if (!banned.test(spot) && flat(level, x, z, top, strict)) {
                        return spot;
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static boolean flat(ClientWorld level, int x, int z, int top, boolean strict) {
      if (!goodFloor(level, new BlockPos(x, top - 1, z))) {
         return false;
      }

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               int height = level.getTopY(Type.MOTION_BLOCKING, x + dx, z + dz);
               if (strict ? height != top : Math.abs(height - top) > 1) {
                  return false;
               }

               if (!goodFloor(level, new BlockPos(x + dx, height - 1, z + dz))) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   @Environment(EnvType.CLIENT)
   static final class Glide {
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      float yaw;
      float pitch;
      int boost;
      double gravity;

      static ElytraFlight.Glide of(ClientPlayerEntity player, int boostLeft) {
         ElytraFlight.Glide glide = new ElytraFlight.Glide();
         glide.x = player.getX();
         glide.y = player.getY();
         glide.z = player.getZ();
         Vec3d motion = player.getVelocity();
         glide.vx = motion.x;
         glide.vy = motion.y;
         glide.vz = motion.z;
         glide.yaw = player.getYaw();
         glide.pitch = player.getPitch();
         glide.boost = boostLeft;
         glide.gravity = player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? 0.01 : 0.08;
         return glide;
      }

      ElytraFlight.Glide copy() {
         ElytraFlight.Glide glide = new ElytraFlight.Glide();
         glide.x = this.x;
         glide.y = this.y;
         glide.z = this.z;
         glide.vx = this.vx;
         glide.vy = this.vy;
         glide.vz = this.vz;
         glide.yaw = this.yaw;
         glide.pitch = this.pitch;
         glide.boost = this.boost;
         glide.gravity = this.gravity;
         return glide;
      }

      void step(float wantYaw, float wantPitch, float maxTurn) {
         this.yaw = this.yaw + MathHelper.clamp(MathHelper.wrapDegrees(wantYaw - this.yaw), -maxTurn, maxTurn);
         this.pitch = MathHelper.clamp(this.pitch + MathHelper.clamp(wantPitch - this.pitch, -maxTurn, maxTurn), -90.0F, 90.0F);
         Vec3d look = Vec3d.fromPolar(this.pitch, this.yaw);
         double radians = this.pitch * (float) (Math.PI / 180.0);
         double flat = Math.sqrt(look.x * look.x + look.z * look.z);
         double speed = Math.sqrt(this.vx * this.vx + this.vz * this.vz);
         double lift = Math.cos(radians);
         lift *= lift;
         double fall = this.vy <= 0.0 ? this.gravity : 0.08;
         this.vy += fall * (-1.0 + lift * 0.75);
         if (this.vy < 0.0 && flat > 0.0) {
            double turn = this.vy * -0.1 * lift;
            this.vx = this.vx + look.x * turn / flat;
            this.vy += turn;
            this.vz = this.vz + look.z * turn / flat;
         }

         if (radians < 0.0 && flat > 0.0) {
            double climb = speed * -Math.sin(radians) * 0.04;
            this.vx = this.vx - look.x * climb / flat;
            this.vy += climb * 3.2;
            this.vz = this.vz - look.z * climb / flat;
         }

         if (flat > 0.0) {
            this.vx = this.vx + (look.x / flat * speed - this.vx) * 0.1;
            this.vz = this.vz + (look.z / flat * speed - this.vz) * 0.1;
         }

         this.vx *= 0.99;
         this.vy *= 0.98;
         this.vz *= 0.99;
         if (this.boost > 0) {
            this.vx = this.vx + (look.x * 0.1 + (look.x * 1.5 - this.vx) * 0.5);
            this.vy = this.vy + (look.y * 0.1 + (look.y * 1.5 - this.vy) * 0.5);
            this.vz = this.vz + (look.z * 0.1 + (look.z * 1.5 - this.vz) * 0.5);
            this.boost--;
         }

         this.x = this.x + this.vx;
         this.y = this.y + this.vy;
         this.z = this.z + this.vz;
      }

      double speed() {
         return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
      }

      double flatSpeed() {
         return Math.sqrt(this.vx * this.vx + this.vz * this.vz);
      }
   }

   @Environment(EnvType.CLIENT)
   record Goal(double x, double z, double altitude, double minClear, boolean landing, boolean rockets, double rocketCost, double maxSpeed, double floor) {
   }

   @Environment(EnvType.CLIENT)
   record Plan(float yaw, float pitch, boolean rocket, double cost, boolean safe) {
   }

   @Environment(EnvType.CLIENT)
   static final class Terrain {
      private final ClientWorld level;
      private final double unknown;
      private final Long2DoubleOpenHashMap heights = new Long2DoubleOpenHashMap();

      Terrain(ClientWorld level, double unknown) {
         this.level = level;
         this.unknown = unknown;
         this.heights.defaultReturnValue(Double.NaN);
      }

      double surface(double x, double z) {
         int bx = MathHelper.floor(x);
         int bz = MathHelper.floor(z);
         long key = BlockPos.asLong(bx, 0, bz);
         double height = this.heights.get(key);
         if (Double.isNaN(height)) {
            height = this.known(bx, bz) ? this.level.getTopY(Type.MOTION_BLOCKING, bx, bz) : this.unknown;
            this.heights.put(key, height);
         }

         return height;
      }

      double under(double x, double z) {
         double top = this.surface(x, z);
         top = Math.max(top, this.surface(x + 0.3, z + 0.3));
         top = Math.max(top, this.surface(x - 0.3, z - 0.3));
         top = Math.max(top, this.surface(x + 0.3, z - 0.3));
         return Math.max(top, this.surface(x - 0.3, z + 0.3));
      }

      boolean known(int x, int z) {
         return this.level.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
      }

      ClientWorld level() {
         return this.level;
      }
   }
}
