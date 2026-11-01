package net.anubis.client.easyplace.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.anubis.client.easyplace.util.StateRules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
final class StandPlanner {
   private static final double PILLAR_COST = 8.0;
   private static final double BRIDGE_COST = 5.0;
   private static final double BUILD_BRIDGE_COST = 2.0;
   private static final double PROP_PENALTY = 8.0;
   private static final double LEAP_COST = 2.5;
   private static final double WADE_COST = 2.5;
   private static final double ICE_COST = 2.0;
   private static final int MAX_GAP = 1;
   private static final double FALL_COST = 40.0;
   private static final double DROP_COST = 2.0;
   private static final int BRIDGE_LIMIT = 10;
   private static final double EYE = 1.62;
   private static final double CROUCH_EYE = 1.27;
   private static final int MAX_SPOTS = 12000;
   private static final double SLACK = 10.0;
   private static final double GAIN = 2.0;
   private static final int MAX_CANDIDATES = 16;
   private static final int COUNT_CAP = 24;
   private static final int RANGE = 40;
   private static final int AROUND = 8;
   private static final double[][] FACE_POINTS = new double[][]{{0.5, 0.5}, {0.2, 0.5}, {0.8, 0.5}, {0.5, 0.2}, {0.5, 0.8}};
   private static final double[][] EDGE_POINTS = new double[][]{{0.5, 0.5}, {0.03, 0.5}, {0.97, 0.5}, {0.5, 0.03}, {0.5, 0.97}};
   private static final int MAX_LEAVES = 10;
   private static final long[] NO_LEAVES = new long[0];
   private final ClientWorld level;
   private final ClientPlayerEntity player;
   private final List<StandPlanner.Target> targets;
   private final Predicate<BlockPos> avoid;
   private final double reach;
   private final StandPlanner.Pillar canPillar;
   private final Predicate<BlockPos> canBridge;
   private final StandPlanner.Exact exact;
   private final StandPlanner.Support support;
   private double maxFall;
   private final int maxFeetY;
   private final Box around;
   private final Box targetBox;
   private final BlockPos start;
   private final PriorityQueue<StandPlanner.Node> open = new PriorityQueue<>(Comparator.comparingDouble(StandPlanner.Node::rank));
   private final Map<Long, Double> best = new HashMap<>();
   private final Map<Long, Long> parent = new HashMap<>();
   private final Map<Long, StandPlanner.Move> moves = new HashMap<>();
   private final Map<Long, Integer> bridges = new HashMap<>();
   private final Map<Long, long[]> leaves = new HashMap<>();
   private final Map<Long, StandPlanner.Target> bridgeTargets = new HashMap<>();
   private int visited;
   private List<StandPlanner.Step> route;
   private StandPlanner.Target found;
   private BlockPos bestSpot;
   private double bestValue = -Double.MAX_VALUE;
   private double firstCost = -1.0;
   private int candidates;
   private boolean propped;
   private BlockPos closest;
   private double closestDistance = Double.MAX_VALUE;
   private final double startDistance;

   StandPlanner(
      ClientWorld level,
      ClientPlayerEntity player,
      List<StandPlanner.Target> targets,
      Predicate<BlockPos> avoid,
      StandPlanner.Pillar canPillar,
      Predicate<BlockPos> canBridge,
      StandPlanner.Exact exact,
      StandPlanner.Support support
   ) {
      this.level = level;
      this.player = player;
      this.targets = targets;
      this.avoid = avoid;
      this.canPillar = canPillar;
      this.canBridge = canBridge;
      this.exact = exact;
      this.support = support;
      this.reach = player.getBlockInteractionRange() - 0.25;
      this.start = player.getBlockPos();

      for (StandPlanner.Target target : targets) {
         BlockState expected = target.expected();
         if (target.use() == StandPlanner.Use.PLACE
            && StateRules.lookFor(expected) == null
            && StateRules.flatLookFor(expected) == null
            && !(expected.getBlock() instanceof FallingBlock)
            && expected.isFullCube(level, target.pos())) {
            this.bridgeTargets.put(target.pos().asLong(), target);
         }
      }

      Box box = null;

      for (StandPlanner.Target target : targets) {
         box = box == null ? new Box(target.pos()) : box.union(new Box(target.pos()));
      }

      this.maxFeetY = box == null ? this.start.getY() : (int)box.maxY + 1;
      this.around = box == null ? new Box(this.start) : box.union(new Box(this.start)).expand(8.0);
      this.targetBox = box == null ? new Box(this.start) : box.expand(8.0);
      this.startDistance = this.workDistance(this.start);
      this.open.add(new StandPlanner.Node(this.start.asLong(), 0.0, 0.0));
      this.best.put(this.start.asLong(), 0.0);
      this.parent.put(this.start.asLong(), Long.MIN_VALUE);
      this.leaves.put(this.start.asLong(), NO_LEAVES);
   }

   int visited() {
      return this.visited;
   }

   int lowestTargetY() {
      return (int)Math.round(this.targetBox.minY) + 8;
   }

   String extent() {
      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;
      int pillars = 0;
      int bridgesSeen = 0;

      for (Entry<Long, StandPlanner.Move> entry : this.moves.entrySet()) {
         BlockPos pos = BlockPos.fromLong(entry.getKey());
         minX = Math.min(minX, pos.getX());
         maxX = Math.max(maxX, pos.getX());
         minY = Math.min(minY, pos.getY());
         maxY = Math.max(maxY, pos.getY());
         minZ = Math.min(minZ, pos.getZ());
         maxZ = Math.max(maxZ, pos.getZ());
         if (entry.getValue() == StandPlanner.Move.PILLAR) {
            pillars++;
         }

         if (entry.getValue() == StandPlanner.Move.BRIDGE) {
            bridgesSeen++;
         }
      }

      return "x "
         + minX
         + ".."
         + maxX
         + " y "
         + minY
         + ".."
         + maxY
         + " z "
         + minZ
         + ".."
         + maxZ
         + " pillars "
         + pillars
         + " bridges "
         + bridgesSeen
         + " targetBox "
         + this.targetBox
         + " maxFeetY "
         + this.maxFeetY
         + " | "
         + this.whyStuck();
   }

   String whyStuck() {
      boolean near = this.around.contains(this.start.toCenterPos());
      StringBuilder out = new StringBuilder(
         "near=" + near + " around=" + this.around + " maxFeetY=" + this.maxFeetY + " avoidUp=" + this.avoid.test(this.start.up(2))
      );
      if (this.canPillar != null) {
         out.append(" pillar=").append(this.canPillar.possible(this.start, false));
      }

      out.append(" floor=").append(Walker.floor(this.level, this.start));
      if (this.canBridge != null) {
         for (Direction way : Type.HORIZONTAL) {
            BlockPos next = this.start.offset(way);
            out.append(' ')
               .append(way)
               .append(":side=")
               .append(Block.isFaceFullSquare(this.level.getBlockState(this.start.down()).getOutlineShape(this.level, this.start.down()), way))
               .append(",gap=")
               .append(Double.isNaN(Walker.floor(this.level, next)))
               .append(",room=")
               .append(Walker.roomAt(this.level, next))
               .append(",fits=")
               .append(this.canBridge.test(next.down()))
               .append(",avoid=")
               .append(this.avoid.test(next));
         }
      }

      return out.toString();
   }

   StandPlanner falls(double blocks) {
      this.maxFall = blocks;
      return this;
   }

   List<StandPlanner.Step> route() {
      return this.route;
   }

   StandPlanner.Target target() {
      return this.found;
   }

   StandPlanner.Status step(long deadline) {
      while (!this.open.isEmpty()) {
         if (System.nanoTime() > deadline) {
            return StandPlanner.Status.RUNNING;
         }

         StandPlanner.Node node = this.open.poll();
         if (!(node.cost() > this.best.getOrDefault(node.pos(), Double.MAX_VALUE))) {
            if (this.firstCost >= 0.0 && (node.cost() > this.firstCost + 10.0 || this.candidates >= 16)) {
               return this.finish();
            }

            if (++this.visited > 12000) {
               return this.finish();
            }

            BlockPos spot = BlockPos.fromLong(node.pos());
            if (!spot.equals(this.start)) {
               double distance = this.workDistance(spot);
               if (distance < this.closestDistance) {
                  this.closestDistance = distance;
                  this.closest = spot;
               }

               long[] planned = this.leaves.getOrDefault(node.pos(), NO_LEAVES);
               StandPlanner.Target target = this.moves.get(node.pos()) == StandPlanner.Move.BRIDGE ? this.bridgeTargets.get(spot.down().asLong()) : null;
               this.propped = false;
               if (target == null) {
                  target = this.workFrom(spot, planned);
               }

               boolean needsProp = this.propped;
               if (target != null) {
                  double value = this.countFrom(spot, planned) * 2.0
                     - node.cost()
                     - (needsProp && this.bridgeTargets.containsKey(target.pos().asLong()) ? 8.0 : 0.0);
                  if (this.firstCost < 0.0) {
                     this.firstCost = node.cost();
                  }

                  this.candidates++;
                  if (value > this.bestValue) {
                     this.bestValue = value;
                     this.bestSpot = spot;
                     this.found = target;
                  }
               }
            }

            for (BlockPos next : Walker.steps(this.level, spot)) {
               int rise = next.getY() - spot.getY();
               double wade = (this.level.getFluidState(next).isEmpty() ? 0.0 : 2.5) + (Walker.icy(this.level, next) ? 2.0 : 0.0);
               this.relax(spot, next, node.cost() + 1.0 + (rise > 0 ? 0.6 : -rise * 2.0) + wade, StandPlanner.Move.WALK);
            }

            if (this.maxFall > 0.0) {
               for (Direction way : Type.HORIZONTAL) {
                  BlockPos landing = Walker.fall(this.level, spot, way, this.maxFall);
                  if (landing != null) {
                     this.relax(spot, landing, node.cost() + 40.0 + (spot.getY() - landing.getY()) * 2.0, StandPlanner.Move.WALK);
                  }
               }
            }

            double fromY = Walker.floorOr(this.level, spot);

            for (Direction way : Type.HORIZONTAL) {
               for (int gap = 1; gap <= 1; gap++) {
                  if (Walker.leap(this.level, spot, fromY, way, gap)) {
                     this.relax(spot, spot.offset(way, gap + 1), node.cost() + 2.5 + gap, StandPlanner.Move.LEAP);
                  }
               }
            }

            boolean near = this.around.contains(spot.toCenterPos()) || spot.getManhattanDistance(this.start) <= 4;
            boolean under = near
               || spot.getY() < this.targetBox.minY
                  && spot.getX() + 0.5 >= this.targetBox.minX
                  && spot.getX() + 0.5 <= this.targetBox.maxX
                  && spot.getZ() + 0.5 >= this.targetBox.minZ
                  && spot.getZ() + 0.5 <= this.targetBox.maxZ;
            boolean onLeaf = this.moves.get(node.pos()) == StandPlanner.Move.PILLAR || this.moves.get(node.pos()) == StandPlanner.Move.BRIDGE;
            if (under && this.canPillar != null && spot.getY() + 1 <= this.maxFeetY && this.canPillar.possible(spot, onLeaf)) {
               if (spot.getY() + 2 <= this.maxFeetY) {
                  this.relax(spot, spot.up(2), node.cost() + 8.0, StandPlanner.Move.PILLAR);
               }

               if (spot.getY() + 1 >= this.lowestTargetY()) {
                  this.relax(spot, spot.up(1), node.cost() + 4.8, StandPlanner.Move.PILLAR);
               }
            }

            if (near && spot.getY() >= this.targetBox.minY && this.canBridge != null && this.bridges.getOrDefault(node.pos(), 0) < 10) {
               this.bridgeFrom(spot, node.cost());
            }
         }
      }

      return this.finish();
   }

   private StandPlanner.Status finish() {
      if (this.bestSpot == null) {
         if (this.closest != null && !(this.closestDistance > this.startDistance - 2.0)) {
            this.route = this.path(this.closest);
            return StandPlanner.Status.APPROACH;
         } else {
            return StandPlanner.Status.NONE;
         }
      } else {
         this.route = this.path(this.bestSpot);
         return StandPlanner.Status.FOUND;
      }
   }

   private int countFrom(BlockPos feet, long[] planned) {
      double y = Walker.floorOr(this.level, feet);
      Vec3d eye = new Vec3d(feet.getX() + 0.5, y + 1.62, feet.getZ() + 0.5);
      Box body = new Box(feet.getX() + 0.2, y, feet.getZ() + 0.2, feet.getX() + 0.8, y + 1.8, feet.getZ() + 0.8);
      int count = 0;

      for (StandPlanner.Target target : this.targets) {
         BlockPos pos = target.pos();
         if (!(pos.toCenterPos().distanceTo(eye) > Math.min(this.reach, target.within()))
            && !body.intersects(new Box(pos))
            && (
               target.use() == StandPlanner.Use.PLACE
                  ? this.seenFrom(eye, target) || this.onPlanned(eye, target, planned)
                  : this.placeableFrom(eye, target, planned)
            )) {
            if (++count >= 24) {
               break;
            }
         }
      }

      return count;
   }

   private boolean seenFrom(Vec3d eye, StandPlanner.Target target) {
      Direction look = StateRules.lookFor(target.expected());
      Direction flat = StateRules.flatLookFor(target.expected());

      for (Direction side : Direction.values()) {
         BlockPos support = target.pos().offset(side);
         BlockState state = this.level.getBlockState(support);
         if (!state.isReplaceable()) {
            VoxelShape shape = state.getOutlineShape(this.level, support);
            if (!shape.isEmpty()) {
               Direction face = side.getOpposite();
               Box box = shape.getBoundingBox().offset(support);
               if (this.seen(eye, support, box, face, look, flat)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void bridgeFrom(BlockPos spot, double cost) {
      StandPlanner.Move how = this.moves.get(spot.asLong());
      boolean onLeaf = how == StandPlanner.Move.BRIDGE || how == StandPlanner.Move.PILLAR;
      if (onLeaf || Walker.floor(this.level, spot) == spot.getY()) {
         BlockPos under = spot.down();

         for (Direction way : Type.HORIZONTAL) {
            if (onLeaf || Block.isFaceFullSquare(this.level.getBlockState(under).getOutlineShape(this.level, under), way)) {
               BlockPos next = spot.offset(way);
               if (Double.isNaN(Walker.floor(this.level, next)) && Walker.roomAt(this.level, next)) {
                  boolean build = this.bridgeTargets.containsKey(next.down().asLong());
                  if (build || this.canBridge.test(next.down())) {
                     this.relax(spot, next, cost + (build ? 2.0 : 5.0), StandPlanner.Move.BRIDGE);
                  }
               }
            }
         }
      }
   }

   private void relax(BlockPos from, BlockPos next, double cost, StandPlanner.Move move) {
      if (!this.avoid.test(next)) {
         if (Math.abs(next.getX() - this.start.getX()) <= 40
            && Math.abs(next.getZ() - this.start.getZ()) <= 40
            && Math.abs(next.getY() - this.start.getY()) <= 40
            && next.getY() <= this.maxFeetY + 1) {
            long key = next.asLong();
            if (!(cost >= this.best.getOrDefault(key, Double.MAX_VALUE))) {
               this.best.put(key, cost);
               this.parent.put(key, from.asLong());
               this.moves.put(key, move);
               this.bridges.put(key, this.bridges.getOrDefault(from.asLong(), 0) + (move == StandPlanner.Move.BRIDGE ? 1 : 0));
               long[] before = this.leaves.getOrDefault(from.asLong(), NO_LEAVES);
               this.leaves
                  .put(
                     key,
                     move != StandPlanner.Move.BRIDGE && move != StandPlanner.Move.PILLAR
                        ? before
                        : withLeaf(before, next.down(), move == StandPlanner.Move.PILLAR)
                  );
               this.open.add(new StandPlanner.Node(key, cost, cost + this.guess(next)));
            }
         }
      }
   }

   private double workDistance(BlockPos spot) {
      Vec3d eye = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.62, spot.getZ() + 0.5);
      double best = Double.MAX_VALUE;

      for (int i = 0; i < Math.min(32, this.targets.size()); i++) {
         best = Math.min(best, this.targets.get(i).pos().toCenterPos().distanceTo(eye));
      }

      return best;
   }

   private double guess(BlockPos spot) {
      Vec3d eye = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.62, spot.getZ() + 0.5);
      double dx = Math.max(0.0, Math.max(this.targetBox.minX + 8.0 - eye.x, eye.x - (this.targetBox.maxX - 8.0)));
      double dy = Math.max(0.0, Math.max(this.targetBox.minY + 8.0 - eye.y, eye.y - (this.targetBox.maxY - 8.0))) * 6.5;
      double dz = Math.max(0.0, Math.max(this.targetBox.minZ + 8.0 - eye.z, eye.z - (this.targetBox.maxZ - 8.0)));
      return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - this.reach) * 0.6;
   }

   private List<StandPlanner.Step> path(BlockPos end) {
      List<StandPlanner.Step> out = new ArrayList<>();
      long at = end.asLong();

      while (at != this.start.asLong() && at != Long.MIN_VALUE) {
         out.add(new StandPlanner.Step(BlockPos.fromLong(at), this.moves.getOrDefault(at, StandPlanner.Move.WALK)));
         at = this.parent.get(at);
      }

      Collections.reverse(out);
      return out;
   }

   private StandPlanner.Target workFrom(BlockPos feet, long[] planned) {
      if ((this.level.getFluidState(feet).isEmpty() || this.level.getFluidState(feet).isStill()) && !Walker.nearLava(this.level, feet)) {
         double y = Walker.floorOr(this.level, feet);
         Vec3d eye = new Vec3d(feet.getX() + 0.5, y + 1.62, feet.getZ() + 0.5);
         Box body = new Box(feet.getX() + 0.2, y, feet.getZ() + 0.2, feet.getX() + 0.8, y + 1.8, feet.getZ() + 0.8);

         for (StandPlanner.Target target : this.targets) {
            BlockPos pos = target.pos();
            if (!(pos.toCenterPos().squaredDistanceTo(eye) > (this.reach + 0.9) * (this.reach + 0.9))
               && !(pos.toCenterPos().distanceTo(eye) > target.within())
               && (!body.intersects(new Box(pos)) || target.use() == StandPlanner.Use.PLACE && target.expected().getCollisionShape(this.level, pos).isEmpty())
               && this.placeableFrom(eye, target, planned)) {
               return target;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean placeableFrom(Vec3d eye, StandPlanner.Target target, long[] planned) {
      BlockPos pos = target.pos();
      switch (target.use()) {
         case WATERLOG:
         case BREAK:
         case CLICK:
            if (pos.toCenterPos().distanceTo(eye) > this.reach) {
               return false;
            } else {
               VoxelShape shape = this.level.getBlockState(pos).getOutlineShape(this.level, pos);
               if (shape.isEmpty()) {
                  return false;
               } else {
                  Box box = shape.getBoundingBox().offset(pos);

                  for (Direction face : Direction.values()) {
                     if (facesEye(box, face, eye) && this.seen(eye, pos, box, face, null, null)) {
                        return true;
                     }
                  }

                  return false;
               }
            }
         case NEAR:
            return pos.toCenterPos().distanceTo(eye) <= this.reach - 0.5 && this.support != null && this.support.possible(pos, eye);
         default:
            Direction look = target.use() == StandPlanner.Use.PLACE ? StateRules.lookFor(target.expected()) : null;
            Direction flat = target.use() == StandPlanner.Use.PLACE ? StateRules.flatLookFor(target.expected()) : null;

            for (Direction side : Direction.values()) {
               BlockPos support = pos.offset(side);
               BlockState state = this.level.getBlockState(support);
               if (!state.isReplaceable()) {
                  VoxelShape shape = state.getOutlineShape(this.level, support);
                  if (!shape.isEmpty()) {
                     Direction face = side.getOpposite();
                     Box box = shape.getBoundingBox().offset(support);
                     if (this.seen(eye, support, box, face, look, flat)
                        && (target.use() != StandPlanner.Use.PLACE || this.exact == null || this.exact.placeable(target.expected(), pos, eye))) {
                        return true;
                     }
                  }
               }
            }

            if (this.onPlanned(eye, target, planned)) {
               return true;
            } else if (target.use() != StandPlanner.Use.PLACE) {
               return false;
            } else if (StateRules.slabHalf(this.level.getBlockState(pos), target.expected())) {
               return this.exact != null && this.exact.placeable(target.expected(), pos, eye);
            } else {
               this.propped = this.support != null && this.support.behind(target.expected(), pos, eye);
               return this.propped;
            }
      }
   }

   private boolean onPlanned(Vec3d eye, StandPlanner.Target target, long[] planned) {
      if (planned.length == 0) {
         return false;
      }

      if (target.use() != StandPlanner.Use.PLACE || StateRules.lookFor(target.expected()) == null && StateRules.flatLookFor(target.expected()) == null) {
         BlockPos pos = target.pos();

         for (long key : planned) {
            BlockPos leaf = BlockPos.fromLong(key);
            if (leaf.getManhattanDistance(pos) == 1 && this.level.getBlockState(leaf).isReplaceable()) {
               Direction face = Direction.getFacing(pos.getX() - leaf.getX(), pos.getY() - leaf.getY(), pos.getZ() - leaf.getZ());
               Box box = new Box(leaf);
               if (facesEye(box, face, eye)) {
                  for (double[] point : FACE_POINTS) {
                     Vec3d aim = facePoint(box, face, point[0], point[1]);
                     if (!(aim.distanceTo(eye) > this.reach)) {
                        Vec3d shy = aim.subtract(aim.subtract(eye).normalize().multiply(0.05));
                        if (this.level.raycast(new RaycastContext(eye, shy, ShapeType.OUTLINE, FluidHandling.NONE, this.player)).getType()
                           == net.minecraft.util.hit.HitResult.Type.MISS) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static long[] withLeaf(long[] before, BlockPos leaf, boolean pillar) {
      long[] out = new long[Math.min(before.length + 1, 10)];
      int n = 0;

      for (int i = Math.max(0, before.length + 1 - 10); i < before.length; i++) {
         BlockPos old = BlockPos.fromLong(before[i]);
         if (!pillar || old.getX() != leaf.getX() || old.getZ() != leaf.getZ() || old.getY() >= leaf.getY()) {
            out[n++] = before[i];
         }
      }

      out[n++] = leaf.asLong();
      return n == out.length ? out : Arrays.copyOf(out, n);
   }

   private boolean seen(Vec3d eye, BlockPos block, Box box, Direction face, Direction look, Direction flat) {
      return this.seenFrom(eye, block, box, face, look, flat, FACE_POINTS, 0.2)
         ? true
         : look == Direction.UP && this.seenFrom(eye.subtract(0.0, 0.3500000000000001, 0.0), block, box, face, look, flat, EDGE_POINTS, 0.05);
   }

   private boolean seenFrom(Vec3d eye, BlockPos block, Box box, Direction face, Direction look, Direction flat, double[][] points, double margin) {
      if (!facesEye(box, face, eye)) {
         return false;
      }

      for (double[] point : points) {
         Vec3d aim = facePoint(box, face, point[0], point[1]);
         if (!(aim.distanceTo(eye) > this.reach)
            && (look == null || Walker.dominant(aim.subtract(eye), look, margin))
            && (flat == null || Walker.facingFlat(aim.subtract(eye), flat))) {
            BlockHitResult hit = this.level
               .raycast(new RaycastContext(eye, aim.add(aim.subtract(eye).normalize().multiply(0.05)), ShapeType.OUTLINE, FluidHandling.NONE, this.player));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(block) && hit.getSide() == face) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean facesEye(Box box, Direction face, Vec3d eye) {
      double gap = 0.05;

      return switch (face) {
         case UP -> eye.y > box.maxY + gap;
         case DOWN -> eye.y < box.minY - gap;
         case NORTH -> eye.z < box.minZ - gap;
         case SOUTH -> eye.z > box.maxZ + gap;
         case WEST -> eye.x < box.minX - gap;
         case EAST -> eye.x > box.maxX + gap;
      };
   }

   private static Vec3d facePoint(Box box, Direction face, double u, double v) {
      double x = box.minX + (box.maxX - box.minX) * u;
      double y = box.minY + (box.maxY - box.minY) * v;

      return switch (face) {
         case UP -> new Vec3d(x, box.maxY, box.minZ + (box.maxZ - box.minZ) * v);
         case DOWN -> new Vec3d(x, box.minY, box.minZ + (box.maxZ - box.minZ) * v);
         case NORTH -> new Vec3d(x, y, box.minZ);
         case SOUTH -> new Vec3d(x, y, box.maxZ);
         case WEST -> new Vec3d(box.minX, y, box.minZ + (box.maxZ - box.minZ) * u);
         case EAST -> new Vec3d(box.maxX, y, box.minZ + (box.maxZ - box.minZ) * u);
      };
   }

   @Environment(EnvType.CLIENT)
   interface Exact {
      boolean placeable(BlockState var1, BlockPos var2, Vec3d var3);
   }

   @Environment(EnvType.CLIENT)
   enum Move {
      WALK,
      PILLAR,
      BRIDGE,
      LEAP;
   }

   @Environment(EnvType.CLIENT)
   private record Node(long pos, double cost, double rank) {
   }

   @Environment(EnvType.CLIENT)
   interface Pillar {
      boolean possible(BlockPos var1, boolean var2);
   }

   @Environment(EnvType.CLIENT)
   enum Status {
      RUNNING,
      FOUND,
      NONE,
      APPROACH;
   }

   @Environment(EnvType.CLIENT)
   record Step(BlockPos pos, StandPlanner.Move move) {
   }

   @Environment(EnvType.CLIENT)
   interface Support {
      boolean possible(BlockPos var1, Vec3d var2);

      boolean behind(BlockState var1, BlockPos var2, Vec3d var3);
   }

   @Environment(EnvType.CLIENT)
   record Target(BlockPos pos, BlockState expected, StandPlanner.Use use, double within) {
      Target(BlockPos pos, BlockState expected, StandPlanner.Use use) {
         this(pos, expected, use, Double.MAX_VALUE);
      }
   }

   @Environment(EnvType.CLIENT)
   enum Use {
      PLACE,
      POUR,
      WATERLOG,
      NEAR,
      BREAK,
      CLICK;
   }
}
