package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.effect.ChainPlacementRules.CandidateState;
import com.mercuriusxeno.goo.effect.ChainPlacementRules.Decision;
import com.mercuriusxeno.goo.effect.ChainPlacementRules.WaterHandling;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Shared on-hit placement for blob-impact effect blocks. The four per-type
 * {@link WorldEffect} implementations ({@code RockEffect},
 * {@code BlazeEffect}, {@code NetherEffect}, {@code FrostEffect}) forward
 * to the entry points below, which build a {@link ChainPlacementRules}
 * candidate state for the hit and face-adjacent positions, apply the
 * decision, and initialize the resulting chain marker.
 *
 * <p>All chain effects (rock, blaze, nether, frost) place waterloggable
 * {@code ChainMarkerBlock}s via {@link #placeChainMarker}.</p>
 *
 * <p>Placement rule: stack onto an existing same-type effect block first,
 * then try the hit block, then the face-adjacent block. The hit block is
 * a first-class placement target (fire, tall grass, snow, water, etc.),
 * so non-solid targets do not always push the marker one block off the
 * face.</p>
 */
final class EffectBlockPlacement {

    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** Initial frost field stack count on first placement. */
    /** Fallback face used when the hit direction is unknown. */
    private static final Direction DEFAULT_FACE = Direction.UP;

    private EffectBlockPlacement() {}

    /**
     * Rock: chain implosion. Places a chain marker on the hit block (if
     * replaceable or water) or the face-adjacent block.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void rockImplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.ROCK);
    }

    /**
     * Blaze: chain explosion. Places a chain marker on the hit block (if
     * replaceable or water) or the face-adjacent block. Additional blobs
     * during the fuse window stack up to 4 for 3/5/7/9 radius.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void blazeExplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.BLAZE);
    }

    /**
     * Nether: chain conversion. Places a chain marker on the hit face. On
     * fuse expiry, blocks with a registered goo value in the radius dissolve
     * into blob items. Blocks without a goo value are left untouched; the
     * goo value registry is the sole gate, with no hardness check and no
     * vanilla fallback.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void netherConvert(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.NETHER);
    }

    /**
     * Frost: instant freeze + persistent melt-resist field. Stacking at an
     * existing field happens first (with a fresh radius-bumped freeze);
     * otherwise the initial freeze runs and the field is placed via the
     * {@link WaterHandling#FREEZE_AND_RISE} rubric - water candidates get
     * frozen to a non-melting mod ice block under the field's protection,
     * swapped to vanilla ice on field expiry, and the field lands on the ice.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    /**
     * Frost: chain marker cold snap. Places a chain marker that freezes
     * a spheroid on fuse expiry. Uses the same placement path as rock/blaze.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void frostColdSnap(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.FROST);
    }

    /**
     * Crystal: chain marker shard cloud. Places a chain marker that
     * becomes a DOT cloud on fuse expiry.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void crystalCloud(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.CRYSTAL);
    }

    /**
     * Unstable: chain marker explosion. Places a chain marker that
     * explodes on fuse expiry.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void unstableExplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.UNSTABLE);
    }

    /**
     * Glow: chain marker light crystal. Places a chain marker that
     * becomes a permanent glow crystal on fuse expiry.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void glowCrystal(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.GLOW);
    }

    /**
     * Metal: chain marker spike trap. Places a chain marker that becomes
     * a spike trap on fuse expiry.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void metalSpikeTrap(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeChainMarker(level, pos, targetFace, GooType.METAL);
    }

    // ── Chain marker placement ─────────────────────────────────────────

    /**
     * Computes and applies the placement decision for a chain marker at the
     * hit block or the face-adjacent block.
     *
     * @param level     the current level
     * @param hitBlock  the hit block position
     * @param face      the face that was hit, or null
     * @param type      the goo type for the marker
     */
    private static void placeChainMarker(Level level, BlockPos hitBlock,
            @Nullable Direction face, GooType type) {
        Direction resolvedFace = face == null ? DEFAULT_FACE : face;
        BlockPos adjacentPos = hitBlock.relative(resolvedFace);

        CandidateState hitState = chainCandidateState(level, hitBlock, type);
        CandidateState adjacentState = chainCandidateState(level, adjacentPos, type);
        Decision decision = ChainPlacementRules.decide(hitState, adjacentState, WaterHandling.WATERLOG);
        applyChainDecision(level, decision, hitBlock, adjacentPos, type, resolvedFace);
    }

    /**
     * Builds a CandidateState for chain marker placement. The aboveIsPlaceable
     * field is unused for WATERLOG handling so is left false.
     *
     * @param level the current level
     * @param pos   the candidate position
     * @param type  the goo type (for same-marker stack detection)
     * @return the candidate state snapshot
     */
    private static CandidateState chainCandidateState(Level level, BlockPos pos, GooType type) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        return new CandidateState(
                isExistingChainMarker(level, pos, state, type),
                state.isAir(),
                state.canBeReplaced(),
                fluid.is(Fluids.WATER),
                fluid.is(Fluids.LAVA),
                false);
    }

    /**
     * Returns true if the block at {@code pos} is an existing chain marker
     * of the same goo type.
     *
     * @param level the current level
     * @param pos   the position to test
     * @param state the block state at {@code pos}
     * @param type  the expected goo type
     * @return true if a same-type chain marker is present
     */
    private static boolean isExistingChainMarker(Level level, BlockPos pos, BlockState state, GooType type) {
        return state.is(GooBlocks.CHAIN_MARKER.get())
                && level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && be.getGooType() == type;
    }

    /**
     * Dispatches a chain-marker placement decision to the right mutation.
     *
     * @param level    the current level
     * @param decision the decision to apply
     * @param hitPos   the hit block position
     * @param adjPos   the face-adjacent block position
     * @param type     the goo type
     * @param face     the resolved hit face
     */
    private static void applyChainDecision(Level level, Decision decision,
            BlockPos hitPos, BlockPos adjPos, GooType type, Direction face) {
        BlockPos target = pickCandidate(decision, hitPos, adjPos);
        switch (decision.action()) {
            case STACK -> stackChainMarker(level, target);
            case DISPLACE -> placeFreshChainMarker(level, target, type, face, false);
            case WATERLOG -> placeFreshChainMarker(level, target, type, face, true);
            case FREEZE_AND_RISE, NONE -> { /* no-op */ }
        }
    }

    /**
     * Bumps the stack count on an existing chain marker at {@code pos}.
     *
     * @param level the current level
     * @param pos   the marker position
     */
    private static void stackChainMarker(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            be.tryStack();
        }
    }

    /**
     * Places a new chain marker at {@code pos}, optionally waterlogged, and
     * initializes its block entity with type and face.
     *
     * @param level       the current level
     * @param pos         the placement position
     * @param type        the goo type for the marker
     * @param face        the hit face direction
     * @param waterlogged whether the marker should coexist with a water fluid
     */
    private static void placeFreshChainMarker(Level level, BlockPos pos, GooType type,
            Direction face, boolean waterlogged) {
        BlockState markerState = GooBlocks.CHAIN_MARKER.get().defaultBlockState()
                .setValue(ChainMarkerBlock.WATERLOGGED, waterlogged);
        level.setBlock(pos, markerState, BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            be.initChain(type, face);
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────

    /**
     * Resolves the candidate position for a decision (index 0 = hit, 1 = adjacent).
     *
     * @param decision the decision
     * @param hitPos   the hit block position
     * @param adjPos   the face-adjacent block position
     * @return the chosen position
     */
    private static BlockPos pickCandidate(Decision decision, BlockPos hitPos, BlockPos adjPos) {
        return decision.candidateIndex() == 0 ? hitPos : adjPos;
    }
}
