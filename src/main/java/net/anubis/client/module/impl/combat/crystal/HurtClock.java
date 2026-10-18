package net.anubis.client.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;

@Environment(EnvType.CLIENT)
public final class HurtClock {
   private static final int NEVER = -1000;
   private static final int WINDOW = 10;
   private static final int MAX_WAIT = 20;
   private static final int MAX_MARGIN = 2;
   private static final int RELAX_STREAK = 8;
   private static final int REMOVE_GRACE = 3;
   private static final double ANCHOR_MATCH_SQR = 0.25;
   private static final Int2ObjectOpenHashMap<HurtClock.Mark> MARKS = new Int2ObjectOpenHashMap();
   private static ClientPlayerEntity lastPlayer;
   private static World lastLevel;
   private static Packet<?> lastPacket;
   private static int now;
   private static int margin;
   private static int streak;

   private HurtClock() {
   }

   public static void note(ClientPlayerEntity player, Packet<?> packet) {
      if (packet != lastPacket) {
         lastPacket = packet;
         if (packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> inner : bundle.getPackets()) {
               handle(player, inner);
            }
         } else {
            handle(player, packet);
         }
      }
   }

   public static void tick(ClientPlayerEntity player) {
      now = player.age;
      if (player != lastPlayer || player.getEntityWorld() != lastLevel) {
         MARKS.clear();
         margin = 0;
         streak = 0;
         lastPlayer = player;
         lastLevel = player.getEntityWorld();
      }

      ObjectIterator<Entry<HurtClock.Mark>> iterator = MARKS.int2ObjectEntrySet().iterator();

      while (iterator.hasNext()) {
         Entry<HurtClock.Mark> entry = (Entry<HurtClock.Mark>)iterator.next();
         if (lastLevel.getEntityById(entry.getIntKey()) instanceof LivingEntity target && target.isAlive()) {
            HurtClock.Mark mark = (HurtClock.Mark)entry.getValue();
            if (mark.removedAt != -1000 && !mark.confirmed && now - mark.removedAt > 3) {
               margin = Math.min(2, margin + 1);
               streak = 0;
               mark.shotTick = -1000;
               mark.removedAt = -1000;
            }
         } else {
            iterator.remove();
         }
      }
   }

   public static boolean freshAt(LivingEntity target, int ahead, int lead) {
      if (target.timeUntilRegen - ahead - lead > 10) {
         return false;
      }

      HurtClock.Mark mark = (HurtClock.Mark)MARKS.get(target.getId());
      return mark == null || mark.shotTick == -1000 || now + ahead - mark.shotTick >= 10 + margin;
   }

   public static int opensIn(LivingEntity target, int lead) {
      for (int ahead = 0; ahead < 20; ahead++) {
         if (freshAt(target, ahead, lead)) {
            return ahead;
         }
      }

      return 20;
   }

   public static float effective(CrystalDamage.Victim victim, float raw, int ahead, int lead) {
      LivingEntity target = victim.entity();
      if (freshAt(target, ahead, lead)) {
         return victim.reduce(raw);
      }

      HurtClock.Mark mark = (HurtClock.Mark)MARKS.get(target.getId());
      float last = mark == null ? Float.MAX_VALUE : mark.lastRaw;
      return raw > last ? victim.reduce(raw - last) : 0.0F;
   }

   public static void shot(ClientPlayerEntity player, LivingEntity target, float raw, Object key, int lead) {
      HurtClock.Mark mark = (HurtClock.Mark)MARKS.computeIfAbsent(target.getId(), id -> new HurtClock.Mark());
      if (freshAt(target, 0, lead)) {
         mark.shotTick = player.age;
         mark.shotRaw = raw;
         mark.lastRaw = raw;
         mark.shotKey = key;
         mark.confirmed = false;
         mark.removedAt = -1000;
      } else {
         mark.lastRaw = Math.max(mark.lastRaw, raw);
      }
   }

   private static void handle(ClientPlayerEntity player, Packet<?> packet) {
      if (packet instanceof EntityDamageS2CPacket damage) {
         damaged(player, damage);
      } else if (packet instanceof EntitiesDestroyS2CPacket remove) {
         removed(remove.getEntityIds());
      }
   }

   private static void damaged(ClientPlayerEntity player, EntityDamageS2CPacket packet) {
      if (player.getEntityWorld().getEntityById(packet.entityId()) instanceof PlayerEntity target && MeleeAttacks.isEnemy(player, target)) {
         HurtClock.Mark mark = (HurtClock.Mark)MARKS.computeIfAbsent(target.getId(), id -> new HurtClock.Mark());
         if (mark.shotTick != -1000 && !mark.confirmed && ours(player, mark, packet)) {
            mark.lastRaw = mark.shotRaw;
            mark.confirmed = true;
            if (mark.shotKey instanceof Integer && ++streak >= 8) {
               margin = Math.max(0, margin - 1);
               streak = 0;
            }
         } else {
            RegistryEntry<DamageType> type = packet.sourceType();
            mark.shotTick = -1000;
            mark.removedAt = -1000;
            mark.lastRaw = !type.matchesKey(DamageTypes.PLAYER_ATTACK) && !type.matchesKey(DamageTypes.MOB_ATTACK) ? Float.MAX_VALUE : 0.0F;
         }
      }
   }

   private static boolean ours(ClientPlayerEntity player, HurtClock.Mark mark, EntityDamageS2CPacket packet) {
      RegistryEntry<DamageType> type = packet.sourceType();
      return type.matchesKey(DamageTypes.PLAYER_EXPLOSION)
         ? packet.sourceCauseId() == player.getId() && mark.shotKey instanceof Integer crystal && packet.sourceDirectId() == crystal
         : type.matchesKey(DamageTypes.BAD_RESPAWN_POINT)
            && mark.shotKey instanceof Vec3d center
            && packet.sourcePosition().filter(at -> at.squaredDistanceTo(center) < 0.25).isPresent();
   }

   private static void removed(IntList ids) {
      ObjectIterator var1 = MARKS.values().iterator();

      while (var1.hasNext()) {
         HurtClock.Mark mark = (HurtClock.Mark)var1.next();
         if (mark.shotTick != -1000 && !mark.confirmed && mark.shotKey instanceof Integer crystal && ids.contains(crystal)) {
            mark.removedAt = now;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Mark {
      float lastRaw = Float.MAX_VALUE;
      int shotTick = -1000;
      float shotRaw;
      Object shotKey;
      boolean confirmed;
      int removedAt = -1000;
   }
}
