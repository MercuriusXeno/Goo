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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Performs the rock chain effect: a directional implosion that mines
 * rock-compatible blocks along the placed face's direction. The blast
 * travels INTO the surface the blob was attached to, not outward.
 * Depth scales with stack count via {@link EffectMath#computeImplosionDepth}.
 */
public final class RockExecutor {

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
            if (!level.isInWorldBounds(current)) break;

            BlockState state = level.getBlockState(current);
            if (state.isAir()) continue;
            if (!isRockBlock(level, state)) break;

            level.destroyBlock(current, true);
            destroyed++;
        }

        spawnEffects(level, pos, blastDir, destroyed, stackCount);
    }

    /** Checks if a block's item form is rock-compatible by goo composition. */
    private static boolean isRockBlock(ServerLevel level, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        return EffectMath.isRockCompatible(value);
    }

    /** Spawns directional dust particles and plays a crumble sound. */
    private static void spawnEffects(ServerLevel level, BlockPos origin,
                                     Direction blastDir, int destroyed,
                                     int stackCount) {
        double cx = origin.getX() + 0.5 + blastDir.getStepX() * destroyed * 0.5;
        double cy = origin.getY() + 0.5 + blastDir.getStepY() * destroyed * 0.5;
        double cz = origin.getZ() + 0.5 + blastDir.getStepZ() * destroyed * 0.5;

        int particleCount = 15 + 5 * destroyed;
        double spread = 0.3 + destroyed * 0.1;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, particleCount, spread, spread, spread, 0.02);

        level.playSound(null, origin, SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, 1.0f + 0.2f * stackCount, 0.6f);
    }
}
