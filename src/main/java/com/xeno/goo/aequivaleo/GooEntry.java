package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.jei.GooIngredient;
import net.minecraft.item.Item;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.*;
import java.util.stream.Collectors;

public class GooEntry
{
    // these goo mappings are special and have important functions. I'm defining them here as static,
    // almost implicit, properties of the GooEntry class as a whole.
    public static final GooEntry EMPTY   = new GooEntry(false, false);
    public static final GooEntry DENIED  = new GooEntry(true, false);
    public static final GooEntry UNKNOWN = new GooEntry(false, true);

    private List<GooValue> values;
    private boolean        isDenied;
    private boolean        isUnknown;
    private boolean        isFixed;
    private boolean        deniesSolidification;

    public GooEntry(List<GooValue> gooValues)
    {
        this.values = gooValues;
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        this.deniesSolidification = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(GooValue... adding)
    {
        this.values = Arrays.asList(adding);
        this.isDenied = false;
        this.isUnknown = false;
        this.isFixed = false;
        this.deniesSolidification = false;
        pruneEmptyValues();
        sortValues();
    }

    public GooEntry(boolean isDenied, boolean isUnknown)
    {
        this.values = new ArrayList<>();
        this.isDenied = isDenied;
        this.isUnknown = isUnknown;
        this.isFixed = false;
        this.deniesSolidification = false;
        pruneEmptyValues();
    }

    public GooEntry(GooEntry gooEntry)
    {
        this.values = gooEntry.values;
        this.isDenied = gooEntry.isDenied;
        this.isUnknown = gooEntry.isUnknown;
        this.isFixed = gooEntry.isFixed;
        this.deniesSolidification = gooEntry.deniesSolidification;
    }

    public GooEntry(RegistryKey<World> worldKey, Item item, Set<CompoundInstance> compounds)
    {
        boolean isValid = compounds.stream().anyMatch(c -> (c.getType() instanceof GooCompoundType));

        this.isDenied = !isValid;
        this.isUnknown = compounds.size() == 0;
        this.isFixed = Equivalencies.isLocked(worldKey, item);
        this.deniesSolidification = compounds.stream().anyMatch(compoundInstance -> compoundInstance.getType() == Registry.FORBIDDEN.get());
        if (isValid)
        {
            this.values = compounds.stream()
                            .filter(c -> c.getType() instanceof GooCompoundType)
                            .filter(c -> ((GooCompoundType) c.getType()).fluidSupplier.get() != null) //Ensure that only goos which are none logical are used.
                            .filter(c -> Math.floor(c.getAmount()) > 0)
                            .map(c -> new GooValue(Objects.requireNonNull(((GooCompoundType) c.getType()).fluidSupplier.get().getRegistryName()).toString(),
                              Math.floor(c.getAmount())))
                            .collect(Collectors.toList());
        }
        else
        {
            this.values = new ArrayList<>();
        }
    }

    private void pruneEmptyValues()
    {
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

    public boolean isUnusable()
    {
        return isUnknown() || isDenied();
    }

    public boolean deniesSolidification() {
        return isUnusable() || this.deniesSolidification;
    }

    public String toString()
    {
        return this.values.stream().map(v -> v.getFluidResourceLocation() + " " + v.amount() + "mB").collect(Collectors.joining(", "));
    }

    public GooEntry copy()
    {
        return new GooEntry(this);
    }

	public GooEntry scale(double scale) {
        List<GooValue> values = new ArrayList<>();
        this.values().forEach((v) -> values.add(scaleValue(v, scale)));
        values.removeIf(Objects::isNull);
        return new GooEntry(values);
	}

    private GooValue scaleValue(GooValue v, double scale) {
        double amount = Math.floor(v.amount() * scale);
        if (amount <= 0d) {
            return null;
        }
        return new GooValue(v.getFluidResourceLocation(), amount);
    }

    public GooEntry addGooContentsToMapping(IFluidHandlerItem capability) {
        int tanks = capability.getTanks();
        List<GooValue> valuesInTank = new ArrayList<>();
        for(int i = 0; i < tanks; i++) {
            FluidStack maybeGoo = capability.getFluidInTank(i);
            if (maybeGoo.isEmpty()) {
                continue;
            }
            if (!(maybeGoo.getFluid() instanceof GooFluid)) {
                continue;
            }

            valuesInTank.add(new GooValue(maybeGoo.getFluid().getRegistryName().toString(), maybeGoo.getAmount()));
        }
        valuesInTank.addAll(this.values);
        return new GooEntry(valuesInTank);
    }

    public GooEntry addGooContentsToMapping(List<FluidStack> contentsOfCurrentItemStackAsGooContainer) {
        List<GooValue> valuesInTank = new ArrayList<>();
        contentsOfCurrentItemStackAsGooContainer.forEach(v -> valuesInTank.add(new GooValue(v.getFluid().getRegistryName().toString(), v.getAmount())));
        valuesInTank.addAll(this.values);
        return new GooEntry(valuesInTank);
    }

	public List<FluidStack> inputsAsFluidStacks() {
        List<FluidStack> ingredients = new ArrayList<>();
        for(GooValue value : values) {
            ingredients.add(new FluidStack(Registry.getFluid(value.getFluidResourceLocation()), (int)value.amount()));
        }
        return ingredients;
	}

	public List<GooIngredient> inputsAsGooIngredient() {
        List<GooIngredient> ingredients = new ArrayList<>();
        for(GooValue value : values) {
            ingredients.add(new GooIngredient((int)value.amount(), new ResourceLocation(value.getFluidResourceLocation())));
        }
        return ingredients;
	}
}
