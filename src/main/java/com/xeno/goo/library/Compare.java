package com.xeno.goo.library;

import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class Compare {
    public static Comparator<Item> itemLexicographicalComparator = Comparator.comparing(o -> Objects.requireNonNull(o.getRegistryName()).toString());

    public static Comparator<String> stringLexicographicalComparator = String::compareTo;

    public static Comparator<Map.Entry<IRecipe<?>, GooEntry>> recipeGoopMappingWeightComparator = Comparator.comparingDouble((k) -> k.getValue().weight());

    public static Comparator<GooValue> valueWeightComparator = Comparator.comparingDouble(GooValue::getAmount);

    public static Comparator<GooValue> goopNameComparator = Comparator.comparing(GooValue::getFluidResourceLocation);

    public static Comparator<FluidStack> fluidAmountComparator = Comparator.comparingDouble(FluidStack::getAmount);

    public static Comparator<FluidStack> fluidNameComparator = Comparator.comparing(FluidStack::getTranslationKey);

    public static Comparator<Map.Entry<String, Double>> fluidAmountMapComparator = Map.Entry.comparingByValue();

    public static Comparator<Map.Entry<String, Double>> fluidNameMapComparator = Map.Entry.comparingByKey();
}
