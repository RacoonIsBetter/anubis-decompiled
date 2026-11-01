package net.anubis.client.module.impl.render;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
enum PeepSong {
   STAR_SHOPPING("starshopping"),
   CRYBABY("crybaby"),
   CRY_ALONE("cryalone"),
   HELLBOY("hellboy"),
   BEAMER_BOY("beamerboy"),
   SPOTLIGHT("spotlight"),
   THE_BRIGHTSIDE("thebrightside", "brightside"),
   SUNLIGHT_ON_YOUR_SKIN("sunlightonyourskin"),
   LIFE_IS_BEAUTIFUL("lifeisbeautiful"),
   FALLING_DOWN("fallingdown"),
   SAVE_THAT_SHIT("savethatshit", "savethatsht"),
   AWFUL_THINGS("awfulthings"),
   WITCHBLADES("witchblades"),
   FOUR_GOLD_CHAINS("4goldchains", "fourgoldchains"),
   GHOST_BOY("ghostboy"),
   GHOST_GIRL("ghostgirl");

   static final PeepSong[] ALL = values();
   private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
   private static final Pattern BRACKETS = Pattern.compile("[(\\[{][^)\\]}]*[)\\]}]");
   private static final Pattern TAIL = Pattern.compile("\\s+(?:[-–—|]|feat\\.?|ft\\.?|with)\\s.*$");
   private static final Pattern VERSION = Pattern.compile("\\s*[-–—]\\s*(?:\\d{4}\\s*)?(?:remaster(?:ed)?|remix(?:ed)?|live)\\b.*$");
   private static final Pattern NOT_NAME = Pattern.compile("[^a-z0-9]");
   private final String[] names;

   PeepSong(String... names) {
      this.names = names;
   }

   static PeepSong of(String title) {
      if (title != null && !title.isBlank()) {
         String plain = Normalizer.normalize(title, Form.NFKC).toLowerCase(Locale.ROOT);
         plain = SPACES.matcher(plain).replaceAll(" ");
         plain = BRACKETS.matcher(plain).replaceAll(" ");
         plain = TAIL.matcher(plain.strip()).replaceAll("");
         plain = VERSION.matcher(plain).replaceAll("");
         plain = NOT_NAME.matcher(plain).replaceAll("");

         for (PeepSong song : ALL) {
            for (String name : song.names) {
               if (name.equals(plain)) {
                  return song;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }
}
