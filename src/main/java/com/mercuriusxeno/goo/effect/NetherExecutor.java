package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumMap;
import java.util.Map;

/**
 * Nether chain effect: walks the spherical volume once, accumulates all
 * recovered goo into a per-type mB map on the chain marker BE, removes the
 * affected blocks immediately, then hands control off to the BE's
 * {@link ChainMarkerBlockEntity.Phase#IMPLODING} phase. No intermediate
 * {@code ItemEntity} is spawned - the implosion animates for N ticks and
 * the BE emits one combined stack per goo type at the marker center during
 * {@link ChainMarkerBlockEntity.Phase#POPPING}.
 *
 * <p>Effect blocks in the sphere (chain markers, frost fields) contribute
 * their single-blob worth to the accumulator and are removed alongside
 * valued blocks. Blocks without a registered goo value are left alone -
 * the goo value registry is the only gate, there is no hardness fallback.
 */
public final class NetherExecutor {

    private NetherExecutor() {}

    /**
     * Fires the nether conversion. Seeds the chain marker BE's phase
     * machine; the actual destruction (sphere walk + block removal) is
     * deferred to {@link #walkAndDestroy}, which the BE calls at the
     * EXPAND → CONTRACT transition so the sphere is fully grown and
     * occluding before anything vanishes.
     *
     * @param level      the server level
     * @param pos        the anchor block position (the marker itself)
     * @param range      computed conversion radius
     * @param stackCount the raw stack count (unused - the BE reads its own field)
     * @param placedFace the face the marker was attached to (unused)
     */
    public static void execute(ServerLevel level, BlockPos pos, int range,
                               int stackCount, Direction placedFace) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity marker)) { return; }
        marker.beginImplosion(level, pos, range);
    }

    /**
     * Walks the spherical volume once, accumulates per-type mB totals,
     * and removes the affected blocks. Called by
     * {@link ChainMarkerBlockEntity} at the EXPAND → CONTRACT transition,
     * i.e. at the moment the black-hole sphere is fully grown and
     * visually occluding the blast zone.
     *
     * @param level  the server level
     * @param pos    the marker position (skipped during the walk)
     * @param range  effect radius in blocks
     * @return the accumulated totals, ready to be dropped at POPPING
     */
    public static GooContents walkAndDestroy(ServerLevel level, BlockPos pos, int range) {
        Map<GooType, Long> totals = new EnumMap<>(GooType.class);
        EffectMath.forEachInSphere(pos, range, target -> {
            if (target.equals(pos)) { return; }
            accumulateAndRemove(level, target, totals);
        });
        return new GooContents(totals);
    }

    /**
     * Processes a single position in the sphere: effect blocks contribute a
     * single blob of their own type, valued blocks contribute their full
     * composition, anything else is left untouched.
     *
     * @param level  the server level
     * @param target the block position to consider
     * @param totals per-type accumulator to merge into
     */
    private static void accumulateAndRemove(ServerLevel level, BlockPos target,
                                            Map<GooType, Long> totals) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return; }
        if (tryAccumulateEffectBlock(level, target, state, totals)) { return; }
        tryAccumulateValuedBlock(level, target, state, totals);
    }

    /**
     * If the block at {@code target} is a chain marker or frost field, adds
     * one blob of its goo type to {@code totals} and removes the block.
     *
     * @param level  the server level
     * @param target the block position
     * @param state  the block state at that position
     * @param totals per-type accumulator
     * @return true if an effect block was handled
     */
    private static boolean tryAccumulateEffectBlock(ServerLevel level, BlockPos target,
                                                    BlockState state, Map<GooType, Long> totals) {
        if (state.is(GooBlocks.CHAIN_MARKER.get())) {
            if (level.getBlockEntity(target) instanceof ChainMarkerBlockEntity be) {
                totals.merge(be.getGooType(), BlobStacks.MB_PER_BLOB, Long::sum);
                level.removeBlock(target, false);
            }
            return true;
        }
        if (state.is(GooBlocks.FROST_FIELD.get())) {
            totals.merge(GooType.FROST, BlobStacks.MB_PER_BLOB, Long::sum);
            level.removeBlock(target, false);
            return true;
        }
        return false;
    }

    /**
     * If the block has a registered goo value, merges its full composition
     * into {@code totals} and removes the block. Blocks without a goo value
     * (bedrock, obsidian, or anything else the registry doesn't know about)
     * are left alone.
     *
     * @param level  the server level
     * @param target the block position
     * @param state  the block state at that position
     * @param totals per-type accumulator
     */
    private static void tryAccumulateValuedBlock(ServerLevel level, BlockPos target,
                                                 BlockState state, Map<GooType, Long> totals) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return; }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) { return; }
        mergeValue(totals, value);
        level.removeBlock(target, false);
    }

    /**
     * Pure helper: merges a {@link GooValue}'s per-type amounts into a
     * shared totals map, widening {@code int} mB to {@code long} as it goes.
     * Extracted so unit tests can verify accumulation without spinning up
     * a server level.
     *
     * @param totals the accumulator to merge into
     * @param value  the goo value to add
     */
    public static void mergeValue(Map<GooType, Long> totals, GooValue value) {
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            int amount = entry.getValue();
            totals.merge(entry.getKey(), (long) amount, Long::sum);
        }
    }
}
