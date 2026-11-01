package net.anubis.client.module.impl.misc;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.playerbypass.AutoRtpLoop;
import net.anubis.client.util.playerbypass.ChunkLightSnapshot;
import net.anubis.client.util.playerbypass.PlayerBypassLightTracker;
import net.anubis.client.util.playerbypass.RtpReply;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class PlayerBypassAutoRtp {
   private static final double TELEPORT_DISTANCE_SQ = 256.0;
   private static final long REPLY_WINDOW_MS = 5000L;
   private static final long STOP_REPLY_WINDOW_MS = 2500L;
   private static final long RESUME_GRACE_MS = 1000L;
   private static final long RESPAWN_GRACE_MS = 3000L;
   private static final long OFFLINE_LIMIT_MS = 30000L;
   private static final long MENU_CLICK_DELAY_MS = 300L;
   private static final long MENU_FILL_MS = 2500L;
   private static final long MENU_CLAIM_MS = 4000L;
   private static final int MAX_MENU_CLICKS = 3;
   private static final int MENU_NAMES_SHOWN = 10;
   private static final long LONG_COOLDOWN_MS = 60000L;
   private static final long BAR_REFRESH_MS = 2000L;
   private static final long NO_CHUNK = Long.MIN_VALUE;
   private static final long NEVER = Long.MIN_VALUE;
   private final PlayerBypassModule module;
   private final BooleanSetting enabled;
   private final StringSetting command;
   private final NumberSetting scanTime;
   private final NumberSetting delay;
   private final NumberSetting timeout;
   private final NumberSetting maxRtps;
   private final BooleanSetting stopOnSuspect;
   private final StringSetting menuItem;
   private final BooleanSetting sound;
   private final BooleanSetting showCoordinates;
   private final AutoRtpLoop loop = new AutoRtpLoop();
   private final LongOpenHashSet knownSuspects = new LongOpenHashSet();
   private ListenerHandle<TickEvent> tickHandle;
   private ListenerHandle<PacketReceiveEvent> packetHandle;
   private ListenerHandle<PacketSendEvent> sendHandle;
   private ClientConnection link;
   private ClientWorld level;
   private Vec3d anchor;
   private int startChunkX;
   private int startChunkZ;
   private boolean wasDead;
   private long offlineSince;
   private long lastRevision;
   private boolean lastTryLanded;
   private long commandSentAt = Long.MIN_VALUE;
   private boolean sendingCommand;
   private volatile long userActionAt = Long.MIN_VALUE;
   private int menuId = -1;
   private int ignoredMenuId = -1;
   private long menuSeenAt;
   private boolean menuClicked;
   private int menuClicks;
   private long lastMenuClickAt;
   private long noticedCooldownUntil;
   private String lastBar = "";
   private long lastBarAt;

   PlayerBypassAutoRtp(
      PlayerBypassModule module,
      BooleanSetting enabled,
      StringSetting command,
      NumberSetting scanTime,
      NumberSetting delay,
      NumberSetting timeout,
      NumberSetting maxRtps,
      BooleanSetting stopOnSuspect,
      StringSetting menuItem,
      BooleanSetting sound,
      BooleanSetting showCoordinates
   ) {
      this.module = module;
      this.enabled = enabled;
      this.command = command;
      this.scanTime = scanTime;
      this.delay = delay;
      this.timeout = timeout;
      this.maxRtps = maxRtps;
      this.stopOnSuspect = stopOnSuspect;
      this.menuItem = menuItem;
      this.sound = sound;
      this.showCoordinates = showCoordinates;
   }

   void start() {
      if (!this.loop.running()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         if (player != null && mc.world != null && connection != null && connection.getConnection().isOpen()) {
            long now = System.currentTimeMillis();
            AutoRtpLoop.Config config = this.config();
            this.loop.start(now, config);
            this.link = connection.getConnection();
            this.level = mc.world;
            this.anchor = player.getEntityPos();
            this.startChunkX = player.getBlockPos().getX() >> 4;
            this.startChunkZ = player.getBlockPos().getZ() >> 4;
            this.wasDead = player.isDead();
            this.offlineSince = 0L;
            this.lastRevision = PlayerBypassLightTracker.visibilityRevision();
            this.lastTryLanded = false;
            this.noticedCooldownUntil = 0L;
            this.lastBar = "";
            this.resetMenu();
            this.rememberSuspects();
            if (this.tickHandle == null) {
               this.tickHandle = TickEvent.TYPE.listen(this, event -> this.tick());
            }

            if (this.packetHandle == null) {
               this.packetHandle = PacketReceiveEvent.TYPE.listen(this, event -> this.onPacket(event.packet));
            }

            if (this.sendHandle == null) {
               this.sendHandle = PacketSendEvent.TYPE.listen(this, event -> this.onPacketSent(event.packet));
            }

            ModuleChat.Body body = ModuleChat.body()
               .good("Auto RTP started")
               .text(": ")
               .value("/" + this.command())
               .text(", ")
               .value(seconds(config.scanMs()))
               .text(" scan per spot, at least ")
               .value(seconds(config.delayMs()))
               .text(" between RTPs");
            if (config.maxRtps() > 0) {
               body.text(", max ").value(config.maxRtps());
            }

            long cooldown = this.loop.cooldownLeft(now);
            if (cooldown > 0L) {
               this.noticedCooldownUntil = now + cooldown;
               body.text(", server cooldown ").value(seconds(cooldown)).text(" left");
               if (cooldown >= 60000L) {
                  body.text(" (turn Player Bypass off and on to clear it)");
               }
            }

            this.say(body.text("."));
         } else {
            this.say(ModuleChat.body().warn("Join a server first").text(", Auto RTP is off."));
            this.enabled.set(false);
         }
      }
   }

   void stopByUser() {
      if (!this.loop.running()) {
         this.unlisten();
      } else {
         this.stop(ModuleChat.body().text("Auto RTP stopped after ").value(this.loop.landings()).text(rtpWord(this.loop.landings())).text("."));
      }
   }

   void forgetCooldown() {
      this.loop.forgetCooldown();
   }

   private void stop(ModuleChat.Body why) {
      boolean was = this.loop.running() || this.tickHandle != null;
      this.loop.stop();
      this.unlisten();
      this.resetMenu();
      this.link = null;
      this.level = null;
      this.anchor = null;
      if (was && why != null) {
         this.say(why);
      }
   }

   private void stopWithRestartHint(ModuleChat.Body why) {
      this.stop(why.text(" Turn ").value("Auto RTP").text(" off and on to start again."));
   }

   private void unlisten() {
      if (this.tickHandle != null) {
         this.tickHandle.unregister();
      }

      if (this.packetHandle != null) {
         this.packetHandle.unregister();
      }

      if (this.sendHandle != null) {
         this.sendHandle.unregister();
      }

      this.tickHandle = null;
      this.packetHandle = null;
      this.sendHandle = null;
   }

   boolean baseSpotted(int blockX, int blockZ, boolean disconnecting) {
      if (this.loop.running() && !this.atStartSpot(blockX >> 4, blockZ >> 4)) {
         this.found("Base", blockX, blockZ, disconnecting);
         return true;
      } else {
         return false;
      }
   }

   private void found(String what, int blockX, int blockZ, boolean disconnecting) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      boolean teleporting = this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING;
      boolean moved = player != null
         && this.anchor != null
         && (player.getEntityWorld() != this.level || player.getEntityPos().squaredDistanceTo(this.anchor) > 256.0);
      boolean pending = this.loop.awaitingTeleport() && player != null && !moved;
      int rtps = this.loop.landings() + (teleporting && moved ? 1 : 0);
      boolean showPos = this.showCoordinates.get();
      ModuleChat.Body body = ModuleChat.body().good(what + " found");
      if (showPos) {
         body.text(" at ").value(blockX + ", " + blockZ);
      }

      this.stopWithRestartHint(body.text(" after ").value(rtps).text(rtpWord(rtps) + ". Auto RTP stopped."));
      if (pending && !disconnecting) {
         this.say(
            ModuleChat.body()
               .warn("Your /" + this.command() + " is still pending")
               .text(": move a little to cancel it (most servers cancel a teleport when you move),")
               .text(" or the server may still teleport you away.")
         );
      }

      ChallengeToast.addOrUpdate(
         new ItemStack(what.equals("Base") ? Items.PLAYER_HEAD : Items.SPYGLASS),
         what + " Found - Auto RTP",
         Text.literal(showPos ? "at " + blockX + ", " + blockZ : "after " + rtps + rtpWord(rtps)),
         this.sound.get()
      );
      if (this.sound.get()) {
         MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F));
      }
   }

   private void tick() {
      if (this.loop.running()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientPlayNetworkHandler connection = mc.getNetworkHandler();
         long now = System.currentTimeMillis();
         if (this.link != null && this.link.isOpen() && (connection == null || connection.getConnection() == this.link)) {
            if (player != null && mc.world != null && connection != null && mc.interactionManager != null) {
               this.offlineSince = 0L;
               AutoRtpLoop.Config config = this.config();
               long revision = PlayerBypassLightTracker.visibilityRevision();
               if (revision != this.lastRevision) {
                  this.lastRevision = revision;
                  this.loop.dataArrived(now);
               }

               if (player.isDead()) {
                  if (!this.wasDead) {
                     this.wasDead = true;
                     this.loop.interrupt(now, config);
                     this.resetMenu();
                  }

                  this.loop.holdUntil(now + 3000L);
                  this.bar(now, this.label(config) + " - paused, you died", now - this.lastBarAt >= 2000L);
               } else {
                  if (this.wasDead) {
                     this.wasDead = false;
                     this.level = mc.world;
                     this.anchor = player.getEntityPos();
                     this.loop.holdUntil(now + 3000L);
                  }

                  boolean levelChanged = mc.world != this.level;
                  if (levelChanged) {
                     this.level = mc.world;
                     this.knownSuspects.clear();
                  }

                  if (levelChanged || this.anchor != null && player.getEntityPos().squaredDistanceTo(this.anchor) > 256.0) {
                     switch (this.loop.phase()) {
                        case TELEPORTING:
                           this.land(player, now, config);
                           break;
                        case SCANNING:
                           this.anchor = player.getEntityPos();
                           this.loop.relanded(now, config);
                           break;
                        default:
                           if (this.loop.landedLate(now, config)) {
                              this.anchor = player.getEntityPos();
                              this.closeMenu(player);
                              this.resetMenu();
                           } else if (levelChanged) {
                              this.stopWithRestartHint(ModuleChat.body().warn("World changed").text(", Auto RTP stopped."));
                              return;
                           }
                     }
                  }

                  if (this.stopOnSuspect.get() && !levelChanged) {
                     long suspect = this.newSuspectNear(mc, player);
                     if (suspect != Long.MIN_VALUE) {
                        this.found("Suspect", (ChunkPos.getPackedX(suspect) << 4) + 8, (ChunkPos.getPackedZ(suspect) << 4) + 8, false);
                        return;
                     }
                  }

                  if (!this.handleMenu(mc, player, now, config)) {
                     String pause = mc.currentScreen != null && !this.ourMenuOpen(mc, player) ? "a menu is open" : null;
                     if (pause != null) {
                        this.loop.holdUntil(now + 1000L);
                     }

                     switch (this.loop.tick(now, config)) {
                        case SEND:
                           this.send(player, connection, now);
                           break;
                        case TIMED_OUT:
                           this.lastTryLanded = false;
                           this.closeMenu(player);
                           break;
                        case SCAN_CLEAR:
                           this.lastTryLanded = true;
                           break;
                        case LIMIT:
                           this.stopWithRestartHint(
                              ModuleChat.body().text("Max RTPs reached (").value(config.maxRtps()).text("), no base found. Auto RTP stopped.")
                           );
                           return;
                        case GAVE_UP:
                           this.closeMenu(player);
                           this.stopWithRestartHint(
                              ModuleChat.body()
                                 .warn("No teleport after 5 tries")
                                 .text(", check ")
                                 .value("RTP Command")
                                 .text(" and ")
                                 .value("Menu Item")
                                 .text(". Auto RTP stopped.")
                           );
                           return;
                     }

                     this.progress(now, pause, config);
                  }
               }
            } else {
               if (this.offlineSince == 0L) {
                  this.offlineSince = now;
               }

               if (now - this.offlineSince > 30000L) {
                  this.stopWithRestartHint(ModuleChat.body().warn("No world for 30s").text(", Auto RTP stopped."));
               }
            }
         } else {
            this.stopWithRestartHint(ModuleChat.body().warn("Disconnected").text(", Auto RTP stopped."));
         }
      }
   }

   private void send(ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now) {
      this.anchor = player.getEntityPos();
      this.resetMenu();
      this.rememberSuspects();
      this.commandSentAt = now;
      this.sendingCommand = true;

      try {
         connection.sendChatCommand(this.command());
      } finally {
         this.sendingCommand = false;
      }
   }

   private void land(ClientPlayerEntity player, long now, AutoRtpLoop.Config config) {
      this.anchor = player.getEntityPos();
      this.closeMenu(player);
      this.resetMenu();
      this.loop.landed(now, config);
   }

   private void onPacket(Packet<?> packet) {
      if (this.loop.running()) {
         if (packet instanceof EntityDamageS2CPacket damage) {
            this.onDamage(damage);
         } else if (packet instanceof GameMessageS2CPacket chat) {
            this.onServerText(chat.content().getString(), !chat.overlay());
         } else if (packet instanceof TitleS2CPacket title) {
            this.onServerText(title.text().getString(), false);
         } else if (packet instanceof SubtitleS2CPacket subtitle) {
            this.onServerText(subtitle.text().getString(), false);
         }
      }
   }

   private void onPacketSent(Packet<?> packet) {
      if (!this.sendingCommand) {
         if (packet instanceof PlayerInteractBlockC2SPacket
            || packet instanceof CommandExecutionC2SPacket
            || packet instanceof ChatCommandSignedC2SPacket
            || packet instanceof PlayerInteractEntityC2SPacket interact && isInteraction(interact)) {
            this.userActionAt = System.currentTimeMillis();
         }
      }
   }

   private static boolean isInteraction(PlayerInteractEntityC2SPacket packet) {
      final boolean[] interaction = new boolean[]{false};
      packet.handle(new Handler() {
         @Override
         public void interact(Hand hand) {
            interaction[0] = true;
         }

         @Override
         public void interactAt(Hand hand, Vec3d pos) {
            interaction[0] = true;
         }

         @Override
         public void attack() {
         }
      });
      return interaction[0];
   }

   private void onServerText(String raw, boolean chatLine) {
      long now = System.currentTimeMillis();
      boolean teleporting = this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING;
      if (teleporting || this.loop.sentWithin(now, 5000L)) {
         if (!chatLine || !RtpReply.looksLikePlayerChat(raw, otherPlayerNames())) {
            RtpReply reply = RtpReply.parse(raw);
            boolean fresh = this.loop.sentWithin(now, 2500L);
            switch (reply.kind()) {
               case COOLDOWN:
                  this.loop.cooldown(now, reply.millis());
                  this.noticeCooldown(now, reply.millis());
                  break;
               case WARMUP:
                  this.loop.warmup(now, reply.millis(), this.config());
                  if (reply.nextUseMs() >= 0L) {
                     this.loop.nextUseIn(now, reply.nextUseMs());
                     this.noticeCooldown(now, reply.nextUseMs());
                  }
                  break;
               case CANCELLED:
                  this.loop.cancelled(now);
                  break;
               case UNKNOWN_COMMAND:
                  if (fresh) {
                     this.stopWithRestartHint(
                        ModuleChat.body()
                           .warn("The server does not know ")
                           .value("/" + this.command())
                           .text(", check ")
                           .value("RTP Command")
                           .text(". Auto RTP stopped.")
                     );
                  }
                  break;
               case NO_PERMISSION:
                  if (fresh) {
                     this.stopWithRestartHint(ModuleChat.body().warn("No permission for ").value("/" + this.command()).text(". Auto RTP stopped."));
                  }
                  break;
               case COMBAT:
                  if (teleporting && fresh) {
                     this.stopWithRestartHint(ModuleChat.body().danger("In combat").text(", Auto RTP stopped."));
                  }
            }
         }
      }
   }

   private void noticeCooldown(long now, long ms) {
      long until = now + ms;
      if (ms >= 60000L && until > this.noticedCooldownUntil + 5000L) {
         this.noticedCooldownUntil = until;
         this.say(ModuleChat.body().text("Server cooldown ").value(seconds(ms)).text(", Auto RTP waits."));
      }
   }

   private static List<String> otherPlayerNames() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (connection == null) {
         return List.of();
      }

      ClientPlayerEntity self = mc.player;
      UUID selfId = self == null ? null : self.getUuid();
      String selfName = self == null ? null : self.getGameProfile().name();
      List<String> names = new ArrayList<>();

      for (PlayerListEntry info : connection.getPlayerList()) {
         GameProfile profile = info.getProfile();
         if (profile != null && profile.name() != null && !Objects.equals(profile.id(), selfId) && !profile.name().equalsIgnoreCase(selfName)) {
            names.add(profile.name());
         }
      }

      return names;
   }

   private void onDamage(EntityDamageS2CPacket packet) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && packet.entityId() == player.getId()) {
         Entity cause = packet.sourceCauseId() >= 0 ? mc.world.getEntityById(packet.sourceCauseId()) : null;
         if (cause != player) {
            boolean byPlayer = cause instanceof PlayerEntity
               || packet.sourceType().matchesKey(DamageTypes.PLAYER_ATTACK)
               || packet.sourceType().matchesKey(DamageTypes.PLAYER_EXPLOSION);
            if (byPlayer) {
               ModuleChat.Body body = ModuleChat.body().danger("Hit by ");
               if (cause instanceof PlayerEntity attacker) {
                  body.name(attacker.getGameProfile().name());
               } else {
                  body.text("a player");
               }

               this.stopWithRestartHint(body.text(", Auto RTP stopped (combat)."));
            }
         }
      }
   }

   private boolean handleMenu(MinecraftClient mc, ClientPlayerEntity player, long now, AutoRtpLoop.Config config) {
      if (this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING && containerOpen(mc, player)) {
         ScreenHandler menu = player.currentScreenHandler;
         if (menu.syncId != this.menuId) {
            if (menu.syncId == this.ignoredMenuId) {
               return false;
            }

            if (!this.menuMayBeOurs(now)) {
               this.ignoredMenuId = menu.syncId;
               return false;
            }

            if (this.menuClicks >= 3) {
               this.stopWithRestartHint(ModuleChat.body().warn("The RTP menu keeps opening").text(", Auto RTP stopped."));
               return true;
            }

            this.menuId = menu.syncId;
            this.menuSeenAt = now;
            this.menuClicked = false;
            this.loop.extend(now, config);
         }

         if (!this.menuClicked && now - this.menuSeenAt >= 300L) {
            List<String> names = new ArrayList<>();
            String wanted = this.menuItem.get().trim();
            int slot = findSlot(menu, wanted, names);
            boolean loading = now - this.menuSeenAt < 2500L;
            if (wanted.isEmpty()) {
               if (names.isEmpty() && loading) {
                  return false;
               }

               this.stopWithRestartHint(
                  ModuleChat.body()
                     .warn("The RTP command opened a menu")
                     .text(", set ")
                     .value("Menu Item")
                     .text(" to the item to click")
                     .text(nameList(names))
                     .text(". Auto RTP stopped.")
               );
               return true;
            } else {
               if (slot >= 0) {
                  mc.interactionManager.clickSlot(menu.syncId, slot, 0, SlotActionType.PICKUP, player);
                  this.menuClicked = true;
                  this.menuClicks++;
                  this.lastMenuClickAt = now;
                  this.loop.extend(now, config);
                  return false;
               }

               if (loading) {
                  return false;
               }

               this.stopWithRestartHint(ModuleChat.body().warn("No \"" + wanted + "\" in the RTP menu").text(nameList(names)).text(". Auto RTP stopped."));
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean menuMayBeOurs(long now) {
      if (this.commandSentAt != Long.MIN_VALUE && this.userActionAt < this.commandSentAt) {
         return now - this.commandSentAt <= 4000L ? true : this.menuClicks > 0 && now - this.lastMenuClickAt <= 4000L;
      } else {
         return false;
      }
   }

   private static int findSlot(ScreenHandler menu, String wanted, List<String> names) {
      List<Slot> slots = new ArrayList<>();
      List<String> slotNames = new ArrayList<>();
      List<String> slotIds = new ArrayList<>();

      for (Slot slot : menu.slots) {
         if (slot.inventory instanceof PlayerInventory) {
            break;
         }

         ItemStack stack = slot.getStack();
         if (!stack.isEmpty()) {
            String name = RtpReply.normalize(stack.getName().getString());
            slots.add(slot);
            slotNames.add(name);
            slotIds.add(Registries.ITEM.getId(stack.getItem()).getPath());
            if (!name.isEmpty() && !names.contains(name)) {
               names.add(name);
            }
         }
      }

      for (String part : wanted.split(",")) {
         String text = RtpReply.normalize(part);
         if (!text.isEmpty()) {
            String id = text.replace(' ', '_');

            for (int i = 0; i < slots.size(); i++) {
               if (slotNames.get(i).contains(text) || slotIds.get(i).equals(id)) {
                  return slots.get(i).id;
               }
            }
         }
      }

      return -1;
   }

   private static String nameList(List<String> names) {
      if (names.isEmpty()) {
         return "";
      }

      List<String> shown = names.size() > 10 ? names.subList(0, 10) : names;
      return " (items: " + String.join(", ", shown) + (names.size() > shown.size() ? ", ..." : "") + ")";
   }

   private static boolean containerOpen(MinecraftClient mc, ClientPlayerEntity player) {
      return player.currentScreenHandler != player.playerScreenHandler && mc.currentScreen instanceof HandledScreen;
   }

   private boolean ourMenuOpen(MinecraftClient mc, ClientPlayerEntity player) {
      return this.menuId >= 0 && containerOpen(mc, player) && player.currentScreenHandler.syncId == this.menuId;
   }

   private void closeMenu(ClientPlayerEntity player) {
      if (this.menuId >= 0 && player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId) {
         player.closeHandledScreen();
      }
   }

   private void resetMenu() {
      this.menuId = -1;
      this.ignoredMenuId = -1;
      this.menuClicked = false;
      this.menuClicks = 0;
   }

   private void rememberSuspects() {
      this.knownSuspects.clear();
      LongIterator var1 = PlayerBypassLightTracker.snapshots().keySet().iterator();

      while (var1.hasNext()) {
         long key = (Long)var1.next();
         if (PlayerBypassLightTracker.isSuspect(key)) {
            this.knownSuspects.add(key);
         }
      }
   }

   private long newSuspectNear(MinecraftClient mc, ClientPlayerEntity player) {
      int radius = mc.options.getClampedViewDistance() + 1;
      int centerX = player.getBlockPos().getX() >> 4;
      int centerZ = player.getBlockPos().getZ() >> 4;
      ObjectIterator var6 = PlayerBypassLightTracker.snapshots().long2ObjectEntrySet().iterator();

      while (var6.hasNext()) {
         Entry<ChunkLightSnapshot> entry = (Entry<ChunkLightSnapshot>)var6.next();
         long key = entry.getLongKey();
         if (!this.knownSuspects.contains(key)
            && PlayerBypassLightTracker.isSuspect(key)
            && PlayerBypassLightTracker.isFlagged(key, (ChunkLightSnapshot)entry.getValue())) {
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius && !this.atStartSpot(chunkX, chunkZ)) {
               return key;
            }
         }
      }

      return Long.MIN_VALUE;
   }

   private boolean atStartSpot(int chunkX, int chunkZ) {
      if (this.loop.landings() > 0) {
         return false;
      }

      int radius = MinecraftClient.getInstance().options.getClampedViewDistance() + 1;
      return Math.abs(chunkX - this.startChunkX) <= radius && Math.abs(chunkZ - this.startChunkZ) <= radius;
   }

   private void progress(long now, String pause, AutoRtpLoop.Config config) {
      String label = this.label(config);
      String text;
      if (pause != null) {
         text = label + " - paused, " + pause;
      } else {
         long next = this.loop.nextSendIn(now);

         text = switch (this.loop.phase()) {
            case TELEPORTING -> label + " - waiting for the teleport";
            case SCANNING -> label + " - scanning, " + seconds(this.loop.scanLeft(now)) + " left";
            case WAITING -> this.loop.cooldownLeft(now) > 0L
               ? label + " - server cooldown, next in " + seconds(next)
               : (
                  this.loop.landings() == 0 && this.loop.sent() == 0
                     ? label + " - starting" + (next >= 1000L ? " in " + seconds(next) : "")
                     : (
                        !this.lastTryLanded
                           ? label + " - no teleport, retry in " + seconds(next)
                           : (this.loop.settling(now) ? label + " - no base yet, chunks still loading" : label + " - no base, next in " + seconds(next))
                     )
               );
            default -> "";
         };
      }

      boolean refresh = this.loop.phase() != AutoRtpLoop.Phase.TELEPORTING && now - this.lastBarAt >= 2000L;
      this.bar(now, text, refresh);
   }

   private String label(AutoRtpLoop.Config config) {
      return "Auto RTP #" + this.loop.sent() + (config.maxRtps() > 0 ? "/" + config.maxRtps() : "");
   }

   private void bar(long now, String text, boolean refresh) {
      if (!text.isEmpty() && (!text.equals(this.lastBar) || refresh)) {
         this.lastBar = text;
         this.lastBarAt = now;
         ModuleChat.actionBarAlways(this.module, ModuleChat.body().text(text));
      }
   }

   private AutoRtpLoop.Config config() {
      return new AutoRtpLoop.Config(millis(this.scanTime), millis(this.delay), millis(this.timeout), this.maxRtps.getInt());
   }

   private String command() {
      String text = this.command.get().trim();

      while (text.startsWith("/")) {
         text = text.substring(1).trim();
      }

      return text.isEmpty() ? "rtp" : text;
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.sendAlways(this.module, body);
   }

   private static long millis(NumberSetting setting) {
      return Math.round(setting.get() * 1000.0);
   }

   private static String seconds(long ms) {
      long s = (ms + 999L) / 1000L;
      return s >= 120L ? String.format(Locale.ROOT, "%dm %ds", s / 60L, s % 60L) : s + "s";
   }

   private static String rtpWord(int count) {
      return count == 1 ? " RTP" : " RTPs";
   }
}
