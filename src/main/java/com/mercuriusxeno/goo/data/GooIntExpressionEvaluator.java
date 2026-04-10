package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recursive-descent evaluator for per-type integer expressions inside goo value
 * JSON. Handles $constants, dot-notation item references, parentheses, and the
 * four arithmetic operators with standard precedence (* / before + -).
 */
final class GooIntExpressionEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

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
    /** Log warning for unknown constant reference. */
    private static final String WARN_UNKNOWN_CONSTANT = "Unknown constant: ${}";
    /** Log warning for unknown operator. */
    private static final String WARN_UNKNOWN_OP = "Unknown operator in constant expression: {}";

    /** Utility class, not instantiable. */
    private GooIntExpressionEvaluator() {}

    /**
     * Resolves a JSON element to an integer value using only scalar constants.
     *
     * @param element the JSON element to resolve
     * @param constants scalar constant symbol table
     * @return the resolved integer value
     */
    static int resolveValue(JsonElement element, Map<String, Integer> constants) {
        return resolveValue(element, constants, null, Map.of());
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
    static int resolveValue(JsonElement element, Map<String, Integer> constants,
                            Map<Identifier, GooValue> baseValues,
                            Map<String, GooValue> treeConstants) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return resolveExpression(element.getAsString().trim(), constants, baseValues, treeConstants);
    }

    // -- Expression evaluation ------------------------------------------------

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
        List<String> tokens = ExpressionTokenizer.tokenize(expr);
        int[] pos = {0};
        return evalExpr(tokens, pos, constants, baseValues, treeConstants);
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
        while (pos[0] < tokens.size() && isTermContinuation(tokens.get(pos[0]))) {
            result = applyTermFactor(result, tokens, pos, constants, baseValues, treeConstants);
        }
        return result;
    }

    /**
     * Returns true if the token continues a multiplicative term (explicit op or implicit multiply).
     * @param token the next token in the expression
     * @return true if the token is a multiplicative operator or can start a new atom
     */
    private static boolean isTermContinuation(String token) {
        return isMultiplicativeOp(token) || isAtomStart(token);
    }

    /**
     * Consumes and applies one multiplicative factor (explicit or implicit).
     * @param result the accumulated value from previous factors
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @param constants scalar constant symbol table
     * @param baseValues item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table
     * @return the result after applying the next multiplicative factor
     */
    private static int applyTermFactor(int result, List<String> tokens, int[] pos,
            Map<String, Integer> constants, Map<Identifier, GooValue> baseValues,
            Map<String, GooValue> treeConstants) {
        String op = consumeMultiplicativeOp(tokens, pos);
        return applyOperator(result, op, evalAtom(tokens, pos, constants, baseValues, treeConstants));
    }

    /**
     * Consumes an explicit multiplicative operator, or returns implicit multiply without advancing.
     * @param tokens the token list from the tokenizer
     * @param pos mutable position index into tokens
     * @return the explicit operator token, or "*" for implicit multiplication
     */
    private static String consumeMultiplicativeOp(List<String> tokens, int[] pos) {
        String token = tokens.get(pos[0]);
        if (isMultiplicativeOp(token)) {
            pos[0]++;
            return token;
        }
        return OP_MUL;
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
     * Reads one atom: unary minus, parenthesized sub-expression, $constant,
     * int literal, or item.type ref.
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

    // -- Operand resolution ---------------------------------------------------

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
        return baseValues != null ? ItemOperandResolver.resolveItemOperand(token, baseValues) : Integer.parseInt(token);
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

    // -- Arithmetic -----------------------------------------------------------

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
