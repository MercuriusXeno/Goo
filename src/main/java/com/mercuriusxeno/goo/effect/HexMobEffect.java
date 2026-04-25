package com.mercuriusxeno.goo.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** Applies weakness and glowing with duration inversely scaled by current health. */
public final class HexMobEffect {

    private static final double HEX_CHARM_BASE_DURATION = 60.0;
    private static final double HEX_CHARM_HEALTH_EXPONENT = 0.4;
    private static final int HEX_TICKS_PER_SECOND = 20;
    private static final int HEX_WEAKNESS_AMPLIFIER = 4;

    private HexMobEffect() {}

    /**
     * Applies weakness and glowing with duration inversely scaled by current health.
     *
     * @param target  the entity to charm
     * @param thrower the entity that threw the blob, or null
     */
    public static void apply(LivingEntity target, @Nullable Entity thrower) {
        if (!(target instanceof Mob mob)) { return; }
        float health = target.getHealth();
        double duration = HEX_CHARM_BASE_DURATION / Math.pow(health, HEX_CHARM_HEALTH_EXPONENT);
        int ticks = (int)(duration * HEX_TICKS_PER_SECOND);
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, HEX_WEAKNESS_AMPLIFIER));
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
        // TODO: Full charm AI override (make mob passive to thrower)
    }
}
