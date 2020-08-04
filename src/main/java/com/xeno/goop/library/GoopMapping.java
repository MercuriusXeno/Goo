package com.xeno.goop.library;

import com.xeno.goop.setup.Registry;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GoopMapping {
    // these goop mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GoopMapping class as a whole.
    public static final GoopMapping EMPTY = new GoopMapping(false, false);
    public static final GoopMapping DENIED = new GoopMapping(true, false);
    public static final GoopMapping UNKNOWN = new GoopMapping(false, true);
    private static final String GOOP_MAPPING_PREFACE_TRANSLATION_KEY = "tooltip.goop.composition_preface";

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
        sortValues();
    }

    public GoopMapping(List<GoopValue> goopValues) {
        this.values = goopValues;
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
        sortValues();
    }

    public GoopMapping(GoopValue... adding) {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
        sortValues();
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

    private void sortValues()
    {
        values.sort(Compare.valueWeightComparator.reversed());
    }

    public boolean isDenied() { return this.isDenied; }

    public boolean isUnknown() { return this.isUnknown; }

    public boolean isEmpty() { return this.values.size() == 0; }

    public boolean isFixed() {return this.isFixed; }

    public List<GoopValue> values() { return this.values; }

    public double weight() { return values.stream().map(GoopValue::getAmount).reduce(0d, Double::sum); }

    /**
     * @param competitor The mapping being compared to "this" instance.
     * @return true if this instance of a mapping weighs less than the competitor.
     */
    public boolean isStrongerThan(GoopMapping competitor) {
        return !this.isDenied() && !this.isEmpty() && !this.isUnknown() &&
                // truncation caused weird values.
                //(Helper.truncateValue(weight()) < Helper.truncateValue(competitor.weight()) || competitor.isDenied() && competitor.isEmpty() && competitor.isUnknown());
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
        Map<String, Double> product = new HashMap<>();
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
        for (Double v : product.values()) {
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

    public GoopMapping multiply(int i) {
        if (this.isUnknown()) {
            return UNKNOWN;
        }
        if (this.isDenied()) {
            return DENIED;
        }
        Map<String, Double> product = new HashMap<>();
        for(GoopValue v : this.values()) {
            product.put(v.getFluidResourceLocation(), Helper.round(v.getAmount() * i, 5));
        }
        return createFromPrimitiveGoopMap(product);
    }

    public GoopMapping divide(int i) {
        // c'mon don't do that.
        if (i == 0) {
            return UNKNOWN;
        }
        Map<String, Double> product = new HashMap<>();
        for (GoopValue v : this.values()) {
            product.put(v.getFluidResourceLocation(), Helper.round(v.getAmount() / i, 5));
        }
        return createFromPrimitiveGoopMap(product);
    }

    // utility method for quickly
    private static GoopMapping createFromPrimitiveGoopMap(Map<String, Double> product) {
        List<GoopValue> values = product.entrySet().stream().map(kv -> new GoopValue(kv.getKey(), kv.getValue())).collect(Collectors.toList());
        return new GoopMapping(values);
    }

    public void translateToTooltip(List<ITextComponent> toolTip)
    {
        if (this.isUnusable()) {
            return;
        }

        toolTip.add(new TranslationTextComponent(GOOP_MAPPING_PREFACE_TRANSLATION_KEY));
        int index = 0;
        int displayIndex = 0;
        ITextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<GoopValue> sortedValues = new SortedList<>(FXCollections.observableArrayList(values), Compare.valueWeightComparator.reversed().thenComparing(Compare.goopNameComparator));
        for(GoopValue v : sortedValues) {
            index++;
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getAmount()) + " mB";
            String fluidTranslationKey = Registry.getFluidTranslationKey(v.getFluidResourceLocation());
            if (fluidTranslationKey == null) {
                continue;
            }
            displayIndex++;
            if (displayIndex % 2 == 1) {
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendText(", ").appendSibling(new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                toolTip.add(fluidAmount);
            }
        }
    }
}
