package com.xeno.goop.library;

import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class Compare {
    public static Comparator<Item> itemLexicographicalComparator = Comparator.comparing(o -> Objects.requireNonNull(o.getRegistryName()).toString());

    public static Comparator<String> stringLexicographicalComparator = String::compareTo;

    public static Comparator<Map.Entry<IRecipe<?>, GoopMapping>> recipeGoopMappingWeightComparator = Comparator.comparingDouble((k) -> k.getValue().weight());

    public static Comparator<GoopValue> valueWeightComparator = Comparator.comparingDouble(GoopValue::getAmount);

    public static Comparator<GoopValue> goopNameComparator = Comparator.comparing(GoopValue::getFluidResourceLocation);

    public static Comparator<FluidStack> fluidAmountComparator = Comparator.comparingDouble(FluidStack::getAmount);

    public static Comparator<FluidStack> fluidNameComparator = Comparator.comparing(FluidStack::getTranslationKey);

}
