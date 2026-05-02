package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock.CrystalShape;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock.CrystalSize;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@link BlockPlacer} that lays down a {@link GlowCrystalBlock} with
 * FACING, SHAPE, and SIZE derived from the chain marker's snapshot:
 * <ul>
 *   <li>FACING -> placedFace (the wall the marker was attached to)</li>
 *   <li>SHAPE -> bump or flat per the marker's blob shape</li>
 *   <li>SIZE -> tiny/small/medium/large by stack count</li>
 * </ul>
 */
public final class GlowCrystalPlacer implements BlockPlacer {

    /** Singleton instance. */
    public static final GlowCrystalPlacer INSTANCE = new GlowCrystalPlacer();

    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private GlowCrystalPlacer() {
    }

    @Override
    public void place(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        CrystalSize size = CrystalSize.fromStacks(be.getStackCount());
        CrystalShape shape = be.isFlatBlob() ? CrystalShape.FLAT : CrystalShape.BUMP;
        Direction facing = be.getPlacedFace();
        BlockState crystal = GooBlocks.GLOW_CRYSTAL.get().defaultBlockState()
                .setValue(GlowCrystalBlock.FACING, facing)
                .setValue(GlowCrystalBlock.SHAPE, shape)
                .setValue(GlowCrystalBlock.SIZE, size);
        level.setBlock(pos, crystal, BLOCK_UPDATE_FLAGS);
    }
}
