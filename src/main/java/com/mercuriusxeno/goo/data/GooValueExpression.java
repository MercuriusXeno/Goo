package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    private GooValueExpression() {}

    /**
     * Represents a value in an expression: either a GooValue (multi-type)
     * or a scalar int (for constants and dot-extracted values).
     */
    private sealed interface ExprVal {
        GooValue toGooValue();
        int toInt();
        boolean isScalar();
    }

    private record GooVal(GooValue value) implements ExprVal {
        @Override public GooValue toGooValue() { return value; }
        @Override public int toInt() { return value.totalBlobs(); }
        @Override public boolean isScalar() { return false; }
    }

    private record ScalarVal(int value) implements ExprVal {
        @Override public GooValue toGooValue() {
            // Scalar can't meaningfully become a GooValue; shouldn't happen in well-formed expressions
            LOGGER.warn("Scalar {} used where GooValue expected", value);
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

    // ── Tokenizer ─────────────────────────────────────────────���──────────

    /** Tokenizes an expression, recognizing namespaced IDs (with optional .type suffix). */
    static List<String> tokenize(String expr) {
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
                // Namespaced ID: letters/digits/underscore, then colon, then path chars, optional .type
                int start = i++;
                while (i < expr.length() && isNamespacedIdChar(expr.charAt(i))) i++;
                tokens.add(expr.substring(start, i));
            } else {
                LOGGER.warn("Unexpected character '{}' in GooValue expression: {}", c, expr);
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

    // ── Recursive descent evaluator ──────────────────────────────────────

    /** Additive level: + and - on GooValues. */
    private static ExprVal evalExpr(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        ExprVal result = evalTerm(tokens, pos, baseValues, constants, treeConstants);
        while (pos[0] < tokens.size()) {
            String op = tokens.get(pos[0]);
            if (!op.equals("+") && !op.equals("-")) break;
            pos[0]++;
            ExprVal rhs = evalTerm(tokens, pos, baseValues, constants, treeConstants);
            result = applyOp(result, op, rhs);
        }
        return result;
    }

    /** Multiplicative level: * and /, plus implicit multiplication (e.g. "4 iron_ingot"). */
    private static ExprVal evalTerm(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        ExprVal result = evalAtom(tokens, pos, baseValues, constants, treeConstants);
        while (pos[0] < tokens.size()) {
            String next = tokens.get(pos[0]);
            if (next.equals("*") || next.equals("/")) {
                pos[0]++;
                ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
                result = applyOp(result, next, rhs);
            } else if (result.isScalar() && isAtomStart(next)) {
                // Implicit multiplication: "4 iron_ingot" == "4 * iron_ingot"
                ExprVal rhs = evalAtom(tokens, pos, baseValues, constants, treeConstants);
                result = applyOp(result, "*", rhs);
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

    /** Atom: parenthesized group, $constant (tree or scalar), integer, or namespaced item ID. */
    private static ExprVal evalAtom(List<String> tokens, int[] pos,
                                    Map<Identifier, GooValue> baseValues,
                                    Map<String, Integer> constants,
                                    Map<String, GooValue> treeConstants) {
        String token = tokens.get(pos[0]++);
        if (token.equals("(")) {
            ExprVal result = evalExpr(tokens, pos, baseValues, constants, treeConstants);
            if (pos[0] < tokens.size() && tokens.get(pos[0]).equals(")")) {
                pos[0]++;
            } else {
                LOGGER.warn("Missing closing parenthesis in GooValue expression");
            }
            return result;
        }
        if (token.startsWith("$")) {
            return lookupAnyConstant(token.substring(1), constants, treeConstants);
        }
        if (Character.isDigit(token.charAt(0))) {
            return new ScalarVal(Integer.parseInt(token));
        }
        // Namespaced ID, possibly with .type suffix
        return resolveItemRef(token, baseValues);
    }

    /** Checks tree constants first, then falls back to scalar constants. */
    private static ExprVal lookupAnyConstant(String name,
                                             Map<String, Integer> constants,
                                             Map<String, GooValue> treeConstants) {
        GooValue tree = treeConstants.get(name);
        if (tree != null) {
            return new GooVal(tree);
        }
        return new ScalarVal(lookupConstant(name, constants));
    }

    /**
     * Resolves a namespaced item reference. If it contains a dot after
     * the colon (e.g. minecraft:coal.blaze), returns a single-type tree
     * containing only that component. Otherwise returns the full GooValue.
     */
    private static ExprVal resolveItemRef(String token, Map<Identifier, GooValue> baseValues) {
        int colonIdx = token.indexOf(':');
        int dotIdx = token.lastIndexOf('.');
        // Dot extraction: works with colon (minecraft:coal.blaze) or bare word (coal.blaze)
        if (dotIdx > 0 && dotIdx < token.length() - 1 && (colonIdx < 0 || dotIdx > colonIdx)) {
            String typeSuffix = token.substring(dotIdx + 1);
            try {
                GooType type = GooType.valueOf(typeSuffix.toUpperCase());
                String itemId = token.substring(0, dotIdx);
                GooValue value = lookupItem(itemId, baseValues);
                return new GooVal(new GooValue(Map.of(type, value.get(type))));
            } catch (IllegalArgumentException ignored) {
                // Not a valid GooType, treat the whole thing as an item ID
            }
        }
        return new GooVal(lookupItem(token, baseValues));
    }

    private static GooValue lookupItem(String id, Map<Identifier, GooValue> baseValues) {
        Identifier itemId = Identifier.parse(id);
        GooValue value = baseValues.get(itemId);
        if (value == null) {
            LOGGER.warn("Unknown item in expression: {}", id);
            return GooValue.EMPTY;
        }
        return value;
    }

    private static int lookupConstant(String name, Map<String, Integer> constants) {
        Integer value = constants.get(name);
        if (value == null) {
            LOGGER.warn("Unknown constant in GooValue expression: ${}", name);
            return 0;
        }
        return value;
    }

    // ── Operators ────────────────────────────────────��───────────────────

    /** Applies an operator, handling mixed GooValue/scalar operands. */
    private static ExprVal applyOp(ExprVal lhs, String op, ExprVal rhs) {
        // Both scalar: int arithmetic
        if (lhs.isScalar() && rhs.isScalar()) {
            return new ScalarVal(intOp(lhs.toInt(), op, rhs.toInt()));
        }
        // GooValue + GooValue or GooValue - GooValue
        if (!lhs.isScalar() && !rhs.isScalar()) {
            return switch (op) {
                case "+" -> new GooVal(lhs.toGooValue().add(rhs.toGooValue(), 1));
                case "-" -> new GooVal(lhs.toGooValue().subtract(rhs.toGooValue()));
                default -> {
                    LOGGER.warn("Cannot {} two GooValues; use a scalar operand", op);
                    yield lhs;
                }
            };
        }
        // One GooValue, one scalar: scale
        GooValue gv = lhs.isScalar() ? rhs.toGooValue() : lhs.toGooValue();
        int scalar = lhs.isScalar() ? lhs.toInt() : rhs.toInt();
        return switch (op) {
            case "*" -> new GooVal(multiplyGooValue(gv, scalar));
            case "/" -> {
                if (lhs.isScalar()) {
                    LOGGER.warn("Cannot divide scalar by GooValue");
                    yield new GooVal(GooValue.EMPTY);
                }
                yield new GooVal(gv.divide(scalar));
            }
            default -> {
                // + or - with mixed types: promote scalar to empty GooValue (becomes no-op add)
                LOGGER.warn("Cannot {} GooValue and scalar directly", op);
                yield new GooVal(gv);
            }
        };
    }

    /** Multiplies every type in a GooValue by a scalar. */
    private static GooValue multiplyGooValue(GooValue value, int scalar) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        value.getAll().forEach((type, amount) -> result.put(type, amount * scalar));
        return new GooValue(result);
    }

    private static int intOp(int a, String op, int b) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b != 0 ? a / b : 0;
            default -> a;
        };
    }
}
