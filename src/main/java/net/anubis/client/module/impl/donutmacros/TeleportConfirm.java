package net.anubis.client.module.impl.donutmacros;

import java.util.Map;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
final class TeleportConfirm {
   private static final long WINDOW_MS = 3000L;
   private final ClientModule owner;
   private Object pressed;
   private boolean warned;
   private long sentAt;

   TeleportConfirm(ClientModule owner) {
      this.owner = owner;
   }

   void reset() {
      this.pressed = null;
      this.warned = false;
      this.sentAt = 0L;
   }

   void sent() {
      this.sentAt = System.currentTimeMillis();
   }

   boolean tick(MinecraftClient mc) {
      Dialogs.View view = Dialogs.current(mc);
      if (view != null && view.titled("teleport")) {
         if (view.dialog() == this.pressed) {
            return true;
         }

         if (System.currentTimeMillis() - this.sentAt > 3000L) {
            return false;
         }

         Dialogs.Button confirm = view.labelled("confirm", "yes");
         if (confirm == null) {
            return false;
         }

         if (!this.warned) {
            this.warned = true;
            ModuleChat.send(
               this.owner,
               ModuleChat.body()
                  .warn("Your teleport confirm menu is on")
                  .text(". Turn it off in /settings so requests go out faster. Confirming each one for now.")
            );
         }

         this.pressed = view.dialog();
         Dialogs.press(mc, view, confirm, Map.of());
         return true;
      } else {
         return false;
      }
   }
}
