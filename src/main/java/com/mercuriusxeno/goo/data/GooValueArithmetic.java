package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pure arithmetic operations on {@link GooValue} instances.
 * Extracted from GooValue to keep method counts manageable while allowing
 * free decomposition of the longer division/scaling implementations.
 */
final class GooValueArithmetic {

    /** Error prefix for lossy division. */
    static final String ERR_LOSSY_PREFIX = "Lossy reverse division: ";
    /** Equals separator in error messages. */
    static final String ERR_EQUALS = "=";
    /** Division separator in error messages. */
    static final String ERR_DIV = " / ";
    /** Remainder prefix in error messages. */
    static final String ERR_REMAINDER = " (remainder ";
    /** Remainder suffix in error messages. */
    static final String ERR_REMAINDER_CLOSE = ")";

    private GooValueArithmetic() {
        // utility class
    }

    /**
     * Returns a new GooValue that is the sum of self and other, scaled by a multiplier.
     *
     * @param self the base value
     * @param other the value to add
     * @param multiplier scaling factor applied to other before adding
     * @return a new GooValue with the combined amounts
     */
    static GooValue add(GooValue self, GooValue other, int multiplier) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        result.putAll(self.getAll());
        other.getAll().forEach((type, amount) ->
            result.merge(type, amount * multiplier, Integer::sum));
        return new GooValue(result);
    }

    /**
     * Returns a new GooValue with per-type subtraction, flooring each type at zero.
     * Types that reach zero or below are excluded from the result.
     *
     * @param self the base value
     * @param other the value to subtract
     * @return a new GooValue with the difference
     */
    static GooValue subtract(GooValue self, GooValue other) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        result.putAll(self.getAll());
        other.getAll().forEach((type, amount) ->
            result.merge(type, -amount, Integer::sum));
        return new GooValue(result);
    }

    /**
     * Multiplies all type amounts by a factor. Symmetric to {@link #divide(GooValue, int)}.
     *
     * @param self the value to multiply
     * @param factor the multiplier to apply to each type
     * @return a new scaled GooValue
     */
    static GooValue multiply(GooValue self, int factor) {
        if (factor <= 0) { return GooValue.EMPTY; }
        if (factor == 1) { return self; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        self.getAll().forEach((type, amount) -> result.put(type, amount * factor));
        return new GooValue(result);
    }

    /**
     * Divides all values by divisor (integer division, floors).
     *
     * @param self the value to divide
     * @param divisor the divisor for each type's amount
     * @return a new GooValue with floored divided amounts
     */
    static GooValue divide(GooValue self, int divisor) {
        if (divisor <= 1) { return self; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        self.getAll().forEach((type, amount) -> {
            int divided = amount / divisor;
            if (divided > 0) {
                result.put(type, divided);
            }
        });
        return new GooValue(result);
    }

    /**
     * Divides all type amounts by divisor, throwing if any type has a remainder.
     *
     * @param self the value to divide
     * @param divisor the divisor for each type's amount
     * @return a new GooValue with divided amounts
     * @throws ArithmeticException if any type's amount is not evenly divisible
     */
    static GooValue divideExact(GooValue self, int divisor) {
        if (divisor <= 1) { return self; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        self.getAll().forEach((type, amount) -> {
            assertDivisible(type, amount, divisor);
            int divided = amount / divisor;
            if (divided > 0) {
                result.put(type, divided);
            }
        });
        return new GooValue(result);
    }

    /**
     * Throws ArithmeticException if the amount is not evenly divisible.
     * @param type the goo type being divided (used in error messages)
     * @param amount the numerator to check for divisibility
     * @param divisor the denominator to divide by
     */
    private static void assertDivisible(GooType type, int amount, int divisor) {
        if (amount % divisor != 0) {
            throw new ArithmeticException(
                    ERR_LOSSY_PREFIX + type.getId() + ERR_EQUALS + amount
                    + ERR_DIV + divisor + ERR_REMAINDER + amount % divisor + ERR_REMAINDER_CLOSE);
        }
    }

    /**
     * Returns a new GooValue with all type volumes scaled by the given fraction.
     * Rounds each type to the nearest integer. Types that round to zero are excluded.
     *
     * @param self the value to scale
     * @param fraction the scaling factor (e.g. 0.5 for half value)
     * @return a new scaled GooValue
     */
    static GooValue scale(GooValue self, double fraction) {
        if (fraction <= 0.0) { return GooValue.EMPTY; }
        if (fraction >= 1.0) { return self; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        self.getAll().forEach((type, amount) -> {
            int scaled = (int) Math.round(amount * fraction);
            if (scaled > 0) { result.put(type, scaled); }
        });
        return new GooValue(result);
    }

    /**
     * Returns a new GooValue with all negative types clamped to zero (removed).
     * Use after applying modifiers that can introduce negatives, when the result
     * must represent a physical goo composition.
     *
     * @param self the value to floor
     * @return a new GooValue with negatives removed, or self if none exist
     */
    static GooValue floorZero(GooValue self) {
        boolean hasNegative = self.getAll().values().stream().anyMatch(v -> v < 0);
        if (!hasNegative) { return self; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        self.getAll().forEach((type, amount) -> {
            if (amount > 0) { result.put(type, amount); }
        });
        return new GooValue(result);
    }
}
