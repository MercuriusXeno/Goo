package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.GlowCrystalBlock.CrystalShape;
import com.mercuriusxeno.goo.block.GlowCrystalBlock.CrystalSize;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Glow crystal behavior. On fuse expiry, places a permanent glow
 * crystal block with size based on stack count and shape based on
 * flat mode. Instant one-shot: all work in {@link #onFuseExpired}.
 */
public final class GlowBehavior implements ChainBehavior {

    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        CrystalSize size = CrystalSize.fromStacks(be.getStackCount());
        CrystalShape shape = be.isFlatBlob() ? CrystalShape.FLAT : CrystalShape.BUMP;
        Direction facing = be.getPlacedFace();

        BlockState crystal = GooBlocks.GLOW_CRYSTAL.get().defaultBlockState()
                .setValue(GlowCrystalBlock.FACING, facing)
                .setValue(GlowCrystalBlock.SHAPE, shape)
                .setValue(GlowCrystalBlock.SIZE, size);
        level.setBlock(pos, crystal, BLOCK_UPDATE_FLAGS);
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
