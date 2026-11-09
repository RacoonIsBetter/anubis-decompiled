// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package net.anubis.client.sound;

import com.example.client.mixin.SoundEntityEventAccessor;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;

import java.util.HashMap;
import java.util.Map;

import static net.anubis.client.sound.SoundObservation.Actor;
import static net.anubis.client.sound.SoundObservation.Route;

/** Main-thread capture bridge. It never requests data, sends packets, or guesses a nearby actor. */
public final class SoundCapture {
    private static int packetDepth;
    private static int excludedDepth;
    private static final Map<BlockPos, Integer> openerCounts = new HashMap<>();
    private record MiningState(BlockPos pos, int progress) { }
    private static final Map<Integer, MiningState> mining = new HashMap<>();
    private static final StackWalker dispatchWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private SoundCapture() { }
    public static void clear() { packetDepth = 0; excludedDepth = 0; openerCounts.clear(); mining.clear(); LocalActionTracker.clear(); }
    public static void forgetChunk(int x, int z) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        openerCounts.keySet().removeIf(pos -> (pos.getX() >> 4) == x && (pos.getZ() >> 4) == z);
        mining.values().removeIf(state -> (state.pos.getX() >> 4) == x && (state.pos.getZ() >> 4) == z);
    }
    public static boolean active() { return SoundDetectorController.enabled() && MinecraftClient.getInstance().isOnThread(); }
    public static void enterPacket() { if (MinecraftClient.getInstance().isOnThread()) packetDepth++; }
    public static void leavePacket() { if (MinecraftClient.getInstance().isOnThread()) packetDepth = Math.max(0, packetDepth - 1); }
    public static void withoutCapture(Runnable action) {
        excludedDepth++;
        try { action.run(); } finally { excludedDepth = Math.max(0, excludedDepth - 1); }
    }
    public static void sound(PlaySoundS2CPacket packet) {
        if (!active()) return;
        emit(Route.POSITIONAL, SoundCatalog.id(packet.getSound().value()), packet.getCategory(),
                new Vec3d(packet.getX(), packet.getY(), packet.getZ()), null, -1, "", null,
                packet.getVolume(), packet.getPitch(), packet.getSeed(), 0, 0, false);
    }
    public static void sound(PlaySoundFromEntityS2CPacket packet) {
        if (!active()) return;
        Entity entity = entity(packet.getEntityId());
        emit(Route.ENTITY_SOUND, SoundCatalog.id(packet.getSound().value()), packet.getCategory(),
                entity == null ? null : entity.getEntityPos(), entity, packet.getEntityId(), "", null,
                packet.getVolume(), packet.getPitch(), packet.getSeed(), 0, 0, false);
    }
    public static void clientSound(SoundEvent sound, SoundCategory source, Vec3d position, Entity entity, float volume, float pitch) {
        if (!active() || excludedDepth > 0) return;
        if (packetDepth > 0) {
            // A third-party handler can throw before RETURN. A stale depth must not silence later dispatch forever.
            if (dispatchWalker.walk(frames -> frames.limit(48).anyMatch(frame -> frame.getDeclaringClass() == ClientPlayNetworkHandler.class))) return;
            packetDepth = 0;
        }
        emit(Route.CLIENT_DERIVED, SoundCatalog.id(sound), source, position, entity, entity == null ? -1 : entity.getId(),
                "", null, volume, pitch, 0, 0, 0, false);
    }
    public static void block(BlockEventS2CPacket packet) {
        if (!active()) return;
        Block block = packet.getBlock();
        BlockPos pos = packet.getPos();
        SoundCatalog.Classification action = null;
        if ((block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock) && packet.getType() == 1) {
            // The count can change without an open/close transition. Never count each viewer as a new opening.
            BlockState state = loadedState(pos);
            if (state != null && state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)
                    && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                BlockPos partner = ChestBlock.getPosInFrontOf(pos, state);
                if (partner.asLong() < pos.asLong()) pos = partner;
            }
            Integer previous = openerCounts.put(pos.toImmutable(), packet.getData());
            if (openerCounts.size() > 512) { openerCounts.clear(); openerCounts.put(pos.toImmutable(), packet.getData()); }
            boolean opening = packet.getData() > 0;
            if (previous != null && (previous > 0) == opening) return;
            // An initial zero only supplies state; it is not proof that a close just occurred.
            if (previous == null && !opening) return;
            action = c("containers", opening ? "container.open" : "container.close", opening ? "Container opened" : "Container closed", 50);
        } else if (block instanceof PistonBlock && packet.getType() >= 0 && packet.getType() <= 2) {
            boolean extending = packet.getType() == 0;
            action = c("machinery", extending ? "piston.extend" : "piston.retract", extending ? "Piston extended" : "Piston retracted", 15);
        } else if (block instanceof NoteBlock && packet.getType() == 0) action = c("music", "note.play", "Note block played", 15);
        else if (block instanceof BellBlock && packet.getType() == 1) action = c("music", "bell.ring", "Bell rang", 15);
        if (action == null) action = c("custom", "block.event", "Unclassified block event", 0);
        emit(Route.BLOCK_EVENT, "", null, Vec3d.ofCenter(pos), null, -1, SoundCatalog.blockId(block), action,
                0, 0, 0, packet.getType(), packet.getData(), false);
    }
    public static void level(WorldEventS2CPacket packet) {
        if (!active()) return;
        int code = packet.getEventId();
        SoundCatalog.Classification action = switch (code) {
            case WorldEvents.DISPENSER_DISPENSES, WorldEvents.DISPENSER_LAUNCHES_PROJECTILE -> c("machinery", "dispenser.dispense", "Dispenser / dropper activated", 15);
            case WorldEvents.DISPENSER_FAILS -> c("machinery", "dispenser.fail", "Dispenser / dropper failed", 15);
            case WorldEvents.CRAFTER_CRAFTS -> c("machinery", "crafter.craft", "Crafter crafted", 15);
            case WorldEvents.CRAFTER_FAILS -> c("machinery", "crafter.fail", "Crafter failed", 15);
            case WorldEvents.ANVIL_USED -> c("workstations", "workstation.anvil_use", "Anvil used", 50);
            case WorldEvents.ANVIL_LANDS, WorldEvents.ANVIL_DESTROYED -> c("building", "anvil.land", "Anvil landed / broke", 25);
            case WorldEvents.GRINDSTONE_USED -> c("workstations", "workstation.grindstone_use", "Grindstone used", 50);
            case WorldEvents.SMITHING_TABLE_USED -> c("workstations", "workstation.smithing_table_use", "Smithing table used", 50);
            case WorldEvents.LECTERN_BOOK_PAGE_TURNED -> c("workstations", "lectern.page", "Lectern page turned", 30);
            case WorldEvents.JUKEBOX_STARTS_PLAYING -> c("music", "jukebox.play", "Jukebox started", 30);
            case WorldEvents.JUKEBOX_STOPS_PLAYING -> c("music", "jukebox.stop", "Jukebox stopped", 15);
            case WorldEvents.BLOCK_BROKEN -> c("building", "building.break", "Block broken", 25);
            case WorldEvents.BLOCK_WAXED, WorldEvents.WAX_REMOVED, WorldEvents.BLOCK_SCRAPED -> c("building", "copper.modify", "Copper waxed / scraped", 30);
            case WorldEvents.BLOCK_FINISHED_BRUSHING -> c("farming", "archaeology.brush", "Brushing completed", 30);
            case WorldEvents.COMPOSTER_USED -> c("farming", "composter.fill", "Composter filled", 15);
            case WorldEvents.BREWING_STAND_BREWS -> c("machinery", "brewing.complete", "Brewing completed", 15);
            case WorldEvents.SCULK_SHRIEKS -> c("sculk", "sculk.shriek", "Sculk shriek", 0);
            case WorldEvents.VAULT_ACTIVATES, WorldEvents.VAULT_DEACTIVATES, WorldEvents.VAULT_EJECTS_ITEM,
                    WorldEvents.TRIAL_SPAWNER_DETECTS_PLAYER, WorldEvents.OMINOUS_TRIAL_SPAWNER_DETECTS_PLAYER -> c("sculk", "generated.activity", "Trial / vault activity", 0);
            default -> c("natural", "world.effect", "World effect " + code, 0);
        };
        String blockId = "";
        if (code == WorldEvents.BLOCK_BROKEN) blockId = SoundCatalog.blockId(Block.getStateFromRawId(packet.getData()).getBlock());
        else {
            BlockState state = loadedState(packet.getPos());
            if (state != null && !state.isAir()) blockId = SoundCatalog.blockId(state.getBlock());
        }
        emit(Route.LEVEL_EVENT, "", null, packet.isGlobal() ? null : Vec3d.ofCenter(packet.getPos()), null, -1, blockId,
                action, 0, 0, 0, code, packet.getData(), packet.isGlobal());
    }
    public static void mining(BlockBreakingProgressS2CPacket packet) {
        if (!active()) return;
        if (packet.getProgress() < 0 || packet.getProgress() > 9) { mining.remove(packet.getEntityId()); return; }
        MiningState previous = mining.put(packet.getEntityId(), new MiningState(packet.getPos().toImmutable(), packet.getProgress()));
        if (mining.size() > 512) mining.clear();
        if (previous != null && previous.pos.equals(packet.getPos()) && previous.progress <= packet.getProgress()) return;
        BlockState state = loadedState(packet.getPos());
        emit(Route.MINING, "", null, Vec3d.ofCenter(packet.getPos()), entity(packet.getEntityId()), packet.getEntityId(),
                state == null ? "" : SoundCatalog.blockId(state.getBlock()), c("building", "building.hit", "Block breaking", 10),
                0, 0, 0, packet.getProgress(), 0, false);
    }
    public static void entityEvent(EntityStatusS2CPacket packet) {
        if (!active()) return;
        Entity entity = packet.getEntity(MinecraftClient.getInstance().world);
        int id = ((SoundEntityEventAccessor) packet).anubis$soundEntityId();
        SoundCatalog.Classification action = switch (packet.getStatus()) {
            case EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES -> c("combat", "combat.death", "Entity died", 25);
            case EntityStatuses.USE_TOTEM_OF_UNDYING -> c("combat", "combat.totem", "Totem activated", 50);
            case EntityStatuses.CONSUME_ITEM -> c("farming", "item.complete", "Item use completed", 25);
            case EntityStatuses.EXPLODE_FIREWORK_CLIENT -> c("explosions", "firework.explode", "Firework exploded", 15);
            default -> c("custom", "entity.status", "Entity status " + packet.getStatus(), 0);
        };
        emit(Route.ENTITY_EVENT, "", null, entity == null ? null : entity.getEntityPos(), entity, id, "", action,
                0, 0, 0, packet.getStatus(), 0, false);
    }
    public static void damage(EntityDamageS2CPacket packet) {
        if (!active()) return;
        Entity victim = entity(packet.entityId());
        Entity cause = entity(packet.sourceCauseId());
        if (cause != null && cause == MinecraftClient.getInstance().player && victim != null) {
            // An outgoing attack attempt alone is insufficient. This server event confirms its cause.
            LocalActionTracker.record(victim.getEntityPos(), "combat");
            LocalActionTracker.record(cause.getEntityPos(), "combat");
        }
        // The victim's identity is not the attacker's. Only the explicit cause is eligible for attribution.
        emit(Route.DAMAGE, "", null, victim == null ? packet.sourcePosition().orElse(null) : victim.getEntityPos(), cause,
                packet.sourceCauseId(), "", c("combat", "combat.action", "Damage event", 35), 0, 0, 0, packet.entityId(), packet.sourceDirectId(), false);
    }
    public static void explosion(ExplosionS2CPacket packet) {
        if (!active()) return;
        emit(Route.EXPLOSION, SoundCatalog.id(packet.explosionSound().value()), null, packet.center(), null, -1, "",
                c("explosions", "explosion", "Explosion", 55), 0, 0, 0, 0, packet.blockCount(), false);
    }
    public static void stop(StopSoundS2CPacket packet) {
        if (!active()) return;
        emit(Route.STOP_SOUND, packet.getSoundId() == null ? "" : packet.getSoundId().toString(), packet.getCategory(), null, null, -1, "",
                c("custom", "sound.stop", "Sound stopped", 0), 0, 0, 0, 0, 0, false);
    }
    private static void emit(Route route, String sound, SoundCategory source, Vec3d pos, Entity entity, int entityId,
                             String blockId, SoundCatalog.Classification classification, float volume, float pitch, long seed,
                             int eventCode, int data, boolean global) {
        if (!active() || excludedDepth > 0) return;
        var c = classification == null ? SoundCatalog.classify(sound) : classification;
        Actor actor = entity == null ? Actor.UNKNOWN : entity == MinecraftClient.getInstance().player ? Actor.SELF
                : entity instanceof PlayerEntity ? Actor.PLAYER : Actor.NON_PLAYER;
        long now = System.nanoTime();
        double x = pos == null ? Double.NaN : pos.x, y = pos == null ? Double.NaN : pos.y, z = pos == null ? Double.NaN : pos.z;
        boolean probableSelf = actor != Actor.PLAYER && actor != Actor.SELF && pos != null && LocalActionTracker.matches(x, y, z, c.category(), now);
        SoundDetectorController.observe(new SoundObservation(now, route, sound, c.category(), c.actionKey(), c.label(), blockId,
                x, y, z, entityId, entity == null ? "" : entity.getUuid().toString(), entity instanceof PlayerEntity ? entity.getName().getString() : "",
                actor, volume, pitch, seed, eventCode, data, probableSelf, c.score(), global)
                .withSoundSource(source == null ? "" : source.getName()));
    }
    private static Entity entity(int id) { return MinecraftClient.getInstance().world == null || id < 0 ? null : MinecraftClient.getInstance().world.getEntityById(id); }
    private static BlockState loadedState(BlockPos pos) {
        var level = MinecraftClient.getInstance().world;
        return level != null && level.isChunkLoaded(pos) ? level.getBlockState(pos) : null;
    }
    private static SoundCatalog.Classification c(String category, String action, String label, int score) { return SoundCatalog.c(category, action, label, score); }
    public static String interactionCategory(Block block) {
        if (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof BarrelBlock
                || block instanceof ChiseledBookshelfBlock || block instanceof ShelfBlock || block instanceof DecoratedPotBlock) return "containers";
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock || block instanceof ButtonBlock || block instanceof LeverBlock) return "switches";
        if (block instanceof NoteBlock || block instanceof BellBlock || block instanceof JukeboxBlock) return "music";
        return "workstations";
    }
}
