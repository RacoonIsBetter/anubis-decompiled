package net.anubis.client.basefinder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * One kind of evidence that a chunk has seen player activity. Each signal contributes
 * {@code weight * strength} to a chunk, and contributions combine as independent odds:
 * {@code p = 1 - prod(1 - contribution)}.
 */
@Environment(EnvType.CLIENT)
public enum Signal {
   PLAYER("Player underground", 0.70F, 1, Group.PLAYERS),
   HIDDEN_PLAYER("Hidden player activity", 0.65F, 1, Group.PLAYERS),
   STORAGE("Player storage", 0.60F, 3, Group.BLOCKS),
   PLACED_ENTITIES("Item frames / armor stands", 0.60F, 2, Group.ENTITIES),
   PETS("Pets / named mobs", 0.55F, 1, Group.ENTITIES),
   ROTATED_DEEPSLATE("Rotated deepslate", 0.55F, 3, Group.BLOCKS),
   TUNNEL("Hand-mined tunnel", 0.50F, 2, Group.TUNNELS),
   ROOM("Dug-out room", 0.50F, 2, Group.TUNNELS),
   SIGNS("Signs / banners / heads", 0.50F, 2, Group.BLOCKS),
   UTILITY("Crafted blocks", 0.45F, 3, Group.BLOCKS),
   SURFACE_BUILD("Surface build", 0.45F, 24, Group.BLOCKS),
   NETHER_PORTAL("Lit nether portal", 0.45F, 1, Group.BLOCKS),
   FARM("Buried farm", 0.45F, 4, Group.BLOCKS),
   ANIMAL_FARM("Animal farm", 0.45F, 1, Group.ENTITIES),
   REDSTONE("Redstone", 0.40F, 4, Group.BLOCKS),
   SHAFT("Hand-mined shaft", 0.40F, 1, Group.TUNNELS),
   LIGHTING("Placed lights", 0.35F, 4, Group.BLOCKS),
   AMETHYST("Grown amethyst", 0.30F, 1, Group.GROWTH),
   BUILDING("Building blocks", 0.30F, 8, Group.BLOCKS),
   KELP("Grown kelp", 0.25F, 1, Group.GROWTH),
   OBSIDIAN("Obsidian", 0.15F, 6, Group.BLOCKS),
   DRIPSTONE("Grown dripstone", 0.12F, 2, Group.GROWTH);

   public static final Signal[] VALUES = values();

   public final String label;
   public final float weight;
   /** Number of finds at which the signal reaches full strength. */
   public final int saturation;
   public final Group group;

   Signal(String label, float weight, int saturation, Group group) {
      this.label = label;
      this.weight = weight;
      this.saturation = saturation;
      this.group = group;
   }

   public float strengthFor(int count) {
      return count <= 0 ? 0.0F : Math.min(1.0F, (float)count / this.saturation);
   }

   @Environment(EnvType.CLIENT)
   public enum Group {
      BLOCKS,
      TUNNELS,
      GROWTH,
      PLAYERS,
      ENTITIES
   }
}
