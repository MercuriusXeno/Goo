package com.mercuriusxeno.goo.ability.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Detonates a vanilla explosion at the target's position on blob impact. */
public final class UnstableExplode {

    private static final float UNSTABLE_MOB_POWER = 2.0f;

    private UnstableExplode() {}

    /**
     * Detonates a vanilla explosion at the target's position on blob impact.
     *
     * @param level  the current level
     * @param target the entity that was hit
     */
    public static void apply(Level level, LivingEntity target) {
        level.explode(null, target.getX(), target.getY(), target.getZ(),
                UNSTABLE_MOB_POWER, Level.ExplosionInteraction.TNT);
    }
}
