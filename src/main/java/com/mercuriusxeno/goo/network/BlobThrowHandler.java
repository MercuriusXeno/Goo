package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.effect.GooMobEffects;
import com.mercuriusxeno.goo.effect.WorldEffects;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-side handler for blob throw requests. Validates the client's claim,
 * depletes goo from the player's inventory, broadcasts the flight to nearby
 * players, and schedules the effect application after the blob "arrives."
 */
public final class BlobThrowHandler {

    /** Cost of one throw in microblobs (1 blob = 1,000 mB). */
    public static final long THROW_COST = 1000;
    /** Maximum throw range in blocks. */
    public static final double MAX_RANGE = 32.0;
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;

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

    /** Pending effects waiting for their blob to arrive. */
    private static final List<PendingEffect> PENDING_EFFECTS = new ArrayList<>();

    /** Log: player not holding glove. */
    private static final String LOG_NO_GLOVE = "Throw rejected: player {} not holding glove";
    /** Log: unknown goo type. */
    private static final String LOG_BAD_TYPE = "Throw rejected: unknown goo type '{}'";
    /** Log: target out of range. */
    private static final String LOG_OUT_OF_RANGE = "Throw rejected: target out of range ({} blocks)";
    /** Log: insufficient goo for throw. */
    private static final String LOG_NO_GOO = "Throw rejected: insufficient {} goo";
    /** Log: partial depletion warning. */
    private static final String LOG_PARTIAL_DEPLETE = "Partial depletion ({}/{} mB) for {} throw - proceeding anyway";
    /** Log: throw executed successfully. */
    private static final String LOG_THROW_OK = "Throw executed: {} by {} -> arrival in {} ticks";
    /** Log: entity no longer exists at blob arrival. */
    private static final String LOG_ENTITY_GONE = "Blob arrived but entity {} no longer exists";

    private BlobThrowHandler() {}

    /**
     * Handles the throw payload on the server thread.
     *
     * @param payload the throw payload data
     * @param context the network context
     */
    public static void handle(BlobThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            execute(player, payload);
        });
    }

    /**
     * Validates and executes the throw.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     */
    private static void execute(ServerPlayer player, BlobThrowPayload payload) {
        if (!isHoldingGlove(player)) {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GLOVE, player.getName().getString()); }
            return;
        }

        GooType gooType = GooType.fromId(payload.gooTypeId());
        if (gooType == null) {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_BAD_TYPE, payload.gooTypeId()); }
            return;
        }

        double distSq = targetDistanceSquared(player, payload);
        if (!isInRange(distSq)) {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_OUT_OF_RANGE, Math.sqrt(distSq)); }
            return;
        }

        if (!hasEnoughGoo(player, gooType)) {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GOO, gooType.getId()); }
            return;
        }

        depleteAndThrow(player, payload, gooType, distSq);
    }

    /** Returns true if the player is holding a goo glove in either hand.
     *
     * @param player the throwing player
     * @return true if a glove is held
     */
    private static boolean isHoldingGlove(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof GooGloveItem
            || player.getOffhandItem().getItem() instanceof GooGloveItem;
    }

    /** Returns true if the squared distance is within max throw range.
     *
     * @param distSq squared distance to target
     * @return true if in range
     */
    private static boolean isInRange(double distSq) {
        return distSq <= MAX_RANGE_SQUARED;
    }

    /** Returns true if the player has enough goo of the given type for one throw.
     *
     * @param player  the throwing player
     * @param gooType the goo type to check
     * @return true if the player can afford the throw
     */
    private static boolean hasEnoughGoo(ServerPlayer player, GooType gooType) {
        return GooSourceScanner.hasEnough(player, gooType, THROW_COST);
    }

    /** Depletes goo, broadcasts the flight, and schedules the delayed effect.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param distSq  squared distance to target (pre-validated)
     */
    private static void depleteAndThrow(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType, double distSq) {
        long depleted = GooSourceScanner.deplete(player, gooType, THROW_COST);
        if (depleted < THROW_COST && Goo.LOGGER.isWarnEnabled()) {
            Goo.LOGGER.warn(LOG_PARTIAL_DEPLETE, depleted, THROW_COST, gooType.getId());
        }

        int travelTicks = (int) ThrowArc.travelTicks(Math.sqrt(distSq));
        broadcastFlight(player, payload, travelTicks);
        scheduleEffect(player, payload, gooType, travelTicks);

        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_THROW_OK, gooType.getId(), player.getName().getString(), travelTicks); }
    }

    /** Builds and broadcasts the flight payload to tracking players and the thrower.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param travelTicks the number of ticks until arrival
     */
    private static void broadcastFlight(ServerPlayer player, BlobThrowPayload payload,
            int travelTicks) {
        Vec3 hand = getThrowHandPosition(player);
        BlobFlightPayload flight = new BlobFlightPayload(
                hand.x, hand.y, hand.z,
                payload.gooTypeId(),
                payload.targetEntityId(),
                payload.targetPos(),
                payload.targetFace(),
                travelTicks,
                payload.grannyArc()
        );
        PacketDistributor.sendToPlayersTrackingEntity(player, flight);
        PacketDistributor.sendToPlayer(player, flight);
    }

    /** Plays the throw sound and queues a pending effect for blob arrival.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param gooType     the goo type being thrown
     * @param travelTicks the number of ticks until arrival
     */
    private static void scheduleEffect(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType, int travelTicks) {
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, THROW_SOUND_VOLUME,
                THROW_PITCH_BASE / (level.getRandom().nextFloat() * THROW_PITCH_RANGE + THROW_PITCH_OFFSET));

        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        Direction face = directionFromOrdinal(payload.targetFace());
        synchronized (PENDING_EFFECTS) {
            PENDING_EFFECTS.add(new PendingEffect(
                    arrivalTick, level, player, gooType,
                    payload.targetEntityId(), payload.targetPos(), face));
        }
    }

    /**
     * Called every server tick to apply effects whose blobs have arrived.
     * Wire this to {@code ServerTickEvent.Post} in the mod event bus.
     *
     * @param event the post-tick event instance
     */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (PENDING_EFFECTS.isEmpty()) { return; }
        int currentTick = event.getServer().getTickCount();

        synchronized (PENDING_EFFECTS) {
            Iterator<PendingEffect> it = PENDING_EFFECTS.iterator();
            while (it.hasNext()) {
                PendingEffect pe = it.next();
                if (currentTick >= pe.arrivalTick) {
                    applyEffect(pe);
                    it.remove();
                }
            }
        }
    }

    /**
     * Applies the goo effect at the target location or entity, with impact sound.
     *
     * @param pe the pending effect to apply
     */
    private static void applyEffect(PendingEffect pe) {
        if (pe.targetEntityId >= 0) {
            Entity target = pe.level.getEntity(pe.targetEntityId);
            if (target instanceof LivingEntity living) {
                pe.level.playSound(null, living.getX(), living.getY(), living.getZ(),
                        SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, IMPACT_SOUND_VOLUME,
                        IMPACT_PITCH_BASE + pe.level.getRandom().nextFloat() * IMPACT_PITCH_RANGE);
                GooMobEffects.apply(pe.level, living, pe.gooType, pe.thrower);
            } else {
                Goo.LOGGER.debug(LOG_ENTITY_GONE, pe.targetEntityId);
            }
        } else {
            BlockPos pos = pe.targetPos;
            pe.level.playSound(null, pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER,
                    SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, IMPACT_SOUND_VOLUME,
                    IMPACT_PITCH_BASE + pe.level.getRandom().nextFloat() * IMPACT_PITCH_RANGE);
            WorldEffects.apply(pe.level, pe.targetPos, pe.gooType, pe.targetFace);
        }
    }

    /**
     * Returns squared distance from the player to the packet's target.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @return squared distance in blocks
     */
    private static double targetDistanceSquared(ServerPlayer player, BlobThrowPayload payload) {
        if (payload.targetEntityId() >= 0) {
            Entity target = player.level().getEntity(payload.targetEntityId());
            if (target == null) { return Double.MAX_VALUE; }
            return player.distanceToSqr(target);
        }
        BlockPos pos = payload.targetPos();
        return player.distanceToSqr(pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER);
    }

    /**
     * Converts a Direction ordinal (from the network payload) to a Direction.
     * Returns null for out-of-range values (e.g. -1 for entity targets).
     *
     * @param ordinal the direction ordinal from the payload
     * @return the corresponding direction, or null if invalid
     */
    private static Direction directionFromOrdinal(int ordinal) {
        Direction[] dirs = Direction.values();
        if (ordinal >= 0 && ordinal < dirs.length) {
            return dirs[ordinal];
        }
        return null;
    }

    /**
     * Computes the world-space glove hand position on the server using
     * yaw-derived basis vectors. Delegates pure offset to {@link ThrowArc}.
     *
     * @param player the throwing player
     * @return world-space hand position
     */
    private static Vec3 getThrowHandPosition(ServerPlayer player) {
        float side = ThrowArc.gloveSide(player.getMainHandItem(), player.getMainArm());
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        // right = (-cos, 0, -sin), up = (0, 1, 0) - yaw-only, no pitch on server
        Vec3 offset = ThrowArc.handOffset(
                new Vec3(-cos, 0, -sin), new Vec3(0, 1, 0),
                side, player.getScale());
        return player.getEyePosition().add(offset);
    }

    /** A goo effect waiting for its blob to finish travelling. */
    private record PendingEffect(int arrivalTick, ServerLevel level,
                                 ServerPlayer thrower, GooType gooType,
                                 int targetEntityId, BlockPos targetPos,
                                 Direction targetFace) {}
}
