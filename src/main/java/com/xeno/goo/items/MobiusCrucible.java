package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class MobiusCrucible extends GooHolder
{
    public MobiusCrucible()
    {
        super(3, GooDrainBehavior.ALL_AT_ONCE);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return tryGooDrainBehavior(stack, context);
    }

    @Override
    public int baseCapacity()
    {
        return GooMod.config.mobiusCrucibleBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.mobiusCrucibleHoldingMultiplier();
    }
}
