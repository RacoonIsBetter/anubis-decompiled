package net.anubis.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.anubis.client.event.Event;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.EventType;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.ModuleToggleEvent;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.Setting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   public static final String SHOW_IN_HUD_SETTING = "Show in HUD";
   private final String id;
   private final String name;
   private final String description;
   private final ModuleCategory category;
   private boolean enabled;
   private boolean enabling;
   private boolean disabling;
   private boolean disableRequested;
   private final List<Setting> settings = new ArrayList<>();
   private final List<Setting> settingsView = Collections.unmodifiableList(this.settings);
   private final BooleanSetting showInHud;
   private final BooleanSetting toggleMessage;
   private BooleanSetting notifications;
   private final List<ListenerHandle<?>> ownedListeners = new ArrayList<>();
   private boolean busRegistered;
   volatile boolean registered;
   private int keybind = -1;
   private boolean holdMode;

   protected ClientModule(String name, String description, ModuleCategory category) {
      this(StableId.legacy(name), name, description, category);
   }

   protected ClientModule(String id, String name, String description, ModuleCategory category) {
      this.id = StableId.validate(id);
      this.name = name;
      this.description = description;
      this.category = Objects.requireNonNull(category, "category");
      this.showInHud = category != ModuleCategory.CLIENT ? this.addSetting("show-in-hud", new BooleanSetting("Show in HUD", true)) : null;
      this.toggleMessage = category != ModuleCategory.CLIENT ? this.addSetting("toggle-message", new BooleanSetting("Toggle Message", true)) : null;
      if (this.toggleMessage != null) {
         this.toggleMessage.visibleWhen(() -> !this.isAction());
      }
   }

   protected final <S extends Setting> S addSetting(S setting) {
      return this.addSetting(setting.id(), setting);
   }

   protected final <S extends Setting> S addSetting(String id, S setting) {
      return this.insertSetting(this.settings.size(), id, setting);
   }

   private <S extends Setting> S insertSetting(int index, String id, S setting) {
      for (Setting existing : this.settings) {
         if (existing.id().equals(id)) {
            throw new IllegalArgumentException("Duplicate setting id: " + this.id + "/" + id);
         }
      }

      setting.bindId(id);
      setting.onChange(ModuleRevision.CHANGED);
      this.settings.add(index, setting);
      ModuleRevision.changed();
      return setting;
   }

   protected final BooleanSetting usesNotifications() {
      if (this.notifications == null) {
         int standard = (this.showInHud != null ? 1 : 0) + (this.toggleMessage != null ? 1 : 0);
         this.notifications = this.insertSetting(standard, "notifications", new BooleanSetting("Notifications", true));
      }

      return this.notifications;
   }

   public final boolean notificationsOn() {
      return this.notifications == null || this.notifications.get();
   }

   public final boolean toggleMessageOn() {
      return this.toggleMessage != null && this.toggleMessage.get() && !this.isAction();
   }

   public final String id() {
      return this.id;
   }

   public final String name() {
      return this.name;
   }

   public final String description() {
      return this.description;
   }

   public final ModuleCategory category() {
      return this.category;
   }

   public final List<Setting> settings() {
      return this.settingsView;
   }

   public final void captureDefaults() {
      for (Setting setting : this.settings) {
         setting.captureDefault();
      }
   }

   public final boolean hasModifiedSettings() {
      for (Setting setting : this.settings) {
         if (!setting.isDefault()) {
            return true;
         }
      }

      return false;
   }

   public final void resetSettings() {
      for (Setting setting : this.settings) {
         setting.resetToDefault();
      }
   }

   public final BooleanSetting showInHud() {
      return this.showInHud;
   }

   public boolean persistEnabled() {
      return true;
   }

   public boolean enabledByDefault() {
      return false;
   }

   public boolean inConfigs() {
      return true;
   }

   public int settingsVersion() {
      return 0;
   }

   public void migrateSettings(int savedVersion) {
   }

   public boolean isAction() {
      return false;
   }

   public void onClick() {
   }

   public final boolean isEnabled() {
      return this.enabled;
   }

   public final void setEnabled(boolean value) {
      boolean was = this.enabled;
      this.switchTo(value);
      if (this.enabled != was) {
         ModuleToggleEvent.dispatch(this);
      }
   }

   private void switchTo(boolean value) {
      if (this.enabled != value && !this.disabling) {
         this.enabled = value;
         ModuleRevision.changed();
         if (this.registered) {
            ModuleRegistry.switched(this);
         }

         if (value) {
            this.enabling = true;
            this.disableRequested = false;

            try {
               this.busRegistered = EventBus.INSTANCE.register(this) > 0;
               this.onEnable();
               this.enabling = false;
               if (this.disableRequested) {
                  this.disableRequested = false;
                  this.switchTo(false);
               }
            } catch (Throwable failure) {
               this.enabling = false;
               this.disableRequested = false;

               try {
                  this.switchTo(false);
               } catch (Throwable cleanupFailure) {
                  if (cleanupFailure != failure) {
                     failure.addSuppressed(cleanupFailure);
                  }

                  if (cleanupFailure instanceof Error error) {
                     throw error;
                  }
               }

               LOGGER.error("Failed to enable module '{}'; activation rolled back", this.name, failure);
               if (failure instanceof Error error) {
                  throw error;
               }
            }
         } else {
            this.disabling = true;

            try {
               this.onDisable();
            } catch (RuntimeException failure) {
               LOGGER.error("Failed to deactivate module '{}'", this.name, failure);
            } finally {
               try {
                  this.ownedListeners.forEach(ListenerHandle::unregister);
                  this.ownedListeners.clear();
                  if (this.busRegistered) {
                     this.busRegistered = false;
                     EventBus.INSTANCE.unregister(this);
                  }
               } finally {
                  this.disabling = false;
               }
            }
         }
      }
   }

   public final void toggle() {
      this.setEnabled(!this.enabled);
   }

   protected final void requestDisable() {
      if (this.enabling) {
         this.disableRequested = true;
      } else {
         this.setEnabled(false);
      }
   }

   public final int keybind() {
      return this.keybind;
   }

   public final void setKeybind(int glfwKey) {
      if (this.keybind != glfwKey) {
         this.keybind = glfwKey;
         ModuleRevision.changed();
      }
   }

   public final boolean isHoldMode() {
      return this.holdMode;
   }

   public final void setHoldMode(boolean value) {
      if (this.holdMode != value) {
         this.holdMode = value;
         ModuleRevision.changed();
      }
   }

   protected void onEnable() {
   }

   protected void onDisable() {
   }

   protected <E extends Event> void on(EventType<E> type, Consumer<E> callback) {
      this.on(type, Stage.PRE, EventPriority.NORMAL, callback);
   }

   protected <E extends Event> void on(EventType<E> type, Stage stage, Consumer<E> callback) {
      this.on(type, stage, EventPriority.NORMAL, callback);
   }

   protected <E extends Event> void on(EventType<E> type, Stage stage, EventPriority priority, Consumer<E> callback) {
      if (!this.enabled) {
         throw new IllegalStateException("Cannot register a listener on disabled module: " + this.name);
      }

      this.ownedListeners.add(type.listen(this, stage, priority, callback));
   }
}
