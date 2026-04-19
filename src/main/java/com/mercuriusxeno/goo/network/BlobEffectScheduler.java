package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.GooMobEffects;
import com.mercuriusxeno.goo.effect.WorldEffects;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Schedules and applies delayed goo effects after blob flight completes.
 * Manages the pending-effect queue and plays impact sounds on arrival.
 * Extracted from {@link BlobThrowHandler} to keep per-class method counts manageable.
 */
final class BlobEffectScheduler {

    /** Sound volume for throw event. */
    private static final float THROW_SOUND_VOLUME = 0.5f;
    /** Base pitch for throw sound. */
    private static final float THROW_PITCH_BASE = 0.4f;
    /** Pitch randomness range for throw sound. */
    private static final float THROW_PITCH_RANGE = 0.4f;
    /** Minimum pitch offset for throw sound. */
    private static final float THROW_PITCH_OFFSET = 0.8f;
    /** Sound volume for impact event. */
    private static final float IMPACT_SOUND_VOLUME = 1.0f;
    /** Base pitch for impact sound. */
    private static final float IMPACT_PITCH_BASE = 0.9f;
    /** Pitch randomness range for impact sound. */
    private static final float IMPACT_PITCH_RANGE = 0.2f;
    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;

    /** Log: entity no longer exists at blob arrival. */
    private static final String LOG_ENTITY_GONE = "Blob arrived but entity {} no longer exists";

    /** Pending effects waiting for their blob to arrive. */
    private static final List<PendingEffect> PENDING_EFFECTS = new ArrayList<>();

    private BlobEffectScheduler() {}

    /**
     * Plays the throw sound and queues a pending effect for blob arrival.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param gooType     the goo type being thrown
     * @param travelTicks the number of ticks until arrival
     */
    static void scheduleEffect(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType, int travelTicks) {
        playThrowSound(player, gooType);
        enqueueArrival(player, payload, gooType, travelTicks);
    }

    /**
     * Plays the throw sound at the player's position. Glow uses a
     * custom laser sound; all other types use the snowball throw.
     *
     * @param player  the throwing player
     * @param gooType the goo type being thrown
     */
    static void playThrowSound(ServerPlayer player, GooType gooType) {
        ServerLevel level = player.level();
        SoundEvent sound = gooType == GooType.GLOW
                ? GooSounds.GLOW_THROW.get()
                : SoundEvents.SNOWBALL_THROW;
        float pitch = THROW_PITCH_BASE / (level.getRandom().nextFloat() * THROW_PITCH_RANGE + THROW_PITCH_OFFSET);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, THROW_SOUND_VOLUME, pitch);
    }

    /**
     * Queues a pending effect for the blob's arrival tick.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param gooType     the goo type being thrown
     * @param travelTicks the number of ticks until arrival
     */
    static void enqueueArrival(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType, int travelTicks) {
        ServerLevel level = player.level();
        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        Direction face = BlobThrowHandler.directionFromOrdinal(payload.targetFace());
        PENDING_EFFECTS.add(new PendingEffect(
                arrivalTick, level, player, gooType,
                payload.targetEntityId(), payload.targetPos(), face,
                payload.abilityId()));
    }

    /**
     * Returns true if there are pending effects to process.
     *
     * @return true if the queue is non-empty
     */
    static boolean hasPending() {
        return !PENDING_EFFECTS.isEmpty();
    }

    /**
     * Applies and removes all effects whose blobs have arrived.
     *
     * @param currentTick the current server tick
     */
    static void drainArrivedEffects(int currentTick) {
        List<PendingEffect> ready = new ArrayList<>();
        Iterator<PendingEffect> it = PENDING_EFFECTS.iterator();
        while (it.hasNext()) {
            PendingEffect pe = it.next();
            if (currentTick >= pe.arrivalTick) {
                ready.add(pe);
                it.remove();
            }
        }
        for (PendingEffect pe : ready) {
            applyEffect(pe);
        }
    }

    /**
     * Applies the goo effect at the target location or entity, with impact sound.
     *
     * @param pe the pending effect to apply
     */
    static void applyEffect(PendingEffect pe) {
        if (pe.targetEntityId >= 0) {
            applyEntityEffect(pe);
        } else {
            applyBlockEffect(pe);
        }
    }

    /**
     * Applies the goo effect to a living entity target with impact sound.
     *
     * @param pe the pending effect targeting an entity
     */
    static void applyEntityEffect(PendingEffect pe) {
        Entity target = pe.level.getEntity(pe.targetEntityId);
        if (!(target instanceof LivingEntity living)) {
            Goo.LOGGER.debug(LOG_ENTITY_GONE, pe.targetEntityId);
            return;
        }
        playImpactSound(pe.level, living.getX(), living.getY(), living.getZ());
        GooMobEffects.apply(pe.level, living, pe.gooType, pe.thrower);
    }

    /**
     * Applies the goo effect to a block target with impact sound.
     *
     * @param pe the pending effect targeting a block
     */
    static void applyBlockEffect(PendingEffect pe) {
        BlockPos pos = pe.targetPos;
        playImpactSound(pe.level, pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER);
        WorldEffects.apply(pe.level, pe.targetPos, pe.gooType, pe.targetFace);
    }

    /**
     * Plays the slime-squish impact sound at the given coordinates.
     *
     * @param level the server level
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     */
    static void playImpactSound(ServerLevel level, double x, double y, double z) {
        float pitch = IMPACT_PITCH_BASE + level.getRandom().nextFloat() * IMPACT_PITCH_RANGE;
        level.playSound(null, x, y, z,
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, IMPACT_SOUND_VOLUME, pitch);
    }

    /** A goo effect waiting for its blob to finish travelling. */
    record PendingEffect(int arrivalTick, ServerLevel level,
                         ServerPlayer thrower, GooType gooType,
                         int targetEntityId, BlockPos targetPos,
                         Direction targetFace, String abilityId) {}
}
