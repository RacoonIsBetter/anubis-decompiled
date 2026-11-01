package net.anubis.client.module.impl.combat.bot;

import java.util.ArrayDeque;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class BotPause {
   private static final int TOUCH_TICKS = 30;
   private static final int RESPAWN_TICKS = 30;
   private static final int WORLD_TICKS = 40;
   private static final int SETBACK_TICKS = 10;
   private static final int SETBACK_LIMIT = 6;
   private static final long SETBACK_SPAN_MS = 10000L;
   private static final double SETBACK_MAX_JUMP = 6.0;
   private static final double CURSOR_SLACK = 1.5;
   private final ArrayDeque<Long> setbacks = new ArrayDeque<>();
   private ClientPlayerEntity body;
   private ClientWorld world;
   private double cursorX = Double.NaN;
   private double cursorY = Double.NaN;
   private int tick;
   private int touchedUntil;
   private int heldUntil;
   private Vec3d lastPosition;
   private long timeAt;
   private long gameTimeAt = -1L;
   private float tps = 20.0F;

   void reset() {
      this.setbacks.clear();
      this.body = null;
      this.world = null;
      this.cursorX = this.cursorY = Double.NaN;
      this.tick = this.touchedUntil = this.heldUntil = 0;
      this.lastPosition = null;
      this.gameTimeAt = -1L;
      this.tps = 20.0F;
   }

   BotPause.State tick(MinecraftClient mc, ClientPlayerEntity player) {
      this.tick++;
      if (this.body != null && player != this.body) {
         this.heldUntil = Math.max(this.heldUntil, this.tick + (mc.world != this.world ? 40 : 30));
      }

      this.body = player;
      this.world = mc.world;
      if (this.touched(mc)) {
         this.touchedUntil = this.tick + 30;
      }

      long now = System.currentTimeMillis();

      while (!this.setbacks.isEmpty() && now - this.setbacks.peekFirst() > 10000L) {
         this.setbacks.pollFirst();
      }

      this.lastPosition = player.getEntityPos();
      if (this.setbacks.size() >= 6) {
         return BotPause.State.STOP;
      } else if (this.tick < this.heldUntil) {
         return BotPause.State.HOLD;
      } else {
         return this.tick < this.touchedUntil ? BotPause.State.YIELD : BotPause.State.RUN;
      }
   }

   void moved(Vec3d to, int pearlAge) {
      if (this.lastPosition != null && pearlAge >= 60) {
         if (!(to.distanceTo(this.lastPosition) > 6.0)) {
            this.setbacks.add(System.currentTimeMillis());
            this.heldUntil = Math.max(this.heldUntil, this.tick + 10);
         }
      }
   }

   void time(long gameTime) {
      long now = System.currentTimeMillis();
      if (this.gameTimeAt >= 0L && now > this.timeAt && gameTime > this.gameTimeAt) {
         float measured = (float)(gameTime - this.gameTimeAt) * 1000.0F / (float)(now - this.timeAt);
         this.tps = Math.min(20.0F, this.tps + (measured - this.tps) * 0.5F);
      }

      this.gameTimeAt = gameTime;
      this.timeAt = now;
   }

   boolean slowServer() {
      return this.tps < 15.0F;
   }

   int setbacksLately() {
      return this.setbacks.size();
   }

   private boolean touched(MinecraftClient mc) {
      if (mc.currentScreen == null && mc.isWindowFocused()) {
         double x = mc.mouse.getX();
         double y = mc.mouse.getY();
         boolean moved = !Double.isNaN(this.cursorX) && (Math.abs(x - this.cursorX) > 1.5 || Math.abs(y - this.cursorY) > 1.5);
         this.cursorX = x;
         this.cursorY = y;
         if (moved) {
            return true;
         }

         KeyBinding[] keys = new KeyBinding[]{
            mc.options.forwardKey,
            mc.options.backKey,
            mc.options.leftKey,
            mc.options.rightKey,
            mc.options.jumpKey,
            mc.options.sneakKey,
            mc.options.attackKey,
            mc.options.useKey
         };

         for (KeyBinding key : keys) {
            if (Crystals.isPhysicallyDown(mc, key)) {
               return true;
            }
         }

         for (KeyBinding key : mc.options.hotbarKeys) {
            if (Crystals.isPhysicallyDown(mc, key)) {
               return true;
            }
         }

         return false;
      } else {
         this.cursorX = this.cursorY = Double.NaN;
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   enum State {
      RUN,
      YIELD,
      HOLD,
      STOP;
   }
}
