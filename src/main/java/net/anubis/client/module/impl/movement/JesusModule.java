package net.anubis.client.module.impl.movement;

import com.example.client.mixin.AccessorJesusServerboundMovePlayerPacket;
import com.example.client.mixin.AccessorServerboundMovePlayerPacket;
import java.util.Iterator;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.PlayerMoveEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.world.CollisionView;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class JesusModule extends ClientModule {
   public static volatile JesusModule active;
   private static final String SOLID = "Solid";
   private static final String IGNORE = "Ignore";
   private static final double ASCEND_SPEED = 0.11;
   private static final double NCP_STRAFE_SPEED = 0.2873;
   private static final int NCP_BOB_INTERVAL = 15;
   private static final int OVER_WATER = 1;
   private static final int OVER_LAVA = 2;
   private final BooleanSetting powderSnow = this.addSetting("powder-snow", new BooleanSetting("Powder Snow", true));
   private final BooleanSetting ncpBypass = this.addSetting("ncp-bypass", new BooleanSetting("NCP Bypass", false));
   private final BooleanSetting slowDown = this.addSetting("slow-down", new BooleanSetting("Slow Down", false));
   private final ModeSetting waterMode = this.addSetting("water-mode", new ModeSetting("Water Mode", "Solid", "Solid", "Ignore"));
   private final BooleanSetting dipIfBurning = this.addSetting("dip-if-burning", new BooleanSetting("Dip If Burning", true));
   private final BooleanSetting dipOnSneakWater = this.addSetting("water-dip-on-sneak", new BooleanSetting("Water Dip On Sneak", true));
   private final BooleanSetting dipOnFallWater = this.addSetting("water-dip-on-fall", new BooleanSetting("Water Dip On Fall", true));
   private final NumberSetting dipFallHeightWater = this.addSetting(
      "water-dip-fall-height", new NumberSetting("Water Dip Fall Height", 4.0F, 1.0F, 255.0F).step(1.0F)
   );
   private final ModeSetting lavaMode = this.addSetting("lava-mode", new ModeSetting("Lava Mode", "Solid", "Solid", "Ignore"));
   private final BooleanSetting dipIfFireResistant = this.addSetting("dip-if-resistant", new BooleanSetting("Dip If Resistant", true));
   private final BooleanSetting dipOnSneakLava = this.addSetting("lava-dip-on-sneak", new BooleanSetting("Lava Dip On Sneak", true));
   private final BooleanSetting dipOnFallLava = this.addSetting("lava-dip-on-fall", new BooleanSetting("Lava Dip On Fall", true));
   private final NumberSetting dipFallHeightLava = this.addSetting(
      "lava-dip-fall-height", new NumberSetting("Lava Dip Fall Height", 4.0F, 1.0F, 255.0F).step(1.0F)
   );
   private int ascending = 10;
   private int swimmingTicks;
   private boolean inBubbleColumn;
   private boolean moveIsVelocity;

   public JesusModule() {
      super("jesus", "Jesus", "Walk on liquids and powder snow like Jesus.", ModuleCategory.MOVEMENT);
      this.slowDown.visibleWhen(this.ncpBypass::get);
      this.dipIfBurning.visibleWhen(() -> this.waterMode.is("Solid"));
      this.dipOnSneakWater.visibleWhen(() -> this.waterMode.is("Solid"));
      this.dipOnFallWater.visibleWhen(() -> this.waterMode.is("Solid"));
      this.dipFallHeightWater.visibleWhen(() -> this.waterMode.is("Solid") && this.dipOnFallWater.get());
      this.dipIfFireResistant.visibleWhen(() -> this.lavaMode.is("Solid"));
      this.dipOnSneakLava.visibleWhen(() -> this.lavaMode.is("Solid"));
      this.dipOnFallLava.visibleWhen(() -> this.lavaMode.is("Solid"));
      this.dipFallHeightLava.visibleWhen(() -> this.lavaMode.is("Solid") && this.dipOnFallLava.get());
   }

   @Override
   protected void onEnable() {
      this.swimmingTicks = 0;
      this.inBubbleColumn = false;
      this.moveIsVelocity = false;
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
      this.on(PlayerMoveEvent.TYPE, Stage.PRE, EventPriority.HIGHEST, this::captureMoveAliasing);
      this.on(PlayerMoveEvent.TYPE, this::onMove);
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
      this.inBubbleColumn = false;
      this.moveIsVelocity = false;
      this.swimmingTicks = 0;
   }

   private void onPostTick(TickEvent event) {
      boolean bubbleColumn = this.inBubbleColumn;
      this.inBubbleColumn = false;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         if (!player.isInSwimmingPose()) {
            if (!player.isTouchingWater() || this.waterShouldBeSolid(player)) {
               if (!player.isInLava() || this.lavaShouldBeSolid(player)) {
                  Entity moving = (Entity)(player.hasVehicle() ? player.getVehicle() : player);
                  if (moving != null) {
                     if (bubbleColumn) {
                        if (mc.options.jumpKey.isPressed() && moving.getVelocity().y < 0.11) {
                           setVelocityY(moving, 0.11);
                        }
                     } else if (!moving.isTouchingWater() && !moving.isInLava()) {
                        BlockState below = mc.world.getBlockState(moving.getBlockPos().down());
                        boolean waterlogged = (Boolean)below.get(Properties.WATERLOGGED, false);
                        if (this.ascending == 0) {
                           setVelocityY(moving, 0.11);
                        } else if (this.ascending == 1
                           && (below.isOf(Blocks.WATER) || below.isOf(Blocks.LAVA) || waterlogged)) {
                           setVelocityY(moving, 0.0);
                        }

                        this.ascending++;
                     } else {
                        setVelocityY(moving, 0.11);
                        this.ascending = 0;
                     }
                  }
               }
            }
         }
      }
   }

   private static void setVelocityY(Entity entity, double y) {
      Vec3d v = entity.getVelocity();
      entity.setVelocity(v.x, y, v.z);
   }

   private void captureMoveAliasing(PlayerMoveEvent event) {
      this.moveIsVelocity = false;
      if (this.ncpBypass.get()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            Vec3d v = player.getVelocity();
            this.moveIsVelocity = v.x == event.x && v.y == event.y && v.z == event.z;
         }
      }
   }

   private void onMove(PlayerMoveEvent event) {
      if (this.ncpBypass.get()) {
         boolean aliased = this.moveIsVelocity;
         this.moveIsVelocity = false;
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.world != null) {
            int over = overLiquid(player);
            boolean water = (over & 1) != 0 && this.waterShouldBeSolid(player);
            boolean lava = (over & 2) != 0 && this.lavaShouldBeSolid(player);
            if (!water && !lava) {
               this.swimmingTicks = 0;
            } else if (++this.swimmingTicks < 15) {
               if (player.isOnGround()) {
                  applyStrafe(mc, player, event, isMoving(player) ? 0.2873 : 0.0);
                  if (aliased) {
                     setVelocityXZ(player, event.x, event.z);
                  }
               }
            } else {
               this.swimmingTicks = 0;
               if (this.slowDown.get()) {
                  event.x = 0.0;
                  event.z = 0.0;
                  if (aliased) {
                     setVelocityXZ(player, 0.0, 0.0);
                  }
               }

               setVelocityY(player, 0.08);
               if (aliased) {
                  event.y = 0.08;
               }
            }
         }
      }
   }

   private static void setVelocityXZ(Entity entity, double x, double z) {
      entity.setVelocity(x, entity.getVelocity().y, z);
   }

   private void onPacketSend(PacketSendEvent event) {
      if (event.packet instanceof PlayerMoveC2SPacket packet) {
         if (this.ncpBypass.get()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.isOnThread()) {
               ClientPlayerEntity player = mc.player;
               if (player != null && mc.world != null) {
                  if (!player.isTouchingWater() || this.waterShouldBeSolid(player)) {
                     if (!player.isInLava() || this.lavaShouldBeSolid(player)) {
                        int over = overLiquid(player);
                        boolean shouldWork = (over & 1) != 0 && this.waterShouldBeSolid(player) || (over & 2) != 0 && this.lavaShouldBeSolid(player);
                        if (!player.isTouchingWater() && !player.isInLava() && !(player.fallDistance > 3.0) && shouldWork) {
                           ((AccessorJesusServerboundMovePlayerPacket)packet).anubis$jesusSetOnGround(false);
                           if (player.isOnGround() && packet.changesPosition()) {
                              ((AccessorServerboundMovePlayerPacket)packet).anubis$setY(packet.getY(0.0) - (0.02 + 1.0E-4 * this.swimmingTicks));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isMoving(ClientPlayerEntity player) {
      return player.forwardSpeed != 0.0F || player.sidewaysSpeed != 0.0F;
   }

   private static void applyStrafe(MinecraftClient mc, ClientPlayerEntity player, PlayerMoveEvent event, double speed) {
      Vec2f input = player.input.getMovementInput();
      float forward = Math.signum(input.y);
      float side = Math.signum(input.x);
      float yaw = player.getLerpedYaw(mc.getRenderTickCounter().getTickProgress(true));
      if (forward == 0.0F && side == 0.0F) {
         event.x = 0.0;
         event.z = 0.0;
      } else {
         float strafe = 90.0F * side;
         if (forward != 0.0F) {
            strafe *= forward * 0.5F;
         }

         yaw -= strafe;
         if (forward < 0.0F) {
            yaw -= 180.0F;
         }

         double yawRadians = Math.toRadians(yaw);
         event.x = -Math.sin(yawRadians) * speed;
         event.z = Math.cos(yawRadians) * speed;
      }
   }

   private static int overLiquid(ClientPlayerEntity player) {
      Entity vehicle = player.getVehicle();
      Box box = vehicle != null ? player.getBoundingBox().union(vehicle.getBoundingBox()) : player.getBoundingBox();
      boolean water = false;
      boolean lava = false;
      Iterator<BlockState> states = player.getEntityWorld().getStatesInBox(box.offset(0.0, -0.01, 0.0)).iterator();

      while (states.hasNext()) {
         BlockState state = states.next();
         if (state.isOf(Blocks.WATER) || state.getFluidState().getFluid() == Fluids.WATER) {
            water = true;
         } else if (state.isOf(Blocks.LAVA)) {
            lava = true;
         } else if (!state.isAir()) {
            return 0;
         }
      }

      return (water ? 1 : 0) | (lava ? 2 : 0);
   }

   private boolean waterShouldBeSolid(ClientPlayerEntity player) {
      if (player.isSpectator() || player.getAbilities().flying) {
         return false;
      } else if (player.getVehicle() instanceof AbstractBoatEntity) {
         return false;
      } else if (FlyModule.active != null) {
         return false;
      } else if (this.dipIfBurning.get() && player.isOnFire()) {
         return false;
      } else if (this.dipOnSneakWater.get() && MinecraftClient.getInstance().options.sneakKey.isPressed()) {
         return false;
      } else {
         return this.dipOnFallWater.get() && player.fallDistance > this.dipFallHeightWater.getInt() ? false : this.waterMode.is("Solid");
      }
   }

   private boolean lavaShouldBeSolid(ClientPlayerEntity player) {
      if (player.isSpectator() || player.getAbilities().flying) {
         return false;
      } else if (player.getVehicle() instanceof StriderEntity) {
         return false;
      } else if (this.isLavaDangerous(player) && this.lavaMode.is("Solid")) {
         return true;
      } else if (this.dipOnSneakLava.get() && MinecraftClient.getInstance().options.sneakKey.isPressed()) {
         return false;
      } else {
         return this.dipOnFallLava.get() && player.fallDistance > this.dipFallHeightLava.getInt() ? false : this.lavaMode.is("Solid");
      }
   }

   private boolean isLavaDangerous(ClientPlayerEntity player) {
      if (!this.dipIfFireResistant.get()) {
         return true;
      }

      StatusEffectInstance fireResistance = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
      return fireResistance == null ? true : !(fireResistance.getDuration() > 300.0 * player.getAttributeValue(EntityAttributes.BURNING_TIME));
   }

   public boolean canWalkOnFluid(FluidState fluid) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && !player.isSwimming()) {
         Fluid type = fluid.getFluid();
         return (type == Fluids.WATER || type == Fluids.FLOWING_WATER) && this.waterShouldBeSolid(player)
            ? true
            : (type == Fluids.LAVA || type == Fluids.FLOWING_LAVA) && this.lavaShouldBeSolid(player);
      } else {
         return false;
      }
   }

   public VoxelShape collisionShape(CollisionView level, BlockState state, BlockPos pos, VoxelShape shape) {
      FluidState fluid = state.getFluidState();
      if (fluid.isEmpty()) {
         return shape;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (level == mc.world && mc.isOnThread()) {
         ClientPlayerEntity player = mc.player;
         if (player == null) {
            return shape;
         } else if ((state.isOf(Blocks.WATER) || fluid.getFluid() == Fluids.WATER)
            && !player.isTouchingWater()
            && this.waterShouldBeSolid(player)
            && pos.getY() <= player.getY() - 1.0) {
            return VoxelShapes.fullCube();
         } else {
            return !state.isOf(Blocks.LAVA)
                  || player.isInLava()
                  || !this.lavaShouldBeSolid(player)
                  || !this.isLavaDangerous(player) && !(pos.getY() <= player.getY() - 1.0)
               ? shape
               : VoxelShapes.fullCube();
         }
      } else {
         return shape;
      }
   }

   public boolean canWalkOnPowderSnow() {
      return this.powderSnow.get();
   }

   public void markInBubbleColumn() {
      this.inBubbleColumn = true;
   }
}
