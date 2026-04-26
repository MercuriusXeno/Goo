package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Frost goo: cold-snap freezer. Stitched merge of the prior FrostEffect
 * (instant world hit), FrostBehavior (chain marker fuse), and FrostExecutor
 * (block-conversion utility). Implements both WorldEffect (instant on blob
 * impact) and ChainBehavior (fused detonation from a chain marker BE);
 * static utility methods convert blocks for both lifecycles plus the
 * data-driven ProgressiveAreaBlock pipeline.
 */
public final class FrostBehavior implements WorldEffect, ChainBehavior {

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
    /** Array index for Z component in offset triples. */
    private static final int Z_INDEX = 2;

    // --- WorldEffect (instant blob hit) ---

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.frostColdSnap(level, pos, targetFace);
    }

    // --- ChainBehavior (fused chain marker detonation) ---

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        int stackCount = be.getStackCount();
        Direction placedFace = be.getPlacedFace();
        boolean flatMode = false;
        boolean underwater = level.getFluidState(pos).isSource();
        if (flatMode) {
            executeFlatMode(level, pos, placedFace, stackCount);
        } else if (underwater) {
            executeTunnel(level, pos, placedFace, stackCount);
        } else {
            int radius = AbilityMath.computeFreezeRadius(stackCount);
            BlockPos center = pos.relative(placedFace.getOpposite());
            execute(level, center, radius);
        }
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        // Instant: never ticks.
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        // No state.
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // No state.
    }

    // --- Block-conversion utility (used by both lifecycles + ProgressiveAreaBlock) ---

    /**
     * Freezes all convertible blocks in a sphere around the center.
     * Water becomes a non-melting mod ice block (melt-protected for the
     * field's lifetime, swapped to vanilla ice on expiry), lava becomes
     * obsidian, fires are extinguished, and plants are destroyed.
     *
     * @param level  the server level
     * @param center the center of the freeze sphere
     * @param radius the freeze radius
     */
    public static void execute(ServerLevel level, BlockPos center, int radius) {
        convertSphere(level, center, radius);
        spawnEffects(level, center, radius);
    }

    /**
     * Freezes all convertible blocks in a flat Euclidean circle footprint.
     * Used when the frost chain marker is in flat mode.
     *
     * @param level      the server level
     * @param origin     the chain marker position
     * @param placedFace the face the marker was placed on
     * @param stackCount the blob stack count
     */
    public static void executeFlatMode(ServerLevel level, BlockPos origin,
            Direction placedFace, int stackCount) {
        List<int[]> offsets = ChainFootprint.computeRegionOffsets(
                stackCount, true, placedFace);
        for (int[] o : offsets) {
            convertBlock(level, origin.offset(o[0], o[1], o[Z_INDEX]));
        }
        int radius = AbilityMath.computeFreezeRadius(stackCount);
        spawnEffects(level, origin, radius);
    }

    /**
     * Freezes all convertible blocks in a tunnel footprint (same shape
     * as rock/blaze). Used when the frost marker is underwater to
     * convert the area ahead instead of a sphere around the player.
     *
     * @param level      the server level
     * @param origin     the chain marker position
     * @param placedFace the face the marker was placed on
     * @param stackCount the blob stack count
     */
    public static void executeTunnel(ServerLevel level, BlockPos origin,
            Direction placedFace, int stackCount) {
        List<int[]> offsets = ChainFootprint.computeRegionOffsets(
                stackCount, false, placedFace);
        for (int[] o : offsets) {
            convertBlock(level, origin.offset(o[0], o[1], o[Z_INDEX]));
        }
        int radius = AbilityMath.computeFreezeRadius(stackCount);
        spawnEffects(level, origin, radius);
    }

    /**
     * Iterates all blocks within the sphere and converts eligible ones.
     *
     * @param level  the server level
     * @param center the center of the freeze sphere
     * @param radius the freeze radius
     */
    private static void convertSphere(ServerLevel level, BlockPos center, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                convertSlice(level, center, dx, dy, radius, r2);
            }
        }
    }

    /**
     * Converts eligible blocks along the z-axis for one (dx, dy) slice of the sphere.
     *
     * @param level  the server level
     * @param center the center of the freeze sphere
     * @param dx     the x offset from center
     * @param dy     the y offset from center
     * @param radius the freeze radius
     * @param r2     the squared radius threshold
     */
    private static void convertSlice(ServerLevel level, BlockPos center,
            int dx, int dy, int radius, int r2) {
        for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dy * dy + dz * dz > r2) { continue; }
            convertBlock(level, center.offset(dx, dy, dz));
        }
    }

    /**
     * Freezes all convertible blocks in one spherical shell at the given
     * radius, centered one block into the wall from the marker.
     *
     * @param level      the server level
     * @param origin     the chain marker position
     * @param placedFace the face the marker was placed on
     * @param shellRadius the shell radius to freeze
     */
    public static void freezeShell(ServerLevel level, BlockPos origin,
            Direction placedFace, int shellRadius) {
        List<int[]> offsets = ChainFootprint.sphereShellOffsets(shellRadius, placedFace);
        for (int[] o : offsets) {
            convertBlock(level, origin.offset(o[0], o[1], o[Z_INDEX]));
        }
        spawnEffects(level, origin.relative(placedFace.getOpposite()), shellRadius);
    }

    /**
     * Freezes one layer of blocks at the given depth, following the same
     * progressive footprint pattern as rock/blaze mine layers.
     *
     * @param level      the server level
     * @param origin     the chain marker position
     * @param placedFace the face the marker was placed on
     * @param stepIndex  the current layer depth index
     * @param stackCount the blob stack count
     */
    public static void freezeLayer(ServerLevel level, BlockPos origin,
            Direction placedFace, int stepIndex, int stackCount) {
        BlockPos layerCenter = origin.relative(placedFace.getOpposite(), stepIndex + 1);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        for (int[] fp : footprint) {
            BlockPos target = resolveFootprintPos(layerCenter, blastAxis, fp);
            convertBlock(level, target);
        }
        int radius = AbilityMath.computeFreezeRadius(stackCount);
        spawnEffects(level, layerCenter, radius);
    }

    /** Maps a 2D footprint offset to a world position at the layer center.
     *
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis along which layers advance
     * @param fp          the 2D footprint offset {perpA, perpB}
     * @return the world position
     */
    private static BlockPos resolveFootprintPos(BlockPos layerCenter,
            Direction.Axis blastAxis, int[] fp) {
        return switch (blastAxis) {
            case X -> layerCenter.offset(0, fp[0], fp[1]);
            case Y -> layerCenter.offset(fp[0], 0, fp[1]);
            case Z -> layerCenter.offset(fp[0], fp[1], 0);
        };
    }

    /**
     * Converts a single block per the frost rules.
     *
     * @param level the server level
     * @param pos   the block position to convert
     */
    public static void convertBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block replacement = frostReplacement(state);
        if (replacement != null) {
            level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Returns the block to replace with under frost rules, or null if no conversion applies.
     *
     * @param state the current block state
     * @return the replacement block, or null
     */
    private static Block frostReplacement(BlockState state) {
        if (state.is(Blocks.WATER)) { return GooBlocks.MAGICKED_ICE.get(); }
        if (state.is(Blocks.LAVA)) { return Blocks.OBSIDIAN; }
        if (isFire(state) || isPlant(state)) { return Blocks.AIR; }
        return null;
    }

    /**
     * Returns true if the block state is any fire variant.
     *
     * @param state the block state to test
     * @return true if the block is fire or soul fire
     */
    private static boolean isFire(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
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
