package com.mercuriusxeno.goo.ability.mob;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Applies slowness, weakness, and poison; bosses are immune. */
public final class ShroomToxify {

    private static final int SHROOM_DEBUFF_DURATION = 200;

    private ShroomToxify() {}

    /**
     * Applies slowness, weakness, and poison. Bosses (wither/dragon) are immune.
     *
     * @param target the entity to debuff
     */
    public static void apply(LivingEntity target) {
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) { return; }
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHROOM_DEBUFF_DURATION, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SHROOM_DEBUFF_DURATION, 1));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, SHROOM_DEBUFF_DURATION, 0));
    }
}
