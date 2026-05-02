package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * One of the permutable axes of a block-break ability. A BlockEffect
 * decides what happens to a single block in the AoE: silk-touch break,
 * fortune-smelt break, freeze, replace, ignite, etc. The owning
 * pipeline (e.g. {@link ProgressiveAreaBlock}) walks the footprint and
 * fans out one call per cell.
 *
 * <p>Implementations must be stateless and singleton-friendly so they
 * can be registered once and shared across all chain markers.</p>
 */
@FunctionalInterface
public interface BlockEffect {

    /**
     * Applies the effect to a single block position.
     *
     * @param level the server level
     * @param pos   the target block position
     * @return true if the effect changed the block (destroyed, replaced, frozen);
     *     false if the cell was a no-op (block was air, immune, or out of bounds)
     */
    boolean apply(ServerLevel level, BlockPos pos);
}
