package com.mercuriusxeno.goo.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** Attempts to clone a mob with probability inversely scaled by max health. */
public final class VitalMobEffect {

    private static final double VITAL_CLONE_BASE_CHANCE = 100.0;
    private static final double VITAL_CLONE_HEALTH_EXPONENT = 0.6;
    private static final int VITAL_CLONE_PERCENT_SCALE = 100;

    private VitalMobEffect() {}

    /**
     * Attempts to clone a mob with probability inversely scaled by max health.
     *
     * @param level  the current level
     * @param target the entity to clone
     */
    public static void apply(Level level, LivingEntity target) {
        if (!(target instanceof Mob mob)) { return; }
        if (!shouldClone(level, target)) { return; }
        spawnClone(level, mob);
    }

    /**
     * Rolls a health-scaled probability check for vital cloning.
     *
     * @param level  the current level
     * @param target the potential clone source
     * @return true if cloning should proceed
     */
    private static boolean shouldClone(Level level, LivingEntity target) {
        double chance = VITAL_CLONE_BASE_CHANCE / Math.pow(target.getMaxHealth(), VITAL_CLONE_HEALTH_EXPONENT);
        return level.getRandom().nextFloat() * VITAL_CLONE_PERCENT_SCALE < chance;
    }

    /**
     * Spawns a copy of the mob near its current position.
     *
     * @param level the current level
     * @param mob   the mob to clone
     */
    private static void spawnClone(Level level, Mob mob) {
        Entity clone = mob.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (clone != null) {
            clone.setPos(mob.getX() + level.getRandom().nextGaussian(), mob.getY(), mob.getZ() + level.getRandom().nextGaussian());
            level.addFreshEntity(clone);
        }
    }
}
