package com.mercuriusxeno.goo.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Directed graph utilities for the goo transport network.
 *
 * <p>Builds adjacency lists from gasket pairings and provides cycle detection
 * and flow-path traversal. All methods are pure functions, testable without
 * Minecraft.</p>
 */
public final class GooNetwork {

    private GooNetwork() {}

    /**
     * Builds a directed adjacency list from gasket pairings.
     * Each entry maps an output gasket UUID to the list of input gasket UUIDs
     * it feeds into. In practice each output has at most one target, but the
     * list representation supports future fan-out.
     *
     * @param pairings output gasket -> input gasket map
     * @return adjacency list (node -> list of successors)
     */
    public static Map<UUID, List<UUID>> buildGraph(Map<UUID, UUID> pairings) {
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (var entry : pairings.entrySet()) {
            graph.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .add(entry.getValue());
            graph.putIfAbsent(entry.getValue(), new ArrayList<>());
        }
        return graph;
    }

    /**
     * Finds all gasket UUIDs that participate in a cycle.
     * Uses iterative DFS with three-color marking (white/gray/black).
     *
     * @param graph adjacency list from {@link #buildGraph}
     * @return set of UUIDs in cycles, empty if acyclic
     */
    public static Set<UUID> findCycles(Map<UUID, List<UUID>> graph) {
        Set<UUID> white = new HashSet<>(graph.keySet());
        Set<UUID> gray = new HashSet<>();
        Set<UUID> black = new HashSet<>();
        Set<UUID> inCycle = new HashSet<>();

        for (UUID node : graph.keySet()) {
            if (!white.contains(node)) continue;
            dfsMarkCycles(node, graph, white, gray, black, inCycle);
        }
        return inCycle;
    }

    /** DFS stack frame: tracks current node and which child index to explore next. */
    private record DfsFrame(UUID node, int childIndex) {}

    /**
     * Iterative DFS that marks nodes in cycles.
     * Gray nodes form the current path; if we revisit a gray node, everything
     * on the path from that node onward is in a cycle.
     */
    private static void dfsMarkCycles(
            UUID start, Map<UUID, List<UUID>> graph,
            Set<UUID> white, Set<UUID> gray, Set<UUID> black,
            Set<UUID> inCycle) {
        List<DfsFrame> stack = new ArrayList<>();

        white.remove(start);
        gray.add(start);
        stack.add(new DfsFrame(start, 0));

        while (!stack.isEmpty()) {
            DfsFrame current = stack.getLast();
            List<UUID> neighbors = graph.getOrDefault(current.node(), Collections.emptyList());

            if (current.childIndex() < neighbors.size()) {
                UUID child = neighbors.get(current.childIndex());
                stack.set(stack.size() - 1, new DfsFrame(current.node(), current.childIndex() + 1));

                if (gray.contains(child)) {
                    markCycleFromStack(stack, child, inCycle);
                } else if (white.contains(child)) {
                    white.remove(child);
                    gray.add(child);
                    stack.add(new DfsFrame(child, 0));
                }
            } else {
                gray.remove(current.node());
                black.add(current.node());
                stack.removeLast();
            }
        }
    }

    /** Marks all nodes on the stack from the cycle entry point onward. */
    private static void markCycleFromStack(
            List<DfsFrame> stack, UUID cycleEntry, Set<UUID> inCycle) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            UUID node = stack.get(i).node();
            inCycle.add(node);
            if (node.equals(cycleEntry)) break;
        }
    }

    /**
     * Follows the directed chain from a source gasket, collecting all reachable
     * gasket UUIDs in traversal order. Stops at the first revisited node to
     * avoid infinite loops in cyclic graphs.
     *
     * @param sourceGasket the starting output gasket UUID
     * @param pairings output -> input pairing map
     * @return ordered list of reachable gasket UUIDs (excluding source)
     */
    public static List<UUID> getFlowPath(UUID sourceGasket, Map<UUID, UUID> pairings) {
        List<UUID> path = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(sourceGasket);

        UUID current = pairings.get(sourceGasket);
        while (current != null && visited.add(current)) {
            path.add(current);
            current = pairings.get(current);
        }
        return path;
    }
}
