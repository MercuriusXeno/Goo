package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import java.util.*;

/**
 * Parses and applies _conversions / _post_conversions blocks from base_values.json.
 * Handles target resolution against pseudo-tags and parallel source pairing.
 */
final class GooConversionLoader {

    private static final String KEY_CONVERSIONS = "_conversions";
    private static final String KEY_POST_CONVERSIONS = "_post_conversions";
    private static final String PREFIX_TAG = "#";

    private static final String LOG_CONV_TARGET_EMPTY = "Conversion target {} resolved to no items";
    private static final String LOG_CONV_SOURCE_EMPTY = "Parallel source {} resolved to no items";

    private GooConversionLoader() {
    }

    /**
     * Parses both _conversions and _post_conversions blocks.
     *
     * @param json  the root JSON object containing conversion blocks
     * @param state mutable parsing state
     */
    static void parseConversions(JsonObject json, GooValueLoader.ParseState state) {
        state.preConversions = parseConversionBlock(json, KEY_CONVERSIONS, state);
        state.postConversions = parseConversionBlock(json, KEY_POST_CONVERSIONS, state);
    }

    /**
     * Parses a single conversion block by key name, with constant resolution.
     *
     * @param json  the root JSON object
     * @param key   the block key (e.g. _conversions)
     * @param state parsing state containing constants
     * @return parsed conversions, or null if the block is absent
     */
    private static GooConversion.ParsedConversions parseConversionBlock(JsonObject json, String key,
                                                                        GooValueLoader.ParseState state) {
        if (!json.has(key)) {
            return null;
        }
        JsonObject block = json.getAsJsonObject(key);
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            entries.put(entry.getKey(), entry.getValue().getAsString());
        }
        return GooConversion.parseBlock(entries, state.constants, state.treeConstants);
    }

    /**
     * Applies a parsed conversion set to a values map.
     *
     * @param parsed     the parsed conversions to apply (may be null)
     * @param values     the mutable values map to modify
     * @param pseudoTags the pseudo-tag map for target resolution
     */
    static void applyConversions(GooConversion.ParsedConversions parsed,
                                 Map<Identifier, GooValue> values,
                                 Map<String, Set<Identifier>> pseudoTags) {
        if (parsed == null) {
            return;
        }
        for (GooConversion.Assignment assignment : parsed.assignments()) {
            applyOneConversion(assignment, parsed, values, pseudoTags);
        }
    }

    /**
     * Resolves targets/sources for a single conversion assignment and applies it.
     *
     * @param assignment the conversion assignment to apply
     * @param parsed     the full parsed conversion set (for formulas/additives)
     * @param values     the mutable values map to modify
     * @param pseudoTags the pseudo-tag map for target resolution
     */
    private static void applyOneConversion(GooConversion.Assignment assignment,
                                           GooConversion.ParsedConversions parsed,
                                           Map<Identifier, GooValue> values,
                                           Map<String, Set<Identifier>> pseudoTags) {
        List<Identifier> targetItems = resolveConversionTarget(assignment.target(), pseudoTags);
        if (targetItems.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_CONV_TARGET_EMPTY, assignment.target());
            }
            return;
        }
        List<Identifier> sourceItems = resolveParallelSource(assignment, pseudoTags);
        if (assignment.parallelSource() != null && sourceItems == null) {
            return;
        }
        GooConversion.applyAssignment(values, targetItems, sourceItems,
                assignment, parsed.formulas(), parsed.additives());
    }

    /**
     * Resolves the parallel source for a conversion assignment.
     *
     * @param assignment the conversion assignment
     * @param pseudoTags the pseudo-tag map for target resolution
     * @return resolved source items, or null if no source or resolution failed
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private static List<Identifier> resolveParallelSource(GooConversion.Assignment assignment,
                                                          Map<String, Set<Identifier>> pseudoTags) {
        if (assignment.parallelSource() == null) {
            return null;
        }
        List<Identifier> sourceItems = resolveConversionTarget(assignment.parallelSource(), pseudoTags);
        if (sourceItems.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_CONV_SOURCE_EMPTY, assignment.parallelSource());
            }
            return null;
        }
        return sourceItems;
    }

    /**
     * Resolves a conversion target (item ID or #tag) to an ordered list of item IDs.
     *
     * @param target     the target string (item ID or #tag reference)
     * @param pseudoTags the pseudo-tag map
     * @return ordered list of resolved item IDs
     */
    private static List<Identifier> resolveConversionTarget(String target,
                                                            Map<String, Set<Identifier>> pseudoTags) {
        if (target.startsWith(PREFIX_TAG)) {
            String name = target.substring(1);
            Set<Identifier> members = pseudoTags.get(name);
            if (members == null) {
                members = pseudoTags.get(Identifier.parse(name).getPath());
            }
            return members != null ? new ArrayList<>(members) : List.of();
        }
        return List.of(Identifier.parse(target));
    }
}
