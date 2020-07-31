package com.xeno.goop.library;

import java.util.*;
import java.util.stream.Collectors;

public class GoopMapping {
    // these goop mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GoopMapping class as a whole.
    public static final GoopMapping EMPTY = new GoopMapping(false, false);
    public static final GoopMapping DENIED = new GoopMapping(true, false);
    public static final GoopMapping UNKNOWN = new GoopMapping(false, true);

    private List<GoopValue> values;
    private boolean isDenied;
    private boolean isUnknown;
    private boolean isFixed;

    public GoopMapping(List<GoopValue> goopValues, boolean isFixed) {
        this.values = goopValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = isFixed;
        pruneEmptyValues();
    }

    public GoopMapping(List<GoopValue> goopValues) {
        this.values = goopValues;
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
    }

    public GoopMapping(GoopValue... adding) {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
    }

    public GoopMapping(boolean isDenied, boolean isUnknown) {
        this.values = new ArrayList<>();
        this.isDenied = isDenied;
        this.isUnknown = isUnknown;
        pruneEmptyValues();
    }

    private void pruneEmptyValues() {
        values.removeIf(v -> v.getAmount() == 0);
    }

    public boolean isDenied() { return this.isDenied; }

    public boolean isUnknown() { return this.isUnknown; }

    public boolean isEmpty() { return this.values.size() == 0; }

    public boolean isFixed() {return this.isFixed; }

    public List<GoopValue> values() { return this.values; }

    public int weight() { return values.stream().map(GoopValue::getAmount).reduce(0, Integer::sum); }

    /**
     * @param competitor The mapping being compared to "this" instance.
     * @return true if this instance of a mapping weighs less than the competitor.
     */
    public boolean isStrongerThan(GoopMapping competitor) {
        return !this.isDenied() && !this.isEmpty() && !this.isUnknown() &&
                (weight() < competitor.weight() || competitor.isDenied() && competitor.isEmpty() && competitor.isUnknown());
    }

    public boolean isUnusable() {
        return isUnknown() || isDenied();
    }

    public GoopMapping combine(GoopMapping combining, boolean isSubtracting) {
        if (this.isUnknown() || combining.isUnknown()) {
            return UNKNOWN;
        }
        if (this.isDenied() || combining.isDenied()) {
            return DENIED;
        }
        Map<String, Integer> product = new HashMap<>();
        for(GoopValue v : this.values()) {
            if (product.containsKey(v.getFluidResourceLocation())) {
                product.put(v.getFluidResourceLocation(), product.get(v.getFluidResourceLocation()) + v.getAmount());
            } else {
                product.put(v.getFluidResourceLocation(), v.getAmount());
            }
        }

        // clone the goop values from this object.
        for (GoopValue v : combining.values()) {
            if (product.containsKey(v.getFluidResourceLocation())) {
                product.put(v.getFluidResourceLocation(), product.get(v.getFluidResourceLocation()) + (v.getAmount() * (isSubtracting ? -1 : 1)));
            } else {
                product.put(v.getFluidResourceLocation(), v.getAmount() * (isSubtracting ? -1 : 1));
            }
        }

        // values can't be negative, that makes less than zero sense (lol)
        for (Integer v : product.values()) {
            if (v < 0) {
                return UNKNOWN;
            }
        }
        return createFromPrimitiveGoopMap(product);
    }

    public GoopMapping add(GoopMapping adding) {
        return combine(adding, false);
    }

    public GoopMapping subtract(GoopMapping subtracting) {
        return combine(subtracting, true);
    }

    public GoopMapping add(GoopValue adding) {
        return combine(new GoopMapping(adding), false);
    }

    public GoopMapping subtract(GoopValue subtracting) {
        return combine(new GoopMapping(subtracting), true);
    }

    public GoopMapping multiply(int i) {
        if (this.isUnknown()) {
            return UNKNOWN;
        }
        if (this.isDenied()) {
            return DENIED;
        }
        Map<String, Integer> product = new HashMap<>();
        for(GoopValue v : this.values()) {
            product.put(v.getFluidResourceLocation(), v.getAmount() * i);
        }
        return createFromPrimitiveGoopMap(product);
    }

    public GoopMapping divide(int i) {
        Map<String, Integer> product = new HashMap<>();
        for (GoopValue v : this.values()) {
            // uneven division results in an unknown quantity, reject it. There's no floats in goop.
            if (v.getAmount() % i != 0) {
                System.out.println("Bad division! You have a bad mapping value.");
                return UNKNOWN;
            }
            product.put(v.getFluidResourceLocation(), v.getAmount() / i);
        }
        return createFromPrimitiveGoopMap(product);
    }

    // utility method for quickly
    private static GoopMapping createFromPrimitiveGoopMap(Map<String, Integer> product) {
        List<GoopValue> values = product.entrySet().stream().map(kv -> new GoopValue(kv.getKey(), kv.getValue())).collect(Collectors.toList());
        return new GoopMapping(values);
    }
}
