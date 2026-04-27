package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates GooValue-level expressions where item IDs are atoms.
 * Supports addition/subtraction of GooValues, and multiplication/division
 * by scalar integers or $constants. Dot notation (e.g. minecraft:coal.blaze)
 * extracts a single type as a scalar, producing a single-type GooValue.
 *
 * <p>Operator precedence: * / bind tighter than + -.
 * Parentheses override precedence.</p>
 *
 * <p>Tokenization is handled by {@link ExpressionTokenizer};
 * arithmetic operators by {@link ExpressionOperators}.</p>
 */
final class GooValueExpression {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Open parenthesis.
     */
    private static final String OP_OPEN_PAREN = "(";
    /**
     * Close parenthesis.
     */
    private static final String OP_CLOSE_PAREN = ")";
    /**
     * Dollar-sign prefix for constants.
     */
    private static final String DOLLAR_PREFIX = "$";

    // --- Log messages ---

    /**
     * Log: scalar used where GooValue expected.
     */
    private static final String LOG_SCALAR_AS_GOO = "Scalar {} used where GooValue expected";
    /**
     * Log: missing closing parenthesis.
     */
    private static final String LOG_MISSING_PAREN = "Missing closing parenthesis in GooValue expression";
    /**
     * Log: unknown item in expression.
     */
    private static final String LOG_UNKNOWN_ITEM = "Unknown item in expression: {}";
    /**
     * Log: unknown constant in expression.
     */
    private static final String LOG_UNKNOWN_CONST = "Unknown constant in GooValue expression: ${}";

    /**
     * Utility class, not instantiable.
     */
    private GooValueExpression() {
    }

    /**
     * Evaluates an item-level expression string into a GooValue.
     *
     * @param expr       the expression (e.g. "minecraft:copper_block + minecraft:pumpkin")
     * @param baseValues already-parsed item values (order-dependent: earlier items available)
     * @param constants  $constant symbol table (scalar ints)
     * @return the computed GooValue
     */
    static GooValue evaluate(String expr, Map<Identifier, GooValue> baseValues,
                             Map<String, Integer> constants) {
        return evaluate(expr, baseValues, constants, Map.of());
    }

    /**
     * Evaluates an item-level expression string into a GooValue, with tree constant support.
     *
     * @param expr          the expression (e.g. "minecraft:copper_block + $stripped")
     * @param baseValues    already-parsed item values (order-dependent: earlier items available)
     * @param constants     $constant symbol table (scalar ints)
     * @param treeConstants $constant symbol table (GooValue trees)
     * @return the computed GooValue
     */
    static GooValue evaluate(String expr, Map<Identifier, GooValue> baseValues,
                             Map<String, Integer> constants,
                             Map<String, GooValue> treeConstants) {
        List<String> tokens = ExpressionTokenizer.tokenize(expr);
        int[] pos = {0};
        ExprVal result = evalExpr(tokens, pos, baseValues, constants, treeConstants);
        return result.toGooValue();
    }

    /**
     * Additive level: + and - on GooValues.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated expression value
     */
    private static ExprVal evalExpr(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        ExprVal result = evalTerm(tokens, pos, baseValues, constants, treeConstants);
        while (pos[0] < tokens.size() && isAdditiveOp(tokens.get(pos[0]))) {
            String op = tokens.get(pos[0]);
            pos[0]++;
            ExprVal rhs = evalTerm(tokens, pos, baseValues, constants, treeConstants);
            result = ExpressionOperators.applyOp(result, op, rhs);
        }
        return result;
    }

    /**
     * Returns true if the token is an additive operator (+ or -).
     *
     * @param token the token to test
     * @return true if the token is + or -
     */
    private static boolean isAdditiveOp(String token) {
        return ExpressionOperators.OP_PLUS.equals(token) || ExpressionOperators.OP_MINUS.equals(token);
    }

    /**
     * Multiplicative level: * and /, plus implicit multiplication (e.g. "4 iron_ingot").
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated term value
     */
    private static ExprVal evalTerm(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        ExprVal result = evalAtom(tokens, pos, baseValues, constants, treeConstants);
        while (pos[0] < tokens.size()) {
            ExprVal folded = foldTermOperand(tokens, pos, result, baseValues, constants, treeConstants);
            if (folded == null) {
                break;
            }
            result = folded;
        }
        return result;
    }

    /**
     * Folds one multiplicative or implicit-multiply operand into the running result.
     * Returns null to signal the caller to break out of the term loop.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param current       the running result so far
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the updated result, or null if no term-level operator applies
     */
    private static ExprVal foldTermOperand(List<String> tokens, int[] pos, ExprVal current,
                                           Map<Identifier, GooValue> baseValues,
                                           Map<String, Integer> constants,
                                           Map<String, GooValue> treeConstants) {
        String next = tokens.get(pos[0]);
        if (isMultiplicativeOp(next)) {
            pos[0]++;
            ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return ExpressionOperators.applyOp(current, next, rhs);
        }
        return tryImplicitMultiply(current, next, tokens, pos, baseValues, constants, treeConstants);
    }

    /**
     * Returns true if the token is an explicit multiplicative operator (* or /).
     *
     * @param token the token to test
     * @return true if the token is '*' or '/'
     */
    private static boolean isMultiplicativeOp(String token) {
        return ExpressionOperators.OP_MULTIPLY.equals(token) || ExpressionOperators.OP_DIVIDE.equals(token);
    }

    /**
     * Applies implicit multiplication when a scalar precedes an atom token.
     *
     * @param current       the left-hand scalar value
     * @param next          the next token (potential atom start)
     * @param tokens        the token list being parsed
     * @param pos           the mutable position index into the token list
     * @param baseValues    the base item-to-goo-value map
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
     * @return the product if implicit multiply applied, otherwise current unchanged
     */
    private static ExprVal tryImplicitMultiply(ExprVal current, String next,
                                               List<String> tokens, int[] pos,
                                               Map<Identifier, GooValue> baseValues,
                                               Map<String, Integer> constants,
                                               Map<String, GooValue> treeConstants) {
        if (current.isScalar() && isAtomStart(next)) {
            ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return ExpressionOperators.applyOp(current, ExpressionOperators.OP_MULTIPLY, rhs);
        }
        return null;
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
     * Atom: unary minus, parenthesized group, $constant (tree or scalar), integer, or namespaced item ID.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index into tokens
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated atom value
     */
    private static ExprVal evalAtom(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        if (pos[0] < tokens.size() && ExpressionOperators.OP_MINUS.equals(tokens.get(pos[0]))) {
            pos[0]++;
            ExprVal inner = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return ExpressionOperators.negate(inner);
        }
        String token = tokens.get(pos[0]);
        pos[0]++;
        return dispatchAtom(token, tokens, pos, baseValues, constants, treeConstants);
    }

    /**
     * Dispatches atom evaluation based on the leading token: parenthesized group,
     * dollar-constant, numeric literal, or namespaced item reference.
     *
     * @param token         the current token to dispatch on
     * @param tokens        the full token list (needed for sub-expressions)
     * @param pos           mutable position index into tokens
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated atom value
     */
    private static ExprVal dispatchAtom(String token, List<String> tokens, int[] pos,
                                        Map<Identifier, GooValue> baseValues,
                                        Map<String, Integer> constants,
                                        Map<String, GooValue> treeConstants) {
        if (OP_OPEN_PAREN.equals(token)) {
            return evalParenGroup(tokens, pos, baseValues, constants, treeConstants);
        }
        if (token.startsWith(DOLLAR_PREFIX)) {
            return lookupAnyConstant(token.substring(1), constants, treeConstants);
        }
        if (Character.isDigit(token.charAt(0))) {
            return new ScalarVal(Integer.parseInt(token));
        }
        return resolveItemRef(token, baseValues);
    }

    /**
     * Evaluates a parenthesized sub-expression, consuming the closing paren.
     *
     * @param tokens        the token list from the tokenizer
     * @param pos           mutable position index (past the opening paren)
     * @param baseValues    item value lookup table
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated sub-expression value
     */
    private static ExprVal evalParenGroup(List<String> tokens, int[] pos,
                                          Map<Identifier, GooValue> baseValues,
                                          Map<String, Integer> constants,
                                          Map<String, GooValue> treeConstants) {
        ExprVal result = evalExpr(tokens, pos, baseValues, constants, treeConstants);
        if (pos[0] < tokens.size() && OP_CLOSE_PAREN.equals(tokens.get(pos[0]))) {
            pos[0]++;
        } else {
            LOGGER.warn(LOG_MISSING_PAREN);
        }
        return result;
    }

    /**
     * Checks tree constants first, then falls back to scalar constants.
     * Supports dot notation: $log.leaf extracts a single type as a GooValue.
     * At item level, this preserves the type identity (e.g. {leaf: 480}).
     *
     * @param name          the constant name (without $ prefix)
     * @param constants     scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the resolved expression value
     */
    private static ExprVal lookupAnyConstant(String name,
                                             Map<String, Integer> constants,
                                             Map<String, GooValue> treeConstants) {
        ExprVal dotResult = tryDotExtraction(name, treeConstants);
        if (dotResult != null) {
            return dotResult;
        }
        GooValue tree = treeConstants.get(name);
        if (tree != null) {
            return new GooVal(tree);
        }
        return new ScalarVal(lookupConstant(name, constants));
    }

    /**
     * Attempts dot extraction if the name contains a valid dot position.
     *
     * @param name          the dotted identifier to resolve (e.g. "iron.rock")
     * @param treeConstants the named goo value constants from the expression tree
     * @return the extracted goo type amount, or null if no valid dot position
     */
    private static ExprVal tryDotExtraction(String name, Map<String, GooValue> treeConstants) {
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < name.length() - 1) {
            return extractDotConstant(name, dotIdx, treeConstants);
        }
        return null;
    }

    /**
     * Attempts dot extraction on a tree constant (e.g. "log.leaf" extracts the leaf
     * component from $log). Returns null if the suffix is not a valid GooType or the
     * constant is not found.
     *
     * @param name          the full constant name with dot (e.g. "log.leaf")
     * @param dotIdx        index of the last dot in name
     * @param treeConstants tree constant symbol table
     * @return a single-type GooVal if extraction succeeds, or null to fall through
     */
    private static ExprVal extractDotConstant(String name, int dotIdx,
                                              Map<String, GooValue> treeConstants) {
        String constName = name.substring(0, dotIdx);
        GooValue tree = treeConstants.get(constName);
        if (tree == null) {
            return null;
        }
        String typeSuffix = name.substring(dotIdx + 1);
        return parseSingleTypeGoo(typeSuffix, tree);
    }

    /**
     * Parses a type suffix into a GooType and extracts that single component from
     * the given GooValue tree. Returns null if the suffix is not a valid GooType.
     *
     * @param typeSuffix the lowercase type name to parse (e.g. "leaf", "blaze")
     * @param tree       the GooValue to extract from
     * @return a single-type GooVal, or null if the suffix is not a valid GooType
     */
    private static ExprVal parseSingleTypeGoo(String typeSuffix, GooValue tree) {
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            return new GooVal(new GooValue(Map.of(type, tree.get(type))));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Resolves a namespaced item reference. If it contains a dot after
     * the colon (e.g. minecraft:coal.blaze), returns a single-type tree
     * containing only that component. Otherwise returns the full GooValue.
     *
     * @param token      the namespaced ID token (possibly with .type suffix)
     * @param baseValues item value lookup table
     * @return the resolved expression value
     */
    private static ExprVal resolveItemRef(String token, Map<Identifier, GooValue> baseValues) {
        int colonIdx = token.indexOf(':');
        int dotIdx = token.lastIndexOf('.');
        if (hasDotExtraction(colonIdx, dotIdx, token.length())) {
            ExprVal extracted = extractDotItemRef(token, dotIdx, baseValues);
            if (extracted != null) {
                return extracted;
            }
        }
        return new GooVal(lookupItem(token, baseValues));
    }

    /**
     * Returns true if the token has a valid dot-extraction suffix position
     * (dot exists, is not first char, is not last char, and comes after the colon if present).
     *
     * @param colonIdx index of the colon, or -1 if absent
     * @param dotIdx   index of the last dot
     * @param length   total token length
     * @return true if dot extraction should be attempted
     */
    private static boolean hasDotExtraction(int colonIdx, int dotIdx, int length) {
        return dotIdx > 0 && dotIdx < length - 1 && (colonIdx < 0 || dotIdx > colonIdx);
    }

    /**
     * Attempts to extract a single goo type from a dotted item reference
     * (e.g. "minecraft:coal.blaze"). Returns null if the suffix is not a valid GooType.
     *
     * @param token      the full dotted token
     * @param dotIdx     index of the extraction dot
     * @param baseValues item value lookup table
     * @return a single-type GooVal, or null if the suffix is not a valid GooType
     */
    private static ExprVal extractDotItemRef(String token, int dotIdx,
                                             Map<Identifier, GooValue> baseValues) {
        String typeSuffix = token.substring(dotIdx + 1);
        String itemId = token.substring(0, dotIdx);
        GooValue value = lookupItem(itemId, baseValues);
        return parseSingleTypeGoo(typeSuffix, value);
    }

    /**
     * Looks up an item's GooValue by parsing the ID string, returning EMPTY if unknown.
     *
     * @param id         the item ID string to parse and look up
     * @param baseValues item value lookup table
     * @return the item's GooValue, or EMPTY if not found
     */
    private static GooValue lookupItem(String id, Map<Identifier, GooValue> baseValues) {
        Identifier itemId = Identifier.parse(id);
        GooValue value = baseValues.get(itemId);
        if (value == null) {
            LOGGER.warn(LOG_UNKNOWN_ITEM, id);
            return GooValue.EMPTY;
        }
        return value;
    }

    /**
     * Looks up a scalar constant by name, returning 0 if unknown.
     *
     * @param name      the constant name (without $ prefix)
     * @param constants scalar constant symbol table
     * @return the constant value, or 0 if unknown
     */
    private static int lookupConstant(String name, Map<String, Integer> constants) {
        Integer value = constants.get(name);
        if (value == null) {
            LOGGER.warn(LOG_UNKNOWN_CONST, name);
            return 0;
        }
        return value;
    }

    /**
     * Represents a value in an expression: either a GooValue (multi-type)
     * or a scalar int (for constants and dot-extracted values).
     */
    sealed interface ExprVal {
        /**
         * Converts this expression value to a GooValue (identity for GooVal, lossy for ScalarVal).
         *
         * @return the GooValue representation
         */
        GooValue toGooValue();

        /**
         * Converts this expression value to an int (totalBlobs for GooVal, identity for ScalarVal).
         *
         * @return the integer representation
         */
        int toInt();

        /**
         * Returns true if this value is a plain integer (not a multi-type GooValue).
         *
         * @return true if scalar, false if multi-type
         */
        boolean isScalar();
    }

    /**
     * Wraps a multi-type GooValue as an expression result.
     */
    record GooVal(GooValue value) implements ExprVal {
        @Override
        public GooValue toGooValue() {
            return value;
        }

        @Override
        public int toInt() {
            return value.totalBlobs();
        }

        @Override
        public boolean isScalar() {
            return false;
        }
    }

    /**
     * Wraps a plain integer as an expression result.
     */
    record ScalarVal(int value) implements ExprVal {
        @Override
        public GooValue toGooValue() {
            // Scalar can't meaningfully become a GooValue; shouldn't happen in well-formed expressions
            LOGGER.warn(LOG_SCALAR_AS_GOO, value);
            return GooValue.EMPTY;
        }

        @Override
        public int toInt() {
            return value;
        }

        @Override
        public boolean isScalar() {
            return true;
        }
    }
}
