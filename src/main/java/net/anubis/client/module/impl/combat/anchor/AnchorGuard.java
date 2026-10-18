package net.anubis.client.module.impl.combat.anchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult.Success;
import net.minecraft.util.ActionResult.SwingSource;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;


import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class AnchorGuard {
   public static final float BLAST_POWER = 5.0F;
   private static final double MIN_SHADE = 0.15;
   private static final double FACE_INSET = 0.3;
   private static final double AIM_INSET = 0.1;

   private AnchorGuard() {
   }

   public static AnchorGuard.Spot find(ClientWorld level, ClientPlayerEntity player, BlockPos anchor, Vec3d sightline) {
      Vec3d blast = Vec3d.ofCenter(anchor);
      Box body = player.getBoundingBox();
      List<Vec3d> points = bodyPoints(body);
      List<Vec3d> open = new ArrayList<>(points.size());

      for (Vec3d point : points) {
         BlockHitResult hit = level.raycast(new RaycastContext(point, blast, ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS || hit.getBlockPos().equals(anchor)) {
            open.add(point);
         }
      }

      BlockPos feet = player.getBlockPos();
      if (open.size() < Math.ceil(points.size() * 0.15)) {
         return new AnchorGuard.Spot(feet, null, null, null);
      }

      int needed = (int)Math.ceil(open.size() * 0.15);
      Vec3d eye = player.getEyePos();
      Set<BlockPos> around = new LinkedHashSet<>();

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               around.add(feet.add(dx, 0, dz));
            }
         }
      }

      for (Direction side : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         around.add(anchor.offset(side));
      }

      List<AnchorGuard.Cell> cells = new ArrayList<>(around.size());

      for (BlockPos pos : around) {
         Box box = new Box(pos);
         if (!pos.equals(anchor) && !box.intersects(body) && !box.raycast(eye, sightline).isPresent()) {
            int shade = shade(box, blast, open);
            if (shade >= needed) {
               cells.add(new AnchorGuard.Cell(pos, shade, box.getCenter().squaredDistanceTo(blast)));
            }
         }
      }

      cells.sort(Comparator.comparingInt(AnchorGuard.Cell::shade).reversed().thenComparingDouble(AnchorGuard.Cell::distance));

      for (AnchorGuard.Cell cell : cells) {
         BlockState state = level.getBlockState(cell.pos());
         if (state.isReplaceable()) {
            AnchorGuard.Spot spot = state.getOutlineShape(level, cell.pos()).isEmpty()
               ? supportFor(level, player, eye, cell.pos())
               : direct(level, player, eye, cell.pos(), state);
            if (spot != null) {
               return spot;
            }
         }
      }

      return null;
   }

   public static BlockHitResult hitAlong(ClientWorld level, ClientPlayerEntity player, Rotation look, AnchorGuard.Spot spot) {
      BlockHitResult hit = trace(level, player, look);
      if (hit == null || !hit.getBlockPos().equals(spot.support())) {
         return null;
      }

      if (spot.face() != null && hit.getSide() != spot.face()) {
         return null;
      }

      BlockPos placed = Crystals.placedObsidian(level, player, hit, level.getBlockState(spot.support()));
      return spot.pos().equals(placed) ? hit : null;
   }

   public static BlockHitResult anchorHit(ClientWorld level, ClientPlayerEntity player, BlockPos anchor) {
      Rotation sent = ServerRotation.sent();
      BlockHitResult hit = sent == null ? null : trace(level, player, sent);
      return hit != null && hit.getBlockPos().equals(anchor) ? hit : null;
   }

   public static boolean isGuardBlock(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem item) {
         Block block = item.getBlock();
         return !(block instanceof BlockEntityProvider) && !(block instanceof FallingBlock) && block != Blocks.RESPAWN_ANCHOR && block != Blocks.TNT
            ? block.getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
            : false;
      } else {
         return false;
      }
   }

   public static int detonatorSlot(PlayerInventory inventory) {
      int totem = Crystals.hotbarSlotOf(inventory, Items.TOTEM_OF_UNDYING);
      if (totem != -1) {
         return totem;
      }

      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         if (Crystals.isCombatItem(inventory.getStack(slot))) {
            return slot;
         }
      }

      return -1;
   }

   public static int detonatorRoute(PlayerInventory inventory, ItemStack main) {
      if (Crystals.isCombatItem(main)) {
         return -2;
      }

      int slot = detonatorSlot(inventory);
      return slot == -1 ? -1 : slot;
   }

   public static Vec3d anchorAim(ClientWorld level, ClientPlayerEntity player, BlockPos anchor) {
      Vec3d eye = player.getEyePos();
      Box box = new Box(anchor).contract(0.1);
      Vec3d point = new Vec3d(
         MathHelper.clamp(eye.x, box.minX, box.maxX),
         MathHelper.clamp(eye.y, box.minY, box.maxY),
         MathHelper.clamp(eye.z, box.minZ, box.maxZ)
      );
      BlockHitResult hit = trace(level, player, ServerRotation.next(player, RotationUtils.fromTo(eye, point)));
      return hit != null && hit.getBlockPos().equals(anchor) ? point : null;
   }

   public static void click(MinecraftClient mc, ClientPlayerEntity player, BlockHitResult hit) {
      ItemStack held = player.getMainHandStack();
      int countBefore = held.getCount();
      if (mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit) instanceof Success success && success.swingSource() == SwingSource.CLIENT) {
         player.swingHand(Hand.MAIN_HAND);
         if (!held.isEmpty() && (held.getCount() != countBefore || player.isInCreativeMode())) {
            mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND);
         }
      }

      MeleeAttacks.claimTick(mc);
   }

   private static AnchorGuard.Spot direct(ClientWorld level, ClientPlayerEntity player, Vec3d eye, BlockPos pos, BlockState state) {
      AnchorGuard.Spot spot = new AnchorGuard.Spot(pos, pos, null, state.getOutlineShape(level, pos).getBoundingBox().offset(pos).getCenter());
      Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, spot.aim()));
      return hitAlong(level, player, look, spot) != null ? spot : null;
   }

   public static AnchorGuard.Spot supportFor(ClientWorld level, ClientPlayerEntity player, Vec3d eye, BlockPos cell) {
      AnchorGuard.Spot best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos support = cell.offset(side);
         BlockState state = level.getBlockState(support);
         if (!state.isReplaceable()) {
            VoxelShape shape = state.getOutlineShape(level, support);
            if (!shape.isEmpty()) {
               Direction face = side.getOpposite();
               AnchorGuard.Spot spot = null;

               for (Vec3d aim : faceAims(shape.getBoundingBox().offset(support), face)) {
                  AnchorGuard.Spot candidate = new AnchorGuard.Spot(cell, support, face, aim);
                  Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, aim));
                  if (hitAlong(level, player, look, candidate) != null) {
                     spot = candidate;
                     break;
                  }
               }

               if (spot != null) {
                  double distance = side == Direction.DOWN ? -1.0 : eye.squaredDistanceTo(spot.aim());
                  if (distance < bestDistance) {
                     best = spot;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private static List<Vec3d> faceAims(Box box, Direction face) {
      Axis axis = face.getAxis();
      double plane = face.getDirection() == AxisDirection.POSITIVE ? box.getMax(axis) : box.getMin(axis);
      Vec3d center = box.getCenter().withAxis(axis, plane);
      List<Vec3d> aims = new ArrayList<>(5);
      aims.add(center);

      for (Direction toward : Direction.values()) {
         Axis along = toward.getAxis();
         if (along != axis) {
            aims.add(center.offset(toward, (box.getMax(along) - box.getMin(along)) * 0.3));
         }
      }

      return aims;
   }

   public static BlockHitResult trace(ClientWorld level, ClientPlayerEntity player, Rotation look) {
      Vec3d eye = player.getEyePos();
      Vec3d reach = Vec3d.fromPolar(look.pitch(), look.yaw()).multiply(player.getBlockInteractionRange());
      BlockHitResult hit = level.raycast(new RaycastContext(eye, eye.add(reach), ShapeType.OUTLINE, FluidHandling.NONE, player));
      return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK ? hit : null;
   }

   private static int shade(Box box, Vec3d blast, List<Vec3d> points) {
      int blocked = 0;

      for (Vec3d point : points) {
         if (box.raycast(point, blast).isPresent()) {
            blocked++;
         }
      }

      return blocked;
   }

   private static List<Vec3d> bodyPoints(Box body) {
      double stepX = 1.0 / ((body.maxX - body.minX) * 2.0 + 1.0);
      double stepY = 1.0 / ((body.maxY - body.minY) * 2.0 + 1.0);
      double stepZ = 1.0 / ((body.maxZ - body.minZ) * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
      List<Vec3d> points = new ArrayList<>();

      for (double fx = 0.0; fx <= 1.0; fx += stepX) {
         for (double fy = 0.0; fy <= 1.0; fy += stepY) {
            for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
               points.add(
                  new Vec3d(
                     MathHelper.lerp(fx, body.minX, body.maxX) + offsetX,
                     MathHelper.lerp(fy, body.minY, body.maxY),
                     MathHelper.lerp(fz, body.minZ, body.maxZ) + offsetZ
                  )
               );
            }
         }
      }

      return points;
   }

   @Environment(EnvType.CLIENT)
   private record Cell(BlockPos pos, int shade, double distance) {
   }

   @Environment(EnvType.CLIENT)
   public record Spot(BlockPos pos, BlockPos support, Direction face, Vec3d aim) {
      public boolean standing() {
         return this.support == null;
      }
   }
}
