package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Server-side handler for blob throw requests. Validates the client's claim,
 * depletes goo from the player's inventory, broadcasts the flight to nearby
 * players, and schedules the effect application after the blob "arrives."
 */
public final class BlobThrowHandler {

    /** Cost of one throw in microblobs (1 blob = 1,000 mB). */
    public static final int THROW_COST = 1000;
    /** Maximum throw range in blocks. */
    public static final double MAX_RANGE = 64.0;
    /** Glow beam travel speed in blocks per tick. */
    private static final double GLOW_BLOCKS_PER_TICK = 2.5;
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;

    /** Block center offset (half-block). */
    private static final double BLOCK_CENTER = 0.5;

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
        if (!validateGlove(player)) { return; }
        GooType gooType = validateGooType(payload);
        if (gooType == null) { return; }
        if (!validateRange(player, payload)) { return; }
        int cost = resolveThrowCost(player, payload, gooType);
        if (!validateSupply(player, gooType, cost)) { return; }
        double distSq = targetDistanceSquared(player, payload);
        depleteAndThrow(player, payload, gooType, distSq, cost);
    }

    /** Validates glove is held, logging rejection if not.
     *
     * @param player the throwing player
     * @return true if valid
     */
    private static boolean validateGlove(ServerPlayer player) {
        boolean held = player.getMainHandItem().getItem() instanceof GooGloveItem
            || player.getOffhandItem().getItem() instanceof GooGloveItem;
        if (held) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GLOVE, player.getName().getString()); }
        return false;
    }

    /** Validates and resolves the goo type from the payload, logging rejection if invalid.
     *
     * @param payload the throw payload data
     * @return the resolved goo type, or null if invalid
     */
    private static GooType validateGooType(BlobThrowPayload payload) {
        GooType gooType = GooType.fromId(payload.gooTypeId());
        if (gooType == null && Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_BAD_TYPE, payload.gooTypeId());
        }
        return gooType;
    }

    /** Validates target is within max throw range, logging rejection if not.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @return true if in range
     */
    private static boolean validateRange(ServerPlayer player, BlobThrowPayload payload) {
        double distSq = targetDistanceSquared(player, payload);
        if (distSq <= MAX_RANGE_SQUARED) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_OUT_OF_RANGE, Math.sqrt(distSq)); }
        return false;
    }

    /** Validates the player has enough goo, logging rejection if not.
     *
     * @param player  the throwing player
     * @param gooType the goo type to check
     * @param cost    the resolved mB cost for this throw
     * @return true if supply is sufficient
     */
    private static boolean validateSupply(ServerPlayer player, GooType gooType, int cost) {
        if (GooSourceScanner.hasEnough(player, gooType, cost)) { return true; }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_NO_GOO, gooType.getId()); }
        return false;
    }

    /** Resolves the throw cost from the ability definition, falling back to THROW_COST.
     * Uses the sequence-aware stack position (landed + in-flight at target).
     *
     * @param player  the throwing player
     * @param payload the throw payload
     * @param gooType the resolved goo type
     * @return the cost in mB for this throw
     */
    private static int resolveThrowCost(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType) {
        if (payload.abilityId().isEmpty()) { return THROW_COST; }
        net.minecraft.resources.Identifier abilityId =
                net.minecraft.resources.Identifier.tryParse(payload.abilityId());
        if (abilityId == null) { return THROW_COST; }
        AbilityDefinition def = AbilityRegistry.getAbility(abilityId);
        if (def == null || def.gooType() != gooType) { return THROW_COST; }
        int stackPos = countExistingStacks(player.level(), payload.targetPos());
        return def.cost().costForStack(stackPos);
    }

    /** Counts the current stack count at a target position (landed blobs).
     *
     * @param level the server level
     * @param pos   the target block position
     * @return the current stack count, or 0 if no marker exists
     */
    private static int countExistingStacks(ServerLevel level, BlockPos pos) {
        ChainMarkerBlockEntity be = findChainMarker(level, pos, null);
        return be != null ? be.getStackCount() : 0;
    }

    /** Depletes goo, broadcasts the flight, and schedules the delayed effect.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     * @param gooType the validated goo type
     * @param distSq  squared distance to target (pre-validated)
     * @param cost    the resolved mB cost for this throw
     */
    private static void depleteAndThrow(ServerPlayer player, BlobThrowPayload payload,
            GooType gooType, double distSq, int cost) {
        int depleted = GooSourceScanner.deplete(player, gooType, cost);
        if (depleted < cost && Goo.LOGGER.isWarnEnabled()) {
            Goo.LOGGER.warn(LOG_PARTIAL_DEPLETE, depleted, cost, gooType.getId());
        }

        stallChainMarkerFuse(player, payload);
        double distance = Math.sqrt(distSq);
        int travelTicks = gooType == GooType.GLOW
                ? Math.max(1, (int) Math.ceil(distance / GLOW_BLOCKS_PER_TICK))
                : (int) ThrowArc.travelTicks(distance);
        broadcastFlight(player, payload, travelTicks);
        BlobEffectScheduler.scheduleEffect(player, payload, gooType, travelTicks);

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
        BlobFlightPayload flight = buildFlightPayload(hand, payload, travelTicks);
        PacketDistributor.sendToPlayersTrackingEntity(player, flight);
        PacketDistributor.sendToPlayer(player, flight);
    }

    /** Builds the flight payload from hand position, throw data, and travel time.
     *
     * @param hand        the world-space hand position
     * @param payload     the throw payload data
     * @param travelTicks the number of ticks until arrival
     * @return the constructed flight payload
     */
    private static BlobFlightPayload buildFlightPayload(Vec3 hand, BlobThrowPayload payload,
            int travelTicks) {
        return new BlobFlightPayload(
                hand.x, hand.y, hand.z,
                payload.gooTypeId(),
                payload.targetEntityId(),
                payload.targetPos(),
                payload.targetFace(),
                travelTicks,
                payload.grannyArc(),
                payload.abilityId()
        );
    }

    /**
     * Called every server tick to apply effects whose blobs have arrived.
     * Wire this to {@code ServerTickEvent.Post} in the mod event bus.
     *
     * @param event the post-tick event instance
     */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!BlobEffectScheduler.hasPending()) { return; }
        int currentTick = event.getServer().getTickCount();
        BlobEffectScheduler.drainArrivedEffects(currentTick);
    }

    /**
     * If the throw targets a chain marker (directly or at the adjacent
     * position), resets its fuse so it doesn't detonate while blobs are
     * in flight. The user's throw declaration is treated as intent to
     * stack, keeping the fuse alive.
     *
     * @param player  the throwing player
     * @param payload the throw payload data
     */
    private static void stallChainMarkerFuse(ServerPlayer player, BlobThrowPayload payload) {
        if (payload.targetEntityId() >= 0) { return; }
        BlockPos pos = payload.targetPos();
        Direction face = directionFromOrdinal(payload.targetFace());
        ServerLevel level = player.level();
        ChainMarkerBlockEntity be = findChainMarker(level, pos, face);
        if (be != null && be.getBehavior() == null) {
            be.stallFuse();
        }
    }

    /**
     * Finds a chain marker BE at the given pos or the adjacent block.
     *
     * @param level the server level
     * @param pos   the hit block position
     * @param face  the hit face, or null
     * @return the chain marker BE, or null
     */
    private static @Nullable ChainMarkerBlockEntity findChainMarker(
            ServerLevel level, BlockPos pos, @Nullable Direction face) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) { return be; }
        if (face != null) {
            BlockPos adj = pos.relative(face);
            if (level.getBlockEntity(adj) instanceof ChainMarkerBlockEntity be) { return be; }
        }
        return null;
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
    static Direction directionFromOrdinal(int ordinal) {
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
}
