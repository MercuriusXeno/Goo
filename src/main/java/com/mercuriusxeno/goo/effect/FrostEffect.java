package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Frost goo: chain marker cold snap - freezes water and lava in a spheroid. */
final class FrostEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.frostColdSnap(level, pos, targetFace);
    }
}
