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
 * capped), and then advances one 3x3 layer per server tick by
 * delegating to {@link RockExecutor#mineLayer}. The BE removes itself
 * when {@link #miningStep} reaches {@link #miningDepth}.
 *
 * <p>Mining advances along {@code placedFace.getOpposite()}, so a
 * blob thrown at a cave wall mines horizontally into the wall rather
 * than downward.
 */
public final class RockBehavior implements ChainBehavior {

    private static final String TAG_MINING_STEP = "RockMiningStep";
    private static final String TAG_MINING_DEPTH = "RockMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "RockStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "RockFace";
    private static final String DEFAULT_FACE_NAME = "up";

    /** Next layer index to mine; incremented each tick. */
    private int miningStep;
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
        this.miningStep = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        RockExecutor.mineLayer(level, pos, placedFace, miningStep, stackCount);
        miningStep++;
    }

    @Override
    public boolean isActive() {
        return miningStep < miningDepth;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_MINING_STEP, miningStep);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        miningStep = input.getIntOr(TAG_MINING_STEP, 0);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE_NAME);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
