package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Generic-explode audio profile: brighter pitch dipping per step,
 * paired with the blaze tunnel's fortune-smelt break. Migrated from
 * the legacy {@code BlazeBehavior.playLayerSound} so the per-step
 * pipeline can dispatch by name.
 */
final class GenericExplodeAudio implements LayerAudio {

    static final GenericExplodeAudio INSTANCE = new GenericExplodeAudio();

    private static final float LAYER_VOLUME_BASE = 0.4f;
    private static final float LAYER_VOLUME_PER_STACK = 0.06f;
    private static final float LAYER_PITCH_BASE = 1.1f;
    private static final float LAYER_PITCH_STEP = 0.03f;
    private static final float LAYER_PITCH_MIN = 0.7f;

    private GenericExplodeAudio() {
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
        level.playSound(null, layerCenter, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, volume, pitch);
    }
}
