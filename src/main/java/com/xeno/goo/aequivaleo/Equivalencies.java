package com.xeno.goo.aequivaleo;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.ICompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.compound.implementation.SimpleCompoundInstance;
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
        furnaceProducts.put(world.func_234923_W_(), Sets.newHashSet(furnaceRecipes(world).stream().map(r -> r.getRecipeOutput().getItem()).collect(Collectors.toList())));
    }

    // locked products are products that have an explicit mapping.
    // explicitly mapped furnace products are attainable by design, whereas furnace products normally are not.
    public static Map<RegistryKey<World>, Set<Item>> lockedProducts = new HashMap<>();

//    public static Map<RegistryKey<World>, Map<ICompoundContainer<?>, Set<ICompoundInstance>>> mappings = new HashMap<>();
//    public static void resetMappings(World world) {
//        Map<ICompoundContainer<?>, Set<ICompoundInstance>> resultMappings = new HashMap<>();
//        IAequivaleoAPI.getInstance().getResultsInformationCache(world.func_234923_W_()).getAll()
//                .entrySet().stream()
//                .forEach(k -> {
//                    if (isValidOutput(k)) resultMappings.put(k.getKey(), k.getValue());
//                });
//        mappings.put(world.func_234923_W_(), resultMappings);
//    }

    public static void resetLockedProducts(World world) {
        Set<ICompoundContainer<?>> lockedSet = IAequivaleoAPI.getInstance().getLockedCompoundWrapperToTypeRegistry(world.func_234923_W_()).get().keySet();
        List<Item> lockedItems = lockedSet.stream().filter(l -> l.getContents() instanceof ItemStack).map(l -> ((ItemStack) l.getContents()).getItem()).collect(Collectors.toList());
        lockedProducts.put(world.func_234923_W_(), Sets.newHashSet(lockedItems));
    }

    public static Map<ICompoundContainer<?>, ImmutableSet<ICompoundInstance>> locked(World world) {
        return IAequivaleoAPI.getInstance().getLockedCompoundWrapperToTypeRegistry(world.func_234923_W_()).get();
    }

    public static IResultsInformationCache cache(World world) {
        return IAequivaleoAPI.getInstance().getResultsInformationCache(world.func_234923_W_());
    }

    public static GooEntry getEntry(World entityWorld, Item item)
    {
        Set<ICompoundInstance> results = cache(entityWorld).getFor(item);
        return new GooEntry(entityWorld, item, results);
    }

    public static boolean isLocked(World world, Item item)
    {
        return lockedProducts.containsKey(world.func_234923_W_()) && lockedProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.equals(item));
    }

    public static boolean isSmelted(World world, Item item)
    {
        return furnaceProducts.containsKey(world.func_234923_W_()) && furnaceProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.asItem().equals(item));
    }
}
