package com.mercuriusxeno.goo.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure graph algorithms for directed graphs. All methods are stateless and
 * generic over the node key type {@code <K>}.
 *
 * <p>Extracted from GooValueRegistry so the algorithms can be tested directly
 * and reused by other directed-graph consumers (e.g. GasketRegistry).
 */
class DirectedGraphUtils {

    private DirectedGraphUtils() {}

    /**
     * Finds all strongly connected components with size &gt; 1 using Tarjan's algorithm.
     *
     * @param graph adjacency map: node -&gt; set of successors
     * @return list of SCCs, each containing at least 2 nodes; singletons are omitted
     */
    static <K> List<Set<K>> findStronglyConnectedComponents(Map<K, Set<K>> graph) {
        List<Set<K>> result = new ArrayList<>();
        Map<K, Integer> index = new HashMap<>();
        Map<K, Integer> lowlink = new HashMap<>();
        Set<K> onStack = new HashSet<>();
        List<K> stack = new ArrayList<>();
        int[] counter = {0};

        for (K node : graph.keySet()) {
            if (!index.containsKey(node)) {
                tarjanDfs(node, graph, index, lowlink, onStack, stack, counter, result);
            }
        }
        return result;
    }

    /**
     * Finds all nodes reachable from any seed node via BFS on the reverse of {@code deps}.
     *
     * @param deps  forward dependency graph: node -&gt; set of inputs it depends on
     * @param seeds starting nodes (e.g. items with known base values)
     * @return all nodes reachable from seeds in the reverse graph, including seeds themselves
     */
    static <K> Set<K> findAnchoredNodes(Map<K, Set<K>> deps, Set<K> seeds) {
        Map<K, Set<K>> reverse = buildReverseGraph(deps);
        return bfsFrom(reverse, seeds);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static <K> void tarjanDfs(K node, Map<K, Set<K>> graph,
            Map<K, Integer> index, Map<K, Integer> lowlink,
            Set<K> onStack, List<K> stack, int[] counter,
            List<Set<K>> result) {
        index.put(node, counter[0]);
        lowlink.put(node, counter[0]);
        counter[0]++;
        stack.add(node);
        onStack.add(node);

        visitNeighbors(node, graph, index, lowlink, onStack, stack, counter, result);
        collectSccIfRoot(node, index, lowlink, onStack, stack, result);
    }

    private static <K> void visitNeighbors(K node, Map<K, Set<K>> graph,
            Map<K, Integer> index, Map<K, Integer> lowlink,
            Set<K> onStack, List<K> stack, int[] counter,
            List<Set<K>> result) {
        for (K neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            if (!index.containsKey(neighbor)) {
                tarjanDfs(neighbor, graph, index, lowlink, onStack, stack, counter, result);
                lowlink.put(node, Math.min(lowlink.get(node), lowlink.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowlink.put(node, Math.min(lowlink.get(node), index.get(neighbor)));
            }
        }
    }

    private static <K> void collectSccIfRoot(K node, Map<K, Integer> index,
            Map<K, Integer> lowlink, Set<K> onStack,
            List<K> stack, List<Set<K>> result) {
        if (!lowlink.get(node).equals(index.get(node))) return;

        Set<K> scc = new HashSet<>();
        K popped;
        do {
            popped = stack.remove(stack.size() - 1);
            onStack.remove(popped);
            scc.add(popped);
        } while (!popped.equals(node));

        if (scc.size() > 1) {
            result.add(scc);
        }
    }

    private static <K> Map<K, Set<K>> buildReverseGraph(Map<K, Set<K>> deps) {
        Map<K, Set<K>> reverse = new HashMap<>();
        for (var entry : deps.entrySet()) {
            for (K input : entry.getValue()) {
                reverse.computeIfAbsent(input, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        return reverse;
    }

    private static <K> Set<K> bfsFrom(Map<K, Set<K>> graph, Set<K> seeds) {
        Set<K> visited = new HashSet<>(seeds);
        List<K> queue = new ArrayList<>(seeds);
        while (!queue.isEmpty()) {
            K current = queue.remove(queue.size() - 1);
            for (K neighbor : graph.getOrDefault(current, Collections.emptySet())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }
}
