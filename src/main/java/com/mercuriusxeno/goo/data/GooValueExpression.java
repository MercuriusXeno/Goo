package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.EnumMap;
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
 */
final class GooValueExpression {

    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Operator constants ---

    /** Addition operator. */
    private static final String OP_PLUS = "+";
    /** Subtraction operator. */
    private static final String OP_MINUS = "-";
    /** Multiplication operator. */
    private static final String OP_MULTIPLY = "*";
    /** Division operator. */
    private static final String OP_DIVIDE = "/";
    /** Open parenthesis. */
    private static final String OP_OPEN_PAREN = "(";
    /** Close parenthesis. */
    private static final String OP_CLOSE_PAREN = ")";
    /** Dollar-sign prefix for constants. */
    private static final String DOLLAR_PREFIX = "$";

    // --- Log messages ---

    /** Log: scalar used where GooValue expected. */
    private static final String LOG_SCALAR_AS_GOO = "Scalar {} used where GooValue expected";
    /** Log: unexpected character in expression. */
    private static final String LOG_UNEXPECTED_CHAR = "Unexpected character '{}' in GooValue expression: {}";
    /** Log: missing closing parenthesis. */
    private static final String LOG_MISSING_PAREN = "Missing closing parenthesis in GooValue expression";
    /** Log: unknown item in expression. */
    private static final String LOG_UNKNOWN_ITEM = "Unknown item in expression: {}";
    /** Log: unknown constant in expression. */
    private static final String LOG_UNKNOWN_CONST = "Unknown constant in GooValue expression: ${}";
    /** Log: cannot apply operator to two GooValues. */
    private static final String LOG_CANNOT_OP_GOO = "Cannot {} two GooValues; use a scalar operand";
    /** Log: cannot divide scalar by GooValue. */
    private static final String LOG_CANNOT_DIV_SCALAR = "Cannot divide scalar by GooValue";
    /** Log: cannot mix GooValue and scalar with operator. */
    private static final String LOG_CANNOT_MIX = "Cannot {} GooValue and scalar directly";

    /** Negation multiplier. */
    private static final int NEGATE = -1;

    /** Utility class, not instantiable. */
    private GooValueExpression() {}

    /**
     * Represents a value in an expression: either a GooValue (multi-type)
     * or a scalar int (for constants and dot-extracted values).
     */
    private sealed interface ExprVal {
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

    /** Wraps a multi-type GooValue as an expression result. */
    private record GooVal(GooValue value) implements ExprVal {
        @Override public GooValue toGooValue() { return value; }
        @Override public int toInt() { return value.totalBlobs(); }
        @Override public boolean isScalar() { return false; }
    }

    /** Wraps a plain integer as an expression result. */
    private record ScalarVal(int value) implements ExprVal {
        @Override public GooValue toGooValue() {
            // Scalar can't meaningfully become a GooValue; shouldn't happen in well-formed expressions
            LOGGER.warn(LOG_SCALAR_AS_GOO, value);
            return GooValue.EMPTY;
        }
        @Override public int toInt() { return value; }
        @Override public boolean isScalar() { return true; }
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
     * @param expr           the expression (e.g. "minecraft:copper_block + $stripped")
     * @param baseValues     already-parsed item values (order-dependent: earlier items available)
     * @param constants      $constant symbol table (scalar ints)
     * @param treeConstants  $constant symbol table (GooValue trees)
     * @return the computed GooValue
     */
    static GooValue evaluate(String expr, Map<Identifier, GooValue> baseValues,
                             Map<String, Integer> constants,
                             Map<String, GooValue> treeConstants) {
        List<String> tokens = tokenize(expr);
        int[] pos = {0};
        ExprVal result = evalExpr(tokens, pos, baseValues, constants, treeConstants);
        return result.toGooValue();
    }

    // ── Tokenizer ────────────────────────────────────────────────────────

    /**
     * Tokenizes an expression, recognizing namespaced IDs (with optional .type suffix).
     *
     * @param expr the expression string to tokenize
     * @return ordered list of tokens
     */
    static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            i = scanNextToken(expr, i, tokens);
        }
        return tokens;
    }

    /**
     * Classifies the character at position i and scans the appropriate token type.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token
     */
    private static int scanNextToken(String expr, int i, List<String> tokens) {
        char c = expr.charAt(i);
        if (Character.isWhitespace(c) || isOperatorOrParen(c)) {
            return scanSingleChar(c, i, tokens);
        }
        return scanValueToken(expr, i, c, tokens);
    }

    /**
     * Appends a single-character operator/paren token (skips whitespace) and advances past it.
     *
     * @param c      the character to possibly add
     * @param i      current scan position
     * @param tokens list to append the token to (whitespace is not appended)
     * @return the position after the character
     */
    private static int scanSingleChar(char c, int i, List<String> tokens) {
        if (!Character.isWhitespace(c)) {
            tokens.add(String.valueOf(c));
        }
        return i + 1;
    }

    /**
     * Scans a value-bearing token: $constant, numeric literal, or namespaced ID.
     * Logs a warning and skips unrecognized characters.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param c      the character at position i
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token
     */
    private static int scanValueToken(String expr, int i, char c, List<String> tokens) {
        if (c == '$') {
            return scanConstant(expr, i, tokens);
        }
        if (Character.isDigit(c)) {
            return scanNumber(expr, i, tokens);
        }
        return scanWordOrSkip(expr, i, c, tokens);
    }

    /**
     * Scans a namespaced ID if the character is a letter, otherwise logs a warning
     * for the unrecognized character and advances past it.
     *
     * @param expr   the full expression string
     * @param i      current scan position
     * @param c      the character at position i
     * @param tokens list to append the scanned token to
     * @return the position after the scanned token or skipped character
     */
    private static int scanWordOrSkip(String expr, int i, char c, List<String> tokens) {
        if (Character.isLetter(c)) {
            return scanNamespacedId(expr, i, tokens);
        }
        LOGGER.warn(LOG_UNEXPECTED_CHAR, c, expr);
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
     * Scans a namespaced ID token (letters/digits/underscore, colon, path chars, optional .type).
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

    // ── Recursive descent evaluator ──────────────────────────────────────

    /**
     * Additive level: + and - on GooValues.
     *
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param baseValues item value lookup table
     * @param constants scalar constant symbol table
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
            result = applyOp(result, op, rhs);
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
        return OP_PLUS.equals(token) || OP_MINUS.equals(token);
    }

    /**
     * Multiplicative level: * and /, plus implicit multiplication (e.g. "4 iron_ingot").
     *
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param baseValues item value lookup table
     * @param constants scalar constant symbol table
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
            if (folded == null) { break; }
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
        if (OP_MULTIPLY.equals(next) || OP_DIVIDE.equals(next)) {
            pos[0]++;
            ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return applyOp(current, next, rhs);
        }
        if (current.isScalar() && isAtomStart(next)) {
            ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return applyOp(current, OP_MULTIPLY, rhs);
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
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param baseValues item value lookup table
     * @param constants scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the evaluated atom value
     */
    private static ExprVal evalAtom(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        if (pos[0] < tokens.size() && OP_MINUS.equals(tokens.get(pos[0]))) {
            pos[0]++;
            ExprVal inner = evalAtom(tokens, pos, baseValues, constants, treeConstants);
            return negate(inner);
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
     * @param name the constant name (without $ prefix)
     * @param constants scalar constant symbol table
     * @param treeConstants tree constant symbol table
     * @return the resolved expression value
     */
    private static ExprVal lookupAnyConstant(String name,
                                             Map<String, Integer> constants,
                                             Map<String, GooValue> treeConstants) {
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < name.length() - 1) {
            ExprVal dotResult = extractDotConstant(name, dotIdx, treeConstants);
            if (dotResult != null) {
                return dotResult;
            }
        }
        GooValue tree = treeConstants.get(name);
        if (tree != null) {
            return new GooVal(tree);
        }
        return new ScalarVal(lookupConstant(name, constants));
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
        String typeSuffix = name.substring(dotIdx + 1);
        GooValue tree = treeConstants.get(constName);
        if (tree == null) {
            return null;
        }
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
     * @param token the namespaced ID token (possibly with .type suffix)
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
     * @param id the item ID string to parse and look up
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
     * @param name the constant name (without $ prefix)
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

    // ── Operators ────────────────────────────────────────────────────────

    /**
     * Applies an operator, handling mixed GooValue/scalar operands.
     *
     * @param lhs the left-hand operand
     * @param op the operator string (+, -, *, /)
     * @param rhs the right-hand operand
     * @return the result of applying the operator
     */
    private static ExprVal applyOp(ExprVal lhs, String op, ExprVal rhs) {
        if (lhs.isScalar() && rhs.isScalar()) {
            return new ScalarVal(intOp(lhs.toInt(), op, rhs.toInt()));
        }
        if (!lhs.isScalar() && !rhs.isScalar()) {
            return applyGooGooOp(lhs, op, rhs);
        }
        return applyMixedOp(lhs, op, rhs);
    }

    /**
     * Applies an additive operator to two GooValue operands. Only + and - are valid;
     * other operators log a warning and return the left operand unchanged.
     *
     * @param lhs the left GooValue operand
     * @param op  the operator string (+, -)
     * @param rhs the right GooValue operand
     * @return the result of the GooValue operation
     */
    private static ExprVal applyGooGooOp(ExprVal lhs, String op, ExprVal rhs) {
        return switch (op) {
            case OP_PLUS -> new GooVal(lhs.toGooValue().add(rhs.toGooValue(), 1));
            case OP_MINUS -> new GooVal(lhs.toGooValue().subtract(rhs.toGooValue()));
            default -> {
                LOGGER.warn(LOG_CANNOT_OP_GOO, op);
                yield lhs;
            }
        };
    }

    /**
     * Applies a scaling operator when one operand is a GooValue and the other is a scalar.
     * Supports * and /; + and - between mixed types log a warning.
     *
     * @param lhs the left operand (one of GooValue or scalar)
     * @param op  the operator string (*, /)
     * @param rhs the right operand (the other of GooValue or scalar)
     * @return the scaled result
     */
    private static ExprVal applyMixedOp(ExprVal lhs, String op, ExprVal rhs) {
        GooValue gv = lhs.isScalar() ? rhs.toGooValue() : lhs.toGooValue();
        int scalar = lhs.isScalar() ? lhs.toInt() : rhs.toInt();
        return switch (op) {
            case OP_MULTIPLY -> new GooVal(multiplyGooValue(gv, scalar));
            case OP_DIVIDE -> divideMixed(lhs.isScalar(), gv, scalar);
            default -> {
                LOGGER.warn(LOG_CANNOT_MIX, op);
                yield new GooVal(gv);
            }
        };
    }

    /**
     * Handles division in a mixed GooValue/scalar context. Scalar / GooValue is invalid
     * (logs a warning); GooValue / scalar divides each component.
     *
     * @param lhsIsScalar true if the left-hand operand was the scalar
     * @param gv          the GooValue operand
     * @param scalar      the scalar operand
     * @return the division result
     */
    private static ExprVal divideMixed(boolean lhsIsScalar, GooValue gv, int scalar) {
        if (lhsIsScalar) {
            LOGGER.warn(LOG_CANNOT_DIV_SCALAR);
            return new GooVal(GooValue.EMPTY);
        }
        return new GooVal(gv.divide(scalar));
    }

    /**
     * Negates an expression value: flips sign on scalar or all goo types.
     *
     * @param val the value to negate
     * @return the negated value
     */
    private static ExprVal negate(ExprVal val) {
        if (val.isScalar()) { return new ScalarVal(-val.toInt()); }
        return new GooVal(multiplyGooValue(val.toGooValue(), NEGATE));
    }

    /**
     * Multiplies every type in a GooValue by a scalar.
     *
     * @param value the GooValue to scale
     * @param scalar the multiplier to apply per type
     * @return a new GooValue with scaled amounts
     */
    private static GooValue multiplyGooValue(GooValue value, int scalar) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        value.getAll().forEach((type, amount) -> result.put(type, amount * scalar));
        return new GooValue(result);
    }

    /**
     * Applies an arithmetic operator to two integer operands.
     *
     * @param a the left operand
     * @param op the operator string (+, -, *, /)
     * @param b the right operand
     * @return the result of the arithmetic operation
     */
    private static int intOp(int a, String op, int b) {
        return switch (op) {
            case OP_PLUS -> a + b;
            case OP_MINUS -> a - b;
            case OP_MULTIPLY -> a * b;
            case OP_DIVIDE -> b == 0 ? 0 : a / b;
            default -> a;
        };
    }
}
