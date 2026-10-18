package net.anubis.client.module.impl.render.character;

import java.util.ArrayList;
import java.util.List;
import net.anubis.client.module.impl.render.character.designs.Aoi;
import net.anubis.client.module.impl.render.character.designs.Luna;
import net.anubis.client.module.impl.render.character.designs.Mika;
import net.anubis.client.module.impl.render.character.designs.Miku;
import net.anubis.client.module.impl.render.character.designs.Sakura;
import net.anubis.client.module.impl.render.character.designs.Tralalero;
import net.anubis.client.module.impl.render.character.designs.TungTungSahur;
import net.anubis.client.module.impl.render.character.kit.BakedDesign;
import net.anubis.client.module.impl.render.character.kit.CharacterDesign;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CharacterCatalog {
   private static final List<CharacterDesign> BUILT_IN = List.of(
      new Sakura(),
      new Luna(),
      new Mika(),
      new Aoi(),
      new Miku(),
      BakedDesign.builtIn("cj", "CJ", "/assets/anubis/characters/cj.anc"),
      BakedDesign.builtIn("neighbor", "Neighbor", "/assets/anubis/characters/neighbor.anc"),
      new TungTungSahur(),
      new Tralalero()
   );
   private static volatile List<CharacterDesign> designs = BUILT_IN;

   private CharacterCatalog() {
   }

   public static List<CharacterDesign> all() {
      return designs;
   }

   static boolean refresh() {
      List<CharacterDesign> next = new ArrayList<>(BUILT_IN);
      List<String> taken = new ArrayList<>(names(BUILT_IN));

      for (BakedDesign custom : CustomCharacters.scan()) {
         if (!taken.contains(custom.name())) {
            taken.add(custom.name());
            next.add(custom);
         }
      }

      List<CharacterDesign> old = designs;
      designs = List.copyOf(next);
      if (old.size() != next.size()) {
         return true;
      }

      for (int i = 0; i < next.size(); i++) {
         if (!old.get(i).id().equals(next.get(i).id())) {
            return true;
         }
      }

      return false;
   }

   static List<String> names() {
      return names(designs);
   }

   private static List<String> names(List<CharacterDesign> list) {
      List<String> out = new ArrayList<>(list.size());

      for (CharacterDesign design : list) {
         out.add(design.name());
      }

      return out;
   }

   static CharacterDesign byName(String name) {
      List<CharacterDesign> list = designs;

      for (CharacterDesign design : list) {
         if (design.name().equals(name)) {
            return design;
         }
      }

      return list.get(0);
   }
}
