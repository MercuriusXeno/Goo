package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Performs the instant freeze effect for frost goo. Converts liquids
 * to solid forms, extinguishes fires, and destroys plants in a sphere.
 * Frozen water becomes packed ice (melt-proof) while the frost field
 * is active; the field swaps it back to regular ice on expiry.
 */
public final class FrostExecutor {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Spread multiplier applied to freeze radius for particle distribution. */
    private static final double SNOWFLAKE_SPREAD_FACTOR = 0.8;
    /** Base number of snowflake particles before per-radius addition. */
    private static final int SNOWFLAKE_BASE_PARTICLES = 30;
    /** Additional snowflake particles per unit of radius. */
    private static final int SNOWFLAKE_PARTICLES_PER_RADIUS = 10;
    /** Sound volume for the ice-crack effect. */
    private static final float ICE_CRACK_VOLUME = 1.0f;
    /** Sound pitch for the ice-crack effect. */
    private static final float ICE_CRACK_PITCH = 0.5f;

    private FrostExecutor() {}

    /**
     * Freezes all convertible blocks in a sphere around the center.
     * Water becomes packed ice (melt-resistant), lava becomes obsidian,
     * fires are extinguished, and plants are destroyed.
     *
     * @param level  the server level
     * @param center the center of the freeze sphere
     * @param radius the freeze radius
     */
    public static void execute(ServerLevel level, BlockPos center, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) { continue; }
                    BlockPos target = center.offset(dx, dy, dz);
                    convertBlock(level, target);
                }
            }
        }
        spawnEffects(level, center, radius);
    }

    /**
     * Converts a single block per the frost rules.
     *
     * @param level the server level
     * @param pos   the block position to convert
     */
    private static void convertBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER)) {
            level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.LAVA)) {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (isPlant(state)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Returns true if the block state is a destructible plant.
     * Covers vines, lily pads, grasses, flowers, and similar foliage.
     *
     * @param state the block state to test
     * @return true if this is a plant that frost destroys
     */
    public static boolean isPlant(BlockState state) {
        return isVineOrAquatic(state)
                || isGrassOrFern(state)
                || isSmallFlower(state)
                || isTallFlowerOrBush(state);
    }

    /**
     * Returns true if the state is a vine, lily pad, or aquatic plant (seagrass, kelp).
     *
     * @param state the block state to test
     * @return true if the block is a vine or aquatic plant
     */
    private static boolean isVineOrAquatic(BlockState state) {
        return state.is(Blocks.VINE)
                || state.is(Blocks.LILY_PAD)
                || isAquaticPlant(state);
    }

    /**
     * Returns true if the state is an aquatic plant (seagrass or kelp).
     *
     * @param state the block state to test
     * @return true if the block is an aquatic plant
     */
    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT);
    }

    /**
     * Returns true if the state is a grass or fern variant.
     *
     * @param state the block state to test
     * @return true if the block is grass or fern
     */
    private static boolean isGrassOrFern(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    /**
     * Returns true if the state is a single-block flower (dandelion, poppy, tulips, etc.).
     *
     * @param state the block state to test
     * @return true if the block is a small flower
     */
    private static boolean isSmallFlower(BlockState state) {
        return isCommonFlower(state)
                || isTulip(state)
                || isDaisyOrLater(state);
    }

    /**
     * Returns true if the state is one of the common single-block flowers (dandelion through azure bluet).
     *
     * @param state the block state to test
     * @return true if the block is a common flower
     */
    private static boolean isCommonFlower(BlockState state) {
        return state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM);
    }

    /**
     * Returns true if the state is azure bluet, oxeye daisy, cornflower, or lily of the valley.
     *
     * @param state the block state to test
     * @return true if the block is a late-palette flower
     */
    private static boolean isDaisyOrLater(BlockState state) {
        return state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY);
    }

    /**
     * Returns true if the state is any tulip color variant.
     *
     * @param state the block state to test
     * @return true if the block is a tulip
     */
    private static boolean isTulip(BlockState state) {
        return state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP);
    }

    /**
     * Returns true if the state is a tall flower, berry bush, or dead bush.
     *
     * @param state the block state to test
     * @return true if the block is a tall flower or bush
     */
    private static boolean isTallFlowerOrBush(BlockState state) {
        return isTallFlower(state)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.DEAD_BUSH);
    }

    /**
     * Returns true if the state is a two-block-tall flower (sunflower, lilac, rose bush, peony).
     *
     * @param state the block state to test
     * @return true if the block is a tall flower
     */
    private static boolean isTallFlower(BlockState state) {
        return state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY);
    }

    /**
     * Spawns freeze particles and plays the ice-crack sound.
     *
     * @param level  the server level
     * @param center the center of the freeze sphere
     * @param radius the freeze radius
     */
    private static void spawnEffects(ServerLevel level, BlockPos center, int radius) {
        double cx = center.getX() + BLOCK_CENTER_OFFSET;
        double cy = center.getY() + BLOCK_CENTER_OFFSET;
        double cz = center.getZ() + BLOCK_CENTER_OFFSET;
        double spread = radius * SNOWFLAKE_SPREAD_FACTOR;
        int particleCount = SNOWFLAKE_BASE_PARTICLES + SNOWFLAKE_PARTICLES_PER_RADIUS * radius;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                cx, cy, cz, particleCount, spread, spread, spread, 0.0);
        level.playSound(null, center, SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, ICE_CRACK_VOLUME, ICE_CRACK_PITCH);
    }
}
