package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * Discovers an existing GooWorldEffect entity at a given block position.
 * Called before spawning a new effect - if one exists, stack onto it instead.
 */
public final class EffectStackFinder {

    private EffectStackFinder() {}

    /**
     * Finds the first effect entity of the given type anchored at {@code pos}.
     *
     * @param level the world to search
     * @param pos   the anchor block position
     * @param clazz the concrete effect class to look for
     * @return the existing effect, or empty if none found
     */
    public static <T extends GooWorldEffect> Optional<T> findAt(Level level, BlockPos pos, Class<T> clazz) {
        AABB box = new AABB(pos).inflate(0.5);
        return level.getEntitiesOfClass(clazz, box).stream()
                .filter(e -> e.getAnchorPos().equals(pos))
                .findFirst();
    }
}
