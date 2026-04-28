package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Pulse goo: sends neighbor updates to trigger observers/pistons, with a bell sound. */
final class PulseEffect implements WorldEffect {

    /** Bell sound volume. */
    private static final float SOUND_VOLUME = 1.0f;
    /** Bell sound pitch. */
    private static final float SOUND_PITCH = 2.0f;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            level.neighborChanged(neighbor, level.getBlockState(pos).getBlock(), null);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }
}
