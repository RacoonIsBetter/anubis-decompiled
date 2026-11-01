package net.anubis.client.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
final class StandPicker {
   private static final float BLAST_PROOF = 600.0F;
   private static final double[] RADII = new double[]{1.5, 3.0, 4.0};
   private static final int HOLE_SEARCH = 4;
   private static final int MAX_HOLES = 6;

   private StandPicker() {
   }

   static StandPicker.Spot pick(World level, PlayerEntity self, PlayerEntity enemy, double min, double ideal, double max, double cover) {
      BlockPos here = self.getBlockPos();
      List<BlockPos> candidates = new ArrayList<>();
      candidates.add(here);

      for (double radius : RADII) {
         for (int i = 0; i < 8; i++) {
            double angle = (Math.PI / 4) * i;
            candidates.add(BlockPos.ofFloored(self.getX() + Math.cos(angle) * radius, self.getY(), self.getZ() + Math.sin(angle) * radius));
         }
      }

      candidates.addAll(holesNear(level, here));
      StandPicker.Spot best = null;
      double stay = Double.NEGATIVE_INFINITY;

      for (BlockPos raw : candidates) {
         BlockPos feet = standable(level, raw);
         if (feet != null) {
            double score = score(level, self, enemy, feet, min, ideal, max, cover);
            if (feet.equals(here)) {
               stay = Math.max(stay, score);
            }

            if (best == null || score > best.score()) {
               best = new StandPicker.Spot(feet, score);
            }
         }
      }

      return best != null && !best.feet().equals(here) && !(best.score() < stay + 0.5) ? best : null;
   }

   static double hole(World level, BlockPos feet) {
      int closed = 0;

      for (Direction side : Type.HORIZONTAL) {
         if (level.getBlockState(feet.offset(side)).getBlock().getBlastResistance() >= 600.0F) {
            closed++;
         }
      }

      return closed == 4 ? 1.0 : (closed == 3 ? 0.4 : 0.0);
   }

   private static double score(World level, PlayerEntity self, PlayerEntity enemy, BlockPos feet, double min, double ideal, double max, double cover) {
      Vec3d at = Vec3d.ofBottomCenter(feet);
      double distance = Math.hypot(enemy.getX() - at.x, enemy.getZ() - at.z);
      if (!(distance < min - 0.5) && !(distance > max + 1.5)) {
         double travel = Math.hypot(self.getX() - at.x, self.getZ() - at.z);
         return -Math.abs(distance - ideal) + 3.0 * cover * hole(level, feet) - 0.15 * travel;
      } else {
         return -10.0 - Math.abs(distance - ideal);
      }
   }

   static BlockPos standable(World level, BlockPos pos) {
      for (int dy : new int[]{0, -1, 1}) {
         BlockPos feet = pos.up(dy);
         BlockPos floor = feet.down();
         BlockState under = level.getBlockState(floor);
         if (!under.getCollisionShape(level, floor).isEmpty()
            && !harmful(under)
            && !under.isOf(Blocks.MAGMA_BLOCK)
            && clear(level, feet)
            && clear(level, feet.up())) {
            return feet;
         }
      }

      return null;
   }

   private static boolean clear(World level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.getCollisionShape(level, pos).isEmpty() && !harmful(state) && level.getFluidState(pos).isEmpty();
   }

   private static boolean harmful(BlockState state) {
      return state.isOf(Blocks.LAVA)
         || state.isIn(BlockTags.FIRE)
         || state.isOf(Blocks.CACTUS)
         || state.isOf(Blocks.SWEET_BERRY_BUSH)
         || state.isOf(Blocks.COBWEB)
         || state.isOf(Blocks.POWDER_SNOW);
   }

   private static List<BlockPos> holesNear(World level, BlockPos here) {
      List<BlockPos> holes = new ArrayList<>();

      for (BlockPos pos : BlockPos.iterate(here.add(-4, -1, -4), here.add(4, 1, 4))) {
         if (hole(level, pos) >= 1.0 && standable(level, pos) != null && standable(level, pos).equals(pos)) {
            holes.add(pos.toImmutable());
         }
      }

      holes.sort(Comparator.comparingDouble(posx -> posx.getSquaredDistance(here)));
      return holes.size() > 6 ? holes.subList(0, 6) : holes;
   }

   @Environment(EnvType.CLIENT)
   record Spot(BlockPos feet, double score) {
      Vec3d center() {
         return Vec3d.ofBottomCenter(this.feet);
      }
   }
}
