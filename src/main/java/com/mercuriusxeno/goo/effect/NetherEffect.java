package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Nether goo: destroys soft blocks in a sphere and spawns soul particles. */
final class NetherEffect implements WorldEffect {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Conversion sphere radius. */
    private static final int CONVERT_RADIUS = 3;
    /** Maximum block hardness that can be dissolved. */
    private static final float MAX_HARDNESS = 3.0f;
    /** Soul particle count. */
    private static final int PARTICLE_COUNT = 30;
    /** Soul particle spread. */
    private static final double PARTICLE_SPREAD = 2.0;
    /** Soul particle speed. */
    private static final double PARTICLE_SPEED = 0.05;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        EffectMath.forEachInSphere(pos, CONVERT_RADIUS, target -> destroySoftBlock(level, target));
        spawnParticles(serverLevel, pos);
    }

    /**
     * Drops resources from and removes a block if breakable.
     * @param level the current world
     * @param pos   the block position to attempt destruction
     */
    private void destroySoftBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { return; }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness >= 0 && hardness <= MAX_HARDNESS) {
            Block.dropResources(state, level, pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Spawns soul particles at the center.
     * @param serverLevel the server-side world for particle emission
     * @param pos         the center of the soul particle burst
     */
    private void spawnParticles(ServerLevel serverLevel, BlockPos pos) {
        serverLevel.sendParticles(ParticleTypes.SOUL,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + BLOCK_CENTER_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            PARTICLE_COUNT, PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPEED);
    }
}
