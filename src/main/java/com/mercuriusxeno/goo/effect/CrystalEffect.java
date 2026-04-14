package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Crystal goo: chain marker shard cloud that shreds moving entities. */
final class CrystalEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.crystalCloud(level, pos, targetFace);
    }
}
