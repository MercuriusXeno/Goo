package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
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
 * recovered goo into a per-type mB map, removes the affected blocks, and
 * returns the totals so the calling {@link NetherBehavior} can drop them
 * at the end of the implosion lifecycle. No intermediate
 * {@code ItemEntity} is spawned during the walk.
 *
 * <p>The goo value registry is the sole gate: blocks with a registered
 * {@link GooValue} are consumed and converted, everything else (including
 * this mod's own effect blocks like chain markers and frost fields) is
 * left alone. No hardness fallback.
 */
public final class NetherExecutor {

    private NetherExecutor() {}

    /**
     * Walks the spherical volume once, accumulates per-type mB totals,
     * and removes the affected blocks. Called by {@link NetherBehavior}
     * at the EXPAND -&gt; HOLD transition, i.e. at the moment the black-hole
     * sphere is fully grown and visually occluding the blast zone.
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
     * Processes a single position in the sphere: blocks with a registered
     * goo value contribute their full composition and are removed, anything
     * else is left untouched.
     *
     * @param level  the server level
     * @param target the block position to consider
     * @param totals per-type accumulator to merge into
     */
    private static void accumulateAndRemove(ServerLevel level, BlockPos target,
                                            Map<GooType, Long> totals) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return; }
        tryAccumulateValuedBlock(level, target, state, totals);
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
