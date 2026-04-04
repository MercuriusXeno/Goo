package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON serialization for {@link GooValue}: parses value objects with optional
 * {@code $constant} reference substitution, and serializes them back to JSON.
 */
class GooValueJsonFormat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GooValueJsonFormat() {}

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
                                  Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        Map<GooType, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                GooType type = GooType.valueOf(entry.getKey().toUpperCase());
                map.put(type, resolveValue(entry.getValue(), constants, baseValues));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown goo type in values: {}", entry.getKey());
            }
        }
        return new GooValue(map);
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
     */
    static int resolveConstantValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static int resolveValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants, null);
    }

    private static int resolveValue(JsonElement element, Map<String, Integer> constants,
                                    Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return resolveExpression(element.getAsString().trim(), constants, baseValues);
    }

    /**
     * Evaluates an expression with optional parentheses and item dot-notation.
     * Tokenizes first, then recurses on parenthetical groups.
     */
    private static int resolveExpression(String expr, Map<String, Integer> constants,
                                         Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        List<String> tokens = tokenize(expr);
        int[] pos = {0};
        return evalExpr(tokens, pos, constants, baseValues);
    }

    /**
     * Splits an expression string into tokens: $constants, integer
     * literals, operators (+, -, *, /), and parens. Whitespace is
     * skipped -- {@code ($a+1)*2} and {@code ( $a + 1 ) * 2} both work.
     */
    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '$') {
                int start = i++;
                while (i < expr.length() && isIdentChar(expr.charAt(i))) i++;
                tokens.add(expr.substring(start, i));
            } else if (Character.isDigit(c)) {
                int start = i++;
                while (i < expr.length() && Character.isDigit(expr.charAt(i))) i++;
                tokens.add(expr.substring(start, i));
            } else if (Character.isLetter(c)) {
                // Namespaced ID with optional .type suffix (for dot notation)
                int start = i++;
                while (i < expr.length() && isNamespacedIdChar(expr.charAt(i))) i++;
                tokens.add(expr.substring(start, i));
            } else {
                LOGGER.warn("Unexpected character '{}' in expression: {}", c, expr);
                i++;
            }
        }
        return tokens;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Characters valid in a namespaced ID token: namespace:path.type */
    private static boolean isNamespacedIdChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == '/' || c == '-';
    }

    /** Additive level: handles + and -, delegates * and / to evalTerm. */
    private static int evalExpr(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        int result = evalTerm(tokens, pos, constants, baseValues);
        while (pos[0] < tokens.size()) {
            String op = tokens.get(pos[0]);
            if (!op.equals("+") && !op.equals("-")) break;
            pos[0]++;
            int rhs = evalTerm(tokens, pos, constants, baseValues);
            result = applyOperator(result, op, rhs);
        }
        return result;
    }

    /** Multiplicative level: * and /, plus implicit multiplication (e.g. "4 $base"). */
    private static int evalTerm(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        int result = evalAtom(tokens, pos, constants, baseValues);
        while (pos[0] < tokens.size()) {
            String next = tokens.get(pos[0]);
            if (next.equals("*") || next.equals("/")) {
                pos[0]++;
                int rhs = evalAtom(tokens, pos, constants, baseValues);
                result = applyOperator(result, next, rhs);
            } else if (isAtomStart(next)) {
                // Implicit multiplication: "4 $base" == "4 * $base"
                int rhs = evalAtom(tokens, pos, constants, baseValues);
                result = applyOperator(result, "*", rhs);
            } else {
                break;
            }
        }
        return result;
    }

    /** True if the token can start an atom (not an operator or closing paren). */
    private static boolean isAtomStart(String token) {
        char c = token.charAt(0);
        return c == '(' || c == '$' || Character.isDigit(c) || Character.isLetter(c);
    }

    /** Reads one atom: a parenthesized sub-expression, $constant, int literal, or item.type ref. */
    private static int evalAtom(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        String token = tokens.get(pos[0]++);
        if (token.equals("(")) {
            int result = evalExpr(tokens, pos, constants, baseValues);
            if (pos[0] < tokens.size() && tokens.get(pos[0]).equals(")")) {
                pos[0]++;
            } else {
                LOGGER.warn("Missing closing parenthesis in expression");
            }
            return result;
        }
        return resolveOperand(token, constants, baseValues);
    }

    /**
     * Resolves a single operand token: $constant, integer literal, or
     * dot-notation item reference (e.g. minecraft:coal.blaze).
     */
    private static int resolveOperand(String token, Map<String, Integer> constants,
                                      Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        if (token.startsWith("$")) {
            return lookupConstant(token.substring(1), constants);
        }
        if (Character.isDigit(token.charAt(0))) {
            return Integer.parseInt(token);
        }
        // Dot-notation item reference: namespace:path.goo_type or bare_word.goo_type
        if (baseValues != null && token.contains(".")) {
            int colonIdx = token.indexOf(':');
            int dotIdx = token.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < token.length() - 1 && (colonIdx < 0 || dotIdx > colonIdx)) {
                String typeSuffix = token.substring(dotIdx + 1);
                try {
                    GooType type = GooType.valueOf(typeSuffix.toUpperCase());
                    String itemId = token.substring(0, dotIdx);
                    GooValue value = baseValues.get(net.minecraft.resources.Identifier.parse(itemId));
                    if (value == null) {
                        LOGGER.warn("Unknown item in dot-notation: {}", itemId);
                        return 0;
                    }
                    return value.get(type);
                } catch (IllegalArgumentException ignored) {
                    // Not a valid GooType
                }
            }
        }
        if (baseValues != null && token.contains(":")) {
            LOGGER.warn("Item reference without .type in int expression: {}", token);
            return 0;
        }
        return Integer.parseInt(token);
    }

    private static int lookupConstant(String name, Map<String, Integer> constants) {
        Integer value = constants.get(name);
        if (value == null) {
            LOGGER.warn("Unknown constant: ${}", name);
            return 0;
        }
        return value;
    }

    private static int applyOperator(int base, String op, int operand) {
        return switch (op) {
            case "*" -> base * operand;
            case "+" -> base + operand;
            case "-" -> base - operand;
            case "/" -> operand != 0 ? base / operand : 0;
            default -> {
                LOGGER.warn("Unknown operator in constant expression: {}", op);
                yield base;
            }
        };
    }
}
