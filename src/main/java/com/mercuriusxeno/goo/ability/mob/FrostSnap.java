package com.mercuriusxeno.goo.ability.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Deals freeze damage, adds freeze buildup, and applies heavy slowness. */
public final class FrostSnap {

    private static final float FROST_SNAP_DAMAGE = 4.0f;
    private static final int FROST_FREEZE_BUILDUP = 140;
    private static final int FROST_SLOW_DURATION = 60;
    private static final int FROST_SLOW_AMPLIFIER = 3;

    private FrostSnap() {}

    /**
     * Deals freeze damage, adds freeze buildup ticks, and applies heavy slowness.
     *
     * @param target the entity to freeze
     */
    public static void apply(LivingEntity target) {
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) { return; }
        target.hurtServer((ServerLevel) target.level(), target.damageSources().freeze(), FROST_SNAP_DAMAGE);
        target.setTicksFrozen(target.getTicksFrozen() + FROST_FREEZE_BUILDUP);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SLOW_DURATION, FROST_SLOW_AMPLIFIER));
    }
}
