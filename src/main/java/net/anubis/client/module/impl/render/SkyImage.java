package net.anubis.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
interface SkyImage extends AutoCloseable {
   int width();

   int height();

   @Override
   void close();
}
