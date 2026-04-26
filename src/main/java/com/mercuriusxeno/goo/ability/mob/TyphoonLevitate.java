package com.mercuriusxeno.goo.ability.mob;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Applies levitation to launch the target skyward. */
public final class TyphoonLevitate {

    private static final int TYPHOON_LEVITATE_DURATION = 100;

    private TyphoonLevitate() {}

    /**
     * Applies levitation to launch the target skyward.
     *
     * @param target the entity to levitate
     */
    public static void apply(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, TYPHOON_LEVITATE_DURATION, 1));
    }
}
