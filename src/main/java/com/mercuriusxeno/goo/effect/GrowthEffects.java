package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Growth-themed world effects: leaf growth (crop ticking) and shroom spores
 * (mycelium spread + mushroom placement). Extracted from WorldEffects to
 * keep method counts under the TooManyMethods threshold.
 */
final class GrowthEffects {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Vertical offset for particles/entities placed above the target block. */
    private static final double ABOVE_BLOCK_OFFSET = 1.0;

    // -- Leaf growth parameters --
    /** Leaf growth area half-width (5x5 area). */
    private static final int LEAF_GROWTH_RADIUS = 2;
    /** Random ticks applied per block for leaf growth. */
    private static final int LEAF_GROWTH_TICKS = 3;
    /** Leaf growth particle count. */
    private static final int LEAF_PARTICLE_COUNT = 20;
    /** Leaf growth horizontal particle spread. */
    private static final double LEAF_PARTICLE_H_SPREAD = 2.0;
    /** Leaf growth vertical particle spread. */
    private static final double LEAF_PARTICLE_V_SPREAD = 0.5;

    // -- Shroom spore parameters --
    /** Shroom spore area half-width (5x5 area). */
    private static final int SHROOM_SPORE_RADIUS = 2;
    /** Probability of placing a mushroom on an air block above mycelium. */
    private static final float SHROOM_SPAWN_CHANCE = 0.2f;
    /** Shroom spore particle count. */
    private static final int SHROOM_PARTICLE_COUNT = 30;
    /** Shroom spore vertical offset for particles. */
    private static final double SHROOM_PARTICLE_Y_OFFSET = 1.5;
    /** Shroom spore horizontal particle spread. */
    private static final double SHROOM_PARTICLE_H_SPREAD = 2.0;
    /** Shroom spore vertical particle spread. */
    private static final double SHROOM_PARTICLE_V_SPREAD = 1.0;

    private GrowthEffects() {}

    /**
     * Applies random ticks to blocks in a 5x5 area to accelerate crop and plant growth.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    static void leafGrowth(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        for (int dx = -LEAF_GROWTH_RADIUS; dx <= LEAF_GROWTH_RADIUS; dx++) {
            for (int dz = -LEAF_GROWTH_RADIUS; dz <= LEAF_GROWTH_RADIUS; dz++) {
                tickGrowthColumn(serverLevel, pos.offset(dx, 0, dz));
            }
        }
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            LEAF_PARTICLE_COUNT, LEAF_PARTICLE_H_SPREAD, LEAF_PARTICLE_V_SPREAD, LEAF_PARTICLE_H_SPREAD, 0.0);
    }

    /**
     * Ticks growth on a single column: the target block and the block above it
     * (to catch crops sitting on farmland).
     *
     * @param level  the server level
     * @param target the ground-level position to tick
     */
    private static void tickGrowthColumn(ServerLevel level, BlockPos target) {
        tickGrowthAt(level, target);
        tickGrowthAt(level, target.above());
    }

    /**
     * Applies multiple random ticks to a single block if it supports random ticking.
     *
     * @param level the server level
     * @param pos   the position to tick
     */
    private static void tickGrowthAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isRandomlyTicking()) {
            for (int i = 0; i < LEAF_GROWTH_TICKS; i++) {
                state.randomTick(level, pos, level.getRandom());
            }
        }
    }

    /**
     * Converts grass/dirt to mycelium in a 5x5 area and randomly places mushrooms.
     *
     * @param level the current level
     * @param pos   the target block position
     */
    static void shroomSpores(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        spreadShroomArea(level, pos);
        serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + SHROOM_PARTICLE_Y_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET,
            SHROOM_PARTICLE_COUNT, SHROOM_PARTICLE_H_SPREAD, SHROOM_PARTICLE_V_SPREAD, SHROOM_PARTICLE_H_SPREAD, 0.0);
    }

    /**
     * Spreads mycelium and places mushrooms across the 5x5 area.
     * @param level the current level containing the target area
     * @param pos the center position of the 5x5 spore spread area
     */
    private static void spreadShroomArea(Level level, BlockPos pos) {
        for (int dx = -SHROOM_SPORE_RADIUS; dx <= SHROOM_SPORE_RADIUS; dx++) {
            for (int dz = -SHROOM_SPORE_RADIUS; dz <= SHROOM_SPORE_RADIUS; dz++) {
                BlockPos target = pos.offset(dx, 0, dz);
                spreadMycelium(level, target);
                tryPlaceMushroom(level, target.above());
            }
        }
    }

    /**
     * Converts grass or dirt to mycelium at the given position.
     *
     * @param level the current level
     * @param pos   the position to convert
     */
    private static void spreadMycelium(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            level.setBlock(pos, Blocks.MYCELIUM.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Randomly places a red or brown mushroom if the position is air and
     * the mushroom can survive there.
     *
     * @param level the current level
     * @param pos   the position to try placing a mushroom
     */
    private static void tryPlaceMushroom(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) { return; }
        if (level.getRandom().nextFloat() >= SHROOM_SPAWN_CHANCE) { return; }
        Block shroom = level.getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
        if (shroom.defaultBlockState().canSurvive(level, pos)) {
            level.setBlock(pos, shroom.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
