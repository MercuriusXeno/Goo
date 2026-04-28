package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates base_values.json for authoring mistakes: unknown constants,
 * forward-referencing items, malformed formulas, and missing $ prefixes.
 * Pure-static utility with no instance state.
 */
final class GooValueValidator {

    // --- JSON keys and prefixes (mirrored from registry for validation context) ---

    /**
     * Prefix for internal JSON keys (constants, groups, conversions).
     */
    private static final String PREFIX_INTERNAL = "_";
    /**
     * Prefix for tag/pseudo-tag references.
     */
    private static final String PREFIX_TAG = "#";
    /**
     * Denied value string in base_values.json.
     */
    private static final String VALUE_DENIED = "denied";
    /**
     * Suffix key for the constants block.
     */
    private static final String CONSTANTS_SUFFIX = "_constants";
    /**
     * Suffix key for the groups block.
     */
    private static final String GROUPS_SUFFIX = "_groups";
    /**
     * Dot separator for validation context paths.
     */
    private static final String DOT = ".";
    /**
     * Constants key prefix for validation context.
     */
    private static final String CTX_CONSTANTS = "_constants.";
    /**
     * Groups key prefix for validation context.
     */
    private static final String CTX_GROUPS = "_groups.";

    // --- Validation warning messages ---

    /**
     * Warning suffix: expected an array of item IDs.
     */
    private static final String WARN_EXPECTED_ARRAY = ": expected an array of item IDs";

    private GooValueValidator() {
    }

    /**
     * Walks the JSON checking expression tokens for mistakes.
     *
     * @param json     the root JSON object to validate
     * @param warnings accumulator for validation warnings
     */
    static void validateJson(JsonObject json, List<String> warnings) {
        Set<String> knownConstants = validateConstantsSection(json, warnings);
        Set<String> knownItems = validateBaseSection(json, knownConstants, warnings);
        validateGroupsSection(json, knownItems, warnings);
        GooConversionValidator.validateConversionsSection(json, knownConstants, warnings);
    }

    /**
     * Validates the _constants block: checks expression tokens within each constant definition.
     *
     * @param json     the root JSON object
     * @param warnings accumulator for validation warnings
     * @return the set of known constant names, in definition order
     */
    private static Set<String> validateConstantsSection(JsonObject json, List<String> warnings) {
        Set<String> knownConstants = new LinkedHashSet<>();
        if (!json.has(CONSTANTS_SUFFIX)) {
            return knownConstants;
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(CONSTANTS_SUFFIX).entrySet()) {
            validateConstantEntry(entry, knownConstants, warnings);
            knownConstants.add(entry.getKey());
        }
        return knownConstants;
    }

    /**
     * Validates expression tokens within a single constant definition.
     *
     * @param entry          the constant entry to validate
     * @param knownConstants known constant names defined so far
     * @param warnings       accumulator for validation warnings
     */
    private static void validateConstantEntry(Map.Entry<String, JsonElement> entry,
                                              Set<String> knownConstants, List<String> warnings) {
        if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
            GooValueTokenValidator.validateExprTokens(entry.getValue().getAsString(), knownConstants,
                    new LinkedHashSet<>(), CTX_CONSTANTS + entry.getKey(), warnings);
        }
    }

    /**
     * Validates individual item entries (non-internal, non-tag keys) for expression correctness.
     *
     * @param json           the root JSON object
     * @param knownConstants known constant names from the constants section
     * @param warnings       accumulator for validation warnings
     * @return the set of known item keys, in definition order
     */
    private static Set<String> validateBaseSection(JsonObject json, Set<String> knownConstants,
                                                   List<String> warnings) {
        Set<String> knownItems = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (isInternalOrTag(entry.getKey())) {
                continue;
            }
            validateBaseEntry(entry, knownConstants, knownItems, warnings);
            knownItems.add(entry.getKey());
        }
        return knownItems;
    }

    /**
     * Returns true if the key is an internal (_) or tag (#) prefix.
     *
     * @param key the JSON key to check
     * @return true if the key starts with _ or #
     */
    private static boolean isInternalOrTag(String key) {
        return key.startsWith(PREFIX_INTERNAL) || key.startsWith(PREFIX_TAG);
    }

    /**
     * Validates a single base-section entry for expression correctness.
     *
     * @param entry          the JSON entry to validate
     * @param knownConstants known constant names
     * @param knownItems     known item IDs defined before this entry
     * @param warnings       accumulator for validation warnings
     */
    private static void validateBaseEntry(Map.Entry<String, JsonElement> entry,
                                          Set<String> knownConstants, Set<String> knownItems,
                                          List<String> warnings) {
        String itemKey = entry.getKey();
        JsonElement value = entry.getValue();
        if (value.isJsonObject()) {
            validateValueObject(value.getAsJsonObject(), knownConstants, knownItems, itemKey, warnings);
        } else if (isNonDeniedString(value)) {
            GooValueTokenValidator.validateExprTokens(value.getAsString(), knownConstants, knownItems, itemKey, warnings);
        }
    }

    /**
     * Returns true if the element is a string primitive that is not "denied".
     *
     * @param value the JSON element to check
     * @return true if the element is a non-denied string
     */
    private static boolean isNonDeniedString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !VALUE_DENIED.equals(value.getAsString());
    }

    /**
     * Validates the _groups block: checks that each group is an array, and registers members as known items.
     *
     * @param json       the root JSON object
     * @param knownItems mutable set of known item keys (group members are added)
     * @param warnings   accumulator for validation warnings
     */
    private static void validateGroupsSection(JsonObject json, Set<String> knownItems, List<String> warnings) {
        if (!json.has(GROUPS_SUFFIX)) {
            return;
        }
        JsonObject groups = json.getAsJsonObject(GROUPS_SUFFIX);
        for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
            validateGroupEntry(group, knownItems, warnings);
        }
    }

    /**
     * Validates a single group entry, registering its members as known items.
     *
     * @param group      the group entry to validate
     * @param knownItems mutable set of known item keys
     * @param warnings   accumulator for validation warnings
     */
    private static void validateGroupEntry(Map.Entry<String, JsonElement> group,
                                           Set<String> knownItems, List<String> warnings) {
        if (!group.getValue().isJsonArray()) {
            warnings.add(CTX_GROUPS + group.getKey() + WARN_EXPECTED_ARRAY);
            return;
        }
        for (JsonElement item : group.getValue().getAsJsonArray()) {
            knownItems.add(item.getAsString());
        }
    }

    /**
     * Validates per-type expressions in a value object like { "metal": "$iron * 3" }.
     *
     * @param obj            the value JSON object to validate
     * @param knownConstants known constant names
     * @param knownItems     known item IDs (defined before this entry)
     * @param context        the item key for error messages
     * @param warnings       accumulator for validation warnings
     */
    private static void validateValueObject(JsonObject obj, Set<String> knownConstants,
                                            Set<String> knownItems, String context,
                                            List<String> warnings) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                GooValueTokenValidator.validateExprTokens(entry.getValue().getAsString(), knownConstants, knownItems,
                        context + DOT + entry.getKey(), warnings);
            }
        }
    }
}
