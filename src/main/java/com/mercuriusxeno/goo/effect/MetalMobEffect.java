package com.mercuriusxeno.goo.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Deals direct magic damage to simulate a metal javelin strike. */
public final class MetalMobEffect {

    private static final float METAL_JAVELIN_DAMAGE = 8.0f;

    private MetalMobEffect() {}

    /**
     * Deals direct magic damage to the target (metal javelin).
     *
     * @param target the entity to damage
     */
    public static void apply(LivingEntity target) {
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), METAL_JAVELIN_DAMAGE);
    }
}
