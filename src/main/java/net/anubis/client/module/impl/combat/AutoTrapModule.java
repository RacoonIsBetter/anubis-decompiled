package net.anubis.client.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.impl.combat.melee.SlotReturn;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

@Environment(EnvType.CLIENT)
public final class AutoTrapModule extends ClientModule {
   private static final Direction[] SUPPORT_ORDER = new Direction[]{
      Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
   };
   private static final int TARGET_COLOR = -24512;
   private static final int CELL_COLOR = -2130706433;
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 4.0F, 1.0F, 6.0F).step(0.5F));
   private final BooleanSetting rotate = this.addSetting("rotate", new BooleanSetting("Rotate", true));
   private final NumberSetting perTick = this.addSetting("blocks-per-tick", new NumberSetting("Blocks Per Tick", 4.0F, 1.0F, 8.0F).step(1.0F));
   private final BooleanSetting top = this.addSetting("top-block", new BooleanSetting("Top Block", true));
   private final BooleanSetting show = this.addSetting("show-target", new BooleanSetting("Show Target", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final SlotReturn slot = new SlotReturn(this);
   private PlayerEntity target;
   private List<BlockPos> plan = List.of();
   private boolean warnedEmpty;

   public AutoTrapModule() {
      super("auto-trap", "Auto Trap", "Boxes the nearest enemy in with obsidian: sides at feet and head height, then the top.", ModuleCategory.COMBAT);
      this.perTick.visibleWhen(() -> !this.rotate.get());
   }

   @Override
   protected void onEnable() {
      this.target = null;
      this.plan = List.of();
      this.warnedEmpty = false;
      this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
      this.on(Render3DEvent.TYPE, Stage.POST, event -> this.render());
   }

   @Override
   protected void onDisable() {
      this.slot.drop(this.mc, true);
      this.target = null;
      this.plan = List.of();
   }

   private void tick() {
      ClientPlayerEntity player = this.mc.player;
      ClientWorld level = this.mc.world;
      if (player != null && level != null && this.mc.interactionManager != null && MeleeAttacks.canAct(this.mc)) {
         this.target = this.pick(player);
         this.plan = this.target == null ? List.of() : this.cells(level, player, this.target);
         if (this.plan.isEmpty()) {
            this.slot.tick(this.mc, true);
         } else {
            int obsidian = Crystals.hotbarSlotOf(player.getInventory(), Items.OBSIDIAN);
            if (obsidian < 0) {
               if (!this.warnedEmpty) {
                  ModuleChat.send(this, ModuleChat.body().warn("No obsidian in the hotbar"));
               }

               this.warnedEmpty = true;
               this.slot.tick(this.mc, true);
            } else {
               this.warnedEmpty = false;
               int budget = this.rotate.get() ? 1 : Math.round(this.perTick.get());
               int placed = 0;

               for (BlockPos cell : this.plan) {
                  if (placed < budget) {
                     BlockHitResult hit = support(level, player, cell);
                     if (hit == null) {
                        continue;
                     }

                     if (this.rotate.get()) {
                        BlockHitResult seen = seenByServer(level, player, hit);
                        if (seen == null) {
                           ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), hit.getPos()));
                           return;
                        }

                        hit = seen;
                     }

                     if (placed != 0 || !MeleeAttacks.actedThisTick(player) && MeleeAttacks.handsFree(this.mc, player)) {
                        if (!this.slot.take(player, obsidian)) {
                           return;
                        }

                        ActionResult result = this.mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                        if (result.isAccepted()) {
                           player.swingHand(Hand.MAIN_HAND);
                           MeleeAttacks.noteAction(player);
                           placed++;
                        }
                        continue;
                     }

                     return;
                  }
                  break;
               }

               if (this.rotate.get() && placed > 0) {
                  ServerRotation.request(ServerRotation.sent());
               }

               return;
            }
         }
      } else {
         this.slot.tick(this.mc, true);
      }
   }

   private PlayerEntity pick(ClientPlayerEntity player) {
      double reach = this.range.get() + 1.5;
      PlayerEntity best = null;

      for (PlayerEntity other : this.mc.world.getPlayers()) {
         if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other)) {
            double distance = other.distanceTo(player);
            if (distance <= reach && (best == null || distance < best.distanceTo(player))) {
               best = other;
            }
         }
      }

      return best;
   }

   private List<BlockPos> cells(ClientWorld level, ClientPlayerEntity player, PlayerEntity who) {
      Box box = who.getBoundingBox();
      int feet = MathHelper.floor(box.minY + 0.01);
      Set<Long> columns = new LinkedHashSet<>();

      for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX - 1.0E-4); x++) {
         for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ - 1.0E-4); z++) {
            columns.add(BlockPos.asLong(x, 0, z));
         }
      }

      List<BlockPos> out = new ArrayList<>();

      for (int dy = 0; dy <= 1; dy++) {
         List<BlockPos> ring = new ArrayList<>();

         for (long key : columns) {
            BlockPos column = BlockPos.fromLong(key).withY(feet + dy);

            for (Direction side : Type.HORIZONTAL) {
               BlockPos cell = column.offset(side);
               if (!columns.contains(BlockPos.asLong(cell.getX(), 0, cell.getZ())) && this.open(level, player, cell) && !ring.contains(cell)) {
                  ring.add(cell);
               }
            }
         }

         ring.sort(Comparator.comparingDouble(cellx -> cellx.getSquaredDistance(player.getEyePos())));
         out.addAll(ring);
      }

      if (this.top.get()) {
         for (long key : columns) {
            BlockPos cell = BlockPos.fromLong(key).withY(feet + 2);
            if (this.open(level, player, cell)) {
               out.add(cell);
            }
         }
      }

      return out;
   }

   private boolean open(ClientWorld level, ClientPlayerEntity player, BlockPos cell) {
      BlockState state = level.getBlockState(cell);
      if (state.isReplaceable() && (state.getFluidState().isEmpty() || state.getFluidState().isStill())) {
         return cell.toCenterPos().distanceTo(player.getEyePos()) > player.getBlockInteractionRange()
            ? false
            : level.canPlace(Blocks.OBSIDIAN.getDefaultState(), cell, ShapeContext.absent());
      } else {
         return false;
      }
   }

   public static BlockHitResult support(ClientWorld level, ClientPlayerEntity player, BlockPos cell) {
      Vec3d eye = player.getEyePos();
      double reach = player.getBlockInteractionRange();

      for (Direction toward : SUPPORT_ORDER) {
         BlockPos against = cell.offset(toward);
         BlockState state = level.getBlockState(against);
         if (!state.isReplaceable() && !state.getCollisionShape(level, against).isEmpty()) {
            Direction face = toward.getOpposite();
            Vec3d point = Vec3d.ofCenter(against).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (!(point.distanceTo(eye) > reach)) {
               BlockHitResult ray = level.raycast(
                  new RaycastContext(eye, point.add(Vec3d.of(toward.getVector()).multiply(0.05)), ShapeType.OUTLINE, FluidHandling.NONE, player)
               );
               if (ray.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && ray.getBlockPos().equals(against) && ray.getSide() == face) {
                  return new BlockHitResult(point, face, against, false);
               }
            }
         }
      }

      return null;
   }

   public static BlockHitResult seenByServer(ClientWorld level, ClientPlayerEntity player, BlockHitResult wanted) {
      Rotation sent = ServerRotation.sent();
      if (sent == null) {
         return null;
      }

      Vec3d eye = player.getEyePos();
      Vec3d end = eye.add(Vec3d.fromPolar(sent.pitch(), sent.yaw()).multiply(player.getBlockInteractionRange()));
      BlockHitResult ray = level.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
      return ray.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            && ray.getBlockPos().equals(wanted.getBlockPos())
            && ray.getSide() == wanted.getSide()
         ? ray
         : null;
   }

   private void render() {
      if (this.show.get() && this.target != null) {
         GizmoDrawing.box(this.target.getBoundingBox(), DrawStyle.stroked(-24512, 2.0F));

         for (BlockPos cell : this.plan) {
            GizmoDrawing.box(cell, DrawStyle.stroked(-2130706433, 1.0F));
         }
      }
   }
}
