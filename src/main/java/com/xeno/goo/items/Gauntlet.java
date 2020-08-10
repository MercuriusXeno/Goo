package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class Gauntlet extends GooHolder
{
    public Gauntlet() {
        super(1, GooDrainBehavior.HELD_CHARGE);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {

        return tryGooDrainBehavior(stack, context);
    }

    @Override
    public int baseCapacity()
    {
        return GooMod.config.gauntletBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.gauntletHoldingMultiplier();
    }
}
