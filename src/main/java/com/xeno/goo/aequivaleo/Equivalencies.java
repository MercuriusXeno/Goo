package com.xeno.goo.aequivaleo;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.results.IResultsInformationCache;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class Equivalencies
{
    public static IResultsInformationCache cache(RegistryKey<World> worldKey) {
        return IAequivaleoAPI.getInstance().getResultsInformationCache(worldKey);
    }

    public static GooEntry getEntry(World world, Item item) {
        return getEntry(world.getDimensionKey(), item);
    }

    public static GooEntry getEntry(RegistryKey<World> worldKey, Item item)
    {
        return getEntry(worldKey, new ItemStack(item));
    }

    public static GooEntry getEntry(RegistryKey<World> worldKey, ItemStack item)
    {
        Set<CompoundInstance> results = cache(worldKey).getFor(item);
        return new GooEntry(results);
    }

    // used to be used to restrict solidification of furnace products but now all it does is power molten goo
    // in-world transformations
    public static Map<RegistryKey<World>, Set<Item>> furnaceProducts = new HashMap<>();

    public static List<IRecipe<?>> furnaceRecipes(World world) {

        return world.getRecipeManager().getRecipes().stream()
                .filter(r -> r.getType().toString().equals("smelting")).collect(Collectors.toList());
    }

    public static void resetFurnaceProducts(World world) {
        furnaceProducts.put(world.getDimensionKey(), Sets.newHashSet(furnaceRecipes(world).stream().map(r -> r.getRecipeOutput().getItem()).collect(Collectors.toList())));
    }

}
