package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes the recipe dependency graph to find root nodes -- items that
 * need manual valuation to unblock downstream derivation cascades.
 * Generates a scaffold file showing what to value and why.
 */
public final class ScaffoldGenerator {

    /** Maximum depth for example derivation chains. */
    private static final int MAX_CHAIN_DEPTH = 5;
    /** Maximum items shown in a chain description before truncating. */
    private static final int MAX_CHAIN_SHOWN = 5;
    /** Minimum members for a tag to qualify as a scaffold group. */
    private static final int MIN_GROUP_SIZE = 2;
    /** Initial capacity for chain description StringBuilder. */
    private static final int CHAIN_DESC_CAPACITY = 32;
    // --- String constants ---

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
    /** JSON opening brace. */
    private static final String JSON_OPEN = "{";
    /** JSON closing brace. */
    private static final String JSON_CLOSE = "}";
    /** Comment line when no roots found. */
    private static final String COMMENT_NO_ROOTS =
            "    \"_comment_scaffold\": \"No unvalued roots found. All recipe chains are anchored.\"";
    /** Root scaffold header comment prefix. */
    private static final String COMMENT_HEADER_PREFIX =
            "    \"_comment_scaffold\": \"Root nodes needing manual valuation. ";
    /** Root scaffold header comment suffix. */
    private static final String COMMENT_HEADER_SUFFIX =
            " root(s) found. Fill in goo types, then paste into base_values.json.\",";
    /** Comment key prefix for individual entries. */
    private static final String COMMENT_KEY_PREFIX = "    \"_comment_";
    /** Comment key/value separator. */
    private static final String COMMENT_VALUE_SEP = "\": \"";
    /** Closing quote-comma for comment lines. */
    private static final String COMMENT_CLOSE = "\",";
    /** JSON key prefix indent and quote. */
    private static final String KEY_PREFIX = "    \"";
    /** JSON empty value with comma. */
    private static final String KEY_EMPTY_VALUE_COMMA = "\": { },";
    /** JSON empty value without comma. */
    private static final String KEY_EMPTY_VALUE = "\": { }";
    /** Tag group comment: member count label. */
    private static final String TAG_MEMBERS = " tag members. Unblocks ";
    /** Tag group JSON key prefix. */
    private static final String TAG_KEY_PREFIX = "#";
    /** Root comment: unblocks label prefix. */
    private static final String UNBLOCKS_PREFIX = ". Unblocks ";
    /** Root comment: chain separator. */
    private static final String CHAIN_COLON = ": ";
    /** Arrow separator in example chains. */
    private static final String CHAIN_ARROW = " -> ";
    /** Truncation prefix in example chains. */
    private static final String CHAIN_TRUNCATE_PREFIX = " ... +";
    /** Truncation suffix in example chains. */
    private static final String CHAIN_TRUNCATE_SUFFIX = " more";
    /** Minecraft namespace for identifier shortening. */
    private static final String NS_MINECRAFT = "minecraft";
    /** Sentinel value for no best candidate found. */
    private static final int NO_BEST = -1;
    /** Empty line separator in scaffold output. */
    private static final String EMPTY_LINE = "";

    /** Utility class, not instantiable. */
    private ScaffoldGenerator() {}

    /**
     * A root node that needs manual valuation to unblock downstream items.
     *
     * @param itemId         the item that needs a value
     * @param downstream     items that would be unblocked if this were valued
     * @param clusterSize    number of items in this root's value cluster
     * @param reason         why this item is a root (no recipe, clique entry, etc.)
     * @param exampleChain   a sample derivation path from this root
     */
    public record Root(
            Identifier itemId,
            Set<Identifier> downstream,
            int clusterSize,
            String reason,
            List<Identifier> exampleChain
    ) {}

    /**
     * Backward-compatible overload without registry items (used by tests).
     * Only finds recipe-graph roots; does not include flat registry items.
     *
     * @param recipes all known recipes
     * @param baseValues currently valued items
     * @param denied denied items (excluded from analysis)
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
        var graphData = buildGraphData(recipes);
        Set<Identifier> valued = initValuedSet(baseValues, recipes, denied);

        List<Root> roots = new ArrayList<>();
        Set<Identifier> trueRootIds = collectTrueRoots(
                graphData.recipeItems, graphData.byOutput, valued, denied, graphData.reverseDeps, roots);

        collectRemainingRoots(graphData, valued, trueRootIds, recipes, denied, allKnownItems, roots);
        sortRootsByImpact(roots);
        return roots;
    }

    /**
     * Initializes the valued set from base values and propagates through recipes.
     *
     * @param baseValues currently valued items
     * @param recipes all recipes for propagation
     * @param denied excluded items
     * @return mutable valued set after initial propagation
     */
    private static Set<Identifier> initValuedSet(Map<Identifier, GooValue> baseValues,
                                                  List<RecipeInput> recipes, Set<Identifier> denied) {
        Set<Identifier> valued = new HashSet<>(baseValues.keySet());
        propagateValues(valued, recipes, denied);
        return valued;
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
    private static void collectRemainingRoots(GraphData graphData, Set<Identifier> valued,
                                               Set<Identifier> trueRootIds, List<RecipeInput> recipes,
                                               Set<Identifier> denied, Set<Identifier> allKnownItems,
                                               List<Root> roots) {
        Set<Identifier> simValued = simulateWithTrueRoots(valued, trueRootIds, recipes, denied);
        Set<Identifier> cycleItems = findUnresolved(graphData.recipeItems, simValued, denied);
        collectCycleRoots(cycleItems, recipes, graphData.reverseDeps, simValued, denied, roots);
        collectUnaccountedRoots(allKnownItems, simValued, cycleItems, denied, graphData.recipeItems, roots);
    }

    /**
     * Intermediate data from recipe graph construction.
     *
     * @param byOutput    recipes grouped by output
     * @param recipeItems all items mentioned in the graph
     * @param reverseDeps reverse dependency graph
     */
    private record GraphData(
            Map<Identifier, List<RecipeInput>> byOutput,
            Set<Identifier> recipeItems,
            Map<Identifier, Set<Identifier>> reverseDeps
    ) {}

    /**
     * Builds the forward/reverse dependency graphs and collects all recipe items.
     *
     * @param recipes all known recipes
     * @return graph data bundle for root-finding phases
     */
    private static GraphData buildGraphData(List<RecipeInput> recipes) {
        var byOutput = GooValueDerivation.groupByOutput(recipes);
        var forwardDeps = buildForwardDeps(byOutput);
        var recipeItems = collectRecipeItems(forwardDeps);
        var reverseDeps = buildReverseDeps(forwardDeps);
        return new GraphData(byOutput, recipeItems, reverseDeps);
    }

    /**
     * Sorts roots by downstream impact (highest first), then cluster size.
     *
     * @param roots mutable list to sort in place
     */
    private static void sortRootsByImpact(List<Root> roots) {
        roots.sort(Comparator.comparingInt((Root r) -> r.downstream().size())
                .thenComparingInt(r -> r.clusterSize())
                .reversed());
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
    private static Set<Identifier> collectTrueRoots(
            Set<Identifier> recipeItems,
            Map<Identifier, List<RecipeInput>> byOutput,
            Set<Identifier> valued, Set<Identifier> denied,
            Map<Identifier, Set<Identifier>> reverseDeps,
            List<Root> roots) {
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
    private static Root buildTrueRoot(Identifier item, Map<Identifier, Set<Identifier>> reverseDeps,
                                       Set<Identifier> valued, Set<Identifier> denied) {
        Set<Identifier> downstream = computeDownstream(item, reverseDeps, valued, denied);
        List<Identifier> chain = traceExampleChain(item, reverseDeps, valued, denied, MAX_CHAIN_DEPTH);
        return new Root(item, downstream, 1, REASON_NO_RECIPE, chain);
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
        propagateValues(simValued, recipes, denied);
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
                                           List<Root> roots) {
        if (cycleItems.isEmpty()) { return; }

        List<RecipeInput> homogenous = filterHomogenous(recipes);
        Map<Identifier, Set<Identifier>> clusters = buildClusters(homogenous, cycleItems);
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
                                          List<Root> roots) {
        if (visited.contains(item)) { return; }
        Set<Identifier> cluster = clusters.getOrDefault(item, Set.of(item));
        if (!Collections.disjoint(cluster, visited)) { return; }

        Identifier entry = pickCycleEntry(cluster, homogenous, reverseDeps, denied);
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
    private static Root buildCycleRoot(Identifier entry, Set<Identifier> cluster,
                                        Map<Identifier, Set<Identifier>> reverseDeps,
                                        Set<Identifier> simValued, Set<Identifier> denied) {
        Set<Identifier> downstream = computeClusterDownstream(entry, cluster, reverseDeps, simValued, denied);
        List<Identifier> chain = traceExampleChain(entry, reverseDeps, simValued, denied, MAX_CHAIN_DEPTH);
        String reason = cluster.size() > 1
                ? REASON_CYCLE_PREFIX + cluster.size() + REASON_CYCLE_SUFFIX : REASON_CYCLE;
        return new Root(entry, downstream, cluster.size(), reason, chain);
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
        Set<Identifier> downstream = unionMemberDownstream(cluster, reverseDeps, simValued, denied);
        addClusterSiblings(downstream, entry, cluster);
        return downstream;
    }

    /**
     * Computes the union of downstream sets for all cluster members.
     *
     * @param cluster the cycle cluster members
     * @param reverseDeps reverse dependency graph
     * @param simValued simulated valued set
     * @param denied excluded items
     * @return combined downstream set
     */
    private static Set<Identifier> unionMemberDownstream(Set<Identifier> cluster,
                                                          Map<Identifier, Set<Identifier>> reverseDeps,
                                                          Set<Identifier> simValued, Set<Identifier> denied) {
        Set<Identifier> downstream = new HashSet<>();
        for (Identifier member : cluster) {
            downstream.addAll(computeDownstream(member, reverseDeps, simValued, denied));
        }
        return downstream;
    }

    /**
     * Adds cluster siblings (excluding the entry point) to the downstream set.
     *
     * @param downstream set to add siblings to (mutated)
     * @param entry the entry point to exclude
     * @param cluster all cluster members
     */
    private static void addClusterSiblings(Set<Identifier> downstream, Identifier entry,
                                            Set<Identifier> cluster) {
        for (Identifier member : cluster) {
            if (!member.equals(entry)) { downstream.add(member); }
        }
        downstream.remove(entry);
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
                                                 List<Root> roots) {
        if (allKnownItems.isEmpty()) { return; }

        Set<Identifier> accounted = buildAccountedSet(simValued, cycleItems, denied);
        for (Identifier item : allKnownItems) {
            if (accounted.contains(item)) { continue; }
            String reason = recipeItems.contains(item)
                    ? REASON_BROKEN_CHAIN : REASON_NO_CHAIN;
            roots.add(new Root(item, Set.of(), 1, reason, List.of()));
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

    /**
     * Result of scaffold generation: the file lines and the root count.
     *
     * @param lines     the generated scaffold file lines
     * @param rootCount the number of roots in the scaffold
     */
    public record ScaffoldResult(List<String> lines, int rootCount) {}

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
        List<String> lines = new ArrayList<>();
        lines.add(JSON_OPEN);

        if (roots.isEmpty()) {
            return emitEmptyScaffold(lines, bare);
        }
        return emitPopulatedScaffold(lines, roots, recipes, bare);
    }

    /**
     * Emits a populated scaffold with entries, header, and closing brace.
     *
     * @param lines output line accumulator (mutated)
     * @param roots non-empty root list
     * @param recipes recipes for tag grouping
     * @param bare if true, omit comments
     * @return scaffold result with entry count
     */
    private static ScaffoldResult emitPopulatedScaffold(List<String> lines, List<Root> roots,
                                                         List<RecipeInput> recipes, boolean bare) {
        List<ScaffoldEntry> entries = buildAllEntries(roots, recipes);
        emitScaffoldHeader(lines, entries.size(), bare);
        emitScaffoldEntries(lines, entries, bare);
        lines.add(JSON_CLOSE);
        return new ScaffoldResult(lines, entries.size());
    }

    /**
     * Emits the scaffold for an empty root list (no roots found).
     *
     * @param lines output line accumulator (mutated)
     * @param bare if true, omit comment lines
     * @return scaffold result with zero roots
     */
    private static ScaffoldResult emitEmptyScaffold(List<String> lines, boolean bare) {
        if (!bare) {
            lines.add(COMMENT_NO_ROOTS);
        }
        lines.add(JSON_CLOSE);
        return new ScaffoldResult(lines, 0);
    }

    /**
     * Builds all scaffold entries from roots with tag grouping applied.
     *
     * @param roots all roots in priority order
     * @param recipes recipes for tag grouping
     * @return ordered scaffold entries
     */
    private static List<ScaffoldEntry> buildAllEntries(List<Root> roots, List<RecipeInput> recipes) {
        Map<Identifier, List<Root>> tagGroups = buildTagGroups(roots, recipes);
        Set<Root> grouped = collectGroupedRoots(tagGroups);
        return buildEntries(roots, tagGroups, grouped);
    }

    /**
     * Collects all roots that belong to at least one tag group.
     *
     * @param tagGroups tag ID to grouped root members
     * @return set of roots assigned to any tag group
     */
    private static Set<Root> collectGroupedRoots(Map<Identifier, List<Root>> tagGroups) {
        Set<Root> grouped = new HashSet<>();
        for (List<Root> group : tagGroups.values()) {
            grouped.addAll(group);
        }
        return grouped;
    }

    /**
     * Emits the scaffold header comment if not in bare mode.
     *
     * @param lines output line accumulator (mutated)
     * @param entryCount number of entries in the scaffold
     * @param bare if true, omit header
     */
    private static void emitScaffoldHeader(List<String> lines, int entryCount, boolean bare) {
        if (!bare) {
            lines.add(COMMENT_HEADER_PREFIX + entryCount + COMMENT_HEADER_SUFFIX);
            lines.add(EMPTY_LINE);
        }
    }

    /**
     * Emits all scaffold entries as JSON lines (comment + key-value pairs).
     *
     * @param lines output line accumulator (mutated)
     * @param entries the scaffold entries to emit
     * @param bare if true, omit comment lines
     */
    private static void emitScaffoldEntries(List<String> lines, List<ScaffoldEntry> entries, boolean bare) {
        for (int i = 0; i < entries.size(); i++) {
            ScaffoldEntry entry = entries.get(i);
            boolean last = i == entries.size() - 1;
            emitSingleEntry(lines, entry, last, bare);
        }
    }

    /**
     * Emits a single scaffold entry (optional comment line + JSON key line).
     *
     * @param lines output line accumulator (mutated)
     * @param entry the scaffold entry to emit
     * @param last true if this is the last entry (no trailing comma)
     * @param bare if true, omit comment line and blank separator
     */
    private static void emitSingleEntry(List<String> lines, ScaffoldEntry entry, boolean last, boolean bare) {
        if (!bare) {
            lines.add(COMMENT_KEY_PREFIX + entry.commentKey + COMMENT_VALUE_SEP + entry.comment + COMMENT_CLOSE);
        }
        lines.add(KEY_PREFIX + entry.jsonKey + (last ? KEY_EMPTY_VALUE : KEY_EMPTY_VALUE_COMMA));
        if (!bare) {
            lines.add(EMPTY_LINE);
        }
    }

    /** A single scaffold entry, either an individual root or a tag group. */
    private record ScaffoldEntry(String commentKey, String comment, String jsonKey, int unblocks) {}

    /**
     * Builds the ordered list of scaffold entries, sorted by unblock count descending.
     *
     * @param roots all roots in priority order
     * @param tagGroups tag ID to grouped root members
     * @param grouped set of roots already assigned to a tag group
     * @return ordered scaffold entries
     */
    private static List<ScaffoldEntry> buildEntries(List<Root> roots,
            Map<Identifier, List<Root>> tagGroups, Set<Root> grouped) {
        List<ScaffoldEntry> entries = new ArrayList<>();
        dispatchRootEntries(roots, tagGroups, grouped, entries);
        entries.sort(Comparator.comparingInt(ScaffoldEntry::unblocks).reversed());
        return entries;
    }

    /**
     * Dispatches each root to either a tag group entry or an individual entry.
     *
     * @param roots all roots in priority order
     * @param tagGroups tag ID to grouped root members
     * @param grouped roots already assigned to a tag group
     * @param entries accumulator for scaffold entries (mutated)
     */
    private static void dispatchRootEntries(List<Root> roots, Map<Identifier, List<Root>> tagGroups,
                                             Set<Root> grouped, List<ScaffoldEntry> entries) {
        Set<Identifier> emittedTags = new HashSet<>();
        for (Root root : roots) {
            if (grouped.contains(root)) {
                emitTagEntry(root, tagGroups, emittedTags, entries);
            } else {
                entries.add(buildRootEntry(root));
            }
        }
    }

    /**
     * Emits a tag group entry the first time a member of that group is encountered.
     *
     * @param root the root triggering the emission
     * @param tagGroups tag ID to grouped root members
     * @param emittedTags tags already emitted (to avoid duplicates)
     * @param entries accumulator for scaffold entries
     */
    private static void emitTagEntry(Root root, Map<Identifier, List<Root>> tagGroups,
            Set<Identifier> emittedTags, List<ScaffoldEntry> entries) {
        for (Map.Entry<Identifier, List<Root>> entry : tagGroups.entrySet()) {
            if (!entry.getValue().contains(root)) { continue; }
            if (!emittedTags.add(entry.getKey())) { continue; }
            entries.add(buildTagScaffoldEntry(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Constructs a scaffold entry for a tag group by computing the union
     * of all members' downstream sets.
     *
     * @param tagId the tag identifier
     * @param members roots belonging to this tag
     * @return scaffold entry for the tag group
     */
    private static ScaffoldEntry buildTagScaffoldEntry(Identifier tagId, List<Root> members) {
        Set<Identifier> unionDownstream = computeTagUnionDownstream(members);
        String tagShort = shortId(tagId);
        int unblocks = unionDownstream.size();
        String comment = members.size() + TAG_MEMBERS + unblocks;
        return new ScaffoldEntry(tagShort, comment, TAG_KEY_PREFIX + tagShort, unblocks);
    }

    /**
     * Computes the union of downstream sets across all tag group members.
     *
     * @param members roots in the tag group
     * @return union of all members' downstream sets
     */
    private static Set<Identifier> computeTagUnionDownstream(List<Root> members) {
        Set<Identifier> unionDownstream = new HashSet<>();
        for (Root member : members) {
            unionDownstream.addAll(member.downstream());
        }
        return unionDownstream;
    }

    /**
     * Builds a scaffold entry for a single ungrouped root.
     *
     * @param root the root to build an entry for
     * @return a scaffold entry with comment and JSON key
     */
    private static ScaffoldEntry buildRootEntry(Root root) {
        String chainDesc = buildChainDescription(root);
        return new ScaffoldEntry(root.itemId().getPath(), chainDesc,
                shortId(root.itemId()), root.downstream().size());
    }

    /**
     * Builds the human-readable chain description for a root's comment line,
     * showing reason, downstream count, and a sample derivation path.
     *
     * @param root the root to describe
     * @return formatted chain description string
     */
    private static String buildChainDescription(Root root) {
        StringBuilder desc = new StringBuilder(CHAIN_DESC_CAPACITY);
        desc.append(root.reason()).append(UNBLOCKS_PREFIX).append(root.downstream().size()).append(CHAIN_COLON);
        appendChainItems(desc, root.exampleChain());
        return desc.toString();
    }

    /**
     * Appends example chain items to a description, truncating if needed.
     *
     * @param desc the StringBuilder to append to (mutated)
     * @param sample the example chain items
     */
    private static void appendChainItems(StringBuilder desc, List<Identifier> sample) {
        int shown = Math.min(sample.size(), MAX_CHAIN_SHOWN);
        for (int j = 0; j < shown; j++) {
            if (j > 0) { desc.append(CHAIN_ARROW); }
            desc.append(shortId(sample.get(j)));
        }
        if (sample.size() > shown) {
            desc.append(CHAIN_TRUNCATE_PREFIX).append(sample.size() - shown).append(CHAIN_TRUNCATE_SUFFIX);
        }
    }

    /**
     * Groups roots by tag: roots whose itemId appears in a multi-variant tagged slot.
     * A root qualifies if its itemId is a member of some slot with size > 1 that has a tag ID.
     *
     * @param roots the roots to group
     * @param recipes recipes providing tag metadata for grouping
     * @return map of tag ID to the list of roots that belong to that tag
     */
    static Map<Identifier, List<Root>> buildTagGroups(List<Root> roots, List<RecipeInput> recipes) {
        Map<Identifier, Set<Identifier>> tagMembers = buildTagIndex(recipes);
        Set<Identifier> rootIds = new HashSet<>();
        for (Root root : roots) {
            rootIds.add(root.itemId());
        }
        return collectTagGroups(roots, tagMembers);
    }

    /**
     * For each tag, collects roots that are members and returns groups with 2+ members.
     *
     * @param roots all roots to match against tags
     * @param tagMembers tag ID to member item IDs
     * @return map of tag ID to grouped roots (only groups with MIN_GROUP_SIZE+ members)
     */
    private static Map<Identifier, List<Root>> collectTagGroups(List<Root> roots,
                                                                 Map<Identifier, Set<Identifier>> tagMembers) {
        Map<Identifier, List<Root>> groups = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Set<Identifier>> entry : tagMembers.entrySet()) {
            List<Root> members = collectRootsForTag(roots, entry.getValue());
            if (members.size() >= MIN_GROUP_SIZE) {
                groups.put(entry.getKey(), members);
            }
        }
        return groups;
    }

    /**
     * Collects roots whose itemId is in the given tag member set.
     *
     * @param roots all roots to filter
     * @param tagItems item IDs belonging to the tag
     * @return roots matching the tag (may be empty)
     */
    private static List<Root> collectRootsForTag(List<Root> roots, Set<Identifier> tagItems) {
        List<Root> members = new ArrayList<>();
        for (Root root : roots) {
            if (tagItems.contains(root.itemId())) {
                members.add(root);
            }
        }
        return members;
    }

    /**
     * Builds an index of tag ID to item IDs from multi-variant tagged recipe slots.
     * Only includes slots with size > 1 that have a tag ID, because single-item
     * tags don't benefit from grouping.
     *
     * @param recipes recipes to scan for tagged multi-variant slots
     * @return map of tag ID to set of member item IDs
     */
    static Map<Identifier, Set<Identifier>> buildTagIndex(List<RecipeInput> recipes) {
        Map<Identifier, Set<Identifier>> index = new HashMap<>();
        for (RecipeInput recipe : recipes) {
            indexRecipeSlots(index, recipe);
        }
        return index;
    }

    /**
     * Indexes all tagged multi-variant slots in a single recipe into the tag index.
     *
     * @param index tag index to populate (mutated)
     * @param recipe the recipe whose slots to scan
     */
    private static void indexRecipeSlots(Map<Identifier, Set<Identifier>> index, RecipeInput recipe) {
        List<Optional<Identifier>> tagIds = recipe.slotTagIds();
        List<Set<Identifier>> slots = recipe.ingredientAlternatives();
        for (int i = 0; i < tagIds.size(); i++) {
            indexSlotTag(index, tagIds.get(i), slots.get(i));
        }
    }

    /**
     * Indexes a single slot's tag into the tag index if it is a tagged multi-variant slot.
     *
     * @param index tag index to populate (mutated)
     * @param tagId the slot's tag ID (empty if untagged)
     * @param alts the slot's alternative item IDs
     */
    private static void indexSlotTag(Map<Identifier, Set<Identifier>> index,
                                      Optional<Identifier> tagId, Set<Identifier> alts) {
        if (tagId.isEmpty()) { return; }
        if (alts.size() <= 1) { return; }
        index.computeIfAbsent(tagId.get(), k -> new HashSet<>()).addAll(alts);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Strips the minecraft: prefix since the expression engine defaults to it.
     *
     * @param id the identifier to shorten
     * @return the path only for minecraft namespace, full string otherwise
     */
    private static String shortId(Identifier id) {
        return NS_MINECRAFT.equals(id.getNamespace()) ? id.getPath() : id.toString();
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
    private static void propagateValues(Set<Identifier> valued,
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
     * Returns the shared alternatives set if all slots are identical, null otherwise.
     * For multi-variant slots this is the full set (e.g. all plank types).
     * Used only for clustering (not for reverse derivation).
     *
     * @param recipe the recipe to inspect
     * @return the shared set of alternatives, or null if slots differ
     */
    private static Set<Identifier> homogenousInputs(RecipeInput recipe) {
        Set<Identifier> shared = null;
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            shared = mergeHomogenousSlot(shared, alts);
            if (shared == null) { return null; }
        }
        return shared;
    }

    /**
     * Merges a slot into the running homogenous check: returns the shared set
     * if this slot matches, or null if the slot is empty or divergent.
     *
     * @param shared the current shared set (null on first slot)
     * @param alts the slot's alternatives
     * @return the shared set if still homogenous, null otherwise
     */
    private static Set<Identifier> mergeHomogenousSlot(Set<Identifier> shared, Set<Identifier> alts) {
        if (alts.isEmpty()) { return null; }
        if (shared == null) { return alts; }
        return shared.equals(alts) ? shared : null;
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
    private static Set<Identifier> computeDownstream(Identifier root,
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
    private static List<Identifier> traceExampleChain(Identifier root,
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

    /**
     * Builds connected components from homogenous recipes.
     * Items linked by homogenous recipes form clusters where valuing
     * any one member propagates to all others via reverse derivation.
     *
     * @param homogenous recipes with a single repeated input item
     * @param allItems all items to include in clustering
     * @return map from each item to its cluster set (shared reference)
     */
    private static Map<Identifier, Set<Identifier>> buildClusters(
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
    private static Identifier pickCycleEntry(Set<Identifier> cluster,
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
