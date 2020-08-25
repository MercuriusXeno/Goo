package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.results.IResultsInformationCache;
import net.minecraft.world.World;

public class Equivalencies
{
    public static IResultsInformationCache cache(World world) {
        return IAequivaleoAPI.getInstance().getResultsInformationCache(world.func_234923_W_());
    }
}
