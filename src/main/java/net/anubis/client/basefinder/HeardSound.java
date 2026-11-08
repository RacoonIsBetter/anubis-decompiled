package net.anubis.client.basefinder;

import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;

/**
 * A sound the server sent that only a player makes: a chest or barrel opening, an anvil or
 * enchanting table in use, a door, or a block mined or placed. Servers relay these to everyone
 * nearby, including players who can't see the source, so they give away people working out of
 * sight. (Anti-xray plugins such as ChunkVeil can cancel them inside the hidden zone.)
 */
@Environment(EnvType.CLIENT)
public record HeardSound(Vec3d pos, HeardSound.Kind kind, String what) {
   /** Players and their pets are this close to the sound when it is just them being heard. */
   public static final double SEEN_RADIUS = 6.0;
   /** Copper golems reach chests within a couple of blocks; this is their sorting range plus margin. */
   private static final double GOLEM_RADIUS = 4.0;

   @Environment(EnvType.CLIENT)
   public enum Kind {
      CONTAINER("Container", 0xFFFFB238),
      WORKSTATION("Workstation", 0xFFB070FF),
      DOOR("Doors / Switches", 0xFF78C8FF),
      MINING("Mining", 0xFFFF6A50),
      BUILDING("Building", 0xFF6AE070),
      PLAYER("Player", 0xFFFF4D9A),
      /** Running redstone: pistons, dispensers, crafters, bulbs, minecarts. Always player-built, so a base is near. */
      MACHINERY("Base activity", 0xFF3FD8C4);

      public final String label;
      public final int color;

      Kind(String label, int color) {
         this.label = label;
         this.color = color;
      }
   }

   /** The player-made sound in {@code packet}, or null when it is something else or someone visible made it. */
   public static HeardSound classify(Packet<?> packet, ClientWorld level) {
      HeardSound heard = null;
      if (packet instanceof PlaySoundS2CPacket sound) {
         heard = fromSound(sound.getSound().value(), sound.getCategory(), new Vec3d(sound.getX(), sound.getY(), sound.getZ()));
      } else if (packet instanceof WorldEventS2CPacket event && !event.isGlobal() && event.getEventId() == WorldEvents.BLOCK_BROKEN) {
         BlockState state = Block.getStateFromRawId(event.getData());
         if (!natural(state)) {
            heard = new HeardSound(Vec3d.ofCenter(event.getPos()), HeardSound.Kind.MINING, "mining " + name(state.getBlock()));
         }
      }

      if (heard == null || seen(level, heard.pos)) {
         return null;
      }

      // Copper golems open chests and copper chests on their own to sort items.
      if (heard.kind == HeardSound.Kind.CONTAINER && golemNear(level, heard.pos)) {
         return null;
      }

      return heard;
   }

   private static HeardSound fromSound(SoundEvent sound, SoundCategory category, Vec3d pos) {
      Identifier id = sound.id();
      String path = id.getPath();
      // Things only players throw or launch; the game files these under neutral or ambient sounds.
      if (THROWN.contains(path)) {
         return new HeardSound(pos, HeardSound.Kind.PLAYER, readable(path));
      }

      if (MACHINERY.stream().anyMatch(path::startsWith)) {
         return new HeardSound(pos, HeardSound.Kind.MACHINERY, readable(path));
      }

      if (category != SoundCategory.BLOCKS && category != SoundCategory.PLAYERS) {
         return null;
      }

      if (path.startsWith("block.chest.") || path.startsWith("block.copper_chest") || path.startsWith("block.barrel.") || path.startsWith("block.ender_chest.") || path.startsWith("block.shulker_box.")
         || path.startsWith("block.trapped_chest.") || path.startsWith("block.chiseled_bookshelf.") || path.startsWith("block.decorated_pot.insert")) {
         return new HeardSound(pos, HeardSound.Kind.CONTAINER, path.substring(6).replace('_', ' ').replace('.', ' '));
      }

      if (path.startsWith("block.anvil.use") || path.startsWith("block.enchantment_table.") || path.startsWith("block.grindstone.") || path.startsWith("block.smithing_table.")
         || path.startsWith("block.brewing_stand.") || path.startsWith("block.respawn_anchor.") || path.startsWith("block.beacon.")) {
         return new HeardSound(pos, HeardSound.Kind.WORKSTATION, path.substring(6).replace('_', ' ').replace('.', ' '));
      }

      if (path.contains("door.") || path.contains("trapdoor.") || path.contains("fence_gate.") || path.startsWith("block.lever.") || path.contains("_button.click")) {
         return new HeardSound(pos, HeardSound.Kind.DOOR, path.substring(path.indexOf('.') + 1).replace('_', ' ').replace('.', ' '));
      }

      if (path.startsWith("block.") && path.endsWith(".place") && category == SoundCategory.BLOCKS) {
         String block = path.substring(6, path.length() - 6);
         // Plants, crops and gravel land or grow on their own; everything else was placed by someone.
         if (!block.contains("grass") && !block.contains("crop") && !block.contains("sapling") && !block.contains("gravel") && !block.contains("sand")
            && !block.contains("pointed_dripstone") && !block.contains("amethyst") && !block.contains("cave_vines") && !block.contains("sculk")) {
            // Place sounds belong to a sound family shared by many blocks (stone, wood, metal...),
            // so the sound names the family, not the exact block.
            return new HeardSound(pos, HeardSound.Kind.BUILDING, "placing a " + block.replace('_', ' ') + "-sounding block");
         }
      }

      // Everything else the game files under player sounds: hits, eating, drinking, level-ups,
      // armour, pickups, buckets, bows, crossbows, tridents, shields.
      if (category == SoundCategory.PLAYERS) {
         return new HeardSound(pos, HeardSound.Kind.PLAYER, readable(path));
      }

      return null;
   }

   /** Sound id prefixes of running machinery; minecarts and TNT are filed under neutral/block sounds. */
   private static final List<String> MACHINERY = List.of(
      "block.piston.", "block.dispenser.", "block.crafter.", "block.copper_bulb.turn_", "block.redstone_torch.burnout", "block.comparator.click",
      "block.tripwire.click_", "block.note_block.", "entity.minecart.riding", "entity.minecart.inside", "entity.tnt.primed"
   );

   private static final Set<String> THROWN = Set.of(
      "entity.ender_pearl.throw", "entity.splash_potion.throw", "entity.lingering_potion.throw", "entity.experience_bottle.throw",
      "entity.firework_rocket.launch", "entity.fishing_bobber.throw", "entity.snowball.throw", "entity.egg.throw", "entity.wind_charge.throw"
   );

   /** {@code entity.player.attack.strong} becomes "player attack strong". */
   private static String readable(String path) {
      String text = path.startsWith("entity.") || path.startsWith("block.") ? path.substring(path.indexOf('.') + 1) : path;
      return text.replace('_', ' ').replace('.', ' ');
   }

   /** Blocks that break by themselves: fluids, plants, leaves, snow, ice, fire, and grass sheep eat. */
   private static boolean natural(BlockState state) {
      return state.isAir() || state.isOf(Blocks.GRASS_BLOCK) || !state.getFluidState().isEmpty() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.REPLACEABLE)
         || state.isIn(BlockTags.CROPS) || state.isIn(BlockTags.SAPLINGS) || state.isIn(BlockTags.ICE) || state.isIn(BlockTags.SNOW) || state.isIn(BlockTags.FIRE)
         || state.isIn(BlockTags.CAVE_VINES);
   }

   private static boolean golemNear(ClientWorld level, Vec3d pos) {
      return !level.getEntitiesByClass(CopperGolemEntity.class, new Box(pos, pos).expand(GOLEM_RADIUS), golem -> true).isEmpty();
   }

   /** Anyone we can see (us included) close enough to have made the sound. */
   private static boolean seen(ClientWorld level, Vec3d pos) {
      double radius = SEEN_RADIUS * SEEN_RADIUS;

      for (AbstractClientPlayerEntity player : level.getPlayers()) {
         if (player.squaredDistanceTo(pos) < radius) {
            return true;
         }
      }

      return false;
   }

   private static String name(Block block) {
      return Registries.BLOCK.getId(block).getPath().replace('_', ' ');
   }

   public BlockPos blockPos() {
      return BlockPos.ofFloored(this.pos);
   }
}
