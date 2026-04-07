package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON serialization for {@link GooValue}: parses value objects with optional
 * {@code $constant} reference substitution, and serializes them back to JSON.
 */
final class GooValueJsonFormat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Log warning for unknown goo type in value JSON. */
    private static final String WARN_UNKNOWN_TYPE = "Unknown goo type in values: {}";
    /** Log warning for unexpected character in expression. */
    private static final String WARN_UNEXPECTED_CHAR = "Unexpected character '{}' in expression: {}";
    /** Operator string for addition. */
    private static final String OP_ADD = "+";
    /** Operator string for subtraction. */
    private static final String OP_SUB = "-";
    /** Operator string for multiplication. */
    private static final String OP_MUL = "*";
    /** Operator string for division. */
    private static final String OP_DIV = "/";
    /** Opening parenthesis token. */
    private static final String TOKEN_OPEN = "(";
    /** Closing parenthesis token. */
    private static final String TOKEN_CLOSE = ")";
    /** Log warning for missing closing parenthesis. */
    private static final String WARN_MISSING_PAREN = "Missing closing parenthesis in expression";
    /** Prefix for constant reference tokens. */
    private static final String CONSTANT_PREFIX = "$";
    /** Dot separator for type extraction. */
    private static final String DOT = ".";
    /** Colon separator for namespaced IDs. */
    private static final String COLON = ":";
    /** Log warning for unknown item in dot-notation. */
    private static final String WARN_UNKNOWN_ITEM = "Unknown item in dot-notation: {}";
    /** Log warning for item reference without .type. */
    private static final String WARN_ITEM_NO_TYPE = "Item reference without .type in int expression: {}";
    /** Log warning for unknown constant reference. */
    private static final String WARN_UNKNOWN_CONSTANT = "Unknown constant: ${}";
    /** Log warning for unknown operator. */
    private static final String WARN_UNKNOWN_OP = "Unknown operator in constant expression: {}";

    /** Utility class, not instantiable. */
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
        return parseGooValue(json, constants, baseValues, Map.of());
    }

    /**
     * Parses a goo value JSON object with item and tree constant dot-notation support.
     * Per-type values can use {@code $tree.type} to extract a single type from a tree constant.
     *
     * @param json           JSON object whose keys are goo type names and values are ints or expressions
     * @param constants      symbol table mapping constant names to integer values
     * @param baseValues     item values for dot-notation lookups (may be null)
     * @param treeConstants  tree constant symbol table for $name.type lookups
     * @return parsed GooValue
     */
    static GooValue parseGooValue(JsonObject json, Map<String, Integer> constants,
                                  Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                  Map<String, GooValue> treeConstants) {
        Map<GooType, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                GooType type = GooType.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                map.put(type, resolveValue(entry.getValue(), constants, baseValues, treeConstants));
            } catch (IllegalArgumentException e) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(WARN_UNKNOWN_TYPE, entry.getKey());
                }
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
     *
     * @param element the JSON element (integer or expression string)
     * @param constants scalar constant symbol table
     * @return the resolved integer value
     */
    static int resolveConstantValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Resolves a JSON element to an integer value using only scalar constants.
     *
     * @param element the JSON element to resolve
     * @param constants scalar constant symbol table
     * @return the resolved integer value
     */
    private static int resolveValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants, null, Map.of());
    }

    /**
     * Resolves a JSON element to an integer value with scalar constants and item dot-notation.
     *
     * @param element the JSON element to resolve
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups
     * @return the resolved integer value
     */
    private static int resolveValue(JsonElement element, Map<String, Integer> constants,
                                    Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        return resolveValue(element, constants, baseValues, Map.of());
    }

    /**
     * Resolves a JSON element to an integer value with all constant and item lookup sources.
     *
     * @param element the JSON element to resolve
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table for $name.type lookups
     * @return the resolved integer value
     */
    private static int resolveValue(JsonElement element, Map<String, Integer> constants,
                                    Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                    Map<String, GooValue> treeConstants) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return resolveExpression(element.getAsString().trim(), constants, baseValues, treeConstants);
    }

    /**
     * Evaluates an expression with optional parentheses and item dot-notation.
     * Tokenizes first, then recurses on parenthetical groups.
     *
     * @param expr the expression string to evaluate
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result
     */
    private static int resolveExpression(String expr, Map<String, Integer> constants,
                                         Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                         Map<String, GooValue> treeConstants) {
        List<String> tokens = tokenize(expr);
        int[] pos = {0};
        return evalExpr(tokens, pos, constants, baseValues, treeConstants);
    }

    /**
     * Splits an expression string into tokens: $constants, integer
     * literals, operators (+, -, *, /), and parens. Whitespace is
     * skipped -- {@code ($a+1)*2} and {@code ( $a + 1 ) * 2} both work.
     *
     * @param expr the expression string to tokenize
     * @return ordered list of tokens
     */
    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (isOperatorOrParen(c)) {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '$') {
                i = scanConstant(expr, i, tokens);
            } else if (Character.isDigit(c)) {
                i = scanNumber(expr, i, tokens);
            } else if (Character.isLetter(c)) {
                i = scanNamespacedId(expr, i, tokens);
            } else {
                LOGGER.warn(WARN_UNEXPECTED_CHAR, c, expr);
                i++;
            }
        }
        return tokens;
    }

    /**
     * Returns true if the character is an operator or parenthesis token.
     *
     * @param c the character to test
     * @return true if c is one of ( ) + - * /
     */
    private static boolean isOperatorOrParen(char c) {
        return isParen(c) || isArithmeticOp(c);
    }

    /**
     * Returns true if the character is an opening or closing parenthesis.
     *
     * @param c the character to test
     * @return true if c is ( or )
     */
    private static boolean isParen(char c) {
        return c == '(' || c == ')';
    }

    /**
     * Returns true if the character is an arithmetic operator.
     *
     * @param c the character to test
     * @return true if c is one of + - * /
     */
    private static boolean isArithmeticOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    /**
     * Scans a $constant token starting at position i (the dollar sign).
     * Allows dots for type extraction (e.g. $log.leaf).
     *
     * @param expr the full expression string
     * @param i current position (at the '$')
     * @param tokens list to append the scanned token to
     * @return the position after the constant token
     */
    private static int scanConstant(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && (isIdentChar(expr.charAt(pos)) || expr.charAt(pos) == '.')) { pos++; }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Scans a numeric literal starting at position i.
     *
     * @param expr the full expression string
     * @param i current position (at the first digit)
     * @param tokens list to append the scanned token to
     * @return the position after the number
     */
    private static int scanNumber(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) { pos++; }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Scans a namespaced ID token with optional .type suffix (for dot notation).
     *
     * @param expr the full expression string
     * @param i current position (at the first letter)
     * @param tokens list to append the scanned token to
     * @return the position after the namespaced ID
     */
    private static int scanNamespacedId(String expr, int i, List<String> tokens) {
        int pos = i;
        pos++;
        while (pos < expr.length() && isNamespacedIdChar(expr.charAt(pos))) { pos++; }
        tokens.add(expr.substring(i, pos));
        return pos;
    }

    /**
     * Returns true if the character is valid in a $constant identifier.
     *
     * @param c the character to test
     * @return true if alphanumeric or underscore
     */
    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Characters valid in a namespaced ID token: namespace:path.type
     *
     * @param c the character to test
     * @return true if valid in a namespaced ID
     */
    private static boolean isNamespacedIdChar(char c) {
        return Character.isLetterOrDigit(c) || isIdPunctuation(c);
    }

    /**
     * Returns true if the character is a punctuation mark valid in namespaced IDs.
     *
     * @param c the character to test
     * @return true if c is one of _ : . / -
     */
    private static boolean isIdPunctuation(char c) {
        return c == '_' || c == ':' || c == '.' || isIdSeparator(c);
    }

    /**
     * Returns true if the character is a path separator or hyphen used in namespaced IDs.
     *
     * @param c the character to test
     * @return true if c is / or -
     */
    private static boolean isIdSeparator(char c) {
        return c == '/' || c == '-';
    }

    /**
     * Additive level: handles + and -, delegates * and / to evalTerm.
     *
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result
     */
    private static int evalExpr(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        int result = evalTerm(tokens, pos, constants, baseValues, treeConstants);
        while (pos[0] < tokens.size()) {
            String op = tokens.get(pos[0]);
            if (!OP_ADD.equals(op) && !OP_SUB.equals(op)) { break; }
            pos[0]++;
            int rhs = evalTerm(tokens, pos, constants, baseValues, treeConstants);
            result = applyOperator(result, op, rhs);
        }
        return result;
    }

    /**
     * Multiplicative level: * and /, plus implicit multiplication (e.g. "4 $base").
     *
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result
     */
    private static int evalTerm(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        int result = evalAtom(tokens, pos, constants, baseValues, treeConstants);
        while (pos[0] < tokens.size()) {
            String next = tokens.get(pos[0]);
            if (OP_MUL.equals(next) || OP_DIV.equals(next)) {
                pos[0]++;
                int rhs = evalAtom(tokens, pos, constants, baseValues, treeConstants);
                result = applyOperator(result, next, rhs);
            } else if (isAtomStart(next)) {
                // Implicit multiplication: "4 $base" == "4 * $base"
                int rhs = evalAtom(tokens, pos, constants, baseValues, treeConstants);
                result = applyOperator(result, OP_MUL, rhs);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * True if the token can start an atom (not an operator or closing paren).
     *
     * @param token the token to check
     * @return true if the token can begin an atom expression
     */
    private static boolean isAtomStart(String token) {
        char c = token.charAt(0);
        return c == '(' || c == '$' || Character.isDigit(c) || Character.isLetter(c);
    }

    /**
     * Reads one atom: unary minus, parenthesized sub-expression, $constant, int literal, or item.type ref.
     *
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result
     */
    private static int evalAtom(List<String> tokens, int[] pos,
                                Map<String, Integer> constants,
                                Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        if (pos[0] < tokens.size() && OP_SUB.equals(tokens.get(pos[0]))) {
            pos[0]++;
            return -evalAtom(tokens, pos, constants, baseValues, treeConstants);
        }
        String token = tokens.get(pos[0]);
        pos[0]++;
        if (TOKEN_OPEN.equals(token)) {
            int result = evalExpr(tokens, pos, constants, baseValues, treeConstants);
            if (pos[0] < tokens.size() && TOKEN_CLOSE.equals(tokens.get(pos[0]))) {
                pos[0]++;
            } else {
                LOGGER.warn(WARN_MISSING_PAREN);
            }
            return result;
        }
        return resolveOperand(token, constants, baseValues, treeConstants);
    }

    /**
     * Resolves a single operand token: $constant (with optional .type for tree extraction),
     * integer literal, or dot-notation item reference (e.g. minecraft:coal.blaze).
     *
     * @param token the operand token to resolve
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the resolved integer value
     */
    private static int resolveOperand(String token, Map<String, Integer> constants,
                                      Map<net.minecraft.resources.Identifier, GooValue> baseValues,
                                      Map<String, GooValue> treeConstants) {
        if (token.startsWith(CONSTANT_PREFIX)) {
            return resolveConstantOperand(token.substring(1), constants, treeConstants);
        }
        if (Character.isDigit(token.charAt(0))) {
            return Integer.parseInt(token);
        }
        if (baseValues != null) {
            return resolveItemOperand(token, baseValues);
        }
        return Integer.parseInt(token);
    }

    /**
     * Resolves a $constant operand, trying tree dot extraction first (e.g. $log.leaf),
     * then falling back to scalar constant lookup.
     *
     * @param name the constant name (without $ prefix, may contain dot)
     * @param constants scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the resolved integer value
     */
    private static int resolveConstantOperand(String name, Map<String, Integer> constants,
                                              Map<String, GooValue> treeConstants) {
        int dotIdx = name.lastIndexOf(DOT.charAt(0));
        if (dotIdx > 0 && dotIdx < name.length() - 1) {
            int extracted = tryTreeDotExtraction(name, dotIdx, treeConstants);
            if (extracted != Integer.MIN_VALUE) {
                return extracted;
            }
        }
        return lookupConstant(name, constants);
    }

    /**
     * Attempts to extract a single goo type value from a tree constant via dot notation.
     * Returns {@link Integer#MIN_VALUE} as a sentinel if extraction fails (not a valid
     * tree constant or goo type).
     *
     * @param name the full constant name including dot suffix
     * @param dotIdx position of the last dot in name
     * @param treeConstants tree constant symbol table
     * @return the extracted type value, or Integer.MIN_VALUE if not resolvable
     */
    private static int tryTreeDotExtraction(String name, int dotIdx,
                                            Map<String, GooValue> treeConstants) {
        String constName = name.substring(0, dotIdx);
        GooValue tree = treeConstants.get(constName);
        if (tree == null) {
            return Integer.MIN_VALUE;
        }
        String typeSuffix = name.substring(dotIdx + 1);
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            return tree.get(type);
        } catch (IllegalArgumentException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Resolves an item reference operand: dot-notation (e.g. minecraft:coal.blaze) extracts
     * a single type, bare namespaced IDs without .type produce a warning.
     *
     * @param token the item reference token
     * @param baseValues item values for lookups
     * @return the resolved integer value
     */
    private static int resolveItemOperand(String token,
                                          Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        if (token.contains(DOT)) {
            int extracted = tryItemDotExtraction(token, baseValues);
            if (extracted != Integer.MIN_VALUE) {
                return extracted;
            }
        }
        if (token.contains(COLON)) {
            LOGGER.warn(WARN_ITEM_NO_TYPE, token);
            return 0;
        }
        return Integer.parseInt(token);
    }

    /**
     * Attempts to extract a single goo type value from an item via dot notation
     * (e.g. minecraft:coal.blaze). Returns {@link Integer#MIN_VALUE} if the suffix
     * is not a valid goo type or the item is unknown.
     *
     * @param token the full dot-notation token
     * @param baseValues item values for lookups
     * @return the extracted type value, or Integer.MIN_VALUE if not resolvable
     */
    private static int tryItemDotExtraction(String token,
                                            Map<net.minecraft.resources.Identifier, GooValue> baseValues) {
        int colonIdx = token.indexOf(COLON.charAt(0));
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= token.length() - 1 || (colonIdx >= 0 && dotIdx <= colonIdx)) {
            return Integer.MIN_VALUE;
        }
        String typeSuffix = token.substring(dotIdx + 1);
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            String itemId = token.substring(0, dotIdx);
            GooValue value = baseValues.get(net.minecraft.resources.Identifier.parse(itemId));
            if (value == null) {
                LOGGER.warn(WARN_UNKNOWN_ITEM, itemId);
                return 0;
            }
            return value.get(type);
        } catch (IllegalArgumentException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Looks up a scalar constant by name, returning 0 if unknown.
     *
     * @param name the constant name (without $ prefix)
     * @param constants scalar constant symbol table
     * @return the constant value, or 0 if unknown
     */
    private static int lookupConstant(String name, Map<String, Integer> constants) {
        Integer value = constants.get(name);
        if (value == null) {
            LOGGER.warn(WARN_UNKNOWN_CONSTANT, name);
            return 0;
        }
        return value;
    }

    /**
     * Applies an arithmetic operator to two integer operands.
     *
     * @param base the left operand
     * @param op the operator string (+, -, *, /)
     * @param operand the right operand
     * @return the result of the arithmetic operation
     */
    private static int applyOperator(int base, String op, int operand) {
        return switch (op) {
            case OP_MUL -> base * operand;
            case OP_ADD -> base + operand;
            case OP_SUB -> base - operand;
            case OP_DIV -> operand == 0 ? 0 : base / operand;
            default -> {
                LOGGER.warn(WARN_UNKNOWN_OP, op);
                yield base;
            }
        };
    }
}
