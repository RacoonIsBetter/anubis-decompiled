package net.anubis.client.easyplace.sync;

import net.anubis.client.easyplace.mixin.AccessorClientLevel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class BlockAcks {
   private static ClientWorld level;
   private static int lastAcked = -1;

   private BlockAcks() {
   }

   public static void acked(int sequence) {
      ClientWorld now = MinecraftClient.getInstance().world;
      if (now != level) {
         level = now;
         lastAcked = -1;
      }

      lastAcked = Math.max(lastAcked, sequence);
   }

   public static int lastSent(ClientWorld level) {
      return ((AccessorClientLevel)level).easyplace$predictions().getSequence();
   }

   public static boolean answered(ClientWorld now, int sequence) {
      return now == level && lastAcked >= sequence;
   }
}
