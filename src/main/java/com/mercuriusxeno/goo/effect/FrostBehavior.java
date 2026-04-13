package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Frost cold-snap behavior. On fuse expiry, freezes water to magicked
 * ice and lava to obsidian in a spheroid (default) or flat Euclidean
 * circle. Instant one-shot: all work in {@link #onFuseExpired},
 * {@link #isActive} returns false immediately.
 */
public final class FrostBehavior implements ChainBehavior {

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        int stackCount = be.getStackCount();
        Direction placedFace = be.getPlacedFace();
        boolean flatMode = be.isFlatMode();
        boolean underwater = level.getFluidState(pos).isSource();
        if (flatMode) {
            FrostExecutor.executeFlatMode(level, pos, placedFace, stackCount);
        } else if (underwater) {
            FrostExecutor.executeTunnel(level, pos, placedFace, stackCount);
        } else {
            int radius = EffectMath.computeFreezeRadius(stackCount);
            BlockPos center = pos.relative(placedFace.getOpposite());
            FrostExecutor.execute(level, center, radius);
        }
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        // Instant: never ticks.
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        // No state.
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // No state.
    }
}
