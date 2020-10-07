package com.xeno.goo.aequivaleo;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
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
    // furnace products are items produced via smelting
    public static Map<RegistryKey<World>, Set<Item>> furnaceProducts = new HashMap<>();

    public static List<IRecipe<?>> furnaceRecipes(World world) {
        return world.getRecipeManager().getRecipes().stream()
                .filter(r -> r.getType().toString().equals("smelting")).collect(Collectors.toList());
    }

    public static void resetFurnaceProducts(World world) {
        furnaceProducts.put(world.getDimensionKey(), Sets.newHashSet(furnaceRecipes(world).stream().map(r -> r.getRecipeOutput().getItem()).collect(Collectors.toList())));
    }

    public static Map<ICompoundContainer<?>, ImmutableSet<CompoundInstance>> locked(World world) {
        return IAequivaleoAPI.getInstance().getLockedCompoundWrapperToTypeRegistry(world.getDimensionKey()).get();
    }

    public static IResultsInformationCache cache(World world) {
        return IAequivaleoAPI.getInstance().getResultsInformationCache(world.getDimensionKey());
    }

    public static GooEntry getEntry(World entityWorld, Item item)
    {
        Set<CompoundInstance> results = cache(entityWorld).getFor(item);
        return new GooEntry(entityWorld, item, results);
    }

    public static boolean isLocked(World world, Item item)
    {
        return locked(world).entrySet().stream().anyMatch(l -> l.getKey().getContents().equals(item));
    }

    public static boolean isSmelted(World world, Item item)
    {
        return furnaceProducts.containsKey(world.getDimensionKey()) && furnaceProducts.get(world.getDimensionKey()).stream().anyMatch(p -> p.asItem().equals(item));
    }
}
