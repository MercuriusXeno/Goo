package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Represents the goo composition of an item: how many blobs of each type it contains.
 */
public class GooValue {

    public static final GooValue EMPTY = new GooValue(Collections.emptyMap());

    /** Error prefix for lossy division. */
    private static final String ERR_LOSSY_PREFIX = "Lossy reverse division: ";
    /** Equals separator in error messages. */
    private static final String ERR_EQUALS = "=";
    /** Division separator in error messages. */
    private static final String ERR_DIV = " / ";
    /** Remainder prefix in error messages. */
    private static final String ERR_REMAINDER = " (remainder ";
    /** Remainder suffix in error messages. */
    private static final String ERR_REMAINDER_CLOSE = ")";
    /** Display label for empty values. */
    private static final String LABEL_NONE = "none";
    /** Separator between types in toString. */
    private static final String TYPE_SEPARATOR = ", ";
    /** Key-value separator in toString. */
    private static final String KV_SEPARATOR = ": ";

    private final Map<GooType, Integer> values;

    /**
     * Creates a GooValue from a map of goo types to amounts.
     * Zero-valued entries are stripped; the internal map is an EnumMap copy.
     *
     * @param values goo type amounts (zero entries are excluded)
     */
    public GooValue(Map<GooType, Integer> values) {
        this.values = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            if (amount != 0) {
                this.values.put(type, amount);
            }
        });
    }

    /**
     * Returns the amount of the given goo type, or 0 if absent.
     *
     * @param type the goo type to query
     * @return amount in blobs, or 0
     */
    public int get(GooType type) {
        return values.getOrDefault(type, 0);
    }

    /**
     * Returns an unmodifiable view of all goo type amounts.
     *
     * @return map of goo types to their amounts (never null, may be empty)
     */
    public Map<GooType, Integer> getAll() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Returns true if this value has no goo types.
     *
     * @return true if the value map is empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * True if any goo type has a negative amount.
     *
     * @return true if any type's amount is below zero
     */
    public boolean hasNegative() {
        return values.values().stream().anyMatch(v -> v < 0);
    }

    /**
     * Number of distinct goo types with non-zero amounts.
     *
     * @return the count of goo types present
     */
    public int typeCount() {
        return values.size();
    }

    /**
     * Returns the sum of all goo type amounts.
     *
     * @return total blobs across all types
     */
    public int totalBlobs() {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns the goo type with the highest amount, or null if empty.
     * Ties break by iteration order (enum ordinal for EnumMap).
     *
     * @return the dominant goo type, or null if empty
     */
    public GooType largestType() {
        GooType largest = null;
        int highest = 0;
        for (Map.Entry<GooType, Integer> entry : values.entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                largest = entry.getKey();
            }
        }
        return largest;
    }

    /**
     * Returns a new GooValue that is the sum of this and another, scaled by a multiplier.
     *
     * @param other the value to add
     * @param multiplier scaling factor applied to other before adding
     * @return a new GooValue with the combined amounts
     */
    public GooValue add(GooValue other, int multiplier) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        result.putAll(this.values);
        other.values.forEach((type, amount) ->
            result.merge(type, amount * multiplier, Integer::sum));
        return new GooValue(result);
    }

    /**
     * Returns a new GooValue with per-type subtraction, flooring each type at zero.
     * Types that reach zero or below are excluded from the result.
     *
     * @param other the value to subtract
     * @return a new GooValue with the difference
     */
    public GooValue subtract(GooValue other) {
        Map<GooType, Integer> result = new EnumMap<>(this.values);
        other.values.forEach((type, amount) ->
            result.merge(type, -amount, Integer::sum));
        return new GooValue(result);
    }

    /**
     * Returns a new GooValue with all negative types clamped to zero (removed).
     * Use after applying modifiers that can introduce negatives, when the result
     * must represent a physical goo composition.
     *
     * @return a new GooValue with negatives removed, or this if none exist
     */
    public GooValue floorZero() {
        boolean hasNegative = values.values().stream().anyMatch(v -> v < 0);
        if (!hasNegative) { return this; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            if (amount > 0) { result.put(type, amount); }
        });
        return new GooValue(result);
    }

    /**
     * Returns a new GooValue with all type volumes scaled by the given fraction.
     * Rounds each type to the nearest integer. Types that round to zero are excluded.
     *
     * @param fraction the scaling factor (e.g. 0.5 for half value)
     * @return a new scaled GooValue
     */
    public GooValue scale(double fraction) {
        if (fraction <= 0.0) { return EMPTY; }
        if (fraction >= 1.0) { return this; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            int scaled = (int) Math.round(amount * fraction);
            if (scaled > 0) { result.put(type, scaled); }
        });
        return new GooValue(result);
    }

    /**
     * Multiplies all type amounts by a factor. Symmetric to {@link #divide(int)}.
     *
     * @param factor the multiplier to apply to each type
     * @return a new scaled GooValue
     */
    public GooValue multiply(int factor) {
        if (factor <= 0) { return EMPTY; }
        if (factor == 1) { return this; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> result.put(type, amount * factor));
        return new GooValue(result);
    }

    /**
     * Divides all type amounts by divisor, throwing if any type has a remainder.
     *
     * @param divisor the divisor for each type's amount
     * @return a new GooValue with divided amounts
     * @throws ArithmeticException if any type's amount is not evenly divisible
     */
    public GooValue divideExact(int divisor) {
        if (divisor <= 1) { return this; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            if (amount % divisor != 0) {
                throw new ArithmeticException(
                        ERR_LOSSY_PREFIX + type.getId() + ERR_EQUALS + amount
                        + ERR_DIV + divisor + ERR_REMAINDER + amount % divisor + ERR_REMAINDER_CLOSE);
            }
            int divided = amount / divisor;
            if (divided > 0) {
                result.put(type, divided);
            }
        });
        return new GooValue(result);
    }

    /**
     * Divides all values by divisor (integer division, floors).
     *
     * @param divisor the divisor for each type's amount
     * @return a new GooValue with floored divided amounts
     */
    public GooValue divide(int divisor) {
        if (divisor <= 1) { return this; }
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            int divided = amount / divisor;
            if (divided > 0) {
                result.put(type, divided);
            }
        });
        return new GooValue(result);
    }

    /**
     * Converts this GooValue to a GooContents, widening int amounts to long.
     * Used when creating a PartiallyMeltedItem from an item's goo value.
     *
     * @return a GooContents with the same type amounts widened to long
     */
    public GooContents toGooContents() {
        return toGooContents(1);
    }

    /**
     * Converts this GooValue to a GooContents scaled by item count.
     * Each type's amount is multiplied by count before widening to long.
     *
     * @param count number of items to scale by
     * @return a GooContents with scaled amounts widened to long
     */
    public GooContents toGooContents(int count) {
        if (isEmpty() || count <= 0) { return GooContents.EMPTY; }
        Map<GooType, Long> longMap = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> longMap.put(type, (long) amount * count));
        return new GooContents(longMap);
    }

    /** Returns a human-readable summary like "metal: 480, vital: 120", or "none" if empty. */
    @Override
    public String toString() {
        if (isEmpty()) { return LABEL_NONE; }
        StringBuilder sb = new StringBuilder();
        values.forEach((type, amount) -> {
            if (sb.length() > 0) { sb.append(TYPE_SEPARATOR); }
            sb.append(type.getId()).append(KV_SEPARATOR).append(amount);
        });
        return sb.toString();
    }
}
