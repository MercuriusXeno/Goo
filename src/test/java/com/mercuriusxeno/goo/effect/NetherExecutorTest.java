package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.world.NetherBehavior;
import com.mercuriusxeno.goo.data.GooValue;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for the nether accumulator helper. The sphere walk itself is
 * stateful (touches {@code ServerLevel}), but the per-block merge step is
 * extracted as {@link NetherExecutor#mergeValue} so its behavior can be
 * exercised here without spinning up Minecraft's {@code Bootstrap}.
 */
class NetherExecutorTest {

    /**
     * Merging into an empty accumulator plants the value verbatim.
     */
    @Test
    void mergeValue_emptyAccumulator_plantsValue() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        GooValue block = new GooValue(Map.of(GooType.ROCK, 1152, GooType.NETHER, 500));

        NetherBehavior.mergeValue(totals, block);

        assertEquals(1152, totals.get(GooType.ROCK));
        assertEquals(500, totals.get(GooType.NETHER));
        assertEquals(2, totals.size());
    }

    /**
     * Merging two blocks with the same type sums their amounts.
     */
    @Test
    void mergeValue_sameType_sums() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        GooValue grass = new GooValue(Map.of(GooType.ROCK, 1152));
        GooValue dirt = new GooValue(Map.of(GooType.ROCK, 1000));

        NetherBehavior.mergeValue(totals, grass);
        NetherBehavior.mergeValue(totals, dirt);

        assertEquals(2152, totals.get(GooType.ROCK));
    }

    /**
     * Merging blocks with different types keeps both, independently.
     */
    @Test
    void mergeValue_differentTypes_independent() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.ROCK, 1000)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.FROST, 500)));

        assertEquals(1000, totals.get(GooType.ROCK));
        assertEquals(500, totals.get(GooType.FROST));
    }

    /**
     * Many blocks of grass (simulating a nether hit on a grass layer) accumulate cleanly.
     */
    @Test
    void mergeValue_repeatedGrass_accumulates() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        GooValue grass = new GooValue(Map.of(GooType.ROCK, 1152));
        int blockCount = 120;

        for (int i = 0; i < blockCount; i++) {
            NetherBehavior.mergeValue(totals, grass);
        }

        assertEquals(1152 * blockCount, totals.get(GooType.ROCK));
        // Single entry → nether BE will drop a single omniblob at the center.
        assertEquals(1, totals.size());
    }

    /**
     * Empty GooValue is a no-op on the accumulator.
     */
    @Test
    void mergeValue_empty_noop() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        totals.put(GooType.ROCK, 1000);

        NetherBehavior.mergeValue(totals, GooValue.EMPTY);

        assertEquals(1000, totals.get(GooType.ROCK));
        assertEquals(1, totals.size());
    }

    /**
     * A block with multiple goo types distributes correctly across the accumulator.
     */
    @Test
    void mergeValue_multiType_distributes() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        GooValue complex = new GooValue(Map.of(
                GooType.ROCK, 800,
                GooType.BLAZE, 200,
                GooType.NETHER, 100));

        NetherBehavior.mergeValue(totals, complex);

        assertEquals(800, totals.get(GooType.ROCK));
        assertEquals(200, totals.get(GooType.BLAZE));
        assertEquals(100, totals.get(GooType.NETHER));
    }

    /**
     * Cross-tick: accumulator survives merges across independent GooValue instances.
     */
    @Test
    void mergeValue_mixedAccumulation_producesCorrectTotals() {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);

        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.ROCK, 1152)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.ROCK, 1000, GooType.FROST, 250)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.BLAZE, 500)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooType.FROST, 750)));

        assertEquals(2152, totals.get(GooType.ROCK));
        assertEquals(1000, totals.get(GooType.FROST));
        assertEquals(500, totals.get(GooType.BLAZE));
        assertTrue(totals.get(GooType.NETHER) == null || totals.get(GooType.NETHER) == 0);
    }
}
