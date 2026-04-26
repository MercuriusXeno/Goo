package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Unstable explosion behavior. On fuse expiry, detonates a vanilla
 * explosion scaled by stack count. Instant one-shot: all work in
 * {@link #onFuseExpired}, {@link #isActive} returns false immediately.
 */
public final class UnstableBehavior implements ChainBehavior {

    /** Base explosion power at 1 stack. */
    private static final float BASE_POWER = 2f;
    /** Additional explosion power per stack. */
    private static final float POWER_PER_STACK = 1f;
    /** Block center offset (0.5 added to BlockPos coords). */
    private static final double BLOCK_CENTER = 0.5;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        float power = BASE_POWER + (be.getStackCount() - 1) * POWER_PER_STACK;
        level.explode(null, pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER,
                power, Level.ExplosionInteraction.TNT);
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
