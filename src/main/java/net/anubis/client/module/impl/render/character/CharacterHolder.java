package net.anubis.client.module.impl.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface CharacterHolder {
   WornCharacter anubis$character();

   void anubis$setCharacter(WornCharacter var1);

   float anubis$seat();

   void anubis$setSeat(float var1);
}
