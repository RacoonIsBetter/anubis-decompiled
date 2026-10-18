package net.anubis.client.event.impl;

import java.util.function.Consumer;
import net.anubis.client.event.CancellableEvent;
import net.anubis.client.event.PooledCancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class PlayerMoveEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PlayerMoveEvent> TYPE = new PooledCancellableEventType<>("PlayerMoveEvent", PlayerMoveEvent::new, 16);
   public double x;
   public double y;
   public double z;

   private PlayerMoveEvent() {
   }

   public static void move(Vec3d movement, Consumer<Vec3d> original) {
      if (!TYPE.hasListeners()) {
         original.accept(movement);
      } else {
         PlayerMoveEvent event = TYPE.pool.acquire();
         event.x = movement.x;
         event.y = movement.y;
         event.z = movement.z;
         event.resetCancellation();

         try {
            TYPE.dispatchPre(event);
            if (event.isCancelled()) {
               return;
            }

            if (Double.isFinite(event.x) && Double.isFinite(event.y) && Double.isFinite(event.z)) {
               Vec3d changed = event.x == movement.x && event.y == movement.y && event.z == movement.z
                  ? movement
                  : new Vec3d(event.x, event.y, event.z);
               original.accept(changed);
               TYPE.dispatchPost(event);
               return;
            }
         } finally {
            TYPE.pool.release(event);
         }
      }
   }
}
