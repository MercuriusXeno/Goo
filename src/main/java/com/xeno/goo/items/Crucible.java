package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class Crucible extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return data(stack).tryGooDrainBehavior(stack, context);
    }

    @Override
    public int capacity()
    {
        return GooMod.config.crucibleBaseCapacity();
    }

    @Override
    public int tanks()
    {
        return 1;
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.crucibleHoldingMultiplier();
    }

    @Override
    public GooDrainBehavior behavior()
    {
        return GooDrainBehavior.ALL_AT_ONCE;
    }
}
