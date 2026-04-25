package com.mercuriusxeno.goo.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Freezes a mob in place: disables AI, makes invulnerable, and applies glowing. */
public final class AeonMobEffect {

    private static final int AEON_GLOW_DURATION = 60;

    private AeonMobEffect() {}

    /**
     * Freezes a mob in place: disables AI, makes invulnerable, and applies glowing.
     *
     * @param target the entity to freeze in time
     */
    public static void apply(LivingEntity target) {
        if (!(target instanceof Mob mob)) { return; }
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, AEON_GLOW_DURATION, 0));
        // TODO: Accumulate ritual progress across multiple applications
        // TODO: At 100% -> convert to egg item
    }
}
