package net.anubis.client.module.impl.misc;

import com.example.client.mixin.AccessorMovementClientInput;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.movement.AutoWalkModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class FreecamModule extends ClientModule {
   private static final int WALK_FWD = 1;
   private static final int WALK_BACK = 2;
   private static final int WALK_LEFT = 4;
   private static final int WALK_RIGHT = 8;
   private static final int WALK_JUMP = 16;
   private static final int WALK_SNEAK = 32;
   private static final int WALK_SPRINT = 64;
   private static final int WALK_FLAT = 15;
   private static final int LEARN_TICKS = 40;
   private static final int CHUNK_CAP = 32;
   private static final float SCROLL_STEP = 1.2F;
   public static volatile FreecamModule active;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 1.0F, 0.1F, 50.0F).step(0.1F));
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical", 1.0F, 0.2F, 5.0F).step(0.05F));
   private final BooleanSetting smoothing = this.addSetting("smoothing", new BooleanSetting("Smooth", true));
   private final BooleanSetting showOwnBody = this.addSetting("show-own-body", new BooleanSetting("Body", true));
   private final BooleanSetting showHands = this.addSetting("show-hands", new BooleanSetting("Hands", true));
   private final BooleanSetting bodyFollowsKeys = this.addSetting("body-uses-movement-keys", new BooleanSetting("Keys Move Body", false));
   private final BooleanSetting keepWalking = this.addSetting("keep-walking", new BooleanSetting("Keep Walking", true));
   private final BooleanSetting scrollSpeed = this.addSetting("scroll-speed", new BooleanSetting("Scroll Speed", true));
   private final BooleanSetting scrollResets = this.addSetting("scroll-resets", new BooleanSetting("Reset Scroll Speed", true));
   private final NumberSetting chunkDistance = this.addSetting("chunk-distance", new NumberSetting("Chunks", 12.0F, 2.0F, 32.0F).step(1.0F));
   private Vec3d pos = Vec3d.ZERO;
   private Vec3d prev = Vec3d.ZERO;
   private float yaw;
   private float pitch;
   private boolean flyingBefore;
   private boolean cullBefore;
   private Perspective viewBefore;
   private boolean viewPushed;
   private ChunkPos focus;
   private int radius = -1;
   private boolean waiting;
   private boolean live;
   private int walk;
   private int learn;
   private float scrolled = 1.0F;

   public FreecamModule() {
      super("freecam", "Freecam", "Detached camera.", ModuleCategory.MISC);
      this.scrollResets.visibleWhen(this.scrollSpeed::get);
      this.speed.onChange(() -> this.scrolled = 1.0F);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.waiting = true;
      this.live = false;
      this.on(TickEvent.TYPE, this::onTick);
   }

   @Override
   protected void onDisable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      boolean wasLive = this.live;
      this.waiting = false;
      this.live = false;
      if (active == this) {
         active = null;
      }

      this.walk = 0;
      this.learn = 0;
      this.scrolled = 1.0F;
      if (this.viewPushed) {
         mc.options.setPerspective(this.viewBefore);
         this.viewPushed = false;
      }

      if (wasLive) {
         mc.chunkCullingEnabled = this.cullBefore;
         if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
         }

         this.releaseChunks(mc);
         if (mc.player != null) {
            mc.player.getAbilities().flying = this.flyingBefore;
         }
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.open(mc);
      ClientPlayerEntity player = mc.player;
      if (this.live && player != null) {
         if (this.learn > 0) {
            this.learn--;
            this.absorb(player, false);
         }

         this.prev = this.pos;
         if (player.getAbilities().creativeMode) {
            player.getAbilities().flying = false;
         }

         this.stepCamera(mc.options);
         this.followChunks(mc);
      }
   }

   private void open(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (this.waiting && player != null && mc.world != null) {
         this.flyingBefore = player.getAbilities().flying;
         this.pos = this.prev = new Vec3d(player.getX(), player.getY() + player.getEyeHeight(EntityPose.STANDING), player.getZ());
         this.yaw = player.getYaw();
         this.pitch = player.getPitch();
         this.viewPushed = false;
         if (this.showOwnBody.get()) {
            this.viewBefore = mc.options.getPerspective();
            if (this.viewBefore.isFirstPerson()) {
               mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
               this.viewPushed = true;
            }
         }

         this.waiting = false;
         this.live = true;
         active = this;
         this.walk = 0;
         this.absorb(player, true);
         this.learn = 40;
         this.focus = null;
         this.radius = -1;
         this.cullBefore = mc.chunkCullingEnabled;
         mc.chunkCullingEnabled = false;
         this.followChunks(mc);
         drive(mc);
      }
   }

   private void stepCamera(GameOptions options) {
      double ahead = (options.forwardKey.isPressed() ? 1 : 0) - (options.backKey.isPressed() ? 1 : 0);
      double side = (options.leftKey.isPressed() ? 1 : 0) - (options.rightKey.isPressed() ? 1 : 0);
      double rise = (options.jumpKey.isPressed() ? 1 : 0) - (options.sneakKey.isPressed() ? 1 : 0);
      double step = this.flySpeed() * (this.smoothing.get() ? 0.5 : 1.0);
      Vec3d look = Vec3d.fromPolar(0.0F, this.yaw);
      Vec3d left = new Vec3d(look.z, 0.0, -look.x);
      this.pos = this.pos
         .add(
            (look.x * ahead + left.x * side) * step,
            rise * step * this.verticalSpeed.get(),
            (look.z * ahead + left.z * side) * step
         );
   }

   private float flySpeed() {
      return !this.scrollResets.get() ? this.speed.get() : MathHelper.clamp(this.speed.get() * this.scrolled, this.speed.min(), this.speed.max());
   }

   private void absorb(ClientPlayerEntity player, boolean opening) {
      GameOptions options = MinecraftClient.getInstance().options;
      PlayerInput keys = player.input.playerInput;
      boolean carry = this.keepWalking.get();
      boolean typed = carry && (opening || this.bodyFollowsKeys.get());
      if (typed) {
         this.setWalk(1, options.forwardKey.isPressed() || keys.forward());
         this.setWalk(2, options.backKey.isPressed() || keys.backward());
         this.setWalk(4, options.leftKey.isPressed() || keys.left());
         this.setWalk(8, options.rightKey.isPressed() || keys.right());
         this.setWalk(16, options.jumpKey.isPressed() || keys.jump());
         this.setWalk(32, options.sneakKey.isPressed() || keys.sneak() || opening && player.isSneaking());
         this.setWalk(64, options.sprintKey.isPressed() || keys.sprint());
      } else if (player.isSneaking()) {
         this.walk |= 32;
      }

      AutoWalkModule autoWalk = ModuleRegistry.get(AutoWalkModule.class);
      if (autoWalk != null && autoWalk.isEnabled()) {
         this.walk |= 1;
      }

      if (carry) {
         if (player.forwardSpeed > 0.02F) {
            this.walk |= 1;
         }

         if (player.forwardSpeed < -0.02F) {
            this.walk |= 2;
         }

         if (player.sidewaysSpeed > 0.02F) {
            this.walk |= 4;
         }

         if (player.sidewaysSpeed < -0.02F) {
            this.walk |= 8;
         }

         if (!opening || (this.walk & 15) == 0) {
            this.aimFromMotion(player);
         }
      }
   }

   private void setWalk(int bit, boolean on) {
      if (on) {
         this.walk |= bit;
      }
   }

   private void aimFromMotion(ClientPlayerEntity player) {
      Vec3d motion = player.getVelocity();
      double vx = motion.x;
      double vz = motion.z;
      if (!(vx * vx + vz * vz < 1.0E-8)) {
         Vec3d look = Vec3d.fromPolar(0.0F, player.getYaw());
         double along = vx * look.x + vz * look.z;
         double across = vz * look.x - vx * look.z;
         if (Math.abs(along) >= Math.abs(across)) {
            if (along > 0.01) {
               this.walk |= 1;
            } else if (along < -0.01) {
               this.walk |= 2;
            }
         } else if (across > 0.01) {
            this.walk |= 4;
         } else if (across < -0.01) {
            this.walk |= 8;
         }
      }
   }

   public static void drive(MinecraftClient mc) {
      FreecamModule module = active;
      if (module != null && mc.player != null) {
         module.pushWalk(mc);
      }
   }

   private void pushWalk(MinecraftClient mc) {
      GameOptions options = mc.options;
      boolean liveKeys = this.bodyFollowsKeys.get();
      boolean forward = this.on(1) || liveKeys && options.forwardKey.isPressed();
      boolean back = this.on(2) || liveKeys && options.backKey.isPressed();
      boolean left = this.on(4) || liveKeys && options.leftKey.isPressed();
      boolean right = this.on(8) || liveKeys && options.rightKey.isPressed();
      boolean jump = this.on(16);
      boolean sneak = this.on(32) || liveKeys && options.sneakKey.isPressed();
      boolean sprint = this.on(64) || liveKeys && options.sprintKey.isPressed();
      if (liveKeys) {
         if (options.backKey.isPressed()) {
            this.walk &= -2;
         }

         if (options.forwardKey.isPressed()) {
            this.walk &= -3;
         }

         if (options.rightKey.isPressed()) {
            this.walk &= -5;
         }

         if (options.leftKey.isPressed()) {
            this.walk &= -9;
         }
      }

      ClientPlayerEntity player = mc.player;
      player.input.playerInput = new PlayerInput(forward, back, left, right, jump, sneak, sprint);
      float sideways = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);
      float ahead = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
      ((AccessorMovementClientInput)player.input).anubis$setMoveVector(new Vec2f(sideways, ahead).normalize());
      player.setSneaking(sneak);
   }

   private boolean on(int bit) {
      return (this.walk & bit) != 0;
   }

   private void followChunks(MinecraftClient mc) {
      if (this.live && mc.world != null && mc.player != null) {
         ClientChunkManager cache = mc.world.getChunkManager();
         ChunkPos camera = new ChunkPos(MathHelper.floor(this.pos.x) >> 4, MathHelper.floor(this.pos.z) >> 4);
         ChunkPos feet = mc.player.getChunkPos();
         int span = Math.max(Math.abs(camera.x - feet.x), Math.abs(camera.z - feet.z));
         int preference = MathHelper.clamp(this.chunkDistance.getInt(), 2, 32);
         ChunkPos next = span > preference
            ? new ChunkPos(feet.x + (camera.x - feet.x) / 2, feet.z + (camera.z - feet.z) / 2)
            : camera;
         int needed = MathHelper.clamp(Math.max(preference, span / 2 + 3), 2, 32);
         if (needed != this.radius) {
            cache.updateLoadDistance(needed);
            this.radius = needed;
         }

         if (this.focus == null || next.x != this.focus.x || next.z != this.focus.z) {
            cache.setChunkMapCenter(next.x, next.z);
            this.focus = next;
            if (mc.worldRenderer != null) {
               mc.worldRenderer.scheduleTerrainUpdate();
            }
         }
      }
   }

   private void releaseChunks(MinecraftClient mc) {
      this.focus = null;
      this.radius = -1;
      if (mc.world != null) {
         ClientChunkManager cache = mc.world.getChunkManager();
         if (mc.player != null) {
            ChunkPos feet = mc.player.getChunkPos();
            cache.setChunkMapCenter(feet.x, feet.z);
         }

         cache.updateLoadDistance(mc.options.getClampedViewDistance());
         if (mc.worldRenderer != null) {
            mc.worldRenderer.scheduleTerrainUpdate();
         }
      }
   }

   public boolean drawsSelf(Entity entity, PlayerEntity viewer) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return this.showOwnBody.get() && mc.player != null && entity == mc.player && viewer == mc.player;
   }

   public boolean keepHands() {
      return this.showHands.get();
   }

   public boolean showBody() {
      return this.showOwnBody.get();
   }

   public static boolean scroll(double amount) {
      FreecamModule module = active;
      if (module != null && module.scrollSpeed.get() && amount != 0.0) {
         float factor = amount > 0.0 ? 1.2F : 0.8333333F;
         NumberSetting speed = module.speed;
         if (module.scrollResets.get()) {
            module.scrolled = MathHelper.clamp(module.scrolled * factor, speed.min() / speed.get(), speed.max() / speed.get());
         } else {
            speed.set(speed.get() * factor);
         }

         return true;
      } else {
         return false;
      }
   }

   public void turn(double yRot, double xRot) {
      this.yaw += (float)yRot * 0.15F;
      this.pitch = MathHelper.clamp(this.pitch + (float)xRot * 0.15F, -90.0F, 90.0F);
   }

   public double getX(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prev.x, this.pos.x);
   }

   public double getY(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prev.y, this.pos.y);
   }

   public double getZ(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prev.z, this.pos.z);
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }
}
