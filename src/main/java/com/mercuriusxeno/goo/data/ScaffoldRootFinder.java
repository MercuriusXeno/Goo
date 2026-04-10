package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Three-phase root discovery for the scaffold pipeline: true roots (recipeless
 * inputs), cycle roots (unresolvable dependency loops), and unaccounted items
 * (registered but never reached by propagation).
 * All methods are stateless; the class is not instantiable.
 */
final class ScaffoldRootFinder {

    /** Reason: item has no recipe producing it. */
    private static final String REASON_NO_RECIPE = "no recipe";
    /** Reason prefix for cycle with member count. */
    private static final String REASON_CYCLE_PREFIX = "cycle (";
    /** Reason suffix for cycle with member count. */
    private static final String REASON_CYCLE_SUFFIX = " items)";
    /** Reason: simple cycle. */
    private static final String REASON_CYCLE = "cycle";
    /** Reason: broken chain (in recipe graph but unresolvable). */
    private static final String REASON_BROKEN_CHAIN = "broken chain";
    /** Reason: no recipe and not in any chain. */
    private static final String REASON_NO_CHAIN = "no recipe or chain";

    private ScaffoldRootFinder() {}

    /**
     * Initializes the valued set from base values and propagates through recipes.
     *
     * @param baseValues currently valued items
     * @param recipes all recipes for propagation
     * @param denied excluded items
     * @return mutable valued set after initial propagation
     */
    static Set<Identifier> initValuedSet(Map<Identifier, GooValue> baseValues,
                                          List<RecipeInput> recipes, Set<Identifier> denied) {
        Set<Identifier> valued = new HashSet<>(baseValues.keySet());
        ScaffoldGraph.propagateValues(valued, recipes, denied);
        return valued;
    }

    /**
     * Phase 1: finds unvalued items no recipe produces (true roots).
     * These are recipeless inputs that need manual valuation regardless
     * of whether their downstream is already covered.
     *
     * @param recipeItems all items in the recipe graph
     * @param byOutput recipes grouped by output
     * @param valued currently valued items
     * @param denied excluded items
     * @param reverseDeps reverse dependency graph
     * @param roots accumulator for discovered roots
     * @return set of true root IDs (for simulation in phase 2)
     */
    static Set<Identifier> collectTrueRoots(
            Set<Identifier> recipeItems,
            Map<Identifier, List<RecipeInput>> byOutput,
            Set<Identifier> valued, Set<Identifier> denied,
            Map<Identifier, Set<Identifier>> reverseDeps,
            List<ScaffoldGenerator.Root> roots) {
        Set<Identifier> trueRootIds = new HashSet<>();
        for (Identifier item : recipeItems) {
            if (!isTrueRoot(item, byOutput, valued, denied)) { continue; }
            trueRootIds.add(item);
            roots.add(buildTrueRoot(item, reverseDeps, valued, denied));
        }
        return trueRootIds;
    }

    /**
     * Builds a Root for a true root item (no recipe produces it).
     *
     * @param item the true root item
     * @param reverseDeps reverse dependency graph
     * @param valued currently valued items
     * @param denied excluded items
     * @return root with downstream and example chain computed
     */
    private static ScaffoldGenerator.Root buildTrueRoot(Identifier item,
                                                         Map<Identifier, Set<Identifier>> reverseDeps,
                                                         Set<Identifier> valued, Set<Identifier> denied) {
        Set<Identifier> downstream = ScaffoldGraph.computeDownstream(item, reverseDeps, valued, denied);
        List<Identifier> chain = ScaffoldGraph.traceExampleChain(
                item, reverseDeps, valued, denied, ScaffoldGraph.MAX_CHAIN_DEPTH);
        return new ScaffoldGenerator.Root(item, downstream, 1, REASON_NO_RECIPE, chain);
    }

    /**
     * An item is a true root when it is unvalued, not denied, and no recipe produces it.
     *
     * @param item the candidate item
     * @param byOutput recipes grouped by output
     * @param valued currently valued items
     * @param denied excluded items
     * @return true if the item qualifies as a true root
     */
    private static boolean isTrueRoot(Identifier item,
                                       Map<Identifier, List<RecipeInput>> byOutput,
                                       Set<Identifier> valued, Set<Identifier> denied) {
        return !valued.contains(item) && !denied.contains(item) && !byOutput.containsKey(item);
    }

    /**
     * Phases 2 and 3: collects cycle roots and unaccounted items after true roots.
     *
     * @param graphData recipe graph data
     * @param valued currently valued items
     * @param trueRootIds true root IDs from phase 1
     * @param recipes all recipes
     * @param denied excluded items
     * @param allKnownItems all registered item IDs (empty disables phase 3)
     * @param roots accumulator for discovered roots (mutated)
     */
    static void collectRemainingRoots(ScaffoldGenerator.GraphData graphData, Set<Identifier> valued,
                                       Set<Identifier> trueRootIds, List<RecipeInput> recipes,
                                       Set<Identifier> denied, Set<Identifier> allKnownItems,
                                       List<ScaffoldGenerator.Root> roots) {
        Set<Identifier> simValued = simulateWithTrueRoots(valued, trueRootIds, recipes, denied);
        Set<Identifier> cycleItems = findUnresolved(graphData.recipeItems(), simValued, denied);
        collectCycleRoots(cycleItems, recipes, graphData.reverseDeps(), simValued, denied, roots);
        collectUnaccountedRoots(allKnownItems, simValued, cycleItems, denied, graphData.recipeItems(), roots);
    }

    /**
     * Sorts roots by downstream impact (highest first), then cluster size.
     *
     * @param roots mutable list to sort in place
     */
    static void sortRootsByImpact(List<ScaffoldGenerator.Root> roots) {
        roots.sort(Comparator.comparingInt((ScaffoldGenerator.Root r) -> r.downstream().size())
                .thenComparingInt(r -> r.clusterSize())
                .reversed());
    }

    /**
     * Simulates value propagation assuming all true roots are valued.
     *
     * @param valued current valued set (not mutated)
     * @param trueRootIds true root IDs to add before propagation
     * @param recipes all recipes
     * @param denied excluded items
     * @return simulated valued set after propagation
     */
    private static Set<Identifier> simulateWithTrueRoots(
            Set<Identifier> valued, Set<Identifier> trueRootIds,
            List<RecipeInput> recipes, Set<Identifier> denied) {
        Set<Identifier> simValued = new HashSet<>(valued);
        simValued.addAll(trueRootIds);
        ScaffoldGraph.propagateValues(simValued, recipes, denied);
        return simValued;
    }

    /**
     * Finds items still unresolved after simulation (candidates for cycle roots).
     *
     * @param recipeItems all items in the recipe graph
     * @param simValued items valued after simulation
     * @param denied excluded items
     * @return set of unresolved cycle candidate items
     */
    private static Set<Identifier> findUnresolved(Set<Identifier> recipeItems,
                                                   Set<Identifier> simValued,
                                                   Set<Identifier> denied) {
        Set<Identifier> unresolved = new HashSet<>();
        for (Identifier item : recipeItems) {
            if (!simValued.contains(item) && !denied.contains(item)) {
                unresolved.add(item);
            }
        }
        return unresolved;
    }

    /**
     * Phase 2: picks cycle entry points from unresolved items clustered
     * by homogenous recipes. Each cluster gets one representative root.
     *
     * @param cycleItems unresolved items forming cycles
     * @param recipes all recipes (filtered to homogenous internally)
     * @param reverseDeps reverse dependency graph
     * @param simValued simulated valued set
     * @param denied excluded items
     * @param roots accumulator for discovered roots
     */
    private static void collectCycleRoots(Set<Identifier> cycleItems,
                                           List<RecipeInput> recipes,
                                           Map<Identifier, Set<Identifier>> reverseDeps,
                                           Set<Identifier> simValued, Set<Identifier> denied,
                                           List<ScaffoldGenerator.Root> roots) {
        if (cycleItems.isEmpty()) { return; }

        List<RecipeInput> homogenous = filterHomogenous(recipes);
        Map<Identifier, Set<Identifier>> clusters = ScaffoldClustering.buildClusters(homogenous, cycleItems);
        Set<Identifier> visited = new HashSet<>();
        for (Identifier item : cycleItems) {
            processCycleItem(item, visited, clusters, homogenous, reverseDeps, simValued, denied, roots);
        }
    }

    /**
     * Filters recipes to only those with a single repeated input item (homogenous).
     *
     * @param recipes all recipes
     * @return homogenous recipes only
     */
    private static List<RecipeInput> filterHomogenous(List<RecipeInput> recipes) {
        return recipes.stream()
                .filter(r -> r.soleInputItem() != null)
                .toList();
    }

    /**
     * Processes a single cycle item: skips if already visited, otherwise picks
     * a cluster entry point and adds a root.
     *
     * @param item the cycle item to process
     * @param visited items already assigned to a cluster (mutated)
     * @param clusters union-find cluster map
     * @param homogenous homogenous recipes for entry selection
     * @param reverseDeps reverse dependency graph
     * @param simValued simulated valued set
     * @param denied excluded items
     * @param roots accumulator for discovered roots (mutated)
     */
    private static void processCycleItem(Identifier item, Set<Identifier> visited,
                                          Map<Identifier, Set<Identifier>> clusters,
                                          List<RecipeInput> homogenous,
                                          Map<Identifier, Set<Identifier>> reverseDeps,
                                          Set<Identifier> simValued, Set<Identifier> denied,
                                          List<ScaffoldGenerator.Root> roots) {
        if (visited.contains(item)) { return; }
        Set<Identifier> cluster = clusters.getOrDefault(item, Set.of(item));
        if (!Collections.disjoint(cluster, visited)) { return; }

        Identifier entry = ScaffoldClustering.pickCycleEntry(cluster, homogenous, reverseDeps, denied);
        visited.addAll(cluster);
        roots.add(buildCycleRoot(entry, cluster, reverseDeps, simValued, denied));
    }

    /**
     * Builds a Root for a cycle cluster entry point, computing its downstream
     * as the union of all cluster members' downstream plus sibling members.
     *
     * @param entry the chosen entry point
     * @param cluster all items in this cycle cluster
     * @param reverseDeps reverse dependency graph
     * @param simValued simulated valued set
     * @param denied excluded items
     * @return the constructed cycle Root
     */
    private static ScaffoldGenerator.Root buildCycleRoot(Identifier entry, Set<Identifier> cluster,
                                                          Map<Identifier, Set<Identifier>> reverseDeps,
                                                          Set<Identifier> simValued, Set<Identifier> denied) {
        Set<Identifier> downstream = computeClusterDownstream(entry, cluster, reverseDeps, simValued, denied);
        List<Identifier> chain = ScaffoldGraph.traceExampleChain(
                entry, reverseDeps, simValued, denied, ScaffoldGraph.MAX_CHAIN_DEPTH);
        String reason = cluster.size() > 1
                ? REASON_CYCLE_PREFIX + cluster.size() + REASON_CYCLE_SUFFIX : REASON_CYCLE;
        return new ScaffoldGenerator.Root(entry, downstream, cluster.size(), reason, chain);
    }

    /**
     * Computes the union of downstream items for all cluster members,
     * adding sibling members (excluding the entry point itself).
     *
     * @param entry the cluster entry point (excluded from downstream)
     * @param cluster all items in the cycle cluster
     * @param reverseDeps reverse dependency graph
     * @param simValued simulated valued set
     * @param denied excluded items
     * @return combined downstream set for the cluster
     */
    private static Set<Identifier> computeClusterDownstream(Identifier entry, Set<Identifier> cluster,
                                                             Map<Identifier, Set<Identifier>> reverseDeps,
                                                             Set<Identifier> simValued, Set<Identifier> denied) {
        Set<Identifier> downstream = new HashSet<>();
        for (Identifier member : cluster) {
            downstream.addAll(ScaffoldGraph.computeDownstream(member, reverseDeps, simValued, denied));
        }
        for (Identifier member : cluster) {
            if (!member.equals(entry)) { downstream.add(member); }
        }
        downstream.remove(entry);
        return downstream;
    }

    /**
     * Phase 3: finds registered items the simulation could not resolve.
     * Items in the recipe graph get "broken chain"; others get "no recipe or chain".
     *
     * @param allKnownItems all registered item IDs (empty disables this phase)
     * @param simValued simulated valued set
     * @param cycleItems items handled by cycle roots
     * @param denied excluded items
     * @param recipeItems items mentioned in the recipe graph
     * @param roots accumulator for discovered roots
     */
    private static void collectUnaccountedRoots(Set<Identifier> allKnownItems,
                                                 Set<Identifier> simValued,
                                                 Set<Identifier> cycleItems,
                                                 Set<Identifier> denied,
                                                 Set<Identifier> recipeItems,
                                                 List<ScaffoldGenerator.Root> roots) {
        if (allKnownItems.isEmpty()) { return; }

        Set<Identifier> accounted = buildAccountedSet(simValued, cycleItems, denied);
        for (Identifier item : allKnownItems) {
            if (accounted.contains(item)) { continue; }
            String reason = recipeItems.contains(item)
                    ? REASON_BROKEN_CHAIN : REASON_NO_CHAIN;
            roots.add(new ScaffoldGenerator.Root(item, Set.of(), 1, reason, List.of()));
        }
    }

    /**
     * Combines simulated-valued, cycle, and denied sets into one accounted set.
     *
     * @param simValued simulated valued items
     * @param cycleItems items handled by cycle roots
     * @param denied excluded items
     * @return union of all accounted items
     */
    private static Set<Identifier> buildAccountedSet(Set<Identifier> simValued,
                                                      Set<Identifier> cycleItems,
                                                      Set<Identifier> denied) {
        Set<Identifier> accounted = new HashSet<>(simValued);
        accounted.addAll(cycleItems);
        accounted.addAll(denied);
        return accounted;
    }
}
