package net.anubis.client.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PickItemFromBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PickItemFromEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

@Environment(EnvType.CLIENT)
public final class FakeLagModule extends ClientModule {
   private static final String DYNAMIC = "Dynamic";
   private static final String ALWAYS = "Always";
   private static final double AIM_CONE_DEGREES = 30.0;
   private static final double TOWARDS = 0.02;
   private static final int BOX_COLOR = -43691;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Dynamic", "Dynamic", "Always"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (ms)", 100.0F, 10.0F, 1000.0F).step(10.0F));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 6.0F, 3.0F, 12.0F).step(0.5F));
   private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Show Server Position", true));
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final Deque<FakeLagModule.Held> held = new ArrayDeque<>();
   private boolean releasing;
   private ClientPlayNetworkHandler heldFor;
   private volatile Vec3d serverFeet;
   private volatile boolean lagNow;

   public FakeLagModule() {
      super(
         "fake-lag",
         "Fake Lag",
         "Holds your outgoing packets for a moment so the server sees you a little behind. Dynamic: only while you walk at the player you aim at.",
         ModuleCategory.COMBAT
      );
      this.range.visibleWhen(() -> this.mode.is("Dynamic"));
   }

   @Override
   protected void onEnable() {
      synchronized (this.held) {
         this.held.clear();
      }

      this.serverFeet = null;
      this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
      this.on(Render3DEvent.TYPE, Stage.POST, event -> this.render());
   }

   @Override
   protected void onDisable() {
      this.lagNow = false;
      this.releaseAll();
   }

   @EventHandler
   public void onPacketSend(PacketSendEvent event) {
      if (!this.releasing) {
         Packet<?> packet = event.packet;
         if (this.lagNow && !acts(packet)) {
            synchronized (this.held) {
               if (this.held.isEmpty()) {
                  this.heldFor = this.mc.getNetworkHandler();
               }

               this.held.addLast(new FakeLagModule.Held(packet, System.currentTimeMillis()));
            }

            event.setCancelled(true);
         } else {
            this.releaseAll();
            this.track(packet);
         }
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveNettyEvent event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.mc.execute(this::releaseAll);
      }
   }

   private void tick() {
      if (this.mc.getNetworkHandler() != null && this.mc.player != null) {
         this.lagNow = this.lagging();
         if (!this.lagNow) {
            this.releaseAll();
         } else {
            long due = System.currentTimeMillis() - Math.round(this.delay.get());

            while (true) {
               FakeLagModule.Held next;
               synchronized (this.held) {
                  next = this.held.peekFirst();
                  if (next == null || next.at() > due) {
                     return;
                  }

                  this.held.pollFirst();
               }

               this.send(next.packet());
            }
         }
      } else {
         this.lagNow = false;
         this.drop();
      }
   }

   private boolean lagging() {
      ClientPlayerEntity player = this.mc.player;
      return player != null && this.mc.getNetworkHandler() != null && player.isAlive() ? this.mode.is("Always") || this.walkingAtTarget(player) : false;
   }

   private boolean walkingAtTarget(ClientPlayerEntity player) {
      Vec3d eye = player.getEyePos();
      Vec3d look = player.getRotationVec(1.0F);
      Vec3d motion = player.getVelocity();
      double reach = this.range.get();

      for (PlayerEntity other : this.mc.world.getPlayers()) {
         if (other != player && MeleeAttacks.isEnemy(player, other) && !(other.distanceTo(player) > reach)) {
            Vec3d to = other.getBoundingBox().getCenter().subtract(eye).normalize();
            if (!(Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(to))))) > 30.0) && motion.x * to.x + motion.z * to.z > 0.02) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean acts(Packet<?> packet) {
      return packet instanceof PlayerInteractEntityC2SPacket
         || packet instanceof PlayerInteractBlockC2SPacket
         || packet instanceof PlayerInteractItemC2SPacket
         || packet instanceof PlayerActionC2SPacket
         || packet instanceof HandSwingC2SPacket
         || packet instanceof ClickSlotC2SPacket
         || packet instanceof ButtonClickC2SPacket
         || packet instanceof CloseHandledScreenC2SPacket
         || packet instanceof ChatMessageC2SPacket
         || packet instanceof CommandExecutionC2SPacket
         || packet instanceof ChatCommandSignedC2SPacket
         || packet instanceof RequestCommandCompletionsC2SPacket
         || packet instanceof UpdateSignC2SPacket
         || packet instanceof BookUpdateC2SPacket
         || packet instanceof CraftRequestC2SPacket
         || packet instanceof SelectMerchantTradeC2SPacket
         || packet instanceof CreativeInventoryActionC2SPacket
         || packet instanceof PickItemFromBlockC2SPacket
         || packet instanceof PickItemFromEntityC2SPacket
         || packet instanceof TeleportConfirmC2SPacket;
   }

   private void releaseAll() {
      while (true) {
         FakeLagModule.Held next;
         synchronized (this.held) {
            next = this.held.pollFirst();
         }

         if (next == null) {
            return;
         }

         this.send(next.packet());
      }
   }

   private void drop() {
      synchronized (this.held) {
         this.held.clear();
      }

      this.serverFeet = null;
   }

   private void send(Packet<?> packet) {
      ClientPlayNetworkHandler connection = this.mc.getNetworkHandler();
      if (connection != null && (connection == this.heldFor || this.heldFor == null)) {
         this.releasing = true;

         try {
            connection.sendPacket(packet);
         } finally {
            this.releasing = false;
         }

         this.track(packet);
      } else {
         this.drop();
      }
   }

   private void track(Packet<?> packet) {
      if (packet instanceof PlayerMoveC2SPacket move && move.changesPosition() && this.mc.player != null) {
         this.serverFeet = new Vec3d(move.getX(this.mc.player.getX()), move.getY(this.mc.player.getY()), move.getZ(this.mc.player.getZ()));
      }
   }

   private void render() {
      ClientPlayerEntity player = this.mc.player;
      Vec3d feet = this.serverFeet;
      boolean holding;
      synchronized (this.held) {
         holding = !this.held.isEmpty();
      }

      if (this.render.get() && player != null && feet != null && holding) {
         double half = player.getWidth() / 2.0;
         GizmoDrawing.box(
            new Box(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + player.getHeight(), feet.z + half), DrawStyle.stroked(-43691, 2.0F)
         );
      }
   }

   @Environment(EnvType.CLIENT)
   private record Held(Packet<?> packet, long at) {
   }
}
