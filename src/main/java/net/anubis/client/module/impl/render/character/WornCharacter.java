package net.anubis.client.module.impl.render.character;

import net.anubis.client.module.impl.render.character.kit.CharacterModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class WornCharacter {
   final CharacterModel model;
   final RenderLayer solid;
   final RenderLayer decal;
   final RenderLayer ghost;

   WornCharacter(CharacterModel model, Identifier texture) {
      this.model = model;
      this.solid = RenderLayers.entitySolid(texture);
      this.decal = RenderLayers.entityTranslucent(texture);
      this.ghost = RenderLayers.itemEntityTranslucentCull(texture);
   }
}
