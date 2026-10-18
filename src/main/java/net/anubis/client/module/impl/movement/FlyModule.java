package net.anubis.client.module.impl.movement;

import com.example.client.mixin.AccessorLocalPlayer;
import com.example.client.mixin.AccessorServerboundMovePlayerPacket;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.block.AbstractBlock.AbstractBlockState;

@Environment(EnvType.CLIENT)
public final class FlyModule extends ClientModule {
   public static volatile FlyModule active;
   private static final String ABILITIES = "Abilities";
   private static final String VELOCITY = "Velocity";
   private static final String KICK_NORMAL = "Normal";
   private static final String KICK_PACKET = "Packet";
   private static final String KICK_NONE = "None";
   private static final double ANTI_KICK_DROP = 0.0313;
   private static final float VANILLA_FLYING_SPEED = 0.05F;
   private static final String DEFAULT_MODE = "Abilities";
   private static final float DEFAULT_SPEED = 0.1F;
   private final ModeSetting mode = this.addSetting("fly-mode", new ModeSetting("Mode", "Abilities", "Abilities", "Velocity"));
   private final NumberSetting speed = this.addSetting("fly-speed", new NumberSetting("Speed", 0.1F, 0.0F, 10.0F).step(0.05F));
   private final BooleanSetting verticalSpeedMatch = this.addSetting("vertical-speed-match", new BooleanSetting("Vertical Speed Match", false));
   private final BooleanSetting noSneak = this.addSetting("no-sneak", new BooleanSetting("No Sneak", false));
   private final ModeSetting antiKickMode = this.addSetting("anti-kick-mode", new ModeSetting("Anti Kick Mode", "Packet", "Normal", "Packet", "None"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Anti Kick Delay", 20.0F, 1.0F, 200.0F).step(1.0F));
   private final NumberSetting offTime = this.addSetting("off-time", new NumberSetting("Anti Kick Off Time", 1.0F, 1.0F, 20.0F).step(1.0F));
   private int delayLeft = 20;
   private int offLeft = 1;
   private boolean flip;
   private float lastYaw;
   private double lastPacketY = Double.MAX_VALUE;
   private ClientPlayerEntity trackedPlayer;

   public FlyModule() {
      super("fly", "Fly", "Lets you fly.", ModuleCategory.MOVEMENT);
      this.noSneak.visibleWhen(() -> this.mode.is("Velocity"));
      this.mode.onChange(() -> {
         if (this.isEnabled()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
               abilitiesOff(player);
            }
         }
      });
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         this.requestDisable();
      } else {
         this.delayLeft = this.delay.getInt();
         this.offLeft = this.offTime.getInt();
         this.resetPlayerState(player);
         active = this;
         if (this.mode.is("Abilities") && !player.isSpectator()) {
            PlayerAbilities abilities = player.getAbilities();
            abilities.flying = true;
            if (!abilities.creativeMode) {
               abilities.allowFlying = true;
            }
         }

         this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
         this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
         this.on(PacketSendEvent.TYPE, this::onPacketSend);
         this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceiveNetty);
      }
   }

   @Override
   protected void onDisable() {
      active = null;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && this.mode.is("Abilities")) {
         abilitiesOff(player);
      }

      this.trackedPlayer = null;
   }

   private void resetPlayerState(ClientPlayerEntity player) {
      this.trackedPlayer = player;
      this.lastYaw = player.getYaw();
      this.lastPacketY = Double.MAX_VALUE;
   }

   private void onPreTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         this.requestDisable();
      } else {
         if (player != this.trackedPlayer) {
            this.resetPlayerState(player);
         }

         float currentYaw = player.getYaw();
         if (player.fallDistance >= 3.0 && currentYaw == this.lastYaw && player.getVelocity().length() < 0.003) {
            player.setYaw(currentYaw + (this.flip ? 1 : -1));
            this.flip = !this.flip;
         }

         this.lastYaw = currentYaw;
      }
   }

   private void onPostTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (player != this.trackedPlayer) {
            this.resetPlayerState(player);
         }

         if (this.delayLeft > 0) {
            this.delayLeft--;
         }

         if (this.offLeft <= 0 && this.delayLeft <= 0) {
            this.delayLeft = this.delay.getInt();
            this.offLeft = this.offTime.getInt();
            if (this.antiKickMode.is("Packet")) {
               forcePositionPacket(player);
            }
         } else if (this.delayLeft <= 0) {
            boolean shouldReturn = false;
            if (this.antiKickMode.is("Normal")) {
               if (this.mode.is("Abilities")) {
                  abilitiesOff(player);
                  shouldReturn = true;
               }
            } else if (this.antiKickMode.is("Packet") && this.offLeft == this.offTime.getInt()) {
               forcePositionPacket(player);
            }

            this.offLeft--;
            if (shouldReturn) {
               return;
            }
         }

         if (player.getYaw() != this.lastYaw) {
            player.setYaw(this.lastYaw);
         }

         if (this.mode.is("Velocity")) {
            if (!player.isSpectator()) {
               player.getAbilities().flying = false;
            }

            player.setVelocity(0.0, 0.0, 0.0);
            Vec3d velocity = player.getVelocity();
            if (!isFreecamActive()) {
               MinecraftClient mc = MinecraftClient.getInstance();
               double vertical = (double)this.speed.get() * (this.verticalSpeedMatch.get() ? 10.0F : 5.0F);
               if (mc.options.jumpKey.isPressed()) {
                  velocity = velocity.add(0.0, vertical, 0.0);
               }

               if (mc.options.sneakKey.isPressed()) {
                  velocity = velocity.subtract(0.0, vertical, 0.0);
               }
            }

            player.setVelocity(velocity);
            if (this.noSneak.get()) {
               player.setOnGround(false);
            }
         } else {
            if (player.isSpectator()) {
               return;
            }

            PlayerAbilities abilities = player.getAbilities();
            abilities.setFlySpeed(this.speed.get());
            abilities.flying = true;
            if (abilities.creativeMode) {
               return;
            }

            abilities.allowFlying = true;
         }
      }
   }

   private void onPacketSend(PacketSendEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.isOnThread()) {
         if (event.packet instanceof TeleportConfirmC2SPacket) {
            this.lastPacketY = Double.MAX_VALUE;
         } else if (event.packet instanceof PlayerMoveC2SPacket packet && this.antiKickMode.is("Packet")) {
            ClientPlayerEntity player = mc.player;
            ClientPlayNetworkHandler connection = mc.getNetworkHandler();
            if (player != null && connection != null) {
               double currentY = packet.getY(Double.MAX_VALUE);
               if (currentY != Double.MAX_VALUE) {
                  this.antiKickPacket(player, packet, currentY);
               } else {
                  PlayerMoveC2SPacket fullPacket;
                  if (packet.changesLook()) {
                     fullPacket = new Full(
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        packet.getYaw(0.0F),
                        packet.getPitch(0.0F),
                        packet.isOnGround(),
                        player.horizontalCollision
                     );
                  } else {
                     fullPacket = new PositionAndOnGround(player.getX(), player.getY(), player.getZ(), packet.isOnGround(), player.horizontalCollision);
                  }

                  event.setCancelled(true);
                  this.antiKickPacket(player, fullPacket, player.getY());
                  connection.sendPacket(fullPacket);
               }
            }
         }
      }
   }

   private void antiKickPacket(ClientPlayerEntity player, PlayerMoveC2SPacket packet, double currentY) {
      if (this.delayLeft <= 0 && this.lastPacketY != Double.MAX_VALUE && shouldFlyDown(currentY, this.lastPacketY) && isOnAir(player)) {
         ((AccessorServerboundMovePlayerPacket)packet).anubis$setY(this.lastPacketY - 0.0313);
      } else {
         this.lastPacketY = currentY;
      }
   }

   private void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof PlayerAbilitiesS2CPacket packet && this.mode.is("Abilities")) {
         event.setCancelled(true);
         MinecraftClient mc = MinecraftClient.getInstance();
         mc.execute(() -> {
            ClientPlayerEntity player = mc.player;
            if (player != null) {
               PlayerAbilities abilities = player.getAbilities();
               abilities.invulnerable = packet.isInvulnerable();
               abilities.creativeMode = packet.isCreativeMode();
               abilities.setWalkSpeed(packet.getWalkSpeed());
            }
         });
      }
   }

   private static boolean shouldFlyDown(double currentY, double lastY) {
      return currentY >= lastY ? true : lastY - currentY < 0.0313;
   }

   private static void forcePositionPacket(ClientPlayerEntity player) {
      ((AccessorLocalPlayer)player).anubis$setPositionReminder(20);
   }

   private static boolean isOnAir(ClientPlayerEntity player) {
      Box box = player.getBoundingBox().expand(0.0625).stretch(0.0, -0.55, 0.0);
      return player.getEntityWorld().getStatesInBox(box).allMatch(AbstractBlockState::isAir);
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }

   private static void abilitiesOff(ClientPlayerEntity player) {
      if (!player.isSpectator()) {
         PlayerAbilities abilities = player.getAbilities();
         abilities.flying = false;
         abilities.setFlySpeed(0.05F);
         if (!abilities.creativeMode) {
            abilities.allowFlying = false;
         }
      }
   }

   public float getOffGroundSpeed(ClientPlayerEntity player) {
      return this.isEnabled() && this.mode.is("Velocity") ? this.speed.get() * (player.isSprinting() ? 15.0F : 10.0F) : -1.0F;
   }

   public boolean noSneak() {
      return this.isEnabled() && this.mode.is("Velocity") && this.noSneak.get();
   }

   public boolean suppressFlightVerticalInput() {
      return this.isEnabled() && isFreecamActive();
   }
}
