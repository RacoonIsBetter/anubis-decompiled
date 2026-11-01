package net.anubis.client.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class LootLatch {
   private static final double RANGE_SQR = 100.0;
   private static final int GRACE_TICKS = 10;
   private static final long ACTIVE_MS = 250L;
   private static final Int2IntOpenHashMap DEAD = new Int2IntOpenHashMap();
   private static ClientPlayerEntity self;
   private static int selfTick = -1;
   private static int clock;
   private static boolean reported;
   private static boolean latched;
   private static boolean killed;
   private static long updatedAt;

   private LootLatch() {
   }

   public static void update(MinecraftClient mc, ClientPlayerEntity player) {
      if (player != self || player.age != selfTick) {
         self = player;
         selfTick = player.age;
         KeyBinding use = mc.options.useKey;
         evaluate(mc.world, player, Crystals.isPhysicallyDown(mc, use), MeleeAttacks.clickPending(use));
      }
   }

   public static boolean latched() {
      return latched;
   }

   public static boolean killedNow() {
      return killed;
   }

   public static boolean active() {
      return latched && Util.getMeasuringTimeMs() - updatedAt < 250L;
   }

   private static void evaluate(ClientWorld level, ClientPlayerEntity player, boolean down, boolean newPress) {
      int now = ++clock;
      updatedAt = Util.getMeasuringTimeMs();
      DEAD.int2IntEntrySet()
         .removeIf(entry -> now - entry.getIntValue() > 10 && !(level.getEntityById(entry.getIntKey()) instanceof PlayerEntity otherx && otherx.isDead()));
      boolean fresh = reported;
      reported = false;

      for (PlayerEntity other : level.getPlayers()) {
         if (other != player && other.isDead() && !DEAD.containsKey(other.getId())) {
            DEAD.put(other.getId(), now);
            if (other.squaredDistanceTo(player) <= 100.0) {
               fresh = true;
            }
         }
      }

      killed = fresh;
      if (fresh && down) {
         latched = true;
      } else if (!down || latched && newPress) {
         latched = false;
      }
   }

   private static void onPacket(PacketAppliedEvent event) {
      if (event.packet instanceof EntityStatusS2CPacket packet && packet.getStatus() == 3) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null && mc.player != null) {
            if (packet.getEntity(mc.world) instanceof PlayerEntity other && other != mc.player && !DEAD.containsKey(other.getId())) {
               DEAD.put(other.getId(), clock);
               if (other.squaredDistanceTo(mc.player) <= 100.0) {
                  reported = true;
               }
            }
         }
      }
   }

   static {
      PacketAppliedEvent.TYPE.listen(LootLatch.class, LootLatch::onPacket);
   }
}
