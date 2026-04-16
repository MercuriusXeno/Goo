package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph-building, value propagation, and downstream traversal for the
 * scaffold root-finding pipeline. Clustering and cycle-entry selection
 * live in {@link ScaffoldClustering}.
 * All methods are stateless; the class is not instantiable.
 */
final class ScaffoldGraph {

    /** Maximum depth for example derivation chains. */
    static final int MAX_CHAIN_DEPTH = 5;

    private ScaffoldGraph() {}


    /**
     * Builds the forward/reverse dependency graphs and collects all recipe items.
     *
     * @param recipes all known recipes
     * @return graph data bundle for root-finding phases
     */
    static ScaffoldGenerator.GraphData buildGraphData(List<RecipeInput> recipes) {
        var byOutput = GooValueDerivation.groupByOutput(recipes);
        var forwardDeps = buildForwardDeps(byOutput);
        var recipeItems = collectRecipeItems(forwardDeps);
        var reverseDeps = buildReverseDeps(forwardDeps);
        return new ScaffoldGenerator.GraphData(byOutput, recipeItems, reverseDeps);
    }

    /**
     * Collects all item IDs mentioned in the recipe graph (inputs and outputs).
     *
     * @param forwardDeps forward dependency graph
     * @return mutable set of all recipe-mentioned item IDs
     */
    private static Set<Identifier> collectRecipeItems(Map<Identifier, Set<Identifier>> forwardDeps) {
        Set<Identifier> items = new HashSet<>(forwardDeps.keySet());
        forwardDeps.values().forEach(items::addAll);
        return items;
    }

    /**
     * Builds output -> all inputs dependency graph from recipes.
     *
     * @param byOutput recipes grouped by output item ID
     * @return forward dependency graph as output to input-set map
     */
    private static Map<Identifier, Set<Identifier>> buildForwardDeps(
            Map<Identifier, List<RecipeInput>> byOutput) {
        Map<Identifier, Set<Identifier>> deps = new HashMap<>();
        for (var entry : byOutput.entrySet()) {
            addForwardDepsForOutput(deps, entry.getKey(), entry.getValue());
        }
        return deps;
    }

    /**
     * Collects all unique inputs for a single output and adds them to the forward deps map.
     *
     * @param deps forward dependency map to populate (mutated)
     * @param output the output item ID
     * @param recipes all recipes producing this output
     */
    private static void addForwardDepsForOutput(Map<Identifier, Set<Identifier>> deps,
                                                 Identifier output, List<RecipeInput> recipes) {
        Set<Identifier> inputs = collectRecipeInputItems(recipes);
        inputs.remove(output);
        if (!inputs.isEmpty()) {
            deps.put(output, inputs);
        }
    }

    /**
     * Collects all input item IDs from a list of recipes producing the same output.
     *
     * @param recipes the recipes to collect inputs from
     * @return mutable set of all input item IDs
     */
    private static Set<Identifier> collectRecipeInputItems(List<RecipeInput> recipes) {
        Set<Identifier> inputs = new HashSet<>();
        for (RecipeInput recipe : recipes) {
            for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
                inputs.addAll(alts);
            }
        }
        return inputs;
    }

    /**
     * Builds input -> outputs reverse dependency graph.
     *
     * @param forwardDeps forward dependency graph to transpose
     * @return reverse dependency graph as input to output-set map
     */
    private static Map<Identifier, Set<Identifier>> buildReverseDeps(
            Map<Identifier, Set<Identifier>> forwardDeps) {
        Map<Identifier, Set<Identifier>> reverse = new HashMap<>();
        for (var entry : forwardDeps.entrySet()) {
            for (Identifier input : entry.getValue()) {
                reverse.computeIfAbsent(input, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        return reverse;
    }


    /**
     * Simulates value propagation through recipes (forward only).
     * Forward: all inputs valued -> output valued.
     * No reverse propagation -- matches the actual derivation system which
     * does not reverse-derive (reverted due to stonecutter value collapse).
     *
     * @param valued mutable set of valued item IDs (grows during propagation)
     * @param recipes all recipes to propagate through
     * @param denied denied items excluded from propagation
     */
    static void propagateValues(Set<Identifier> valued,
                                List<RecipeInput> recipes,
                                Set<Identifier> denied) {
        boolean changed = true;
        while (changed) {
            changed = propagateOnePass(valued, recipes, denied);
        }
    }

    /**
     * Runs one pass of value propagation, returning true if any new item was valued.
     *
     * @param valued mutable valued set (may grow)
     * @param recipes all recipes to check
     * @param denied excluded items
     * @return true if at least one new item was valued this pass
     */
    private static boolean propagateOnePass(Set<Identifier> valued, List<RecipeInput> recipes,
                                             Set<Identifier> denied) {
        boolean changed = false;
        for (RecipeInput recipe : recipes) {
            if (tryPropagateRecipe(valued, recipe, denied)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Attempts to propagate a single recipe: if output is not denied and all
     * inputs are valued, marks the output as valued.
     *
     * @param valued mutable valued set (may be mutated)
     * @param recipe the recipe to check
     * @param denied excluded items
     * @return true if a new item was valued
     */
    private static boolean tryPropagateRecipe(Set<Identifier> valued, RecipeInput recipe, Set<Identifier> denied) {
        Identifier output = recipe.output();
        if (denied.contains(output)) { return false; }

        boolean allInputsValued = recipe.ingredientAlternatives().stream()
                .allMatch(alts -> alts.stream().anyMatch(valued::contains));
        return allInputsValued && valued.add(output);
    }


    /**
     * Computes all downstream items reachable from a root via the reverse graph.
     *
     * @param root the starting item
     * @param reverseDeps reverse dependency graph (input to outputs)
     * @param valued items already valued (stops traversal)
     * @param denied items excluded from results
     * @return set of all unvalued downstream item IDs
     */
    static Set<Identifier> computeDownstream(Identifier root,
                                              Map<Identifier, Set<Identifier>> reverseDeps,
                                              Set<Identifier> valued,
                                              Set<Identifier> denied) {
        Set<Identifier> downstream = new HashSet<>();
        List<Identifier> queue = new ArrayList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Identifier current = queue.remove(queue.size() - 1);
            expandDownstreamNeighbors(current, reverseDeps, valued, denied, downstream, queue);
        }
        return downstream;
    }

    /**
     * Expands a single node's neighbors during downstream BFS, adding unvisited
     * unvalued non-denied neighbors to both the result set and the queue.
     *
     * @param current the node to expand
     * @param reverseDeps reverse dependency graph
     * @param valued items already valued (excluded)
     * @param denied items excluded
     * @param downstream result set (mutated, also used as visited check)
     * @param queue BFS queue (mutated)
     */
    private static void expandDownstreamNeighbors(Identifier current,
                                                   Map<Identifier, Set<Identifier>> reverseDeps,
                                                   Set<Identifier> valued, Set<Identifier> denied,
                                                   Set<Identifier> downstream, List<Identifier> queue) {
        for (Identifier next : reverseDeps.getOrDefault(current, Set.of())) {
            if (!valued.contains(next) && !denied.contains(next) && downstream.add(next)) {
                queue.add(next);
            }
        }
    }

    /**
     * Traces an example derivation chain from a root, following the highest-impact path.
     *
     * @param root the starting item
     * @param reverseDeps reverse dependency graph (input to outputs)
     * @param valued items already valued (excluded from chain)
     * @param denied items excluded from chain
     * @param maxDepth maximum chain length
     * @return ordered list of items in the example chain
     */
    static List<Identifier> traceExampleChain(Identifier root,
                                               Map<Identifier, Set<Identifier>> reverseDeps,
                                               Set<Identifier> valued,
                                               Set<Identifier> denied,
                                               int maxDepth) {
        List<Identifier> chain = new ArrayList<>();
        traceChainSteps(chain, root, reverseDeps, valued, denied, maxDepth);
        return chain;
    }

    /**
     * Follows highest-impact neighbors up to maxDepth steps, appending each to the chain.
     *
     * @param chain accumulator for chain items (mutated)
     * @param start the starting item
     * @param reverseDeps reverse dependency graph
     * @param valued items already valued (excluded)
     * @param denied items excluded
     * @param maxDepth maximum steps to follow
     */
    private static void traceChainSteps(List<Identifier> chain, Identifier start,
                                         Map<Identifier, Set<Identifier>> reverseDeps,
                                         Set<Identifier> valued, Set<Identifier> denied, int maxDepth) {
        Identifier current = start;
        for (int i = 0; i < maxDepth; i++) {
            Identifier best = findBestCandidate(current, reverseDeps, valued, denied);
            if (best == null) { break; }
            chain.add(best);
            current = best;
        }
    }

    /**
     * Finds the best next candidate in a chain trace: the neighbor with the
     * most downstream reach that is neither valued nor denied.
     *
     * @param current the current node in the chain
     * @param reverseDeps reverse dependency graph
     * @param valued items already valued (excluded)
     * @param denied items excluded
     * @return the best candidate, or null if none qualify
     */
    private static Identifier findBestCandidate(Identifier current,
                                                 Map<Identifier, Set<Identifier>> reverseDeps,
                                                 Set<Identifier> valued, Set<Identifier> denied) {
        Set<Identifier> next = reverseDeps.getOrDefault(current, Set.of());
        if (next.isEmpty()) { return null; }
        return scoreBestNeighbor(next, reverseDeps, valued, denied);
    }

    /**
     * Scores neighbors by their downstream fan-out and returns the one
     * with the highest reach, skipping valued and denied items.
     *
     * @param candidates neighbor candidates to score
     * @param reverseDeps reverse dependency graph for fan-out scoring
     * @param valued items already valued (skipped)
     * @param denied items excluded (skipped)
     * @return the highest-scoring candidate, or null if all are excluded
     */
    private static Identifier scoreBestNeighbor(Set<Identifier> candidates,
                                                 Map<Identifier, Set<Identifier>> reverseDeps,
                                                 Set<Identifier> valued, Set<Identifier> denied) {
        return candidates.stream()
                .filter(c -> !valued.contains(c) && !denied.contains(c))
                .max(Comparator.comparingInt(c -> reverseDeps.getOrDefault(c, Set.of()).size()))
                .orElse(null);
    }

}
