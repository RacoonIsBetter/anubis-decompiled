package net.anubis.client.module.impl.movement;

import java.util.ArrayList;
import java.util.List;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.anubis.client.module.impl.movement.entitycontrol.VehicleAntiKick;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.packet.c2s.play.BoatPaddleStateC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

@Environment(EnvType.CLIENT)
public final class BoatNoclipModule extends ClientModule {
   public static volatile BoatNoclipModule active;
   private static final double MAX_MISS = 0.24;
   private static final double MAX_PACKET_LENGTH = 1.0;
   private static final int MAX_PACKETS_PER_TICK = 16;
   private static final double MAX_SERVER_LEAD = 19.0;
   private static final double MAX_UNCONFIRMED = 28.5;
   private static final int STALE_SERVER_TICKS = 40;
   private static final double MIN_BURST = 2.0;
   private static final double MOUNT_BURST = 4.0;
   private static final double BURST_RECOVERY = 0.025;
   private static final double CAUTION_STEP = 0.5;
   private static final double CAUTION_GROWTH = 1.25;
   private static final double LAVA_MARGIN = 3.0;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 10.0F, 0.0F, 320.0F).step(0.5F));
   private final NumberSetting speedInsideBlocks = this.addSetting(
      "speed-inside-blocks", new NumberSetting("Speed Inside Blocks", 5.0F, 0.0F, 320.0F).step(0.5F)
   );
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical Speed", 6.0F, 0.0F, 320.0F).step(0.5F));
   private final NumberSetting fallSpeed = this.addSetting("fall-speed", new NumberSetting("Fall Speed", 0.0F, 0.0F, 10.0F).step(0.1F));
   private final BooleanSetting lavaProtection = this.addSetting("lava-protection", new BooleanSetting("Lava Protection", true));
   private final BooleanSetting antiKick = this.addSetting("anti-fly-kick", new BooleanSetting("Anti Fly Kick", true));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 40.0F, 1.0F, 80.0F).step(1.0F));
   private final VehicleAntiKick antiKicker = new VehicleAntiKick();
   private AbstractBoatEntity noclipBoat;
   private Vec3d serverPos;
   private Vec3d lastBase;
   private int staleTicks;
   private double cautionCap = Double.POSITIVE_INFINITY;
   private boolean sendingStops;
   private int sinceBase;
   private double burst = 4.0;
   private double lastMoveLength;

   public BoatNoclipModule() {
      super("boat-noclip", "Boat Noclip", "Fly your boat through blocks and entities.", ModuleCategory.MOVEMENT);
      this.delay.visibleWhen(this.antiKick::get);
      this.antiKick.onChange(this.antiKicker::forget);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      BoatFlyModule fly = ModuleRegistry.get(BoatFlyModule.class);
      if (fly != null) {
         fly.setEnabled(false);
      }

      this.antiKicker.start(this.delay.getInt());
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
      if (FreecamModule.active == null) {
         MinecraftClient.getInstance().chunkCullingEnabled = true;
      }

      this.antiKicker.stop();
      this.releaseBoat();
   }

   private void onPreTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (this.noclipBoat != null && (player == null || player.getVehicle() != this.noclipBoat)) {
         this.releaseBoat();
      }

      this.antiKicker.tick();
      this.updateCulling(mc);
   }

   private void updateCulling(MinecraftClient mc) {
      if (FreecamModule.active == null && mc.world != null) {
         boolean sealed = this.noclipBoat != null && mc.world.getBlockState(mc.gameRenderer.getCamera().getBlockPos()).isOpaqueFullCube();
         mc.chunkCullingEnabled = !sealed;
      }
   }

   private void onPacketSend(PacketSendEvent event) {
      if (this.antiKick.get() && event.packet instanceof VehicleMoveC2SPacket packet) {
         if (this.sendingStops) {
            this.antiKicker.observe(packet);
         } else if (this.antiKicker.onPacketSend(packet, this.delay.getInt())) {
            event.setCancelled(true);
         }
      }
   }

   public void onVehicleSetback() {
      this.antiKicker.forget();
      if (this.noclipBoat != null) {
         this.serverPos = this.noclipBoat.getEntityPos();
      }

      this.cautionCap = 0.5;
   }

   private void releaseBoat() {
      if (this.noclipBoat != null) {
         this.noclipBoat.noClip = false;
      }

      this.noclipBoat = null;
      this.serverPos = null;
      this.lastBase = null;
      this.sinceBase = 0;
      this.burst = 4.0;
   }

   public Vec3d boatMovement(AbstractBoatEntity boat, ClientPlayerEntity player) {
      if (this.noclipBoat != boat) {
         this.releaseBoat();
         this.noclipBoat = boat;
      }

      boat.noClip = true;
      forceServerBroadcast(boat);
      double budget = this.serverBudget(boat);
      if (this.antiKick.get() && this.antiKicker.isHolding()) {
         return Vec3d.ZERO;
      }

      budget = Math.min(budget, this.cautionCap);
      this.cautionCap *= 1.25;
      double bps = isInsideBlock(boat, player) ? this.speedInsideBlocks.get() : this.speed.get();
      Vec3d horizontal = EntityControlVehicleHook.horizontalVelocity(player, bps);
      MinecraftClient mc = MinecraftClient.getInstance();
      double velY = 0.0;
      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.jumpKey)) {
         velY += this.verticalSpeed.get() / 20.0;
      }

      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.sprintKey)) {
         velY -= this.verticalSpeed.get() / 20.0;
      } else {
         velY -= this.fallSpeed.get() / 20.0;
      }

      Vec3d move = new Vec3d(horizontal.x, velY, horizontal.z);
      double length = move.length();
      if (length > budget) {
         move = move.multiply(budget / length);
      }

      Vec3d planned = this.planMove(boat, move);
      this.lastMoveLength = planned.length();
      return planned;
   }

   private static void forceServerBroadcast(AbstractBoatEntity boat) {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection != null) {
         connection.sendPacket(new BoatPaddleStateC2SPacket(!boat.isPaddleMoving(0), boat.isPaddleMoving(1)));
      }
   }

   private double serverBudget(AbstractBoatEntity boat) {
      Vec3d base = boat.getTrackedPosition().getPos();
      if (!base.equals(this.lastBase)) {
         this.burst = Math.max(this.burst, this.sinceBase + 1);
         this.sinceBase = 0;
         this.lastBase = base;
         this.serverPos = base;
         this.staleTicks = 0;
      } else {
         if (this.lastMoveLength > 0.01) {
            this.sinceBase++;
         }

         if (++this.staleTicks >= 40) {
            this.serverPos = boat.getEntityPos();
         }
      }

      this.burst = Math.max(2.0, this.burst - 0.025);
      if (this.serverPos == null) {
         this.serverPos = boat.getEntityPos();
      }

      double lead = boat.getEntityPos().distanceTo(this.serverPos);
      return Math.max(0.0, Math.min(19.0 / this.burst, 28.5 - lead));
   }

   private Vec3d planMove(AbstractBoatEntity boat, Vec3d move) {
      Vec3d start = boat.getEntityPos();
      Vec3d pos = start;
      Vec3d left = move;
      List<Box> lava = this.lavaProtection.get() ? nearbyLava(boat, move) : List.of();
      List<Vec3d> stops = new ArrayList<>();

      while (stops.size() < 16 && left.lengthSquared() > 1.0E-8) {
         Vec3d hop = left.multiply(Math.min(1.0, 1.0 / left.length()));
         double fraction = allowedFraction(boat, pos, hop);
         if (fraction <= 0.0) {
            break;
         }

         Vec3d step = hop.multiply(fraction);
         if (reachesUnloadedChunk(boat, pos, step) || !lava.isEmpty() && approachesLava(boat, pos, step, lava)) {
            break;
         }

         pos = pos.add(step);
         left = left.subtract(step);
         stops.add(pos);
      }

      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection != null) {
         this.sendingStops = true;

         try {
            for (int i = 0; i < stops.size() - 1; i++) {
               connection.sendPacket(new VehicleMoveC2SPacket(stops.get(i), boat.getYaw(), boat.getPitch(), false));
            }
         } finally {
            this.sendingStops = false;
         }
      }

      return pos.subtract(start);
   }

   private static List<Box> nearbyLava(AbstractBoatEntity boat, Vec3d move) {
      Box area = boat.getBoundingBox().stretch(move).expand(4.0);
      List<Box> lava = new ArrayList<>();

      for (BlockPos pos : BlockPos.iterate(
         MathHelper.floor(area.minX),
         MathHelper.floor(area.minY),
         MathHelper.floor(area.minZ),
         MathHelper.floor(area.maxX),
         MathHelper.floor(area.maxY),
         MathHelper.floor(area.maxZ)
      )) {
         if (boat.getEntityWorld().getFluidState(pos).isIn(FluidTags.LAVA)) {
            lava.add(new Box(pos));
         }
      }

      return lava;
   }

   private static boolean approachesLava(AbstractBoatEntity boat, Vec3d from, Vec3d step, List<Box> lava) {
      Box box = boat.getBoundingBox().offset(from.subtract(boat.getEntityPos()));
      double after = distanceTo(box.offset(step), lava);
      return after < 3.0 && after < distanceTo(box, lava);
   }

   private static double distanceTo(Box box, List<Box> blocks) {
      double best = Double.MAX_VALUE;

      for (Box block : blocks) {
         double dx = Math.max(0.0, Math.max(block.minX - box.maxX, box.minX - block.maxX));
         double dy = Math.max(0.0, Math.max(block.minY - box.maxY, box.minY - block.maxY));
         double dz = Math.max(0.0, Math.max(block.minZ - box.maxZ, box.minZ - block.maxZ));
         best = Math.min(best, dx * dx + dy * dy + dz * dz);
      }

      return Math.sqrt(best);
   }

   private static boolean reachesUnloadedChunk(AbstractBoatEntity boat, Vec3d from, Vec3d step) {
      Box box = boat.getBoundingBox().offset(from.subtract(boat.getEntityPos())).stretch(step);

      for (int x = ChunkSectionPos.getSectionCoordFloored(box.minX); x <= ChunkSectionPos.getSectionCoordFloored(box.maxX); x++) {
         for (int z = ChunkSectionPos.getSectionCoordFloored(box.minZ); z <= ChunkSectionPos.getSectionCoordFloored(box.maxZ); z++) {
            if (!boat.getEntityWorld().getChunkManager().isChunkLoaded(x, z)) {
               return true;
            }
         }
      }

      return false;
   }

   private static double allowedFraction(AbstractBoatEntity boat, Vec3d from, Vec3d move) {
      Box box = boat.getBoundingBox().offset(from.subtract(boat.getEntityPos()));
      List<VoxelShape> entities = serverEntityShapes(boat, box.stretch(move));
      if (replayMiss(boat, box, move, entities) <= 0.24) {
         return 1.0;
      }

      double lo = 0.0;
      double hi = 1.0;

      for (int i = 0; i < 12; i++) {
         double mid = (lo + hi) / 2.0;
         if (replayMiss(boat, box, move.multiply(mid), entities) <= 0.24) {
            lo = mid;
         } else {
            hi = mid;
         }
      }

      return lo;
   }

   private static double replayMiss(AbstractBoatEntity boat, Box box, Vec3d move, List<VoxelShape> entities) {
      Vec3d replay = Entity.adjustMovementForCollisions(boat, move, box, boat.getEntityWorld(), entities);
      return Math.hypot(move.x - replay.x, move.z - replay.z);
   }

   private static List<VoxelShape> serverEntityShapes(AbstractBoatEntity boat, Box area) {
      List<VoxelShape> shapes = new ArrayList<>();

      for (Entity other : boat.getEntityWorld().getOtherEntities(boat, area.expand(1.0E-7), EntityPredicates.EXCEPT_SPECTATOR)) {
         if (AbstractBoatEntity.canCollide(boat, other)) {
            shapes.add(VoxelShapes.cuboid(other.getBoundingBox()));
         }
      }

      return shapes;
   }

   public static boolean hidesWallOverlay(PlayerEntity player) {
      return active != null && player.getVehicle() instanceof AbstractBoatEntity;
   }

   private static boolean isInsideBlock(AbstractBoatEntity boat, ClientPlayerEntity player) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return false;
      }

      Box boatBox = boat.getBoundingBox();
      Box box = boatBox.shrink(0.0, 0.05, 0.0).expand(0.5, 0.0, 0.5);
      int maxX = MathHelper.floor(box.maxX);
      int maxY = MathHelper.floor(box.maxY);
      int maxZ = MathHelper.floor(box.maxZ);
      Mutable pos = new Mutable();

      for (int x = MathHelper.floor(box.minX); x <= maxX; x++) {
         for (int y = MathHelper.floor(box.minY); y <= maxY; y++) {
            for (int z = MathHelper.floor(box.minZ); z <= maxZ; z++) {
               BlockState state = level.getBlockState(pos.set(x, y, z));
               if (!state.isAir()) {
                  VoxelShape shape = state.getCollisionShape(level, pos, ShapeContext.absent());
                  if (!shape.isEmpty() && boatBox.intersects(shape.getBoundingBox().offset(pos))) {
                     return true;
                  }
               }
            }
         }
      }

      for (Entity other : level.getOtherEntities(boat, box)) {
         if (other != player && !other.hasPassenger(boat) && !boat.hasPassenger(other) && other.isAlive() && other.getBoundingBox().intersects(box)) {
            return true;
         }
      }

      return false;
   }
}
