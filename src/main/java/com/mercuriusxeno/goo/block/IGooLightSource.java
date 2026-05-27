package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Marker for block entities whose goo contents contribute to block-light
 * emission. The host block's {@code getLightEmission} reads this via
 * {@code BlockGetter.getBlockEntity} and returns the contribution clamped
 * to {@link GooLightContribution#MAX_LIGHT}.
 */
public interface IGooLightSource {

    /**
     * @return current block-light contribution from goo contents, in [0, 15]
     */
    int gooLightEmission();

    /**
     * Convenience for {@code Block.getLightEmission(state, getter, pos)}
     * overrides on blocks that host an {@link IGooLightSource} BE.
     * Returns 0 when the BE is absent or not a goo light source -- safe
     * during chunk load and on unloaded edges.
     *
     * @param level the block-getter (level or chunk)
     * @param pos   the block position
     * @return goo emission for the BE at {@code pos}, or 0
     */
    static int blockEmissionFor(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IGooLightSource src ? src.gooLightEmission() : 0;
    }
}
