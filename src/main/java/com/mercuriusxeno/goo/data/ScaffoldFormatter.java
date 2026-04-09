package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON scaffold output formatting and entry building. Tag grouping
 * lives in {@link ScaffoldTagIndex}.
 * Produces human-readable scaffold files showing which items need manual valuation.
 * All methods are stateless; the class is not instantiable.
 */
final class ScaffoldFormatter {

    /** Maximum items shown in a chain description before truncating. */
    private static final int MAX_CHAIN_SHOWN = 5;
    /** Initial capacity for chain description StringBuilder. */
    private static final int CHAIN_DESC_CAPACITY = 32;

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
    /** Empty line separator in scaffold output. */
    private static final String EMPTY_LINE = "";

    private ScaffoldFormatter() {}

    /**
     * Generates a scaffold JSON showing which items need manual valuation.
     *
     * @param roots   the roots to generate scaffold for
     * @param recipes recipes providing tag metadata for grouping
     * @param bare    if true, emit only root keys with empty values (no comments)
     * @return lines suitable for writing to a file, plus root count
     */
    static ScaffoldGenerator.ScaffoldResult generateScaffold(List<ScaffoldGenerator.Root> roots,
                                                              List<RecipeInput> recipes, boolean bare) {
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
    private static ScaffoldGenerator.ScaffoldResult emitPopulatedScaffold(
            List<String> lines, List<ScaffoldGenerator.Root> roots,
            List<RecipeInput> recipes, boolean bare) {
        List<ScaffoldEntry> entries = buildAllEntries(roots, recipes);
        emitScaffoldHeader(lines, entries.size(), bare);
        emitScaffoldEntries(lines, entries, bare);
        lines.add(JSON_CLOSE);
        return new ScaffoldGenerator.ScaffoldResult(lines, entries.size());
    }

    /**
     * Emits the scaffold for an empty root list (no roots found).
     *
     * @param lines output line accumulator (mutated)
     * @param bare if true, omit comment lines
     * @return scaffold result with zero roots
     */
    private static ScaffoldGenerator.ScaffoldResult emitEmptyScaffold(List<String> lines, boolean bare) {
        if (!bare) {
            lines.add(COMMENT_NO_ROOTS);
        }
        lines.add(JSON_CLOSE);
        return new ScaffoldGenerator.ScaffoldResult(lines, 0);
    }

    /**
     * Builds all scaffold entries from roots with tag grouping applied.
     *
     * @param roots all roots in priority order
     * @param recipes recipes for tag grouping
     * @return ordered scaffold entries
     */
    private static List<ScaffoldEntry> buildAllEntries(List<ScaffoldGenerator.Root> roots,
                                                        List<RecipeInput> recipes) {
        Map<Identifier, List<ScaffoldGenerator.Root>> tagGroups = ScaffoldTagIndex.buildTagGroups(roots, recipes);
        Set<ScaffoldGenerator.Root> grouped = collectGroupedRoots(tagGroups);
        return buildEntries(roots, tagGroups, grouped);
    }

    /**
     * Collects all roots that belong to at least one tag group.
     *
     * @param tagGroups tag ID to grouped root members
     * @return set of roots assigned to any tag group
     */
    private static Set<ScaffoldGenerator.Root> collectGroupedRoots(
            Map<Identifier, List<ScaffoldGenerator.Root>> tagGroups) {
        Set<ScaffoldGenerator.Root> grouped = new HashSet<>();
        for (List<ScaffoldGenerator.Root> group : tagGroups.values()) {
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
            lines.add(COMMENT_KEY_PREFIX + entry.commentKey() + COMMENT_VALUE_SEP + entry.comment() + COMMENT_CLOSE);
        }
        lines.add(KEY_PREFIX + entry.jsonKey() + (last ? KEY_EMPTY_VALUE : KEY_EMPTY_VALUE_COMMA));
        if (!bare) {
            lines.add(EMPTY_LINE);
        }
    }

    /** A single scaffold entry, either an individual root or a tag group. */
    record ScaffoldEntry(String commentKey, String comment, String jsonKey, int unblocks) {}

    /**
     * Builds the ordered list of scaffold entries, sorted by unblock count descending.
     *
     * @param roots all roots in priority order
     * @param tagGroups tag ID to grouped root members
     * @param grouped set of roots already assigned to a tag group
     * @return ordered scaffold entries
     */
    private static List<ScaffoldEntry> buildEntries(List<ScaffoldGenerator.Root> roots,
            Map<Identifier, List<ScaffoldGenerator.Root>> tagGroups,
            Set<ScaffoldGenerator.Root> grouped) {
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
    private static void dispatchRootEntries(List<ScaffoldGenerator.Root> roots,
                                             Map<Identifier, List<ScaffoldGenerator.Root>> tagGroups,
                                             Set<ScaffoldGenerator.Root> grouped,
                                             List<ScaffoldEntry> entries) {
        Set<Identifier> emittedTags = new HashSet<>();
        for (ScaffoldGenerator.Root root : roots) {
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
    private static void emitTagEntry(ScaffoldGenerator.Root root,
                                      Map<Identifier, List<ScaffoldGenerator.Root>> tagGroups,
                                      Set<Identifier> emittedTags, List<ScaffoldEntry> entries) {
        for (Map.Entry<Identifier, List<ScaffoldGenerator.Root>> entry : tagGroups.entrySet()) {
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
    private static ScaffoldEntry buildTagScaffoldEntry(Identifier tagId,
                                                        List<ScaffoldGenerator.Root> members) {
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
    private static Set<Identifier> computeTagUnionDownstream(List<ScaffoldGenerator.Root> members) {
        Set<Identifier> unionDownstream = new HashSet<>();
        for (ScaffoldGenerator.Root member : members) {
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
    private static ScaffoldEntry buildRootEntry(ScaffoldGenerator.Root root) {
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
    private static String buildChainDescription(ScaffoldGenerator.Root root) {
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
     * Strips the minecraft: prefix since the expression engine defaults to it.
     *
     * @param id the identifier to shorten
     * @return the path only for minecraft namespace, full string otherwise
     */
    static String shortId(Identifier id) {
        return NS_MINECRAFT.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
