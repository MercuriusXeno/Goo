package com.mercuriusxeno.goo.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Deals magic damage (2x to undead), crit particles, fire on undead, and glowing on survivors. */
public final class GlowMobEffect {

    private static final float GLOW_LASER_DAMAGE = 4.0f;
    private static final float GLOW_UNDEAD_MULTIPLIER = 2.0f;
    private static final int GLOW_IGNITE_SECONDS = 1;
    private static final int GLOW_CRIT_COUNT = 10;
    private static final double GLOW_CRIT_SPREAD = 0.5;
    private static final double GLOW_CRIT_SPEED = 0.1;
    private static final int GLOW_EFFECT_DURATION = 200;
    private static final double ENTITY_MID_HEIGHT = 0.5;

    private GlowMobEffect() {}

    /**
     * Glow laser: deals magic damage (2x to undead), crit particles,
     * sets undead on fire, and applies glowing to survivors.
     *
     * @param level  the current level
     * @param target the entity hit by the beam
     */
    public static void apply(Level level, LivingEntity target) {
        boolean undead = target.isInvertedHealAndHarm();
        float damage = undead ? GLOW_LASER_DAMAGE * GLOW_UNDEAD_MULTIPLIER : GLOW_LASER_DAMAGE;
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), damage);
        if (undead) {
            target.igniteForSeconds(GLOW_IGNITE_SECONDS);
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY(ENTITY_MID_HEIGHT), target.getZ(),
                    GLOW_CRIT_COUNT, GLOW_CRIT_SPREAD, GLOW_CRIT_SPREAD,
                    GLOW_CRIT_SPREAD, GLOW_CRIT_SPEED);
        }
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_EFFECT_DURATION, 0));
        }
    }
}
