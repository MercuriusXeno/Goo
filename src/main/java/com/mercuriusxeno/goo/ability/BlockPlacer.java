package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Places a single block at the chain marker position when the fuse
 * expires. Owns the per-block-type state derivation (e.g. Glow
 * crystal: FACING from placedFace, SHAPE from blobShape, SIZE from
 * stackCount). Implementations are stateless singletons looked up by
 * name from {@link BlockPlacerType}.
 */
@FunctionalInterface
public interface BlockPlacer {

    /**
     * Places the configured block at the marker position, deriving its
     * blockstate from the marker's snapshot fields.
     *
     * @param level the server level
     * @param pos   the marker block position (and the placement target)
     * @param be    the marker block entity (for placedFace, stackCount, blobShape)
     */
    void place(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be);
}
