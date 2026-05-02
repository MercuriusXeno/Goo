package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * No-op {@link LayerVisuals}. Used for area pipelines that emit no
 * preview or struck particles (currently the freeze pipeline).
 */
final class NoneLayerVisuals implements LayerVisuals {

    static final NoneLayerVisuals INSTANCE = new NoneLayerVisuals();

    private NoneLayerVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount) {
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
    }
}
