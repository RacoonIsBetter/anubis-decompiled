package net.anubis.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class ServerRotation {
   private static Rotation requested;
   private static Rotation committed;
   private static Rotation server;
   private static boolean silent;
   private static ClientPlayerEntity owner;
   private static float ownYaw;
   private static float ownPitch;
   private static boolean swapped;
   private static Rotation walked;

   private ServerRotation() {
   }

   public static void request(Rotation rotation) {
      if (requested == null && committed == null) {
         requested = rotation;
      }
   }

   public static boolean pending() {
      return requested != null || committed != null;
   }

   public static Rotation sent() {
      return server;
   }

   public static boolean canRotate(ClientPlayerEntity player) {
      return !player.isGliding() && !player.isUsingRiptide() && !player.hasVehicle() && !player.isSwimming() && player.networkHandler.isLoaded();
   }

   public static Rotation next(ClientPlayerEntity player, Rotation wanted) {
      Rotation base = server != null ? server : new Rotation(player.getYaw(), player.getPitch());
      double k = RotationUtils.mouseScale(MinecraftClient.getInstance(), player);
      float step = (float)k * 0.15F;
      long yawCounts = Math.round(MathHelper.wrapDegrees(wanted.yaw() - base.yaw()) / step);
      long pitchCounts = Math.round((wanted.pitch() - base.pitch()) / step);
      float yaw = base.yaw() + (float)(yawCounts * k) * 0.15F;
      float pitch = MathHelper.clamp(base.pitch() + (float)(pitchCounts * k) * 0.15F, -90.0F, 90.0F);
      return new Rotation(yaw, pitch);
   }

   public static void startTick() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != owner) {
         owner = player;
         server = null;
      }

      requested = null;
      committed = null;
      if (player != null && server != null && !(Math.abs(player.getYaw() - server.yaw()) <= 180.0F)) {
         float shift = server.yaw() + MathHelper.wrapDegrees(player.getYaw() - server.yaw()) - player.getYaw();
         player.setYaw(player.getYaw() + shift);
         player.lastYaw += shift;
         player.renderYaw += shift;
         player.lastRenderYaw += shift;
         player.headYaw += shift;
         player.lastHeadYaw += shift;
         player.bodyYaw += shift;
         player.lastBodyYaw += shift;
      }
   }

   public static void noteSent(Packet<?> packet) {
      if (packet instanceof PlayerMoveC2SPacket move && move.changesLook() && MinecraftClient.getInstance().isOnThread()) {
         server = new Rotation(move.getYaw(0.0F), move.getPitch(0.0F));
         silent = swapped;
         owner = MinecraftClient.getInstance().player;
      }
   }

   public static Rotation commit(ClientPlayerEntity player) {
      if (committed == null) {
         committed = requested != null && canRotate(player) ? next(player, requested) : new Rotation(player.getYaw(), player.getPitch());
      }

      return committed;
   }

   public static boolean isSwapped() {
      return swapped;
   }

   public static void applyForMovement(ClientPlayerEntity player) {
      if (!swapped && canRotate(player)) {
         Rotation target = committed != null ? committed : (requested != null ? next(player, requested) : null);
         if (target != null && (target.yaw() != player.getYaw() || target.pitch() != player.getPitch())) {
            ownYaw = player.getYaw();
            ownPitch = player.getPitch();
            player.setYaw(target.yaw());
            player.setPitch(target.pitch());
            walked = target;
            swapped = true;
         }
      }
   }

   public static PlayerInput correctInput(PlayerInput keys) {
      if (walked == null) {
         return keys;
      }

      int forward = impulse(keys.forward(), keys.backward());
      int strafe = impulse(keys.left(), keys.right());
      if (forward == 0 && strafe == 0) {
         return keys;
      }

      double[] wanted = heading(ownYaw, strafe, forward);
      int bestForward = forward;
      int bestStrafe = strafe;
      double bestDot = Double.NEGATIVE_INFINITY;

      for (int f = -1; f <= 1; f++) {
         for (int s = -1; s <= 1; s++) {
            if (f != 0 || s != 0) {
               double[] way = heading(walked.yaw(), s, f);
               double dot = way[0] * wanted[0] + way[1] * wanted[1];
               if (dot > bestDot) {
                  bestDot = dot;
                  bestForward = f;
                  bestStrafe = s;
               }
            }
         }
      }

      return bestForward == forward && bestStrafe == strafe
         ? keys
         : new PlayerInput(bestForward > 0, bestForward < 0, bestStrafe > 0, bestStrafe < 0, keys.jump(), keys.sneak(), keys.sprint());
   }

   public static boolean misses(ClientPlayerEntity player, Box box, double range) {
      if (server != null && pending() && canRotate(player)) {
         Vec3d eye = player.getEyePos();
         if (box.contains(eye)) {
            return false;
         }

         Rotation look = committed != null ? committed : next(player, requested);
         return !hits(eye, box, range, look.yaw(), look.pitch())
            && !hits(eye, box, range, server.yaw(), look.pitch())
            && !hits(eye, box, range, server.yaw(), server.pitch());
      } else {
         return false;
      }
   }

   public static boolean missesBlock(ClientPlayerEntity player, Box box, double range) {
      if (server == null) {
         return false;
      }

      if (!silent) {
         if (!pending() || !canRotate(player)) {
            return false;
         }

         Rotation look = committed != null ? committed : next(player, requested);
         if (look.yaw() == player.getYaw() && look.pitch() == player.getPitch()) {
            return false;
         }
      }

      Vec3d eye = player.getEyePos();
      return !box.contains(eye) && !hits(eye, box, range, server.yaw(), server.pitch());
   }

   public static void afterSend(ClientPlayerEntity player) {
      if (swapped) {
         restore(player);
      }
   }

   public static void finishTick(ClientPlayerEntity player) {
      if (swapped) {
         restore(player);
      }
   }

   private static int impulse(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }

   private static double[] heading(float yaw, int strafe, int forward) {
      double sin = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
      double cos = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
      double x = strafe * cos - forward * sin;
      double z = forward * cos + strafe * sin;
      double length = Math.sqrt(x * x + z * z);
      return new double[]{x / length, z / length};
   }

   private static boolean hits(Vec3d eye, Box box, double range, float yaw, float pitch) {
      Vec3d direction = Vec3d.fromPolar(pitch, yaw);
      return box.raycast(eye, eye.add(direction.multiply(range))).isPresent();
   }

   private static void restore(ClientPlayerEntity player) {
      player.renderYaw = player.renderYaw + (ownYaw - walked.yaw()) * 0.5F;
      player.renderPitch = player.renderPitch + (ownPitch - walked.pitch()) * 0.5F;
      walked = null;
      player.setYaw(ownYaw);
      player.setPitch(ownPitch);
      swapped = false;
   }
}
