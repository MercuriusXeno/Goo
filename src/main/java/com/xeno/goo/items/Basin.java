package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.ItemStack;

public class Basin extends BasinAbstraction
{
    public Basin()
    {
        super(() -> new ItemStack(Registry.BASIN.get()), GooMod.config.basinCapacity());
    }
}