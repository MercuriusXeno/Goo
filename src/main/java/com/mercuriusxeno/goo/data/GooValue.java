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

    private final Map<GooType, Integer> values;

    public GooValue(Map<GooType, Integer> values) {
        this.values = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            if (amount > 0) {
                this.values.put(type, amount);
            }
        });
    }

    public int get(GooType type) {
        return values.getOrDefault(type, 0);
    }

    public Map<GooType, Integer> getAll() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int totalBlobs() {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns the goo type with the highest amount, or null if empty.
     * Ties break by iteration order (enum ordinal for EnumMap).
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
            result.computeIfPresent(type, (k, v) -> v - amount));
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
        if (fraction <= 0.0) return EMPTY;
        if (fraction >= 1.0) return this;
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> {
            int scaled = (int) Math.round(amount * fraction);
            if (scaled > 0) result.put(type, scaled);
        });
        return new GooValue(result);
    }

    /**
     * Divides all values by divisor (integer division, floors).
     */
    public GooValue divide(int divisor) {
        if (divisor <= 1) return this;
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
     */
    public GooContents toGooContents() {
        return toGooContents(1);
    }

    /**
     * Converts this GooValue to a GooContents scaled by item count.
     * Each type's amount is multiplied by count before widening to long.
     */
    public GooContents toGooContents(int count) {
        if (isEmpty() || count <= 0) return GooContents.EMPTY;
        Map<GooType, Long> longMap = new EnumMap<>(GooType.class);
        values.forEach((type, amount) -> longMap.put(type, (long) amount * count));
        return new GooContents(longMap);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        values.forEach((type, amount) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(type.getId()).append(": ").append(amount);
        });
        return sb.toString();
    }
}
