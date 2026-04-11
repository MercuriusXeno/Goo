package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Blaze goo: chain explosion on the hit face. */
final class BlazeEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.blazeExplosion(level, pos, targetFace);
    }
}
