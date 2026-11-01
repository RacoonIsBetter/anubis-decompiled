package net.anubis.client.easyplace;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class EasyPlace {
   public static final Logger LOG = LoggerFactory.getLogger("Anubis Easy Place");

   private EasyPlace() {
   }
}
