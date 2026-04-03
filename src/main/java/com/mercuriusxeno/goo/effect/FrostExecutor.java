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
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos target = center.offset(dx, dy, dz);
                    convertBlock(level, target);
                }
            }
        }
        spawnEffects(level, center, radius);
    }

    /** Converts a single block per the frost rules. */
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
        return state.is(Blocks.VINE)
                || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.DEAD_BUSH);
    }

    /** Spawns freeze particles and plays the ice-crack sound. */
    private static void spawnEffects(ServerLevel level, BlockPos center, int radius) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        double spread = radius * 0.8;
        int particleCount = 30 + 10 * radius;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                cx, cy, cz, particleCount, spread, spread, spread, 0.0);
        level.playSound(null, center, SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, 1.0f, 0.5f);
    }
}
