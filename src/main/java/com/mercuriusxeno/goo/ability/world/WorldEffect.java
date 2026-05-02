package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * Pre-impact hook that runs in BOTH the legacy (no-ability) and the
     * data-driven (ability-id) blob landing paths. Returns true to
     * absorb the blob and skip downstream chain marker placement
     * entirely. Default returns false.
     *
     * <p>The canonical use is the Glow {@code grow-existing} rule: when
     * a glow blob hits an existing glow crystal, the crystal grows one
     * size step and no marker is placed. The check has to fire on both
     * paths (legacy and ability-driven) or selecting an ability from
     * the radial would silently disable the feature.</p>
     *
     * @param level      the server level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null if unknown
     * @return true if the blob was absorbed and no further placement should run
     */
    default boolean tryAbsorbAtTarget(ServerLevel level, BlockPos pos,
                                      @Nullable Direction targetFace) {
        return false;
    }
}
