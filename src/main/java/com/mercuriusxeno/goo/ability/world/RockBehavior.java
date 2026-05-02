package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Rock world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is owned by the
 * data-driven {@link com.mercuriusxeno.goo.ability.ProgressiveAreaBlock}
 * pipeline (silk-break + rock-dust visuals + stone-break audio); see
 * {@code rock_tunnel}/{@code rock_flat} ability JSON for the wiring.
 */
public final class RockBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.rockImplosion(level, pos, targetFace);
    }
}
