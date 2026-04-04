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
public class ScaffoldGenerator {

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
     * Finds all root nodes in the recipe graph that need manual valuation.
     *
     * @param recipes    all known recipes
     * @param baseValues currently valued items
     * @param denied     denied items (excluded from analysis)
     * @return roots sorted by downstream impact (highest first)
     */
    public static List<Root> findRoots(List<RecipeInput> recipes,
                                       Map<Identifier, GooValue> baseValues,
                                       Set<Identifier> denied) {
        Map<Identifier, List<RecipeInput>> byOutput = GooValueDerivation.groupByOutput(recipes);

        // Build forward graph: output -> all input items needed
        Map<Identifier, Set<Identifier>> forwardDeps = buildForwardDeps(byOutput);

        // Collect all items mentioned anywhere (as input or output)
        Set<Identifier> allItems = new HashSet<>(forwardDeps.keySet());
        forwardDeps.values().forEach(allItems::addAll);

        // Build reverse graph: input -> items it directly enables
        Map<Identifier, Set<Identifier>> reverseDeps = buildReverseDeps(forwardDeps);

        // Items that are valued (base or derivable from base)
        Set<Identifier> valued = new HashSet<>(baseValues.keySet());

        // Simulate value propagation (forward + homogenous reverse)
        propagateValues(valued, recipes, denied);

        // Build value clusters: groups connected by homogenous recipes.
        // Valuing any one member propagates to the whole cluster.
        List<RecipeInput> homogenous = recipes.stream()
                .filter(ScaffoldGenerator::isHomogenousInput)
                .toList();
        Set<Identifier> eligible = new HashSet<>(allItems);
        eligible.removeAll(valued);
        Map<Identifier, Set<Identifier>> clusters = buildClusters(homogenous, eligible);

        // For each unvalued cluster, pick the most-multiplied member as entry point
        List<Root> roots = new ArrayList<>();
        Set<Identifier> visited = new HashSet<>();

        for (Identifier item : allItems) {
            if (valued.contains(item) || denied.contains(item) || visited.contains(item)) continue;

            Set<Identifier> cluster = clusters.getOrDefault(item, Set.of(item));
            if (!Collections.disjoint(cluster, visited)) continue;

            // Pick the cluster member with the highest multiplication factor
            Identifier entry = pickMostMultiplied(cluster, homogenous, reverseDeps, valued, denied);
            visited.addAll(cluster);

            // Downstream = everything reachable from ANY cluster member,
            // since valuing the entry propagates to the whole cluster.
            Set<Identifier> downstream = new HashSet<>();
            for (Identifier member : cluster) {
                downstream.addAll(computeDownstream(member, reverseDeps, valued, denied));
            }
            // Cluster members themselves are also covered (minus entry)
            for (Identifier member : cluster) {
                if (!member.equals(entry) && !valued.contains(member) && !denied.contains(member)) {
                    downstream.add(member);
                }
            }
            downstream.remove(entry);
            if (downstream.isEmpty()) continue;

            List<Identifier> chain = traceExampleChain(entry, reverseDeps, valued, denied, 5);
            String reason = cluster.size() > 1 ? "value cluster (" + cluster.size() + " items)" : "no recipe";
            roots.add(new Root(entry, downstream, cluster.size(), reason, chain));
        }

        // Sort by downstream impact (desc), then cluster size (desc) as tiebreak
        roots.sort(Comparator.comparingInt((Root r) -> r.downstream().size())
                .thenComparingInt(r -> r.clusterSize())
                .reversed());
        return roots;
    }

    /**
     * Result of scaffold generation: the file lines and the root count.
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
        List<String> lines = new ArrayList<>();
        lines.add("{");

        if (roots.isEmpty()) {
            lines.add("    \"_comment_scaffold\": \"No unvalued roots found. All recipe chains are anchored.\"");
            lines.add("}");
            return new ScaffoldResult(lines, 0);
        }

        Map<Identifier, List<Root>> tagGroups = buildTagGroups(roots, recipes);
        Set<Root> grouped = new HashSet<>();
        for (List<Root> group : tagGroups.values()) {
            grouped.addAll(group);
        }

        // Build ordered entries: tag groups first, then ungrouped roots
        List<ScaffoldEntry> entries = buildEntries(roots, tagGroups, grouped);

        lines.add("    \"_comment_scaffold\": \"Root nodes needing manual valuation. "
                + entries.size() + " root(s) found. Fill in goo types, then paste into base_values.json.\",");
        lines.add("");

        for (int i = 0; i < entries.size(); i++) {
            ScaffoldEntry entry = entries.get(i);
            boolean last = (i == entries.size() - 1);
            lines.add("    \"_comment_" + entry.commentKey + "\": \"" + entry.comment + "\",");
            lines.add("    \"" + entry.jsonKey + "\": { }" + (last ? "" : ","));
            lines.add("");
        }

        lines.add("}");
        return new ScaffoldResult(lines, entries.size());
    }

    /** A single scaffold entry, either an individual root or a tag group. */
    private record ScaffoldEntry(String commentKey, String comment, String jsonKey) {}

    /** Builds the ordered list of scaffold entries from tag groups and ungrouped roots. */
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
        return entries;
    }

    /** Emits a tag group entry the first time a member of that group is encountered. */
    private static void emitTagEntry(Root root, Map<Identifier, List<Root>> tagGroups,
            Set<Identifier> emittedTags, List<ScaffoldEntry> entries) {
        for (Map.Entry<Identifier, List<Root>> entry : tagGroups.entrySet()) {
            if (!entry.getValue().contains(root)) continue;
            if (!emittedTags.add(entry.getKey())) continue;

            Identifier tagId = entry.getKey();
            List<Root> members = entry.getValue();
            Set<Identifier> unionDownstream = new HashSet<>();
            for (Root member : members) {
                unionDownstream.addAll(member.downstream());
            }
            String tagShort = shortId(tagId);
            String comment = members.size() + " tag members. Unblocks " + unionDownstream.size();
            entries.add(new ScaffoldEntry(tagShort, comment, "#" + tagShort));
        }
    }

    /** Builds a scaffold entry for a single ungrouped root. */
    private static ScaffoldEntry buildRootEntry(Root root) {
        StringBuilder chainDesc = new StringBuilder();
        chainDesc.append(root.reason()).append(". Unblocks ").append(root.downstream().size()).append(": ");
        List<Identifier> sample = root.exampleChain();
        int shown = Math.min(sample.size(), 5);
        for (int j = 0; j < shown; j++) {
            if (j > 0) chainDesc.append(" -> ");
            chainDesc.append(shortId(sample.get(j)));
        }
        if (sample.size() > shown) {
            chainDesc.append(" ... +").append(sample.size() - shown).append(" more");
        }
        return new ScaffoldEntry(root.itemId().getPath(), chainDesc.toString(), shortId(root.itemId()));
    }

    /**
     * Groups roots by tag: roots whose itemId appears in a multi-variant tagged slot.
     * A root qualifies if its itemId is a member of some slot with size > 1 that has a tag ID.
     *
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
            if (members.size() >= 2) {
                groups.put(entry.getKey(), members);
            }
        }
        return groups;
    }

    /**
     * Builds an index of tag ID to item IDs from multi-variant tagged recipe slots.
     * Only includes slots with size > 1 that have a tag ID, because single-item
     * tags don't benefit from grouping.
     */
    static Map<Identifier, Set<Identifier>> buildTagIndex(List<RecipeInput> recipes) {
        Map<Identifier, Set<Identifier>> index = new HashMap<>();
        for (RecipeInput recipe : recipes) {
            List<Optional<Identifier>> tagIds = recipe.slotTagIds();
            List<Set<Identifier>> slots = recipe.ingredientAlternatives();
            for (int i = 0; i < tagIds.size(); i++) {
                if (tagIds.get(i).isEmpty()) continue;
                Set<Identifier> alts = slots.get(i);
                if (alts.size() <= 1) continue;
                Identifier tagId = tagIds.get(i).get();
                index.computeIfAbsent(tagId, k -> new HashSet<>()).addAll(alts);
            }
        }
        return index;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /** Strips the minecraft: prefix since the expression engine defaults to it. */
    private static String shortId(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    /** Builds output -> all inputs dependency graph from recipes. */
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

    /** Builds input -> outputs reverse dependency graph. */
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
     * Simulates value propagation through recipes.
     * Forward: all inputs valued -> output valued.
     * Reverse: output valued + homogenous inputs (single item type) -> input valued.
     * Mixed-input recipes can't reverse because you can't split the output value
     * across heterogeneous ingredients.
     */
    private static void propagateValues(Set<Identifier> valued,
                                        List<RecipeInput> recipes,
                                        Set<Identifier> denied) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RecipeInput recipe : recipes) {
                Identifier output = recipe.output();
                if (denied.contains(output)) continue;

                // Forward: all inputs valued -> output valued
                boolean allInputsValued = recipe.ingredientAlternatives().stream()
                        .allMatch(alts -> alts.stream().anyMatch(valued::contains));
                if (allInputsValued && valued.add(output)) {
                    changed = true;
                }

                // Reverse: output valued + single homogenous input -> input valued
                if (valued.contains(output)) {
                    Identifier sole = soleInputItem(recipe);
                    if (sole != null && !denied.contains(sole) && valued.add(sole)) {
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * True if every ingredient slot is the same single item.
     * Multi-variant slots (tag-based like "any planks") don't count because
     * you can't reverse-derive a specific variant's value from the output.
     */
    private static boolean isHomogenousInput(RecipeInput recipe) {
        return soleInputItem(recipe) != null;
    }

    /** Returns the single input item if all slots use the same one, null otherwise. */
    private static Identifier soleInputItem(RecipeInput recipe) {
        Identifier sole = null;
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            if (alts.size() != 1) return null;
            Identifier item = alts.iterator().next();
            if (sole == null) {
                sole = item;
            } else if (!sole.equals(item)) {
                return null;
            }
        }
        return sole;
    }

    /**
     * Returns the shared alternatives set if all slots are identical, null otherwise.
     * For multi-variant slots this is the full set (e.g. all plank types).
     * Used only for clustering (not for reverse derivation).
     */
    private static Set<Identifier> homogenousInputs(RecipeInput recipe) {
        Set<Identifier> shared = null;
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            if (alts.isEmpty()) return null;
            if (shared == null) {
                shared = alts;
            } else if (!shared.equals(alts)) {
                return null;
            }
        }
        return shared;
    }

    /** Computes all downstream items reachable from a root via the reverse graph. */
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

    /** Traces an example derivation chain from a root, following the highest-impact path. */
    private static List<Identifier> traceExampleChain(Identifier root,
                                                       Map<Identifier, Set<Identifier>> reverseDeps,
                                                       Set<Identifier> valued,
                                                       Set<Identifier> denied,
                                                       int maxDepth) {
        List<Identifier> chain = new ArrayList<>();
        Identifier current = root;
        for (int i = 0; i < maxDepth; i++) {
            Set<Identifier> next = reverseDeps.getOrDefault(current, Set.of());
            if (next.isEmpty()) break;
            // Follow the path with the most downstream reach
            Identifier best = null;
            int bestCount = -1;
            for (Identifier candidate : next) {
                if (valued.contains(candidate) || denied.contains(candidate)) continue;
                int count = reverseDeps.getOrDefault(candidate, Set.of()).size();
                if (count > bestCount) {
                    bestCount = count;
                    best = candidate;
                }
            }
            if (best == null) break;
            chain.add(best);
            current = best;
        }
        return chain;
    }

    /**
     * Builds connected components from homogenous recipes.
     * Items linked by homogenous recipes form clusters where valuing
     * any one member propagates to all others via reverse derivation.
     */
    private static Map<Identifier, Set<Identifier>> buildClusters(
            List<RecipeInput> homogenous, Set<Identifier> allItems) {
        // Union-find via set merging
        Map<Identifier, Set<Identifier>> membership = new HashMap<>();
        for (Identifier item : allItems) {
            Set<Identifier> singleton = new HashSet<>();
            singleton.add(item);
            membership.put(item, singleton);
        }
        for (RecipeInput recipe : homogenous) {
            Identifier input = soleInputItem(recipe);
            Identifier output = recipe.output();
            if (input == null || input.equals(output)) continue;
            Set<Identifier> inputCluster = membership.get(input);
            Set<Identifier> outputCluster = membership.get(output);
            if (inputCluster == null || outputCluster == null || inputCluster == outputCluster) continue;
            if (inputCluster.size() < outputCluster.size()) {
                outputCluster.addAll(inputCluster);
                for (Identifier id : inputCluster) membership.put(id, outputCluster);
            } else {
                inputCluster.addAll(outputCluster);
                for (Identifier id : outputCluster) membership.put(id, inputCluster);
            }
        }
        return membership;
    }

    /**
     * Picks the cluster member with the highest multiplication factor that
     * is also used as an ingredient in other recipes. End products (high
     * multiplier but no fan-out) are poor anchors. Items like sticks (16x
     * from log, used in dozens of recipes) and nuggets (9x from ingot,
     * used in several) are ideal.
     */
    private static Identifier pickMostMultiplied(Set<Identifier> cluster,
                                                  List<RecipeInput> homogenous,
                                                  Map<Identifier, Set<Identifier>> reverseDeps,
                                                  Set<Identifier> valued,
                                                  Set<Identifier> denied) {
        if (cluster.size() == 1) return cluster.iterator().next();

        Map<Identifier, Long> factors = new HashMap<>();
        for (Identifier member : cluster) {
            factors.put(member, 1L);
        }

        // Propagate multiplication through homogenous recipes within the cluster
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RecipeInput recipe : homogenous) {
                Identifier input = soleInputItem(recipe);
                Identifier output = recipe.output();
                if (input == null || !cluster.contains(input) || !cluster.contains(output)) continue;
                long inputFactor = factors.getOrDefault(input, 1L);
                int inputCount = recipe.ingredientAlternatives().size();
                // 2 planks -> 4 sticks = ratio of 4/2 = 2x per plank
                long outputFactor = inputFactor * recipe.resultCount() / inputCount;
                if (outputFactor > factors.getOrDefault(output, 1L)) {
                    factors.put(output, outputFactor);
                    changed = true;
                }
            }
        }

        // Score = multiplication factor, but only for items that fan out
        // (are used as input in at least one recipe). End products score 0.
        // Score = multiplication factor, but only for non-denied items with fan-out.
        // Denied items can be in the cluster but should not be the entry point.
        return cluster.stream()
                .filter(id -> !denied.contains(id))
                .max(Comparator.<Identifier>comparingLong(id -> {
                            int fanOut = reverseDeps.getOrDefault(id, Set.of()).size();
                            return fanOut > 0 ? factors.getOrDefault(id, 1L) : 0L;
                        })
                        .thenComparingInt(id -> reverseDeps.getOrDefault(id, Set.of()).size()))
                .orElse(cluster.iterator().next());
    }
}
