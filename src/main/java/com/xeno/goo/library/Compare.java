package com.xeno.goo.library;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.Map;

public class Compare {
    public static Comparator<String> stringLexicographicalComparator = String::compareTo;

    public static Comparator<Map.Entry<IRecipe<?>, GooEntry>> recipeGooEntryWeightComparator = Comparator.comparingDouble((k) -> k.getValue().weight());

    public static Comparator<GooValue> valueWeightComparator = Comparator.comparingDouble(GooValue::amount);

    public static Comparator<GooValue> gooNameComparator = Comparator.comparing(GooValue::getFluidResourceLocation);

    public static Comparator<FluidStack> fluidAmountComparator = Comparator.comparingDouble(FluidStack::getAmount);

    public static Comparator<FluidStack> fluidNameComparator = Comparator.comparing(FluidStack::getTranslationKey);
}
