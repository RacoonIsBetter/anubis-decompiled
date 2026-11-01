package net.anubis.client.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.combat.totem.OffhandRefill;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.GameMode;

@Environment(EnvType.CLIENT)
public final class AutoTotemModule extends ClientModule {
   private static final int OFFHAND_BUTTON = 40;
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final int TOTEM_POP_EVENT = 35;
   private static final int NEAREST_CHOICES = 3;
   private static AutoTotemModule instance;
   private final NumberSetting refillDelay = this.addSetting("refill-delay", new NumberSetting("Refill Delay", 3.0F, 1.0F, 10.0F).step(1.0F));
   private final BooleanSetting useHotbar = this.addSetting("use-hotbar-totems", new BooleanSetting("Use Hotbar Totems", false));
   private final OffhandRefill refill = new OffhandRefill(
      new OffhandRefill.Driver() {
         @Override
         public boolean needsTotem() {
            return AutoTotemModule.player().getOffHandStack().isEmpty();
         }

         @Override
         public boolean canInteract() {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            return mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL
               && player.currentScreenHandler == player.playerScreenHandler
               && player.playerScreenHandler.getCursorStack().isEmpty();
         }

         @Override
         public int findSource() {
            return AutoTotemModule.this.findSource(AutoTotemModule.player().getInventory());
         }

         @Override
         public boolean validSource(int index) {
            ClientPlayerEntity player = AutoTotemModule.player();
            if (!AutoTotemModule.isSource(player.getInventory(), index, AutoTotemModule.this.useHotbar.get())) {
               return false;
            }

            Slot slot = player.playerScreenHandler.getSlot(AutoTotemModule.menuSlot(index));
            return slot.inventory == player.getInventory() && slot.getIndex() == index && slot.canTakeItems(player);
         }

         @Override
         public void swap(int index) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, AutoTotemModule.menuSlot(index), 40, SlotActionType.SWAP, player);
            AutoTotemModule.this.previousSource = index;
         }

         @Override
         public int containerId() {
            return AutoTotemModule.player().playerScreenHandler.syncId;
         }

         @Override
         public int stateId() {
            return AutoTotemModule.player().playerScreenHandler.getRevision();
         }

         @Override
         public int latencyMillis() {
            ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
            PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(AutoTotemModule.player().getUuid());
            return info == null ? 0 : info.getLatency();
         }
      },
      ThreadLocalRandom.current()
   );
   private int previousSource = -1;

   public AutoTotemModule() {
      super(
         "autototem",
         "Auto Totem",
         "Refills an empty offhand with a totem from your inventory, at a human pace you choose with Refill Delay.",
         ModuleCategory.COMBAT
      );
      instance = this;
   }

   public static boolean wantsHotbar() {
      return false;
   }

   public static boolean busy() {
      AutoTotemModule module = instance;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return module != null && module.isEnabled() && player != null && !player.isSpectator() && !player.isInCreativeMode()
         ? module.refill.busy() || player.getOffHandStack().isEmpty() && module.findSource(player.getInventory()) >= 0
         : false;
   }

   @Override
   protected void onEnable() {
      AutoInvTotemModule other = ModuleRegistry.get(AutoInvTotemModule.class);
      if (other != null) {
         other.setEnabled(false);
      }

      this.reset();
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      this.update();
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      this.update();
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         if (event.packet instanceof EntityStatusS2CPacket status && status.getStatus() == 35 && status.getEntity(mc.world) == mc.player) {
            this.reset();
         }
      }
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      long now = System.nanoTime();
      if (event.packet instanceof SetPlayerInventoryS2CPacket update) {
         if (update.slot() == 40) {
            this.observe(-1, -1, offhand(update.contents()), now);
         }
      } else if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket update) {
         if (update.getSlot() == 45) {
            this.observe(update.getSyncId(), update.getRevision(), offhand(update.getStack()), now);
         }
      } else if (event.packet instanceof InventoryS2CPacket update && update.contents().size() > 45) {
         this.observe(update.syncId(), update.revision(), offhand(update.contents().get(45)), now);
      }
   }

   private void observe(int containerId, int stateId, OffhandRefill.Offhand offhand, long receivedAt) {
      MinecraftClient.getInstance().execute(() -> {
         if (this.isEnabled()) {
            this.refill.observe(containerId, stateId, offhand, receivedAt);
         }
      });
   }

   private void update() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (mc.world != null && player != null && mc.interactionManager != null && player.isAlive() && !player.isRemoved() && !(player.getHealth() <= 0.0F)) {
         this.refill.step(System.nanoTime(), this.refillDelay.getInt());
      } else {
         this.reset();
      }
   }

   private void reset() {
      this.refill.reset();
      this.previousSource = -1;
   }

   private int findSource(PlayerInventory inventory) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (this.useHotbar.get()) {
         List<Integer> hotbar = new ArrayList<>();

         for (int i = 0; i < 9; i++) {
            if (isSource(inventory, i, true)) {
               hotbar.add(i);
            }
         }

         if (!hotbar.isEmpty()) {
            return hotbar.get(random.nextInt(hotbar.size()));
         }
      }

      List<Integer> stored = new ArrayList<>();

      for (int i = 9; i < 36; i++) {
         if (isSource(inventory, i, false)) {
            stored.add(i);
         }
      }

      if (stored.isEmpty()) {
         return -1;
      }

      int anchor = this.previousSource >= 9 && this.previousSource < 36 ? this.previousSource - 9 : random.nextInt(27);
      stored.sort(Comparator.comparingInt(index -> {
         int offset = index - 9;
         return Math.abs(offset % 9 - anchor % 9) + Math.abs(offset / 9 - anchor / 9);
      }));
      return stored.get(random.nextInt(Math.min(3, stored.size())));
   }

   private static boolean isSource(PlayerInventory inventory, int index, boolean hotbar) {
      return index >= (hotbar ? 0 : 9) && index < 36 && inventory.getStack(index).isOf(Items.TOTEM_OF_UNDYING);
   }

   private static int menuSlot(int index) {
      return index < 9 ? index + 36 : index;
   }

   private static OffhandRefill.Offhand offhand(ItemStack stack) {
      if (stack.isEmpty()) {
         return OffhandRefill.Offhand.EMPTY;
      } else {
         return stack.isOf(Items.TOTEM_OF_UNDYING) ? OffhandRefill.Offhand.TOTEM : OffhandRefill.Offhand.OTHER;
      }
   }

   private static ClientPlayerEntity player() {
      return MinecraftClient.getInstance().player;
   }
}
