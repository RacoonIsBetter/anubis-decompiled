package net.anubis.client.basefinder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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

   @Environment(EnvType.CLIENT)
   public enum Kind {
      CONTAINER("Container", 0xFFFFB238),
      WORKSTATION("Workstation", 0xFFB070FF),
      DOOR("Door", 0xFF78C8FF),
      MINING("Mining", 0xFFFF6A50),
      BUILDING("Building", 0xFF6AE070);

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

      return heard != null && !seen(level, heard.pos) ? heard : null;
   }

   private static HeardSound fromSound(SoundEvent sound, SoundCategory category, Vec3d pos) {
      if (category != SoundCategory.BLOCKS && category != SoundCategory.PLAYERS) {
         return null;
      }

      Identifier id = sound.id();
      String path = id.getPath();
      if (path.startsWith("block.chest.") || path.startsWith("block.barrel.") || path.startsWith("block.ender_chest.") || path.startsWith("block.shulker_box.")
         || path.startsWith("block.trapped_chest.")) {
         return new HeardSound(pos, HeardSound.Kind.CONTAINER, path.substring(6).replace('_', ' ').replace('.', ' '));
      }

      if (path.startsWith("block.anvil.use") || path.startsWith("block.enchantment_table.") || path.startsWith("block.grindstone.") || path.startsWith("block.smithing_table.")
         || path.startsWith("block.brewing_stand.") || path.startsWith("block.crafter.") || path.startsWith("block.respawn_anchor.") || path.startsWith("block.beacon.")) {
         return new HeardSound(pos, HeardSound.Kind.WORKSTATION, path.substring(6).replace('_', ' ').replace('.', ' '));
      }

      if (path.contains("door.") || path.contains("trapdoor.") || path.contains("fence_gate.")) {
         return new HeardSound(pos, HeardSound.Kind.DOOR, path.substring(path.indexOf('.') + 1).replace('_', ' ').replace('.', ' '));
      }

      if (path.startsWith("block.") && path.endsWith(".place") && category == SoundCategory.BLOCKS) {
         String block = path.substring(6, path.length() - 6);
         // Plants, crops and gravel land or grow on their own; everything else was placed by someone.
         if (!block.contains("grass") && !block.contains("crop") && !block.contains("sapling") && !block.contains("gravel") && !block.contains("sand")
            && !block.contains("pointed_dripstone") && !block.contains("amethyst") && !block.contains("cave_vines") && !block.contains("sculk")) {
            return new HeardSound(pos, HeardSound.Kind.BUILDING, "placing " + block.replace('_', ' '));
         }
      }

      return null;
   }

   /** Blocks that break by themselves: fluids, plants, leaves, snow, ice, fire, and grass sheep eat. */
   private static boolean natural(BlockState state) {
      return state.isAir() || state.isOf(Blocks.GRASS_BLOCK) || !state.getFluidState().isEmpty() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.REPLACEABLE)
         || state.isIn(BlockTags.CROPS) || state.isIn(BlockTags.SAPLINGS) || state.isIn(BlockTags.ICE) || state.isIn(BlockTags.SNOW) || state.isIn(BlockTags.FIRE)
         || state.isIn(BlockTags.CAVE_VINES);
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
