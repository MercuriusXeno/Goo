package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GooEntry
{
    // these goo mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GooEntry class as a whole.
    public static final GooEntry EMPTY = new GooEntry(false, false);
    public static final GooEntry DENIED = new GooEntry(true, false);
    public static final GooEntry UNKNOWN = new GooEntry(false, true);

    private List<GooValue> values;
    private boolean isDenied;
    private boolean isUnknown;
    private boolean isFixed;

    public GooEntry(List<GooValue> gooValues) {
        this.values = gooValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(GooValue... adding) {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isDenied, boolean isUnknown) {
        this.values = new ArrayList<>();
        this.isDenied = isDenied;
        this.isUnknown = isUnknown;
        this.isFixed = false;
        pruneEmptyValues();
    }

    public GooEntry(GooEntry gooEntry)
    {
        this.values = gooEntry.values;
        this.isDenied = gooEntry.isDenied;
        this.isUnknown = gooEntry.isUnknown;
        this.isFixed = gooEntry.isFixed;
    }

    public GooEntry(World world, Item item, Set<CompoundInstance> compounds)
    {
        boolean isValid = compounds.stream().anyMatch(c -> (c.getType() instanceof GooCompoundType));

        this.isDenied = !isValid;
        this.isUnknown = compounds.size() == 0;
        this.isFixed = Equivalencies.isLocked(world, item);
        if (isValid) {
            this.values = compounds.stream().filter(c -> c.getType() instanceof GooCompoundType).map(c -> new GooValue(Objects.requireNonNull(((GooCompoundType) c.getType()).fluidSupplier.get().getRegistryName()).toString(), c.getAmount())).collect(Collectors.toList());
        } else {
            this.values = new ArrayList<>();
        }
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

    public boolean isUnusable() {
        return isUnknown() || isDenied();
    }

    public String toString() {
        return this.values.stream().map(v -> v.getFluidResourceLocation() + " " + v.amount() + "mB").collect(Collectors.joining(", "));
    }

    public GooEntry copy()
    {
        return new GooEntry(this);
    }
}
