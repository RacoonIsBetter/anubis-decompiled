package net.anubis.client.module.impl.movement;

import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class ClickTpModule extends ClientModule {
   private static final double RAYCAST_DISTANCE = 210.0;
   private static final double BLOCKS_PER_PACKET = 10.0;
   private static final int MAX_PADDING_PACKETS = 19;

   public ClickTpModule() {
      super("click-tp", "Click TP", "Teleports you to the block you click on.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   private void onPostTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         if (player.getInventory().getSelectedStack().getUseAction() == UseAction.NONE) {
            if (mc.options.useKey.isPressed()) {
               Camera camera = mc.gameRenderer.getCamera();
               Vec3d cameraPos = camera.getCameraPos();
               Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
               FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
               boolean freecamOn = freecam != null && freecam.isEnabled();
               HitResult crosshair = freecamOn ? pickFromCamera(player, level, cameraPos, direction) : mc.crosshairTarget;
               if (crosshair != null) {
                  if (crosshair.getType() == Type.ENTITY
                     && player.interact(((EntityHitResult)crosshair).getEntity(), Hand.MAIN_HAND) != ActionResult.PASS) {
                     return;
                  }

                  if (crosshair.getType() == Type.BLOCK && player.getMainHandStack().getItem() instanceof BlockItem) {
                     return;
                  }
               }

               BlockHitResult hit = level.raycast(
                  new RaycastContext(cameraPos, cameraPos.add(direction.multiply(210.0)), ShapeType.OUTLINE, FluidHandling.NONE, player)
               );
               if (hit.getType() == Type.BLOCK) {
                  if (hit.isInsideBlock() && !freecamOn) {
                     FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
                     if (freelook != null && freelook.isEnabled()) {
                        return;
                     }
                  }

                  BlockPos pos = hit.getBlockPos();
                  Direction side = hit.getSide();
                  if (level.getBlockState(pos).onUse(level, player, hit) == ActionResult.PASS) {
                     BlockState state = level.getBlockState(pos);
                     VoxelShape shape = state.getCollisionShape(level, pos);
                     if (shape.isEmpty()) {
                        shape = state.getOutlineShape(level, pos);
                     }

                     double height = shape.isEmpty() ? 1.0 : shape.getMax(Axis.Y);
                     Vec3d newPos = new Vec3d(
                        pos.getX() + 0.5 + side.getOffsetX(), pos.getY() + height, pos.getZ() + 0.5 + side.getOffsetZ()
                     );
                     int packetsRequired = (int)Math.ceil(player.getEntityPos().distanceTo(newPos) / 10.0) - 1;
                     if (packetsRequired > 19) {
                        packetsRequired = 0;
                     }

                     for (int i = 0; i < packetsRequired; i++) {
                        player.networkHandler.sendPacket(new OnGroundOnly(true, true));
                     }

                     player.networkHandler.sendPacket(new PositionAndOnGround(newPos.x, newPos.y, newPos.z, true, true));
                     player.setPosition(newPos);
                  }
               }
            }
         }
      }
   }

   private static HitResult pickFromCamera(ClientPlayerEntity player, ClientWorld level, Vec3d eye, Vec3d dir) {
      double blockRange = player.getBlockInteractionRange();
      double entityRange = player.getEntityInteractionRange();
      double reach = Math.max(blockRange, entityRange);
      double reachSq = reach * reach;
      BlockHitResult blockHit = level.raycast(
         new RaycastContext(eye, eye.add(dir.multiply(reach)), ShapeType.OUTLINE, FluidHandling.NONE, player)
      );
      double blockDistSq = blockHit.getPos().squaredDistanceTo(eye);
      if (blockHit.getType() != Type.MISS) {
         reachSq = blockDistSq;
         reach = Math.sqrt(blockDistSq);
      }

      Vec3d end = eye.add(dir.multiply(reach));
      Box searchBox = new Box(eye, end).expand(1.0);
      EntityHitResult entityHit = ProjectileUtil.raycast(player, eye, end, searchBox, EntityPredicates.CAN_HIT, reachSq);
      if (entityHit != null && entityHit.getPos().squaredDistanceTo(eye) < blockDistSq) {
         return entityHit.getPos().isInRange(eye, entityRange) ? entityHit : null;
      } else {
         return blockHit.getPos().isInRange(eye, blockRange) ? blockHit : null;
      }
   }
}
