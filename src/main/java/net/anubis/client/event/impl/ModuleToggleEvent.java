package net.anubis.client.event.impl;

import net.anubis.client.event.Event;
import net.anubis.client.event.EventType;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleToggleEvent extends Event {
   public static final EventType<ModuleToggleEvent> TYPE = new EventType<>("ModuleToggleEvent");
   private static int silentDepth;
   public final ClientModule module;
   public final boolean enabled;

   private ModuleToggleEvent(ClientModule module, boolean enabled) {
      this.module = module;
      this.enabled = enabled;
   }

   public static void dispatch(ClientModule module) {
      if (silentDepth <= 0 && TYPE.hasPreListeners()) {
         TYPE.dispatch(new ModuleToggleEvent(module, module.isEnabled()));
      }
   }

   public static void silently(Runnable changes) {
      silentDepth++;

      try {
         changes.run();
      } finally {
         silentDepth--;
      }
   }
}
