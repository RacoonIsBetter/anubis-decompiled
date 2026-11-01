package net.anubis.client.module.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.CombatMacroLock;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.combat.CombatTarget;
import net.anubis.client.util.pvp.Rotation;
import net.anubis.client.util.pvp.RotationUtils;
import net.anubis.client.util.pvp.ServerRotation;
import net.anubis.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class AimAssistModule extends ClientModule {
   private static final long LAST_HIT_MS = 1500L;
   private static final double STICK_RANGE = 0.5;
   private static final double STICK_FOV = 1.3;
   private static final long MOUSE_IDLE_MS = 150L;
   private static final double[] STILL = new double[]{0.0, 0.0};
   private static final long MINING_QUIET_MS = 250L;
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 4.0F, 3.0F, 6.0F).step(0.1F));
   private final NumberSetting fov = this.addSetting("fov", new NumberSetting("FOV", 60.0F, 10.0F, 180.0F).step(5.0F));
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 60.0F, 10.0F, 180.0F).step(5.0F));
   private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
   private final BooleanSetting vertical = this.addSetting("vertical", new BooleanSetting("Vertical", true));
   private PlayerEntity target;
   private double pace;
   private double pendingYaw;
   private double pendingPitch;
   private long movedAt;
   private double userYawSign;
   private double userPitchSign;
   private long minedAt;

   public AimAssistModule() {
      super("aim-assist", "Aim Assist", "Eases your crosshair onto the nearest enemy player while you move the mouse.", ModuleCategory.COMBAT);
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1) {
         this.speed.set(60.0F);
      }
   }

   @Override
   protected void onDisable() {
      this.follow(null);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
         this.minedAt = Util.getMeasuringTimeMs();
      }

      if (MeleeAttacks.canAct(mc)
         && !player.isUsingItem()
         && !mc.options.useKey.isPressed()
         && (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(player.getMainHandStack()))
         && !ServerRotation.pending()
         && !CombatMacroLock.heldByOther(this)
         && Util.getMeasuringTimeMs() - this.minedAt >= 250L) {
         this.follow(this.choose(mc, player));
      } else {
         this.follow(null);
      }
   }

   public double[] nudge(ClientPlayerEntity player, double userYaw, double userPitch, double seconds) {
      long now = Util.getMeasuringTimeMs();
      if (userYaw != 0.0 || userPitch != 0.0) {
         this.movedAt = now;
      }

      if (userYaw != 0.0) {
         this.userYawSign = Math.signum(userYaw);
      }

      if (userPitch != 0.0) {
         this.userPitchSign = Math.signum(userPitch);
      }

      Rotation want = this.target != null && now - this.movedAt <= 150L ? this.aim(player, player.getEyePos(), core(this.target)) : null;
      if (want == null) {
         this.pendingYaw = 0.0;
         this.pendingPitch = 0.0;
         this.userYawSign = 0.0;
         this.userPitchSign = 0.0;
         return STILL;
      }

      double dt = Math.min(seconds, 0.05);
      float yawError = MathHelper.wrapDegrees(want.yaw() - player.getYaw());
      float pitchError = this.vertical.get() ? want.pitch() - player.getPitch() : 0.0F;
      if (this.userYawSign * yawError < 0.0) {
         yawError = 0.0F;
      }

      if (this.userPitchSign * pitchError < 0.0) {
         pitchError = 0.0F;
      }

      double cap = this.speed.get() * this.pace * dt;
      double ease = 1.0 - Math.exp(-12.0 * dt);
      double k = RotationUtils.mouseScale(MinecraftClient.getInstance(), player);
      double degreesPerCount = (float)k * 0.15F;
      this.pendingYaw = this.pendingYaw + MathHelper.clamp(yawError * ease, -cap, cap) / degreesPerCount;
      this.pendingPitch = this.pendingPitch + MathHelper.clamp(pitchError * ease * 0.6, -cap, cap) / degreesPerCount;
      long yawCounts = (long)this.pendingYaw;
      long pitchCounts = (long)this.pendingPitch;
      this.pendingYaw -= yawCounts;
      this.pendingPitch -= pitchCounts;
      return new double[]{yawCounts * k, pitchCounts * k};
   }

   private void follow(PlayerEntity next) {
      if (next != this.target) {
         this.target = next;
         this.pace = ThreadLocalRandom.current().nextDouble(0.85, 1.15);
         this.pendingYaw = 0.0;
         this.pendingPitch = 0.0;
      }
   }

   private PlayerEntity choose(MinecraftClient mc, ClientPlayerEntity player) {
      Vec3d eye = player.getEyePos();
      double halfFov = this.fov.get() / 2.0;
      if (CombatTarget.lastHit(1500L) instanceof PlayerEntity hit && this.offAngle(player, eye, hit, this.range.get()) <= halfFov) {
         return hit;
      } else {
         if (this.target != null
            && this.target.getEntityWorld() == player.getEntityWorld()
            && this.offAngle(player, eye, this.target, this.range.get() + 0.5) <= halfFov * 1.3) {
            return this.target;
         }

         PlayerEntity best = null;
         double bestAngle = halfFov;

         for (PlayerEntity candidate : mc.world.getPlayers()) {
            double angle = this.offAngle(player, eye, candidate, this.range.get());
            if (!(angle > bestAngle)) {
               best = candidate;
               bestAngle = angle;
            }
         }

         return best;
      }
   }

   private double offAngle(ClientPlayerEntity player, Vec3d eye, PlayerEntity candidate, double reach) {
      if (MeleeAttacks.isEnemy(player, candidate) && (!candidate.isInvisible() || armourShows(candidate))) {
         if (eye.distanceTo(TargetingUtils.closestPoint(eye, candidate.getBoundingBox())) > reach) {
            return Double.MAX_VALUE;
         }

         Box core = core(candidate);
         if (!MeleeAttacks.lineIsClear(player, eye, core.getCenter())) {
            return Double.MAX_VALUE;
         }

         Rotation want = this.aim(player, eye, core);
         return want == null ? 0.0 : RotationUtils.angleTo(RotationUtils.entityRotation(player), want);
      } else {
         return Double.MAX_VALUE;
      }
   }

   private Rotation aim(ClientPlayerEntity player, Vec3d eye, Box core) {
      Vec3d view = player.getRotationVec(1.0F);
      if (core.raycast(eye, eye.add(view.multiply(this.range.get() + 1.0))).isPresent()) {
         return null;
      }

      Vec3d near = TargetingUtils.closestPoint(eye.add(view.multiply(eye.distanceTo(core.getCenter()))), core);
      return RotationUtils.fromTo(eye, near);
   }

   private static boolean armourShows(PlayerEntity player) {
      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         if (slot.isArmorSlot() && !player.getEquippedStack(slot).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static Box core(PlayerEntity player) {
      Box box = player.getBoundingBox();
      return box.contract(box.getLengthX() * 0.25, box.getLengthY() * 0.1, box.getLengthX() * 0.25);
   }
}
