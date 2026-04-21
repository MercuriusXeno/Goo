package com.mercuriusxeno.goo.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Disables AI and applies max slowness to simulate a stun. */
public final class PulseMobEffect {

    private static final int PULSE_STUN_DURATION = 100;
    private static final int PULSE_STUN_AMPLIFIER = 127;

    private PulseMobEffect() {}

    /**
     * Disables AI and applies max slowness to simulate a stun.
     *
     * @param target the entity to stun
     */
    public static void apply(LivingEntity target) {
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PULSE_STUN_DURATION, PULSE_STUN_AMPLIFIER));
        }
    }
}
