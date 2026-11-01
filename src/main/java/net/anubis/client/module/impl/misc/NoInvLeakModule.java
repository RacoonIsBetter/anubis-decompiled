package net.anubis.client.module.impl.misc;

import com.example.client.mixin.AccessorCreativeSlotWrapper;
import com.sun.jna.Platform;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.noinvleak.FakeLoadout;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.joml.Vector2f;

@Environment(EnvType.CLIENT)
public final class NoInvLeakModule extends ClientModule {
   private static final String STYLE_TOTEM = "Totem";
   private static final String STYLE_RANDOM = "Random";
   private static final String STYLE_EMPTY = "Empty";
   private static final int OFFHAND_SEED = 10;
   private static volatile NoInvLeakModule active;
   private static ItemStack totem;
   private static boolean bareModel;
   private static final Vector2f POINT = new Vector2f();
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Totem", "Totem", "Random", "Empty"));
   private final BooleanSetting fillEmpty = this.addSetting("fill-empty", new BooleanSetting("Fill Empty Slots", true));
   private final BooleanSetting streamOnly = this.addSetting("stream-only", new BooleanSetting("Stream Only", false));
   private final BooleanSetting borderless = this.addSetting("borderless-fullscreen", new BooleanSetting("Borderless Fullscreen", true));
   private final BooleanSetting tooltips = this.addSetting("hide-tooltips", new BooleanSetting("Hide Tooltips", true));
   private final BooleanSetting itemName = this.addSetting("hide-item-name", new BooleanSetting("Hide Item Name", true));
   private final BooleanSetting selectedSlot = this.addSetting("hide-selected-slot", new BooleanSetting("Hide Selected Slot", true));
   private final BooleanSetting hud = this.addSetting("anubis-hud", new BooleanSetting("Anubis HUD", true));
   private final FakeLoadout loadout = new FakeLoadout();

   public NoInvLeakModule() {
      super("no-inv-leak", "No Inv Leak", "Hides your items on screen for screen shares. Stream Only keeps them visible to you alone.", ModuleCategory.MISC);
      this.fillEmpty.visibleWhen(() -> !this.style.is("Empty"));
      this.streamOnly.visibleWhen(Platform::isWindows);
      this.streamOnly.onChange(() -> {
         if (this.streamOnly.get()) {
            StreamOverlay.retry();
         } else {
            StreamOverlay.stop();
         }
      });
      this.borderless.visibleWhen(() -> Platform.isWindows() && this.streamOnly.get());
   }

   public static boolean wantsBorderless() {
      NoInvLeakModule module = ModuleRegistry.get(NoInvLeakModule.class);
      return module != null && module.streamOnly.get() && module.borderless.get()
         ? module.isEnabled() || ModuleConfigStore.getInstance().enablesOnLoad(module)
         : false;
   }

   @Override
   protected void onEnable() {
      this.loadout.reseed();
      StreamOverlay.retry();
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      StreamOverlay.stop();
   }

   public static ItemStack hotbar(ItemStack stack, int x, int y, int seed) {
      NoInvLeakModule module = active;
      if (module != null && stack != null) {
         int slot = seed == 10 ? 40 : (seed >= 1 && seed <= 9 ? seed - 1 : 41);
         if (module.streamOverlaying() && StreamOverlay.recordsHotbar()) {
            StreamOverlay.hotbar(slot, x, y, stack);
            return ItemStack.EMPTY;
         } else {
            return module.shown(stack, slot);
         }
      } else {
         return stack;
      }
   }

   public static ItemStack container(ItemStack stack, Slot slot, int x, int y, boolean hovered) {
      NoInvLeakModule module = active;
      if (module != null && stack != null) {
         int index = inventoryIndex(slot);
         if (module.streamOverlaying()) {
            StreamOverlay.container(index, x, y, stack, hovered);
            return ItemStack.EMPTY;
         } else {
            return module.shown(stack, index);
         }
      } else {
         return stack;
      }
   }

   private static int inventoryIndex(Slot slot) {
      Slot real = slot instanceof AccessorCreativeSlotWrapper wrapper ? wrapper.anubis$target() : slot;
      return real.getIndex();
   }

   public static ItemStack dragged(ItemStack stack) {
      NoInvLeakModule module = active;
      if (module != null && stack != null && !stack.isEmpty()) {
         return module.streamOverlaying() ? ItemStack.EMPTY : module.fake(stack, 41);
      } else {
         return stack;
      }
   }

   public static boolean hidesModelOf(LivingEntity entity) {
      return active != null && entity != null && entity == MinecraftClient.getInstance().player;
   }

   public static void bareModel(boolean on) {
      bareModel = on;
   }

   public static ItemStack modelItem(LivingEntity entity, ItemStack stack) {
      return bareModel && stack != null && !stack.isEmpty() && entity == MinecraftClient.getInstance().player ? ItemStack.EMPTY : stack;
   }

   public static int screenX(DrawContext guiGraphics, int localX, int localY, int fallback) {
      if (guiGraphics != null && active != null) {
         guiGraphics.getMatrices().transformPosition(localX, localY, POINT);
         return Math.round(POINT.x);
      } else {
         return fallback;
      }
   }

   public static int screenY(DrawContext guiGraphics, int localX, int localY, int fallback) {
      if (guiGraphics != null && active != null) {
         guiGraphics.getMatrices().transformPosition(localX, localY, POINT);
         return Math.round(POINT.y);
      } else {
         return fallback;
      }
   }

   public static ItemStack carried(ItemStack stack, int x, int y) {
      NoInvLeakModule module = active;
      if (module == null || stack == null || stack.isEmpty()) {
         return stack;
      } else if (module.streamOverlaying()) {
         StreamOverlay.carried(stack, x, y);
         return ItemStack.EMPTY;
      } else {
         return module.fake(stack, 41);
      }
   }

   public static ItemStack shownInHud(ItemStack stack, EquipmentSlot slot) {
      NoInvLeakModule module = active;
      return module != null && module.hud.get() && stack != null && !stack.isEmpty() ? module.fake(stack, inventorySlot(slot)) : stack;
   }

   public static ItemStack fakeFor(int slot) {
      NoInvLeakModule module = active;
      return module == null ? ItemStack.EMPTY : module.fake(ItemStack.EMPTY, slot);
   }

   public static boolean fillsEmpty() {
      NoInvLeakModule module = active;
      return module != null && module.fillEmpty.get() && !module.style.is("Empty");
   }

   public static boolean overlaying() {
      NoInvLeakModule module = active;
      return module != null && module.streamOnly.get() && StreamOverlay.usable();
   }

   private boolean streamOverlaying() {
      return this.streamOnly.get() && StreamOverlay.usable();
   }

   public static boolean ownSlot(Slot slot) {
      return active != null && slot != null && slot.inventory instanceof PlayerInventory;
   }

   public static boolean hidesTooltips() {
      NoInvLeakModule module = active;
      return module != null && module.tooltips.get();
   }

   public static boolean hidesItemName() {
      NoInvLeakModule module = active;
      return module != null && module.itemName.get();
   }

   public static boolean hidesSelectedSlot() {
      NoInvLeakModule module = active;
      return module != null && module.selectedSlot.get();
   }

   public static boolean overlayWanted() {
      NoInvLeakModule module = active;
      return module != null && module.streamOnly.get();
   }

   public static void streamNotice(String headline, String detail) {
      NoInvLeakModule module = active;
      if (module != null) {
         ModuleChat.send(module, ModuleChat.body().warn(headline).text(detail));
      }
   }

   public static boolean opaqueCells() {
      NoInvLeakModule module = active;
      return module != null && !module.style.is("Empty");
   }

   private ItemStack shown(ItemStack stack, int slot) {
      return stack.isEmpty() && !this.fillEmpty.get() ? stack : this.fake(stack, slot);
   }

   private ItemStack fake(ItemStack stack, int slot) {
      if (this.style.is("Empty")) {
         return ItemStack.EMPTY;
      }

      if (this.style.is("Random")) {
         return this.loadout.get(slot);
      }

      ItemStack stand = totem;
      if (stand == null) {
         totem = stand = new ItemStack(Items.TOTEM_OF_UNDYING);
      }

      return stand;
   }

   private static int inventorySlot(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> 39;
         case CHEST -> 38;
         case LEGS -> 37;
         case FEET -> 36;
         case OFFHAND -> 40;
         case MAINHAND -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            yield player != null ? player.getInventory().getSelectedSlot() : 41;
         }
         default -> 41;
      };
   }
}
