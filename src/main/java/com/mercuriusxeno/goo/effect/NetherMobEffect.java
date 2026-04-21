package com.mercuriusxeno.goo.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Halves the target's current health and applies wither; withers are immune. */
public final class NetherMobEffect {

    private static final int NETHER_WITHER_DURATION = 200;
    private static final float NETHER_HEALTH_DIVISOR = 2.0f;

    private NetherMobEffect() {}

    /**
     * Halves the target's current health and applies wither. Withers are immune.
     *
     * @param target the entity to wither
     */
    public static void apply(LivingEntity target) {
        if (target.getType() != EntityType.WITHER) {
            float halfHealth = target.getHealth() / NETHER_HEALTH_DIVISOR;
            target.setHealth(halfHealth);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, NETHER_WITHER_DURATION, 1));
        }
    }
}
