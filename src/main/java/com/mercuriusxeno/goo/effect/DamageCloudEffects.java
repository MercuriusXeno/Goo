package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Damage-cloud world effects: metal urchin, crystal shards, and hex ensorcelled.
 * All three spawn an AreaEffectCloud; metal and crystal also deal immediate AoE
 * damage. Extracted from WorldEffects to keep method counts under the
 * TooManyMethods threshold.
 */
final class DamageCloudEffects {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Vertical offset for clouds placed above the target block. */
    private static final double ABOVE_BLOCK_OFFSET = 1.0;

    // -- Metal urchin parameters --
    /** Metal urchin damage cloud radius. */
    private static final float URCHIN_CLOUD_RADIUS = 1.5f;
    /** Metal urchin cloud duration in ticks (10 seconds). */
    private static final int URCHIN_CLOUD_DURATION = 200;
    /** Metal urchin immediate damage radius. */
    private static final double URCHIN_DAMAGE_RADIUS = 2.0;
    /** Metal urchin immediate damage amount. */
    private static final float URCHIN_DAMAGE = 3.0f;

    // -- Crystal shard parameters --
    /** Crystal shard damage cloud radius. */
    private static final float CRYSTAL_CLOUD_RADIUS = 2.0f;
    /** Crystal shard cloud duration in ticks (15 seconds). */
    private static final int CRYSTAL_CLOUD_DURATION = 300;
    /** Crystal shard cloud radius shrink rate per tick. */
    private static final float CRYSTAL_CLOUD_SHRINK_RATE = -0.005f;
    /** Crystal shard immediate damage radius. */
    private static final double CRYSTAL_DAMAGE_RADIUS = 2.5;
    /** Crystal shard immediate damage amount. */
    private static final float CRYSTAL_DAMAGE = 4.0f;

    // -- Hex ensorcelled parameters --
    /** Hex ensorcelled cloud radius. */
    private static final float HEX_CLOUD_RADIUS = 4.0f;
    /** Hex ensorcelled cloud duration in ticks (30 seconds). */
    private static final int HEX_CLOUD_DURATION = 600;

    private DamageCloudEffects() {}

    /**
     * Places a lingering damage cloud and deals immediate damage to nearby entities.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    static void metalUrchin(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        // Place a damage cloud that hurts entities walking through
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(URCHIN_CLOUD_RADIUS);
        cloud.setDuration(URCHIN_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.CRIT);
        level.addFreshEntity(cloud);
        // Damage entities in area immediately
        damageEntitiesInArea(level, pos, URCHIN_DAMAGE_RADIUS, URCHIN_DAMAGE);
    }

    /**
     * Places a shrinking crystal damage cloud and deals immediate AoE damage.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    static void crystalShards(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(CRYSTAL_CLOUD_RADIUS);
        cloud.setDuration(CRYSTAL_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(CRYSTAL_CLOUD_SHRINK_RATE); // slowly shrinks
        cloud.setCustomParticle(ParticleTypes.DAMAGE_INDICATOR);
        level.addFreshEntity(cloud);
        damageEntitiesInArea(level, pos, CRYSTAL_DAMAGE_RADIUS, CRYSTAL_DAMAGE);
    }

    /**
     * Spawns a large witch-particle cloud to create a dark ensorcelled zone.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    static void hexEnsorcelled(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) { return; }
        // Spawn a few hostile mobs near the impact point
        // For now, just spawn particles and make the area dark via area effect cloud
        AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        cloud.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        cloud.setRadius(HEX_CLOUD_RADIUS);
        cloud.setDuration(HEX_CLOUD_DURATION);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0);
        cloud.setCustomParticle(ParticleTypes.WITCH);
        level.addFreshEntity(cloud);
    }

    /**
     * Deals magic damage to all living entities within a radius of the position.
     *
     * @param level  the current level
     * @param pos    the center block position
     * @param radius the damage radius in blocks
     * @param damage the damage amount
     */
    private static void damageEntitiesInArea(Level level, BlockPos pos, double radius, float damage) {
        AABB area = new AABB(pos).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.hurt(level.damageSources().magic(), damage);
        }
    }
}
