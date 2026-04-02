package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure graph algorithms in DirectedGraphUtils.
 */
class DirectedGraphUtilsTest {

    // ── Tarjan SCC ──────────────────────────────────────────────────────

    @Nested
    class TarjanScc {

        /** DAG with no cycles produces no SCCs. */
        @Test
        void dagProducesNoSccs() {
            Map<Identifier, Set<Identifier>> deps = Map.of(
                id("a"), Set.of(id("b")),
                id("b"), Set.of(id("c"))
            );
            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
            assertTrue(sccs.isEmpty());
        }

        /** Simple cycle A->B->A produces one SCC of size 2. */
        @Test
        void simpleCycleProducesOneScc() {
            Map<Identifier, Set<Identifier>> deps = new HashMap<>();
            deps.put(id("a"), Set.of(id("b")));
            deps.put(id("b"), Set.of(id("a")));

            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
            assertEquals(1, sccs.size());
            assertEquals(Set.of(id("a"), id("b")), sccs.get(0));
        }

        /** Multiple independent cycles produce multiple SCCs. */
        @Test
        void multipleIndependentCycles() {
            Map<Identifier, Set<Identifier>> deps = new HashMap<>();
            deps.put(id("a"), Set.of(id("b")));
            deps.put(id("b"), Set.of(id("a")));
            deps.put(id("x"), Set.of(id("y")));
            deps.put(id("y"), Set.of(id("x")));

            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
            assertEquals(2, sccs.size());
        }

        /** Self-loop (size 1 SCC) is filtered out. */
        @Test
        void selfLoopFiltered() {
            Map<Identifier, Set<Identifier>> deps = Map.of(
                id("a"), Set.of(id("a"))
            );
            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
            assertTrue(sccs.isEmpty());
        }

        /** Three-node cycle A->B->C->A. */
        @Test
        void threeNodeCycle() {
            Map<Identifier, Set<Identifier>> deps = new HashMap<>();
            deps.put(id("a"), Set.of(id("b")));
            deps.put(id("b"), Set.of(id("c")));
            deps.put(id("c"), Set.of(id("a")));

            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
            assertEquals(1, sccs.size());
            assertEquals(Set.of(id("a"), id("b"), id("c")), sccs.get(0));
        }

        /** Empty graph produces no SCCs. */
        @Test
        void emptyGraphNoSccs() {
            List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(Map.of());
            assertTrue(sccs.isEmpty());
        }
    }

    // ── Anchor Reachability ─────────────────────────────────────────────

    @Nested
    class AnchorReachability {

        /** Seed node and node that depends on it are both anchored. */
        @Test
        void directSeedIsAnchored() {
            Map<Identifier, Set<Identifier>> deps = Map.of(
                id("b"), Set.of(id("a"))
            );

            Set<Identifier> anchored = DirectedGraphUtils.findAnchoredNodes(deps, Set.of(id("a")));
            assertTrue(anchored.contains(id("a")));
            assertTrue(anchored.contains(id("b")));
        }

        /** Node reachable from seed via chain is anchored. */
        @Test
        void transitiveReachabilityAnchors() {
            Map<Identifier, Set<Identifier>> deps = new HashMap<>();
            deps.put(id("mid"), Set.of(id("root")));
            deps.put(id("leaf"), Set.of(id("mid")));

            Set<Identifier> anchored = DirectedGraphUtils.findAnchoredNodes(deps, Set.of(id("root")));
            assertTrue(anchored.contains(id("root")));
            assertTrue(anchored.contains(id("mid")));
            assertTrue(anchored.contains(id("leaf")));
        }

        /** Isolated node with no path from seeds is not anchored. */
        @Test
        void isolatedNodeNotAnchored() {
            Map<Identifier, Set<Identifier>> deps = Map.of(
                id("island"), Set.of(id("other_island"))
            );

            Set<Identifier> anchored = DirectedGraphUtils.findAnchoredNodes(deps, Set.of(id("base")));
            assertTrue(anchored.contains(id("base")));
            assertFalse(anchored.contains(id("island")));
        }
    }
}
