package net.anubis.client.module.impl.combat.bot;

import java.util.List;
import net.anubis.client.util.pvp.Rotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
final class PearlFlight {
   private static final double SPEED = 1.5;
   private static final double GRAVITY = 0.03;
   private static final double DRAG = 0.99;
   private static final double WATER_DRAG = 0.8;
   private static final int MAX_TICKS = 100;
   private static final int MAX_DROP = 3;
   private static final double EDGE_MARGIN = 0.8;

   private PearlFlight() {
   }

   static PearlFlight.Landing land(ClientPlayerEntity self, float yaw, float pitch) {
      Vec3d start = self.getEyePos().subtract(0.0, 0.1, 0.0);
      Vec3d motion = self.getMovement();
      Vec3d velocity = Vec3d.fromPolar(pitch, yaw).multiply(1.5).add(motion.x, self.isOnGround() ? 0.0 : motion.y, motion.z);
      return fly(self.getEntityWorld(), self, start, velocity);
   }

   static PearlFlight.Landing fly(World level, Entity ignored, Vec3d from, Vec3d velocity) {
      Vec3d at = from;
      Vec3d v = velocity;

      for (int tick = 1; tick <= 100; tick++) {
         BlockPos cell = BlockPos.ofFloored(at);
         if (!level.getChunkManager().isChunkLoaded(cell.getX() >> 4, cell.getZ() >> 4) || at.y < level.getBottomY()) {
            return null;
         }

         boolean water = level.getFluidState(cell).isIn(FluidTags.WATER);
         v = new Vec3d(v.x, v.y - 0.03, v.z).multiply(water ? 0.8 : 0.99);
         Vec3d next = at.add(v);
         BlockHitResult hit = level.raycast(new RaycastContext(at, next, ShapeType.COLLIDER, FluidHandling.NONE, ignored));
         if (hit.getType() != Type.MISS) {
            Vec3d landed = hit.getPos();
            BlockPos floor = floorUnder(level, landed);
            return floor == null ? null : new PearlFlight.Landing(landed, floor, tick);
         }

         at = next;
      }

      return null;
   }

   static Rotation escape(ClientPlayerEntity self, List<? extends Entity> enemies, double minAway) {
      Vec3d middle = Vec3d.ZERO;

      for (Entity enemy : enemies) {
         middle = middle.add(enemy.getEntityPos());
      }

      middle = middle.multiply(1.0 / Math.max(1, enemies.size()));
      float away = (float)Math.toDegrees(Math.atan2(-(self.getX() - middle.x), self.getZ() - middle.z));
      Rotation best = null;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (int k = -4; k <= 4; k++) {
         float yaw = MathHelper.wrapDegrees(away + k * 22.5F);

         for (float pitch = -45.0F; pitch <= 5.0F; pitch += 7.0F) {
            PearlFlight.Landing landing = land(self, yaw, pitch);
            if (landing != null && safe(self.getEntityWorld(), landing)) {
               double nearest = nearest(enemies, landing.at());
               if (!(nearest < minAway)) {
                  double score = nearest - 0.5 * (landing.at().y - landing.floor().getY() - 1.0) - 0.05 * landing.ticks();
                  if (score > bestScore) {
                     bestScore = score;
                     best = new Rotation(yaw, pitch);
                  }
               }
            }
         }
      }

      return best;
   }

   static Rotation toward(ClientPlayerEntity self, Vec3d spot, double near, double far) {
      Vec3d eye = self.getEyePos();
      double straight = Math.hypot(spot.x - eye.x, spot.z - eye.z);
      float yaw0 = (float)Math.toDegrees(Math.atan2(-(spot.x - eye.x), spot.z - eye.z));
      double middle = (near + far) / 2.0;

      for (boolean lob : new boolean[]{false, true}) {
         for (double past : lob ? new double[]{0.0, middle, -middle} : new double[]{middle, -middle}) {
            double wanted = straight + past;
            if (!(wanted < 1.0)) {
               for (float off : new float[]{0.0F, -1.5F, 1.5F, -3.0F, 3.0F}) {
                  Rotation found = pitchFor(self, eye, MathHelper.wrapDegrees(yaw0 + off), wanted, lob);
                  if (found != null && reaches(self, found.yaw(), found.pitch(), spot, lob ? 0.0 : near, far)) {
                     return found;
                  }
               }
            }
         }
      }

      return null;
   }

   private static Rotation pitchFor(ClientPlayerEntity self, Vec3d eye, float yaw, double wanted, boolean lob) {
      float shortOf = Float.NaN;
      float from = lob ? -88.0F : 15.0F;
      float to = lob ? -40.0F : -60.0F;
      float step = lob ? 4.0F : -5.0F;

      for (float pitch = from; lob ? pitch <= to : pitch >= to; pitch += step) {
         if (!(carry(self, eye, yaw, pitch) < wanted)) {
            if (Float.isNaN(shortOf)) {
               return null;
            }

            float low = shortOf;
            float high = pitch;

            for (int i = 0; i < 8; i++) {
               float mid = (low + high) / 2.0F;
               if (carry(self, eye, yaw, mid) >= wanted) {
                  high = mid;
               } else {
                  low = mid;
               }
            }

            return new Rotation(yaw, (low + high) / 2.0F);
         }

         shortOf = pitch;
      }

      return null;
   }

   private static double carry(ClientPlayerEntity self, Vec3d eye, float yaw, float pitch) {
      PearlFlight.Landing landing = land(self, yaw, pitch);
      return landing == null ? Double.MAX_VALUE : Math.hypot(landing.at().x - eye.x, landing.at().z - eye.z);
   }

   static boolean escapes(ClientPlayerEntity self, float yaw, float pitch, List<? extends Entity> enemies, double minAway) {
      PearlFlight.Landing landing = land(self, yaw, pitch);
      return landing != null && safe(self.getEntityWorld(), landing) && nearest(enemies, landing.at()) >= minAway;
   }

   static boolean reaches(ClientPlayerEntity self, float yaw, float pitch, Vec3d spot, double near, double far) {
      PearlFlight.Landing landing = land(self, yaw, pitch);
      if (landing != null && safe(self.getEntityWorld(), landing)) {
         double distance = horizontal(landing.at(), spot);
         return distance >= near && distance <= far && Math.abs(landing.at().y - spot.y) <= 3.0;
      } else {
         return false;
      }
   }

   static int flightTicks(double distance) {
      if (distance <= 6.0) {
         return 5;
      } else if (distance <= 10.0) {
         return 5 + (int)Math.round((distance - 6.0) / 2.0);
      } else if (distance <= 14.0) {
         return 7 + (int)Math.round((distance - 10.0) * 0.75);
      } else {
         return distance <= 20.0 ? 10 + (int)Math.round((distance - 14.0) * 0.83) : 15 + (int)Math.round((distance - 20.0) * 0.8);
      }
   }

   static double nearest(List<? extends Entity> others, Vec3d at) {
      double nearest = Double.MAX_VALUE;

      for (Entity other : others) {
         nearest = Math.min(nearest, other.getEntityPos().distanceTo(at));
      }

      return nearest;
   }

   private static double horizontal(Vec3d a, Vec3d b) {
      return Math.hypot(a.x - b.x, a.z - b.z);
   }

   private static BlockPos floorUnder(World level, Vec3d landed) {
      BlockPos at = BlockPos.ofFloored(landed.x, landed.y + 0.05, landed.z);

      for (int down = 0; down <= 4; down++) {
         BlockPos pos = at.down(down);
         if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return pos;
         }
      }

      return null;
   }

   private static boolean safe(World level, PearlFlight.Landing landing) {
      BlockPos floor = landing.floor();
      if (landing.at().y - (floor.getY() + 1.0) > 3.0) {
         return false;
      }

      for (double[] off : new double[][]{{0.8, 0.0}, {-0.8, 0.0}, {0.0, 0.8}, {0.0, -0.8}}) {
         BlockPos beside = floorUnder(level, landing.at().add(off[0], 0.0, off[1]));
         if (beside == null || beside.getY() != floor.getY()) {
            return false;
         }
      }

      if (!harmful(level.getBlockState(floor)) && !level.getBlockState(floor).isOf(Blocks.MAGMA_BLOCK)) {
         for (int up = 1; up <= 2; up++) {
            BlockPos pos = floor.up(up);
            BlockState state = level.getBlockState(pos);
            if (harmful(state) || !state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean harmful(BlockState state) {
      return state.isOf(Blocks.LAVA)
         || state.isIn(BlockTags.FIRE)
         || state.isOf(Blocks.CACTUS)
         || state.isOf(Blocks.SWEET_BERRY_BUSH)
         || state.isOf(Blocks.COBWEB)
         || state.isOf(Blocks.POWDER_SNOW)
         || state.isOf(Blocks.WITHER_ROSE);
   }

   @Environment(EnvType.CLIENT)
   record Landing(Vec3d at, BlockPos floor, int ticks) {
   }
}
