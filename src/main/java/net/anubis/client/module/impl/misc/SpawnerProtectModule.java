package net.anubis.client.module.impl.misc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.misc.spawnerprotect.SpawnerAlerts;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class SpawnerProtectModule extends ClientModule {
   private static final int SHOP_END_CATEGORY_SLOT = 11;
   private static final int SHOP_ENDER_CHEST_SLOT = 9;
   private static final int SHOP_CONFIRM_SLOT = 25;
   private final NumberSetting targetStackCount = this.addSetting("stack-to-deposit", new NumberSetting("Stack To Deposit", 35.0F, 1.0F, 35.0F).step(1.0F));
   private final NumberSetting scanRange = this.addSetting("trigger-range", new NumberSetting("Trigger Range", 64.0F, 16.0F, 128.0F).step(0.1F));
   private final NumberSetting rotationSpeed = this.addSetting("rotation-speed", new NumberSetting("Rotation Speed", 15.0F, 1.0F, 30.0F).step(0.1F));
   private final BooleanSetting detectBlockUpdates = this.addSetting("detect-block-updates", new BooleanSetting("Detect Block Updates", true));
   private final StringSetting whitelist = this.addSetting("whitelist", new StringSetting("Whitelist", ""));
   private final NumberSetting breakRange = this.addSetting("break-range", new NumberSetting("Break Range", 5.5F, 1.0F, 8.0F).step(0.1F));
   private final NumberSetting blockUpdateThreshold = this.addSetting(
      "block-update-threshold", new NumberSetting("Block Update Threshold", 5.0F, 1.0F, 20.0F).step(1.0F)
   );
   private final NumberSetting blockUpdateWindow = this.addSetting(
      "block-update-window", new NumberSetting("Block Update Window", 60.0F, 1.0F, 600.0F).step(1.0F)
   );
   private final StringSetting webhookUrl = this.addSetting("webhook-url", new StringSetting("Webhook URL", ""));
   private SpawnerProtectModule.State currentState = SpawnerProtectModule.State.WAITING_FOR_STRANGER;
   private BlockPos targetBlock;
   private BlockPos targetChest;
   private int chestTick;
   private int breakCooldown;
   private int lagWaitTicks;
   private boolean strangerDetected;
   private final Set<UUID> nearbyPlayers = new HashSet<>();
   private int blockBreakCounter;
   private long lastBreakTimestamp;
   private int shopSequence;
   private int shopTick;
   private int miningTicks;

   public SpawnerProtectModule() {
      super("spawner-protect", "Spawner Protect", "Protects spawners on block updates; player arrivals only send webhook notifications.", ModuleCategory.DONUT);
      this.whitelist.privateValue();
      this.webhookUrl.privateValue();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1 && this.targetStackCount.get() == 3.0F) {
         this.targetStackCount.resetToDefault();
      }
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, this::onTick);
      this.on(BlockUpdateEvent.TYPE, this::onBlockUpdate);
      this.resetModule();
   }

   @Override
   protected void onDisable() {
      this.stopMovement();
      this.updateSneak(false);
   }

   private void resetModule() {
      this.currentState = SpawnerProtectModule.State.WAITING_FOR_STRANGER;
      this.strangerDetected = false;
      this.nearbyPlayers.clear();
      this.lagWaitTicks = 0;
      this.blockBreakCounter = 0;
      this.lastBreakTimestamp = 0L;
      this.stopMovement();
      this.updateSneak(false);
      this.shopSequence = 0;
      this.shopTick = 0;
      this.resetState();
   }

   private void resetState() {
      this.targetChest = null;
      this.targetBlock = null;
      this.chestTick = 0;
      this.breakCooldown = 0;
      this.miningTicks = 0;
   }

   private void onBlockUpdate(BlockUpdateEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!this.strangerDetected && mc.player != null && mc.world != null && !this.isNearSpawn() && this.detectBlockUpdates.get()) {
         if (!event.oldState.isAir() && !(event.oldState.getBlock() instanceof ShulkerBoxBlock)) {
            this.checkBlockUpdate(event.pos, event.newState);
         }
      }
   }

   private void checkBlockUpdate(BlockPos pos, BlockState newState) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!this.strangerDetected && mc.world != null) {
         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) < 8.0 && this.isWhitelisted(player.getName().getString())) {
               return;
            }
         }

         if (pos.getY() > -64 && newState.isAir()) {
            if (!(mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) < 6.0)) {
               double dx = mc.player.getX() - pos.getX();
               double dz = mc.player.getZ() - pos.getZ();
               double horizontalDist = Math.sqrt(dx * dx + dz * dz);
               if (!(horizontalDist > this.scanRange.get())) {
                  long now = System.currentTimeMillis();
                  if (now - this.lastBreakTimestamp > this.blockUpdateWindow.getInt() * 1000L) {
                     this.blockBreakCounter = 0;
                  }

                  this.blockBreakCounter++;
                  this.lastBreakTimestamp = now;
                  if (this.blockBreakCounter >= this.blockUpdateThreshold.getInt()) {
                     this.strangerDetected = true;
                     this.currentState = SpawnerProtectModule.State.WORKING;
                     ModuleChat.send(
                        this, "\ud83d\udea8 UZAK TESPİT: Yakında bir blok kırıldı! (Yatay Mesafe: " + (int)horizontalDist + "m, Y: " + pos.getY() + ")"
                     );
                     this.sendWebhook(
                        "\ud83d\udea8 Protection activated",
                        "The block-break threshold was reached. Spawner collection has started.",
                        15548997,
                        SpawnerAlerts.field("\ud83d\udce1 Trigger", "Block breaking", true),
                        SpawnerAlerts.field("\ud83e\uddf1 Block counter", this.blockBreakCounter + " / " + this.blockUpdateThreshold.getInt(), true),
                        SpawnerAlerts.field("\ud83d\udccf Horizontal distance", (int)horizontalDist + " blocks", true),
                        SpawnerAlerts.field("\ud83d\udccd Block location", coordinates(pos), false)
                     );
                  }
               }
            }
         }
      }
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen instanceof DisconnectedScreen) {
         this.resetModule();
      } else if (this.isNearSpawn()) {
         if (this.strangerDetected) {
            this.resetModule();
         }
      } else {
         this.notifyNearbyPlayers();
         if (this.currentState != SpawnerProtectModule.State.WAITING_FOR_STRANGER) {
            if (this.currentState != SpawnerProtectModule.State.OPENING_CHEST
               && this.currentState != SpawnerProtectModule.State.DEPOSITING_ITEMS
               && this.currentState != SpawnerProtectModule.State.BUYING_ECHEST) {
               this.updateSneak(true);
            }

            this.handleRotation();
            switch (this.currentState) {
               case WAITING_FOR_STRANGER:
               default:
                  break;
               case WORKING:
                  this.handleWorking();
                  break;
               case GOING_TO_CHEST:
                  this.handleGoingToChest();
                  break;
               case OPENING_CHEST:
                  this.handleOpeningChest();
                  break;
               case DEPOSITING_ITEMS:
                  this.handleDepositing();
                  break;
               case FINAL_EXIT:
                  this.handleFinalExit();
                  break;
               case BUYING_ECHEST:
                  this.handleBuyingEChest();
                  break;
               case PLACING_ECHEST:
                  this.handlePlacingEChest();
            }
         }
      }
   }

   private void notifyNearbyPlayers() {
      MinecraftClient mc = MinecraftClient.getInstance();
      Set<UUID> present = new HashSet<>();

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player
            && !player.isSpectator()
            && !this.isWhitelisted(player.getName().getString())
            && !(mc.player.distanceTo(player) > this.scanRange.get())) {
            present.add(player.getUuid());
            if (!this.nearbyPlayers.contains(player.getUuid())) {
               this.sendWebhook(
                  "\ud83d\udc64 A player entered your range",
                  "A new player is nearby. This event only sends a notification.",
                  5793266,
                  SpawnerAlerts.field("Player", "`" + player.getName().getString() + "`", true),
                  SpawnerAlerts.field("\ud83d\udccf Distance", String.format(Locale.ROOT, "%.1f blocks", mc.player.distanceTo(player)), true),
                  SpawnerAlerts.field("\ud83d\udee1️ Protection", this.strangerDetected ? "In progress" : "Standby", true),
                  SpawnerAlerts.field("\ud83d\udccd Player location", coordinates(player.getBlockPos()), false)
               );
            }
         }
      }

      this.nearbyPlayers.clear();
      this.nearbyPlayers.addAll(present);
   }

   private void handleRotation() {
      MinecraftClient mc = MinecraftClient.getInstance();
      Vec3d targetPos = null;
      if (this.currentState == SpawnerProtectModule.State.WORKING) {
         ItemEntity dropped = this.findDroppedSpawner();
         if (dropped != null) {
            targetPos = dropped.getEntityPos();
         } else {
            this.retargetSpawner(mc);
            if (this.targetBlock != null) {
               targetPos = Vec3d.ofCenter(this.targetBlock);
            }
         }
      } else if ((this.currentState == SpawnerProtectModule.State.GOING_TO_CHEST || this.currentState == SpawnerProtectModule.State.OPENING_CHEST)
         && this.targetChest != null) {
         targetPos = Vec3d.ofCenter(this.targetChest);
      }

      if (targetPos != null) {
         this.smoothLook(mc.player, targetPos);
      }
   }

   private void retargetSpawner(MinecraftClient mc) {
      if (this.targetBlock == null
         || mc.world.getBlockState(this.targetBlock).getBlock() != Blocks.SPAWNER
         || mc.player.getBlockPos().getSquaredDistance(this.targetBlock) > 256.0) {
         this.targetBlock = this.findRandomBlock(Blocks.SPAWNER, 16);
         this.miningTicks = 0;
      }
   }

   private void smoothLook(ClientPlayerEntity player, Vec3d target) {
      Vec3d eyes = player.getEyePos();
      double dx = target.x - eyes.x;
      double dy = target.y - eyes.y;
      double dz = target.z - eyes.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float step = this.rotationSpeed.get();
      player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - player.getYaw()), -step, step));
      player.setPitch(player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(targetPitch - player.getPitch()), -step, step));
   }

   private void handleWorking() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player.currentScreenHandler != player.playerScreenHandler) {
         player.closeHandledScreen();
      }

      if (this.findDroppedSpawner() != null) {
         this.lagWaitTicks = 0;
         this.stopBreaking();
         mc.options.forwardKey.setPressed(true);
      } else {
         int spawners = this.getSpawnerCount();
         if (spawners < this.targetStackCount.getInt() * 64 && (spawners <= 0 || player.getInventory().getEmptySlot() != -1)) {
            this.retargetSpawner(mc);
            if (this.targetBlock == null) {
               this.stopMovement();
               if (this.lagWaitTicks < 40) {
                  this.lagWaitTicks++;
               } else if (this.getSpawnerCount() > 0) {
                  this.goToChest();
               } else {
                  this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
                  this.lagWaitTicks = 0;
               }
            } else {
               this.lagWaitTicks = 0;
               if (player.getEyePos().distanceTo(Vec3d.ofCenter(this.targetBlock)) > this.breakRange.get()) {
                  this.stopBreaking();
                  mc.options.forwardKey.setPressed(true);
               } else {
                  mc.options.forwardKey.setPressed(false);
                  this.miningTicks++;
                  if (this.miningTicks > 60) {
                     mc.interactionManager
                        .interactBlock(
                           player,
                           Hand.MAIN_HAND,
                           new BlockHitResult(Vec3d.ofCenter(this.targetBlock), Direction.UP, this.targetBlock, false)
                        );
                     player.swingHand(Hand.MAIN_HAND);
                     this.miningTicks = 0;
                  }

                  if (this.breakCooldown <= 0) {
                     mc.interactionManager.updateBlockBreakingProgress(this.targetBlock, Direction.UP);
                     player.swingHand(Hand.MAIN_HAND);
                     mc.options.attackKey.setPressed(true);
                     this.breakCooldown = 6;
                  } else {
                     this.breakCooldown--;
                  }
               }
            }
         } else {
            this.goToChest();
         }
      }
   }

   private void goToChest() {
      this.stopMovement();
      this.targetChest = this.findNearestBlock(Blocks.ENDER_CHEST, 4);
      if (this.targetChest != null) {
         this.currentState = SpawnerProtectModule.State.GOING_TO_CHEST;
      } else if (this.getEnderChestCount() > 0) {
         this.currentState = SpawnerProtectModule.State.PLACING_ECHEST;
         this.shopTick = 0;
      } else {
         this.currentState = SpawnerProtectModule.State.BUYING_ECHEST;
         this.shopSequence = 0;
         this.shopTick = 0;
      }
   }

   private void handleGoingToChest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.targetChest == null) {
         this.targetChest = this.findNearestBlock(Blocks.ENDER_CHEST, 4);
      }

      if (this.targetChest == null) {
         this.currentState = SpawnerProtectModule.State.WORKING;
      } else {
         mc.options.forwardKey.setPressed(true);
         if (mc.player.getBlockPos().isWithinDistance(this.targetChest, 4.0)) {
            this.stopMovement();
            this.currentState = SpawnerProtectModule.State.OPENING_CHEST;
         }
      }
   }

   private void handleOpeningChest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.updateSneak(false);
      if (this.chestTick > 200) {
         ModuleChat.send(this, "Ender chest açılamadı; eşyalar envanterde tutuluyor.");
         this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
         this.lagWaitTicks = 0;
      } else {
         if (this.chestTick % 12 == 0 && this.targetChest != null) {
            mc.interactionManager
               .interactBlock(
                  mc.player,
                  Hand.MAIN_HAND,
                  new BlockHitResult(Vec3d.ofCenter(this.targetChest), Direction.UP, this.targetChest, false)
               );
         }

         this.chestTick++;
         if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            this.chestTick = 0;
            this.currentState = SpawnerProtectModule.State.DEPOSITING_ITEMS;
            this.lagWaitTicks = 0;
         }
      }
   }

   private void handleBuyingEChest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      this.stopMovement();
      this.updateSneak(false);
      this.shopTick++;
      if (this.shopTick > 200) {
         ModuleChat.send(this, "Market zaman aşımı; eşyalar envanterde tutuluyor.");
         this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
         this.lagWaitTicks = 0;
      } else if (this.shopSequence > 0 && mc.currentScreen == null && this.shopTick > 60) {
         ModuleChat.send(this, "Market ekranı kapandı, baştan deneniyor...");
         this.shopSequence = 0;
         this.shopTick = 0;
      } else if (this.shopTick >= 30) {
         switch (this.shopSequence) {
            case 0:
               ModuleChat.send(this, "Market açılıyor: /shop");
               mc.getNetworkHandler().sendChatCommand("shop");
               this.shopSequence = 1;
               this.shopTick = 0;
               break;
            case 1:
               this.shopStep(mc, "SHOP", "Market: End kategorisi seçiliyor...", 11);
               break;
            case 2:
               this.shopStep(mc, "END", "Market: Ender Chest seçiliyor...", 9);
               break;
            case 3:
               this.shopStep(mc, "ENDER CHEST", "Market: Satın alma onaylanıyor...", 25);
               break;
            case 4:
               if (this.getEnderChestCount() > 0) {
                  ModuleChat.send(this, "Ender Chest satın alındı.");
                  mc.player.closeHandledScreen();
                  this.currentState = SpawnerProtectModule.State.PLACING_ECHEST;
                  this.shopTick = 0;
               } else if (this.shopTick > 100) {
                  ModuleChat.send(this, "Satın alma başarısız oldu (Zaman aşımı).");
                  mc.player.closeHandledScreen();
                  this.shopSequence = 0;
                  this.shopTick = 0;
               }
         }
      }
   }

   private void shopStep(MinecraftClient mc, String title, String message, int slot) {
      if (mc.currentScreen != null && mc.currentScreen.getTitle().getString().toUpperCase(Locale.ROOT).contains(title)) {
         ModuleChat.send(this, message);
         mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
         this.shopSequence++;
         this.shopTick = 0;
      }
   }

   private void handlePlacingEChest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      this.shopTick++;
      if (this.shopTick >= 10) {
         int slot = -1;

         for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Blocks.ENDER_CHEST.asItem()) {
               slot = i;
               break;
            }
         }

         if (slot == -1) {
            for (int i = 9; i < 36; i++) {
               if (player.getInventory().getStack(i).getItem() == Blocks.ENDER_CHEST.asItem()) {
                  player.closeHandledScreen();
                  mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, 0, SlotActionType.SWAP, player);
                  this.shopTick = 0;
                  return;
               }
            }

            this.currentState = SpawnerProtectModule.State.BUYING_ECHEST;
            this.shopSequence = 0;
         } else {
            player.getInventory().setSelectedSlot(slot);
            BlockPos feet = player.getBlockPos();
            BlockPos placePos = null;

            for (Direction direction : Direction.values()) {
               if (direction != Direction.UP && direction != Direction.DOWN) {
                  BlockPos candidate = feet.offset(direction);
                  if (mc.world.getBlockState(candidate).isReplaceable() && !mc.world.getBlockState(candidate.down()).isAir()) {
                     placePos = candidate;
                     break;
                  }
               }
            }

            if (placePos == null) {
               ModuleChat.send(this, "E-Chest koyacak yer bulunamadı!");
               this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
               this.lagWaitTicks = 0;
            } else {
               BlockPos support = placePos.down();
               mc.interactionManager
                  .interactBlock(
                     player,
                     Hand.MAIN_HAND,
                     new BlockHitResult(Vec3d.ofCenter(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)
                  );
               player.swingHand(Hand.MAIN_HAND);
               this.targetChest = placePos;
               this.currentState = SpawnerProtectModule.State.GOING_TO_CHEST;
            }
         }
      }
   }

   private int getEnderChestCount() {
      return countInInventory(Blocks.ENDER_CHEST);
   }

   private void handleDepositing() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.lagWaitTicks < 15) {
         this.lagWaitTicks++;
      } else if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest)) {
         this.currentState = SpawnerProtectModule.State.OPENING_CHEST;
         this.chestTick = 0;
      } else {
         int var8 = chest.slots.size() - 36;
         boolean hasSpace = false;

         for (int depositedSomething = 0; depositedSomething < var8; depositedSomething++) {
            ItemStack stack = chest.getSlot(depositedSomething).getStack();
            if (stack.isEmpty() || stack.getItem() == Blocks.SPAWNER.asItem() && stack.getCount() < stack.getMaxCount()) {
               hasSpace = true;
               break;
            }
         }

         boolean depositedSomething = false;

         for (int i = 0; i < 36; i++) {
            int slotId = var8 + i;
            if (chest.getSlot(slotId).getStack().getItem() == Blocks.SPAWNER.asItem()) {
               if (!hasSpace) {
                  this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
                  this.lagWaitTicks = 0;
                  return;
               }

               mc.interactionManager.clickSlot(chest.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
               depositedSomething = true;
               break;
            }
         }

         if (!depositedSomething) {
            mc.player.closeHandledScreen();
            if (this.findNearestBlock(Blocks.SPAWNER, 16) != null) {
               this.currentState = SpawnerProtectModule.State.WORKING;
            } else {
               this.currentState = SpawnerProtectModule.State.FINAL_EXIT;
               this.lagWaitTicks = 0;
            }
         }
      }
   }

   private void handleFinalExit() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.lagWaitTicks == 0) {
         int remaining = this.getSpawnerCount();
         boolean leftOver = remaining > 0;
         this.sendWebhook(
            leftOver ? "⚠️ Spawners remain in inventory" : "✅ Task completed",
            leftOver
               ? "Some spawners are still in your inventory. Disconnecting in two seconds."
               : "The collection sequence has ended. Disconnecting in two seconds.",
            leftOver ? 16705372 : 5763719,
            SpawnerAlerts.field("\ud83c\udf92 Remaining in inventory", remaining + " spawners", true),
            SpawnerAlerts.field("\ud83d\udeaa Next step", "Disconnect from server", true)
         );
         ModuleChat.send(this, "Görev bitti, eşyaların sunucuya kaydedilmesi için 2 saniye beklenip çıkılacak...");
      }

      this.stopMovement();
      this.updateSneak(false);
      this.lagWaitTicks++;
      if (this.lagWaitTicks > 40) {
         if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("[SpawnerProtect] Görev tamamlandı, güvenli çıkış yapıldı."));
         }

         this.resetModule();
         this.requestDisable();
      }
   }

   @SafeVarargs
   private void sendWebhook(String title, String description, int color, Map<String, Object>... eventFields) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!this.webhookUrl.get().isBlank() && mc.player != null && mc.world != null) {
         List<Map<String, Object>> fields = new ArrayList<>(Arrays.asList(eventFields));
         fields.add(SpawnerAlerts.field("\ud83d\udc64 Account", "`" + mc.player.getName().getString() + "`", true));
         fields.add(SpawnerAlerts.field("\ud83c\udf0d Dimension", "`" + mc.world.getRegistryKey().getValue() + "`", true));
         fields.add(SpawnerAlerts.field("\ud83d\udccd Your location", coordinates(mc.player.getBlockPos()), false));
         SpawnerAlerts.send(this.webhookUrl.get(), SpawnerAlerts.payload(title, description, color, fields, Instant.now()));
      }
   }

   private static String coordinates(BlockPos pos) {
      return "`X: " + pos.getX() + "  Y: " + pos.getY() + "  Z: " + pos.getZ() + "`";
   }

   private void updateSneak(boolean sneak) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         mc.player.setSneaking(sneak);
      }

      mc.options.sneakKey.setPressed(sneak);
   }

   private boolean isNearSpawn() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player != null && Math.abs(player.getX()) < 100.0 && Math.abs(player.getZ()) < 100.0;
   }

   private boolean isWhitelisted(String name) {
      return Arrays.stream(this.whitelist.get().split("[,;\\s]+")).anyMatch(entry -> entry.equalsIgnoreCase(name));
   }

   private BlockPos findNearestBlock(Block block, int radius) {
      MinecraftClient mc = MinecraftClient.getInstance();
      BlockPos origin = mc.player.getBlockPos();
      BlockPos nearest = null;
      double minDist = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (mc.world.getBlockState(pos).getBlock() == block) {
                  double dist = origin.getSquaredDistance(pos);
                  if (dist < minDist) {
                     minDist = dist;
                     nearest = pos;
                  }
               }
            }
         }
      }

      return nearest;
   }

   private BlockPos findRandomBlock(Block block, int radius) {
      MinecraftClient mc = MinecraftClient.getInstance();
      BlockPos origin = mc.player.getBlockPos();
      List<BlockPos> found = new ArrayList<>();
      double maxDistSq = radius * radius;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (origin.getSquaredDistance(pos) <= maxDistSq && mc.world.getBlockState(pos).getBlock() == block) {
                  found.add(pos);
               }
            }
         }
      }

      return found.isEmpty() ? null : found.get(new Random().nextInt(found.size()));
   }

   private int getSpawnerCount() {
      return countInInventory(Blocks.SPAWNER);
   }

   private static int countInInventory(Block block) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      int count = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.getItem() == block.asItem()) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private ItemEntity findDroppedSpawner() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ItemEntity nearest = null;
      float nearestDist = Float.MAX_VALUE;

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof ItemEntity item && item.getStack().getItem() == Blocks.SPAWNER.asItem()) {
            float dist = mc.player.distanceTo(item);
            if (!(dist >= 16.0F) && !item.cannotPickup() && dist < nearestDist) {
               nearestDist = dist;
               nearest = item;
            }
         }
      }

      return nearest;
   }

   private void stopBreaking() {
      MinecraftClient mc = MinecraftClient.getInstance();
      mc.options.attackKey.setPressed(false);
      if (mc.interactionManager != null) {
         mc.interactionManager.cancelBlockBreaking();
      }

      this.breakCooldown = 0;
   }

   private void stopMovement() {
      this.stopBreaking();
      MinecraftClient.getInstance().options.forwardKey.setPressed(false);
   }

   @Environment(EnvType.CLIENT)
   private enum State {
      WAITING_FOR_STRANGER,
      WORKING,
      GOING_TO_CHEST,
      OPENING_CHEST,
      DEPOSITING_ITEMS,
      FINAL_EXIT,
      BUYING_ECHEST,
      PLACING_ECHEST;
   }
}
