package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Typhoon goo: launches entities upward with wind particles. */
final class TyphoonEffect implements WorldEffect {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Vertical offset for particles placed above the target block. */
    private static final double ABOVE_BLOCK_OFFSET = 1.0;

    /** Horizontal inflation radius. */
    private static final double H_INFLATE = 1.0;
    /** Vertical inflation radius (column height). */
    private static final double V_INFLATE = 5.0;
    /** Upward push strength. */
    private static final double PUSH_STRENGTH = 1.5;
    /** Wind particle count. */
    private static final int PARTICLE_COUNT = 30;
    /** Wind horizontal particle spread. */
    private static final double PARTICLE_H_SPREAD = 0.5;
    /** Wind vertical particle spread. */
    private static final double PARTICLE_V_SPREAD = 3.0;
    /** Wind particle speed. */
    private static final double PARTICLE_SPEED = 0.1;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        launchEntities(level, pos);
        spawnParticles(serverLevel, pos);
    }

    /**
     * Pushes all living entities in a vertical column upward.
     * @param level the current world
     * @param pos   the center of the vertical push column
     */
    private void launchEntities(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(H_INFLATE, V_INFLATE, H_INFLATE);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.push(0, PUSH_STRENGTH, 0);
            entity.hurtMarked = true;
        }
    }

    /**
     * Spawns upward wind cloud particles.
     * @param serverLevel the server-side world for particle emission
     * @param pos         the center of the wind cloud
     */
    private void spawnParticles(ServerLevel serverLevel, BlockPos pos) {
        serverLevel.sendParticles(ParticleTypes.CLOUD,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            PARTICLE_COUNT, PARTICLE_H_SPREAD, PARTICLE_V_SPREAD, PARTICLE_H_SPREAD, PARTICLE_SPEED);
    }
}
