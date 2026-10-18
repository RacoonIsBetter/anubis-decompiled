package net.anubis.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientBrand {
   public static final String NAME = "Anubis";
   public static final String WINDOW_TITLE = "Anubis Client";
   public static final String LOGGER_NAME = "Anubis";

   private ClientBrand() {
   }
}
