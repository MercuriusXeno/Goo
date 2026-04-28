package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes the recipe dependency graph to find root nodes -- items that
 * need manual valuation to unblock downstream derivation cascades.
 * Generates a scaffold file showing what to value and why.
 *
 * <p>Graph construction and traversal live in {@link ScaffoldGraph}.
 * Phase 1/2/3 root discovery lives in {@link ScaffoldRootFinder}.
 * JSON formatting and tag grouping live in {@link ScaffoldFormatter}.
 */
public final class ScaffoldGenerator {

    /**
     * Utility class, not instantiable.
     */
    private ScaffoldGenerator() {
    }

    /**
     * Backward-compatible overload without registry items (used by tests).
     * Only finds recipe-graph roots; does not include flat registry items.
     *
     * @param recipes    all known recipes
     * @param baseValues currently valued items
     * @param denied     denied items (excluded from analysis)
     * @return roots sorted by downstream impact (highest first)
     */
    public static List<Root> findRoots(List<RecipeInput> recipes,
                                       Map<Identifier, GooValue> baseValues,
                                       Set<Identifier> denied) {
        return findRoots(recipes, baseValues, denied, Set.of());
    }

    /**
     * Finds all items that need manual valuation.
     * <p>Three phases:
     * <ol>
     *   <li><b>True roots</b> -- unvalued recipe inputs no recipe produces
     *       (e.g. blaze_rod, prismarine_shard). Shown even when downstream
     *       is empty (all products already valued).</li>
     *   <li><b>Cycle roots</b> -- items trapped in dependency cycles even
     *       after all true roots would be valued. Picks the smallest unit
     *       via multiplication factor scoring.</li>
     *   <li><b>Unaccounted items</b> -- registered items the simulation
     *       couldn't resolve. "no recipe or chain" if not in any recipe;
     *       "broken chain" if in the recipe graph but propagation failed.</li>
     * </ol>
     *
     * @param recipes       all known recipes
     * @param baseValues    currently valued items
     * @param denied        denied items (excluded from analysis)
     * @param allKnownItems all registered item IDs (from BuiltInRegistries);
     *                      empty set disables phase 3
     * @return roots sorted by downstream impact (highest first), then flat items
     */
    public static List<Root> findRoots(List<RecipeInput> recipes,
                                       Map<Identifier, GooValue> baseValues,
                                       Set<Identifier> denied,
                                       Set<Identifier> allKnownItems) {
        var graphData = ScaffoldGraph.buildGraphData(recipes);
        Set<Identifier> valued = ScaffoldRootFinder.initValuedSet(baseValues, recipes, denied);

        List<Root> roots = new ArrayList<>();
        Set<Identifier> trueRootIds = ScaffoldRootFinder.collectTrueRoots(
                graphData.recipeItems(), graphData.byOutput(), valued, denied, graphData.reverseDeps(), roots);

        ScaffoldRootFinder.collectRemainingRoots(graphData, valued, trueRootIds, recipes, denied, allKnownItems, roots);
        ScaffoldRootFinder.sortRootsByImpact(roots);
        return roots;
    }

    /**
     * Generates scaffold without tag grouping (backward-compatible overload).
     *
     * @param roots the roots to generate scaffold for
     * @return lines suitable for writing to a file, plus root count
     */
    public static ScaffoldResult generateScaffold(List<Root> roots) {
        return generateScaffold(roots, List.of());
    }

    /**
     * Generates scaffold file lines, grouping roots that share a recipe tag
     * into a single {@code "#tag": { }} entry.
     *
     * @param roots   the roots to generate scaffold for
     * @param recipes recipes providing tag metadata for grouping
     * @return lines suitable for writing to a file, plus root count
     */
    public static ScaffoldResult generateScaffold(List<Root> roots, List<RecipeInput> recipes) {
        return generateScaffold(roots, recipes, false);
    }

    /**
     * Generates a scaffold JSON showing which items need manual valuation.
     *
     * @param roots   the roots to generate scaffold for
     * @param recipes recipes providing tag metadata for grouping
     * @param bare    if true, emit only root keys with empty values (no comments)
     * @return lines suitable for writing to a file, plus root count
     */
    public static ScaffoldResult generateScaffold(List<Root> roots, List<RecipeInput> recipes, boolean bare) {
        return ScaffoldFormatter.generateScaffold(roots, recipes, bare);
    }

    /**
     * A root node that needs manual valuation to unblock downstream items.
     *
     * @param itemId       the item that needs a value
     * @param downstream   items that would be unblocked if this were valued
     * @param clusterSize  number of items in this root's value cluster
     * @param reason       why this item is a root (no recipe, clique entry, etc.)
     * @param exampleChain a sample derivation path from this root
     */
    public record Root(
            Identifier itemId,
            Set<Identifier> downstream,
            int clusterSize,
            String reason,
            List<Identifier> exampleChain
    ) {
    }

    /**
     * Intermediate data from recipe graph construction.
     *
     * @param byOutput    recipes grouped by output
     * @param recipeItems all items mentioned in the graph
     * @param reverseDeps reverse dependency graph
     */
    record GraphData(
            Map<Identifier, List<RecipeInput>> byOutput,
            Set<Identifier> recipeItems,
            Map<Identifier, Set<Identifier>> reverseDeps
    ) {
    }

    /**
     * Result of scaffold generation: the file lines and the root count.
     *
     * @param lines     the generated scaffold file lines
     * @param rootCount the number of roots in the scaffold
     */
    public record ScaffoldResult(List<String> lines, int rootCount) {
    }
}
