package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
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

    /** Empty sentinel for unknown goo type (no chain profile). */
    private static final int[] UNKNOWN_STACKS = new int[0];
    /** Empty ability id for legacy throws. */
    private static final String LEGACY_ABILITY = "";

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
        if (!canThrow(player)) { return; }
        TargetResult target = resolveAimTarget(player);
        if (wouldExceedMaxStacks(target, gooType)) {
            ThrowFreezeState.armThrowBlock();
            return;
        }
        String abilityId = resolveAbilityId(player);
        BlobThrowPayload payload = targetToPayload(target, gooType, abilityId);
        if (payload != null) {
            ThrowFreezeState.arm(target);
            trackInFlight(target);
            sendPayload(payload);
        }
    }

    /** Pre-throw validation: goo available, not throw-blocked, ability selected.
     *
     * @param player the local player
     * @return true if throwing is allowed
     */
    private static boolean canThrow(Player player) {
        return GloveUseTracker.isSelectedTypeAvailable()
                && !ThrowFreezeState.isThrowBlocked()
                && hasAbilitySelected(player);
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
     * arrives. Checks both the given pos and all adjacent positions
     * since the flight payload carries the hit block pos but the
     * in-flight map tracks the canonical marker pos (which may be adjacent).
     *
     * @param pos the target position from the flight payload
     */
    public static void onFlightArrived(BlockPos pos) {
        if (decrementInFlight(pos)) { return; }
        for (Direction dir : Direction.values()) {
            if (decrementInFlight(pos.relative(dir))) { return; }
        }
    }

    /** Decrements the in-flight count at pos. Returns true if the entry existed.
     *
     * @param pos the block position to decrement
     * @return true if an in-flight entry existed at pos
     */
    private static boolean decrementInFlight(BlockPos pos) {
        return IN_FLIGHT.computeIfPresent(pos, (k, v) -> v > 1 ? v - 1 : null) != null;
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
     * counting both current stacks and in-flight blobs. Works even before
     * the marker exists on the client by predicting the placement position
     * and looking up maxStacks from the ChainProfile.
     *
     * @param target  the resolved aim target
     * @param gooType the goo type being thrown
     * @return true if the throw should be blocked
     */
    private static boolean wouldExceedMaxStacks(TargetResult target, GooType gooType) {
        if (target instanceof TargetResult.GlowCrystalTarget gct && gooType == GooType.GLOW) {
            return wouldExceedCrystalMax(gct);
        }
        BlockPos pos = resolveTrackingPos(target);
        return pos != null && wouldExceedMarkerMax(pos, gooType);
    }

    /**
     * Checks current + pending stacks against the marker's max, using the BE if present.
     *
     * @param pos     the canonical marker position
     * @param gooType the goo type being thrown
     * @return true if the throw should be blocked
     */
    private static boolean wouldExceedMarkerMax(BlockPos pos, GooType gooType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return false; }

        int[] currentAndMax = resolveCurrentAndMax(mc.level, pos, gooType);
        if (currentAndMax.length == 0) { return false; }
        int pending = IN_FLIGHT.getOrDefault(pos, 0);
        return currentAndMax[0] + pending >= currentAndMax[1];
    }

    /**
     * Returns [current, max] from the marker BE or chain profile, or empty if unknown.
     *
     * @param level   the client level
     * @param pos     the marker position
     * @param gooType the goo type being thrown
     * @return a 2-element array [current, max], or empty if the type has no profile
     */
    private static int[] resolveCurrentAndMax(
            ClientLevel level, BlockPos pos, GooType gooType) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            return new int[]{be.getStackCount(), be.getMaxStacks()};
        }
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile == null) { return UNKNOWN_STACKS; }
        return new int[]{0, profile.maxStacks()};
    }

    /**
     * Returns true if the crystal is at (or will reach) max size with in-flight blobs.
     *
     * @param gct the glow crystal target
     * @return true if the crystal cannot accept another blob
     */
    private static boolean wouldExceedCrystalMax(TargetResult.GlowCrystalTarget gct) {
        int current = gct.currentStacks();
        if (current <= 0) { return false; }
        ChainProfile profile = ChainProfile.forType(GooType.GLOW);
        if (profile == null) { return false; }
        int pending = IN_FLIGHT.getOrDefault(gct.pos(), 0);
        return current + pending >= profile.maxStacks();
    }

    /** Increments the in-flight count for any throw that would place or
     * stack on a chain marker. Tracks even before the marker exists so
     * rapid throws during flight time are counted.
     *
     * @param target the resolved aim target
     */
    private static void trackInFlight(TargetResult target) {
        BlockPos pos = resolveTrackingPos(target);
        if (pos != null) {
            IN_FLIGHT.merge(pos, 1, Integer::sum);
        }
    }

    /**
     * Resolves the canonical tracking position for in-flight counting.
     * Returns the position where a chain marker IS or WOULD BE placed.
     * Works before the marker exists so the first burst of throws can
     * be counted against maxStacks during the flight window.
     *
     * @param target the resolved aim target
     * @return the canonical marker position, or null for entity/none targets
     */
    private static @Nullable BlockPos resolveTrackingPos(TargetResult target) {
        if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            return cmt.pos();
        }
        if (target instanceof TargetResult.GlowCrystalTarget gct) {
            return gct.pos();
        }
        if (target instanceof TargetResult.BlockTarget bt) {
            return resolveBlockTrackingPos(bt);
        }
        return null;
    }

    /** Resolves the tracking position for a block target. If a marker
     * already exists at the hit pos or adjacent, returns its position.
     * Otherwise predicts placement: replaceable blocks are displaced
     * in-place, solid blocks place the marker on the adjacent face.
     *
     * @param bt the block target to resolve
     * @return the canonical marker position, or null if level unavailable
     */
    private static @Nullable BlockPos resolveBlockTrackingPos(TargetResult.BlockTarget bt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return null; }
        if (mc.level.getBlockEntity(bt.pos()) instanceof ChainMarkerBlockEntity) {
            return bt.pos();
        }
        BlockPos adjacent = bt.pos().relative(bt.face());
        if (mc.level.getBlockEntity(adjacent) instanceof ChainMarkerBlockEntity) {
            return adjacent;
        }
        BlockState state = mc.level.getBlockState(bt.pos());
        return state.canBeReplaced() ? bt.pos() : adjacent;
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
     * @param abilityId the selected ability id string
     * @return the payload, or null for no target
     */
    private static @Nullable BlobThrowPayload targetToPayload(TargetResult target,
            GooType gooType, String abilityId) {
        if (target instanceof TargetResult.None) { return null; }
        return buildPayload(target, gooType.getId(), abilityId);
    }

    /** Returns true if the player's glove has an ability selected.
     * Throws are suppressed when no ability is chosen.
     *
     * @param player the local player
     * @return true if an ability is selected
     */
    private static boolean hasAbilitySelected(Player player) {
        ItemStack glove = player.getMainHandItem();
        if (!(glove.getItem() instanceof GooGloveItem)) {
            glove = player.getOffhandItem();
        }
        GloveSelection sel = GooGloveItem.getSelection(glove);
        return sel != null && sel.hasAbility();
    }

    /**
     * Reads the ability ID from the player's glove, or empty for legacy.
     * @param player    the local player
     * @return the ability id, or empty for legacy
     */
    private static String resolveAbilityId(Player player) {
        ItemStack glove = player.getMainHandItem();
        if (!(glove.getItem() instanceof GooGloveItem)) {
            glove = player.getOffhandItem();
        }
        GloveSelection sel = GooGloveItem.getSelection(glove);
        if (sel != null && sel.hasAbility()) { return sel.abilityId(); }
        return LEGACY_ABILITY;
    }

    /** Builds the payload for non-None targets. Kept separate so the None early-exit
     * @param target the resolved non-None aim target
     * @param typeId the goo type registry id
     * @param abilityId the selected ability id string
     * @return the constructed throw payload
     * reduces the switch to 4 arms and keeps CC within threshold. */
    private static BlobThrowPayload buildPayload(TargetResult target, String typeId, String abilityId) {
        return switch (target) {
            case TargetResult.EntityTarget et -> entityPayload(typeId, et, abilityId);
            case TargetResult.BlockTarget bt -> blockPayload(typeId, bt, abilityId);
            case TargetResult.ChainMarkerTarget cmt -> chainMarkerPayload(typeId, cmt, abilityId);
            case TargetResult.GlowCrystalTarget gct -> new BlobThrowPayload(typeId, NO_ENTITY,
                    gct.pos(), gct.face().ordinal(), false, abilityId);
            default -> throw new IllegalArgumentException(target.toString());
        };
    }

    /**
     * Builds a throw payload aimed at an entity.
     * @param typeId the goo type registry id
     * @param et the entity aim target
     * @param abilityId the selected ability id string
     * @return the entity-targeted throw payload
     */
    private static BlobThrowPayload entityPayload(String typeId,
            TargetResult.EntityTarget et, String abilityId) {
        return new BlobThrowPayload(typeId, et.entity().getId(), BlockPos.ZERO, NO_ENTITY,
                false, abilityId);
    }

    /**
     * Builds a throw payload aimed at a block face.
     * @param typeId the goo type registry id
     * @param bt the block face aim target
     * @param abilityId the selected ability id string
     * @return the block-targeted throw payload
     */
    private static BlobThrowPayload blockPayload(String typeId,
            TargetResult.BlockTarget bt, String abilityId) {
        return new BlobThrowPayload(typeId, NO_ENTITY, bt.pos(), bt.face().ordinal(),
                bt.grannyArc(), abilityId);
    }

    /**
     * Builds a throw payload aimed at a chain marker, resolving its placed face.
     * @param typeId the goo type registry id
     * @param cmt the chain marker aim target
     * @param abilityId the selected ability id string
     * @return the chain-marker-targeted throw payload
     */
    private static BlobThrowPayload chainMarkerPayload(String typeId,
            TargetResult.ChainMarkerTarget cmt, String abilityId) {
        int faceOrdinal = resolveChainMarkerFace(cmt.pos()).getOpposite().ordinal();
        return new BlobThrowPayload(typeId, NO_ENTITY, cmt.pos(), faceOrdinal, false, abilityId);
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
