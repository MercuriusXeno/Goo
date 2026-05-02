package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Sound profile for a progressive area-coverage pipeline. Emits a
 * localised cue per mined layer with volume scaling on stack count
 * and pitch dipping per step. Implementations are stateless
 * singletons looked up by name from {@link LayerAudioType}.
 */
@FunctionalInterface
public interface LayerAudio {

    /**
     * Plays the on-struck sound for a layer that was just mined.
     *
     * @param level      the server level
     * @param origin     the marker block position (chain origin)
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param destroyed  the number of blocks destroyed in this layer
     * @param stackCount the chain stack count (volume scaling)
     */
    void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                       int stepIndex, int destroyed, int stackCount);
}
