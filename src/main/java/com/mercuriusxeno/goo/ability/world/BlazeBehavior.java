package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Blaze world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is owned by the
 * data-driven {@link com.mercuriusxeno.goo.ability.ProgressiveAreaBlock}
 * pipeline (fortune-smelt + blaze-flame visuals + generic-explode
 * audio); see {@code blaze_tunnel}/{@code blaze_flat} ability JSON for
 * the wiring.
 */
public final class BlazeBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.blazeExplosion(level, pos, targetFace);
    }
}
