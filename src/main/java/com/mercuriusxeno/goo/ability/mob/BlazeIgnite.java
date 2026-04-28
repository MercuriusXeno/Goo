package com.mercuriusxeno.goo.ability.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Sets the target and nearby non-fireproof entities on fire. */
public final class BlazeIgnite {

    private static final int BLAZE_IGNITE_SECONDS = 10;
    private static final double BLAZE_AOE_RADIUS = 2.5;
    private static final int BLAZE_NEARBY_IGNITE_SECONDS = 5;

    private BlazeIgnite() {}

    /**
     * Sets the target and nearby non-fireproof entities on fire.
     *
     * @param level  the current level
     * @param target the primary target entity
     */
    public static void apply(Level level, LivingEntity target) {
        target.igniteForSeconds(BLAZE_IGNITE_SECONDS);
        AABB area = target.getBoundingBox().inflate(BLAZE_AOE_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!nearby.fireImmune()) {
                nearby.igniteForSeconds(BLAZE_NEARBY_IGNITE_SECONDS);
            }
        }
    }
}
