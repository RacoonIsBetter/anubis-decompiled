package net.anubis.client.event.impl;

import net.anubis.client.event.CancellableEvent;
import net.anubis.client.event.PooledCancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;

@Environment(EnvType.CLIENT)
public final class PacketReceiveNettyEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PacketReceiveNettyEvent> TYPE = new PooledCancellableEventType<>(
      "PacketReceiveNettyEvent", PacketReceiveNettyEvent::new, 1024
   );
   public Packet<?> packet;

   private PacketReceiveNettyEvent() {
   }

   public static boolean dispatchSync(Packet<?> packet) {
      if (!TYPE.hasPreListeners()) {
         return false;
      }

      PacketReceiveNettyEvent event = TYPE.pool.acquire();
      event.packet = packet;
      event.resetCancellation();

      try {
         TYPE.dispatch(event);
         return event.isCancelled();
      } finally {
         event.packet = null;
         TYPE.pool.release(event);
      }
   }
}
