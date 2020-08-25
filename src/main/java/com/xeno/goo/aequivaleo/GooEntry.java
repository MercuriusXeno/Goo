package com.xeno.goo.aequivaleo;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.Compare;
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
    // these goo mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GooEntry class as a whole.
    public static final GooEntry EMPTY = new GooEntry(false, false, true);
    public static final GooEntry DENIED = new GooEntry(true, false, false);
    public static final GooEntry UNKNOWN = new GooEntry(false, true, true);
    private static final String GOO_MAPPING_PREFACE_TRANSLATION_KEY = "tooltip.goo.composition_preface";
    private static final String CANNOT_SOLIDIFY_TRANSLATION_KEY = "tooltip.goo.cannot_solidify";
    private static final String CANNOT_GOO_TRANSLATION_KEY = "tooltip.goo.cannot_goo";

    private List<GooValue> values;
    private boolean isDenied;
    private boolean isUnknown;
    private boolean isFixed;
    private boolean isAttainable;

    public GooEntry(List<GooValue> gooValues) {
        this.values = gooValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = true;
        this.isAttainable = true;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isAttainable, List<GooValue> gooValues) {
        this.values = gooValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        this.isAttainable = isAttainable;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isAttainable, GooValue... adding) {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        this.isAttainable = isAttainable;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isDenied, boolean isUnknown, boolean isAttainable) {
        this.values = new ArrayList<>();
        this.isDenied = isDenied;
        this.isUnknown = isUnknown;
        this.isAttainable = isAttainable;
        this.isFixed = false;
        pruneEmptyValues();
    }

    public GooEntry(GooEntry gooEntry)
    {
        this.values = gooEntry.values;
        this.isDenied = gooEntry.isDenied;
        this.isUnknown = gooEntry.isUnknown;
        this.isAttainable = gooEntry.isAttainable;
        this.isFixed = gooEntry.isFixed;
    }

    private void pruneEmptyValues() {
        values.removeIf(v -> v.amount() == 0);
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

    public double weight() { return values.stream().map(GooValue::amount).reduce(0d, Double::sum); }

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

    public boolean isUnattainable() {
        return isUnusable() || !isAttainable();
    }

    private boolean isAttainable() { return isAttainable; }

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
                product.put(v.getFluidResourceLocation(), product.get(v.getFluidResourceLocation()) + v.amount());
            } else {
                product.put(v.getFluidResourceLocation(), v.amount());
            }
        }

        // clone the goo values from this object.
        for (GooValue v : combining.values()) {
            if (product.containsKey(v.getFluidResourceLocation())) {
                product.put(v.getFluidResourceLocation(), product.get(v.getFluidResourceLocation()) + (v.amount() * (isSubtracting ? -1 : 1)));
            } else {
                product.put(v.getFluidResourceLocation(), v.amount() * (isSubtracting ? -1 : 1));
            }
        }

        // values can't be negative, that makes less than zero sense (lol)
        for (Double v : product.values()) {
            if (v < 0) {
                return UNKNOWN;
            }
        }
        return createFromPrimitiveGooMap(this.isAttainable && combining.isAttainable, product);
    }

    public GooEntry add(GooEntry adding) {
        return combine(adding, false);
    }

    public GooEntry subtract(GooEntry subtracting) {
        return combine(subtracting, true);
    }

    public GooEntry add(GooValue adding) {
        return combine(new GooEntry(this.isAttainable, adding), false);
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
            product.put(v.getFluidResourceLocation(), EntryHelper.round(v.amount() * i, 5));
        }
        return createFromPrimitiveGooMap(this.isAttainable, product);
    }

    public GooEntry divide(String hint, int i) {
        // c'mon don't do that.
        if (i == 0) {
            return UNKNOWN;
        }
        Map<String, Double> product = new HashMap<>();
        for (GooValue v : this.values()) {
            if (v.amount() / i != EntryHelper.round(v.amount() / i, 5)) {
                GooMod.warn("Ugly division happening... hint: " + hint);
            }
            product.put(v.getFluidResourceLocation(), EntryHelper.round(v.amount() / i, 5));
        }
        return createFromPrimitiveGooMap(this.isAttainable, product);
    }

    // utility method for quickly
    private static GooEntry createFromPrimitiveGooMap(boolean isAttainable, Map<String, Double> product) {
        List<GooValue> values = product.entrySet().stream().map(kv -> new GooValue(kv.getKey(), kv.getValue())).collect(Collectors.toList());
        return new GooEntry(isAttainable, values);
    }

    public void translateToTooltip(List<ITextComponent> toolTip)
    {
        if (this.isUnusable()) {
            toolTip.add(new TranslationTextComponent(CANNOT_GOO_TRANSLATION_KEY));
            return;
        }

        if (this.isUnattainable()) {
            toolTip.add(new TranslationTextComponent(CANNOT_SOLIDIFY_TRANSLATION_KEY));
        }

        toolTip.add(new TranslationTextComponent(GOO_MAPPING_PREFACE_TRANSLATION_KEY));
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<GooValue> sortedValues = new SortedList<>(FXCollections.observableArrayList(values), Compare.valueWeightComparator.reversed().thenComparing(Compare.gooNameComparator));
        for(GooValue v : sortedValues) {
            index++;
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.amount()) + " mB";
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

    public String toString() {
        return this.values.stream().map(v -> v.getFluidResourceLocation() + " " + v.amount() + "mB").collect(Collectors.joining(", "));
    }

    public void makeUnattainable()
    {
        this.isAttainable = false;
    }

    public GooEntry copy()
    {
        return new GooEntry(this);
    }
}
