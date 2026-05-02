package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Particle profile for a progressive area-coverage pipeline. Two events
 * fire per layer: a preview (before the layer is mined) and an
 * on-struck signal (when the layer is mined, scaled by destroyed
 * count). Implementations are stateless singletons looked up by name
 * from {@link LayerVisualsType}.
 */
public interface LayerVisuals {

    /**
     * Emits the preview particles for a layer about to be mined.
     *
     * @param level      the server level
     * @param origin     the marker block position (chain origin)
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param stackCount the current stack count (for footprint scaling)
     */
    void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                 int stepIndex, int stackCount);

    /**
     * Emits the on-struck particles for a layer that was just mined.
     *
     * @param level      the server level
     * @param origin     the marker block position (chain origin)
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param destroyed  the number of blocks destroyed in this layer
     */
    void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                       int stepIndex, int destroyed);
}
