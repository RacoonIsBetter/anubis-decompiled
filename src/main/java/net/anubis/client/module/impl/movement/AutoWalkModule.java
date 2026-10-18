package net.anubis.client.module.impl.movement;

import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.PlayerMoveEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AutoWalkModule extends ClientModule {
   private final ModeSetting direction = this.addSetting(
      "simple-direction", new ModeSetting("Simple Direction", "Forwards", "Forwards", "Backwards", "Left", "Right")
   );
   private final BooleanSetting disableOnInput = this.addSetting("disable-on-input", new BooleanSetting("Disable On Input", false));
   private final BooleanSetting disableOnY = this.addSetting("disable-on-y-change", new BooleanSetting("Disable On Y Change", false));
   private final BooleanSetting waitForChunks = this.addSetting("no-unloaded-chunks", new BooleanSetting("No Unloaded Chunks", true));

   public AutoWalkModule() {
      super("auto-walk", "Auto Walk", "Automatically walks forward.", ModuleCategory.MOVEMENT);
      this.direction.onChange(() -> {
         if (this.isEnabled()) {
            releaseKeys();
         }
      });
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onPreTick);
      this.on(KeyInputEvent.TYPE, this::onKey);
      this.on(MouseInputEvent.TYPE, this::onMouseButton);
      this.on(PlayerMoveEvent.TYPE, this::onPlayerMove);
   }

   @Override
   protected void onDisable() {
      releaseKeys();
   }

   private void onPreTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         if (this.disableOnY.get() && player.lastY != player.getY()) {
            this.requestDisable();
         } else {
            this.keyFor(mc.options).setPressed(true);
         }
      }
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1 && this.disableOnInput.get()) {
         if (event.key != -1) {
            GameOptions options = MinecraftClient.getInstance().options;
            KeyInput key = new KeyInput(event.key, event.scancode, event.mods);
            if (options.forwardKey.matchesKey(key)
               || options.backKey.matchesKey(key)
               || options.leftKey.matchesKey(key)
               || options.rightKey.matchesKey(key)
               || options.sneakKey.matchesKey(key)
               || options.jumpKey.matchesKey(key)) {
               this.onMovementInput();
            }
         }
      }
   }

   private void onMouseButton(MouseInputEvent event) {
      if (event.action == 1 && this.disableOnInput.get()) {
         GameOptions options = MinecraftClient.getInstance().options;
         Click click = new Click(0.0, 0.0, new MouseInput(event.button, event.mods));
         if (options.forwardKey.matchesMouse(click)
            || options.backKey.matchesMouse(click)
            || options.leftKey.matchesMouse(click)
            || options.rightKey.matchesMouse(click)
            || options.sneakKey.matchesMouse(click)
            || options.jumpKey.matchesMouse(click)) {
            this.onMovementInput();
         }
      }
   }

   private void onMovementInput() {
      if (MinecraftClient.getInstance().currentScreen == null) {
         this.requestDisable();
      }
   }

   private void onPlayerMove(PlayerMoveEvent event) {
      if (this.waitForChunks.get()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientWorld level = mc.world;
         if (player != null && level != null) {
            int chunkX = ChunkSectionPos.getSectionCoordFloored(player.getX() + event.x * 2.0);
            int chunkZ = ChunkSectionPos.getSectionCoordFloored(player.getZ() + event.z * 2.0);
            if (!level.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
               event.x = 0.0;
               event.z = 0.0;
            }
         }
      }
   }

   private KeyBinding keyFor(GameOptions options) {
      return switch (this.direction.get()) {
         case "Backwards" -> options.backKey;
         case "Left" -> options.leftKey;
         case "Right" -> options.rightKey;
         default -> options.forwardKey;
      };
   }

   private static void releaseKeys() {
      MinecraftClient mc = MinecraftClient.getInstance();
      GameOptions options = mc.options;
      if (options != null) {
         boolean inGame = mc.currentScreen == null && !isFreecamActive();
         restore(mc, options.forwardKey, inGame);
         restore(mc, options.backKey, inGame);
         restore(mc, options.leftKey, inGame);
         restore(mc, options.rightKey, inGame);
      }
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }

   private static void restore(MinecraftClient mc, KeyBinding mapping, boolean inGame) {
      mapping.setPressed(inGame && isPhysicallyHeld(mc, mapping));
   }

   private static boolean isPhysicallyHeld(MinecraftClient mc, KeyBinding mapping) {
      if (mapping.isUnbound()) {
         return false;
      } else {
         Key key = InputUtil.fromTranslationKey(mapping.getBoundKeyTranslationKey());
         Type type = key.getCategory();
         if (type == Type.KEYSYM) {
            return AnubisKeyPolling.isKeyDown(mc.getWindow(), key.getCode());
         } else {
            return type == Type.MOUSE ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == 1 : false;
         }
      }
   }
}
