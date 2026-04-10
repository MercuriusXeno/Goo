package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Union-find clustering and cycle-entry selection for the scaffold pipeline.
 * Groups items linked by homogenous recipes into clusters, then picks the
 * best entry point (smallest base unit) via multiplication factor scoring.
 * All methods are stateless; the class is not instantiable.
 */
final class ScaffoldClustering {

    private ScaffoldClustering() {}

    /**
     * Builds connected components from homogenous recipes.
     * Items linked by homogenous recipes form clusters where valuing
     * any one member propagates to all others via reverse derivation.
     *
     * @param homogenous recipes with a single repeated input item
     * @param allItems all items to include in clustering
     * @return map from each item to its cluster set (shared reference)
     */
    static Map<Identifier, Set<Identifier>> buildClusters(
            List<RecipeInput> homogenous, Set<Identifier> allItems) {
        Map<Identifier, Set<Identifier>> membership = initSingletons(allItems);
        for (RecipeInput recipe : homogenous) {
            Identifier input = recipe.soleInputItem();
            Identifier output = recipe.output();
            if (!isValidClusterEdge(input, output)) { continue; }
            mergeClusters(membership, input, output);
        }
        return membership;
    }

    /**
     * Initializes each item into its own singleton cluster for union-find.
     *
     * @param items items to initialize
     * @return mutable membership map (item to its cluster set)
     */
    private static Map<Identifier, Set<Identifier>> initSingletons(Set<Identifier> items) {
        Map<Identifier, Set<Identifier>> membership = new HashMap<>();
        for (Identifier item : items) {
            Set<Identifier> singleton = new HashSet<>();
            singleton.add(item);
            membership.put(item, singleton);
        }
        return membership;
    }

    /**
     * A recipe edge is valid for clustering when input and output are distinct non-null items.
     *
     * @param input the recipe's sole input item (may be null)
     * @param output the recipe's output item
     * @return true if this edge should merge clusters
     */
    private static boolean isValidClusterEdge(Identifier input, Identifier output) {
        return input != null && !input.equals(output);
    }

    /**
     * Merges two clusters in the union-find, absorbing the smaller into the larger.
     * Updates all membership pointers for the absorbed cluster.
     *
     * @param membership the union-find membership map (mutated)
     * @param a first item whose cluster to merge
     * @param b second item whose cluster to merge
     */
    private static void mergeClusters(Map<Identifier, Set<Identifier>> membership,
                                       Identifier a, Identifier b) {
        Set<Identifier> clusterA = membership.get(a);
        Set<Identifier> clusterB = membership.get(b);
        if (shouldSkipMerge(clusterA, clusterB)) { return; }
        absorbSmaller(membership, clusterA, clusterB);
    }

    /**
     * Returns true if the merge should be skipped: either cluster is null or they're the same instance.
     *
     * @param clusterA first cluster (may be null)
     * @param clusterB second cluster (may be null)
     * @return true if merge is unnecessary
     */
    private static boolean shouldSkipMerge(Set<Identifier> clusterA, Set<Identifier> clusterB) {
        return clusterA == null || clusterB == null || clusterA == clusterB;
    }

    /**
     * Absorbs the smaller cluster into the larger, updating all membership pointers.
     *
     * @param membership the union-find membership map (mutated)
     * @param clusterA first cluster
     * @param clusterB second cluster
     */
    private static void absorbSmaller(Map<Identifier, Set<Identifier>> membership,
                                       Set<Identifier> clusterA, Set<Identifier> clusterB) {
        Set<Identifier> larger = clusterA.size() >= clusterB.size() ? clusterA : clusterB;
        Set<Identifier> smaller = larger == clusterA ? clusterB : clusterA;
        larger.addAll(smaller);
        for (Identifier id : smaller) { membership.put(id, larger); }
    }

    /**
     * Picks the cycle cluster member that is the smallest unit. Uses
     * multiplication factor scoring: the item that multiplies most from
     * within-cluster recipes is the smallest base unit (e.g. nugget > ingot
     * > block). Ties broken by fan-out.
     *
     * @param cluster the set of items in the cycle cluster
     * @param homogenous homogenous recipes for propagation scoring
     * @param reverseDeps reverse dependency graph for fan-out tiebreaking
     * @param denied items excluded from selection
     * @return the best cycle entry point item ID
     */
    static Identifier pickCycleEntry(Set<Identifier> cluster,
                                      List<RecipeInput> homogenous,
                                      Map<Identifier, Set<Identifier>> reverseDeps,
                                      Set<Identifier> denied) {
        if (cluster.size() == 1) { return cluster.iterator().next(); }

        Map<Identifier, Long> factors = initFactors(cluster);
        propagateFactors(factors, cluster, homogenous);
        return selectBestEntry(cluster, factors, reverseDeps, denied);
    }

    /**
     * Initializes multiplication factors to 1 for all cluster members.
     *
     * @param cluster the cycle cluster members
     * @return mutable map of item to multiplication factor (all 1)
     */
    private static Map<Identifier, Long> initFactors(Set<Identifier> cluster) {
        Map<Identifier, Long> factors = new HashMap<>();
        for (Identifier member : cluster) {
            factors.put(member, 1L);
        }
        return factors;
    }

    /**
     * Propagates multiplication factors through homogenous recipes within
     * the cluster until no more updates occur.
     *
     * @param factors mutable factor map (mutated)
     * @param cluster the cycle cluster members
     * @param homogenous homogenous recipes to propagate through
     */
    private static void propagateFactors(Map<Identifier, Long> factors,
                                          Set<Identifier> cluster,
                                          List<RecipeInput> homogenous) {
        boolean changed = true;
        while (changed) {
            changed = propagateFactorsOnePass(factors, cluster, homogenous);
        }
    }

    /**
     * Runs one pass of factor propagation across homogenous recipes.
     *
     * @param factors mutable factor map (may grow)
     * @param cluster the cycle cluster members
     * @param homogenous homogenous recipes to check
     * @return true if at least one factor was updated this pass
     */
    private static boolean propagateFactorsOnePass(Map<Identifier, Long> factors,
                                                    Set<Identifier> cluster,
                                                    List<RecipeInput> homogenous) {
        boolean changed = false;
        for (RecipeInput recipe : homogenous) {
            if (tryPropagateFactorRecipe(factors, cluster, recipe)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Attempts to propagate a single recipe's multiplication factor within a cluster.
     *
     * @param factors mutable factor map (may be mutated)
     * @param cluster the cycle cluster members
     * @param recipe the homogenous recipe to check
     * @return true if a factor was updated
     */
    private static boolean tryPropagateFactorRecipe(Map<Identifier, Long> factors,
                                                     Set<Identifier> cluster,
                                                     RecipeInput recipe) {
        Identifier input = recipe.soleInputItem();
        Identifier output = recipe.output();
        if (input == null || !cluster.contains(input) || !cluster.contains(output)) { return false; }

        long outputFactor = computeOutputFactor(factors, input, recipe);
        return tryUpdateFactor(factors, output, outputFactor);
    }

    /**
     * Computes the output multiplication factor from a recipe's input factor.
     *
     * @param factors current factor map
     * @param input the recipe's input item
     * @param recipe the recipe providing result count and input count
     * @return the computed output factor
     */
    private static long computeOutputFactor(Map<Identifier, Long> factors, Identifier input, RecipeInput recipe) {
        long inputFactor = factors.getOrDefault(input, 1L);
        int inputCount = recipe.ingredientAlternatives().size();
        return inputFactor * recipe.resultCount() / inputCount;
    }

    /**
     * Updates the factor for an item if the new factor exceeds the current one.
     *
     * @param factors mutable factor map (may be mutated)
     * @param item the item to update
     * @param newFactor the proposed new factor
     * @return true if the factor was updated
     */
    private static boolean tryUpdateFactor(Map<Identifier, Long> factors, Identifier item, long newFactor) {
        if (newFactor > factors.getOrDefault(item, 1L)) {
            factors.put(item, newFactor);
            return true;
        }
        return false;
    }

    /**
     * Selects the best cycle entry: highest multiplication factor, tiebroken by fan-out.
     *
     * @param cluster the cycle cluster members
     * @param factors multiplication factor map
     * @param reverseDeps reverse dependency graph for fan-out tiebreaking
     * @param denied items excluded from selection
     * @return the best cycle entry point item ID
     */
    private static Identifier selectBestEntry(Set<Identifier> cluster,
                                               Map<Identifier, Long> factors,
                                               Map<Identifier, Set<Identifier>> reverseDeps,
                                               Set<Identifier> denied) {
        return cluster.stream()
                .filter(id -> !denied.contains(id))
                .max(Comparator.<Identifier>comparingLong(id -> factors.getOrDefault(id, 1L))
                        .thenComparingInt(id -> reverseDeps.getOrDefault(id, Set.of()).size()))
                .orElse(cluster.iterator().next());
    }
}
