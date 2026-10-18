package net.anubis.client.chat;

import net.anubis.client.config.ThemeStore;
import net.anubis.client.gui.ToggleToasts;
import net.anubis.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;

@Environment(EnvType.CLIENT)
public final class ModuleChat {
   private static final String HEADER = "Anubis Client";
   private static final int DARK_GREY = 5592405;
   private static final int GREY = 11184810;
   private static final int WHITE = 16777215;
   private static final int DANGER = 16733525;
   private static final int GOOD = 5635925;
   private static final int WARN = 16755200;
   private static final int VALUE = 5636095;

   private ModuleChat() {
   }

   public static ModuleChat.Body body() {
      return new ModuleChat.Body();
   }

   public static void send(ClientModule module, Text body) {
      onClientThread(() -> {
         if (module.notificationsOn()) {
            addLine(module, body);
         }
      });
   }

   public static void send(ClientModule module, ModuleChat.Body body) {
      send(module, body.build());
   }

   public static void send(ClientModule module, String text) {
      send(module, Text.literal(text));
   }

   public static void actionBar(ClientModule module, ModuleChat.Body body) {
      Text built = body.build();
      onClientThread(() -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (module.notificationsOn() && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(line(module, built), false);
         }
      });
   }

   public static void toggleLine(ClientModule module, ModuleChat.Body state) {
      ToggleToasts.show(module, module.isEnabled());
   }

   private static void addLine(ClientModule module, Text body) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.inGameHud != null) {
         mc.inGameHud.getChatHud().addMessage(line(module, body));
      }
   }

   private static MutableText line(ClientModule module, Text body) {
      return Text.empty()
         .append(Text.literal("[").withColor(5592405))
         .append(Text.literal("Anubis Client").withColor(ThemeStore.get().accent.rgb()))
         .append(Text.literal("] ").withColor(5592405))
         .append(Text.literal(module.name()).withColor(16777215))
         .append(Text.literal(" : ").withColor(5592405))
         .append(Text.empty().withColor(11184810).append(body));
   }

   private static void onClientThread(Runnable task) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.isOnThread()) {
         task.run();
      } else {
         mc.execute(task);
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class Body {
      private final MutableText root = Text.empty().withColor(11184810);

      private Body() {
      }

      public ModuleChat.Body text(String text) {
         this.root.append(Text.literal(text));
         return this;
      }

      public ModuleChat.Body name(String name) {
         return this.part(name, 16777215);
      }

      public ModuleChat.Body danger(String text) {
         return this.part(text, 16733525);
      }

      public ModuleChat.Body good(String text) {
         return this.part(text, 5635925);
      }

      public ModuleChat.Body warn(String text) {
         return this.part(text, 16755200);
      }

      public ModuleChat.Body value(Object value) {
         return this.part(String.valueOf(value), 5636095);
      }

      public ModuleChat.Body coords(int x, int y, int z) {
         return this.part(x + ", " + y + ", " + z, 5636095);
      }

      public ModuleChat.Body append(Text component) {
         this.root.append(component);
         return this;
      }

      public Text build() {
         return this.root.copy();
      }

      private ModuleChat.Body part(String text, int rgb) {
         this.root.append(Text.literal(text).withColor(rgb));
         return this;
      }
   }
}
