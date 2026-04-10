package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Performs the rock chain effect: a directional implosion that mines
 * rock-compatible blocks in a 3x3 column travelling into the surface
 * the marker was attached to. Depth scales with stack count via
 * {@link EffectMath#computeImplosionDepth}.
 */
public final class RockExecutor {

    /** Half-width of the 3x3 footprint. */
    private static final int FOOTPRINT_HALF = 1;
    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Base dust particle count before per-block addition. */
    private static final int DUST_BASE_PARTICLES = 15;
    /** Additional dust particles per destroyed block. */
    private static final int DUST_PARTICLES_PER_BLOCK = 2;
    /** Base particle spread radius perpendicular to the blast axis. */
    private static final double DUST_BASE_SPREAD = 0.8;
    /** Spread increase per destroyed block. */
    private static final double DUST_SPREAD_PER_BLOCK = 0.02;
    /** Dust particle velocity. */
    private static final double DUST_PARTICLE_SPEED = 0.02;
    /** Base sound volume for stone break. */
    private static final float BREAK_BASE_VOLUME = 1.0f;
    /** Volume increase per stack count. */
    private static final float BREAK_VOLUME_PER_STACK = 0.2f;
    /** Sound pitch for stone break. */
    private static final float BREAK_PITCH = 0.6f;

    private RockExecutor() {}

    /**
     * Fires the rock implosion. Mines rock-compatible blocks in a 3x3
     * footprint perpendicular to the placed face, travelling {@code depth}
     * layers into the surface the marker was attached to. Per DESIGN-TYPES:
     * "in one direction" -- the blast travels along the face the blob hit.
     *
     * @param level      the server level
     * @param pos        the anchor block position
     * @param depth      computed implosion depth
     * @param stackCount the raw stack count
     * @param placedFace the face the marker was attached to
     */
    public static void execute(ServerLevel level, BlockPos pos, int depth,
                               int stackCount, Direction placedFace) {
        Direction blastDir = placedFace.getOpposite();
        Direction.Axis blastAxis = blastDir.getAxis();
        int destroyed = mineColumn(level, pos, blastDir, blastAxis, depth);
        spawnEffects(level, pos, blastDir, depth, destroyed, stackCount);
    }

    /**
     * Mines a 3x3 column along the blast axis. Each layer of the column
     * is a 3x3 footprint perpendicular to the blast direction; non-rock
     * blocks in a layer are skipped but do not halt the implosion.
     *
     * @param level     the server level
     * @param origin    the anchor position
     * @param blastDir  the direction the blast travels
     * @param blastAxis the axis of the blast direction
     * @param depth     the column depth in layers
     * @return the number of blocks destroyed
     */
    private static int mineColumn(ServerLevel level, BlockPos origin,
                                  Direction blastDir, Direction.Axis blastAxis,
                                  int depth) {
        int destroyed = 0;
        for (int step = 0; step < depth; step++) {
            BlockPos layerCenter = origin.relative(blastDir, step);
            destroyed += mineLayer(level, layerCenter, blastAxis);
        }
        return destroyed;
    }

    /**
     * Mines the 3x3 footprint at the given layer center, perpendicular
     * to the blast axis.
     *
     * @param level       the server level
     * @param layerCenter the center of the current layer
     * @param blastAxis   the axis the blast travels along
     * @return the number of blocks destroyed in this layer
     */
    private static int mineLayer(ServerLevel level, BlockPos layerCenter,
                                 Direction.Axis blastAxis) {
        int destroyed = 0;
        for (int a = -FOOTPRINT_HALF; a <= FOOTPRINT_HALF; a++) {
            for (int b = -FOOTPRINT_HALF; b <= FOOTPRINT_HALF; b++) {
                BlockPos target = offsetPerpendicular(layerCenter, blastAxis, a, b);
                if (tryMineBlock(level, target)) { destroyed++; }
            }
        }
        return destroyed;
    }

    /**
     * Attempts to mine a single block if it is in-bounds, non-air, and
     * rock-compatible.
     *
     * @param level  the server level
     * @param target the position to attempt
     * @return true if a block was destroyed
     */
    private static boolean tryMineBlock(ServerLevel level, BlockPos target) {
        if (!level.isInWorldBounds(target)) { return false; }
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return false; }
        if (!isRockBlock(level, state)) { return false; }
        level.destroyBlock(target, true);
        return true;
    }

    /**
     * Offsets a position in the two axes perpendicular to the blast axis,
     * producing one cell of the 3x3 footprint at the current blast layer.
     *
     * @param center    the center of the current blast layer
     * @param blastAxis the axis the blast travels along
     * @param a         first perpendicular offset
     * @param b         second perpendicular offset
     * @return the footprint cell position
     */
    private static BlockPos offsetPerpendicular(BlockPos center,
                                                Direction.Axis blastAxis,
                                                int a, int b) {
        return switch (blastAxis) {
            case X -> center.offset(0, a, b);
            case Y -> center.offset(a, 0, b);
            case Z -> center.offset(a, b, 0);
        };
    }

    /**
     * Checks if a block's item form is rock-compatible by goo composition.
     *
     * @param level the server level
     * @param state the block state to check
     * @return true if the block is rock-compatible
     */
    private static boolean isRockBlock(ServerLevel level, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return false; }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        return EffectMath.isRockCompatible(value);
    }

    /**
     * Spawns dust particles along the imploded column and plays a crumble sound.
     * Particles stretch along the blast axis and fill the 3x3 footprint
     * perpendicular to it, centered at the far end of the column.
     *
     * @param level      the server level
     * @param origin     the implosion origin position
     * @param blastDir   the direction the blast travels
     * @param depth      the full column depth
     * @param destroyed  the number of blocks destroyed
     * @param stackCount the raw stack count
     */
    private static void spawnEffects(ServerLevel level, BlockPos origin,
                                     Direction blastDir, int depth,
                                     int destroyed, int stackCount) {
        spawnDustParticles(level, origin, blastDir, depth, destroyed);
        playCrumbleSound(level, origin, stackCount);
    }

    /**
     * Spawns dust particles centered at the imploded column.
     *
     * @param level     the server level
     * @param origin    the implosion origin position
     * @param blastDir  the blast direction
     * @param depth     the full column depth
     * @param destroyed the number of blocks destroyed
     */
    private static void spawnDustParticles(ServerLevel level, BlockPos origin,
                                           Direction blastDir, int depth, int destroyed) {
        double cx = origin.getX() + BLOCK_CENTER_OFFSET + blastDir.getStepX() * depth * BLOCK_CENTER_OFFSET;
        double cy = origin.getY() + BLOCK_CENTER_OFFSET + blastDir.getStepY() * depth * BLOCK_CENTER_OFFSET;
        double cz = origin.getZ() + BLOCK_CENTER_OFFSET + blastDir.getStepZ() * depth * BLOCK_CENTER_OFFSET;

        int particleCount = DUST_BASE_PARTICLES + DUST_PARTICLES_PER_BLOCK * destroyed;
        double along = depth * BLOCK_CENTER_OFFSET + destroyed * DUST_SPREAD_PER_BLOCK;
        double perp = DUST_BASE_SPREAD;
        double spreadX = blastDir.getAxis() == Direction.Axis.X ? along : perp;
        double spreadY = blastDir.getAxis() == Direction.Axis.Y ? along : perp;
        double spreadZ = blastDir.getAxis() == Direction.Axis.Z ? along : perp;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, particleCount, spreadX, spreadY, spreadZ, DUST_PARTICLE_SPEED);
    }

    /**
     * Plays the crumble sound scaled by stack count.
     *
     * @param level      the server level
     * @param origin     the implosion origin position
     * @param stackCount the raw stack count
     */
    private static void playCrumbleSound(ServerLevel level, BlockPos origin, int stackCount) {
        level.playSound(null, origin, SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, BREAK_BASE_VOLUME + BREAK_VOLUME_PER_STACK * stackCount, BREAK_PITCH);
    }
}
