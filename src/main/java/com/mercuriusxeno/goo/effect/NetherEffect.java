package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Nether goo: chain conversion on the hit face. */
final class NetherEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.netherConvert(level, pos, targetFace);
    }
}
