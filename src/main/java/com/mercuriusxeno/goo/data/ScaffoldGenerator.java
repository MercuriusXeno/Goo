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
        Map<Identifier, List<RecipeInput>> byOutput = GooValueDerivation.groupByOutput(recipes);
        Map<Identifier, Set<Identifier>> forwardDeps = buildForwardDeps(byOutput);
        Set<Identifier> recipeItems = collectRecipeItems(forwardDeps);
        Map<Identifier, Set<Identifier>> reverseDeps = buildReverseDeps(forwardDeps);

        Set<Identifier> valued = new HashSet<>(baseValues.keySet());
        propagateValues(valued, recipes, denied);

        List<Root> roots = new ArrayList<>();
        Set<Identifier> trueRootIds = collectTrueRoots(
                recipeItems, byOutput, valued, denied, reverseDeps, roots);

        Set<Identifier> simValued = simulateWithTrueRoots(valued, trueRootIds, recipes, denied);
        Set<Identifier> cycleItems = findUnresolved(recipeItems, simValued, denied);
        collectCycleRoots(cycleItems, recipes, reverseDeps, simValued, denied, roots);
        collectUnaccountedRoots(allKnownItems, simValued, cycleItems, denied, recipeItems, roots);

        roots.sort(Comparator.comparingInt((Root r) -> r.downstream().size())
                .thenComparingInt(r -> r.clusterSize())
                .reversed());
        return roots;
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
            Set<Identifier> downstream = computeDownstream(item, reverseDeps, valued, denied);
            List<Identifier> chain = traceExampleChain(item, reverseDeps, valued, denied, MAX_CHAIN_DEPTH);
            roots.add(new Root(item, downstream, 1, REASON_NO_RECIPE, chain));
        }
        return trueRootIds;
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

        List<RecipeInput> homogenous = recipes.stream()
                .filter(r -> r.soleInputItem() != null)
                .toList();
        Map<Identifier, Set<Identifier>> clusters = buildClusters(homogenous, cycleItems);
        Set<Identifier> visited = new HashSet<>();

        for (Identifier item : cycleItems) {
            if (visited.contains(item)) { continue; }
            Set<Identifier> cluster = clusters.getOrDefault(item, Set.of(item));
            if (!Collections.disjoint(cluster, visited)) { continue; }

            Identifier entry = pickCycleEntry(cluster, homogenous, reverseDeps, denied);
            visited.addAll(cluster);
            Root root = buildCycleRoot(entry, cluster, reverseDeps, simValued, denied);
            roots.add(root);
        }
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
        Set<Identifier> downstream = new HashSet<>();
        for (Identifier member : cluster) {
            downstream.addAll(computeDownstream(member, reverseDeps, simValued, denied));
        }
        for (Identifier member : cluster) {
            if (!member.equals(entry)) { downstream.add(member); }
        }
        downstream.remove(entry);

        List<Identifier> chain = traceExampleChain(entry, reverseDeps, simValued, denied, MAX_CHAIN_DEPTH);
        String reason = cluster.size() > 1
                ? REASON_CYCLE_PREFIX + cluster.size() + REASON_CYCLE_SUFFIX : REASON_CYCLE;
        return new Root(entry, downstream, cluster.size(), reason, chain);
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

        Set<Identifier> accounted = new HashSet<>(simValued);
        accounted.addAll(cycleItems);
        accounted.addAll(denied);

        for (Identifier item : allKnownItems) {
            if (accounted.contains(item)) { continue; }
            String reason = recipeItems.contains(item)
                    ? REASON_BROKEN_CHAIN : REASON_NO_CHAIN;
            roots.add(new Root(item, Set.of(), 1, reason, List.of()));
        }
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
            if (!bare) {
                lines.add(COMMENT_NO_ROOTS);
            }
            lines.add(JSON_CLOSE);
            return new ScaffoldResult(lines, 0);
        }

        Map<Identifier, List<Root>> tagGroups = buildTagGroups(roots, recipes);
        Set<Root> grouped = new HashSet<>();
        for (List<Root> group : tagGroups.values()) {
            grouped.addAll(group);
        }

        // Build ordered entries: tag groups first, then ungrouped roots
        List<ScaffoldEntry> entries = buildEntries(roots, tagGroups, grouped);

        if (!bare) {
            lines.add(COMMENT_HEADER_PREFIX
                    + entries.size() + COMMENT_HEADER_SUFFIX);
            lines.add(EMPTY_LINE);
        }

        for (int i = 0; i < entries.size(); i++) {
            ScaffoldEntry entry = entries.get(i);
            boolean last = i == entries.size() - 1;
            if (!bare) {
                lines.add(COMMENT_KEY_PREFIX + entry.commentKey + COMMENT_VALUE_SEP + entry.comment + COMMENT_CLOSE);
            }
            lines.add(KEY_PREFIX + entry.jsonKey + (last ? KEY_EMPTY_VALUE : KEY_EMPTY_VALUE_COMMA));
            if (!bare) {
                lines.add(EMPTY_LINE);
            }
        }

        lines.add(JSON_CLOSE);
        return new ScaffoldResult(lines, entries.size());
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
        Set<Identifier> emittedTags = new HashSet<>();

        for (Root root : roots) {
            if (grouped.contains(root)) {
                emitTagEntry(root, tagGroups, emittedTags, entries);
            } else {
                entries.add(buildRootEntry(root));
            }
        }
        entries.sort(Comparator.comparingInt(ScaffoldEntry::unblocks).reversed());
        return entries;
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

            Identifier tagId = entry.getKey();
            List<Root> members = entry.getValue();
            Set<Identifier> unionDownstream = new HashSet<>();
            for (Root member : members) {
                unionDownstream.addAll(member.downstream());
            }
            String tagShort = shortId(tagId);
            int unblocks = unionDownstream.size();
            String comment = members.size() + TAG_MEMBERS + unblocks;
            entries.add(new ScaffoldEntry(tagShort, comment, TAG_KEY_PREFIX + tagShort, unblocks));
        }
    }

    /**
     * Builds a scaffold entry for a single ungrouped root.
     *
     * @param root the root to build an entry for
     * @return a scaffold entry with comment and JSON key
     */
    private static ScaffoldEntry buildRootEntry(Root root) {
        StringBuilder chainDesc = new StringBuilder(CHAIN_DESC_CAPACITY);
        chainDesc.append(root.reason()).append(UNBLOCKS_PREFIX).append(root.downstream().size()).append(CHAIN_COLON);
        List<Identifier> sample = root.exampleChain();
        int shown = Math.min(sample.size(), MAX_CHAIN_SHOWN);
        for (int j = 0; j < shown; j++) {
            if (j > 0) { chainDesc.append(CHAIN_ARROW); }
            chainDesc.append(shortId(sample.get(j)));
        }
        if (sample.size() > shown) {
            chainDesc.append(CHAIN_TRUNCATE_PREFIX).append(sample.size() - shown).append(CHAIN_TRUNCATE_SUFFIX);
        }
        return new ScaffoldEntry(root.itemId().getPath(), chainDesc.toString(),
                shortId(root.itemId()), root.downstream().size());
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

        // For each tag, collect the roots that are members of it
        Map<Identifier, List<Root>> groups = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Set<Identifier>> entry : tagMembers.entrySet()) {
            List<Root> members = new ArrayList<>();
            for (Root root : roots) {
                if (entry.getValue().contains(root.itemId())) {
                    members.add(root);
                }
            }
            // Only group when 2+ roots share the tag
            if (members.size() >= MIN_GROUP_SIZE) {
                groups.put(entry.getKey(), members);
            }
        }
        return groups;
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
            List<Optional<Identifier>> tagIds = recipe.slotTagIds();
            List<Set<Identifier>> slots = recipe.ingredientAlternatives();
            for (int i = 0; i < tagIds.size(); i++) {
                if (tagIds.get(i).isEmpty()) { continue; }
                Set<Identifier> alts = slots.get(i);
                if (alts.size() <= 1) { continue; }
                Identifier tagId = tagIds.get(i).get();
                index.computeIfAbsent(tagId, k -> new HashSet<>()).addAll(alts);
            }
        }
        return index;
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
            Set<Identifier> inputs = new HashSet<>();
            for (RecipeInput recipe : entry.getValue()) {
                for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
                    inputs.addAll(alts);
                }
            }
            inputs.remove(entry.getKey()); // no self-loops
            if (!inputs.isEmpty()) {
                deps.put(entry.getKey(), inputs);
            }
        }
        return deps;
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
            changed = false;
            for (RecipeInput recipe : recipes) {
                Identifier output = recipe.output();
                if (denied.contains(output)) { continue; }

                boolean allInputsValued = recipe.ingredientAlternatives().stream()
                        .allMatch(alts -> alts.stream().anyMatch(valued::contains));
                if (allInputsValued && valued.add(output)) {
                    changed = true;
                }
            }
        }
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
            if (alts.isEmpty()) { return null; }
            if (shared == null) {
                shared = alts;
            } else if (!shared.equals(alts)) {
                return null;
            }
        }
        return shared;
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
            for (Identifier next : reverseDeps.getOrDefault(current, Set.of())) {
                if (!valued.contains(next) && !denied.contains(next) && downstream.add(next)) {
                    queue.add(next);
                }
            }
        }
        return downstream;
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
        Identifier current = root;
        for (int i = 0; i < maxDepth; i++) {
            Set<Identifier> next = reverseDeps.getOrDefault(current, Set.of());
            if (next.isEmpty()) { break; }
            // Follow the path with the most downstream reach
            Identifier best = null;
            int bestCount = NO_BEST;
            for (Identifier candidate : next) {
                if (valued.contains(candidate) || denied.contains(candidate)) { continue; }
                int count = reverseDeps.getOrDefault(candidate, Set.of()).size();
                if (count > bestCount) {
                    bestCount = count;
                    best = candidate;
                }
            }
            if (best == null) { break; }
            chain.add(best);
            current = best;
        }
        return chain;
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
        if (clusterA == null || clusterB == null || clusterA == clusterB) { return; }

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

        Map<Identifier, Long> factors = new HashMap<>();
        for (Identifier member : cluster) {
            factors.put(member, 1L);
        }

        // Propagate multiplication through homogenous recipes within the cluster
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RecipeInput recipe : homogenous) {
                Identifier input = recipe.soleInputItem();
                Identifier output = recipe.output();
                if (input == null || !cluster.contains(input) || !cluster.contains(output)) { continue; }
                long inputFactor = factors.getOrDefault(input, 1L);
                int inputCount = recipe.ingredientAlternatives().size();
                long outputFactor = inputFactor * recipe.resultCount() / inputCount;
                if (outputFactor > factors.getOrDefault(output, 1L)) {
                    factors.put(output, outputFactor);
                    changed = true;
                }
            }
        }

        // Highest multiplication factor = smallest unit. Tiebreak by fan-out.
        return cluster.stream()
                .filter(id -> !denied.contains(id))
                .max(Comparator.<Identifier>comparingLong(id -> factors.getOrDefault(id, 1L))
                        .thenComparingInt(id -> reverseDeps.getOrDefault(id, Set.of()).size()))
                .orElse(cluster.iterator().next());
    }
}
