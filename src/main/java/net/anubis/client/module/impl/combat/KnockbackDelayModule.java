package net.anubis.client.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

@Environment(EnvType.CLIENT)
public final class KnockbackDelayModule extends ClientModule {
   private static final double FIGHT_RANGE = 6.0;
   private final NumberSetting chance = this.addSetting("chance", new NumberSetting("Chance (%)", 40.0F, 0.0F, 100.0F).step(5.0F));
   private final NumberSetting airMin = this.addSetting("air-min", new NumberSetting("Air Delay Min (ms)", 50.0F, 0.0F, 500.0F).step(10.0F));
   private final NumberSetting airMax = this.addSetting("air-max", new NumberSetting("Air Delay Max (ms)", 100.0F, 0.0F, 500.0F).step(10.0F));
   private final NumberSetting groundMin = this.addSetting("ground-min", new NumberSetting("Ground Delay Min (ms)", 200.0F, 0.0F, 1000.0F).step(10.0F));
   private final NumberSetting groundMax = this.addSetting("ground-max", new NumberSetting("Ground Delay Max (ms)", 250.0F, 0.0F, 1000.0F).step(10.0F));
   private final BooleanSetting waterCheck = this.addSetting("water-check", new BooleanSetting("Not In Water", false));
   private final BooleanSetting onlyFights = this.addSetting("only-fights", new BooleanSetting("Only Near Enemies", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final Deque<KnockbackDelayModule.Held> held = new ArrayDeque<>();
   private ClientPlayNetworkHandler heldFor;
   private volatile boolean fightNear;

   public KnockbackDelayModule() {
      super(
         "knockback-delay",
         "Knockback Delay",
         "Takes some hits' knockback a random moment late: a little in the air, longer on the ground.",
         ModuleCategory.COMBAT
      );
   }

   @Override
   protected void onEnable() {
      synchronized (this.held) {
         this.held.clear();
      }

      this.on(TickEvent.TYPE, Stage.PRE, event -> {
         this.fightNear = this.mc.player != null && this.enemyNear(this.mc.player);
         this.applyDue(false);
      });
   }

   @Override
   protected void onDisable() {
      this.mc.execute(() -> this.applyDue(true));
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveNettyEvent event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && pushesMe(event.packet, player)) {
         boolean waiting;
         synchronized (this.held) {
            waiting = !this.held.isEmpty();
         }

         if (waiting || this.delays(player)) {
            boolean ground = player.isOnGround();
            long wait = waiting ? 0L : randomBetween(ground ? this.groundMin.get() : this.airMin.get(), ground ? this.groundMax.get() : this.airMax.get());
            synchronized (this.held) {
               if (this.held.isEmpty()) {
                  this.heldFor = player.networkHandler;
               }

               long due = Math.max(System.currentTimeMillis() + wait, this.held.isEmpty() ? 0L : this.held.peekLast().due());
               this.held.addLast(new KnockbackDelayModule.Held(event.packet, due));
            }

            event.setCancelled(true);
         }
      }
   }

   private static boolean pushesMe(Packet<?> packet, ClientPlayerEntity player) {
      return packet instanceof EntityVelocityUpdateS2CPacket motion
         ? motion.getEntityId() == player.getId()
         : packet instanceof ExplosionS2CPacket explode && explode.playerKnockback().isPresent();
   }

   private boolean delays(ClientPlayerEntity player) {
      if (this.waterCheck.get() && player.isTouchingWater()) {
         return false;
      } else {
         return this.onlyFights.get() && !this.fightNear ? false : ThreadLocalRandom.current().nextDouble(100.0) < this.chance.get();
      }
   }

   private boolean enemyNear(ClientPlayerEntity player) {
      for (PlayerEntity other : player.getEntityWorld().getPlayers()) {
         if (other != player && other.distanceTo(player) <= 6.0 && MeleeAttacks.isEnemy(player, other)) {
            return true;
         }
      }

      return false;
   }

   private static long randomBetween(float a, float b) {
      long low = Math.round(Math.min(a, b));
      long high = Math.round(Math.max(a, b));
      return high <= low ? low : ThreadLocalRandom.current().nextLong(low, high + 1L);
   }

   private void applyDue(boolean all) {
      ClientPlayNetworkHandler connection = this.mc.getNetworkHandler();
      long now = System.currentTimeMillis();

      while (true) {
         KnockbackDelayModule.Held next;
         synchronized (this.held) {
            next = this.held.peekFirst();
            if (next == null || !all && next.due() > now) {
               return;
            }

            this.held.pollFirst();
            if (connection == null || connection != this.heldFor) {
               this.held.clear();
               return;
            }
         }

         ((Packet<ClientPlayNetworkHandler>)next.packet()).apply(connection);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Held(Packet<?> packet, long due) {
   }
}
