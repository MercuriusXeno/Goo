package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

/**
 * Performs the nether chain effect: dissolves every block in a spherical
 * volume whose item form has a registered goo value, spawning the block's
 * goo composition as blob items. Blocks without a goo value are left
 * untouched — there is no hardness gate, no vanilla fallback, and no
 * hardness-based filtering. Goo value presence is the only gate.
 *
 * <p>Effect blocks (chain markers, frost fields) in the radius are also
 * converted: they drop a single blob of their own goo type and are removed.
 */
public final class NetherExecutor {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Base soul particle count before stack scaling. */
    private static final int SOUL_PARTICLE_BASE = 20;
    /** Additional soul particles per stack. */
    private static final int SOUL_PARTICLE_PER_STACK = 10;
    /** Particle spread as a fraction of the conversion radius. */
    private static final double SOUL_SPREAD_PER_RANGE = 0.5;
    /** Soul particle velocity. */
    private static final double SOUL_PARTICLE_SPEED = 0.05;

    private NetherExecutor() {}

    /**
     * Fires the nether conversion. Iterates the spherical volume once;
     * each block is either (a) an effect block that drops one blob of its
     * own type, (b) a valued block that dissolves into one blob stack per
     * goo type, or (c) left alone.
     *
     * @param level      the server level
     * @param pos        the anchor block position (the marker itself)
     * @param range      computed conversion radius
     * @param stackCount the raw stack count
     * @param placedFace the face the marker was attached to (unused)
     */
    public static void execute(ServerLevel level, BlockPos pos, int range,
                               int stackCount, Direction placedFace) {
        EffectMath.forEachInSphere(pos, range, target -> {
            // Skip the marker block itself; the BE will remove it after we return.
            if (target.equals(pos)) { return; }
            convertOne(level, target);
        });

        spawnParticles(level, pos, range, stackCount);
    }

    /**
     * Converts a single block position: effect block → single blob drop,
     * valued block → composition drop, anything else → untouched.
     *
     * @param level  the server level
     * @param target the block position to consider
     */
    private static void convertOne(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return; }

        if (tryConvertEffectBlock(level, target, state)) { return; }
        tryConvertValuedBlock(level, target, state);
    }

    /**
     * If the block at {@code target} is a chain marker or frost field,
     * drops one blob of its goo type and removes it.
     *
     * @param level  the server level
     * @param target the block position
     * @param state  the block state at that position
     * @return true if an effect block was converted
     */
    private static boolean tryConvertEffectBlock(ServerLevel level, BlockPos target,
                                                 BlockState state) {
        if (state.is(GooBlocks.CHAIN_MARKER.get())) {
            if (level.getBlockEntity(target) instanceof ChainMarkerBlockEntity be) {
                dropSingleBlob(level, target, be.getGooType());
                level.removeBlock(target, false);
            }
            return true;
        }
        if (state.is(GooBlocks.FROST_FIELD.get())) {
            dropSingleBlob(level, target, GooType.FROST);
            level.removeBlock(target, false);
            return true;
        }
        return false;
    }

    /**
     * If the block has a registered goo value, drops one blob stack per
     * goo type in its composition and removes the block. Blocks without a
     * goo value (including bedrock, obsidian, or anything else the registry
     * doesn't know about) are deliberately left alone.
     *
     * @param level  the server level
     * @param target the block position
     * @param state  the block state at that position
     */
    private static void tryConvertValuedBlock(ServerLevel level, BlockPos target,
                                              BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return; }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) { return; }

        popValueAsBlobs(level, target, value);
        level.removeBlock(target, false);
    }

    /**
     * Pops one blob stack for each goo type in the given value. GooValue
     * amounts are stored in microblobs, matching createForOutput's unit.
     *
     * @param level  the server level
     * @param target the position to drop blobs at
     * @param value  the block's goo composition
     */
    private static void popValueAsBlobs(ServerLevel level, BlockPos target, GooValue value) {
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            int amount = entry.getValue();
            ItemStack stack = BlobStacks.createForOutput(entry.getKey(), amount);
            if (!stack.isEmpty()) {
                Block.popResource(level, target, stack);
            }
        }
    }

    /**
     * Drops a single blob of the given goo type at the target position.
     *
     * @param level  the server level
     * @param target the position to drop the blob at
     * @param type   the goo type
     */
    private static void dropSingleBlob(ServerLevel level, BlockPos target, GooType type) {
        ItemStack stack = BlobStacks.createForOutput(type, BlobStacks.MB_PER_BLOB);
        if (!stack.isEmpty()) {
            Block.popResource(level, target, stack);
        }
    }

    /**
     * Spawns a burst of SOUL particles at the marker position.
     *
     * @param level      the server level
     * @param pos        the anchor block position
     * @param range      the conversion radius
     * @param stackCount the raw stack count
     */
    private static void spawnParticles(ServerLevel level, BlockPos pos,
                                       int range, int stackCount) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;
        int count = SOUL_PARTICLE_BASE + SOUL_PARTICLE_PER_STACK * stackCount;
        double spread = range * SOUL_SPREAD_PER_RANGE;
        level.sendParticles(ParticleTypes.SOUL,
                cx, cy, cz, count, spread, spread, spread, SOUL_PARTICLE_SPEED);
    }
}
