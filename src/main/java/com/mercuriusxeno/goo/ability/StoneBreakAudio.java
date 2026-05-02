package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Stone-break audio profile: low pitch dipping per step, paired with
 * the rock implosion's silk-touch break. Migrated from the legacy
 * {@code RockBehavior.playLayerSound} so the per-step pipeline can
 * dispatch by name.
 */
final class StoneBreakAudio implements LayerAudio {

    static final StoneBreakAudio INSTANCE = new StoneBreakAudio();

    private static final float LAYER_VOLUME_BASE = 0.55f;
    private static final float LAYER_VOLUME_PER_STACK = 0.08f;
    private static final float LAYER_PITCH_BASE = 0.75f;
    private static final float LAYER_PITCH_STEP = 0.03f;
    private static final float LAYER_PITCH_MIN = 0.45f;

    private StoneBreakAudio() {
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed, int stackCount) {
        if (destroyed <= 0) {
            return;
        }
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        float volume = LAYER_VOLUME_BASE + LAYER_VOLUME_PER_STACK * stackCount;
        float pitch = Math.max(LAYER_PITCH_MIN,
                LAYER_PITCH_BASE - LAYER_PITCH_STEP * stepIndex);
        level.playSound(null, layerCenter, SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, volume, pitch);
    }
}
