package com.mercuriusxeno.goo.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Applies slowness and poison to simulate vine entanglement. */
public final class LeafMobEffect {

    private static final int LEAF_SLOW_DURATION = 100;
    private static final int LEAF_SLOW_AMPLIFIER = 2;
    private static final int LEAF_POISON_DURATION = 60;

    private LeafMobEffect() {}

    /**
     * Applies slowness and poison to simulate vine entanglement.
     *
     * @param target the entity to entangle
     */
    public static void apply(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LEAF_SLOW_DURATION, LEAF_SLOW_AMPLIFIER));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, LEAF_POISON_DURATION, 0));
    }
}
