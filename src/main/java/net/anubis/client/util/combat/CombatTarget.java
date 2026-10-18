package net.anubis.client.util.combat;

import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PlayerAttackEntityEvent;
import net.anubis.client.event.impl.TickEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class CombatTarget {
   private static final Object OWNER = new Object();
   private static final long MEMORY_CAP_MS = 30000L;
   private static LivingEntity lastHit;
   private static long lastHitAt;

   private CombatTarget() {
   }

   public static void install() {
      PlayerAttackEntityEvent.TYPE.listen(OWNER, Stage.PRE, EventPriority.LOWEST, event -> {
         if (!event.isCancelled() && event.target instanceof LivingEntity living) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.isOnThread() && living.getEntityWorld() == mc.world) {
               lastHit = living;
               lastHitAt = Util.getMeasuringTimeMs();
            }
         }
      });
      TickEvent.TYPE.listen(OWNER, event -> forgetStale());
   }

   private static void forgetStale() {
      LivingEntity entity = lastHit;
      if (entity != null) {
         if (!entity.isAlive() || entity.getEntityWorld() != MinecraftClient.getInstance().world || Util.getMeasuringTimeMs() - lastHitAt > 30000L) {
            lastHit = null;
         }
      }
   }

   public static LivingEntity lastHit(long timeoutMs) {
      LivingEntity entity = lastHit;
      if (entity != null && entity.isAlive() && entity.getEntityWorld() == MinecraftClient.getInstance().world) {
         return Util.getMeasuringTimeMs() - lastHitAt > timeoutMs ? null : entity;
      } else {
         return null;
      }
   }

   public static LivingEntity current(long timeoutMs) {
      LivingEntity hit = lastHit(timeoutMs);
      if (hit != null) {
         return hit;
      } else {
         return MinecraftClient.getInstance().targetedEntity instanceof LivingEntity living && living.isAlive() ? living : null;
      }
   }
}
