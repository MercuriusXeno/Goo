package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.ChainFootprint;
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

    private static boolean containsOffset3d(List<int[]> list, int x, int y, int z) {
        return list.stream().anyMatch(p -> p[0] == x && p[1] == y && p[2] == z);
    }

    private static Set<String> toSet3d(List<int[]> list) {
        Set<String> set = new HashSet<>();
        for (int[] p : list) {
            set.add(p[0] + "," + p[1] + "," + p[2]);
        }
        return set;
    }

    private static boolean disjoint(Set<String> a, Set<String> b) {
        for (String s : a) {
            if (b.contains(s)) {
                return false;
            }
        }
        return true;
    }

    @Nested
    class TotalBlocks {
        @Test
        void oneStack() {
            assertEquals(1, ChainFootprint.totalBlocks(1));
        }

        @Test
        void twoStacks() {
            assertEquals(5, ChainFootprint.totalBlocks(2));
        }

        @Test
        void threeStacks() {
            assertEquals(9, ChainFootprint.totalBlocks(3));
        }

        @Test
        void fourStacks() {
            assertEquals(18, ChainFootprint.totalBlocks(4));
        }

        @Test
        void fiveStacks() {
            assertEquals(27, ChainFootprint.totalBlocks(5));
        }

        @Test
        void sixStacks() {
            assertEquals(36, ChainFootprint.totalBlocks(6));
        }

        @Test
        void twentySevenStacks() {
            assertEquals(225, ChainFootprint.totalBlocks(27));
        }
    }

    @Nested
    class TunnelDepth {
        @Test
        void oneStack() {
            assertEquals(1, ChainFootprint.tunnelDepth(1));
        }

        @Test
        void twoStacks() {
            assertEquals(1, ChainFootprint.tunnelDepth(2));
        }

        @Test
        void threeStacks() {
            assertEquals(1, ChainFootprint.tunnelDepth(3));
        }

        @Test
        void fourStacks() {
            assertEquals(2, ChainFootprint.tunnelDepth(4));
        }

        @Test
        void fiveStacks() {
            assertEquals(3, ChainFootprint.tunnelDepth(5));
        }

        @Test
        void twentySevenStacks() {
            assertEquals(25, ChainFootprint.tunnelDepth(27));
        }

        @Test
        void capsAtMaxDepth() {
            assertEquals(25, ChainFootprint.tunnelDepth(30));
        }
    }

    // ── Helpers ──

    @Nested
    class LayerFootprint {
        @Test
        void singleBlock() {
            List<int[]> fp = ChainFootprint.layerFootprint(1);
            assertEquals(1, fp.size());
            assertArrayEquals(new int[]{0, 0}, fp.get(0));
        }

        @Test
        void crossHasFiveBlocks() {
            List<int[]> fp = ChainFootprint.layerFootprint(2);
            assertEquals(5, fp.size());
            assertTrue(containsOffset(fp, 0, 0));
            assertTrue(containsOffset(fp, 1, 0));
            assertTrue(containsOffset(fp, -1, 0));
            assertTrue(containsOffset(fp, 0, 1));
            assertTrue(containsOffset(fp, 0, -1));
        }

        @Test
        void threeByThreeHasNineBlocks() {
            List<int[]> fp = ChainFootprint.layerFootprint(3);
            assertEquals(9, fp.size());
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    assertTrue(containsOffset(fp, a, b),
                            "Missing offset [" + a + "," + b + "]");
                }
            }
        }

        @Test
        void fourStacksStillThreeByThreePerLayer() {
            assertEquals(9, ChainFootprint.layerFootprint(4).size());
        }
    }

    @Nested
    class FlatFootprint {
        @Test
        void oneToThreeSameAsLayer() {
            for (int s = 1; s <= 3; s++) {
                assertEquals(
                        ChainFootprint.layerFootprint(s).size(),
                        ChainFootprint.flatFootprint(s).size(),
                        "flat footprint size mismatch at stacks=" + s);
            }
        }

        @Test
        void fourStacksDoesNotExceedBudget() {
            List<int[]> fp = ChainFootprint.flatFootprint(4);
            assertTrue(fp.size() <= 18, "flat 4 exceeds budget: " + fp.size());
            assertAllUnique(fp);
        }

        @Test
        void fiveStacksDoesNotExceedBudget() {
            List<int[]> fp = ChainFootprint.flatFootprint(5);
            assertTrue(fp.size() <= 27, "flat 5 exceeds budget: " + fp.size());
            assertAllUnique(fp);
        }

        @Test
        void includesOrigin() {
            for (int s = 4; s <= 8; s++) {
                assertTrue(containsOffset(ChainFootprint.flatFootprint(s), 0, 0),
                        "flat footprint missing origin at stacks=" + s);
            }
        }

        @Test
        void quarterSymmetric() {
            for (int s = 4; s <= 10; s++) {
                List<int[]> fp = ChainFootprint.flatFootprint(s);
                for (int[] pos : fp) {
                    if (pos[0] == 0 && pos[1] == 0) {
                        continue;
                    }
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
        @Test
        void smallBudgetReturnsOrigin() {
            List<int[]> circle = ChainFootprint.euclideanCircle(1);
            assertEquals(1, circle.size());
            assertArrayEquals(new int[]{0, 0}, circle.get(0));
        }

        @Test
        void budgetFiveReturnsFullFirstTier() {
            List<int[]> circle = ChainFootprint.euclideanCircle(5);
            assertEquals(5, circle.size());
            assertTrue(containsOffset(circle, 0, 0));
            assertTrue(containsOffset(circle, 1, 0));
            assertTrue(containsOffset(circle, -1, 0));
            assertTrue(containsOffset(circle, 0, 1));
            assertTrue(containsOffset(circle, 0, -1));
        }

        @Test
        void allPositionsUnique() {
            for (int budget : new int[]{9, 18, 27, 36, 50}) {
                assertAllUnique(ChainFootprint.euclideanCircle(budget));
            }
        }

        @Test
        void neverExceedsBudget() {
            for (int budget = 1; budget <= 50; budget++) {
                assertTrue(ChainFootprint.euclideanCircle(budget).size() <= budget,
                        "exceeded budget " + budget);
            }
        }
    }

    @Nested
    class FlatRings {
        @Test
        void ringsUnionEqualsFlatFootprint() {
            for (int s = 4; s <= 10; s++) {
                List<List<int[]>> rings = ChainFootprint.flatRings(s);
                Set<String> union = new HashSet<>();
                for (List<int[]> ring : rings) {
                    for (int[] p : ring) {
                        union.add(p[0] + "," + p[1]);
                    }
                }
                List<int[]> flat = ChainFootprint.flatFootprint(s);
                assertEquals(flat.size(), union.size(),
                        "ring union size != flat footprint at stacks=" + s);
            }
        }

        @Test
        void ringsDoNotOverlap() {
            for (int s = 4; s <= 8; s++) {
                List<List<int[]>> rings = ChainFootprint.flatRings(s);
                Set<String> seen = new HashSet<>();
                for (List<int[]> ring : rings) {
                    for (int[] p : ring) {
                        assertTrue(seen.add(p[0] + "," + p[1]),
                                "duplicate across rings at stacks=" + s);
                    }
                }
            }
        }

        @Test
        void firstRingIsOrigin() {
            List<List<int[]>> rings = ChainFootprint.flatRings(5);
            assertFalse(rings.isEmpty());
            assertTrue(containsOffset(rings.get(0), 0, 0));
        }

        @Test
        void ringsExpandOutward() {
            List<List<int[]>> rings = ChainFootprint.flatRings(6);
            for (int i = 1; i < rings.size(); i++) {
                int prevMaxDist = maxSqDist(rings.get(i - 1));
                int currMinDist = minSqDist(rings.get(i));
                assertTrue(currMinDist > prevMaxDist,
                        "ring " + i + " not strictly farther than ring " + (i - 1));
            }
        }

        private int maxSqDist(List<int[]> ring) {
            return ring.stream().mapToInt(p -> p[0] * p[0] + p[1] * p[1]).max().orElse(0);
        }

        private int minSqDist(List<int[]> ring) {
            return ring.stream().mapToInt(p -> p[0] * p[0] + p[1] * p[1]).min().orElse(0);
        }
    }

    @Nested
    class SphereShell {
        @Test
        void radius0IsOriginOnly() {
            List<int[]> shell = ChainFootprint.sphereShell(0);
            assertEquals(1, shell.size());
            assertTrue(containsOffset3d(shell, 0, 0, 0));
        }

        @Test
        void radius1HasSixCardinals() {
            List<int[]> shell = ChainFootprint.sphereShell(1);
            assertTrue(containsOffset3d(shell, 1, 0, 0));
            assertTrue(containsOffset3d(shell, -1, 0, 0));
            assertTrue(containsOffset3d(shell, 0, 1, 0));
            assertTrue(containsOffset3d(shell, 0, -1, 0));
            assertTrue(containsOffset3d(shell, 0, 0, 1));
            assertTrue(containsOffset3d(shell, 0, 0, -1));
            assertFalse(containsOffset3d(shell, 0, 0, 0), "origin should not be in shell 1");
        }

        @Test
        void shellsDoNotOverlap() {
            Set<String> r0 = toSet3d(ChainFootprint.sphereShell(0));
            Set<String> r1 = toSet3d(ChainFootprint.sphereShell(1));
            Set<String> r2 = toSet3d(ChainFootprint.sphereShell(2));
            assertTrue(disjoint(r0, r1), "shell 0 and 1 overlap");
            assertTrue(disjoint(r1, r2), "shell 1 and 2 overlap");
            assertTrue(disjoint(r0, r2), "shell 0 and 2 overlap");
        }

        @Test
        void shellsUnionEqualsSolid() {
            int radius = 3;
            Set<String> union = new HashSet<>();
            for (int r = 0; r <= radius; r++) {
                union.addAll(toSet3d(ChainFootprint.sphereShell(r)));
            }
            int r2 = radius * radius;
            int solidCount = 0;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z <= r2) {
                            solidCount++;
                        }
                    }
                }
            }
            assertEquals(solidCount, union.size(),
                    "union of shells 0.." + radius + " should equal solid sphere");
        }

        @Test
        void allUniqueWithinShell() {
            for (int r = 0; r <= 4; r++) {
                List<int[]> shell = ChainFootprint.sphereShell(r);
                Set<String> seen = new HashSet<>();
                for (int[] p : shell) {
                    assertTrue(seen.add(p[0] + "," + p[1] + "," + p[2]),
                            "duplicate in shell " + r);
                }
            }
        }
    }
}
