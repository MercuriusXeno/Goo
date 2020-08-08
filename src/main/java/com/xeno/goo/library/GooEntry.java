package com.xeno.goo.library;

import com.xeno.goo.setup.Registry;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GooEntry
{
    // these goop mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GoopMapping class as a whole.
    public static final GooEntry EMPTY = new GooEntry(false, false);
    public static final GooEntry DENIED = new GooEntry(true, false);
    public static final GooEntry UNKNOWN = new GooEntry(false, true);
    private static final String GOOP_MAPPING_PREFACE_TRANSLATION_KEY = "tooltip.goop.composition_preface";

    private List<GooValue> values;
    private boolean isDenied;
    private boolean isUnknown;
    private boolean isFixed;

    public GooEntry(List<GooValue> goopValues, boolean isFixed) {
        this.values = goopValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = isFixed;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(List<GooValue> goopValues) {
        this.values = goopValues;
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(GooValue... adding) {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isDenied, boolean isUnknown) {
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

    public List<GooValue> values() { return this.values; }

    public double weight() { return values.stream().map(GooValue::getAmount).reduce(0d, Double::sum); }

    /**
     * @param competitor The mapping being compared to "this" instance.
     * @return true if this instance of a mapping weighs less than the competitor.
     */
    public boolean isStrongerThan(GooEntry competitor) {
        return !this.isDenied() && !this.isEmpty() && !this.isUnknown() &&
                // truncation caused weird values.
                //(Helper.truncateValue(weight()) < Helper.truncateValue(competitor.weight()) || competitor.isDenied() && competitor.isEmpty() && competitor.isUnknown());
        (weight() < competitor.weight() || competitor.isDenied() && competitor.isEmpty() && competitor.isUnknown());
    }

    public boolean isUnusable() {
        return isUnknown() || isDenied();
    }

    public GooEntry combine(GooEntry combining, boolean isSubtracting) {
        if (this.isUnknown() || combining.isUnknown()) {
            return UNKNOWN;
        }
        if (this.isDenied() || combining.isDenied()) {
            return DENIED;
        }
        Map<String, Double> product = new HashMap<>();
        for(GooValue v : this.values()) {
            if (product.containsKey(v.getFluidResourceLocation())) {
                product.put(v.getFluidResourceLocation(), product.get(v.getFluidResourceLocation()) + v.getAmount());
            } else {
                product.put(v.getFluidResourceLocation(), v.getAmount());
            }
        }

        // clone the goop values from this object.
        for (GooValue v : combining.values()) {
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

    public GooEntry add(GooEntry adding) {
        return combine(adding, false);
    }

    public GooEntry subtract(GooEntry subtracting) {
        return combine(subtracting, true);
    }

    public GooEntry add(GooValue adding) {
        return combine(new GooEntry(adding), false);
    }

    public GooEntry multiply(int i) {
        if (this.isUnknown()) {
            return UNKNOWN;
        }
        if (this.isDenied()) {
            return DENIED;
        }
        Map<String, Double> product = new HashMap<>();
        for(GooValue v : this.values()) {
            product.put(v.getFluidResourceLocation(), EntryHelper.round(v.getAmount() * i, 5));
        }
        return createFromPrimitiveGoopMap(product);
    }

    public GooEntry divide(int i) {
        // c'mon don't do that.
        if (i == 0) {
            return UNKNOWN;
        }
        Map<String, Double> product = new HashMap<>();
        for (GooValue v : this.values()) {
            product.put(v.getFluidResourceLocation(), EntryHelper.round(v.getAmount() / i, 5));
        }
        return createFromPrimitiveGoopMap(product);
    }

    // utility method for quickly
    private static GooEntry createFromPrimitiveGoopMap(Map<String, Double> product) {
        List<GooValue> values = product.entrySet().stream().map(kv -> new GooValue(kv.getKey(), kv.getValue())).collect(Collectors.toList());
        return new GooEntry(values);
    }

    public void translateToTooltip(List<ITextComponent> toolTip)
    {
        if (this.isUnusable()) {
            return;
        }

        toolTip.add(new TranslationTextComponent(GOOP_MAPPING_PREFACE_TRANSLATION_KEY));
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<GooValue> sortedValues = new SortedList<>(FXCollections.observableArrayList(values), Compare.valueWeightComparator.reversed().thenComparing(Compare.goopNameComparator));
        for(GooValue v : sortedValues) {
            index++;
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getAmount()) + " mB";
            String fluidTranslationKey = Registry.getFluidTranslationKey(v.getFluidResourceLocation());
            if (fluidTranslationKey == null) {
                continue;
            }
            displayIndex++;
            if (displayIndex % 2 == 1) {
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendString(", ").append(new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                toolTip.add(fluidAmount);
            }
        }
    }
}
