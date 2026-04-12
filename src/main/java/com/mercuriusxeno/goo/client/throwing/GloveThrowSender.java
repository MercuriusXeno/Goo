package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-only helper that resolves the player's aim target and sends
 * a {@link BlobThrowPayload} to the server. Tracks in-flight blob
 * counts per chain marker so the client can block throws that would
 * exceed max stacks without waiting for server acknowledgement.
 */
public final class GloveThrowSender {

    /** Sentinel value indicating no entity target. */
    private static final int NO_ENTITY = -1;

    /** In-flight throws toward chain markers, keyed by block position. */
    private static final Map<BlockPos, Integer> IN_FLIGHT = new HashMap<>();

    private GloveThrowSender() {}

    /**
     * Resolves the current aim target and sends the throw packet.
     * Blocks the throw if in-flight blobs would exceed the marker's
     * max stacks, and arms a throw-block freeze when maxed.
     *
     * @param player the local player
     * @param gooType the selected goo type to throw
     */
    public static void sendThrow(Player player, GooType gooType) {
        if (!GloveUseTracker.isSelectedTypeAvailable()) { return; }
        if (ThrowFreezeState.isThrowBlocked()) { return; }
        TargetResult target = resolveAimTarget(player);
        if (wouldExceedMaxStacks(target)) {
            ThrowFreezeState.armThrowBlock();
            return;
        }
        BlobThrowPayload payload = targetToPayload(target, gooType);
        if (payload != null) {
            ThrowFreezeState.arm(target);
            trackInFlight(target);
            sendPayload(payload);
        }
    }

    /**
     * Called each client tick to decrement in-flight counters as blobs
     * arrive. Wire to the same client tick as {@link ThrowFreezeState#tick()}.
     */
    public static void tick() {
        // In-flight counts are decremented when BlobFlightManager removes
        // arrived flights. This tick cleans up stale entries.
        Iterator<Map.Entry<BlockPos, Integer>> it = IN_FLIGHT.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= 0) { it.remove(); }
        }
    }

    /**
     * Decrements the in-flight count for a chain marker when a blob
     * arrives. Called by BlobFlightManager on flight completion.
     *
     * @param pos the chain marker position
     */
    public static void onFlightArrived(BlockPos pos) {
        IN_FLIGHT.computeIfPresent(pos, (k, v) -> v > 1 ? v - 1 : null);
    }

    /** Clears all in-flight tracking (on disconnect or dimension change). */
    public static void clearInFlight() {
        IN_FLIGHT.clear();
    }

    /**
     * Returns the number of blobs currently in flight toward the given position.
     *
     * @param pos the target position
     * @return the in-flight count
     */
    public static int getInFlightCount(BlockPos pos) {
        return IN_FLIGHT.getOrDefault(pos, 0);
    }

    /**
     * Returns true if this throw would push a chain marker past max stacks,
     * counting both current stacks and in-flight blobs.
     *
     * @param target the resolved aim target
     * @return true if the throw should be blocked
     */
    private static boolean wouldExceedMaxStacks(TargetResult target) {
        if (!(target instanceof TargetResult.ChainMarkerTarget cmt)) { return false; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return false; }
        if (!(mc.level.getBlockEntity(cmt.pos()) instanceof ChainMarkerBlockEntity be)) { return false; }
        int current = be.getStackCount();
        int pending = IN_FLIGHT.getOrDefault(cmt.pos(), 0);
        return current + pending >= be.getMaxStacks();
    }

    /** Increments the in-flight count when a throw targets a chain marker.
     *
     * @param target the resolved aim target
     */
    private static void trackInFlight(TargetResult target) {
        if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            IN_FLIGHT.merge(cmt.pos(), 1, Integer::sum);
        }
    }

    /** Resolves the player's current aim target at the current partial tick.
     *
     * @param player the local player
     * @return the resolved target result
     */
    private static TargetResult resolveAimTarget(Player player) {
        float partialTick = Minecraft.getInstance()
                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return GooTargetHighlighter.resolveTarget(player, partialTick);
    }

    /** Converts a target result into a throw payload, or null if no valid target.
     *
     * @param target  the aim target
     * @param gooType the selected goo type
     * @return the payload, or null for no target
     */
    private static BlobThrowPayload targetToPayload(TargetResult target, GooType gooType) {
        return switch (target) {
            case TargetResult.EntityTarget et -> new BlobThrowPayload(gooType.getId(), et.entity().getId(), BlockPos.ZERO, NO_ENTITY, false);
            case TargetResult.BlockTarget bt -> new BlobThrowPayload(gooType.getId(), NO_ENTITY, bt.pos(), bt.face().ordinal(), bt.grannyArc());
            case TargetResult.ChainMarkerTarget cmt -> new BlobThrowPayload(gooType.getId(), NO_ENTITY, cmt.pos(), resolveChainMarkerFace(cmt.pos()).getOpposite().ordinal(), false);
            case TargetResult.None ignored -> null;
        };
    }

    /**
     * Reads the placed face from the chain marker BE so the flight
     * destination lands at the orb's face boundary position.
     *
     * @param pos the chain marker block position
     * @return the placed face, or UP if the BE is unavailable
     */
    private static Direction resolveChainMarkerFace(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            return be.getPlacedFace();
        }
        return Direction.UP;
    }

    /** Sends a custom payload packet to the server.
     *
     * @param payload the payload to send
     */
    private static void sendPayload(BlobThrowPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
