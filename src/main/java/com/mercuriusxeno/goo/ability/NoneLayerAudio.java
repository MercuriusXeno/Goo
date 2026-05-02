package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * No-op {@link LayerAudio}. Used for area pipelines that emit no
 * per-layer sound (currently the freeze pipeline).
 */
final class NoneLayerAudio implements LayerAudio {

    static final NoneLayerAudio INSTANCE = new NoneLayerAudio();

    private NoneLayerAudio() {
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed, int stackCount) {
    }
}
