package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rock progressive-mining behavior. On fuse expiry, snapshots state
 * and pipelines per-layer sonic-boom previews followed by block
 * destruction. Footprint and depth scale with stack count via
 * {@link ChainFootprint}. Flat mode mines a single taxicab-circle
 * layer instead of a deep tunnel.
 */
public final class RockBehavior implements ChainBehavior {

    /** Ticks the sonic-boom preview leads the actual break. */
    private static final int PREVIEW_DELAY_TICKS = 8;

    private static final String TAG_PIPELINE_TICK = "RockPipelineTick";
    private static final String TAG_MINING_DEPTH = "RockMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "RockStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "RockFace";
    private static final String TAG_FLAT_MODE = "RockFlatMode";
    private static final String DEFAULT_FACE_NAME = "up";

    private int pipelineTick;
    private int miningDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;
    private boolean flatMode;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.flatMode = be.isFlatMode();
        this.miningDepth = flatMode ? 1 : ChainFootprint.tunnelDepth(stackCount);
        this.pipelineTick = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < miningDepth) {
            RockExecutor.previewLayer(level, pos, placedFace, pipelineTick);
        }
        int breakIndex = pipelineTick - PREVIEW_DELAY_TICKS;
        if (breakIndex >= 0 && breakIndex < miningDepth) {
            RockExecutor.mineLayer(level, pos, placedFace, breakIndex,
                    stackCount, flatMode);
        }
        pipelineTick++;
    }

    @Override
    public boolean isActive() {
        return pipelineTick < miningDepth + PREVIEW_DELAY_TICKS;
    }

    @Override
    public int getMinedLayers() {
        int breakIndex = pipelineTick - PREVIEW_DELAY_TICKS;
        return Math.max(0, breakIndex);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_PIPELINE_TICK, pipelineTick);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
        output.putBoolean(TAG_FLAT_MODE, flatMode);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        pipelineTick = input.getIntOr(TAG_PIPELINE_TICK, 0);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        flatMode = input.getBooleanOr(TAG_FLAT_MODE, false);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE_NAME);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
