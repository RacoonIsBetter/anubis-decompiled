package net.anubis.client.addon;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MenuPanels {
   private static volatile Supplier<? extends MenuPanels.Panel> accounts;

   private MenuPanels() {
   }

   public static void registerAccounts(Supplier<? extends MenuPanels.Panel> factory) {
      accounts = factory;
   }

   public static MenuPanels.Panel newAccountsPanel() {
      Supplier<? extends MenuPanels.Panel> factory = accounts;
      return factory == null ? null : factory.get();
   }

   @Environment(EnvType.CLIENT)
   public interface Panel {
      void open();

      boolean isOpen();

      boolean keyPressed(int var1);

      void render(float var1, float var2, float var3, float var4);
   }
}
