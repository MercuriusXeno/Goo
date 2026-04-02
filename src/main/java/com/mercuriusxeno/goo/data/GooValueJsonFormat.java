package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON serialization for {@link GooValue}: parses value objects with optional
 * {@code $constant} reference substitution, and serializes them back to JSON.
 */
class GooValueJsonFormat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GooValueJsonFormat() {}

    /**
     * Parses a goo value JSON object, resolving {@code $constant} references via the provided
     * symbol table.
     *
     * @param json      JSON object whose keys are goo type names and values are ints or expressions
     * @param constants symbol table mapping constant names to integer values
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json, Map<String, Integer> constants) {
        Map<GooType, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                GooType type = GooType.valueOf(entry.getKey().toUpperCase());
                map.put(type, resolveValue(entry.getValue(), constants));
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

    // ── Private helpers ──────────────────────────────────────────────────

    private static int resolveValue(JsonElement element, Map<String, Integer> constants) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return resolveExpression(element.getAsString().trim(), constants);
    }

    private static int resolveExpression(String expr, Map<String, Integer> constants) {
        if (!expr.startsWith("$")) {
            return Integer.parseInt(expr);
        }
        String[] tokens = expr.split("\\s+");
        int base = lookupConstant(tokens[0].substring(1), constants);
        if (tokens.length == 1) return base;
        if (tokens.length != 3) {
            LOGGER.warn("Malformed constant expression: {}", expr);
            return base;
        }
        return applyOperator(base, tokens[1], resolveOperand(tokens[2], constants));
    }

    private static int resolveOperand(String token, Map<String, Integer> constants) {
        return token.startsWith("$") ? lookupConstant(token.substring(1), constants) : Integer.parseInt(token);
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
