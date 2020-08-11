package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class Gauntlet extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return data(stack).tryGooDrainBehavior(stack, context);
    }

    @Override
    public int capacity()
    {
        return GooMod.config.gauntletBaseCapacity();
    }

    @Override
    public int tanks()
    {
        return 1;
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.gauntletHoldingMultiplier();
    }

    @Override
    public GooDrainBehavior behavior()
    {
        return GooDrainBehavior.HELD_CHARGE;
    }
}
