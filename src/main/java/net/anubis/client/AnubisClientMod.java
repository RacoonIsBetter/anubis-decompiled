package net.anubis.client;

import net.anubis.client.addon.AddonManager;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ProfileAutoLoad;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.event.impl.PacketAppliedEvent;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.BorderlessFullscreen;
import net.anubis.client.gui.HudLayoutStore;
import net.anubis.client.gui.HudRenderer;
import net.anubis.client.gui.ImGuiScreen;
import net.anubis.client.gui.LoadingScreens;
import net.anubis.client.gui.StaffGhost;
import net.anubis.client.gui.ToggleToasts;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.noinvleak.StreamOverlay;
import net.anubis.client.util.BlockEntityLeakTracker;
import net.anubis.client.util.combat.CombatTarget;
import net.anubis.client.util.netherite.NetheriteChunkTracker;
import net.anubis.client.util.playerbypass.PlayerBypassLightTracker;
import net.anubis.client.util.playerbypass.ProtectedChunkStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Identifier;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AnubisClientMod implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   public static final Identifier SPAWNER_NOTIFY_SOUND_ID = Identifier.of("anubis", "spawner_notify");
   public static final SoundEvent SPAWNER_NOTIFY_SOUND = SoundEvent.of(SPAWNER_NOTIFY_SOUND_ID);
   public static final Category KEY_CATEGORY = Category.create(Identifier.of("anubis", "controls"));
   private static KeyBinding openGuiKey;
   private final ModuleKeybinds keybinds = new ModuleKeybinds();
   private static final Object LEAK_TRACKER_OWNER = new Object();
   private static final Object PLAYER_BYPASS_TRACKER_OWNER = new Object();
   private static final Object NETHERITE_TRACKER_OWNER = new Object();

   public void onInitializeClient() {
      LOGGER.info("[Anubis] Initialising");
      AddonManager.init();
      ImGuiManager.getInstance().setHudRenderer(HudRenderer.getInstance()::render, HudRenderer.getInstance()::hasWork);
      ImGuiManager.getInstance().setStreamRenderer(StreamOverlay::drawFakes, StreamOverlay::hasFakes);
      ImGuiManager.getInstance().setToastRenderer(ToggleToasts::render, ToggleToasts::hasWork);
      ImGuiManager.getInstance().setOverlayRenderer(LoadingScreens::render, LoadingScreens::hasWork);
      ImGuiManager.getInstance().setAlertRenderer(StaffGhost::render, StaffGhost::hasWork);
      Render2DEvent.TYPE.listen(StaffGhost.class, StaffGhost::renderNote);
      ChunkScanner.init();
      PacketReceiveNettyEvent.TYPE.listen(LEAK_TRACKER_OWNER, event -> BlockEntityLeakTracker.ingest(event.packet));
      PacketAppliedEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onPacketApplied(event.packet));
      TickEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onTick());
      TickEvent.TYPE.listen(BorderlessFullscreen.class, event -> BorderlessFullscreen.tick());
      CombatTarget.install();
      PacketAppliedEvent.TYPE.listen(NETHERITE_TRACKER_OWNER, event -> NetheriteChunkTracker.onPacketApplied(event.packet));
      ToggleToasts.install();
      ProfileAutoLoad.install();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         HudLayoutStore.getInstance().save();
         ModuleConfigStore.getInstance().save();
         ThemeStore.get().save();
         HudTheme.get().save();
         ProtectedChunkStore.get().flushAndWait();
         StreamOverlay.stop();
      });
      openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.anubis.open_gui", Type.KEYSYM, 344, KEY_CATEGORY));
      ModuleConfigStore.getInstance().load();
      AddonManager.configLoaded();
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               ModuleConfigStore.getInstance().applyPendingEnabled();

               while (openGuiKey.wasPressed()) {
                  if (client.currentScreen instanceof ImGuiScreen) {
                     client.setScreen(null);
                  } else {
                     client.setScreen(new ImGuiScreen());
                  }
               }

               this.keybinds
                  .update(ModuleRegistry.all(), AnubisClientMod::isBoundKeyDown, client.currentScreen == null && client.isWindowFocused() && client.player != null);
               ModuleConfigStore.getInstance().tickAutosave();
            }
         );
   }

   public static boolean isGuiKey(long window, KeyInput event) {
      return openGuiKey != null && window == MinecraftClient.getInstance().getWindow().getHandle() && openGuiKey.matchesKey(event);
   }

   public static boolean toggleGuiFromKey() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.currentScreen == null) {
         client.setScreen(new ImGuiScreen());
         return true;
      }

      if (client.currentScreen instanceof ImGuiScreen gui && !gui.capturesKeys()) {
         client.setScreen(null);
      }

      return false;
   }

   public static void dropGuiClicks() {
      while (openGuiKey.wasPressed()) {
      }
   }

   private static boolean isBoundKeyDown(int key) {
      MinecraftClient client = MinecraftClient.getInstance();
      return key >= 1000
         ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key - 1000) == 1
         : AnubisKeyPolling.isKeyDown(client.getWindow(), key);
   }
}
