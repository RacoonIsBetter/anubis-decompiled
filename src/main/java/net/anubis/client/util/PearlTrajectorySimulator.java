package net.anubis.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class PearlTrajectorySimulator {
   private static final double SPAWN_EYE_OFFSET = 0.1;
   private static final double GRAVITY = 0.03;
   private static final float AIR_DRAG = 0.99F;
   private static final float WATER_DRAG = 0.8F;
   private static final int MAX_TICKS = 300;
   private static final double ENTITY_MARGIN = 0.3;

   private PearlTrajectorySimulator() {
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(World level, ClientPlayerEntity player, float partialTick) {
      Vec3d eye = player.getCameraPosVec(partialTick);
      Vec3d pos = new Vec3d(eye.x, eye.y - 0.1, eye.z);
      Vec3d velocity = player.getRotationVec(partialTick).multiply(EnderPearlItem.POWER);
      Vec3d motion = player.getMovement();
      velocity = velocity.add(motion.x, player.isOnGround() ? 0.0 : motion.y, motion.z);
      return simulate(level, pos, velocity, player, null);
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(World level, ProjectileEntity pearl) {
      return simulate(level, pearl.getEntityPos(), pearl.getVelocity(), pearl, pearl.getOwner());
   }

   public static PearlTrajectorySimulator.SimulationResult simulate(
      World level, Vec3d start, Vec3d velocity, Entity ignore, Entity alsoIgnore
   ) {
      List<Vec3d> points = new ArrayList<>(64);
      points.add(start);
      Vec3d pos = start;
      BlockHitResult blockHit = null;
      Entity entityHit = null;
      int ticks = 0;

      while (ticks < 300) {
         ticks++;
         Vec3d var15 = velocity.add(0.0, -0.03, 0.0);
         velocity = var15.multiply(isInWater(level, pos) ? 0.8F : 0.99F);
         Vec3d next = pos.add(velocity);
         HitResult blockResult = level.raycast(new RaycastContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, ignore));
         Vec3d end = blockResult.getType() != Type.MISS ? blockResult.getPos() : next;
         Entity entity = firstEntityOnSegment(level, pos, end, ignore, alsoIgnore);
         if (entity != null) {
            Vec3d at = entity.getBoundingBox().expand(0.3).raycast(pos, end).orElse(end);
            points.add(at);
            entityHit = entity;
            break;
         }

         if (blockResult instanceof BlockHitResult hit && hit.getType() != Type.MISS) {
            points.add(end);
            blockHit = hit;
            break;
         }

         pos = next;
         points.add(pos);
         if (pos.y < level.getBottomY() - 64) {
            break;
         }
      }

      return new PearlTrajectorySimulator.SimulationResult(List.copyOf(points), points.getLast(), blockHit, entityHit, ticks);
   }

   private static Entity firstEntityOnSegment(World level, Vec3d from, Vec3d to, Entity ignore, Entity alsoIgnore) {
      Box sweep = new Box(from, to).expand(1.0);
      Entity closest = null;
      double closestSq = Double.MAX_VALUE;

      for (Entity entity : level.getOtherEntities(ignore, sweep, PearlTrajectorySimulator::canHit)) {
         if (entity != alsoIgnore) {
            Optional<Vec3d> hit = entity.getBoundingBox().expand(0.3).raycast(from, to);
            if (!hit.isEmpty()) {
               double distanceSq = from.squaredDistanceTo(hit.get());
               if (distanceSq < closestSq) {
                  closestSq = distanceSq;
                  closest = entity;
               }
            }
         }
      }

      return closest;
   }

   private static boolean canHit(Entity entity) {
      return EntityPredicates.EXCEPT_SPECTATOR.test(entity) && entity.canHit() && !(entity instanceof ClientPlayerEntity);
   }

   private static boolean isInWater(World level, Vec3d pos) {
      return level.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
   }

   public static boolean isHoldingPearl(ClientPlayerEntity player) {
      return player.getMainHandStack().isOf(Items.ENDER_PEARL) || player.getOffHandStack().isOf(Items.ENDER_PEARL);
   }

   @Environment(EnvType.CLIENT)
   public record SimulationResult(List<Vec3d> points, Vec3d landing, BlockHitResult blockHit, Entity entityHit, int ticks) {
      public boolean landed() {
         return this.blockHit != null || this.entityHit != null;
      }
   }
}
