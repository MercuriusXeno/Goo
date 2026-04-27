package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValueExpression.ExprVal;
import com.mercuriusxeno.goo.data.GooValueExpression.GooVal;
import com.mercuriusxeno.goo.data.GooValueExpression.ScalarVal;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.EnumMap;
import java.util.Map;

/**
 * Arithmetic operators for GooValue expression evaluation.
 * Handles GooValue+GooValue, GooValue+scalar, and scalar+scalar combinations.
 */
final class ExpressionOperators {

    /**
     * Addition operator.
     */
    static final String OP_PLUS = "+";
    /**
     * Subtraction operator.
     */
    static final String OP_MINUS = "-";
    /**
     * Multiplication operator.
     */
    static final String OP_MULTIPLY = "*";
    /**
     * Division operator.
     */
    static final String OP_DIVIDE = "/";
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Log: cannot apply operator to two GooValues.
     */
    private static final String LOG_CANNOT_OP_GOO = "Cannot {} two GooValues; use a scalar operand";
    /**
     * Log: cannot divide scalar by GooValue.
     */
    private static final String LOG_CANNOT_DIV_SCALAR = "Cannot divide scalar by GooValue";
    /**
     * Log: cannot mix GooValue and scalar with operator.
     */
    private static final String LOG_CANNOT_MIX = "Cannot {} GooValue and scalar directly";

    /**
     * Negation multiplier.
     */
    private static final int NEGATE = -1;

    /**
     * Utility class, not instantiable.
     */
    private ExpressionOperators() {
    }

    /**
     * Applies an operator, handling mixed GooValue/scalar operands.
     *
     * @param lhs the left-hand operand
     * @param op  the operator string (+, -, *, /)
     * @param rhs the right-hand operand
     * @return the result of applying the operator
     */
    static ExprVal applyOp(ExprVal lhs, String op,
                           ExprVal rhs) {
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
    private static ExprVal applyGooGooOp(ExprVal lhs,
                                         String op,
                                         ExprVal rhs) {
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
    private static ExprVal applyMixedOp(ExprVal lhs, String op,
                                        ExprVal rhs) {
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
    static ExprVal negate(ExprVal val) {
        if (val.isScalar()) {
            return new ScalarVal(-val.toInt());
        }
        return new GooVal(multiplyGooValue(val.toGooValue(), NEGATE));
    }

    /**
     * Multiplies every type in a GooValue by a scalar.
     *
     * @param value  the GooValue to scale
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
     * @param a  the left operand
     * @param op the operator string (+, -, *, /)
     * @param b  the right operand
     * @return the result of the arithmetic operation
     */
    private static int intOp(int a, String op, int b) {
        return switch (op) {
            case OP_PLUS -> a + b;
            case OP_MINUS -> a - b;
            case OP_MULTIPLY -> a * b;
            case OP_DIVIDE -> safeDivide(a, b);
            default -> a;
        };
    }

    /**
     * Divides a by b, returning 0 when b is zero to avoid ArithmeticException.
     *
     * @param a the dividend
     * @param b the divisor
     * @return the quotient, or 0 if b is zero
     */
    private static int safeDivide(int a, int b) {
        return b == 0 ? 0 : a / b;
    }
}
