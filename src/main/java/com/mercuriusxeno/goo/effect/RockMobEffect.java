package com.mercuriusxeno.goo.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Applies max slowness; if already petrified, kills and drops cobblestone. */
public final class RockMobEffect {

    private static final int ROCK_PETRIFY_DURATION = 100;
    private static final int ROCK_PETRIFY_AMPLIFIER = 127;
    private static final int ROCK_CRUSH_DROP_BOUND = 3;

    private RockMobEffect() {}

    /**
     * Applies max slowness; if already petrified, kills the target and drops cobblestone.
     *
     * @param level  the current level
     * @param target the entity to petrify
     */
    public static void apply(Level level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROCK_PETRIFY_DURATION, ROCK_PETRIFY_AMPLIFIER));
        MobEffectInstance existing = target.getEffect(MobEffects.SLOWNESS);
        if (existing != null && existing.getAmplifier() >= ROCK_PETRIFY_AMPLIFIER && level instanceof ServerLevel sl) {
            target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), Float.MAX_VALUE);
            target.spawnAtLocation(sl, new ItemStack(Items.COBBLESTONE, 1 + level.getRandom().nextInt(ROCK_CRUSH_DROP_BOUND)));
        }
    }
}
