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
        // The blast direction is opposite the placed face: if the blob
        // was placed on the west face of a block, it travels east (into the block).
        Direction blastDir = placedFace.getOpposite();
        BlockPos current = pos;

        int destroyed = 0;
        for (int i = 0; i < depth; i++) {
            current = current.relative(blastDir);
            if (!level.isInWorldBounds(current)) { break; }

            BlockState state = level.getBlockState(current);
            if (state.isAir()) { continue; }
            if (!isRockBlock(level, state)) { break; }

            level.destroyBlock(current, true);
            destroyed++;
        }

        spawnEffects(level, pos, blastDir, destroyed, stackCount);
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
        double cx = origin.getX() + BLOCK_CENTER_OFFSET + blastDir.getStepX() * destroyed * BLOCK_CENTER_OFFSET;
        double cy = origin.getY() + BLOCK_CENTER_OFFSET + blastDir.getStepY() * destroyed * BLOCK_CENTER_OFFSET;
        double cz = origin.getZ() + BLOCK_CENTER_OFFSET + blastDir.getStepZ() * destroyed * BLOCK_CENTER_OFFSET;

        int particleCount = DUST_BASE_PARTICLES + DUST_PARTICLES_PER_BLOCK * destroyed;
        double spread = DUST_BASE_SPREAD + destroyed * DUST_SPREAD_PER_BLOCK;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, particleCount, spread, spread, spread, DUST_PARTICLE_SPEED);

        level.playSound(null, origin, SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, BREAK_BASE_VOLUME + BREAK_VOLUME_PER_STACK * stackCount, BREAK_PITCH);
    }
}
