package com.xeno.goop.library;

import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class Compare {
    public static Comparator<Item> itemLexicographicalComparator = (o1, o2) -> Objects.requireNonNull(o1.getRegistryName()).toString().compareTo(o2.getRegistryName().toString());

    public static Comparator<String> stringLexicographicalComparator = String::compareTo;

    public static Comparator<? super Map.Entry<IRecipe<?>, GoopMapping>> recipeGoopMappingWeightComparator = Comparator.comparingInt((k) -> k.getValue().weight());
}
