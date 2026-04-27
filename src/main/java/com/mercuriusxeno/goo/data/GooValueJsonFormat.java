package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JSON serialization for {@link GooValue}: parses value objects with optional
 * {@code $constant} reference substitution, and serializes them back to JSON.
 * Expression evaluation is delegated to {@link GooIntExpressionEvaluator}.
 */
final class GooValueJsonFormat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Log warning for unknown goo type in value JSON.
     */
    private static final String WARN_UNKNOWN_TYPE = "Unknown goo type in values: {}";

    /**
     * Utility class, not instantiable.
     */
    private GooValueJsonFormat() {
    }

    /**
     * Parses a flat goo value JSON object with integer-only values (no expressions).
     * Used when loading the pre-resolved effective cache file.
     *
     * @param json JSON object whose keys are goo type names and values are integers
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json) {
        return parseGooValue(json, Map.of(), null);
    }

    /**
     * Parses a goo value JSON object, resolving {@code $constant} references via the provided
     * symbol table.
     *
     * @param json      JSON object whose keys are goo type names and values are ints or expressions
     * @param constants symbol table mapping constant names to integer values
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json, Map<String, Integer> constants) {
        return parseGooValue(json, constants, null);
    }

    /**
     * Parses a goo value JSON object with item reference support. Per-type values
     * can use dot notation (e.g. {@code minecraft:coal.blaze}) to extract a single
     * type from another item's value as an integer.
     *
     * @param json       JSON object whose keys are goo type names and values are ints or expressions
     * @param constants  symbol table mapping constant names to integer values
     * @param baseValues item values for dot-notation lookups (may be null)
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json, Map<String, Integer> constants,
                                  Map<Identifier, GooValue> baseValues) {
        return parseGooValue(json, constants, baseValues, Map.of());
    }

    /**
     * Parses a goo value JSON object with item and tree constant dot-notation support.
     * Per-type values can use {@code $tree.type} to extract a single type from a tree constant.
     *
     * @param json          JSON object whose keys are goo type names and values are ints or expressions
     * @param constants     symbol table mapping constant names to integer values
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table for $name.type lookups
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json, Map<String, Integer> constants,
                                  Map<Identifier, GooValue> baseValues,
                                  Map<String, GooValue> treeConstants) {
        Map<GooType, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            parseTypeEntry(entry, constants, baseValues, treeConstants, map);
        }
        return new GooValue(map);
    }

    /**
     * Parses a single JSON entry as a goo type/amount pair. Skips unknown type names
     * with a warning rather than failing the entire parse.
     *
     * @param entry         the JSON map entry (type name to value expression)
     * @param constants     scalar constant symbol table
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @param map           output map to populate with the parsed type and amount
     */
    private static void parseTypeEntry(Map.Entry<String, JsonElement> entry,
                                       Map<String, Integer> constants,
                                       Map<Identifier, GooValue> baseValues,
                                       Map<String, GooValue> treeConstants,
                                       Map<GooType, Integer> map) {
        try {
            GooType type = GooType.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            map.put(type, GooIntExpressionEvaluator.resolveValue(
                    entry.getValue(), constants, baseValues, treeConstants));
        } catch (IllegalArgumentException e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(WARN_UNKNOWN_TYPE, entry.getKey());
            }
        }
    }

    /**
     * Converts a GooValue to a JSON object with goo type IDs as keys.
     *
     * @param value the value to serialize
     * @return JSON object with string keys and integer values
     */
    static JsonObject toJson(GooValue value) {
        JsonObject json = new JsonObject();
        value.getAll().forEach((type, amount) ->
                json.addProperty(type.getId(), amount));
        return json;
    }

    /**
     * Resolves a single JSON value (int literal or $expression) against
     * the constants table. Used by both item values and constant definitions
     * so constants can reference earlier constants.
     *
     * @param element   the JSON element (integer or expression string)
     * @param constants scalar constant symbol table
     * @return the resolved integer value
     */
    static int resolveConstantValue(JsonElement element, Map<String, Integer> constants) {
        return GooIntExpressionEvaluator.resolveValue(element, constants);
    }
}
