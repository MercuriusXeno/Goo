package com.mercuriusxeno.goo.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Deals primary and AoE splash magic damage with damage indicator particles. */
public final class CrystalMobEffect {

    private static final float CRYSTAL_PRIMARY_DAMAGE = 4.0f;
    private static final float CRYSTAL_SPLASH_DAMAGE = 2.0f;
    private static final double CRYSTAL_AOE_RADIUS = 3.0;
    private static final double CRYSTAL_PARTICLE_Y_OFFSET = 1.0;
    private static final int CRYSTAL_PARTICLE_COUNT = 15;
    private static final double CRYSTAL_PARTICLE_H_SPREAD = 1.5;
    private static final double CRYSTAL_PARTICLE_V_SPREAD = 0.5;

    private CrystalMobEffect() {}

    /**
     * Deals damage to the target and splash damage to nearby entities (crystal flechettes).
     *
     * @param level  the current level
     * @param target the primary target entity
     */
    public static void apply(Level level, LivingEntity target) {
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), CRYSTAL_PRIMARY_DAMAGE);
        splashDamageNearby(level, target);
        sendDamageParticles(level, target);
    }

    /**
     * Deals splash magic damage to all living entities near the target.
     *
     * @param level  the current level
     * @param target the primary target (excluded from splash)
     */
    private static void splashDamageNearby(Level level, LivingEntity target) {
        AABB area = target.getBoundingBox().inflate(CRYSTAL_AOE_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (nearby != target) {
                nearby.hurtServer((ServerLevel) nearby.level(), nearby.damageSources().magic(), CRYSTAL_SPLASH_DAMAGE);
            }
        }
    }

    /**
     * Sends damage indicator particles at the target's position.
     *
     * @param level  the current level
     * @param target the target entity
     */
    private static void sendDamageParticles(Level level, LivingEntity target) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + CRYSTAL_PARTICLE_Y_OFFSET, target.getZ(),
                    CRYSTAL_PARTICLE_COUNT, CRYSTAL_PARTICLE_H_SPREAD, CRYSTAL_PARTICLE_V_SPREAD,
                    CRYSTAL_PARTICLE_H_SPREAD, 0.0);
        }
    }
}
