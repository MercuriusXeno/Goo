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
 * rock-compatible blocks along the placed face's direction. The blast
 * travels INTO the surface the blob was attached to, not outward.
 * Depth scales with stack count via {@link EffectMath#computeImplosionDepth}.
 */
public final class RockExecutor {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Base dust particle count before per-block addition. */
    private static final int DUST_BASE_PARTICLES = 15;
    /** Additional dust particles per destroyed block. */
    private static final int DUST_PARTICLES_PER_BLOCK = 5;
    /** Base particle spread radius. */
    private static final double DUST_BASE_SPREAD = 0.3;
    /** Spread increase per destroyed block. */
    private static final double DUST_SPREAD_PER_BLOCK = 0.1;
    /** Dust particle velocity. */
    private static final double DUST_PARTICLE_SPEED = 0.02;
    /** Base sound volume for stone break. */
    private static final float BREAK_BASE_VOLUME = 1.0f;
    /** Volume increase per stack count. */
    private static final float BREAK_VOLUME_PER_STACK = 0.2f;
    /** Sound pitch for stone break. */
    private static final float BREAK_PITCH = 0.6f;
    /** Mine result: block was mined successfully. */
    private static final int MINE_SUCCESS = 1;
    /** Mine result: block was air, skip to next. */
    private static final int MINE_SKIP = 0;
    /** Mine result: hit non-rock, stop the column. */
    private static final int MINE_STOP = -1;

    private RockExecutor() {}

    /**
     * Fires the directional rock implosion. Mines rock-compatible blocks
     * starting from the anchor position, traveling in the direction the
     * marker was facing (into the surface it was placed on).
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
        int destroyed = mineAlongAxis(level, pos, blastDir, depth);
        spawnEffects(level, pos, blastDir, destroyed, stackCount);
    }

    /**
     * Mines rock-compatible blocks along the blast axis, returning the count destroyed.
     *
     * @param level    the server level
     * @param origin   the starting position
     * @param blastDir the direction to mine
     * @param depth    the maximum mining depth
     * @return the number of blocks destroyed
     */
    private static int mineAlongAxis(ServerLevel level, BlockPos origin,
            Direction blastDir, int depth) {
        BlockPos current = origin;
        int destroyed = 0;
        for (int i = 0; i < depth; i++) {
            current = current.relative(blastDir);
            int result = tryMineBlock(level, current);
            if (result < 0) { break; }
            destroyed += result;
        }
        return destroyed;
    }

    /**
     * Attempts to mine a single block. Returns 1 if mined, 0 if skipped (air), -1 if chain stops.
     *
     * @param level the server level
     * @param pos   the block position to mine
     * @return {@link #MINE_SUCCESS}, {@link #MINE_SKIP}, or {@link #MINE_STOP}
     */
    private static int tryMineBlock(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) { return MINE_STOP; }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { return MINE_SKIP; }
        if (!isRockBlock(level, state)) { return MINE_STOP; }
        level.destroyBlock(pos, true);
        return MINE_SUCCESS;
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
     * Spawns directional dust particles and plays a crumble sound.
     *
     * @param level      the server level
     * @param origin     the implosion origin position
     * @param blastDir   the blast direction
     * @param destroyed  the number of blocks destroyed
     * @param stackCount the raw stack count
     */
    private static void spawnEffects(ServerLevel level, BlockPos origin,
                                     Direction blastDir, int destroyed,
                                     int stackCount) {
        spawnDustParticles(level, origin, blastDir, destroyed);
        playCrumbleSound(level, origin, stackCount);
    }

    /**
     * Spawns directional dust particles at the blast midpoint.
     *
     * @param level     the server level
     * @param origin    the implosion origin position
     * @param blastDir  the blast direction
     * @param destroyed the number of blocks destroyed
     */
    private static void spawnDustParticles(ServerLevel level, BlockPos origin,
            Direction blastDir, int destroyed) {
        double cx = origin.getX() + BLOCK_CENTER_OFFSET + blastDir.getStepX() * destroyed * BLOCK_CENTER_OFFSET;
        double cy = origin.getY() + BLOCK_CENTER_OFFSET + blastDir.getStepY() * destroyed * BLOCK_CENTER_OFFSET;
        double cz = origin.getZ() + BLOCK_CENTER_OFFSET + blastDir.getStepZ() * destroyed * BLOCK_CENTER_OFFSET;
        int particleCount = DUST_BASE_PARTICLES + DUST_PARTICLES_PER_BLOCK * destroyed;
        double spread = DUST_BASE_SPREAD + destroyed * DUST_SPREAD_PER_BLOCK;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, particleCount, spread, spread, spread, DUST_PARTICLE_SPEED);
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
