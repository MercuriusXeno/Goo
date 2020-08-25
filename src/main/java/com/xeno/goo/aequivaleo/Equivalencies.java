package com.xeno.goo.aequivaleo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.ICompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.results.IResultsInformationCache;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

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
    public static Map<RegistryKey<World>, Set<ICompoundContainer<?>>> lockedProducts = new HashMap<>();

    public static void resetLockedProducts(World world) {
        lockedProducts.put(world.func_234923_W_(), IAequivaleoAPI.getInstance().getLockedCompoundWrapperToTypeRegistry(world.func_234923_W_()).get().keySet());
    }

    public static IResultsInformationCache cache(World world) {
        return IAequivaleoAPI.getInstance().getResultsInformationCache(world.func_234923_W_());
    }

    public static GooEntry getEntry(World entityWorld, Item item)
    {
        if (item instanceof BlockItem) {
            return new GooEntry(entityWorld, item, cache(entityWorld).getFor(((BlockItem) item).getBlock()));
        }
        return new GooEntry(entityWorld, item, cache(entityWorld).getFor(new ItemStack(item, 1)));
    }

    public static boolean isLocked(World world, Item item)
    {
        if (item instanceof BlockItem) {
            return lockedProducts.containsKey(world.func_234923_W_()) && lockedProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.getContents() instanceof BlockItem && (p.getContents()).equals(((BlockItem)item).getBlock()));
        }
        return lockedProducts.containsKey(world.func_234923_W_()) && lockedProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.getContents() instanceof ItemStack && ((ItemStack) p.getContents()).equals(new ItemStack(item, 1), false));
    }

    public static boolean isSmelted(World world, Item item)
    {
        if (item instanceof BlockItem) {
            return furnaceProducts.containsKey(world.func_234923_W_()) && furnaceProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.asItem() instanceof BlockItem && ((BlockItem)p.asItem()).getBlock().equals(((BlockItem)item).getBlock()));
        }
        return furnaceProducts.containsKey(world.func_234923_W_()) && furnaceProducts.get(world.func_234923_W_()).stream().anyMatch(p -> p.asItem().equals(item));
    }
}
