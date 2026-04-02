package com.mercuriusxeno.goo.data;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GooNetwork graph builder, cycle detection, and flow path traversal.
 * Pure functions, no Minecraft dependencies.
 */
class GooNetworkTest {

    // --- buildGraph ---

    @Test
    void buildGraph_emptyPairings_returnsEmptyGraph() {
        Map<UUID, List<UUID>> graph = GooNetwork.buildGraph(Map.of());
        assertTrue(graph.isEmpty());
    }

    @Test
    void buildGraph_singlePairing_createsBothNodes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, List<UUID>> graph = GooNetwork.buildGraph(Map.of(a, b));

        assertTrue(graph.containsKey(a));
        assertTrue(graph.containsKey(b));
        assertEquals(List.of(b), graph.get(a));
        assertTrue(graph.get(b).isEmpty());
    }

    @Test
    void buildGraph_chain_createsLinearGraph() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);

        Map<UUID, List<UUID>> graph = GooNetwork.buildGraph(pairings);

        assertEquals(3, graph.size());
        assertEquals(List.of(b), graph.get(a));
        assertEquals(List.of(c), graph.get(b));
        assertTrue(graph.get(c).isEmpty());
    }

    // --- findCycles ---

    @Test
    void findCycles_linearChain_returnsEmpty() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertTrue(cycles.isEmpty());
    }

    @Test
    void findCycles_selfLoop_detectsSingleNode() {
        UUID a = UUID.randomUUID();
        Map<UUID, UUID> pairings = Map.of(a, a);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertEquals(Set.of(a), cycles);
    }

    @Test
    void findCycles_twoNodeCycle_detectsBothNodes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, a);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertEquals(Set.of(a, b), cycles);
    }

    @Test
    void findCycles_threeNodeCycle_detectsAllThree() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);
        pairings.put(c, a);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertEquals(Set.of(a, b, c), cycles);
    }

    @Test
    void findCycles_tailIntoCycle_detectsOnlyCycleNodes() {
        UUID tail = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(tail, a);
        pairings.put(a, b);
        pairings.put(b, a);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertEquals(Set.of(a, b), cycles);
        assertFalse(cycles.contains(tail));
    }

    @Test
    void findCycles_disconnectedCycleAndChain_detectsOnlyCycle() {
        UUID chain1 = UUID.randomUUID();
        UUID chain2 = UUID.randomUUID();
        UUID cycleA = UUID.randomUUID();
        UUID cycleB = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(chain1, chain2);
        pairings.put(cycleA, cycleB);
        pairings.put(cycleB, cycleA);

        Set<UUID> cycles = GooNetwork.findCycles(GooNetwork.buildGraph(pairings));
        assertEquals(Set.of(cycleA, cycleB), cycles);
    }

    // --- getFlowPath ---

    @Test
    void getFlowPath_noConnections_returnsEmpty() {
        UUID source = UUID.randomUUID();
        List<UUID> path = GooNetwork.getFlowPath(source, Map.of());
        assertTrue(path.isEmpty());
    }

    @Test
    void getFlowPath_linearChain_returnsOrderedPath() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);

        List<UUID> path = GooNetwork.getFlowPath(a, pairings);
        assertEquals(List.of(b, c), path);
    }

    @Test
    void getFlowPath_withCycle_stopsAtRevisitedNode() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);
        pairings.put(c, a);

        List<UUID> path = GooNetwork.getFlowPath(a, pairings);
        assertEquals(List.of(b, c), path);
    }

    @Test
    void getFlowPath_selfLoop_returnsEmpty() {
        UUID a = UUID.randomUUID();
        List<UUID> path = GooNetwork.getFlowPath(a, Map.of(a, a));
        assertTrue(path.isEmpty());
    }

    @Test
    void getFlowPath_fromMiddleOfChain_returnsRemainingPath() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, UUID> pairings = new HashMap<>();
        pairings.put(a, b);
        pairings.put(b, c);

        List<UUID> path = GooNetwork.getFlowPath(b, pairings);
        assertEquals(List.of(c), path);
    }
}
