package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
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
    /** Sentinel indicating no valid dot position was found. */
    private static final int NO_DOT = -1;

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
                                  Map<Identifier, GooValue> baseValues) {
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
            map.put(type, resolveValue(entry.getValue(), constants, baseValues, treeConstants));
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
     * @param element the JSON element (integer or expression string)
     * @param constants scalar constant symbol table
     * @return the resolved integer value
     */
    static int resolveConstantValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants);
    }

    // -- Private helpers -------------------------------------------------------

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
                                    Map<Identifier, GooValue> baseValues) {
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
                                    Map<Identifier, GooValue> baseValues,
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
                                         Map<Identifier, GooValue> baseValues,
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
            i = tokenizeChar(expr, i, tokens);
        }
        return tokens;
    }

    /**
     * Classifies the character at position {@code i} and dispatches to the
     * appropriate scanner. Returns the updated position after the token.
     *
     * @param expr   the full expression string
     * @param i      current position in the expression
     * @param tokens list to append scanned tokens to
     * @return the position after the consumed character(s)
     */
    private static int tokenizeChar(String expr, int i, List<String> tokens) {
        char c = expr.charAt(i);
        if (Character.isWhitespace(c) || scanSingleChar(c, tokens)) {
            return i + 1;
        }
        return tokenizeWord(expr, i, c, tokens);
    }

    /**
     * If the character is an operator or paren, appends it as a single-char token.
     *
     * @param c      the character to test
     * @param tokens list to append the token to if matched
     * @return true if the character was consumed as a single-char token
     */
    private static boolean scanSingleChar(char c, List<String> tokens) {
        if (!isOperatorOrParen(c)) {
            return false;
        }
        tokens.add(String.valueOf(c));
        return true;
    }

    /**
     * Dispatches multi-character token scanning for $constants, numbers,
     * namespaced IDs, and logs a warning for unexpected characters.
     *
     * @param expr   the full expression string
     * @param i      current position in the expression
     * @param c      the character at position i
     * @param tokens list to append scanned tokens to
     * @return the position after the consumed token
     */
    private static int tokenizeWord(String expr, int i, char c, List<String> tokens) {
        if (c == '$') {
            return scanConstant(expr, i, tokens);
        }
        return tokenizeNonConstant(expr, i, c, tokens);
    }

    /**
     * Scans a numeric literal, namespaced ID, or warns on an unexpected character.
     *
     * @param expr   the full expression string
     * @param i      current position in the expression
     * @param c      the character at position i
     * @param tokens list to append scanned tokens to
     * @return the position after the consumed token
     */
    private static int tokenizeNonConstant(String expr, int i, char c, List<String> tokens) {
        if (Character.isDigit(c)) {
            return scanNumber(expr, i, tokens);
        }
        if (Character.isLetter(c)) {
            return scanNamespacedId(expr, i, tokens);
        }
        LOGGER.warn(WARN_UNEXPECTED_CHAR, c, expr);
        return i + 1;
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
                                Map<Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        int result = evalTerm(tokens, pos, constants, baseValues, treeConstants);
        while (pos[0] < tokens.size()) {
            String op = tokens.get(pos[0]);
            if (!isAdditiveOp(op)) { break; }
            pos[0]++;
            result = applyOperator(result, op, evalTerm(tokens, pos, constants, baseValues, treeConstants));
        }
        return result;
    }

    /**
     * Returns true if the operator string is additive (+ or -).
     *
     * @param op the operator string to test
     * @return true if op is "+" or "-"
     */
    private static boolean isAdditiveOp(String op) {
        return OP_ADD.equals(op) || OP_SUB.equals(op);
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
                                Map<Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        int result = evalAtom(tokens, pos, constants, baseValues, treeConstants);
        while (pos[0] < tokens.size()) {
            result = evalTermStep(tokens, pos, result, constants, baseValues, treeConstants);
        }
        return result;
    }

    /**
     * Resolves the multiplicative operator for the current token: returns the
     * explicit operator (* or /) if present, OP_MUL for implicit multiplication
     * (adjacent atom), or null to signal end of the term.
     *
     * @param token the current token to classify
     * @return the operator string, or null if the term should end
     */
    private static String resolveTermOp(String token) {
        if (isMultiplicativeOp(token)) {
            return token;
        }
        return isAtomStart(token) ? OP_MUL : null;
    }

    /**
     * Evaluates one step of the multiplicative loop: explicit * or /, implicit
     * multiplication when adjacent atoms appear, or signals termination by
     * returning the accumulated result unchanged.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param result        the running accumulated value
     * @param constants     scalar constant symbol table
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the updated accumulated value after this step
     */
    private static int evalTermStep(List<String> tokens, int[] pos, int result,
                                    Map<String, Integer> constants,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, GooValue> treeConstants) {
        String op = resolveTermOp(tokens.get(pos[0]));
        if (op == null) {
            pos[0] = tokens.size();
            return result;
        }
        advanceIfExplicit(op, pos);
        return applyOperator(result, op, evalAtom(tokens, pos, constants, baseValues, treeConstants));
    }

    /**
     * Advances the position past an explicit operator token. Implicit
     * operators (like adjacent-atom multiplication) have no token to skip.
     *
     * @param op  the resolved operator string
     * @param pos mutable position index to advance
     */
    private static void advanceIfExplicit(String op, int[] pos) {
        if (isMultiplicativeOp(op)) {
            pos[0]++;
        }
    }

    /**
     * Returns true if the operator string is multiplicative (* or /).
     *
     * @param op the operator string to test
     * @return true if op is "*" or "/"
     */
    private static boolean isMultiplicativeOp(String op) {
        return OP_MUL.equals(op) || OP_DIV.equals(op);
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
                                Map<Identifier, GooValue> baseValues,
                                Map<String, GooValue> treeConstants) {
        if (isUnaryMinus(tokens, pos)) {
            pos[0]++;
            return -evalAtom(tokens, pos, constants, baseValues, treeConstants);
        }
        return evalPositiveAtom(tokens, pos, constants, baseValues, treeConstants);
    }

    /**
     * Returns true if the current token position holds a unary minus operator.
     *
     * @param tokens the token list
     * @param pos    mutable position index into tokens
     * @return true if the current token is a unary minus
     */
    private static boolean isUnaryMinus(List<String> tokens, int[] pos) {
        return pos[0] < tokens.size() && OP_SUB.equals(tokens.get(pos[0]));
    }

    /**
     * Evaluates a non-negated atom: consumes the current token then dispatches
     * to parenthesized evaluation or operand resolution.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param constants     scalar constant symbol table
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result
     */
    private static int evalPositiveAtom(List<String> tokens, int[] pos,
                                        Map<String, Integer> constants,
                                        Map<Identifier, GooValue> baseValues,
                                        Map<String, GooValue> treeConstants) {
        String token = tokens.get(pos[0]);
        pos[0]++;
        if (TOKEN_OPEN.equals(token)) {
            return evalParenthesized(tokens, pos, constants, baseValues, treeConstants);
        }
        return resolveOperand(token, constants, baseValues, treeConstants);
    }

    /**
     * Evaluates a parenthesized sub-expression and consumes the closing paren.
     * Warns if the closing paren is missing rather than failing.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index (past the opening paren)
     * @param constants     scalar constant symbol table
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the evaluated integer result of the sub-expression
     */
    private static int evalParenthesized(List<String> tokens, int[] pos,
                                         Map<String, Integer> constants,
                                         Map<Identifier, GooValue> baseValues,
                                         Map<String, GooValue> treeConstants) {
        int result = evalExpr(tokens, pos, constants, baseValues, treeConstants);
        if (pos[0] < tokens.size() && TOKEN_CLOSE.equals(tokens.get(pos[0]))) {
            pos[0]++;
        } else {
            LOGGER.warn(WARN_MISSING_PAREN);
        }
        return result;
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
                                      Map<Identifier, GooValue> baseValues,
                                      Map<String, GooValue> treeConstants) {
        if (token.startsWith(CONSTANT_PREFIX)) {
            return resolveConstantOperand(token.substring(1), constants, treeConstants);
        }
        if (Character.isDigit(token.charAt(0))) {
            return Integer.parseInt(token);
        }
        return baseValues != null ? resolveItemOperand(token, baseValues) : Integer.parseInt(token);
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
        GooValue tree = treeConstants.get(name.substring(0, dotIdx));
        if (tree == null) {
            return Integer.MIN_VALUE;
        }
        return extractGooType(name.substring(dotIdx + 1), tree);
    }

    /**
     * Parses a type suffix string into a {@link GooType} and extracts its value
     * from the given {@link GooValue}. Returns {@link Integer#MIN_VALUE} if the
     * suffix is not a recognized goo type.
     *
     * @param typeSuffix the goo type name to look up (case-insensitive)
     * @param source     the GooValue to extract the type amount from
     * @return the type's amount, or Integer.MIN_VALUE if the type is unknown
     */
    private static int extractGooType(String typeSuffix, GooValue source) {
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            return source.get(type);
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
                                          Map<Identifier, GooValue> baseValues) {
        if (token.contains(DOT)) {
            int extracted = tryItemDotExtraction(token, baseValues);
            if (extracted != Integer.MIN_VALUE) {
                return extracted;
            }
        }
        return resolveBareName(token);
    }

    /**
     * Handles a bare (non-dot-notation) item token: namespaced IDs without a .type
     * suffix get a warning and return 0, plain numbers are parsed as integers.
     *
     * @param token the bare item reference or numeric literal
     * @return 0 for namespaced IDs missing a type, or the parsed integer
     */
    private static int resolveBareName(String token) {
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
                                            Map<Identifier, GooValue> baseValues) {
        int dotIdx = findTypeDot(token);
        if (dotIdx < 0) {
            return Integer.MIN_VALUE;
        }
        return resolveItemType(token, dotIdx, baseValues);
    }

    /**
     * Finds the position of the dot separating the item ID from its type suffix.
     * Returns {@link #NO_DOT} if no valid type-dot exists (dot at edges, or dot before a colon).
     *
     * @param token the full dot-notation token
     * @return the dot index, or NO_DOT if no valid type-dot is found
     */
    private static int findTypeDot(String token) {
        int colonIdx = token.indexOf(COLON.charAt(0));
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= token.length() - 1) {
            return NO_DOT;
        }
        return (colonIdx >= 0 && dotIdx <= colonIdx) ? NO_DOT : dotIdx;
    }

    /**
     * Resolves the goo type and item lookup for a dot-notation token once the dot
     * position is known. Returns {@link Integer#MIN_VALUE} for unrecognized types,
     * warns and returns 0 for unknown items.
     *
     * @param token      the full dot-notation token
     * @param dotIdx     position of the type-separating dot
     * @param baseValues item values for lookups
     * @return the extracted type value, or Integer.MIN_VALUE if the type is invalid
     */
    private static int resolveItemType(String token, int dotIdx,
                                       Map<Identifier, GooValue> baseValues) {
        String typeSuffix = token.substring(dotIdx + 1);
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            return lookupItemValue(token.substring(0, dotIdx), type, baseValues);
        } catch (IllegalArgumentException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Looks up an item's value for a specific goo type. Warns and returns 0 if
     * the item ID is not found in the base values map.
     *
     * @param itemId     the namespaced item identifier string
     * @param type       the goo type to extract
     * @param baseValues item values for lookups
     * @return the item's amount for the given type, or 0 if the item is unknown
     */
    private static int lookupItemValue(String itemId, GooType type,
                                       Map<Identifier, GooValue> baseValues) {
        GooValue value = baseValues.get(Identifier.parse(itemId));
        if (value == null) {
            LOGGER.warn(WARN_UNKNOWN_ITEM, itemId);
            return 0;
        }
        return value.get(type);
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
     * Division by zero returns 0 rather than throwing.
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
            case OP_DIV -> safeDivide(base, operand);
            default -> warnUnknownOp(op, base);
        };
    }

    /**
     * Integer division guarded against divide-by-zero, returning 0 instead.
     *
     * @param base the dividend
     * @param operand the divisor
     * @return the quotient, or 0 if the divisor is zero
     */
    private static int safeDivide(int base, int operand) {
        return operand == 0 ? 0 : base / operand;
    }

    /**
     * Logs a warning for an unrecognized operator and returns the left operand unchanged.
     *
     * @param op   the unrecognized operator string
     * @param base the left operand to pass through
     * @return the base value unchanged
     */
    private static int warnUnknownOp(String op, int base) {
        LOGGER.warn(WARN_UNKNOWN_OP, op);
        return base;
    }
}
