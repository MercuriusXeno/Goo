package com.xeno.goo.aequivaleo;

import com.xeno.goo.setup.Registry;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;

import java.util.Map;
import java.util.Objects;

public class EntryHelper
{
    public static ItemStack getSingleton(Item item) {
        return new ItemStack(item);
    }

    public static double round(double d, int precision) {
        return Math.round(d * Math.pow(10, precision)) / Math.pow(10, precision);
    }
}
