package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Gauntlet extends GauntletAbstraction
{
    public Gauntlet()
    {
        super(GooMod.config.gauntletCapacity());
    }
}