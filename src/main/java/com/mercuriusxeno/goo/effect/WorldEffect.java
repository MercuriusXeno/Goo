package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Polymorphic world effect applied when a goo blob hits a block. */
@FunctionalInterface
public interface WorldEffect {

    /**
     * Applies this effect at the given position.
     *
     * @param level      the world
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null if unknown
     */
    void apply(Level level, BlockPos pos, @Nullable Direction targetFace);
}
