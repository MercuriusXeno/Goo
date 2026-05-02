package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.world.FrostBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@link BlockEffect} that converts a single block under frost rules:
 * water becomes magicked-ice, lava becomes obsidian, fire and plants
 * become air, and other blocks are left unchanged.
 *
 * <p>The conversion table itself lives on {@link FrostBehavior} since
 * the standalone frost cold-snap and the data-driven freeze pipeline
 * share it.</p>
 */
public final class FreezeEffect implements BlockEffect {

    /** Singleton instance. */
    public static final FreezeEffect INSTANCE = new FreezeEffect();

    private FreezeEffect() {
    }

    @Override
    public boolean apply(ServerLevel level, BlockPos pos) {
        return FrostBehavior.convertBlock(level, pos);
    }
}
