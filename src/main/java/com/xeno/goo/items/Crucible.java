package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class Crucible extends GooHolder
{

    public Crucible()
    {
        super(1, GooDrainBehavior.ALL_AT_ONCE);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return tryGooDrainBehavior(stack, context);
    }

    @Override
    public int baseCapacity()
    {
        return GooMod.config.crucibleBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.crucibleHoldingMultiplier();
    }
}
