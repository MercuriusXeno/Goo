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
final class DirectedGraphUtils {

    /** Utility class, not instantiable. */
    private DirectedGraphUtils() {}

    /**
     * Finds all strongly connected components with size &gt; 1 using Tarjan's algorithm.
     *
     * @param <K> the node key type
     * @param graph adjacency map: node -&gt; set of successors
     * @return list of SCCs, each containing at least 2 nodes; singletons are omitted
     */
    static <K> List<Set<K>> findStronglyConnectedComponents(Map<K, Set<K>> graph) {
        TarjanState<K> state = new TarjanState<>();
        for (K node : graph.keySet()) {
            if (!state.index.containsKey(node)) {
                tarjanDfs(node, graph, state);
            }
        }
        return state.result;
    }

    /** Mutable state bundle for Tarjan's SCC algorithm. */
    private static final class TarjanState<K> {
        final List<Set<K>> result = new ArrayList<>();
        final Map<K, Integer> index = new HashMap<>();
        final Map<K, Integer> lowlink = new HashMap<>();
        final Set<K> onStack = new HashSet<>();
        final List<K> stack = new ArrayList<>();
        int counter;
    }

    /**
     * Finds all nodes reachable from any seed node via BFS on the reverse of {@code deps}.
     *
     * @param <K> the node key type
     * @param deps  forward dependency graph: node -&gt; set of inputs it depends on
     * @param seeds starting nodes (e.g. items with known base values)
     * @return all nodes reachable from seeds in the reverse graph, including seeds themselves
     */
    static <K> Set<K> findAnchoredNodes(Map<K, Set<K>> deps, Set<K> seeds) {
        Map<K, Set<K>> reverse = buildReverseGraph(deps);
        return bfsFrom(reverse, seeds);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Performs one DFS step of Tarjan's algorithm from the given node.
     *
     * @param <K> the node key type
     * @param node the starting node for this DFS step
     * @param graph adjacency map of the directed graph
     * @param s mutable Tarjan state
     */
    private static <K> void tarjanDfs(K node, Map<K, Set<K>> graph, TarjanState<K> s) {
        s.index.put(node, s.counter);
        s.lowlink.put(node, s.counter);
        s.counter++;
        s.stack.add(node);
        s.onStack.add(node);

        visitNeighbors(node, graph, s);
        collectSccIfRoot(node, s);
    }

    /**
     * Visits all neighbors of a node, recursing on unvisited ones and updating lowlinks.
     *
     * @param <K> the node key type
     * @param node the node whose neighbors to visit
     * @param graph adjacency map of the directed graph
     * @param s mutable Tarjan state
     */
    private static <K> void visitNeighbors(K node, Map<K, Set<K>> graph, TarjanState<K> s) {
        for (K neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            updateNeighborLowlink(node, neighbor, graph, s);
        }
    }

    /**
     * Recurses into an unvisited neighbor or updates lowlink for an on-stack one.
     * @param <K>      the node key type
     * @param node     the current node being explored
     * @param neighbor the adjacent node to recurse into or update from
     * @param graph    the directed adjacency map
     * @param s        mutable Tarjan state
     */
    private static <K> void updateNeighborLowlink(K node, K neighbor,
            Map<K, Set<K>> graph, TarjanState<K> s) {
        if (s.index.containsKey(neighbor)) {
            if (s.onStack.contains(neighbor)) {
                s.lowlink.put(node, Math.min(s.lowlink.get(node), s.index.get(neighbor)));
            }
        } else {
            tarjanDfs(neighbor, graph, s);
            s.lowlink.put(node, Math.min(s.lowlink.get(node), s.lowlink.get(neighbor)));
        }
    }

    /**
     * Pops the stack into an SCC if this node is the root of its component.
     *
     * @param <K> the node key type
     * @param node the candidate SCC root
     * @param s mutable Tarjan state
     */
    private static <K> void collectSccIfRoot(K node, TarjanState<K> s) {
        if (!s.lowlink.get(node).equals(s.index.get(node))) { return; }
        Set<K> scc = popScc(node, s);
        if (scc.size() > 1) {
            s.result.add(scc);
        }
    }

    /**
     * Pops nodes from the Tarjan stack until the root node is reached.
     * @param <K>  the node key type
     * @param root the SCC root node to pop down to
     * @param s    mutable Tarjan state
     * @return the set of nodes forming this strongly connected component
     */
    private static <K> Set<K> popScc(K root, TarjanState<K> s) {
        Set<K> scc = new HashSet<>();
        K popped;
        do {
            popped = s.stack.remove(s.stack.size() - 1);
            s.onStack.remove(popped);
            scc.add(popped);
        } while (!popped.equals(root));
        return scc;
    }

    /**
     * Builds the reverse (transposed) adjacency map from a forward dependency graph.
     *
     * @param <K> the node key type
     * @param deps forward dependency graph to transpose
     * @return the reversed adjacency map
     */
    private static <K> Map<K, Set<K>> buildReverseGraph(Map<K, Set<K>> deps) {
        Map<K, Set<K>> reverse = new HashMap<>();
        for (var entry : deps.entrySet()) {
            for (K input : entry.getValue()) {
                reverse.computeIfAbsent(input, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        return reverse;
    }

    /**
     * BFS from seed nodes, returning all reachable nodes including the seeds.
     *
     * @param <K> the node key type
     * @param graph adjacency map to traverse
     * @param seeds starting nodes for the BFS
     * @return all reachable nodes including seeds
     */
    private static <K> Set<K> bfsFrom(Map<K, Set<K>> graph, Set<K> seeds) {
        Set<K> visited = new HashSet<>(seeds);
        List<K> queue = new ArrayList<>(seeds);
        while (!queue.isEmpty()) {
            K current = queue.remove(queue.size() - 1);
            enqueueUnvisited(graph, current, visited, queue);
        }
        return visited;
    }

    /**
     * Adds all unvisited neighbors of {@code current} to the BFS queue.
     * @param <K>     the node key type
     * @param graph   the directed adjacency map
     * @param current the node whose neighbors to enqueue
     * @param visited the set of already-visited nodes (updated in place)
     * @param queue   the BFS queue to append to
     */
    private static <K> void enqueueUnvisited(Map<K, Set<K>> graph, K current,
            Set<K> visited, List<K> queue) {
        for (K neighbor : graph.getOrDefault(current, Collections.emptySet())) {
            if (visited.add(neighbor)) {
                queue.add(neighbor);
            }
        }
    }
}
