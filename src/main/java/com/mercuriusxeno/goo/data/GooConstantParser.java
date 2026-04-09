package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import java.util.Map;

/**
 * Parses the _constants block from base_values.json into scalar and tree constants.
 * Scalar constants resolve to a single integer; tree constants resolve to a full
 * GooValue (multi-type map). Extracted from GooValueLoader to keep method count
 * within PMD TooManyMethods limits.
 */
final class GooConstantParser {

    private static final String CONSTANTS_SUFFIX = "_constants";
    private static final String LOG_LOADED_CONSTANTS = "Loaded {} constants ({} scalar, {} tree)";

    private GooConstantParser() {}

    /**
     * Parses the _constants object from the JSON root, if present.
     *
     * @param json the root JSON object containing the _constants key
     * @param state mutable parsing state
     */
    static void parseConstants(JsonObject json, GooValueLoader.ParseState state) {
        state.constants.clear();
        state.treeConstants.clear();
        if (!json.has(CONSTANTS_SUFFIX)) { return; }
        JsonObject obj = json.getAsJsonObject(CONSTANTS_SUFFIX);
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            parseOneConstant(entry.getKey(), entry.getValue(), state);
        }
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED_CONSTANTS,
                    state.constants.size() + state.treeConstants.size(),
                    state.constants.size(), state.treeConstants.size());
        }
    }

    /**
     * Parses a single constant entry, dispatching to tree or scalar handling.
     *
     * @param name the constant name
     * @param value the JSON value (object for tree, string for scalar/expression)
     * @param state mutable parsing state
     */
    static void parseOneConstant(String name, JsonElement value, GooValueLoader.ParseState state) {
        if (value.isJsonObject()) {
            state.treeConstants.put(name,
                    GooValueJsonFormat.parseGooValue(value.getAsJsonObject(),
                            state.constants, null, state.treeConstants));
        } else {
            parseScalarConstant(name, value, state);
        }
    }

    /**
     * Parses a scalar constant. Tries tree evaluation first for expressions
     * referencing tree constants (e.g. "9 $metal_nugget"), falling back to scalar.
     *
     * @param name the constant name
     * @param value the JSON value to resolve
     * @param state mutable parsing state
     */
    static void parseScalarConstant(String name, JsonElement value, GooValueLoader.ParseState state) {
        String expr = value.getAsString().trim();
        if (referencesTreeConstant(expr, state.treeConstants)) {
            GooValue tree = GooValueExpression.evaluate(expr, Map.of(),
                    state.constants, state.treeConstants);
            if (!tree.isEmpty()) {
                state.treeConstants.put(name, tree);
                return;
            }
        }
        state.constants.put(name, GooValueJsonFormat.resolveConstantValue(value, state.constants));
    }

    /**
     * True if the expression string contains a $ref that's a known tree constant.
     *
     * @param expr the expression to scan for tree constant references
     * @param treeConstants the known tree constants
     * @return true if any $ref resolves to a tree constant
     */
    static boolean referencesTreeConstant(String expr, Map<String, GooValue> treeConstants) {
        int i = expr.indexOf('$');
        while (i >= 0 && i < expr.length() - 1) {
            int end = scanIdentifier(expr, i + 1);
            if (end > i + 1 && treeConstants.containsKey(expr.substring(i + 1, end))) {
                return true;
            }
            i = expr.indexOf('$', end);
        }
        return false;
    }

    /**
     * Scans forward from {@code start} while characters are word-like (letter, digit, or '_').
     *
     * @param expr the string to scan
     * @param start the starting index
     * @return the index of the first non-word character (or string length)
     */
    static int scanIdentifier(String expr, int start) {
        int end = start;
        while (end < expr.length() && (Character.isLetterOrDigit(expr.charAt(end)) || expr.charAt(end) == '_')) {
            end++;
        }
        return end;
    }
}
