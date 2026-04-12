package com.mercuriusxeno.goo.effect;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChainFootprint: shared scaling math for rock and blaze effects.
 */
class ChainFootprintTest {

    @Nested
    class TotalBlocks {
        @Test void oneStack() { assertEquals(1, ChainFootprint.totalBlocks(1)); }
        @Test void twoStacks() { assertEquals(5, ChainFootprint.totalBlocks(2)); }
        @Test void threeStacks() { assertEquals(9, ChainFootprint.totalBlocks(3)); }
        @Test void fourStacks() { assertEquals(18, ChainFootprint.totalBlocks(4)); }
        @Test void fiveStacks() { assertEquals(27, ChainFootprint.totalBlocks(5)); }
        @Test void sixStacks() { assertEquals(36, ChainFootprint.totalBlocks(6)); }
        @Test void twentySevenStacks() { assertEquals(225, ChainFootprint.totalBlocks(27)); }
    }

    @Nested
    class TunnelDepth {
        @Test void oneStack() { assertEquals(1, ChainFootprint.tunnelDepth(1)); }
        @Test void twoStacks() { assertEquals(1, ChainFootprint.tunnelDepth(2)); }
        @Test void threeStacks() { assertEquals(1, ChainFootprint.tunnelDepth(3)); }
        @Test void fourStacks() { assertEquals(2, ChainFootprint.tunnelDepth(4)); }
        @Test void fiveStacks() { assertEquals(3, ChainFootprint.tunnelDepth(5)); }
        @Test void twentySevenStacks() { assertEquals(25, ChainFootprint.tunnelDepth(27)); }
        @Test void capsAtMaxDepth() { assertEquals(25, ChainFootprint.tunnelDepth(30)); }
    }

    @Nested
    class LayerFootprint {
        @Test void singleBlock() {
            List<int[]> fp = ChainFootprint.layerFootprint(1);
            assertEquals(1, fp.size());
            assertArrayEquals(new int[]{0, 0}, fp.get(0));
        }

        @Test void crossHasFiveBlocks() {
            List<int[]> fp = ChainFootprint.layerFootprint(2);
            assertEquals(5, fp.size());
            assertTrue(containsOffset(fp, 0, 0));
            assertTrue(containsOffset(fp, 1, 0));
            assertTrue(containsOffset(fp, -1, 0));
            assertTrue(containsOffset(fp, 0, 1));
            assertTrue(containsOffset(fp, 0, -1));
        }

        @Test void threeByThreeHasNineBlocks() {
            List<int[]> fp = ChainFootprint.layerFootprint(3);
            assertEquals(9, fp.size());
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    assertTrue(containsOffset(fp, a, b),
                            "Missing offset [" + a + "," + b + "]");
                }
            }
        }

        @Test void fourStacksStillThreeByThreePerLayer() {
            assertEquals(9, ChainFootprint.layerFootprint(4).size());
        }
    }

    @Nested
    class FlatFootprint {
        @Test void oneToThreeSameAsLayer() {
            for (int s = 1; s <= 3; s++) {
                assertEquals(
                        ChainFootprint.layerFootprint(s).size(),
                        ChainFootprint.flatFootprint(s).size(),
                        "flat footprint size mismatch at stacks=" + s);
            }
        }

        @Test void fourStacksDoesNotExceedBudget() {
            List<int[]> fp = ChainFootprint.flatFootprint(4);
            assertTrue(fp.size() <= 18, "flat 4 exceeds budget: " + fp.size());
            assertAllUnique(fp);
        }

        @Test void fiveStacksDoesNotExceedBudget() {
            List<int[]> fp = ChainFootprint.flatFootprint(5);
            assertTrue(fp.size() <= 27, "flat 5 exceeds budget: " + fp.size());
            assertAllUnique(fp);
        }

        @Test void includesOrigin() {
            for (int s = 4; s <= 8; s++) {
                assertTrue(containsOffset(ChainFootprint.flatFootprint(s), 0, 0),
                        "flat footprint missing origin at stacks=" + s);
            }
        }

        @Test void quarterSymmetric() {
            for (int s = 4; s <= 10; s++) {
                List<int[]> fp = ChainFootprint.flatFootprint(s);
                for (int[] pos : fp) {
                    if (pos[0] == 0 && pos[1] == 0) { continue; }
                    assertTrue(
                            containsOffset(fp, -pos[0], pos[1])
                            && containsOffset(fp, pos[0], -pos[1])
                            && containsOffset(fp, -pos[0], -pos[1]),
                            "Asymmetric at stacks=" + s
                            + " pos=[" + pos[0] + "," + pos[1] + "]");
                }
            }
        }
    }

    @Nested
    class EuclideanCircle {
        @Test void smallBudgetReturnsOrigin() {
            List<int[]> circle = ChainFootprint.euclideanCircle(1);
            assertEquals(1, circle.size());
            assertArrayEquals(new int[]{0, 0}, circle.get(0));
        }

        @Test void budgetFiveReturnsFullFirstTier() {
            List<int[]> circle = ChainFootprint.euclideanCircle(5);
            assertEquals(5, circle.size());
            assertTrue(containsOffset(circle, 0, 0));
            assertTrue(containsOffset(circle, 1, 0));
            assertTrue(containsOffset(circle, -1, 0));
            assertTrue(containsOffset(circle, 0, 1));
            assertTrue(containsOffset(circle, 0, -1));
        }

        @Test void allPositionsUnique() {
            for (int budget : new int[]{9, 18, 27, 36, 50}) {
                assertAllUnique(ChainFootprint.euclideanCircle(budget));
            }
        }

        @Test void neverExceedsBudget() {
            for (int budget = 1; budget <= 50; budget++) {
                assertTrue(ChainFootprint.euclideanCircle(budget).size() <= budget,
                        "exceeded budget " + budget);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static boolean containsOffset(List<int[]> list, int a, int b) {
        return list.stream().anyMatch(p -> p[0] == a && p[1] == b);
    }

    private static void assertAllUnique(List<int[]> list) {
        Set<String> seen = new HashSet<>();
        for (int[] p : list) {
            assertTrue(seen.add(p[0] + "," + p[1]),
                    "Duplicate offset [" + p[0] + "," + p[1] + "]");
        }
    }
}
