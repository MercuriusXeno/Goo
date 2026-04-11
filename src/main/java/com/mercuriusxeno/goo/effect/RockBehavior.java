package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rock progressive-mining behavior for rock chain markers. On fuse
 * expiry, snapshots the stack count and placed face, computes the
 * column depth via {@link EffectMath#computeImplosionDepth} (stack^2,
 * capped), and then runs a pipelined sequence of per-layer previews
 * and breaks: each layer's sonic-boom shockwave is emitted
 * {@link #PREVIEW_DELAY_TICKS} ticks before the actual 3x3 destruction
 * lands for that layer, so the warden-style particle visually leads
 * the break.
 *
 * <p>Mining advances along {@code placedFace.getOpposite()}, so a
 * blob thrown at a cave wall mines horizontally into the wall rather
 * than downward.
 */
public final class RockBehavior implements ChainBehavior {

    /** Number of ticks the sonic-boom preview leads the actual break
     * for any given layer. Tuned to let the warden shockwave visually
     * register before the blocks crumble. */
    private static final int PREVIEW_DELAY_TICKS = 8;

    private static final String TAG_PIPELINE_TICK = "RockPipelineTick";
    private static final String TAG_MINING_DEPTH = "RockMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "RockStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "RockFace";
    private static final String DEFAULT_FACE_NAME = "up";

    /** Pipeline step counter. On step {@code t}, layer {@code t} is
     * previewed (sonic boom) and layer {@code t - PREVIEW_DELAY_TICKS}
     * is actually mined. Runs from 0 to
     * {@code miningDepth + PREVIEW_DELAY_TICKS}. */
    private int pipelineTick;
    /** Total number of layers to mine; computed at fuse expiry. */
    private int miningDepth;
    /** Snapshot of the BE's stack count at fuse expiry. */
    private int stackCount;
    /** Snapshot of the BE's placed face at fuse expiry. */
    private Direction placedFace = Direction.UP;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.miningDepth = EffectMath.computeImplosionDepth(stackCount);
        this.pipelineTick = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < miningDepth) {
            RockExecutor.previewLayer(level, pos, placedFace, pipelineTick);
        }
        int breakIndex = pipelineTick - PREVIEW_DELAY_TICKS;
        if (breakIndex >= 0 && breakIndex < miningDepth) {
            RockExecutor.mineLayer(level, pos, placedFace, breakIndex, stackCount);
        }
        pipelineTick++;
    }

    @Override
    public boolean isActive() {
        return pipelineTick < miningDepth + PREVIEW_DELAY_TICKS;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_PIPELINE_TICK, pipelineTick);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        pipelineTick = input.getIntOr(TAG_PIPELINE_TICK, 0);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE_NAME);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
