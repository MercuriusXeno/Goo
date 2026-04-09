package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Frost goo: instant freeze with persistent melt-resist field. */
final class FrostEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        ChainAndFrostEffects.frostFreeze(level, pos, targetFace);
    }
}
